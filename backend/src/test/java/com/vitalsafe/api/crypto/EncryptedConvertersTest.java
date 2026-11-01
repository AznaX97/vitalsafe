package com.vitalsafe.api.crypto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class EncryptedConvertersTest {

    private final FieldCipher cipher = new FieldCipher(
            Base64.getEncoder().encodeToString("k".repeat(32).getBytes(StandardCharsets.UTF_8)));
    private final EncryptedStringConverter strings = new EncryptedStringConverter(cipher);
    private final EncryptedDecimalConverter decimals = new EncryptedDecimalConverter(cipher);

    @Test
    void textRoundTripsAndNullStaysNull() {
        assertThat(strings.convertToEntityAttribute(strings.convertToDatabaseColumn("note"))).isEqualTo("note");
        assertThat(strings.convertToDatabaseColumn(null)).isNull();
        assertThat(strings.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void numbersRoundTripWithTheirExactScale() {
        assertThat(decimals.convertToEntityAttribute(decimals.convertToDatabaseColumn(new BigDecimal("98.60"))))
                .isEqualTo(new BigDecimal("98.60"));
        assertThat(decimals.convertToEntityAttribute(decimals.convertToDatabaseColumn(new BigDecimal("120"))))
                .isEqualTo(new BigDecimal("120"));
    }

    @Test
    void numbersAreStoredAsCiphertextNotDigits() {
        assertThat(decimals.convertToDatabaseColumn(new BigDecimal("120"))).startsWith("v1.").isNotEqualTo("120");
        assertThat(decimals.convertToDatabaseColumn(null)).isNull();
        assertThat(decimals.convertToEntityAttribute(null)).isNull();
    }
}