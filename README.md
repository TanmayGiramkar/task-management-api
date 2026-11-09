# Task Management API

A REST API for managing tasks, built with Java 21, Spring Boot 4 and PostgreSQL.
It supports create, read, replace, partial update and delete, plus filtering, search, sorting and pagination.
Users register and log in with JWT access tokens and rotating refresh tokens, and each user sees only their own tasks.

## Tech stack

| Area | Choice |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.1.1 (Spring MVC, Spring Data JPA, Bean Validation) |
| Security | Spring Security, OAuth2 Resource Server (JWT, HS256), BCrypt, Bucket4j rate limiting |
| Database | PostgreSQL 17, schema managed by Flyway |
| API docs | springdoc-openapi (OpenAPI 3.1 + Swagger UI) |
| Testing | JUnit 5, Mockito, Spring MVC test slices, Testcontainers (PostgreSQL), JaCoCo |
| Build | Maven, through the wrapper (`mvnw` / `mvnw.cmd`) |

## Prerequisites

- JDK 21
- Docker. The tests start their own PostgreSQL container. To run the app you also need a PostgreSQL 17
  container on port 5432 with a database named `taskdb`. To create one (choose your own username and password):

  ```bash
  docker run --name task-postgres -e POSTGRES_USER=<username> -e POSTGRES_PASSWORD=<password> -e POSTGRES_DB=taskdb -p 5432:5432 -d postgres:17
  ```

  If the container already exists, start it with `docker start task-postgres`.

## Configuration

Secrets are read from a `.env` file in the project root. It is ignored by Git, so never commit it.

```properties
DB_USERNAME=<username>
DB_PASSWORD=<password>
JWT_SECRET=<Base64 of at least 32 random bytes>
```

`JWT_SECRET` signs the access tokens. Generate one with either command and paste the output into `.env`:

```bash
openssl rand -base64 48
```

```powershell
$b = New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)
```

`application.properties` has no default secrets, so the app fails at startup if any is missing.
Run the app from the project root so `.env` is found.

## Run

```bash
./mvnw spring-boot:run
```

On Windows PowerShell use `mvnw.cmd spring-boot:run`. The API starts on `http://localhost:8080`.
Flyway applies any pending migrations from `src/main/resources/db/migration` at startup.

## Test

| Command | Runs | Needs |
|---|---|---|
| `./mvnw test` | Unit and web-layer tests (`*Test`) | JDK only |
| `./mvnw clean verify` | All tests, including integration tests (`*IT`), plus the coverage report | Docker running |

On Windows PowerShell use `mvnw.cmd` instead of `./mvnw`.

Integration tests start a throwaway PostgreSQL 17 container with Testcontainers and build the schema with
Flyway from V1. They never touch the local `task-postgres` database and do not need `.env`.
The first run downloads the `postgres:17` and `testcontainers/ryuk` images.

Coverage report (from both test phases): `target/site/jacoco/index.html`.

| Layer | Example class | What it checks |
|---|---|---|
| Unit | `TaskServiceTest`, `AuthServiceTest`, `RefreshTokenServiceTest` | Service rules with mocked repositories |
| Web | `TaskControllerTest`, `AuthControllerTest` | Status codes, JSON, validation messages, 401/403 responses |
| JPA | `TaskSpecificationsIT` | Owner filter, status filter and search SQL on real PostgreSQL |
| End to end | `TaskApiIT`, `AuthenticationIT`, `RefreshTokenIT`, `TaskOwnershipIT`, `AuthRateLimitIT`, `FlywayMigrationIT` | Full request path with real commits, real tokens and the real security filter chain |

Tests generate a random JWT signing key on every run (`src/test/resources/config/application.properties`),
so no key is stored in the repository.

See [ADR 0002](docs/adr/0002-test-strategy-with-testcontainers.md) for the reasoning.

### Running tests in IntelliJ IDEA

Maven passes `-Duser.timezone=Asia/Kolkata` to the test JVMs. IntelliJ's own JUnit runner may not, and on
Windows Java then reports the zone as `Asia/Calcutta`, which PostgreSQL 17 rejects when connecting.
Add the option once to the JUnit template:

1. **Run → Edit Configurations… → Edit configuration templates… → JUnit**
2. In **VM options**, add `-Duser.timezone=Asia/Kolkata` (if the field is hidden: **Modify options → Add VM options**)
3. Delete any JUnit run configurations created before this change, so they are recreated from the template.

Integration tests also need Docker Desktop to be running.

## API documentation

With the app running:

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs
- OpenAPI YAML: http://localhost:8080/v3/api-docs.yaml

## Authentication

1. `POST /auth/register` with `{"email", "password"}` creates an account (password: 8–72 characters, stored as a BCrypt hash).
2. `POST /auth/login` returns an `accessToken` (JWT, 15 minutes) and a `refreshToken` (7 days).
3. Send `Authorization: Bearer <accessToken>` on every `/tasks` request. In Swagger UI, use **Authorize**.
4. `POST /auth/refresh` with `{"refreshToken"}` returns a new pair. Each refresh token works once; reusing an old one
   logs out every session from that login.
5. `POST /auth/logout` with `{"refreshToken"}` revokes it. The access token still works until it expires.

Login, register and refresh are limited per client IP (5, 3 and 10 requests per minute); over the limit returns 429
with `Retry-After`. Design and trade-offs: [ADR 0004](docs/adr/0004-jwt-access-tokens-refresh-rotation-and-rate-limiting.md).

## Endpoints

| Method | Path | Description | Success | Token |
|---|---|---|---|---|
| POST | `/auth/register` | Create an account | 201 | No |
| POST | `/auth/login` | Get an access token and a refresh token | 200 | No |
| POST | `/auth/refresh` | Exchange a refresh token for a new pair | 200 | No |
| POST | `/auth/logout` | Revoke a refresh token | 204 | No |
| POST | `/tasks` | Create a task | 201 | Yes |
| GET | `/tasks` | List your tasks. Query parameters: `status`, `search`, `sortBy`, `direction`, `page`, `size` | 200 | Yes |
| GET | `/tasks/{id}` | Get one of your tasks | 200 | Yes |
| PUT | `/tasks/{id}` | Replace a task. An omitted `description` is cleared; an omitted `status` is kept | 200 | Yes |
| PATCH | `/tasks/{id}` | Update only the fields sent | 200 | Yes |
| DELETE | `/tasks/{id}` | Delete a task | 204 | Yes |

Each user sees only their own tasks. Another user's task returns the same 404 as a task that does not exist.

`GET /tasks` rules: `sortBy` is one of `id, title, status, createdAt, updatedAt` (default `id`),
`direction` is `asc` or `desc`, `page` starts at 0, and `size` is 1–50 (default 10).

## Errors

Every error is an [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) Problem Details body,
sent as `application/problem+json`:

```json
{
  "title": "Not Found",
  "status": 404,
  "detail": "Task not found with id: 42",
  "instance": "/tasks/42",
  "timestamp": "2026-11-04T17:01:34.431"
}
```

`type` is omitted, which means `about:blank`: the HTTP status describes the problem and `title` is its phrase.
`detail` explains this occurrence and `instance` is the request path.
Validation errors return 400 with `"detail": "Validation failed"` and an `errors` map of field name to message.
Status codes used: 400, 401, 403, 404, 405, 409, 429 and 500. A 401 also carries a `WWW-Authenticate: Bearer` header.
Stack traces are never returned. See [ADR 0003](docs/adr/0003-rfc-9457-problem-details-for-errors.md).

## Project layout

```
src/main/java/com/example/taskapi
├── config         OpenAPI configuration, Clock
├── controller     REST endpoints
├── dto            Request and response objects
├── exception      Custom exceptions and the global error handler
├── model          JPA entities
├── repository     Spring Data repositories
├── security       Security filter chain, JWT, login, 401/403 handlers, rate limiting
├── service        Business logic
└── specification  JPA Specifications for filtering and search
```

Architecture decisions are recorded in [`docs/adr`](docs/adr).
