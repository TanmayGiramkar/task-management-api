# 0003. Return every error as RFC 9457 Problem Details

- **Status:** Accepted
- **Date:** 2026-11-09
- **Stage:** 4 (Security)

## Context

Errors used a custom body, `{ "status", "message", "timestamp" }`, built from two records in
`GlobalExceptionHandler`. Stage 4 adds 401, 403 and 429 responses. 401 and 403 come from Spring Security's
filter chain, which runs before any controller, so `@RestControllerAdvice` never sees them; by default
Spring Security answers with an empty body or its own format. We want one error format for every response,
and preferably a standard one that clients and tools already understand.

## Decision

1. **Use RFC 9457 Problem Details** (`application/problem+json`) through Spring's built-in `ProblemDetail`.
   Every `@ExceptionHandler` returns `ProblemDetail`; Spring MVC sets the HTTP status from it, fills
   `instance` with the request path and picks the content type.
2. **Keep `type` as `about:blank`** (omitted in the JSON). The HTTP status already says what kind of problem
   it is, so `title` is the status phrase and `detail` explains this occurrence. Custom `type` URIs would only
   help if clients needed to tell apart problems with the same status, which they do not yet.
3. **Keep the useful extras as extension members:** `timestamp` on every error, `errors` (field → message)
   on validation errors. Spring writes extensions at the top level of the JSON.
4. **Route Spring Security's 401/403 through the same handler.** `ProblemDetailsAuthenticationEntryPoint` and
   `ProblemDetailsAccessDeniedHandler` first let Spring's bearer-token handlers set the status and the
   standard `WWW-Authenticate: Bearer ...` header (RFC 6750), then pass the exception to Spring MVC's
   `HandlerExceptionResolver`, so `GlobalExceptionHandler` writes the body. The rate-limit filter does the
   same for 429.
5. **Correct the OpenAPI schema.** springdoc documents `ProblemDetail` with a nested `properties` object that
   the API never sends; an `OpenApiCustomizer` replaces it with the real `timestamp` and `errors` members.
6. **Bump the API contract version to 2.0.0**, because the error body changed shape.

## Alternatives considered

| Alternative | Why not |
|---|---|
| **Keep the custom error body** | Works, but it is our own invention: every client must learn it, and the security errors would still need custom handlers. The standard costs the same effort. |
| **Extend `ResponseEntityExceptionHandler`** with `spring.mvc.problemdetails.enabled` | Produces Problem Details for Spring MVC's own exceptions automatically, but customising our existing messages (for example "Allowed values" on a bad enum) means overriding several protected methods. Explicit `@ExceptionHandler` methods are shorter and easier to read. |
| **Write the JSON directly in the security handlers** | Two places that format errors instead of one; they would drift. |
| **Custom `type` URIs** (e.g. `/problems/validation-error`) | Useful later if clients need machine-readable problem kinds within one status. Not needed now, and `about:blank` is valid RFC 9457. |

## Consequences

- One format for every error, including 401/403/429 from filters, and every error has the
  `application/problem+json` content type.
- Breaking change for clients of the old format (there are none outside this project yet). Postman requests
  and tests now read `detail` instead of `message`.
- New errors are added as an `@ExceptionHandler` that returns `problem(status, detail)`; CLAUDE.md records this.
