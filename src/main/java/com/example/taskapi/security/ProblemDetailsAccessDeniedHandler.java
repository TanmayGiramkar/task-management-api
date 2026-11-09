package com.example.taskapi.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;

/**
 * Answers authenticated requests that are not allowed with 403, in the Problem Details format.
 * Works like {@link ProblemDetailsAuthenticationEntryPoint}. Stage 4 has no roles, so nothing returns
 * 403 yet; team roles arrive in Stage 5. Access to another user's task is 404, not 403.
 */
public class ProblemDetailsAccessDeniedHandler implements AccessDeniedHandler {

    private final AccessDeniedHandler bearerAccessDeniedHandler = new BearerTokenAccessDeniedHandler();
    private final HandlerExceptionResolver exceptionResolver;

    public ProblemDetailsAccessDeniedHandler(HandlerExceptionResolver exceptionResolver) {
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {
        bearerAccessDeniedHandler.handle(request, response, accessDeniedException);
        exceptionResolver.resolveException(request, response, null, accessDeniedException);
    }
}
