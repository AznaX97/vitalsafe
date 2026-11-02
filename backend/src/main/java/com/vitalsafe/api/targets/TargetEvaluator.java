package com.vitalsafe.api.targets;

import java.math.BigDecimal;

/** Compares a reading with the range the USER set. It describes position only and never judges health. */
public final class TargetEvaluator {

    public enum Status { WITHIN, ABOVE, BELOW, MIXED }

    private TargetEvaluator() {
    }

    /** MIXED means one part is above its bound while another part is below its own (for example blood pressure). */
    public static Status classify(BigDecimal primary, BigDecimal secondary, TargetRange range) {
        boolean above = exceeds(primary, range.getHighPrimary());
        boolean below = falls(primary, range.getLowPrimary());
        if (secondary != null) {
            above = above || exceeds(secondary, range.getHighSecondary());
            below = below || falls(secondary, range.getLowSecondary());
        }
        if (above && below) {
            return Status.MIXED;
        }
        if (above) {
            return Status.ABOVE;
        }
        if (below) {
            return Status.BELOW;
        }
        return Status.WITHIN;
    }

    private static boolean exceeds(BigDecimal value, BigDecimal high) {
        return high != null && value.compareTo(high) > 0;
    }

    private static boolean falls(BigDecimal value, BigDecimal low) {
        return low != null && value.compareTo(low) < 0;
    }
}