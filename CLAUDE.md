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
- Credentials live in **`.env`** in the project root (`DB_USERNAME`, `DB_PASSWORD`), loaded by
  `spring.config.import=optional:file:.env[.properties]`. The path is relative to the working
  directory, so always run the app from the project root.
- `application.properties` references `${DB_USERNAME}` / `${DB_PASSWORD}` with no defaults (fail fast).
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
  ran there), `V2__fix_task_column_lengths.sql` (title 100, description 500).
  `spring.jpa.hibernate.ddl-auto=validate`. **The next migration is V3.**
- Git + GitHub (`TanmayGiramkar/task-management-api`), `.gitignore` covers `.env`, `target/`,
  `target_test-classes/`, `*.log`

### Error response contract (keep this shape for every new error)
```json
{ "status": 400, "message": "...", "timestamp": "..." }
```
Validation errors add `"errors": { "field": "message" }` with `"message": "Validation failed"`.

### Package layout (`com.example.taskapi`)
`controller`, `dto`, `exception`, `model`, `repository`, `service`, `specification`.
Constructor injection only. Controllers stay thin; logic lives in services.

### Known gaps
- The only test, `TaskapiApplicationTests.contextLoads`, needs a live database. Replace or back it
  with Testcontainers in Stage 3 so the build runs anywhere (Jenkins included).
- `spring.jpa.show-sql=true` should move to a dev-only profile.
- `%` and `_` in `search` act as LIKE wildcards (bound parameters, so not injection). Optional fix.

## 5. Roadmap

Work **one stage at a time**, in this order. Each item below is also an acceptance target.

### Project 1 — Team Task Management Platform (pure Java backend)
| Stage | Scope |
|---|---|
| 2. Swagger | springdoc-openapi; document endpoints, params, schemas and error responses |
| 3. Testing | JUnit 5 + Mockito service tests, `@WebMvcTest` controller tests, Testcontainers integration tests on real PostgreSQL, JaCoCo report |
| 4. Security | `users` table via Flyway; BCrypt; JWT access tokens + refresh-token rotation and revocation; each user sees only their own tasks (return 404, not 403, for others' resources); security tests for cross-user access; rate limiting on auth endpoints |
| 5. Domain | Teams, projects, members, roles; relationships; detect and fix N+1 queries (fetch joins / entity graphs); optimistic locking with `@Version` → 409; idempotency keys on POST |
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
