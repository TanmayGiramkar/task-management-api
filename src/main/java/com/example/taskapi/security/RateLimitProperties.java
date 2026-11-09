package com.example.taskapi.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Per-client limits for the auth endpoints, bound from {@code app.security.rate-limit.*}.
 * Each limit allows {@code capacity} requests at once, then refills at {@code capacity} per {@code period}.
 */
@ConfigurationProperties("app.security.rate-limit")
public record RateLimitProperties(Limit login, Limit register, Limit refresh) {

    public record Limit(int capacity, Duration period) {
    }
}
