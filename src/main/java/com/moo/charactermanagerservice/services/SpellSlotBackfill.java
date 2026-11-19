package com.moo.charactermanagerservice.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moo.charactermanagerservice.progression.ClassProgression;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The per-row decision of the V58 backfill, which moves every character onto
 * server-owned spell-slot maximums ({@link SpellSlotState}). Before V58 the
 * {@code max} in {@code pc.spell_slots} was whatever the sheet last sent, so a
 * Dungeon Master could type extra slots in by hand; from V58 it is the class
 * table plus the DM's bonus lines.
 *
 * <p>Per character:
 * <ul>
 *   <li>a spell level stored with MORE slots than the character now works out
 *       to keeps the difference, as a bonus line labelled
 *       {@value #CARRIED_OVER} — hand-typed slots are not lost, and the DM can
 *       see and remove them like any other line;</li>
 *   <li>a spell level stored with fewer (or not stored at all — every Paladin
 *       and Ranger) simply gets the table's number;</li>
 *   <li>{@code used} is kept, clamped to the new maximum.</li>
 * </ul>
 *
 * <p>Pure: text in, text out, no database and no Spring, so the migration
 * class is only a loop around {@link #plan}. Applying it to its own output
 * changes nothing.
 */
public final class SpellSlotBackfill {

    private SpellSlotBackfill() {}

    /** Label of the bonus line that keeps slots a DM had typed in by hand. */
    public static final String CARRIED_OVER = "Carried over";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** The new values of one row's two columns (either may equal the old one). */
    public record Change(String spellSlots, String statAdjustments) {}

    /**
     * What one {@code pc} row should become, or empty when it should be left
     * alone: nothing to change, or one of the two columns holds text that is
     * not a JSON object (such a row is skipped rather than guessed at).
     */
    public static Optional<Change> plan(String clazz, Integer level, String spellSlots, String statAdjustments) {
        Map<Integer, SpellSlotState.Slot> stored;
        Map<String, Object> adjustments;
        try {
            stored = SpellSlotState.parse(spellSlots);
            adjustments = parseObject(statAdjustments);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        // What the character works out to before anything is carried over: the class
        // table plus any bonus lines already there (none on a first run).
        Map<Integer, Integer> max = SpellSlotState.maxFor(clazz, level, statAdjustments);
        boolean carried = false;
        for (Map.Entry<Integer, SpellSlotState.Slot> entry : stored.entrySet()) {
            int extra = entry.getValue().max() - max.getOrDefault(entry.getKey(), 0);
            if (extra > 0) {
                carried |= addLine(adjustments, SpellSlotState.bonusKey(entry.getKey()),
                        Math.min(extra, StatAdjustmentRules.VALUE_MAX));
            }
        }

        String newAdjustments = carried ? write(adjustments) : statAdjustments;
        String newSlots = SpellSlotState.write(
                SpellSlotState.maxFor(clazz, level, newAdjustments), stored, spellSlots);
        // Compared as data, not text: a column that only differs in spacing or key order
        // is already right and is not rewritten.
        boolean slotsChanged = !parseObject(newSlots).equals(parseObject(spellSlots));
        if (!carried && !slotsChanged) {
            return Optional.empty();
        }
        return Optional.of(new Change(slotsChanged ? newSlots : spellSlots, newAdjustments));
    }

    /**
     * Append one {@value #CARRIED_OVER} line under {@code stat}, within the
     * limits {@link StatAdjustmentRules} enforces on a save. A line that would
     * break one of them is skipped, not forced in: the character then just has
     * the table's number for that spell level.
     */
    @SuppressWarnings("unchecked")
    private static boolean addLine(Map<String, Object> adjustments, String stat, int value) {
        Object existing = adjustments.get(stat);
        if (existing == null) {
            if (adjustments.size() >= StatAdjustmentRules.STATS_MAX) return false;
            existing = new ArrayList<>();
            adjustments.put(stat, existing);
        }
        if (!(existing instanceof List<?> lines) || lines.size() >= StatAdjustmentRules.LINES_MAX) {
            return false;
        }
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("value", value);
        line.put("label", CARRIED_OVER);
        ((List<Object>) lines).add(line);
        return true;
    }

    /** Null or blank is "no lines"; text that is not a JSON object throws. */
    private static Map<String, Object> parseObject(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        try {
            Map<String, Object> raw = MAPPER.readValue(json, new TypeReference<>() {});
            return raw == null ? new LinkedHashMap<>() : new LinkedHashMap<>(raw);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Stat adjustments are not a JSON object", e);
        }
    }

    private static String write(Map<String, Object> adjustments) {
        try {
            return MAPPER.writeValueAsString(adjustments);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize stat adjustments", e);
        }
    }
}
