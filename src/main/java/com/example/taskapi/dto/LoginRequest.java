package com.example.taskapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Body for logging in")
public record LoginRequest(

        @Schema(example = "tanmay@example.com")
        @NotBlank(message = "Email is required")
        String email,

        @Schema(example = "correct-horse-battery")
        @NotBlank(message = "Password is required")
        String password
) {

    /** Keeps the password out of logs and debugger output. */
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=****]";
    }
}
