package com.example.taskapi.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;

/**
 * Answers unauthenticated requests (no token, or an invalid/expired one) with 401.
 * <p>
 * Spring Security rejects these requests in its filter chain, before any controller runs, so
 * {@code @RestControllerAdvice} would never see them. This entry point first lets
 * {@link BearerTokenAuthenticationEntryPoint} set the status and the standard
 * {@code WWW-Authenticate: Bearer ...} header (RFC 6750), then hands the exception to Spring MVC's
 * exception resolver, so {@code GlobalExceptionHandler} writes the same Problem Details body as for
 * every other error.
 */
public class ProblemDetailsAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final AuthenticationEntryPoint bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();
    private final HandlerExceptionResolver exceptionResolver;

    public ProblemDetailsAuthenticationEntryPoint(HandlerExceptionResolver exceptionResolver) {
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {
        bearerEntryPoint.commence(request, response, authException);
        exceptionResolver.resolveException(request, response, null, authException);
    }
}
