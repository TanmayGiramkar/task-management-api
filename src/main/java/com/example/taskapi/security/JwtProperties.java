package com.example.taskapi.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Settings for access tokens, bound from {@code app.security.jwt.*} in application.properties.
 *
 * @param secret         Base64-encoded HMAC key, from {@code JWT_SECRET} in .env. At least 32 bytes once decoded
 * @param issuer         value of the {@code iss} claim; tokens with another issuer are rejected
 * @param accessTokenTtl how long an access token is valid
 */
@ConfigurationProperties("app.security.jwt")
public record JwtProperties(String secret, String issuer, Duration accessTokenTtl) {
}
