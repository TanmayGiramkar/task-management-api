package com.example.taskapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Body for creating an account")
public record RegisterRequest(

        @Schema(description = "Login email. Stored lower-cased, so it is case-insensitive",
                example = "tanmay@example.com")
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        @Size(max = 254, message = "Email must not exceed 254 characters")
        String email,

        @Schema(description = "8 to 72 characters, and at most 72 bytes in UTF-8 (the BCrypt limit)",
                example = "correct-horse-battery")
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters")
        String password
) {

    /** Keeps the password out of logs and debugger output. */
    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + ", password=****]";
    }
}
