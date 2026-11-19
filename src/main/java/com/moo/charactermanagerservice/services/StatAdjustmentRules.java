package com.moo.charactermanagerservice.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The labelled lines a Dungeon Master adds to a calculated number on the sheet,
 * e.g. {@code +1 | Taunka} under Armor Class (V57). Stored on the pc row as one
 * JSON object keyed by the number the lines adjust:
 *
 * <pre>{"ac":[{"value":1,"label":"Taunka"}]}</pre>
 *
 * <p>The number's own column (e.g. {@code ac}) keeps holding the finished total,
 * lines included: the SPA works that total out, because the armor math lives
 * there. This class only guards the shape of the lines, so that what is stored
 * is always something every client can read back.
 *
 * <p>The key is deliberately not a fixed list. Armor Class is the only number
 * with a breakdown today; when the SPA gives another one (an ability score,
 * speed) the same treatment it just sends a new key, with no migration and no
 * change here.
 *
 * <p>One family of keys the server does read: {@code slot1} .. {@code slot9}
 * are bonus spell slots of that spell level. {@link SpellSlotState} adds them
 * to the class table to get the maximum stored in {@code pc.spell_slots}, so a
 * line there changes a number the server owns, not one the SPA works out.
 *
 * <p>A number can also have a companion key, its own name plus
 * {@value #REMOVED_SUFFIX} ({@code acRemoved}). It lists the lines the SPA
 * works out by itself that the DM took out of the total, one entry per line,
 * named by that line's label:
 *
 * <pre>{"acRemoved":[{"value":1,"label":"Dexterity"}]}</pre>
 *
 * <p>An entry there has the shape of a line so it passes the same checks, but
 * only its label means anything; the value is a placeholder. A companion key
 * is never one of the numbers themselves: {@link SpellSlotState} reads the
 * exact keys {@code slot1} .. {@code slot9} and nothing that merely starts
 * like one, so {@code slot3Removed} adds no slots.
 */
public final class StatAdjustmentRules {

    private StatAdjustmentRules() {}

    /** Longest label a line may carry; the SPA's field has the same limit. */
    public static final int LABEL_MAX = 40;
    /** Largest amount a line may add or take away. */
    public static final int VALUE_MAX = 99;
    /** Most lines one number may carry. */
    public static final int LINES_MAX = 12;
    /** Most numbers one character may carry lines for. */
    public static final int STATS_MAX = 16;

    /** What a number's companion key ends in: {@code acRemoved} holds the removed lines of {@code ac}. */
    public static final String REMOVED_SUFFIX = "Removed";

    /** Whether a key is the companion key of some number, listing its removed calculated lines. */
    public static boolean isRemovedKey(String key) {
        return key != null && key.length() > REMOVED_SUFFIX.length() && key.endsWith(REMOVED_SUFFIX);
    }

    /** The companion key of a number: {@code ac} gives {@code acRemoved}. */
    public static String removedKey(String stat) {
        return stat + REMOVED_SUFFIX;
    }

    /** The number a companion key belongs to: {@code acRemoved} gives {@code ac}. */
    public static String statOfRemovedKey(String key) {
        return key.substring(0, key.length() - REMOVED_SUFFIX.length());
    }

    /** A stat key: a short camelCase name such as {@code ac}, {@code hpMax} or {@code STR}. */
    private static final Pattern STAT_KEY = Pattern.compile("[A-Za-z][A-Za-z0-9]{0,23}");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** One adjustment line, as parsed. */
    public record Line(int value, String label) {
        /** "+1 Taunka" — how a line reads in the activity log. */
        public String describe() {
            return (value < 0 ? "-" + Math.abs(value) : "+" + value) + " " + label;
        }
    }

    /**
     * Check a {@code statAdjustments} value from a request body and return it in
     * its stored form: labels trimmed, numbers with no lines left out, keys in
     * the order they were sent. Null or blank means "the body carried none" and
     * comes back as null (the caller decides whether that keeps the stored value).
     *
     * @throws ResponseStatusException 400 when the value is not an object of
     *         line lists, or a line breaks one of the limits above
     */
    public static String normalizeJson(String json) {
        if (json == null || json.isBlank()) return null;
        Map<String, Object> raw;
        try {
            raw = MAPPER.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw bad("Stat adjustments must be a JSON object");
        }
        if (raw == null) return null;
        if (raw.size() > STATS_MAX) throw bad("Too many adjusted numbers (at most " + STATS_MAX + ")");

        Map<String, List<Map<String, Object>>> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            String stat = entry.getKey();
            if (!STAT_KEY.matcher(stat).matches()) throw bad("Unknown adjusted number: " + stat);
            if (!(entry.getValue() instanceof List<?> lines)) {
                throw bad("Adjustments for " + stat + " must be a list of lines");
            }
            if (lines.size() > LINES_MAX) {
                throw bad("Too many adjustment lines for " + stat + " (at most " + LINES_MAX + ")");
            }
            List<Map<String, Object>> cleaned = new ArrayList<>();
            for (Object line : lines) {
                Line parsed = parseLine(stat, line);
                Map<String, Object> stored = new LinkedHashMap<>();
                stored.put("value", parsed.value());
                stored.put("label", parsed.label());
                cleaned.add(stored);
            }
            if (!cleaned.isEmpty()) out.put(stat, cleaned);
        }
        try {
            return MAPPER.writeValueAsString(out);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to serialize stat adjustments");
        }
    }

    private static Line parseLine(String stat, Object line) {
        if (!(line instanceof Map<?, ?> fields)) throw bad("Each adjustment line for " + stat + " must be an object");
        Object value = fields.get("value");
        boolean whole = value instanceof Integer || value instanceof Long || value instanceof Short;
        if (!whole) throw bad("An adjustment line for " + stat + " needs a whole-number value");
        long amount = ((Number) value).longValue();
        if (amount == 0 || Math.abs(amount) > VALUE_MAX) {
            throw bad("An adjustment value for " + stat + " must be between -" + VALUE_MAX + " and " + VALUE_MAX
                    + ", and not 0");
        }
        String label = fields.get("label") instanceof String text ? text.trim() : "";
        if (label.isEmpty()) throw bad("An adjustment line for " + stat + " needs a label");
        if (label.length() > LABEL_MAX) {
            throw bad("An adjustment label for " + stat + " can be at most " + LABEL_MAX + " characters");
        }
        return new Line((int) amount, label);
    }

    /**
     * Read a stored value back into lines per number, for the activity-log diff.
     * Defensive, unlike {@link #normalizeJson}: null, blank or unreadable text,
     * and any line that is not well-formed, simply yield nothing — a bad column
     * must never fail the save that is being logged.
     */
    public static Map<String, List<Line>> parse(String json) {
        Map<String, List<Line>> out = new LinkedHashMap<>();
        if (json == null || json.isBlank()) return out;
        Map<String, Object> raw;
        try {
            raw = MAPPER.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return out;
        }
        if (raw == null) return out;
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            if (!(entry.getValue() instanceof List<?> lines)) continue;
            List<Line> parsed = new ArrayList<>();
            for (Object line : lines) {
                if (line instanceof Map<?, ?> fields
                        && fields.get("value") instanceof Number value
                        && fields.get("label") instanceof String label) {
                    parsed.add(new Line(value.intValue(), label));
                }
            }
            if (!parsed.isEmpty()) out.put(entry.getKey(), parsed);
        }
        return out;
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
