package com.example.taskapi.dto;

import com.example.taskapi.model.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Body for partially updating a task (PATCH). Send only the fields to change: "
        + "omitted or null fields are left unchanged")
public record TaskPatchRequest(

        @Schema(description = "New title. If sent, must not be blank", example = "Write final project report")
        @Size(max = 100, message = "Title must not exceed 100 characters")
        @Pattern(regexp = ".*\\S.*", message = "Title must not be blank")
        String title,

        @Schema(description = "New description", example = "Add the test results")
        @Size(max = 500, message = "Description must not exceed 500 characters")
        String description,

        @Schema(description = "New status", example = "IN_PROGRESS")
        TaskStatus status
) {
}