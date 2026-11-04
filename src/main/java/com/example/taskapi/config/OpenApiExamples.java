package com.example.taskapi.config;

/**
 * Example error bodies shown in Swagger UI. The messages match what GlobalExceptionHandler returns.
 */
public final class OpenApiExamples {

    public static final String VALIDATION_FAILED = """
            {
              "status": 400,
              "message": "Validation failed",
              "errors": { "title": "Title is required" },
              "timestamp": "2026-11-04T17:01:34.886"
            }
            """;

    public static final String MALFORMED_BODY = """
            {
              "status": 400,
              "message": "Request body is malformed or contains invalid values",
              "timestamp": "2026-11-04T17:01:34.886"
            }
            """;

    public static final String INVALID_ID = """
            {
              "status": 400,
              "message": "Invalid value 'abc' for parameter 'id'",
              "timestamp": "2026-11-04T17:01:34.484"
            }
            """;

    public static final String INVALID_PAGE_SIZE = """
            {
              "status": 400,
              "message": "Page size must be between 1 and 50",
              "timestamp": "2026-11-04T17:01:34.534"
            }
            """;

    public static final String INVALID_STATUS = """
            {
              "status": 400,
              "message": "Invalid value 'DONE' for parameter 'status'. Allowed values: [TODO, IN_PROGRESS, COMPLETED]",
              "timestamp": "2026-11-04T17:01:34.588"
            }
            """;

    public static final String TASK_NOT_FOUND = """
            {
              "status": 404,
              "message": "Task not found with id: 42",
              "timestamp": "2026-11-04T17:01:34.431"
            }
            """;

    public static final String INTERNAL_ERROR = """
            {
              "status": 500,
              "message": "An unexpected error occurred",
              "timestamp": "2026-11-04T17:01:34.431"
            }
            """;

    private OpenApiExamples() {
        // Constants only: not meant to be instantiated
    }
}
