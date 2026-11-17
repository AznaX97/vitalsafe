package com.vitalsafe.api.account;

import com.vitalsafe.api.common.FieldValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository accounts;
    private final PasswordEncoder encoder;

    public AccountService(AccountRepository accounts, PasswordEncoder encoder) {
        this.accounts = accounts;
        this.encoder = encoder;
    }

    /** A wrong current password is a 400 with a field message, not a 401, so the app does not think the login expired. */
    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        String storedHash = storedHash(userId);
        Map<String, String> errors = new LinkedHashMap<>();
        if (!matches(currentPassword, storedHash)) {
            errors.put("currentPassword", "Current password is incorrect");
        }
        String problem = PasswordPolicy.problem(newPassword);
        if (problem != null) {
            errors.put("newPassword", problem);
        } else if (newPassword.equals(currentPassword)) {
            errors.put("newPassword", "The new password must be different from the current one");
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
        accounts.updatePasswordHash(userId, encoder.encode(newPassword));
        accounts.expireUnusedResetTokens(userId);
        accounts.revokeAllSessions(userId);
    }

    @Transactional
    public void deleteAccount(UUID userId, String password) {
        if (!matches(password, storedHash(userId))) {
            throw new FieldValidationException(Map.of("password", "Password is incorrect"));
        }
        accounts.deleteAccount(userId);
    }

    private String storedHash(UUID userId) {
        return accounts.findPasswordHash(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    // BCrypt only reads the first 72 bytes, so a longer password can never be the right one
    private boolean matches(String raw, String hash) {
        return raw != null && raw.getBytes(StandardCharsets.UTF_8).length <= PasswordPolicy.MAX_BYTES && encoder.matches(raw, hash);
    }
}