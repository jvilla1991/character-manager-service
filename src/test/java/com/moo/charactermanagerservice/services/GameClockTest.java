package com.moo.charactermanagerservice.services;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/** The in-world clock: segment cycle and the simplified 30/12 calendar rollover. */
class GameClockTest {

    @Test
    void initial_isDayOneDawn() {
        assertThat(GameClock.initial())
                .containsEntry("year", 1)
                .containsEntry("month", 1)
                .containsEntry("day", 1)
                .containsEntry("timeOfDay", "dawn");
    }

    @Test
    void advances_throughTheDaySegments() {
        Map<String, Object> t = GameClock.of(1492, 3, 12, "dawn");
        t = GameClock.advanceSegment(t);
        assertThat(t).containsEntry("timeOfDay", "noon").containsEntry("day", 12);
        t = GameClock.advanceSegment(t);
        assertThat(t).containsEntry("timeOfDay", "dusk").containsEntry("day", 12);
        t = GameClock.advanceSegment(t);
        assertThat(t).containsEntry("timeOfDay", "night").containsEntry("day", 12);
    }

    @Test
    void advancingPastNight_startsTheNextDay() {
        Map<String, Object> t = GameClock.advanceSegment(GameClock.of(1492, 3, 12, "night"));
        assertThat(t)
                .containsEntry("timeOfDay", "dawn")
                .containsEntry("day", 13)
                .containsEntry("month", 3)
                .containsEntry("year", 1492);
    }

    @Test
    void rollsOver_monthAndYear() {
        assertThat(GameClock.advanceSegment(GameClock.of(1492, 3, 30, "night")))
                .containsEntry("day", 1)
                .containsEntry("month", 4)
                .containsEntry("year", 1492);
        assertThat(GameClock.advanceSegment(GameClock.of(1492, 12, 30, "night")))
                .containsEntry("day", 1)
                .containsEntry("month", 1)
                .containsEntry("year", 1493);
    }

    @Test
    void repairsAMalformedSegment_withoutAdvancingTheDay() {
        Map<String, Object> t = GameClock.advanceSegment(GameClock.of(5, 2, 9, "high-noon"));
        assertThat(t)
                .containsEntry("timeOfDay", "dawn")
                .containsEntry("day", 9)
                .containsEntry("month", 2)
                .containsEntry("year", 5);
    }

    @Test
    void validatesSegments() {
        assertThat(GameClock.isValidSegment("dawn")).isTrue();
        assertThat(GameClock.isValidSegment("night")).isTrue();
        assertThat(GameClock.isValidSegment("midnight")).isFalse();
        assertThat(GameClock.isValidSegment(null)).isFalse();
    }
}
