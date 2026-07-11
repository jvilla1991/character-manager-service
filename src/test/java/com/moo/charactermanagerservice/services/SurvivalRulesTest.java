package com.moo.charactermanagerservice.services;

import com.moo.charactermanagerservice.dto.SurvivalAction;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/** Darker Dungeons ch. 31 stage math: day-segment bumps, consume actions, clamping. */
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
    void morning_raisesHungerAndThirst() {
        // The dawn boundary: +1 hunger, +1 thirst — no fatigue step.
        Map<String, Object> out =
                SurvivalRules.applySegment(Map.of("hunger", 2, "thirst", 2, "fatigue", 2), "morning");
        assertThat(out)
                .containsEntry("hunger", 3)
                .containsEntry("thirst", 3)
                .containsEntry("fatigue", 2);
    }

    @Test
    void noon_raisesFatigueOnly() {
        assertThat(SurvivalRules.applySegment(Map.of(), "noon"))
                .containsEntry("hunger", 2)
                .containsEntry("thirst", 2)
                .containsEntry("fatigue", 3);
    }

    @Test
    void night_raisesAllThree() {
        Map<String, Object> out =
                SurvivalRules.applySegment(Map.of("hunger", 2, "thirst", 3, "fatigue", 5), "night");
        assertThat(out)
                .containsEntry("hunger", 3)
                .containsEntry("thirst", 4)
                .containsEntry("fatigue", 6);
    }

    @Test
    void night_clampsAtTheWorstStage() {
        Map<String, Object> out =
                SurvivalRules.applySegment(Map.of("hunger", 6, "thirst", 5, "fatigue", 0), "night");
        assertThat(out)
                .containsEntry("hunger", 6)
                .containsEntry("thirst", 6)
                .containsEntry("fatigue", 1);
    }

    @Test
    void applySegment_preservesExtraKeys_likeSeeded() {
        Map<String, Object> out = SurvivalRules.applySegment(
                Map.of("hunger", 2, "thirst", 2, "fatigue", 2, "seeded", true), "noon");
        assertThat(out).containsEntry("seeded", true);
    }

    @Test
    void applyAction_improvesTheMatchingStage() {
        Map<String, Object> stages = Map.of("hunger", 4, "thirst", 4, "fatigue", 4);
        assertThat(SurvivalRules.applyAction(stages, SurvivalAction.EAT)).containsEntry("hunger", 3);
        assertThat(SurvivalRules.applyAction(stages, SurvivalAction.DRINK)).containsEntry("thirst", 3);
        assertThat(SurvivalRules.applyAction(stages, SurvivalAction.SLEEP_GOOD)).containsEntry("fatigue", 1);
        assertThat(SurvivalRules.applyAction(stages, SurvivalAction.SLEEP_DISTURBED)).containsEntry("fatigue", 3);
    }

    @Test
    void applyAction_preservesExtraKeys_likeSeeded() {
        Map<String, Object> out = SurvivalRules.applyAction(
                Map.of("hunger", 4, "thirst", 2, "fatigue", 2, "seeded", true), SurvivalAction.EAT);
        assertThat(out).containsEntry("seeded", true).containsEntry("hunger", 3);
    }

    @Test
    void bump_appliesADeltaFlooredAndCapped() {
        assertThat(SurvivalRules.bump(Map.of("fatigue", 2), "fatigue", -3)).containsEntry("fatigue", 0);
        assertThat(SurvivalRules.bump(Map.of("fatigue", 5), "fatigue", -1)).containsEntry("fatigue", 4);
    }

    @Test
    void bump_preservesExtraKeys_likeSeeded() {
        assertThat(SurvivalRules.bump(Map.of("fatigue", 5, "seeded", true), "fatigue", -1))
                .containsEntry("fatigue", 4)
                .containsEntry("seeded", true);
    }
}
