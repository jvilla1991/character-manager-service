package com.moo.charactermanagerservice.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.progression.ClassProgression;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A character's spell slots: {@code pc.spell_slots}, a JSON object keyed by
 * spell level — {@code {"1":{"max":4,"used":1},"2":{"max":3,"used":0}}}.
 *
 * <p>The MAXIMUM is the server's. It is the class table
 * ({@link ClassProgression#spellSlotsFor}) plus the Dungeon Master's bonus
 * lines for that spell level, which live with the other adjustment lines in
 * {@code pc.stat_adjustments} under the key {@code slot1} .. {@code slot9}
 * ({@link StatAdjustmentRules}) — {@code {"slot3":[{"value":1,"label":"Pearl
 * of Power"}]}} is one more 3rd-level slot. A {@code max} in a request body is
 * never read. The stored {@code max} is a copy of that sum, kept so every
 * reader of the column (the session snapshot, a cast) sees a finished number.
 *
 * <p>Only {@code used} is state. Every whole-character save goes through
 * {@link #normalize}, and a level-up through {@link #rebuild}: a spell level
 * the character has no slots for is dropped, and {@code used} is clamped to
 * 0..max, so a maximum that shrank pulls the used count down with it.
 */
final class SpellSlotState {

    private SpellSlotState() {}

    /** Highest spell level there are slots for. */
    static final int MAX_SPELL_LEVEL = 9;

    /** A level's bonus lines are stored under this prefix plus the level: {@code slot3}. */
    static final String BONUS_KEY_PREFIX = "slot";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** One stored spell level. {@code used} is null when the entry carried none. */
    record Slot(int max, Integer used) {}

    /** The stat-adjustment key holding the bonus lines for a spell level. */
    static String bonusKey(int spellLevel) {
        return BONUS_KEY_PREFIX + spellLevel;
    }

    /** The maximum per spell level for this character: class table plus bonus lines. */
    static Map<Integer, Integer> maxFor(PC pc) {
        return maxFor(pc.getClazz(), pc.getLevel() == null ? null : pc.getLevel().intValue(),
                pc.getStatAdjustments());
    }

    /**
     * The maximum per spell level, in spell-level order: the class table for
     * this level (a missing level counts as 1) plus the sum of the bonus lines
     * for each spell level. A spell level whose total is zero or less is left
     * out — so a class with no slot table still gets a slot from a bonus line,
     * and a negative line can take a table slot level away entirely.
     */
    static Map<Integer, Integer> maxFor(String clazz, Integer level, String statAdjustments) {
        Map<Integer, Integer> table = ClassProgression.spellSlotsFor(clazz, level == null ? 1 : level);
        Map<String, List<StatAdjustmentRules.Line>> lines = StatAdjustmentRules.parse(statAdjustments);
        Map<Integer, Integer> max = new LinkedHashMap<>();
        for (int spellLevel = 1; spellLevel <= MAX_SPELL_LEVEL; spellLevel++) {
            int total = table.getOrDefault(spellLevel, 0);
            for (StatAdjustmentRules.Line line : lines.getOrDefault(bonusKey(spellLevel), List.of())) {
                total += line.value();
            }
            if (total > 0) {
                max.put(spellLevel, total);
            }
        }
        return max;
    }

    /**
     * The value to store when a whole-character save arrives (create, owner
     * update, DM update). Call it once {@code statAdjustments} is settled on
     * {@code incoming}, because the bonus lines are part of the maximum.
     *
     * <p>{@code max} always comes from {@link #maxFor}. {@code used} comes from
     * the stored value, unless {@code bodySetsUsed}: then a spell level the
     * body carries a {@code used} for takes the body's (clamped). A spell level
     * the body leaves out keeps the stored count, so a body built without the
     * slots — or with an empty object — cannot hand every slot back.
     *
     * @param existing     the stored character, or null on create
     * @param bodySetsUsed whether this caller may move {@code used}: the DM, or
     *                     the owner of a character in no campaign. A campaign
     *                     player spends through the spend endpoint and gets
     *                     slots back from a rest or the DM.
     */
    static String normalize(PC incoming, PC existing, boolean bodySetsUsed) {
        String stored = existing == null ? null : existing.getSpellSlots();
        Map<Integer, Slot> used = read(stored);
        if (bodySetsUsed) {
            read(incoming.getSpellSlots()).forEach((spellLevel, slot) -> {
                if (slot.used() != null) {
                    used.put(spellLevel, slot);
                }
            });
        }
        return write(maxFor(incoming), used, existing == null ? incoming.getSpellSlots() : stored);
    }

    /**
     * The value to store after the character itself changed (a level-up): the
     * maximum for what the character is now, the stored {@code used} carried
     * over and clamped, a newly gained spell level starting unspent.
     */
    static String rebuild(PC pc) {
        return write(maxFor(pc), read(pc.getSpellSlots()), pc.getSpellSlots());
    }

    /** The stored maximum per spell level, sorted — what the sheet shows today. */
    static Map<Integer, Integer> storedMax(String json) {
        Map<Integer, Integer> max = new TreeMap<>();
        read(json).forEach((spellLevel, slot) -> max.put(spellLevel, slot.max()));
        return max;
    }

    /**
     * Read the column, defensively: null, blank or unreadable text is no slots,
     * so a bad column never fails a save or a level-up.
     */
    static Map<Integer, Slot> read(String json) {
        try {
            return parse(json);
        } catch (IllegalArgumentException e) {
            return new LinkedHashMap<>();
        }
    }

    /**
     * Read the column, strictly: null or blank is no slots, but text that is
     * not a JSON object throws. Inside the object, an entry whose key is not a
     * spell level 1-9 or whose value is not an object is skipped.
     *
     * @throws IllegalArgumentException when the text is not a JSON object
     */
    static Map<Integer, Slot> parse(String json) {
        Map<Integer, Slot> slots = new LinkedHashMap<>();
        if (json == null || json.isBlank()) return slots;
        Map<String, Object> raw;
        try {
            raw = MAPPER.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Spell slots are not a JSON object", e);
        }
        if (raw == null) return slots;
        raw.forEach((key, value) -> {
            Integer spellLevel = spellLevelOf(key);
            if (spellLevel != null && value instanceof Map<?, ?> entry) {
                int max = entry.get("max") instanceof Number n ? n.intValue() : 0;
                Integer used = entry.get("used") instanceof Number n ? n.intValue() : null;
                slots.put(spellLevel, new Slot(max, used));
            }
        });
        return slots;
    }

    private static Integer spellLevelOf(String key) {
        try {
            int spellLevel = Integer.parseInt(key.trim());
            return spellLevel >= 1 && spellLevel <= MAX_SPELL_LEVEL ? spellLevel : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Serialize {@code max} with each level's {@code used} clamped to 0..max.
     * A character with no slots at all keeps {@code whenEmpty} if that already
     * says "no slots" (null, blank or an empty object), so saving a Fighter
     * never rewrites its row; anything else stored there becomes {@code {}}.
     */
    static String write(Map<Integer, Integer> max, Map<Integer, Slot> used, String whenEmpty) {
        if (max.isEmpty()) {
            return saysNoSlots(whenEmpty) ? whenEmpty : "{}";
        }
        Map<String, Map<String, Integer>> out = new LinkedHashMap<>();
        max.forEach((spellLevel, slots) -> {
            Slot prior = used.get(spellLevel);
            int spent = prior == null || prior.used() == null ? 0 : prior.used();
            Map<String, Integer> entry = new LinkedHashMap<>();
            entry.put("max", slots);
            entry.put("used", Math.max(0, Math.min(spent, slots)));
            out.put(String.valueOf(spellLevel), entry);
        });
        try {
            return MAPPER.writeValueAsString(out);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to serialize spell slots");
        }
    }

    private static boolean saysNoSlots(String json) {
        if (json == null || json.isBlank()) return true;
        try {
            Map<String, Object> raw = MAPPER.readValue(json, new TypeReference<>() {});
            return raw == null || raw.isEmpty();
        } catch (JsonProcessingException e) {
            return false;
        }
    }
}
