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

/**
 * Read/write helpers for the PC's JSON-array-of-objects TEXT columns ({@code features} and
 * {@code spells}) used by {@link LevelUpService}. Reads are defensive (null/blank/malformed yields
 * an empty list so a level-up never fails on a bad column); writes fail loud.
 */
class PcJsonColumns {

    private final ObjectMapper objectMapper;

    PcJsonColumns(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Parse a JSON-array-of-objects TEXT column into a mutable list; defensive about null/blank/malformed. */
    List<Map<String, Object>> parse(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            List<Map<String, Object>> parsed = objectMapper.readValue(json, new TypeReference<>() {});
            return parsed == null ? new ArrayList<>() : new ArrayList<>(parsed);
        } catch (JsonProcessingException e) {
            return new ArrayList<>();
        }
    }

    /** Serialize a list of objects back to the TEXT column; failing loud on a serialization error. */
    String write(List<Map<String, Object>> list) {
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to serialize features");
        }
    }

    /** Build a {@code name/source/desc} feature entry for the features column. */
    Map<String, Object> featureEntry(String name, String source, String desc) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("name", name);
        entry.put("source", source);
        entry.put("desc", desc);
        return entry;
    }
}
