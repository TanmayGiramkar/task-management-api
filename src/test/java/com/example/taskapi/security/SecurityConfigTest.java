package com.example.taskapi.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The app must refuse to start with a missing, malformed or too-short JWT secret. */
class SecurityConfigTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void rejectsAMissingSecret(String secret) {
        assertThatThrownBy(() -> SecurityConfig.signingKey(secret))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void rejectsASecretThatIsNotBase64() {
        assertThatThrownBy(() -> SecurityConfig.signingKey("not base64 !!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JWT_SECRET must be Base64-encoded");
    }

    @Test
    void rejectsASecretShorterThan32Bytes() {
        String shortSecret = Base64.getEncoder().encodeToString(new byte[31]);

        assertThatThrownBy(() -> SecurityConfig.signingKey(shortSecret))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    void acceptsA32ByteSecretAsAnHmacSha256Key() {
        String secret = Base64.getEncoder().encodeToString(new byte[32]);

        assertThat(SecurityConfig.signingKey(secret).getAlgorithm()).isEqualTo("HmacSHA256");
    }
}
