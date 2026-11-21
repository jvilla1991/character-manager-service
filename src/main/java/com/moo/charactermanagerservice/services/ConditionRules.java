package com.moo.charactermanagerservice.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The conditions a character is immune to (V63, {@code pc.condition_immunities}), kept beside
 * the conditions it has ({@code pc.conditions}). Both columns hold a JSON array of condition
 * names, e.g. {@code ["poisoned","charmed"]}. The sheet shows a condition the character has in
 * red and one it is immune to in green.
 *
 * <p>Who may change what is decided by {@link PCService}: the Dungeon Master changes both
 * lists; in a campaign the owner changes only the immunities, because the conditions are the
 * DM's; a character in no campaign has no DM, so its owner changes both.
 *
 * <p>A condition is never in both lists. An immunity whose condition is active is dropped: the
 * condition the DM put on the character is the one that stands.
 */
public final class ConditionRules {

    private ConditionRules() {}

    /** Most names one list may carry. The sheet offers seventeen. */
    public static final int NAMES_MAX = 40;
    /** Longest name one entry may be. */
    public static final int NAME_MAX = 40;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Clean the immunities a save carries against the conditions it ends up with: names
     * trimmed, blanks and repeats dropped, and any name that is also an active condition
     * dropped. {@code null} stays {@code null} (the caller has already decided whether a null
     * keeps the stored list). A body that is not a JSON array of names is refused with a 400.
     */
    public static String normalizeImmunitiesJson(String immunitiesJson, String conditionsJson) {
        if (immunitiesJson == null) {
            return null;
        }
        Set<String> active = new HashSet<>(readNames(conditionsJson));
        Set<String> kept = new LinkedHashSet<>();
        for (String name : parseStrict(immunitiesJson)) {
            if (!active.contains(name)) {
                kept.add(name);
            }
        }
        try {
            return MAPPER.writeValueAsString(kept);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to serialize condition immunities");
        }
    }

    /** Whether two stored lists name the same conditions, order ignored; null reads as none. */
    public static boolean sameNames(String a, String b) {
        return new HashSet<>(readNames(a)).equals(new HashSet<>(readNames(b)));
    }

    /**
     * Read a stored list without failing: null, blank or malformed text reads as no names,
     * and entries that are not text are skipped. For values already in the database.
     */
    static List<String> readNames(String json) {
        List<String> names = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return names;
        }
        try {
            JsonNode node = MAPPER.readTree(json);
            if (node != null && node.isArray()) {
                for (JsonNode entry : node) {
                    if (entry.isTextual() && !entry.asText().isBlank()) {
                        names.add(entry.asText().trim());
                    }
                }
            }
        } catch (JsonProcessingException e) {
            // A malformed stored value reads as empty, like the other JSON columns.
        }
        return names;
    }

    /** Read a list a client sent, refusing anything that is not an array of short names. */
    private static List<String> parseStrict(String json) {
        if (json.isBlank()) {
            return List.of();
        }
        JsonNode node;
        try {
            node = MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw badRequest("conditionImmunities must be a JSON array of condition names");
        }
        if (node == null || !node.isArray()) {
            throw badRequest("conditionImmunities must be a JSON array of condition names");
        }
        if (node.size() > NAMES_MAX) {
            throw badRequest("conditionImmunities may name at most " + NAMES_MAX + " conditions");
        }
        List<String> names = new ArrayList<>();
        for (JsonNode entry : node) {
            if (!entry.isTextual()) {
                throw badRequest("conditionImmunities must be a JSON array of condition names");
            }
            String name = entry.asText().trim();
            if (name.length() > NAME_MAX) {
                throw badRequest("A condition name may be at most " + NAME_MAX + " characters");
            }
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        return names;
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
