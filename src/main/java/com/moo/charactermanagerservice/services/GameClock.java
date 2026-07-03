package com.moo.charactermanagerservice.services;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The in-world campaign clock: {@code {year, month, day, timeOfDay}} with the
 * Darker Dungeons day segments dawn → noon → dusk → night. Pure map-in/map-out
 * helpers over the JSON shape stored in {@code campaign.game_time}.
 *
 * <p>Calendar is deliberately simple and setting-agnostic: 12 months of 30 days.
 * The DM can set the date directly to match any homebrew calendar.
 */
final class GameClock {

    static final List<String> SEGMENTS = List.of("dawn", "noon", "dusk", "night");
    static final int DAYS_PER_MONTH = 30;
    static final int MONTHS_PER_YEAR = 12;

    private GameClock() {}

    /** The clock a campaign starts at the first time the DM advances time. */
    static Map<String, Object> initial() {
        return of(1, 1, 1, "dawn");
    }

    static Map<String, Object> of(int year, int month, int day, String timeOfDay) {
        Map<String, Object> time = new LinkedHashMap<>();
        time.put("year", year);
        time.put("month", month);
        time.put("day", day);
        time.put("timeOfDay", timeOfDay);
        return time;
    }

    static boolean isValidSegment(String timeOfDay) {
        return timeOfDay != null && SEGMENTS.contains(timeOfDay);
    }

    /**
     * Advance one segment; wrapping past night starts the next day (with month
     * and year rollover). A malformed stored value is repaired to a normalized
     * clock at dawn rather than failing the whole advance.
     */
    static Map<String, Object> advanceSegment(Map<String, Object> gameTime) {
        int year = intOf(gameTime.get("year"), 1);
        int month = intOf(gameTime.get("month"), 1);
        int day = intOf(gameTime.get("day"), 1);
        int idx = SEGMENTS.indexOf(String.valueOf(gameTime.get("timeOfDay")));
        if (idx < 0) {
            return of(year, month, day, "dawn"); // repair, don't advance
        }

        int next = (idx + 1) % SEGMENTS.size();
        if (next == 0) { // night → next day's dawn
            day++;
            if (day > DAYS_PER_MONTH) {
                day = 1;
                month++;
                if (month > MONTHS_PER_YEAR) {
                    month = 1;
                    year++;
                }
            }
        }
        return of(year, month, day, SEGMENTS.get(next));
    }

    private static int intOf(Object value, int fallback) {
        return value instanceof Number n ? n.intValue() : fallback;
    }
}
