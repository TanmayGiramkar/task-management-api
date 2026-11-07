# Task Management API

A REST API for managing tasks, built with Java 21, Spring Boot 4 and PostgreSQL.
It supports create, read, replace, partial update and delete, plus filtering, search, sorting and pagination.

## Tech stack

| Area | Choice |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.1.1 (Spring MVC, Spring Data JPA, Bean Validation) |
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

Database credentials are read from a `.env` file in the project root. It is ignored by Git, so never commit it.

```properties
DB_USERNAME=<username>
DB_PASSWORD=<password>
```

`application.properties` has no default credentials, so the app fails at startup if they are missing.
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
| Unit | `TaskServiceTest` | Service rules with a mocked repository |
| Web | `TaskControllerTest` | Status codes, JSON, validation messages, error responses |
| JPA | `TaskSpecificationsIT` | Filtering and search SQL on real PostgreSQL |
| End to end | `TaskApiIT`, `FlywayMigrationIT` | Full request path with real commits; migrations on an empty database |

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

## Endpoints

| Method | Path | Description | Success |
|---|---|---|---|
| POST | `/tasks` | Create a task | 201 |
| GET | `/tasks` | List tasks. Query parameters: `status`, `search`, `sortBy`, `direction`, `page`, `size` | 200 |
| GET | `/tasks/{id}` | Get a task | 200 |
| PUT | `/tasks/{id}` | Replace a task. An omitted `description` is cleared; an omitted `status` is kept | 200 |
| PATCH | `/tasks/{id}` | Update only the fields sent | 200 |
| DELETE | `/tasks/{id}` | Delete a task | 204 |

`GET /tasks` rules: `sortBy` is one of `id, title, status, createdAt, updatedAt` (default `id`),
`direction` is `asc` or `desc`, `page` starts at 0, and `size` is 1–50 (default 10).

## Errors

Every error has the same shape:

```json
{ "status": 404, "message": "Task not found with id: 42", "timestamp": "2026-11-04T17:01:34.431" }
```

Validation errors return 400 with `"message": "Validation failed"` and an `errors` map of field name to message.
Status codes used: 400, 404, 405 and 500. Stack traces are never returned.

## Project layout

```
src/main/java/com/example/taskapi
├── config         OpenAPI configuration
├── controller     REST endpoints
├── dto            Request and response objects
├── exception      Custom exceptions and the global error handler
├── model          JPA entities
├── repository     Spring Data repositories
├── service        Business logic
└── specification  JPA Specifications for filtering and search
```

Architecture decisions are recorded in [`docs/adr`](docs/adr).
