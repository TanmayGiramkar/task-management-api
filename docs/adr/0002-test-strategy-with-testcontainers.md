# 0002. Test strategy: layered tests, Testcontainers, separate unit and integration phases

- **Status:** Accepted
- **Date:** 2026-11-07
- **Stage:** 3 (Testing)

## Context

Before this stage the build had 7 tests, and all of them loaded the full application against the local
`task-postgres` database. So:

- `mvnw verify` failed on any machine without that container and a `.env` file, including Jenkins (Stage 8).
- Tests that write data would have written into the database that holds the owner's manual test data.
- Business rules (PUT vs PATCH, query validation, sorting) and the HTTP error contract had no tests.
- `V1__create_tasks_table.sql` had never run anywhere: the local database was baselined at V1.

## Decision

1. **Test in layers, each checking one thing:**

   | Layer | Tool | Checks |
   |---|---|---|
   | Unit (`TaskServiceTest`) | JUnit 5 + Mockito | Service rules, with the repository mocked |
   | Web slice (`TaskControllerTest`) | `@WebMvcTest` + `@MockitoBean` | HTTP contract: status codes, JSON, validation, error body |
   | JPA slice (`TaskSpecificationsIT`) | `@DataJpaTest` + Testcontainers | Specification SQL on real PostgreSQL |
   | Full stack (`TaskApiIT`, `FlywayMigrationIT`, `OpenApiDocumentationIT`, `TaskapiApplicationIT`) | `@SpringBootTest` + MockMvc + Testcontainers | Whole request path, real commits, migrations |

2. **Run database tests on PostgreSQL 17 in Testcontainers.** `TestcontainersConfiguration` declares the
   container as a bean with `@ServiceConnection`, which points the DataSource and Flyway at it (random
   port, generated credentials). The local database and `.env` are not used. Flyway builds the schema
   from V1 on every run.
3. **Split the Maven phases by name.** `*Test` classes run in surefire (`mvnw test`, no Docker, seconds).
   `*IT` classes run in failsafe (`mvnw verify`, needs Docker). The two existing context tests were
   renamed to `*IT`.
4. **One JaCoCo agent for both phases.** `prepare-agent` writes the agent into the `argLine` property;
   surefire and failsafe both reference it with `@{argLine}` and append to `target/jacoco.exec`;
   `report` runs in `verify`. The HTML report is `target/site/jacoco/index.html`. This stage adds the
   report only; the coverage gate is part of the Stage 8 pipeline.
5. **Integration tests commit for real and clean up with `deleteAll()`** in the container database.
   They are not `@Transactional`: a test transaction would keep changes unflushed, so `@PreUpdate`
   would never set `updatedAt` and the tests would pass for the wrong reason. (`@DataJpaTest` does roll
   back, which is fine there because it tests queries, not commit behaviour.)
6. **Load Mockito as a `-javaagent`** (Mockito's documented Maven setup) instead of letting it attach
   itself at runtime, which current JDKs warn about and future JDKs will block.

## Alternatives considered

| Alternative | Why not |
|---|---|
| **H2 in-memory database** | Fast and needs no Docker, but it is not PostgreSQL. SQL dialect, `LOWER`/`LIKE` behaviour, check constraints and Flyway scripts can behave differently, so a passing test would not prove production works. |
| **Keep using the local `task-postgres`** | Breaks on Jenkins and on any new machine, and tests would write into the owner's data. |
| **A static `@Container` field with `@Testcontainers`** | Also works. The `@Bean @ServiceConnection` form is what Spring Boot documents and generates, needs no JDBC property wiring, and lets Spring manage the container's lifecycle. |
| **All tests in surefire** | One phase is simpler, but then `mvnw test` needs Docker, and the Stage 8 pipeline cannot run fast unit tests and Testcontainers tests as separate stages. |
| **`@Transactional` rollback in API tests** | No cleanup code, but hides flush-time behaviour (see decision 5). |
| **Separate JaCoCo files per phase plus a `merge` goal** | Same result with more configuration. Appending to one file is enough while there is one module. |

## Consequences

- `mvnw verify` runs on any machine with JDK 21 and Docker; no database or `.env` setup. Checked by
  running it from a fresh checkout with no `.env`.
- `mvnw test` runs the unit and web tests without Docker.
- The integration tests start three containers per run (one per distinct Spring test context, about
  5 seconds each). Acceptable now; a shared static container is the fix if this grows.
- `jacoco.exec` is appended to across runs, so use `mvnw clean verify` for an accurate report.
- The tests found a real bug: create/update responses returned timestamps with the JVM clock's
  precision (100 ns on Windows), while `TIMESTAMP(6)` stores microseconds, so a later GET returned a
  different value. `Task` now truncates to microseconds.
- The `-javaagent` and `-Duser.timezone=Asia/Kolkata` options live in the surefire and failsafe
  `argLine`. IntelliJ does not always pick these up, so the README explains the JUnit VM option.
- Each JVM loaded with these agents prints a harmless warning:
  "Sharing is only supported for boot loader classes because bootstrap classpath has been appended".
  Class data sharing (a JVM start-up optimisation) is limited to JDK classes once an agent adds jars
  to the bootstrap classpath, which the Mockito agent does.
