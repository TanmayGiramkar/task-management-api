package com.example.taskapi.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    // Version of the API contract, not of the Maven build. Bump it when the contract changes.
    private static final String API_VERSION = "2.0.0";

    private static final String DESCRIPTION = """
            REST API for managing tasks: create, list (with filtering, search, sorting and pagination), \
            replace, partially update and delete.

            ## Authentication

            1. `POST /auth/register` to create an account.
            2. `POST /auth/login` to get an `accessToken` (a JWT, valid for 15 minutes).
            3. Send it on every `/tasks` request as `Authorization: Bearer <accessToken>`. \
            In Swagger UI, click **Authorize** and paste the token.

            A missing, malformed or expired token gets `401` with a `WWW-Authenticate: Bearer` header.

            ## Error responses

            Every error is an [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) Problem Details body \
            with content type `application/problem+json`:

            ```json
            { "title": "Not Found", "status": 404, "detail": "Task not found with id: 42", \
            "instance": "/tasks/42", "timestamp": "2026-11-04T17:01:34.431" }
            ```

            `type` is omitted, which RFC 9457 defines as `about:blank`: the problem is fully described by \
            the HTTP status, so `title` is the status phrase. `detail` explains this occurrence. \
            Validation errors (400) use `"detail": "Validation failed"` and add `errors`, a map of field name \
            to message.

            | Status | When |
            |---|---|
            | 400 | Failed validation, malformed request body, or an invalid path or query parameter |
            | 401 | Missing, invalid or expired access token, or wrong email or password on login |
            | 404 | No task with the given id, or no endpoint for the requested path |
            | 409 | Registering an email that already has an account |
            | 405 | The HTTP method is not supported for the path, for example `PUT /tasks` |
            | 500 | Unexpected server error. Details are logged on the server and never returned |

            The 405 and the unknown-path 404 come from requests that match no operation, \
            so they are described here rather than under each endpoint.

            ## Timestamps

            `createdAt`, `updatedAt` and error `timestamp` values are local date-times in the \
            Asia/Kolkata time zone, without a UTC offset.
            """;

    static final String BEARER_AUTH = "bearerAuth";

    /**
     * Declares the JWT bearer scheme and requires it on every operation by default; AuthController
     * opts out with an empty {@code @SecurityRequirements}. This is what gives Swagger UI its
     * Authorize button and lock icons.
     */
    @Bean
    public OpenAPI taskApiOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Task Management API")
                        .version(API_VERSION)
                        .description(DESCRIPTION)
                        .contact(new Contact()
                                .name("Tanmay Giramkar")
                                .url("https://github.com/TanmayGiramkar/task-management-api")))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Access token from POST /auth/login")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }

    /**
     * springdoc documents Spring's {@link org.springframework.http.ProblemDetail} with a nested
     * {@code properties} object, but Spring writes those extension members at the top level of the JSON.
     * This replaces that member with the extensions the API actually returns.
     */
    @Bean
    public OpenApiCustomizer problemDetailSchemaCustomizer() {
        return openApi -> {
            Schema<?> problem = openApi.getComponents().getSchemas().get("ProblemDetail");
            if (problem == null) {
                return;
            }
            problem.description("RFC 9457 Problem Details, returned as application/problem+json");
            problem.getProperties().remove("properties");
            problem.addProperty("timestamp", new StringSchema()
                    .format("date-time")
                    .description("When the error occurred. Local date-time in Asia/Kolkata, without a UTC offset"));
            problem.addProperty("errors", new MapSchema()
                    .additionalProperties(new StringSchema())
                    .description("Validation errors only: each invalid field mapped to its message"));
        };
    }
}
