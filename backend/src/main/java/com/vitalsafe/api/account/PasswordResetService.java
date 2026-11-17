package com.vitalsafe.api.account;

import com.vitalsafe.api.account.mail.ResetMailer;
import com.vitalsafe.api.common.FieldValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

@Service
public class PasswordResetService {

    static final int VALID_MINUTES = 30;
    static final int MAX_REQUESTS_PER_HOUR = 3;

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final AccountRepository accounts;
    private final PasswordEncoder encoder;
    private final ResetMailer mailer;
    private final ExecutorService mailExecutor;
    private final String frontendBaseUrl;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(AccountRepository accounts,
                                PasswordEncoder encoder,
                                ResetMailer mailer,
                                @Qualifier("resetMailExecutor") ExecutorService mailExecutor,
                                @Value("${app.frontend.base-url:http://localhost:5173}") String frontendBaseUrl) {
        this.accounts = accounts;
        this.encoder = encoder;
        this.mailer = mailer;
        this.mailExecutor = mailExecutor;
        this.frontendBaseUrl = frontendBaseUrl.endsWith("/") ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1) : frontendBaseUrl;
    }

    /** Never says whether the address has an account. The caller always answers the same thing. */
    public void requestReset(String rawEmail) {
        String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase(Locale.ROOT);
        if (email.isEmpty() || email.length() > 254) {
            return;
        }
        Optional<UUID> userId = accounts.findUserIdByEmail(email);
        if (userId.isEmpty()) {
            return;
        }
        if (accounts.countRecentResetTokens(userId.get()) >= MAX_REQUESTS_PER_HOUR) {
            log.info("A password reset request was ignored: too many in the last hour");
            return;
        }

        String token = newToken();
        accounts.insertResetToken(userId.get(), hash(token), Instant.now().plus(VALID_MINUTES, ChronoUnit.MINUTES));
        // The token travels in the URL fragment, which browsers never send to a server and servers never log
        String link = frontendBaseUrl + "/reset-password#token=" + token;
        try {
            mailExecutor.execute(() -> {
                try {
                    mailer.sendResetLink(email, link, VALID_MINUTES);
                } catch (RuntimeException e) {
                    log.error("Sending a password reset email failed: {}", e.getClass().getSimpleName());
                }
            });
        } catch (RejectedExecutionException e) {
            log.error("A password reset email could not be queued");
        }
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        String problem = PasswordPolicy.problem(newPassword);
        if (problem != null) {
            throw new FieldValidationException(Map.of("newPassword", problem));
        }
        if (token == null || token.isBlank() || token.length() > 200) {
            throw invalidLink();
        }
        AccountRepository.ResetToken found = accounts.findUsableResetToken(hash(token)).orElseThrow(PasswordResetService::invalidLink);
        if (!accounts.markResetTokenUsed(found.id())) {
            throw invalidLink();
        }
        accounts.updatePasswordHash(found.userId(), encoder.encode(newPassword));
        accounts.expireUnusedResetTokens(found.userId());
        accounts.revokeAllSessions(found.userId());
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ResponseStatusException invalidLink() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "This reset link is invalid or has expired. Request a new one.");
    }
}