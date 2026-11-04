package com.example.taskapi.dto;

import com.example.taskapi.model.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Body for creating (POST) or replacing (PUT) a task")
public class TaskRequest {

    @Schema(description = "Short name of the task. Must not be blank", example = "Write project report")
    @NotBlank(message = "Title is required")
    @Size(max = 100, message = "Title must not exceed 100 characters")
    private String title;

    @Schema(description = "Optional details. On PUT, omitting it clears the current description",
            example = "Summarise the Stage 2 changes for review")
    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    @Schema(description = "Defaults to TODO on create. On PUT, omitting it keeps the current status",
            example = "TODO")
    private TaskStatus status;

    public TaskRequest() {
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }
}