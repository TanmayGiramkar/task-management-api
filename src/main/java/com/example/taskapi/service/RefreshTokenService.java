package com.example.taskapi.service;

import com.example.taskapi.exception.InvalidRefreshTokenException;
import com.example.taskapi.model.RefreshToken;
import com.example.taskapi.repository.RefreshTokenRepository;
import com.example.taskapi.repository.UserRepository;
import com.example.taskapi.security.RefreshTokenProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Issues, rotates and revokes refresh tokens (ADR 0004).
 * <ul>
 *   <li>A token is 32 random bytes (256 bits) from {@link SecureRandom}, Base64URL-encoded. It is not a
 *       JWT: the server must look it up anyway to check revocation, so it does not need to carry data.</li>
 *   <li>Only its SHA-256 hash is stored. A fast hash is enough here (unlike passwords, which need BCrypt)
 *       because 256 random bits cannot be guessed, and a fast hash can be looked up directly.</li>
 *   <li>Every refresh revokes the presented token and issues a new one in the same family (rotation).
 *       Presenting a token that is already revoked means it was used twice, so it may have been stolen:
 *       the whole family is revoked, logging out both the attacker and the real user.</li>
 * </ul>
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final RefreshTokenProperties properties;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               UserRepository userRepository,
                               RefreshTokenProperties properties,
                               Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.properties = properties;
        this.clock = clock;
    }

    /** The result of a successful rotation: who the token belongs to and the replacement token. */
    public record Rotation(long userId, String refreshToken) {
    }

    /** Starts a new family at login and returns the token to give the client. */
    @Transactional
    public String issue(long userId) {
        return issueInFamily(userId, UUID.randomUUID());
    }

    /**
     * Exchanges a valid refresh token for a new one. {@code noRollbackFor}: when reuse is detected,
     * the family revocation must be committed even though the method then throws.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public Rotation rotate(String presentedToken) {
        RefreshToken current = refreshTokenRepository.findByTokenHashForUpdate(hash(presentedToken))
                .orElseThrow(InvalidRefreshTokenException::new);
        Instant now = clock.instant();
        long userId = current.getUser().getId();

        if (current.isRevoked()) {
            int revoked = refreshTokenRepository.revokeFamily(current.getFamilyId(), now);
            log.warn("Refresh token reused for user {}: revoked {} active token(s) in family {}",
                    userId, revoked, current.getFamilyId());
            throw new InvalidRefreshTokenException();
        }
        if (current.isExpiredAt(now)) {
            throw new InvalidRefreshTokenException();
        }

        current.revoke(now);
        return new Rotation(userId, issueInFamily(userId, current.getFamilyId()));
    }

    /** Logout. Unknown or already revoked tokens are ignored, so logout never reveals anything. */
    @Transactional
    public void revoke(String presentedToken) {
        refreshTokenRepository.findByTokenHashForUpdate(hash(presentedToken))
                .ifPresent(token -> token.revoke(clock.instant()));
    }

    private String issueInFamily(long userId, UUID familyId) {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        Instant now = clock.instant();
        refreshTokenRepository.save(new RefreshToken(
                userRepository.getReferenceById(userId), hash(token), familyId, now, now.plus(properties.ttl())));
        return token;
    }

    /** SHA-256 as 64 hex characters. */
    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            // Every Java runtime must support SHA-256
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
