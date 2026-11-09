package com.example.taskapi.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Issues tokens with the real encoder and checks them with the real decoder configuration from
 * {@link SecurityConfig}, using a fixed clock so expiry is exact.
 */
class JwtTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-09T08:00:00Z");
    private static final String ISSUER = "task-management-api";

    private final SecretKey key = SecurityConfig.signingKey(randomSecret());
    private final JwtProperties properties = new JwtProperties(null, ISSUER, Duration.ofMinutes(15));
    private final JwtEncoder encoder = new SecurityConfig().jwtEncoder(key);
    private final JwtTokenService tokenService =
            new JwtTokenService(encoder, properties, Clock.fixed(NOW, ZoneOffset.UTC));

    private static String randomSecret() {
        byte[] bytes = new byte[32];
        new java.security.SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * The production decoder (HS256, our key), with the same checks as production (expiry and issuer)
     * but reading the given time instead of the system clock, and without clock skew.
     */
    private JwtDecoder decoderAt(Instant time) {
        NimbusJwtDecoder decoder = (NimbusJwtDecoder) new SecurityConfig().jwtDecoder(key, properties);
        JwtTimestampValidator timestamps = new JwtTimestampValidator(Duration.ZERO);
        timestamps.setClock(Clock.fixed(time, ZoneOffset.UTC));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps, new JwtIssuerValidator(ISSUER)));
        return decoder;
    }

    @Test
    void tokenCarriesUserIdIssuerAndA15MinuteExpiry() {
        Jwt jwt = decoderAt(NOW).decode(tokenService.createAccessToken(42L));

        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo(ISSUER);
        assertThat(jwt.getIssuedAt()).isEqualTo(NOW);
        assertThat(jwt.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256").containsEntry("typ", "JWT");
        assertThat(tokenService.accessTokenTtlSeconds()).isEqualTo(900);
    }

    @Test
    void tokenIsRejectedOnceExpired() {
        String token = tokenService.createAccessToken(42L);

        assertThat(decoderAt(NOW.plus(Duration.ofMinutes(14))).decode(token).getSubject()).isEqualTo("42");
        assertThatThrownBy(() -> decoderAt(NOW.plus(Duration.ofMinutes(16))).decode(token))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        String token = tokenService.createAccessToken(42L);
        JwtDecoder otherKeyDecoder = new SecurityConfig().jwtDecoder(SecurityConfig.signingKey(randomSecret()), properties);

        assertThatThrownBy(() -> otherKeyDecoder.decode(token)).isInstanceOf(BadJwtException.class);
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() {
        JwtTokenService otherIssuer = new JwtTokenService(encoder,
                new JwtProperties(null, "someone-else", Duration.ofMinutes(15)), Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> decoderAt(NOW).decode(otherIssuer.createAccessToken(42L)))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("iss");
    }
}
