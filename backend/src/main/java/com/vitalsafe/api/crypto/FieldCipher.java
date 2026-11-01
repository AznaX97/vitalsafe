package com.vitalsafe.api.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts and decrypts single database fields with AES-256-GCM.
 *
 * Stored format: "v1." followed by base64(iv + ciphertext + tag). The version prefix lets a future
 * key change tell old values from new ones. A fresh random IV is used for every value, so equal
 * plaintexts produce different ciphertexts. GCM also detects any tampering.
 */
@Component
public class FieldCipher {

    private static final String PREFIX = "v1.";
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;     // the recommended size for GCM
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public FieldCipher(@Value("${app.encryption.key}") String base64Key) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("ENCRYPTION_KEY is not valid base64");
        }
        if (bytes.length != 32) {
            throw new IllegalStateException("ENCRYPTION_KEY must be 32 bytes (base64-encoded) for AES-256");
        }
        this.key = new SecretKeySpec(bytes, "AES");
    }

    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
            buffer.put(iv).put(encrypted);
            return PREFIX + Base64.getEncoder().encodeToString(buffer.array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public String decrypt(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) {
            throw new IllegalStateException("Decryption failed");
        }
        try {
            byte[] data = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            if (data.length < IV_BYTES + TAG_BITS / 8) {
                throw new IllegalStateException("Decryption failed");
            }
            ByteBuffer buffer = ByteBuffer.wrap(data);
            byte[] iv = new byte[IV_BYTES];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // Deliberately vague: never say whether it was the key, the format or tampering
            throw new IllegalStateException("Decryption failed");
        }
    }
}