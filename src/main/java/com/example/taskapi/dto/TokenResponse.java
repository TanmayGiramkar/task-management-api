package com.example.taskapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Tokens returned by login and refresh")
public record TokenResponse(
        @Schema(description = "JWT to send as 'Authorization: Bearer <token>' on every /tasks request")
        String accessToken,
        @Schema(description = "Opaque token for POST /auth/refresh and POST /auth/logout. Single use: "
                + "every refresh returns a new one")
        String refreshToken,
        @Schema(description = "Always \"Bearer\"", example = "Bearer")
        String tokenType,
        @Schema(description = "Seconds until the access token expires", example = "900")
        long expiresIn
) {

    public static TokenResponse bearer(String accessToken, String refreshToken, long expiresIn) {
        return new TokenResponse(accessToken, refreshToken, "Bearer", expiresIn);
    }

    /** Keeps the tokens out of logs and debugger output. */
    @Override
    public String toString() {
        return "TokenResponse[accessToken=****, refreshToken=****, tokenType=" + tokenType
                + ", expiresIn=" + expiresIn + "]";
    }
}
