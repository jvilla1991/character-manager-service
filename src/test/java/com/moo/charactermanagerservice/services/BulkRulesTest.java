package com.moo.charactermanagerservice.services;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Weight-band fallback for items without an official catalog bulk rating. */
class BulkRulesTest {

    private static BigDecimal fromWeight(String weight) {
        return BulkRules.bulkFromWeight(weight == null ? null : new BigDecimal(weight));
    }

    @Test
    void unknownWeightDefaultsToSmall() {
        assertThat(fromWeight(null)).isEqualByComparingTo("1");
    }

    @Test
    void negligibleWeightIsTiny() {
        assertThat(fromWeight("0")).isEqualByComparingTo("0.2");
    }

    @Test
    void bandBoundariesAreInclusive() {
        assertThat(fromWeight("2")).isEqualByComparingTo("1");
        assertThat(fromWeight("2.1")).isEqualByComparingTo("2");
        assertThat(fromWeight("5")).isEqualByComparingTo("2");
        assertThat(fromWeight("10")).isEqualByComparingTo("3");
        assertThat(fromWeight("35")).isEqualByComparingTo("6");
        assertThat(fromWeight("70")).isEqualByComparingTo("9");
        assertThat(fromWeight("71")).isEqualByComparingTo("9");
    }

    @Test
    void catalogBulkWinsOverWeight() {
        assertThat(BulkRules.bulkFor(new BigDecimal("9"), new BigDecimal("1")))
                .isEqualByComparingTo("9");
        assertThat(BulkRules.bulkFor(null, new BigDecimal("1")))
                .isEqualByComparingTo("1");
    }
}
