package com.moo.charactermanagerservice.services;

import com.moo.charactermanagerservice.dto.SurvivalAction;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/** Darker Dungeons ch. 31 stage math: time-of-day bumps, player actions, clamping. */
class SurvivalRulesTest {

    @Test
    void normalize_defaultsMissingStagesToZero_andClamps() {
        assertThat(SurvivalRules.normalize(Map.of()))
                .containsEntry("hunger", 0)
                .containsEntry("thirst", 0)
                .containsEntry("fatigue", 0);
        assertThat(SurvivalRules.normalize(Map.of("hunger", 11, "fatigue", -2)))
                .containsEntry("hunger", 6)
                .containsEntry("fatigue", 0);
    }

    @Test
    void dawn_bumpsHungerAndThirst() {
        Map<String, Object> out =
                SurvivalRules.applyTimeBump(Map.of("hunger", 2, "thirst", 2, "fatigue", 2), "dawn");
        assertThat(out)
                .containsEntry("hunger", 3)
                .containsEntry("thirst", 3)
                .containsEntry("fatigue", 2);
    }

    @Test
    void noon_bumpsFatigueOnly() {
        assertThat(SurvivalRules.applyTimeBump(Map.of(), "noon"))
                .containsEntry("hunger", 0)
                .containsEntry("thirst", 0)
                .containsEntry("fatigue", 1);
    }

    @Test
    void dusk_bumpsAllThree_clampedAtStarving() {
        Map<String, Object> out =
                SurvivalRules.applyTimeBump(Map.of("hunger", 6, "thirst", 5, "fatigue", 0), "dusk");
        assertThat(out)
                .containsEntry("hunger", 6)   // already starving — stays 6
                .containsEntry("thirst", 6)
                .containsEntry("fatigue", 1);
    }

    @Test
    void night_isTheSleepWindow_noBumps() {
        assertThat(SurvivalRules.applyTimeBump(Map.of("hunger", 3), "night"))
                .containsEntry("hunger", 3)
                .containsEntry("thirst", 0)
                .containsEntry("fatigue", 0);
    }

    @Test
    void actions_applyTheBookDeltas_flooredAtZero() {
        assertThat(SurvivalRules.applyAction(Map.of("hunger", 4), SurvivalAction.EAT))
                .containsEntry("hunger", 3);
        assertThat(SurvivalRules.applyAction(Map.of("thirst", 1), SurvivalAction.DRINK))
                .containsEntry("thirst", 0);
        assertThat(SurvivalRules.applyAction(Map.of("fatigue", 2), SurvivalAction.SLEEP_GOOD))
                .containsEntry("fatigue", 0); // −3 floored at 0
        assertThat(SurvivalRules.applyAction(Map.of("fatigue", 5), SurvivalAction.SLEEP_DISTURBED))
                .containsEntry("fatigue", 4);
    }
}
