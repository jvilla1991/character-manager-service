package com.moo.charactermanagerservice.services;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The in-world campaign clock: free-text date labels the DM curates plus a
 * three-segment day cycle. Shape of {@code campaign.game_time}:
 *
 * <pre>{"year":"1492 DR","month":"Hammer","day":"3rd","timeOfDay":"morning",
 *  "weekday":"Far","weekdaysSeen":["Sul","Mol"],"week":1}</pre>
 *
 * Advancing time cycles morning → noon → night. The date parts are free text
 * (any homebrew calendar); on the night → morning rollover a NUMERIC day
 * label ("3" / "3rd") also steps forward — anything else the DM updates by
 * hand when a new day starts. The weekday history powers the week counter:
 * re-entering any previously seen weekday marks a completed week (handled in
 * the session service).
 *
 * <p>Older clocks stored numeric dates with dawn/noon/dusk/night segments;
 * {@link #normalize} converts those on read (numbers → strings, dawn →
 * morning, dusk/night → night) so no SQL migration is needed.
 */
final class GameClock {

    static final List<String> SEGMENTS = List.of("morning", "noon", "night");
    static final int MAX_LABEL_LENGTH = 40;
    static final int MIN_WEEK_DAYS = 2;
    static final int MAX_WEEK_DAYS = 20;

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
     * True when the list is a usable week definition: {@value #MIN_WEEK_DAYS}–
     * {@value #MAX_WEEK_DAYS} entries, each a non-blank label of at most
     * {@value #MAX_LABEL_LENGTH} characters, unique case-insensitively.
     */
    static boolean isValidWeekDays(List<String> weekDays) {
        if (weekDays == null
                || weekDays.size() < MIN_WEEK_DAYS || weekDays.size() > MAX_WEEK_DAYS) {
            return false;
        }
        Set<String> seen = new HashSet<>();
        for (String day : weekDays) {
            if (day == null || day.isBlank() || day.trim().length() > MAX_LABEL_LENGTH) {
                return false;
            }
            if (!seen.add(day.trim().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }

    /**
     * The defined-casing entry matching {@code day} case-insensitively, or null
     * when the day is not part of the definition (or either argument is null).
     */
    static String canonicalDay(List<String> weekDays, String day) {
        if (weekDays == null || day == null) return null;
        return weekDays.stream().filter(day::equalsIgnoreCase).findFirst().orElse(null);
    }

    /**
     * Advance one segment: morning → noon → night → next day's MORNING. The
     * rollover steps a numeric day label forward ({@link #incrementDayLabel});
     * the rest of the date is free text the DM curates (the client opens the
     * edit form on the night → morning rollover).
     */
    static Map<String, Object> advanceSegment(Map<String, Object> gameTime) {
        return advanceSegment(gameTime, null);
    }

    /**
     * Advance one segment with an optional week definition. On the night →
     * morning rollover a numeric day label steps forward (with or without a
     * definition), and a defined week walks the weekday to the next name in
     * the list — wrapping past the last day increments the week counter. A
     * null or unknown current weekday (definition edited mid-campaign) leaves
     * the weekday AND week untouched; a null definition is byte-identical to
     * the 1-arg overload.
     */
    static Map<String, Object> advanceSegment(Map<String, Object> gameTime, List<String> weekDays) {
        Map<String, Object> time = normalize(gameTime);
        int idx = SEGMENTS.indexOf(String.valueOf(time.get("timeOfDay")));
        boolean newDay = idx == SEGMENTS.size() - 1; // night wraps to the next morning
        time.put("timeOfDay", SEGMENTS.get((idx + 1) % SEGMENTS.size()));
        if (newDay) {
            time.put("day", incrementDayLabel((String) time.get("day")));
            if (weekDays != null && time.get("weekday") instanceof String current) {
                String canonical = canonicalDay(weekDays, current);
                if (canonical != null) {
                    int next = (weekDays.indexOf(canonical) + 1) % weekDays.size();
                    time.put("weekday", weekDays.get(next));
                    if (next == 0) { // wrapped past the last defined day — a week completed
                        time.put("week", (int) time.get("week") + 1);
                    }
                }
            }
        }
        return time;
    }

    // A day-of-month label the clock can count: an integer with an optional
    // English ordinal suffix ("3", "3rd", "21st"). Anything else is left alone.
    private static final Pattern COUNTABLE_DAY = Pattern.compile("(\\d+)(st|nd|rd|th)?", Pattern.CASE_INSENSITIVE);

    /**
     * The next day's label, when the current one is numeric: "3" → "4" and
     * "3rd" → "4th" — ordinal in, correct ordinal out (11th/12th/13th
     * included); bare number in, bare number out. Labels the clock can't
     * count ("Midwinter"), and null/blank ones, are returned unchanged: months
     * are free text with unknown lengths, so the DM stays in charge of those.
     * There is deliberately no month/year rollover for the same reason.
     */
    static String incrementDayLabel(String day) {
        if (day == null || day.isBlank()) return day;
        Matcher m = COUNTABLE_DAY.matcher(day.trim());
        if (!m.matches()) return day;
        int next;
        try {
            next = Integer.parseInt(m.group(1)) + 1;
        } catch (NumberFormatException e) {
            return day; // numeric but absurdly long — leave it to the DM
        }
        return m.group(2) == null ? String.valueOf(next) : next + ordinalSuffix(next);
    }

    /** st/nd/rd/th for a day number, with the 11th–13th special cases. */
    private static String ordinalSuffix(int n) {
        int mod100 = n % 100;
        if (mod100 >= 11 && mod100 <= 13) return "th";
        return switch (n % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
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
