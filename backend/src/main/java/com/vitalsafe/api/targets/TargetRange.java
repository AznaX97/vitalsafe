package com.vitalsafe.api.targets;

import com.vitalsafe.api.crypto.EncryptedDecimalConverter;
import com.vitalsafe.api.vitals.VitalType;
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

/** A range the USER chose for one kind of reading. VitalSafe never decides what is healthy. */
@Entity
@Table(name = "target_ranges")
@Getter
@Setter
@NoArgsConstructor
public class TargetRange {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private VitalType type;

    @Column(nullable = false, length = 16, updatable = false)
    private String unit;

    // The bounds are encrypted before they reach the database
    @Convert(converter = EncryptedDecimalConverter.class)
    @Column(name = "low_primary")
    private BigDecimal lowPrimary;

    @Convert(converter = EncryptedDecimalConverter.class)
    @Column(name = "high_primary")
    private BigDecimal highPrimary;

    @Convert(converter = EncryptedDecimalConverter.class)
    @Column(name = "low_secondary")
    private BigDecimal lowSecondary;

    @Convert(converter = EncryptedDecimalConverter.class)
    @Column(name = "high_secondary")
    private BigDecimal highSecondary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}