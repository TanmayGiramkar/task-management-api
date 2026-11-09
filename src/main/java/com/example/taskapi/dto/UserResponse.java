package com.example.taskapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "A registered account")
public record UserResponse(
        @Schema(description = "Generated id", example = "1")
        Long id,
        @Schema(description = "Lower-cased login email", example = "tanmay@example.com")
        String email,
        @Schema(description = "When the account was created. Local date-time in Asia/Kolkata, without a UTC offset",
                example = "2026-11-09T14:05:12.123456")
        LocalDateTime createdAt
) {
}
