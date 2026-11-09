package com.example.taskapi.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.web.servlet.HandlerExceptionResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * The 401/403 handlers must keep the standard Bearer challenge header and pass the exception to Spring
 * MVC, where GlobalExceptionHandler writes the Problem Details body. The body itself is checked in the
 * web-layer and integration tests.
 */
class ProblemDetailsSecurityHandlersTest {

    private final HandlerExceptionResolver resolver = mock(HandlerExceptionResolver.class);
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/tasks");
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @Test
    void entryPointSends401WithBearerChallengeAndDelegatesTheBody() throws Exception {
        var exception = new InsufficientAuthenticationException("Full authentication is required");

        new ProblemDetailsAuthenticationEntryPoint(resolver).commence(request, response, exception);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).startsWith("Bearer");
        verify(resolver).resolveException(request, response, null, exception);
    }

    @Test
    void accessDeniedHandlerSends403WithBearerChallengeAndDelegatesTheBody() throws Exception {
        var exception = new AccessDeniedException("denied");

        new ProblemDetailsAccessDeniedHandler(resolver).handle(request, response, exception);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).startsWith("Bearer");
        verify(resolver).resolveException(request, response, null, exception);
    }
}
