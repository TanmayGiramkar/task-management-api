# CLAUDE.md — Task Management API

Read this file fully before doing anything in this repository.

## 1. What this project is

A Java Spring Boot REST API for task management, being grown into a portfolio-grade
**Java backend + DevOps** project for off-campus placements.

The owner (Tanmay) is a third-year IT student. **He does not write the code; he reviews it,
learns from it, and must be able to explain every decision in an interview.** So:

- Explain *why* for every non-obvious choice, in plain language.
- Prefer clear, conventional, well-known solutions over clever ones.
- Never add a technology just to list it. If you can't state the problem it solves here, don't add it.

## 2. Environment

| Item | Value |
|---|---|
| OS | Windows 11 (use `mvnw.cmd` in PowerShell, `./mvnw` in Git Bash) |
| Java | 21 |
| Spring Boot | 4.1.1 (Jackson 3, Hibernate 7, Spring Framework 7) |
| Build | Maven via the wrapper. Never require a global Maven install |
| Database | PostgreSQL 17 in the Docker container **`task-postgres`**, port 5432, database `taskdb` |
| Project root | `E:\Desktop\taskapi\taskapi` (the folder with `pom.xml`) |
| IDE | IntelliJ IDEA Community. Run config has VM option `-Duser.timezone=Asia/Kolkata` and working directory = project root |

### Configuration and secrets
- Credentials live in **`.env`** in the project root (`DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`), loaded by
  `spring.config.import=optional:file:.env[.properties]`. The path is relative to the working
  directory, so always run the app from the project root.
- `application.properties` references `${DB_USERNAME}` / `${DB_PASSWORD}` with no defaults (fail fast).
  `app.security.jwt.secret=${JWT_SECRET:}` has an empty default only so `SecurityConfig` can fail at startup
  with a clear message; the secret must be Base64 of at least 32 bytes.
- Tests never read `.env`: Testcontainers supplies the database and
  `src/test/resources/config/application.properties` generates a random JWT key per run.
- The datasource URL contains `?options=-c%20TimeZone%3DAsia/Kolkata`. Do not remove it
  (it fixes a past `Asia/Calcutta` JDBC timezone error).

### Commands
- Build and run all tests: `mvnw.cmd verify`
- Run the app: `mvnw.cmd spring-boot:run` (from the project root; `task-postgres` must be running)
- Check the DB is up: `docker ps` (look for `task-postgres`, status `Up`)
- Start it if stopped: `docker start task-postgres`
- Inspect Flyway history: `docker exec task-postgres psql -U admin -d taskdb -c "SELECT version, description, type, success FROM flyway_schema_history;"`

## 3. Hard rules (never break these)

1. **Never read, print, log, copy or commit `.env`**, or any password, token or key. Never add
   secrets to `application.properties`, tests, Dockerfiles, Jenkinsfiles or docs.
2. **Never commit to or push to `main`.** One branch per stage, e.g. `stage-3-testing`.
   The owner reviews and merges.
3. **Never delete or recreate the `task-postgres` container or its volumes**, and never run
   `docker rm`, `docker volume rm`, `docker system prune`, `DROP TABLE`, `TRUNCATE` or bulk `DELETE`
   against the local database. Task IDs 10–15 are test data the owner relies on.
4. **Never edit a Flyway migration that has already been committed.** Add a new `V<n>__...sql`.
5. **Every feature ships with tests.** Run `mvnw.cmd verify` and make it pass before saying a task is done.
   Never delete, skip or weaken a test to make a build pass; fix the cause.
6. **Do not change unrelated code** or reformat files you aren't otherwise editing.
7. **No new dependency without a one-line justification** in your summary. Let the Spring Boot
   parent manage versions wherever it can.
8. If something is ambiguous or could destroy data, **stop and ask**.
9. Commits and PRs must not contain Co-Authored-By trailers or 'Generated with Claude Code' lines.

## 4. Current state (verified)

### Done
- CRUD: `POST /tasks`, `GET /tasks`, `GET /tasks/{id}`, `PUT /tasks/{id}`, `PATCH /tasks/{id}`, `DELETE /tasks/{id}`
- DTOs: `TaskRequest` (class, POST/PUT), `TaskPatchRequest` (record, PATCH: all fields optional,
  `@Pattern(".*\\S.*")` so a sent title can't be blank), `TaskResponse`, generic `PageResponse<T>` record
- Validation: title `@NotBlank`, max 100; description max 500
- `GET /tasks` supports `status`, `search` (case-insensitive, title OR description), `sortBy`,
  `direction`, `page`, `size` — built with **JPA Specifications** (`TaskSpecifications`) and
  `JpaSpecificationExecutor`
- Query parameter rules in `TaskService`: sort whitelist `id, title, status, createdAt, updatedAt`;
  default sort `id`; `id ASC` tiebreaker for stable pagination; `MAX_PAGE_SIZE = 50`;
  invalid input throws `InvalidQueryParameterException`
- `GlobalExceptionHandler` (`@RestControllerAdvice`): 400 (validation, bad query params, type mismatch,
  unreadable body), 404 (task not found, unknown path), 405, and a catch-all 500 that logs with SLF4J
  and never exposes stack traces
- **Flyway** owns the schema. `V1__create_tasks_table.sql` (local DB was *baselined* at V1, so V1 never
  ran there), `V2__fix_task_column_lengths.sql` (title 100, description 500), `V3__create_users_table.sql`,
  `V4__create_refresh_tokens_table.sql`, `V5__add_owner_to_tasks.sql` (nullable `owner_id`).
  `spring.jpa.hibernate.ddl-auto=validate`. **The next migration is V6.**
- `Task` timestamps are truncated to microseconds to match the `TIMESTAMP(6)` columns, so create/update
  responses equal what later reads return
- **Stage 2 — OpenAPI** (ADR 0001): springdoc-openapi 3.2.0 (pinned). Swagger UI at `/swagger-ui.html`,
  JSON at `/v3/api-docs`. Annotations on `TaskController`; each operation declares only the errors it can
  return (`springdoc.override-with-generic-response=false`); example error bodies in `OpenApiExamples`
- **Stage 3 — Testing** (ADR 0002), 96 tests:
  - `*Test` = unit and web-slice tests, run by **surefire** (`mvnw.cmd test`, no Docker):
    `TaskServiceTest` (Mockito), `TaskControllerTest` (`@WebMvcTest` + `@MockitoBean`)
  - `*IT` = integration tests, run by **failsafe** in `mvnw.cmd verify` (needs Docker):
    `TaskSpecificationsIT` (`@DataJpaTest`), `TaskApiIT`, `FlywayMigrationIT`, `OpenApiDocumentationIT`,
    `TaskapiApplicationIT`
  - **Testcontainers**: `TestcontainersConfiguration` (`@Bean @ServiceConnection` `postgres:17`), imported
    with `@Import`. Tests never use `task-postgres` or `.env`. API tests are not `@Transactional` (real
    commits); they clean up with `deleteAll()` in the container only
  - **JaCoCo** 0.8.16 (pinned): one agent for both phases via `@{argLine}`, report at
    `target/site/jacoco/index.html` (use `clean verify`). No coverage gate yet (Stage 8)
  - Surefire and failsafe `argLine` = `@{argLine} -javaagent:<mockito> -Duser.timezone=Asia/Kolkata`.
    IntelliJ JUnit runs need the VM option `-Duser.timezone=Asia/Kolkata` (see README)
- **Stage 4 — Security** (ADR 0003 Problem Details, ADR 0004 auth design), 186 tests:
  - `POST /auth/register` (201; 409 taken email; password 8–72 chars and ≤ 72 bytes), `/auth/login`,
    `/auth/refresh`, `/auth/logout` (204). All `/auth/**`, Swagger and `/error` are public; everything else
    needs `Authorization: Bearer <JWT>`. Stateless, CSRF off
  - Passwords: `BCryptPasswordEncoder`; login via `AuthenticationManager` + `DaoAuthenticationProvider`
    (`AuthenticationConfig`, `DatabaseUserDetailsService`, `UserPrincipal`). Emails stored lower-cased
  - Access token: HS256 JWT, 15 min, `sub` = user id, issued by `JwtTokenService` (`NimbusJwtEncoder`),
    validated by the Resource Server `NimbusJwtDecoder` (HS256 only, exp, issuer) in `SecurityConfig`
  - Refresh token: 32 random bytes, Base64URL, 7 days, stored as SHA-256 only (`RefreshTokenService`).
    Rotation in a family; reuse of a revoked token revokes the family (`noRollbackFor`); row locked with
    `PESSIMISTIC_WRITE` during rotation
  - Ownership: controller reads the user id with `@AuthenticationPrincipal Jwt`; every `TaskService` method
    takes `ownerId`; lookups via `findByIdAndOwnerId` and the `ownedBy` Specification → another user's task is 404
  - 401/403 from the filter chain: `ProblemDetailsAuthenticationEntryPoint` / `ProblemDetailsAccessDeniedHandler`
    keep the `WWW-Authenticate` header and delegate the body to `GlobalExceptionHandler`
  - Rate limiting: `AuthRateLimitFilter` (Bucket4j 8.21.0 pinned + Caffeine), per IP and endpoint: login 5,
    register 3, refresh 10 per minute (`app.security.rate-limit.*`) → 429 + `Retry-After`
  - No roles yet (Stage 5). `Clock` bean in `ClockConfig` for testable expiry
  - Tests: ITs mint tokens with `JwtTokenService` instead of logging in; tests that call `/auth` use
    `TestClientIp.uniqueClientIp()` so per-IP limits never leak between tests
- Git + GitHub (`TanmayGiramkar/task-management-api`), `.gitignore` covers `.env`, `target/`,
  `target_test-classes/`, `*.log`

### Error response contract (keep this shape for every new error)
Every error is **RFC 9457 Problem Details** (Spring's `ProblemDetail`), `Content-Type: application/problem+json`,
built in `GlobalExceptionHandler` (ADR 0003):
```json
{ "title": "Not Found", "status": 404, "detail": "...", "instance": "/tasks/42", "timestamp": "..." }
```
`type` is omitted (= `about:blank`), so `title` is the HTTP status phrase; `instance` is the request path,
filled in by Spring. Validation errors add `"errors": { "field": "message" }` with `"detail": "Validation failed"`.
New errors: add an `@ExceptionHandler` that returns `problem(status, detail)`; never a custom error class.

### Package layout (`com.example.taskapi`)
`config`, `controller`, `dto`, `exception`, `model`, `repository`, `security`, `service`, `specification`.
Test helpers live in `support` (test sources). Constructor injection only. Controllers stay thin; logic lives in services.

### Known gaps
- `spring.jpa.show-sql=true` should move to a dev-only profile.
- `%` and `_` in `search` act as LIKE wildcards (bound parameters, so not injection). Optional fix.
- `tasks.owner_id` is nullable: pre-Stage 4 local tasks (IDs 10–15) have no owner until the owner claims them
  with an `UPDATE`. Then add a `V6` migration with `SET NOT NULL`. No index on `owner_id` yet (Stage 6, with `EXPLAIN ANALYZE`).
- Access tokens cannot be revoked before they expire (≤ 15 min); logout only revokes the refresh token.
- Revoked/expired refresh tokens are never deleted; add a scheduled clean-up when needed.
- Rate limits are in memory (per instance) and keyed by `getRemoteAddr()`; needs Redis for several instances
  (Stage 6) and forwarded-header handling behind a proxy (Stage 8).

## 5. Roadmap

Work **one stage at a time**, in this order. Each item below is also an acceptance target.

### Project 1 — Team Task Management Platform (pure Java backend)
| Stage | Scope |
|---|---|
| 2. Swagger | springdoc-openapi; document endpoints, params, schemas and error responses |
| 3. Testing | JUnit 5 + Mockito service tests, `@WebMvcTest` controller tests, Testcontainers integration tests on real PostgreSQL, JaCoCo report |
| 4. Security | `users` table via Flyway; BCrypt; JWT access tokens + refresh-token rotation and revocation; each user sees only their own tasks (return 404, not 403, for others' resources); security tests for cross-user access; rate limiting on auth endpoints |
| 5. Domain | Teams, projects, members; **role-based authorization with team roles (owner / admin / member)** — Stage 4 deliberately has no roles, only per-user ownership; relationships; detect and fix N+1 queries (fetch joins / entity graphs); optimistic locking with `@Version` → 409; idempotency keys on POST |
| 6. Performance | Redis caching with explicit invalidation; indexes justified with `EXPLAIN ANALYZE`; k6 load tests with recorded before/after numbers |
| 7. Docker | Multi-stage, non-root image; Docker Compose for app + PostgreSQL + Redis with health checks, named volumes and `.env` |
| 8. Jenkins + Cloud | Jenkins in Docker; declarative `Jenkinsfile`: build → unit tests → Testcontainers tests → JaCoCo coverage gate → Trivy scan → push image → deploy; credentials store for secrets; webhook trigger; HTTPS deployment |
| 9. Observability | Actuator, structured JSON logs, Micrometer/Prometheus metrics, Grafana dashboard |

### Project 2 — Event-Driven Microservices Platform (Java backend + DevOps)
Starts only after Project 1 is deployed. Add a **Notification Service**: Kafka with the transactional
outbox pattern in the task service, idempotent consumers, retry and dead-letter topics, WebSockets
(STOMP), Spring Cloud Gateway with JWT validation at the edge, Resilience4j, database per service,
contract tests, distributed tracing (Micrometer Tracing + Zipkin). Then Terraform (AWS), Kubernetes
with Helm, GitOps with ArgoCD, HPA validated with k6, Prometheus + Grafana + Loki.
Repository layout (new repo vs. multi-module) is decided with the owner and recorded in an ADR.

## 6. How to work on a stage

1. **Plan first.** Read the relevant code, then propose a plan: files to add/change, dependencies,
   migrations, tests, risks. **Wait for approval before writing code.**
2. Create the stage branch from an up-to-date `main`.
3. Implement in small, logical commits with clear messages
   (e.g. `Add Testcontainers integration tests for task filtering`).
4. Run `mvnw.cmd verify` until green.
5. Finish with a **stage report**:
   - what changed and why (short)
   - new dependencies and why
   - **manual checks for the owner**: exact Postman requests (method, URL, body) with expected
     status codes and responses
   - an ADR in `docs/adr/NNNN-title.md` for each significant decision (context, decision, alternatives, consequences)
   - README updates if behaviour or setup changed
   - **5 interview questions** about this stage, with short model answers
6. Do not start the next stage until the owner confirms.

## 7. Style

- Java 21 features where they help readability (records for DTOs, `var` sparingly, switch expressions).
- Meaningful names; no commented-out code; no `System.out.println` (use SLF4J).
- Never log request bodies that could contain passwords or tokens.
- Keep `README.md` accurate: setup, how to run, how to test, API overview, architecture diagram (later).
