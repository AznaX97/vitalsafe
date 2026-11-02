package com.vitalsafe.api.vitals;

import java.time.Instant;

/** Wide default limits, so queries never need a null date parameter. */
public final class TimeBounds {

    public static final Instant EARLIEST = Instant.parse("1900-01-01T00:00:00Z");
    public static final Instant LATEST = Instant.parse("2200-01-01T00:00:00Z");

    private TimeBounds() {
    }
}