package com.example.taskapi.dto;

import com.example.taskapi.model.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "A task as returned by the API")
public class TaskResponse {

    @Schema(description = "Generated id", example = "10")
    private Long id;
    @Schema(example = "Write project report")
    private String title;
    @Schema(description = "May be null", example = "Summarise the Stage 2 changes for review")
    private String description;
    @Schema(example = "TODO")
    private TaskStatus status;
    @Schema(description = "When the task was created. Local date-time in Asia/Kolkata, without a UTC offset",
            example = "2026-11-04T17:01:34.431152")
    private LocalDateTime createdAt;
    @Schema(description = "When the task was last changed. Local date-time in Asia/Kolkata, without a UTC offset",
            example = "2026-11-04T17:01:34.431152")
    private LocalDateTime updatedAt;

    public TaskResponse() {
    }

    public TaskResponse(
            Long id,
            String title,
            String description,
            TaskStatus status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {

        this.id = id;
        this.title = title;
        this.description = description;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}