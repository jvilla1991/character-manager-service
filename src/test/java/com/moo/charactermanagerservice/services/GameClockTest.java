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
    void advances_throughTheThreeSegments_steppingANumericDayOnTheRollover() {
        Map<String, Object> t = GameClock.of("1492 DR", "Hammer", "3rd", "morning",
                "Far", new ArrayList<>(List.of("Far")), 2);
        t = GameClock.advanceSegment(t);
        assertThat(t).containsEntry("timeOfDay", "noon").containsEntry("day", "3rd");
        t = GameClock.advanceSegment(t);
        assertThat(t).containsEntry("timeOfDay", "night").containsEntry("day", "3rd");
        t = GameClock.advanceSegment(t);
        // night wraps to morning: a countable day steps forward; the rest of
        // the date stays — free text is the DM's to edit
        assertThat(t)
                .containsEntry("timeOfDay", "morning")
                .containsEntry("day", "4th")
                .containsEntry("month", "Hammer")
                .containsEntry("year", "1492 DR")
                .containsEntry("weekday", "Far")
                .containsEntry("week", 2);
    }

    @Test
    void advanceSegment_leavesAFreeTextDayAlone_onTheRollover() {
        Map<String, Object> t = GameClock.of("1", "1", "Midwinter", "night",
                null, new ArrayList<>(), 1);
        t = GameClock.advanceSegment(t);
        // the clock can't count "Midwinter" — the DM updates it by hand
        assertThat(t).containsEntry("timeOfDay", "morning").containsEntry("day", "Midwinter");
    }

    @Test
    void incrementDayLabel_countsNumbersAndOrdinals_leavingTheRestAlone() {
        // bare number in → bare number out
        assertThat(GameClock.incrementDayLabel("3")).isEqualTo("4");
        assertThat(GameClock.incrementDayLabel("9")).isEqualTo("10");
        // ordinal in → correct ordinal out
        assertThat(GameClock.incrementDayLabel("3rd")).isEqualTo("4th");
        assertThat(GameClock.incrementDayLabel("20th")).isEqualTo("21st");
        assertThat(GameClock.incrementDayLabel("21st")).isEqualTo("22nd");
        assertThat(GameClock.incrementDayLabel("22nd")).isEqualTo("23rd");
        assertThat(GameClock.incrementDayLabel("1st")).isEqualTo("2nd");
        // the 11th–13th family always takes "th"
        assertThat(GameClock.incrementDayLabel("10th")).isEqualTo("11th");
        assertThat(GameClock.incrementDayLabel("11th")).isEqualTo("12th");
        assertThat(GameClock.incrementDayLabel("12th")).isEqualTo("13th");
        assertThat(GameClock.incrementDayLabel("112th")).isEqualTo("113th");
        // unparseable, null, and blank pass through unchanged
        assertThat(GameClock.incrementDayLabel("Midwinter")).isEqualTo("Midwinter");
        assertThat(GameClock.incrementDayLabel("the third day")).isEqualTo("the third day");
        assertThat(GameClock.incrementDayLabel(null)).isNull();
        assertThat(GameClock.incrementDayLabel("  ")).isEqualTo("  ");
    }

    // --- defined week (weekDays) ---

    @Test
    void advanceSegment_withDefinedWeek_walksTheWeekdayOnTheNightToMorningRollover() {
        List<String> week = List.of("Sul", "Mol", "Zol");
        Map<String, Object> t = GameClock.of("1", "1", "1", "night",
                "Sul", new ArrayList<>(), 1);

        t = GameClock.advanceSegment(t, week);

        assertThat(t)
                .containsEntry("timeOfDay", "morning")
                .containsEntry("weekday", "Mol")
                .containsEntry("week", 1); // no wrap — the counter holds
    }

    @Test
    void advanceSegment_withDefinedWeek_wrapPastTheLastDay_incrementsTheWeek() {
        List<String> week = List.of("Sul", "Mol", "Zol");
        // "zol" — case-insensitive match, canonicalized on the way out
        Map<String, Object> t = GameClock.of("1", "1", "1", "night",
                "zol", new ArrayList<>(), 4);

        t = GameClock.advanceSegment(t, week);

        assertThat(t)
                .containsEntry("weekday", "Sul")
                .containsEntry("week", 5);
    }

    @Test
    void advanceSegment_withDefinedWeek_unknownOrNullWeekday_isLeftUntouched() {
        List<String> week = List.of("Sul", "Mol", "Zol");
        // definition edited mid-campaign — "Far" is no longer a defined day
        Map<String, Object> orphaned = GameClock.advanceSegment(
                GameClock.of("1", "1", "1", "night", "Far", new ArrayList<>(), 3), week);
        assertThat(orphaned)
                .containsEntry("timeOfDay", "morning")
                .containsEntry("weekday", "Far")
                .containsEntry("week", 3);

        Map<String, Object> unset = GameClock.advanceSegment(
                GameClock.of("1", "1", "1", "night", null, new ArrayList<>(), 3), week);
        assertThat(unset)
                .containsEntry("weekday", null)
                .containsEntry("week", 3);
    }

    @Test
    void advanceSegment_withDefinedWeek_nonRolloverSegments_neverMoveTheWeekday() {
        List<String> week = List.of("Sul", "Mol", "Zol");
        Map<String, Object> t = GameClock.of("1", "1", "1", "morning",
                "Sul", new ArrayList<>(), 1);

        t = GameClock.advanceSegment(t, week); // morning → noon
        assertThat(t).containsEntry("weekday", "Sul").containsEntry("week", 1);
        t = GameClock.advanceSegment(t, week); // noon → night
        assertThat(t).containsEntry("weekday", "Sul").containsEntry("week", 1);
    }

    @Test
    void isValidWeekDays_enforcesSizeBlanksLengthAndUniqueness() {
        assertThat(GameClock.isValidWeekDays(List.of("Sul", "Mol"))).isTrue();
        assertThat(GameClock.isValidWeekDays(null)).isFalse();
        assertThat(GameClock.isValidWeekDays(List.of("Sul"))).isFalse();          // too few
        assertThat(GameClock.isValidWeekDays(
                java.util.stream.IntStream.rangeClosed(1, 21).mapToObj(i -> "Day " + i).toList()))
                .isFalse();                                                        // too many
        assertThat(GameClock.isValidWeekDays(java.util.Arrays.asList("Sul", " "))).isFalse();  // blank
        assertThat(GameClock.isValidWeekDays(java.util.Arrays.asList("Sul", null))).isFalse(); // null entry
        assertThat(GameClock.isValidWeekDays(List.of("Sul", "sul"))).isFalse();   // dup, case-insensitive
        assertThat(GameClock.isValidWeekDays(List.of("Sul", "x".repeat(41)))).isFalse(); // over-length
    }

    @Test
    void canonicalDay_matchesCaseInsensitively_returningTheDefinedCasing() {
        List<String> week = List.of("Sul", "Mol", "Zol");
        assertThat(GameClock.canonicalDay(week, "mol")).isEqualTo("Mol");
        assertThat(GameClock.canonicalDay(week, "Mol")).isEqualTo("Mol");
        assertThat(GameClock.canonicalDay(week, "Far")).isNull();
        assertThat(GameClock.canonicalDay(week, null)).isNull();
        assertThat(GameClock.canonicalDay(null, "Mol")).isNull();
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
