package com.example.taskapi.security;

import com.example.taskapi.exception.RateLimitExceededException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** The rate-limit filter on its own, with small limits: 2 logins, 1 registration, 3 refreshes per minute. */
class AuthRateLimitFilterTest {

    private final HandlerExceptionResolver resolver = mock(HandlerExceptionResolver.class);
    private final AuthRateLimitFilter filter = new AuthRateLimitFilter(new RateLimitProperties(
            new RateLimitProperties.Limit(2, Duration.ofMinutes(1)),
            new RateLimitProperties.Limit(1, Duration.ofMinutes(1)),
            new RateLimitProperties.Limit(3, Duration.ofMinutes(1))), resolver);

    /** Sends one request and says whether it reached the next filter (true) or was rejected (false). */
    private boolean passes(String method, String path, String clientIp) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(clientIp);
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        return chain.getRequest() != null;
    }

    @Test
    void allowsRequestsUpToTheCapacityThenRejectsWithRetryAfter() throws Exception {
        assertThat(passes("POST", "/auth/login", "10.0.0.1")).isTrue();
        assertThat(passes("POST", "/auth/login", "10.0.0.1")).isTrue();
        assertThat(passes("POST", "/auth/login", "10.0.0.1")).isFalse();

        ArgumentCaptor<Exception> rejection = ArgumentCaptor.forClass(Exception.class);
        verify(resolver).resolveException(any(), any(), isNull(), rejection.capture());
        // 2 per minute refill one token every 30 seconds
        assertThat(rejection.getValue()).isInstanceOf(RateLimitExceededException.class);
        assertThat(((RateLimitExceededException) rejection.getValue()).getRetryAfterSeconds()).isBetween(1L, 30L);
    }

    @Test
    void eachClientIpHasItsOwnLimit() throws Exception {
        assertThat(passes("POST", "/auth/register", "10.0.0.1")).isTrue();
        assertThat(passes("POST", "/auth/register", "10.0.0.1")).isFalse();

        assertThat(passes("POST", "/auth/register", "10.0.0.2")).isTrue();
    }

    @Test
    void eachEndpointHasItsOwnLimit() throws Exception {
        assertThat(passes("POST", "/auth/register", "10.0.0.1")).isTrue();
        assertThat(passes("POST", "/auth/register", "10.0.0.1")).isFalse();

        assertThat(passes("POST", "/auth/login", "10.0.0.1")).isTrue();
        assertThat(passes("POST", "/auth/refresh", "10.0.0.1")).isTrue();
    }

    @Test
    void otherRequestsAreNeverLimited() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertThat(passes("GET", "/tasks", "10.0.0.1")).isTrue();
            assertThat(passes("POST", "/tasks", "10.0.0.1")).isTrue();
            assertThat(passes("POST", "/auth/logout", "10.0.0.1")).isTrue();
            assertThat(passes("GET", "/auth/login", "10.0.0.1")).isTrue();
        }
        verify(resolver, never()).resolveException(any(), any(), any(), any());
    }
}
