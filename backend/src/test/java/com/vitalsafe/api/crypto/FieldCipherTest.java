package com.vitalsafe.api.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldCipherTest {

    private static String keyOf(int seed, int length) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) (seed + i);
        }
        return Base64.getEncoder().encodeToString(bytes);
    }

    private final FieldCipher cipher = new FieldCipher(keyOf(1, 32));

    @Test
    void encryptThenDecryptReturnsTheOriginal() {
        assertThat(cipher.decrypt(cipher.encrypt("after a walk"))).isEqualTo("after a walk");
    }

    @Test
    void storedValueCarriesTheVersionPrefixAndHidesThePlainText() {
        String stored = cipher.encrypt("private note");

        assertThat(stored).startsWith("v1.").doesNotContain("private").doesNotContain("note");
    }

    @Test
    void theSameTextEncryptsDifferentlyEveryTime() {
        String first = cipher.encrypt("120");
        String second = cipher.encrypt("120");

        assertThat(first).isNotEqualTo(second);
        assertThat(cipher.decrypt(first)).isEqualTo("120");
        assertThat(cipher.decrypt(second)).isEqualTo("120");
    }

    @Test
    void emptyAndUnicodeTextRoundTrip() {
        assertThat(cipher.decrypt(cipher.encrypt(""))).isEmpty();

        String text = "Tired \u2013 caf\u00e9 \u2615 \u0DC1\u0DCA\u200D\u0DBB\u0DD3";
        assertThat(cipher.decrypt(cipher.encrypt(text))).isEqualTo(text);
    }

    @Test
    void tamperedDataIsDetected() {
        String stored = cipher.encrypt("hello");
        byte[] bytes = Base64.getDecoder().decode(stored.substring(3));
        bytes[bytes.length - 1] ^= 0x01;   // flip one bit
        String tampered = "v1." + Base64.getEncoder().encodeToString(bytes);

        assertThatThrownBy(() -> cipher.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Decryption failed");
    }

    @Test
    void aDifferentKeyCannotDecrypt() {
        String stored = cipher.encrypt("hello");
        FieldCipher other = new FieldCipher(keyOf(100, 32));

        assertThatThrownBy(() -> other.decrypt(stored))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Decryption failed");
    }

    @Test
    void valuesWithoutTheVersionPrefixAreRejected() {
        assertThatThrownBy(() -> cipher.decrypt("hello"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Decryption failed");
        assertThatThrownBy(() -> cipher.decrypt(null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void malformedStoredValuesAreRejectedWithoutLeakingDetails() {
        String tooShort = "v1." + Base64.getEncoder().encodeToString("short".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> cipher.decrypt("v1.%%%not-base64%%%"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Decryption failed");
        assertThatThrownBy(() -> cipher.decrypt(tooShort))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Decryption failed");
    }

    @Test
    void aKeyThatIsNotBase64IsRefusedAtStartup() {
        assertThatThrownBy(() -> new FieldCipher("this is not base64!!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("base64");
    }

    @Test
    void aKeyOfTheWrongLengthIsRefusedAtStartup() {
        assertThatThrownBy(() -> new FieldCipher(keyOf(1, 16)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }
}