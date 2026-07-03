package com.moo.charactermanagerservice.services;

import java.math.BigDecimal;

/**
 * Darker Dungeons object-bulk rules (ch. 10) for the slot-based inventory
 * campaign variant. Catalog items carry an official {@code bulk} rating;
 * anything else falls back to these weight bands.
 */
final class BulkRules {

    private BulkRules() {}

    /**
     * DD weight bands: negligible → 0.2, ≤2 lb → 1, ≤5 lb → 2, ≤10 lb → 3,
     * ≤35 lb → 6, heavier → 9. Unknown weight → 1 (a small object).
     */
    static BigDecimal bulkFromWeight(BigDecimal weight) {
        if (weight == null) return BigDecimal.ONE;
        double w = weight.doubleValue();
        if (w <= 0) return new BigDecimal("0.2");
        if (w <= 2) return BigDecimal.ONE;
        if (w <= 5) return new BigDecimal("2");
        if (w <= 10) return new BigDecimal("3");
        if (w <= 35) return new BigDecimal("6");
        return new BigDecimal("9");
    }

    /** Official catalog bulk when present, weight-band fallback otherwise. */
    static BigDecimal bulkFor(BigDecimal catalogBulk, BigDecimal weight) {
        return catalogBulk != null ? catalogBulk : bulkFromWeight(weight);
    }
}
