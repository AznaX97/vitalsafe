package com.vitalsafe.api.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Encrypts a number as text when it is saved and turns it back into a number when it is loaded. */
@Component
@Converter
public class EncryptedDecimalConverter implements AttributeConverter<BigDecimal, String> {

    private final FieldCipher cipher;

    public EncryptedDecimalConverter(FieldCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public String convertToDatabaseColumn(BigDecimal attribute) {
        return attribute == null ? null : cipher.encrypt(attribute.toPlainString());
    }

    @Override
    public BigDecimal convertToEntityAttribute(String stored) {
        return stored == null ? null : new BigDecimal(cipher.decrypt(stored));
    }
}