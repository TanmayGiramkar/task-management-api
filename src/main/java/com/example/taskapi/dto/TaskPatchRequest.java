package com.example.taskapi.dto;

import com.example.taskapi.model.TaskStatus;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TaskPatchRequest(

        @Size(max = 100, message = "Title must not exceed 100 characters")
        @Pattern(regexp = ".*\\S.*", message = "Title must not be blank")
        String title,

        @Size(max = 500, message = "Description must not exceed 500 characters")
        String description,

        TaskStatus status
) {
}