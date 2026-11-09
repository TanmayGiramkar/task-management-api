package com.example.taskapi.config;

/**
 * Example Problem Details bodies shown in Swagger UI. They match what GlobalExceptionHandler returns.
 */
public final class OpenApiExamples {

    public static final String VALIDATION_FAILED = """
            {
              "title": "Bad Request",
              "status": 400,
              "detail": "Validation failed",
              "instance": "/tasks",
              "errors": { "title": "Title is required" },
              "timestamp": "2026-11-04T17:01:34.886"
            }
            """;

    public static final String MALFORMED_BODY = """
            {
              "title": "Bad Request",
              "status": 400,
              "detail": "Request body is malformed or contains invalid values",
              "instance": "/tasks",
              "timestamp": "2026-11-04T17:01:34.886"
            }
            """;

    public static final String INVALID_ID = """
            {
              "title": "Bad Request",
              "status": 400,
              "detail": "Invalid value 'abc' for parameter 'id'",
              "instance": "/tasks/abc",
              "timestamp": "2026-11-04T17:01:34.484"
            }
            """;

    public static final String INVALID_PAGE_SIZE = """
            {
              "title": "Bad Request",
              "status": 400,
              "detail": "Page size must be between 1 and 50",
              "instance": "/tasks",
              "timestamp": "2026-11-04T17:01:34.534"
            }
            """;

    public static final String INVALID_STATUS = """
            {
              "title": "Bad Request",
              "status": 400,
              "detail": "Invalid value 'DONE' for parameter 'status'. Allowed values: [TODO, IN_PROGRESS, COMPLETED]",
              "instance": "/tasks",
              "timestamp": "2026-11-04T17:01:34.588"
            }
            """;

    public static final String TASK_NOT_FOUND = """
            {
              "title": "Not Found",
              "status": 404,
              "detail": "Task not found with id: 42",
              "instance": "/tasks/42",
              "timestamp": "2026-11-04T17:01:34.431"
            }
            """;

    public static final String INTERNAL_ERROR = """
            {
              "title": "Internal Server Error",
              "status": 500,
              "detail": "An unexpected error occurred",
              "instance": "/tasks/42",
              "timestamp": "2026-11-04T17:01:34.431"
            }
            """;

    private OpenApiExamples() {
        // Constants only: not meant to be instantiated
    }
}
