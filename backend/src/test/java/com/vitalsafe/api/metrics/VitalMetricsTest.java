package com.vitalsafe.api.metrics;

import com.vitalsafe.api.vitals.VitalType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VitalMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final VitalMetrics metrics = new VitalMetrics(registry);

    @Test
    void savedReadingsAreCountedByTypeAndSource() {
        metrics.readingSaved(VitalType.HEART_RATE, "api");
        metrics.readingSaved(VitalType.HEART_RATE, "api");
        metrics.readingSaved(VitalType.BODY_WEIGHT, "api");

        assertThat(registry.get("vitalsafe.readings.saved").tags("type", "HEART_RATE", "source", "api").counter().count())
                .isEqualTo(2.0);
        assertThat(registry.get("vitalsafe.readings.saved").tags("type", "BODY_WEIGHT", "source", "api").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void importedAndSkippedRowsAreCountedSeparately() {
        metrics.csvRows(5, 2);

        assertThat(registry.get("vitalsafe.csv.rows").tag("result", "imported").counter().count()).isEqualTo(5.0);
        assertThat(registry.get("vitalsafe.csv.rows").tag("result", "skipped_duplicate").counter().count()).isEqualTo(2.0);
    }

    @Test
    void zeroCountsCreateNoCounters() {
        metrics.csvRows(0, 0);

        assertThat(registry.find("vitalsafe.csv.rows").counter()).isNull();
    }

    @Test
    void noLabelCanHoldAnythingButTheFixedSet() {
        metrics.readingSaved(VitalType.BLOOD_PRESSURE, "csv");

        assertThat(registry.getMeters()).allSatisfy(meter ->
                assertThat(meter.getId().getTags()).extracting(tag -> tag.getKey()).containsOnly("type", "source"));
    }
}