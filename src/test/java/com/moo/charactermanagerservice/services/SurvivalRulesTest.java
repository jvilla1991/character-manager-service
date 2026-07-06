package com.moo.charactermanagerservice.services;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/** Darker Dungeons ch. 31 stage math: the auto-consume day-segment and clamping. */
class SurvivalRulesTest {

    @Test
    void normalize_defaultsMissingStagesToOk_andClamps() {
        // Never-tracked = "Ok" (stage 2), the table's neutral row — not
        // "Stuffed" (stage 0), which would grant −1 exhaustion for free.
        assertThat(SurvivalRules.normalize(Map.of()))
                .containsEntry("hunger", 2)
                .containsEntry("thirst", 2)
                .containsEntry("fatigue", 2);
        assertThat(SurvivalRules.normalize(Map.of("hunger", 11, "fatigue", -2)))
                .containsEntry("hunger", 6)
                .containsEntry("fatigue", 0);
    }

    @Test
    void morning_whenFed_holdsHungerAndThirst() {
        // ate + drank → hunger/thirst do NOT rise; morning has no fatigue step
        Map<String, Object> out =
                SurvivalRules.applySegment(Map.of("hunger", 2, "thirst", 2, "fatigue", 2), "morning", true, true);
        assertThat(out)
                .containsEntry("hunger", 2)
                .containsEntry("thirst", 2)
                .containsEntry("fatigue", 2);
    }

    @Test
    void morning_whenOutOfSupplies_raisesHungerAndThirst() {
        Map<String, Object> out =
                SurvivalRules.applySegment(Map.of("hunger", 2, "thirst", 2, "fatigue", 2), "morning", false, false);
        assertThat(out)
                .containsEntry("hunger", 3)
                .containsEntry("thirst", 3)
                .containsEntry("fatigue", 2);
    }

    @Test
    void noon_raisesFatigueOnly_regardlessOfSupplies() {
        assertThat(SurvivalRules.applySegment(Map.of(), "noon", false, false))
                .containsEntry("hunger", 2)
                .containsEntry("thirst", 2)
                .containsEntry("fatigue", 3);
    }

    @Test
    void night_whenFed_stillRaisesFatigue_butHoldsHungerThirst() {
        Map<String, Object> out =
                SurvivalRules.applySegment(Map.of("hunger", 5, "thirst", 5, "fatigue", 5), "night", true, true);
        assertThat(out)
                .containsEntry("hunger", 5)   // fed → held
                .containsEntry("thirst", 5)
                .containsEntry("fatigue", 6);  // fatigue always climbs (clamped at 6)
    }

    @Test
    void night_whenStarving_raisesAllThree_clampedAtStarving() {
        Map<String, Object> out =
                SurvivalRules.applySegment(Map.of("hunger", 6, "thirst", 5, "fatigue", 0), "night", false, false);
        assertThat(out)
                .containsEntry("hunger", 6)
                .containsEntry("thirst", 6)
                .containsEntry("fatigue", 1);
    }

    @Test
    void applySegment_preservesExtraKeys_likeSeeded() {
        Map<String, Object> out = SurvivalRules.applySegment(
                Map.of("hunger", 2, "thirst", 2, "fatigue", 2, "seeded", true), "noon", false, false);
        assertThat(out).containsEntry("seeded", true);
    }

    @Test
    void isSupplyStep_isTrueForMorningAndNight() {
        assertThat(SurvivalRules.isSupplyStep("morning")).isTrue();
        assertThat(SurvivalRules.isSupplyStep("night")).isTrue();
        assertThat(SurvivalRules.isSupplyStep("noon")).isFalse();
    }

    @Test
    void bump_appliesADeltaFlooredAndCapped() {
        assertThat(SurvivalRules.bump(Map.of("fatigue", 2), "fatigue", -3)).containsEntry("fatigue", 0);
        assertThat(SurvivalRules.bump(Map.of("fatigue", 5), "fatigue", -1)).containsEntry("fatigue", 4);
    }
}
