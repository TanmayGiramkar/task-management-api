package com.example.taskapi.controller;

import com.example.taskapi.dto.LoginRequest;
import com.example.taskapi.dto.RefreshTokenRequest;
import com.example.taskapi.dto.RegisterRequest;
import com.example.taskapi.dto.TokenResponse;
import com.example.taskapi.dto.UserResponse;
import com.example.taskapi.exception.EmailAlreadyRegisteredException;
import com.example.taskapi.exception.FieldValidationException;
import com.example.taskapi.exception.InvalidRefreshTokenException;
import com.example.taskapi.security.SecurityConfig;
import com.example.taskapi.service.AuthService;
import com.example.taskapi.support.TestClientIp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link AuthController}: public access, validation and the error contract.
 * {@code AuthService} is mocked; the real login flow is tested in {@code AuthenticationIT}.
 */
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    /** Each test calls from its own IP, so the per-IP rate limits never carry over between tests. */
    private RequestPostProcessor clientIp;

    @BeforeEach
    void useAFreshClientIp() {
        clientIp = TestClientIp.uniqueClientIp();
    }

    private ResultActions postJson(String path, String json) throws Exception {
        return mockMvc.perform(post(path).with(clientIp).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Nested
    class Register {

        @Test
        void returns201WithTheAccountAndNeedsNoToken() throws Exception {
            when(authService.register(new RegisterRequest("tanmay@example.com", "correct-horse")))
                    .thenReturn(new UserResponse(1L, "tanmay@example.com", LocalDateTime.of(2026, 11, 9, 14, 5, 12)));

            postJson("/auth/register", """
                    {"email": "tanmay@example.com", "password": "correct-horse"}
                    """)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.email").value("tanmay@example.com"))
                    .andExpect(jsonPath("$.createdAt").value("2026-11-09T14:05:12"))
                    // Never echo the password or its hash
                    .andExpect(content().string(not(containsString("correct-horse"))))
                    .andExpect(jsonPath("$.password").doesNotExist())
                    .andExpect(jsonPath("$.passwordHash").doesNotExist());
        }

        @Test
        void rejectsAnInvalidEmail() throws Exception {
            postJson("/auth/register", """
                    {"email": "not-an-email", "password": "correct-horse"}
                    """)
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.detail").value("Validation failed"))
                    .andExpect(jsonPath("$.errors.email").value("Email must be a valid email address"));
            verifyNoInteractions(authService);
        }

        @Test
        void rejectsAPasswordShorterThan8Characters() throws Exception {
            postJson("/auth/register", """
                    {"email": "tanmay@example.com", "password": "short"}
                    """)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.password").value("Password must be 8 to 72 characters"));
            verifyNoInteractions(authService);
        }

        @Test
        void rejectsAPasswordLongerThan72Characters() throws Exception {
            postJson("/auth/register", "{\"email\": \"tanmay@example.com\", \"password\": \"" + "p".repeat(73) + "\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.password").value("Password must be 8 to 72 characters"));
            verifyNoInteractions(authService);
        }

        @Test
        void reportsAPasswordOver72BytesAsAFieldError() throws Exception {
            when(authService.register(any(RegisterRequest.class)))
                    .thenThrow(new FieldValidationException("password", "Password must not exceed 72 bytes"));

            postJson("/auth/register", "{\"email\": \"tanmay@example.com\", \"password\": \"" + "é".repeat(40) + "\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("Validation failed"))
                    .andExpect(jsonPath("$.errors.password").value("Password must not exceed 72 bytes"));
        }

        @Test
        void returns409WhenTheEmailIsTaken() throws Exception {
            when(authService.register(any(RegisterRequest.class))).thenThrow(new EmailAlreadyRegisteredException());

            postJson("/auth/register", """
                    {"email": "tanmay@example.com", "password": "correct-horse"}
                    """)
                    .andExpect(status().isConflict())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.title").value("Conflict"))
                    .andExpect(jsonPath("$.detail").value("An account with this email already exists"))
                    .andExpect(jsonPath("$.instance").value("/auth/register"));
        }
    }

    @Nested
    class Login {

        @Test
        void returnsBearerTokensAndNeedsNoToken() throws Exception {
            when(authService.login(new LoginRequest("tanmay@example.com", "correct-horse")))
                    .thenReturn(TokenResponse.bearer("header.payload.signature", "refresh-token", 900));

            postJson("/auth/login", """
                    {"email": "tanmay@example.com", "password": "correct-horse"}
                    """)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("header.payload.signature"))
                    .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.expiresIn").value(900));
        }

        @Test
        void rejectsAMissingPassword() throws Exception {
            postJson("/auth/login", """
                    {"email": "tanmay@example.com"}
                    """)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.password").value("Password is required"));
            verifyNoInteractions(authService);
        }

        @Test
        void returns401ProblemForWrongCredentials() throws Exception {
            when(authService.login(any(LoginRequest.class))).thenThrow(new BadCredentialsException("Bad credentials"));

            postJson("/auth/login", """
                    {"email": "tanmay@example.com", "password": "wrong-password"}
                    """)
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.title").value("Unauthorized"))
                    .andExpect(jsonPath("$.detail").value("Invalid email or password"))
                    .andExpect(jsonPath("$.instance").value("/auth/login"));
            verify(authService).login(new LoginRequest("tanmay@example.com", "wrong-password"));
        }
    }

    @Nested
    class RefreshAndLogout {

        @Test
        void refreshReturnsNewTokensAndNeedsNoAccessToken() throws Exception {
            when(authService.refresh(new RefreshTokenRequest("old-refresh")))
                    .thenReturn(TokenResponse.bearer("new.access.token", "new-refresh", 900));

            postJson("/auth/refresh", """
                    {"refreshToken": "old-refresh"}
                    """)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("new.access.token"))
                    .andExpect(jsonPath("$.refreshToken").value("new-refresh"));
        }

        @Test
        void refreshReturns401ProblemForAnInvalidToken() throws Exception {
            when(authService.refresh(any(RefreshTokenRequest.class))).thenThrow(new InvalidRefreshTokenException());

            postJson("/auth/refresh", """
                    {"refreshToken": "revoked"}
                    """)
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.detail").value("Refresh token is invalid, expired or revoked"))
                    .andExpect(jsonPath("$.instance").value("/auth/refresh"));
        }

        @Test
        void refreshRejectsAMissingToken() throws Exception {
            postJson("/auth/refresh", "{}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.refreshToken").value("Refresh token is required"));
            verifyNoInteractions(authService);
        }

        @Test
        void logoutReturns204WithNoBody() throws Exception {
            postJson("/auth/logout", """
                    {"refreshToken": "refresh-token"}
                    """)
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));
            verify(authService).logout(new RefreshTokenRequest("refresh-token"));
        }
    }
}
