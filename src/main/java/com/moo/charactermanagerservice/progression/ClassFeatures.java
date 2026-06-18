package com.moo.charactermanagerservice.progression;

import com.moo.charactermanagerservice.dto.FeatureGain;

import java.util.List;
import java.util.Map;

/**
 * Class features granted automatically on level-up (D&D 5e 2024), keyed by class then by the
 * level at which they are gained. Server-authoritative and server-owned (names and descriptions
 * both live here, unlike feats whose descriptions are presentation).
 *
 * <p>This is a <strong>curated, extensible seed</strong> — like {@link FeatCatalog} it is not
 * exhaustive. Barbarian and Fighter are filled in for the lower levels as worked examples; other
 * classes and higher levels return no features until their entries are added here. Level 1
 * features are intentionally absent: those are granted at character creation, not on level-up.
 * Subclass features are out of scope (the subclass catalog is the seam for those).
 */
public final class ClassFeatures {

    private ClassFeatures() {}

    private static final Map<String, Map<Integer, List<FeatureGain>>> FEATURES = Map.of(
            "barbarian", Map.of(
                    2, List.of(
                            new FeatureGain("Reckless Attack",
                                    "On your first attack of your turn you can attack with advantage; attack rolls against you have advantage until your next turn."),
                            new FeatureGain("Danger Sense",
                                    "You have advantage on Dexterity saving throws against effects you can see, such as traps and spells.")),
                    3, List.of(
                            new FeatureGain("Primal Knowledge",
                                    "You gain an additional skill proficiency, and can channel your rage into certain ability checks.")),
                    5, List.of(
                            new FeatureGain("Extra Attack",
                                    "You can attack twice, instead of once, whenever you take the Attack action on your turn."),
                            new FeatureGain("Fast Movement",
                                    "Your speed increases by 10 feet while you aren't wearing heavy armor."))
            ),
            "fighter", Map.of(
                    2, List.of(
                            new FeatureGain("Action Surge",
                                    "Once per short or long rest, you can take one additional action on your turn.")),
                    3, List.of(
                            new FeatureGain("Tactical Mind",
                                    "When you fail an ability check you can expend a use of Second Wind to add 1d10 to it.")),
                    5, List.of(
                            new FeatureGain("Extra Attack",
                                    "You can attack twice, instead of once, whenever you take the Attack action on your turn."),
                            new FeatureGain("Tactical Shift",
                                    "When you activate Second Wind, you can move up to half your speed without provoking opportunity attacks."))
            )
    );

    /** Features gained by a class at a specific level; empty when none are seeded. */
    public static List<FeatureGain> featuresAt(String clazz, int level) {
        if (clazz == null) return List.of();
        Map<Integer, List<FeatureGain>> byLevel = FEATURES.get(clazz.trim().toLowerCase());
        if (byLevel == null) return List.of();
        return byLevel.getOrDefault(level, List.of());
    }
}
