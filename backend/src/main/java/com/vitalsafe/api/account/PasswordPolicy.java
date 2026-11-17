package com.vitalsafe.api.account;

import java.nio.charset.StandardCharsets;

/** The same rule as registration: 12 to 72 characters, and at most 72 bytes because BCrypt ignores anything after that. */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_BYTES = 72;

    private PasswordPolicy() {
    }

    /** Returns a message when the password is not acceptable, or null when it is. */
    public static String problem(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            return "Password must be at least " + MIN_LENGTH + " characters";
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            return "Password must be at most " + MAX_BYTES + " bytes (about 72 characters)";
        }
        return null;
    }
}