# 0001. Document the API with springdoc-openapi

- **Status:** Accepted
- **Date:** 2026-11-04
- **Stage:** 2 (Swagger / OpenAPI)

## Context

The API had no machine-readable description. Anyone using it had to read the controller code or ask
how filtering, pagination and errors behave. We want:

- an interactive page where endpoints can be tried without Postman,
- an OpenAPI document that tools can import (Postman, client generators, contract tests later),
- documentation that stays in step with the code as it changes.

The project runs Spring Boot 4.1.1 (Spring Framework 7, Jackson 3).

## Decision

1. **Use `springdoc-openapi-starter-webmvc-ui` 3.2.0.** It reads the Spring MVC mappings, Bean
   Validation annotations and Java types at startup and serves the OpenAPI 3.1 document at
   `/v3/api-docs` and Swagger UI at `/swagger-ui.html`. The 3.x line is the one built for Spring Boot 4;
   3.2.0 is built against Boot 4.1.2, the same minor line as ours. Spring Boot does not manage the
   springdoc version, so it is pinned in the `springdoc.version` Maven property.
2. **Generate the document from the code (code-first), with annotations on the controller.**
   `@Tag`, `@Operation`, `@Parameter` and `@ApiResponse` sit next to the Spring MVC annotations they
   describe. `@Schema` on the DTOs and error records adds descriptions and examples.
3. **List only the errors each operation can return.** By default springdoc copies every
   `@ExceptionHandler` in `GlobalExceptionHandler` onto every operation, so `POST /tasks` would claim it
   can return 404. We set `springdoc.override-with-generic-response=false` and declare each operation's
   error responses explicitly. The 500 response applies to every operation, so it is declared once on the
   controller class.
4. **Describe 405 and the unknown-path 404 in the API description.** These come from requests that
   match no operation, so OpenAPI has no operation to attach them to.
5. **Keep example error bodies in `OpenApiExamples`**, copied from real responses, so the annotations
   stay short and the examples match what the API returns.
6. **Test the generated document** (`OpenApiDocumentationTests`) for the structure we rely on: the
   operations, their response codes, the shared error schemas and the query parameter rules.

## Alternatives considered

| Alternative | Why not |
|---|---|
| **SpringFox** | Unmaintained since 2020; does not support Spring Boot 3 or 4. |
| **Hand-written `openapi.yaml` (design-first)** | Good when the contract is agreed before coding, for example between teams. Here one team owns both sides, and a hand-written file drifts from the code unless a contract test enforces it. |
| **Spring REST Docs** | Builds docs from passing tests, so they cannot drift. But it produces AsciiDoc rather than an interactive UI (an OpenAPI output needs a third-party extension), and every documented detail needs a test first. More effort than this stage justifies. |
| **Annotations on a separate interface (`TaskApi`) that the controller implements** | Keeps the controller shorter, but splits each endpoint across two files. With six endpoints, one file is easier to review and explain. Worth revisiting if the controller grows. |
| **Keep springdoc's generic error responses** | No annotations needed, but every operation would list 404 and 405, which is wrong for most of them. |

## Consequences

- Swagger UI and the OpenAPI JSON/YAML are available whenever the app runs. Existing endpoint
  behaviour and response bodies are unchanged (checked against responses recorded before the change).
- springdoc brings in swagger-core, which uses Jackson 2 internally. Spring MVC still uses Jackson 3
  for our responses.
- Annotations can go out of date when code changes. The schemas, types and validation rules are
  generated from the code, so they stay accurate. The error responses are hand-declared;
  `OpenApiDocumentationTests` catches the most likely drift there.
- The DTOs and error records now import `io.swagger.v3.oas.annotations.media.Schema`, a compile-time
  dependency on the swagger annotations.
- The docs endpoints have no authentication. Stage 4 (security) must explicitly allow
  `/v3/api-docs/**` and `/swagger-ui/**`. Whether to disable them in production
  (`springdoc.api-docs.enabled=false`, `springdoc.swagger-ui.enabled=false`) is decided in Stages 7–8.
- Timestamps are documented as `date-time`, but the API returns local Asia/Kolkata date-times without
  a UTC offset. The descriptions say so. Switching to offset or UTC timestamps would be an API change
  with its own ADR.
