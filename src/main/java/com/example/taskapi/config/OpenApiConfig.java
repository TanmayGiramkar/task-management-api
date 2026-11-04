package com.example.taskapi.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    // Version of the API contract, not of the Maven build. Bump it when the contract changes.
    private static final String API_VERSION = "1.0.0";

    private static final String DESCRIPTION = """
            REST API for managing tasks: create, list (with filtering, search, sorting and pagination), \
            replace, partially update and delete.

            ## Error responses

            Every error uses the same JSON shape:

            ```json
            { "status": 404, "message": "Task not found with id: 42", "timestamp": "2026-11-04T17:01:34.431" }
            ```

            Validation errors (400) use `"message": "Validation failed"` and add `errors`, a map of field name to message.

            | Status | When |
            |---|---|
            | 400 | Failed validation, malformed request body, or an invalid path or query parameter |
            | 404 | No task with the given id, or no endpoint for the requested path |
            | 405 | The HTTP method is not supported for the path, for example `PUT /tasks` |
            | 500 | Unexpected server error. Details are logged on the server and never returned |

            The 405 and the unknown-path 404 come from requests that match no operation, \
            so they are described here rather than under each endpoint.

            ## Timestamps

            `createdAt`, `updatedAt` and error `timestamp` values are local date-times in the \
            Asia/Kolkata time zone, without a UTC offset.
            """;

    @Bean
    public OpenAPI taskApiOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Task Management API")
                        .version(API_VERSION)
                        .description(DESCRIPTION)
                        .contact(new Contact()
                                .name("Tanmay Giramkar")
                                .url("https://github.com/TanmayGiramkar/task-management-api")));
    }
}
