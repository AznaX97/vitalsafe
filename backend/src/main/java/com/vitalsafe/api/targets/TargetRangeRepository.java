package com.vitalsafe.api.targets;

import com.vitalsafe.api.vitals.VitalType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TargetRangeRepository extends JpaRepository<TargetRange, UUID> {

    List<TargetRange> findByUserIdOrderByTypeAscUnitAsc(UUID userId);

    // Always includes the owner, so one user can never reach another user's range
    Optional<TargetRange> findByUserIdAndTypeAndUnit(UUID userId, VitalType type, String unit);
}