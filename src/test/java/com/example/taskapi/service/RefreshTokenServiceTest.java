package com.example.taskapi.service;

import com.example.taskapi.exception.InvalidRefreshTokenException;
import com.example.taskapi.model.RefreshToken;
import com.example.taskapi.model.User;
import com.example.taskapi.repository.RefreshTokenRepository;
import com.example.taskapi.repository.UserRepository;
import com.example.taskapi.security.RefreshTokenProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-09T08:00:00Z");
    private static final Duration TTL = Duration.ofDays(7);

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UserRepository userRepository;

    private final User user = userWithId(7L);
    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(refreshTokenRepository, userRepository,
                new RefreshTokenProperties(TTL), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static User userWithId(long id) {
        User user = new User("tanmay@example.com", "hash");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    /** A stored token for {@code rawToken}, issued an hour ago. */
    private RefreshToken storedToken(String rawToken, UUID family) {
        return new RefreshToken(user, RefreshTokenService.hash(rawToken), family,
                NOW.minus(Duration.ofHours(1)), NOW.minus(Duration.ofHours(1)).plus(TTL));
    }

    private RefreshToken lastSavedToken() {
        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(saved.capture());
        return saved.getValue();
    }

    @Test
    void issueStoresOnlyTheHashWithA7DayExpiry() {
        when(userRepository.getReferenceById(7L)).thenReturn(user);

        String token = service.issue(7L);

        RefreshToken saved = lastSavedToken();
        assertThat(token).hasSize(43); // 32 bytes in Base64URL without padding
        assertThat(saved.getTokenHash()).isEqualTo(RefreshTokenService.hash(token)).isNotEqualTo(token).hasSize(64);
        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getFamilyId()).isNotNull();
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
        assertThat(saved.getExpiresAt()).isEqualTo(NOW.plus(TTL));
        assertThat(saved.isRevoked()).isFalse();
    }

    @Test
    void issuedTokensAreUnique() {
        when(userRepository.getReferenceById(7L)).thenReturn(user);

        assertThat(service.issue(7L)).isNotEqualTo(service.issue(7L));
    }

    @Test
    void rotateRevokesTheTokenAndIssuesANewOneInTheSameFamily() {
        UUID family = UUID.randomUUID();
        RefreshToken current = storedToken("old-token", family);
        when(refreshTokenRepository.findByTokenHashForUpdate(RefreshTokenService.hash("old-token")))
                .thenReturn(Optional.of(current));
        when(userRepository.getReferenceById(7L)).thenReturn(user);

        RefreshTokenService.Rotation rotation = service.rotate("old-token");

        assertThat(current.getRevokedAt()).isEqualTo(NOW);
        assertThat(rotation.userId()).isEqualTo(7L);
        assertThat(rotation.refreshToken()).isNotEqualTo("old-token");
        RefreshToken replacement = lastSavedToken();
        assertThat(replacement.getFamilyId()).isEqualTo(family);
        assertThat(replacement.getTokenHash()).isEqualTo(RefreshTokenService.hash(rotation.refreshToken()));
        assertThat(replacement.getExpiresAt()).isEqualTo(NOW.plus(TTL));
    }

    @Test
    void rotateRejectsAnUnknownToken() {
        when(refreshTokenRepository.findByTokenHashForUpdate(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate("unknown")).isInstanceOf(InvalidRefreshTokenException.class);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void rotateRejectsAnExpiredToken() {
        RefreshToken expired = new RefreshToken(user, RefreshTokenService.hash("expired"), UUID.randomUUID(),
                NOW.minus(TTL).minusSeconds(1), NOW.minusSeconds(1));
        when(refreshTokenRepository.findByTokenHashForUpdate(any())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.rotate("expired")).isInstanceOf(InvalidRefreshTokenException.class);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void reusingARevokedTokenRevokesTheWholeFamily() {
        UUID family = UUID.randomUUID();
        RefreshToken alreadyRotated = storedToken("stolen", family);
        alreadyRotated.revoke(NOW.minus(Duration.ofMinutes(5)));
        when(refreshTokenRepository.findByTokenHashForUpdate(any())).thenReturn(Optional.of(alreadyRotated));

        assertThatThrownBy(() -> service.rotate("stolen")).isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokenRepository).revokeFamily(family, NOW);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void revokeMarksTheTokenRevoked() {
        RefreshToken token = storedToken("token", UUID.randomUUID());
        when(refreshTokenRepository.findByTokenHashForUpdate(RefreshTokenService.hash("token")))
                .thenReturn(Optional.of(token));

        service.revoke("token");

        assertThat(token.getRevokedAt()).isEqualTo(NOW);
    }

    @Test
    void revokeIgnoresAnUnknownToken() {
        when(refreshTokenRepository.findByTokenHashForUpdate(any())).thenReturn(Optional.empty());

        service.revoke("unknown");

        verify(refreshTokenRepository, never()).revokeFamily(any(), any());
    }

    @Test
    void hashIsSha256InHex() {
        // Known SHA-256 of "abc"
        assertThat(RefreshTokenService.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
