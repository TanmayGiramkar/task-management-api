package com.example.taskapi.security;

import com.example.taskapi.exception.RateLimitExceededException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Limits how often one client can call {@code POST /auth/login}, {@code /auth/register} and
 * {@code /auth/refresh}, to slow down password guessing, credential stuffing and mass sign-ups.
 * <p>
 * Each (endpoint, client IP) pair gets a Bucket4j token bucket: it starts full, each request takes one
 * token, and tokens come back at a steady rate. An empty bucket means 429 with a Retry-After header.
 * <p>
 * The buckets live in memory, in a Caffeine cache that forgets idle clients and has a size cap, so
 * memory stays bounded even if many different IPs send requests. Limits are per app instance; with
 * several instances they would move to a shared store such as Redis (Stage 6). Behind a reverse proxy,
 * the client IP must come from forwarded headers (Stage 8).
 */
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Duration FORGET_IDLE_CLIENTS_AFTER = Duration.ofMinutes(10);
    private static final long MAX_TRACKED_CLIENTS = 100_000;

    private final Map<String, Bandwidth> limitsByPath;
    private final HandlerExceptionResolver exceptionResolver;
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(FORGET_IDLE_CLIENTS_AFTER)
            .maximumSize(MAX_TRACKED_CLIENTS)
            .build();

    public AuthRateLimitFilter(RateLimitProperties properties, HandlerExceptionResolver exceptionResolver) {
        this.limitsByPath = Map.of(
                "/auth/login", bandwidth(properties.login()),
                "/auth/register", bandwidth(properties.register()),
                "/auth/refresh", bandwidth(properties.refresh()));
        this.exceptionResolver = exceptionResolver;
    }

    private static Bandwidth bandwidth(RateLimitProperties.Limit limit) {
        return Bandwidth.builder()
                .capacity(limit.capacity())
                .refillGreedy(limit.capacity(), limit.period())
                .build();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !limitsByPath.containsKey(path(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = path(request);
        Bucket bucket = buckets.get(path + " " + request.getRemoteAddr(),
                key -> Bucket.builder().addLimit(limitsByPath.get(path)).build());

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            chain.doFilter(request, response);
            return;
        }

        // Round up, so a client that waits this long is guaranteed a token
        long retryAfterSeconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill() + 999_999_999L));
        exceptionResolver.resolveException(request, response, null, new RateLimitExceededException(retryAfterSeconds));
    }

    private static String path(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }
}
