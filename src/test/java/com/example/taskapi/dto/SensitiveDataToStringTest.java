package com.example.taskapi.dto;

import com.example.taskapi.security.UserPrincipal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Records generate a toString() with every field, so a log line or exception message containing one of
 * these objects would leak a password, token or hash. Each overrides toString(); this checks they hide it.
 */
class SensitiveDataToStringTest {

    @Test
    void requestsHidePasswordsAndTokens() {
        assertThat(new RegisterRequest("tanmay@example.com", "secret-password").toString())
                .contains("tanmay@example.com").doesNotContain("secret-password");
        assertThat(new LoginRequest("tanmay@example.com", "secret-password").toString())
                .contains("tanmay@example.com").doesNotContain("secret-password");
        assertThat(new RefreshTokenRequest("secret-refresh-token").toString())
                .doesNotContain("secret-refresh-token");
    }

    @Test
    void tokenResponseHidesBothTokens() {
        assertThat(TokenResponse.bearer("secret-access-token", "secret-refresh-token", 900).toString())
                .doesNotContain("secret-access-token", "secret-refresh-token")
                .contains("expiresIn=900");
    }

    @Test
    void userPrincipalHidesThePasswordHash() {
        assertThat(new UserPrincipal(7L, "tanmay@example.com", "$2a$10$secrethash").toString())
                .contains("tanmay@example.com").doesNotContain("$2a$10$secrethash");
    }
}
