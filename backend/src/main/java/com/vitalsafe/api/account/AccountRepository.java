package com.vitalsafe.api.account;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Everything the account features need from the database, in plain SQL. */
@Repository
public class AccountRepository {

    public record ResetToken(UUID id, UUID userId) {}

    private final JdbcTemplate jdbc;

    public AccountRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UUID> findUserIdByEmail(String email) {
        return jdbc.query("select id from users where email = ?", (rs, row) -> rs.getObject(1, UUID.class), email)
                .stream().findFirst();
    }

    public Optional<String> findPasswordHash(UUID userId) {
        return jdbc.query("select password_hash from users where id = ?", (rs, row) -> rs.getString(1), userId)
                .stream().findFirst();
    }

    public void updatePasswordHash(UUID userId, String passwordHash) {
        jdbc.update("update users set password_hash = ? where id = ?", passwordHash, userId);
    }

    /** Ends every login session of the user. Their refresh tokens can no longer be used. */
    public void revokeAllSessions(UUID userId) {
        jdbc.update("update refresh_tokens set revoked = true where user_id = ? and revoked = false", userId);
    }

    /** Removes the user and everything that belongs to them (readings, ranges and reset tokens go with it). */
    public void deleteAccount(UUID userId) {
        jdbc.update("delete from refresh_tokens where user_id = ?", userId);
        jdbc.update("delete from users where id = ?", userId);
    }

    public void insertResetToken(UUID userId, String tokenHash, Instant expiresAt) {
        jdbc.update("insert into password_reset_tokens (user_id, token_hash, expires_at) values (?, ?, ?)",
                userId, tokenHash, Timestamp.from(expiresAt));
    }

    public int countRecentResetTokens(UUID userId) {
        Integer count = jdbc.queryForObject(
                "select count(*) from password_reset_tokens where user_id = ? and created_at > now() - interval '1 hour'",
                Integer.class, userId);
        return count == null ? 0 : count;
    }

    public Optional<ResetToken> findUsableResetToken(String tokenHash) {
        return jdbc.query(
                "select id, user_id from password_reset_tokens where token_hash = ? and used_at is null and expires_at > now()",
                (rs, row) -> new ResetToken(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)), tokenHash)
                .stream().findFirst();
    }

    /** True only for the one caller that really used it, so two requests with the same token cannot both succeed. */
    public boolean markResetTokenUsed(UUID tokenId) {
        return jdbc.update("update password_reset_tokens set used_at = now() where id = ? and used_at is null", tokenId) == 1;
    }

    /** After a password change, no older emailed link may work any more. */
    public void expireUnusedResetTokens(UUID userId) {
        jdbc.update("update password_reset_tokens set used_at = now() where user_id = ? and used_at is null", userId);
    }
}