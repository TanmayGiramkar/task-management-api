package com.example.taskapi.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Settings for refresh tokens, bound from {@code app.security.refresh-token.*}.
 *
 * @param ttl how long a refresh token can be used. Rotation gives each new token a fresh lifetime,
 *            so an active user stays logged in; an idle one must log in again after this long
 */
@ConfigurationProperties("app.security.refresh-token")
public record RefreshTokenProperties(Duration ttl) {
}
