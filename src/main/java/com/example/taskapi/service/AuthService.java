package com.example.taskapi.service;

import com.example.taskapi.dto.LoginRequest;
import com.example.taskapi.dto.RefreshTokenRequest;
import com.example.taskapi.dto.RegisterRequest;
import com.example.taskapi.dto.TokenResponse;
import com.example.taskapi.dto.UserResponse;
import com.example.taskapi.exception.EmailAlreadyRegisteredException;
import com.example.taskapi.exception.FieldValidationException;
import com.example.taskapi.model.User;
import com.example.taskapi.repository.UserRepository;
import com.example.taskapi.security.JwtTokenService;
import com.example.taskapi.security.UserPrincipal;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class AuthService {

    /** BCrypt only uses the first 72 bytes, and Spring's encoder rejects longer input. */
    static final int MAX_PASSWORD_BYTES = 72;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtTokenService jwtTokenService,
                       RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        // @Size counts characters; a character can take up to 4 bytes in UTF-8
        if (request.password().getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new FieldValidationException("password",
                    "Password must not exceed " + MAX_PASSWORD_BYTES + " bytes");
        }

        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException();
        }

        try {
            User user = userRepository.saveAndFlush(new User(email, passwordEncoder.encode(request.password())));
            return new UserResponse(user.getId(), user.getEmail(), user.getCreatedAt());
        } catch (DataIntegrityViolationException exception) {
            // Two registrations with the same email at the same moment: the unique constraint decides
            throw new EmailAlreadyRegisteredException();
        }
    }

    /**
     * Checks the email and password with Spring Security's AuthenticationManager. A wrong password and
     * an unknown email both throw BadCredentialsException, which becomes the same 401.
     */
    public TokenResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        normalizeEmail(request.email()), request.password()));

        UserPrincipal user = (UserPrincipal) authentication.getPrincipal();
        return tokens(user.id(), refreshTokenService.issue(user.id()));
    }

    /** Exchanges a refresh token for a new access token and a new refresh token (rotation). */
    public TokenResponse refresh(RefreshTokenRequest request) {
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(request.refreshToken());
        return tokens(rotation.userId(), rotation.refreshToken());
    }

    /**
     * Revokes the refresh token. The current access token stays valid until it expires (at most
     * 15 minutes): it is checked by signature alone, which is what makes it stateless.
     */
    public void logout(RefreshTokenRequest request) {
        refreshTokenService.revoke(request.refreshToken());
    }

    private TokenResponse tokens(long userId, String refreshToken) {
        return TokenResponse.bearer(
                jwtTokenService.createAccessToken(userId),
                refreshToken,
                jwtTokenService.accessTokenTtlSeconds());
    }

    /** Emails are case-insensitive in practice, so they are stored and looked up lower-cased. */
    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
