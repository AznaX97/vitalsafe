package com.vitalsafe.api.auth;

import com.vitalsafe.api.security.TokenHasher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class RefreshTokenService {

    public record Rotated(UUID userId, String rawToken) {}

    private final RefreshTokenRepository repository;
    private final Duration lifetime;

    public RefreshTokenService(RefreshTokenRepository repository,
                               @Value("${app.refresh.expiration-days}") long days) {
        this.repository = repository;
        this.lifetime = Duration.ofDays(days);
    }

    /** Creates a token. Returns the RAW value for the browser, and stores only its hash. */
    @Transactional
    public String issue(UUID userId) {
        String raw = TokenHasher.newRawToken();
        RefreshToken token = new RefreshToken();
        token.setUserId(userId);
        token.setTokenHash(TokenHasher.sha256Hex(raw));
        token.setExpiresAt(Instant.now().plus(lifetime));
        repository.save(token);
        return raw;
    }

    /**
     * Checks the presented token, retires it, and issues a replacement.
     * noRollbackFor: when a used token comes back we revoke every session and then throw,
     * and the revoking must be saved even though an exception follows.
     */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public Rotated rotate(String raw) {
        RefreshToken token = repository.findByTokenHash(TokenHasher.sha256Hex(raw))
                .orElseThrow(RefreshTokenService::unauthorized);

        if (token.isRevoked()) {
            // A token that was already used has come back: possible theft. End every session of this user.
            repository.revokeAllForUser(token.getUserId());
            throw unauthorized();
        }
        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw unauthorized();
        }

        token.setRevoked(true);
        repository.save(token);
        return new Rotated(token.getUserId(), issue(token.getUserId()));
    }

    @Transactional
    public void revoke(String raw) {
        repository.findByTokenHash(TokenHasher.sha256Hex(raw)).ifPresent(token -> {
            token.setRevoked(true);
            repository.save(token);
        });
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void purgeExpired() {
        repository.deleteExpired(Instant.now());
    }

    private static ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expired. Please log in again.");
    }
}