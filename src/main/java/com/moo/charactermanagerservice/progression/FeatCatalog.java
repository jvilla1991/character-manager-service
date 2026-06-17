package com.moo.charactermanagerservice.progression;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Selectable General feats (D&D 5e 2024) — the feat option at an Ability Score Improvement level.
 *
 * <p>This is the server-authoritative source of truth for <em>which</em> feats may be chosen on
 * level-up; it holds names only. Descriptions are presentation and live in the frontend. The list
 * is a curated subset of the 2024 General feats, not exhaustive — add names here to expand it.
 *
 * <p>Origin feats (Alert, Tough, Lucky, Magic Initiate, …) are deliberately excluded: those are
 * granted at level 1 by a background, not chosen at an ASI level.
 */
public final class FeatCatalog {

    private FeatCatalog() {}

    private static final Set<String> GENERAL_FEATS = Set.of(
            "Great Weapon Master",
            "Sharpshooter",
            "Sentinel",
            "War Caster",
            "Resilient",
            "Speedy",
            "Mage Slayer",
            "Polearm Master",
            "Inspiring Leader",
            "Skill Expert"
    );

    /** Selectable feat names, sorted for a stable preview/UI ordering. */
    public static List<String> generalFeats() {
        return GENERAL_FEATS.stream().sorted().collect(Collectors.toList());
    }

    /** Whether the given name is a selectable General feat (case-insensitive). */
    public static boolean isValidFeat(String name) {
        if (name == null) return false;
        String trimmed = name.trim();
        return GENERAL_FEATS.stream().anyMatch(f -> f.equalsIgnoreCase(trimmed));
    }
}
