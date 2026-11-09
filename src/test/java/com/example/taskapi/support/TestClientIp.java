package com.example.taskapi.support;

import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fake client IPs for MockMvc requests. The auth endpoints are rate-limited per IP, and every MockMvc
 * request comes from 127.0.0.1 by default, so tests that call /auth would use up each other's limits.
 * Giving each test its own IP keeps them independent while the real limits stay in place.
 */
public final class TestClientIp {

    private static final AtomicInteger NEXT = new AtomicInteger(1);

    private TestClientIp() {
        // Static helpers only
    }

    /** A request post-processor that sets a client IP no other caller of this method gets. */
    public static RequestPostProcessor uniqueClientIp() {
        int n = NEXT.getAndIncrement();
        String ip = "10." + ((n >> 16) & 255) + "." + ((n >> 8) & 255) + "." + (n & 255);
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
