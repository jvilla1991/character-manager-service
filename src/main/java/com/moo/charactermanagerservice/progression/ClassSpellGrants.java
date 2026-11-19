package com.moo.charactermanagerservice.progression;

import com.moo.charactermanagerservice.dto.FeatureGain;
import com.moo.charactermanagerservice.dto.SpellGrant;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Spells a class feature always keeps prepared (D&D 5e 2024), keyed by class and then by the
 * feature that grants them. A grant is delivered at the level-up that delivers its feature — it
 * asks {@link ClassFeatures#featuresAt(String, String, String, int)} — so a subclass that replaces
 * the feature also drops the spell: a Death Knight's Runic Smite replaces Divine Smite, and with
 * it the Divine Smite spell (its own Rune Strike is on the hero-class table in the app).
 *
 * <p>Names only: the spell text lives in the app, which sends the full spell with the level-up.
 * Level-1 grants (a Ranger's Hunter's Mark, a Druid's Speak with Animals) are not here — level-1
 * features are handed out by the create wizard, like every other level-1 feature. Subclass spell
 * lists are not here either; the World of Warcraft ones are shown from the app's own tables.
 */
public final class ClassSpellGrants {

    private ClassSpellGrants() {}

    /** class (lower-case) -> granting feature name -> the spells it always keeps prepared. */
    private static final Map<String, Map<String, List<String>>> GRANTS = Map.of(
            // Paladin's Smite (level 2) and Faithful Steed (level 5).
            "paladin", Map.of(
                    "Divine Smite", List.of("Divine Smite"),
                    "Faithful Steed", List.of("Find Steed")),
            // Contact Patron (level 9).
            "warlock", Map.of(
                    "Contact Patron", List.of("Contact Other Plane")),
            // Words of Creation (level 20).
            "bard", Map.of(
                    "Words of Creation", List.of("Power Word Heal", "Power Word Kill"))
    );

    /**
     * The always-prepared spells a character gains on reaching {@code level}, in feature order;
     * empty for most classes and levels.
     */
    public static List<SpellGrant> grantsAt(String clazz, String subclass, String specialization, int level) {
        if (clazz == null) return List.of();
        Map<String, List<String>> byFeature = GRANTS.get(clazz.trim().toLowerCase());
        if (byFeature == null) return List.of();
        List<SpellGrant> grants = new ArrayList<>();
        for (FeatureGain feature : ClassFeatures.featuresAt(clazz, subclass, specialization, level)) {
            for (String spell : byFeature.getOrDefault(feature.name(), List.of())) {
                grants.add(new SpellGrant(spell, feature.name()));
            }
        }
        return List.copyOf(grants);
    }

    /** Every spell name this table can grant, lower-cased (for tests and diagnostics). */
    static Set<String> allSpellNames() {
        return GRANTS.values().stream()
                .flatMap(byFeature -> byFeature.values().stream())
                .flatMap(List::stream)
                .map(String::toLowerCase)
                .collect(Collectors.toUnmodifiableSet());
    }
}
