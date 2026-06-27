package com.moo.charactermanagerservice.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Spell-slot {@code spellSlots} JSON mechanics for {@link LevelUpService}: rebuild the TEXT column
 * for a new level and read the current max-per-level for the preview. Parsing is defensive — null,
 * blank, or malformed JSON yields an empty map rather than failing the level-up.
 */
class SpellSlotCalculator {

    private final ObjectMapper objectMapper;

    SpellSlotCalculator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Rebuild the {@code spellSlots} JSON for the new level: {@code max} comes from {@code target}
     * (the class table), {@code used} is carried over from {@code currentJson} (clamped to the new
     * max), and spell levels newly unlocked start at {@code used = 0}. Ordered by spell level.
     */
    String rebuild(String currentJson, Map<Integer, Integer> target) {
        Map<Integer, SlotState> existing = parseSlots(currentJson);

        Map<String, Map<String, Integer>> rebuilt = new LinkedHashMap<>();
        target.forEach((spellLevel, max) -> {
            SlotState prior = existing.get(spellLevel);
            int used = prior == null ? 0 : Math.min(prior.used(), max);
            Map<String, Integer> entry = new LinkedHashMap<>();
            entry.put("max", max);
            entry.put("used", used);
            rebuilt.put(String.valueOf(spellLevel), entry);
        });

        try {
            return objectMapper.writeValueAsString(rebuilt);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to serialize spell slots");
        }
    }

    /** Current {@code spellLevel -> max} for the preview (sorted; empty if none/malformed). */
    Map<Integer, Integer> currentMax(String json) {
        Map<Integer, Integer> out = new TreeMap<>();
        parseSlots(json).forEach((lvl, state) -> out.put(lvl, state.max()));
        return out;
    }

    /**
     * Parse the {@code spellSlots} TEXT column into {@code spellLevel -> {max, used}}. Defensive:
     * null, blank, or malformed JSON yields an empty map rather than failing the level-up.
     */
    private Map<Integer, SlotState> parseSlots(String json) {
        Map<Integer, SlotState> out = new LinkedHashMap<>();
        if (json == null || json.isBlank()) return out;
        try {
            Map<String, Map<String, Integer>> raw =
                    objectMapper.readValue(json, new TypeReference<>() {});
            raw.forEach((lvl, state) -> {
                try {
                    int max = state.getOrDefault("max", 0);
                    int used = state.getOrDefault("used", 0);
                    out.put(Integer.parseInt(lvl), new SlotState(max, used));
                } catch (NumberFormatException ignored) {
                    // skip non-numeric spell-level keys
                }
            });
        } catch (JsonProcessingException e) {
            return new LinkedHashMap<>(); // malformed column — treat as no slots
        }
        return out;
    }

    private record SlotState(int max, int used) {}
}
