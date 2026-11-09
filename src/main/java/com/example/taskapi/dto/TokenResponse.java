package com.example.taskapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Tokens returned by login")
public record TokenResponse(
        @Schema(description = "JWT to send as 'Authorization: Bearer <token>' on every /tasks request")
        String accessToken,
        @Schema(description = "Always \"Bearer\"", example = "Bearer")
        String tokenType,
        @Schema(description = "Seconds until the access token expires", example = "900")
        long expiresIn
) {

    public static TokenResponse bearer(String accessToken, long expiresIn) {
        return new TokenResponse(accessToken, "Bearer", expiresIn);
    }
}
