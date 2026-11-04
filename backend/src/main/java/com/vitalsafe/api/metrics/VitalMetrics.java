package com.vitalsafe.api.metrics;

import com.vitalsafe.api.vitals.VitalType;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Business counters. Labels are a small fixed set (reading type, source, result). They never carry a user id,
 * an email or a value, because labels are stored for good and appear on dashboards.
 */
@Component
public class VitalMetrics {

    private final MeterRegistry registry;

    public VitalMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void readingSaved(VitalType type, String source) {
        registry.counter("vitalsafe.readings.saved", "type", type.name(), "source", source).increment();
    }

    public void csvRows(int imported, int skippedDuplicates) {
        if (imported > 0) {
            registry.counter("vitalsafe.csv.rows", "result", "imported").increment(imported);
        }
        if (skippedDuplicates > 0) {
            registry.counter("vitalsafe.csv.rows", "result", "skipped_duplicate").increment(skippedDuplicates);
        }
    }
}