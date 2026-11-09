package com.example.taskapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Body for refreshing tokens or logging out")
public record RefreshTokenRequest(
        @Schema(description = "The refresh token from the last login or refresh")
        @NotBlank(message = "Refresh token is required")
        String refreshToken
) {

    /** Keeps the token out of logs and debugger output. */
    @Override
    public String toString() {
        return "RefreshTokenRequest[refreshToken=****]";
    }
}
