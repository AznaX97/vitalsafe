package com.vitalsafe.api.vitals;

import com.vitalsafe.api.crypto.EncryptedDecimalConverter;
import com.vitalsafe.api.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vital_readings")
@Getter
@Setter
@NoArgsConstructor
public class VitalReading {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // The owner never changes, so a reading can never be handed to another user
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    // type, unit and time stay in plain text on purpose: the database sorts and filters on them
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private VitalType type;

    @Column(nullable = false, length = 16)
    private String unit;

    @Column(name = "measured_at", nullable = false)
    private Instant measuredAt;

    // The values and the note are encrypted before they reach the database
    @Convert(converter = EncryptedDecimalConverter.class)
    @Column(name = "value_primary", nullable = false)
    private BigDecimal valuePrimary;

    @Convert(converter = EncryptedDecimalConverter.class)
    @Column(name = "value_secondary")
    private BigDecimal valueSecondary;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "note")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}