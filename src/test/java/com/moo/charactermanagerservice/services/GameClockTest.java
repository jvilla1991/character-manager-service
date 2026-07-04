package com.moo.charactermanagerservice.services;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/** The v2 clock: free-text date labels, three segments, weekday/week fields. */
class GameClockTest {

    @Test
    void initial_isDayOneMorning_weekOne() {
        assertThat(GameClock.initial())
                .containsEntry("year", "1")
                .containsEntry("month", "1")
                .containsEntry("day", "1")
                .containsEntry("timeOfDay", "morning")
                .containsEntry("weekday", null)
                .containsEntry("week", 1);
    }

    @Test
    void advances_throughTheThreeSegments_withoutTouchingTheDate() {
        Map<String, Object> t = GameClock.of("1492 DR", "Hammer", "3rd", "morning",
                "Far", new ArrayList<>(List.of("Far")), 2);
        t = GameClock.advanceSegment(t);
        assertThat(t).containsEntry("timeOfDay", "noon").containsEntry("day", "3rd");
        t = GameClock.advanceSegment(t);
        assertThat(t).containsEntry("timeOfDay", "night").containsEntry("day", "3rd");
        t = GameClock.advanceSegment(t);
        // night wraps to morning; the DATE stays — free text is the DM's to edit
        assertThat(t)
                .containsEntry("timeOfDay", "morning")
                .containsEntry("day", "3rd")
                .containsEntry("weekday", "Far")
                .containsEntry("week", 2);
    }

    @Test
    void normalize_convertsThePreV2NumericShape() {
        Map<String, Object> old = Map.of("year", 1492, "month", 3, "day", 12, "timeOfDay", "dawn");
        assertThat(GameClock.normalize(old))
                .containsEntry("year", "1492")
                .containsEntry("month", "3")
                .containsEntry("day", "12")
                .containsEntry("timeOfDay", "morning")
                .containsEntry("weekday", null)
                .containsEntry("week", 1);
        assertThat(GameClock.normalize(Map.of("timeOfDay", "dusk")))
                .containsEntry("timeOfDay", "night");
    }

    @Test
    void validatesSegmentsAndLabels() {
        assertThat(GameClock.isValidSegment("morning")).isTrue();
        assertThat(GameClock.isValidSegment("dawn")).isFalse(); // v1 segment no longer accepted
        assertThat(GameClock.isValidSegment(null)).isFalse();
        assertThat(GameClock.isValidLabel("Hammer")).isTrue();
        assertThat(GameClock.isValidLabel(null)).isTrue();
        assertThat(GameClock.isValidLabel("x".repeat(41))).isFalse();
    }
}
