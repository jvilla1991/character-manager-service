package com.moo.charactermanagerservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moo.charactermanagerservice.models.PC;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The feats a character has, read from where they are stored. There is no feat table: a feat is
 * a name in one of two places.
 * <ul>
 *   <li>{@code pc.feat} — the Origin feat of the background, set by the create wizard.</li>
 *   <li>{@code pc.features} — one entry per feat, each {@code {name, source, desc}}: the
 *       background's Origin feat again ({@code "Sage · Origin Feat"}), a species' Origin feat
 *       ({@code "Human (World of Warcraft) · Origin Feat"}), a feat taken at a level-up
 *       ({@code "Feat (Level 4)"}), a Fighting Style ({@code "Fighting Style (Level 2)"}) and a
 *       feat the DM granted ({@code "Feat"}).</li>
 * </ul>
 *
 * <p>The source wording differs by who wrote the entry, so this does not read it: it returns the
 * Origin feat and the name of every features entry, and the rules tables pick out the names they
 * know (feat names are not shared with class features). Each name is returned once, in the
 * order stored.
 */
final class CharacterFeats {

    private CharacterFeats() {}

    private static final PcJsonColumns JSON = new PcJsonColumns(new ObjectMapper());

    /** The Origin feat, then the name of every entry of the features list; never null. */
    static List<String> namesOn(PC pc) {
        Map<String, String> names = new LinkedHashMap<>();
        if (pc == null) return List.of();
        add(names, pc.getFeat());
        for (Map<String, Object> feature : JSON.parse(pc.getFeatures())) {
            Object name = feature.get("name");
            add(names, name == null ? null : name.toString());
        }
        return new ArrayList<>(names.values());
    }

    private static void add(Map<String, String> names, String name) {
        if (name == null || name.isBlank()) return;
        names.putIfAbsent(name.trim().toLowerCase(), name.trim());
    }
}
