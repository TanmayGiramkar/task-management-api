package com.example.taskapi.service;

import com.example.taskapi.dto.LoginRequest;
import com.example.taskapi.dto.RefreshTokenRequest;
import com.example.taskapi.dto.RegisterRequest;
import com.example.taskapi.dto.TokenResponse;
import com.example.taskapi.dto.UserResponse;
import com.example.taskapi.exception.EmailAlreadyRegisteredException;
import com.example.taskapi.exception.FieldValidationException;
import com.example.taskapi.exception.InvalidRefreshTokenException;
import com.example.taskapi.model.User;
import com.example.taskapi.repository.UserRepository;
import com.example.taskapi.security.JwtTokenService;
import com.example.taskapi.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private RefreshTokenService refreshTokenService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository, passwordEncoder, authenticationManager, jwtTokenService, refreshTokenService);
    }

    @Nested
    class Register {

        @Test
        void storesTheLowerCasedEmailAndOnlyTheHash() {
            when(userRepository.existsByEmail("tanmay@example.com")).thenReturn(false);
            when(passwordEncoder.encode("correct-horse")).thenReturn("$2a$10$hash");
            when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                ReflectionTestUtils.setField(user, "id", 1L);
                return user;
            });

            UserResponse response = authService.register(new RegisterRequest("  Tanmay@Example.COM ", "correct-horse"));

            ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
            verify(userRepository).saveAndFlush(saved.capture());
            assertThat(saved.getValue().getEmail()).isEqualTo("tanmay@example.com");
            assertThat(saved.getValue().getPasswordHash()).isEqualTo("$2a$10$hash");
            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.email()).isEqualTo("tanmay@example.com");
        }

        @Test
        void rejectsAnEmailThatIsAlreadyRegistered() {
            when(userRepository.existsByEmail("tanmay@example.com")).thenReturn(true);

            assertThatThrownBy(() -> authService.register(new RegisterRequest("TANMAY@example.com", "correct-horse")))
                    .isInstanceOf(EmailAlreadyRegisteredException.class);
            verify(userRepository, never()).saveAndFlush(any(User.class));
        }

        @Test
        void treatsAUniqueConstraintViolationAsAlreadyRegistered() {
            // Another request registered the same email between our check and our insert
            when(userRepository.existsByEmail("tanmay@example.com")).thenReturn(false);
            when(passwordEncoder.encode("correct-horse")).thenReturn("$2a$10$hash");
            when(userRepository.saveAndFlush(any(User.class)))
                    .thenThrow(new DataIntegrityViolationException("users_email_unique"));

            assertThatThrownBy(() -> authService.register(new RegisterRequest("tanmay@example.com", "correct-horse")))
                    .isInstanceOf(EmailAlreadyRegisteredException.class);
        }

        @Test
        void rejectsAPasswordOver72BytesBeforeHashingIt() {
            // 40 characters pass @Size(max = 72), but "é" is 2 bytes in UTF-8, so this is 80 bytes
            String password = "é".repeat(40);

            assertThatThrownBy(() -> authService.register(new RegisterRequest("tanmay@example.com", password)))
                    .isInstanceOf(FieldValidationException.class)
                    .hasMessage("Password must not exceed 72 bytes")
                    .extracting("field").isEqualTo("password");
            verify(passwordEncoder, never()).encode(any());
        }

        @Test
        void acceptsAPasswordOfExactly72Bytes() {
            when(userRepository.existsByEmail("tanmay@example.com")).thenReturn(false);
            when(passwordEncoder.encode("é".repeat(36))).thenReturn("$2a$10$hash");
            when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            authService.register(new RegisterRequest("tanmay@example.com", "é".repeat(36)));

            verify(passwordEncoder).encode("é".repeat(36));
        }
    }

    @Nested
    class Login {

        @Test
        void authenticatesTheLowerCasedEmailAndReturnsAccessAndRefreshTokens() {
            Authentication authenticated = UsernamePasswordAuthenticationToken.authenticated(
                    new UserPrincipal(7L, "tanmay@example.com", "$2a$10$hash"), null, List.of());
            when(authenticationManager.authenticate(any())).thenReturn(authenticated);
            when(jwtTokenService.createAccessToken(7L)).thenReturn("access.jwt.token");
            when(jwtTokenService.accessTokenTtlSeconds()).thenReturn(900L);
            when(refreshTokenService.issue(7L)).thenReturn("refresh-token");

            TokenResponse response = authService.login(new LoginRequest(" Tanmay@Example.com", "correct-horse"));

            ArgumentCaptor<Authentication> attempt = ArgumentCaptor.forClass(Authentication.class);
            verify(authenticationManager).authenticate(attempt.capture());
            assertThat(attempt.getValue().getName()).isEqualTo("tanmay@example.com");
            assertThat(attempt.getValue().getCredentials()).isEqualTo("correct-horse");
            assertThat(response).isEqualTo(TokenResponse.bearer("access.jwt.token", "refresh-token", 900));
        }

        @Test
        void letsBadCredentialsPropagateSoTheyBecomeA401() {
            when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));

            assertThatThrownBy(() -> authService.login(new LoginRequest("tanmay@example.com", "wrong")))
                    .isInstanceOf(BadCredentialsException.class);
            verify(jwtTokenService, never()).createAccessToken(anyLong());
            verify(refreshTokenService, never()).issue(anyLong());
        }
    }

    @Nested
    class RefreshAndLogout {

        @Test
        void refreshReturnsANewAccessTokenWithTheRotatedRefreshToken() {
            when(refreshTokenService.rotate("old-refresh"))
                    .thenReturn(new RefreshTokenService.Rotation(7L, "new-refresh"));
            when(jwtTokenService.createAccessToken(7L)).thenReturn("new.access.token");
            when(jwtTokenService.accessTokenTtlSeconds()).thenReturn(900L);

            TokenResponse response = authService.refresh(new RefreshTokenRequest("old-refresh"));

            assertThat(response).isEqualTo(TokenResponse.bearer("new.access.token", "new-refresh", 900));
        }

        @Test
        void refreshWithAnInvalidTokenIssuesNoAccessToken() {
            when(refreshTokenService.rotate("bad")).thenThrow(new InvalidRefreshTokenException());

            assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest("bad")))
                    .isInstanceOf(InvalidRefreshTokenException.class);
            verify(jwtTokenService, never()).createAccessToken(anyLong());
        }

        @Test
        void logoutRevokesTheRefreshToken() {
            authService.logout(new RefreshTokenRequest("refresh-token"));

            verify(refreshTokenService).revoke("refresh-token");
        }
    }
}
