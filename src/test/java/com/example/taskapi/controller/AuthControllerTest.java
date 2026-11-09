package com.example.taskapi.controller;

import com.example.taskapi.dto.LoginRequest;
import com.example.taskapi.dto.RegisterRequest;
import com.example.taskapi.dto.TokenResponse;
import com.example.taskapi.dto.UserResponse;
import com.example.taskapi.exception.EmailAlreadyRegisteredException;
import com.example.taskapi.exception.FieldValidationException;
import com.example.taskapi.security.SecurityConfig;
import com.example.taskapi.service.AuthService;
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

    private ResultActions postJson(String path, String json) throws Exception {
        return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json));
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
        void returnsABearerTokenAndNeedsNoToken() throws Exception {
            when(authService.login(new LoginRequest("tanmay@example.com", "correct-horse")))
                    .thenReturn(TokenResponse.bearer("header.payload.signature", 900));

            postJson("/auth/login", """
                    {"email": "tanmay@example.com", "password": "correct-horse"}
                    """)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("header.payload.signature"))
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
}
