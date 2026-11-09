package com.example.taskapi.controller;

import com.example.taskapi.dto.TaskRequest;
import com.example.taskapi.dto.TaskResponse;
import com.example.taskapi.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import com.example.taskapi.model.TaskStatus;
import com.example.taskapi.dto.PageResponse;
import com.example.taskapi.dto.TaskPatchRequest;
import com.example.taskapi.config.OpenApiExamples;
import org.springframework.http.ProblemDetail;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE;

@RestController
@RequestMapping("/tasks")
@Tag(name = "Tasks", description = "Create, read, update and delete tasks")
@ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token",
        content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class),
                examples = @ExampleObject(value = OpenApiExamples.UNAUTHORIZED)))
@ApiResponse(responseCode = "500", description = "Unexpected server error. Details are logged, never returned",
        content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class),
                examples = @ExampleObject(value = OpenApiExamples.INTERNAL_ERROR)))
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a task",
            description = "Creates a task. `title` is required. `status` defaults to `TODO` when omitted.")
    @ApiResponse(responseCode = "201", description = "Task created")
    @ApiResponse(responseCode = "400", description = "Validation failed, or the request body is malformed",
            content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class),
                    examples = {
                            @ExampleObject(name = "Validation failed",
                                    value = OpenApiExamples.VALIDATION_FAILED),
                            @ExampleObject(name = "Malformed body",
                                    value = OpenApiExamples.MALFORMED_BODY)
                    }))
    public TaskResponse createTask(@Valid @RequestBody TaskRequest request) {
        return taskService.createTask(request);
    }

    @GetMapping
    @Operation(summary = "List tasks",
            description = """
                    Returns one page of tasks. Filter by `status`, search the title and description with `search`, \
                    and order with `sortBy` and `direction`. Tasks with equal sort values are ordered by `id`, \
                    so pages are stable. The maximum page size is 50.""")
    @ApiResponse(responseCode = "200", description = "One page of tasks")
    @ApiResponse(responseCode = "400", description = "Invalid query parameter",
            content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class),
                    examples = {
                            @ExampleObject(name = "Invalid page size",
                                    value = OpenApiExamples.INVALID_PAGE_SIZE),
                            @ExampleObject(name = "Invalid status",
                                    value = OpenApiExamples.INVALID_STATUS)
                    }))
    public PageResponse<TaskResponse> getAllTasks(
            @Parameter(description = "Return only tasks with this status")
            @RequestParam(required = false) TaskStatus status,
            @Parameter(description = "Case-insensitive text to find in the title or description", example = "report")
            @RequestParam(required = false) String search,
            @Parameter(description = "Field to sort by. Defaults to `id`",
                    schema = @Schema(allowableValues = {"id", "title", "status", "createdAt", "updatedAt"}))
            @RequestParam(required = false) String sortBy,
            @Parameter(description = "Sort direction, case-insensitive",
                    schema = @Schema(allowableValues = {"asc", "desc"}, defaultValue = "asc"))
            @RequestParam(required = false, defaultValue = "asc") String direction,
            @Parameter(description = "Zero-based page index",
                    schema = @Schema(type = "integer", format = "int32", minimum = "0", defaultValue = "0"))
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Number of tasks per page",
                    schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "50", defaultValue = "10"))
            @RequestParam(defaultValue = "10") int size) {

        return taskService.getAllTasks(status, search, sortBy, direction, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a task by id")
    @ApiResponse(responseCode = "200", description = "The task")
    @ApiResponse(responseCode = "400", description = "The id is not a number",
            content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = OpenApiExamples.INVALID_ID)))
    @ApiResponse(responseCode = "404", description = "No task with this id",
            content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = OpenApiExamples.TASK_NOT_FOUND)))
    public TaskResponse getTaskById(@Parameter(description = "Task id", example = "10") @PathVariable Long id) {
        return taskService.getTaskById(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace a task",
            description = """
                    Replaces the task with the values sent. Fields you omit are not kept: an omitted \
                    `description` is cleared (set to null). This is the difference from PATCH, which leaves \
                    omitted fields unchanged. `title` is required. One exception: if `status` is omitted, \
                    the current status is kept.""")
    @ApiResponse(responseCode = "200", description = "The replaced task")
    @ApiResponse(responseCode = "400", description = "Validation failed, the request body is malformed, or the id is not a number",
            content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class),
                    examples = {
                            @ExampleObject(name = "Validation failed",
                                    value = OpenApiExamples.VALIDATION_FAILED),
                            @ExampleObject(name = "Malformed body",
                                    value = OpenApiExamples.MALFORMED_BODY),
                            @ExampleObject(name = "Invalid id",
                                    value = OpenApiExamples.INVALID_ID)
                    }))
    @ApiResponse(responseCode = "404", description = "No task with this id",
            content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = OpenApiExamples.TASK_NOT_FOUND)))
    public TaskResponse updateTask(
            @Parameter(description = "Task id", example = "10") @PathVariable Long id,
            @Valid @RequestBody TaskRequest request) {

        return taskService.updateTask(id, request);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Partially update a task",
            description = """
                    Updates only the fields sent. Omitted or null fields are left unchanged, unlike PUT. \
                    A `title`, if sent, must not be blank.""")
    @ApiResponse(responseCode = "200", description = "The updated task")
    @ApiResponse(responseCode = "400", description = "Validation failed, the request body is malformed, or the id is not a number",
            content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class),
                    examples = {
                            @ExampleObject(name = "Validation failed",
                                    value = OpenApiExamples.VALIDATION_FAILED),
                            @ExampleObject(name = "Malformed body",
                                    value = OpenApiExamples.MALFORMED_BODY),
                            @ExampleObject(name = "Invalid id",
                                    value = OpenApiExamples.INVALID_ID)
                    }))
    @ApiResponse(responseCode = "404", description = "No task with this id",
            content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = OpenApiExamples.TASK_NOT_FOUND)))
    public TaskResponse patchTask(
            @Parameter(description = "Task id", example = "10") @PathVariable Long id,
            @Valid @RequestBody TaskPatchRequest request) {

        return taskService.patchTask(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a task", description = "Permanently deletes the task.")
    @ApiResponse(responseCode = "204", description = "Task deleted. No response body")
    @ApiResponse(responseCode = "400", description = "The id is not a number",
            content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = OpenApiExamples.INVALID_ID)))
    @ApiResponse(responseCode = "404", description = "No task with this id",
            content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = OpenApiExamples.TASK_NOT_FOUND)))
    public void deleteTask(@Parameter(description = "Task id", example = "10") @PathVariable Long id) {
        taskService.deleteTask(id);
    }
}