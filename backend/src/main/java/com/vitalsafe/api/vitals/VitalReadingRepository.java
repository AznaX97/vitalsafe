package com.vitalsafe.api.vitals;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface VitalReadingRepository extends JpaRepository<VitalReading, UUID> {

    // Always filter by BOTH id and owner, so one user can never reach another user's reading
    Optional<VitalReading> findByIdAndUserId(UUID id, UUID userId);

    // The date range is never null (callers pass wide defaults), which avoids null-parameter problems.
    // The id is a tie-breaker, so paging stays stable when two readings share a time.
    Page<VitalReading> findByUserIdAndMeasuredAtBetweenOrderByMeasuredAtDescIdAsc(
            UUID userId, Instant from, Instant to, Pageable pageable);

    Page<VitalReading> findByUserIdAndTypeAndMeasuredAtBetweenOrderByMeasuredAtDescIdAsc(
            UUID userId, VitalType type, Instant from, Instant to, Pageable pageable);
}