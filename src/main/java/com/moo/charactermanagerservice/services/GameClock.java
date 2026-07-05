package com.moo.charactermanagerservice.services;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The in-world campaign clock: free-text date labels the DM curates plus a
 * three-segment day cycle. Shape of {@code campaign.game_time}:
 *
 * <pre>{"year":"1492 DR","month":"Hammer","day":"3rd","timeOfDay":"morning",
 *  "weekday":"Far","weekdaysSeen":["Sul","Mol"],"week":1}</pre>
 *
 * Advancing time only cycles morning → noon → night — the DATE is free text
 * (any homebrew calendar), so the DM updates it by hand when a new day starts.
 * The weekday history powers the week counter: re-entering any previously
 * seen weekday marks a completed week (handled in the session service).
 *
 * <p>Older clocks stored numeric dates with dawn/noon/dusk/night segments;
 * {@link #normalize} converts those on read (numbers → strings, dawn →
 * morning, dusk/night → night) so no SQL migration is needed.
 */
final class GameClock {

    static final List<String> SEGMENTS = List.of("morning", "noon", "night");
    static final int MAX_LABEL_LENGTH = 40;

    private GameClock() {}

    /** The clock a campaign starts at the first time the DM advances time. */
    static Map<String, Object> initial() {
        return of("1", "1", "1", "morning", null, new ArrayList<>(), 1);
    }

    static Map<String, Object> of(String year, String month, String day, String timeOfDay,
                                  String weekday, List<String> weekdaysSeen, int week) {
        Map<String, Object> time = new LinkedHashMap<>();
        time.put("year", year);
        time.put("month", month);
        time.put("day", day);
        time.put("timeOfDay", timeOfDay);
        time.put("weekday", weekday);
        time.put("weekdaysSeen", weekdaysSeen);
        time.put("week", week);
        return time;
    }

    static boolean isValidSegment(String timeOfDay) {
        return timeOfDay != null && SEGMENTS.contains(timeOfDay);
    }

    static boolean isValidLabel(String label) {
        return label == null || label.length() <= MAX_LABEL_LENGTH;
    }

    /**
     * Advance one segment: morning → noon → night → next day's MORNING. The
     * date never changes here — it's free text the DM curates (the client
     * opens the edit form on the night → morning rollover).
     */
    static Map<String, Object> advanceSegment(Map<String, Object> gameTime) {
        Map<String, Object> time = normalize(gameTime);
        int idx = SEGMENTS.indexOf(String.valueOf(time.get("timeOfDay")));
        time.put("timeOfDay", SEGMENTS.get((idx + 1) % SEGMENTS.size()));
        return time;
    }

    /**
     * Normalized copy in the current shape, converting pre-v2 clocks:
     * numeric date parts become strings, dawn → morning, dusk/night → night,
     * and the weekday/week fields get their defaults.
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> normalize(Map<String, Object> gameTime) {
        String segment = switch (String.valueOf(gameTime.get("timeOfDay"))) {
            case "morning", "dawn" -> "morning";
            case "noon" -> "noon";
            default -> "night"; // night, dusk, or anything malformed
        };
        Object seen = gameTime.get("weekdaysSeen");
        List<String> weekdaysSeen = seen instanceof List<?> list
                ? new ArrayList<>((List<String>) list)
                : new ArrayList<>();
        Object weekday = gameTime.get("weekday");
        Object week = gameTime.get("week");
        return of(
                labelOf(gameTime.get("year"), "1"),
                labelOf(gameTime.get("month"), "1"),
                labelOf(gameTime.get("day"), "1"),
                segment,
                weekday instanceof String s && !s.isBlank() ? s : null,
                weekdaysSeen,
                week instanceof Number n ? Math.max(1, n.intValue()) : 1);
    }

    private static String labelOf(Object value, String fallback) {
        if (value instanceof String s) return s.isBlank() ? fallback : s;
        if (value instanceof Number n) return String.valueOf(n.intValue());
        return fallback;
    }
}
