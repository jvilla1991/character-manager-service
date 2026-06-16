package com.moo.charactermanagerservice.progression;

import java.util.Map;

/**
 * Static D&D 5e (2024) single-class progression rules.
 *
 * <p>This is the server-authoritative source of truth for level-dependent math. The SPA
 * renders the results of these functions; it never recomputes them. Tables live here — in
 * their own package — rather than inside {@code PCService}, which keeps persistence/ownership
 * (PCService) separate from the rules engine (this + {@link com.moo.charactermanagerservice.services.LevelUpService}).
 *
 * <p>Pure functions and immutable tables only: no Spring, no I/O, no entity dependency, so it
 * is trivially unit-testable. As later phases land, spell-slot / subclass-level / ASI / feature
 * tables join this class (or sibling classes in this package).
 */
public final class ClassProgression {

    private ClassProgression() {}

    /** Highest single-class level supported by 5e. */
    public static final int MAX_LEVEL = 20;

    /** Hit die used when a class is unknown/unmapped. */
    public static final int DEFAULT_HIT_DIE = 8;

    /**
     * Hit die size per class (2024 PHB). Keyed lower-case; lookups normalise case so the
     * persisted {@code clazz} ("Wizard") and any future input shape both resolve.
     */
    private static final Map<String, Integer> HIT_DICE = Map.ofEntries(
            Map.entry("barbarian", 12),
            Map.entry("fighter", 10),
            Map.entry("paladin", 10),
            Map.entry("ranger", 10),
            Map.entry("bard", 8),
            Map.entry("cleric", 8),
            Map.entry("druid", 8),
            Map.entry("monk", 8),
            Map.entry("rogue", 8),
            Map.entry("warlock", 8),
            Map.entry("sorcerer", 6),
            Map.entry("wizard", 6)
    );

    /** Hit die for a class; case-insensitive, defaults to {@value #DEFAULT_HIT_DIE} for unknowns. */
    public static int hitDie(String clazz) {
        if (clazz == null) {
            return DEFAULT_HIT_DIE;
        }
        return HIT_DICE.getOrDefault(clazz.trim().toLowerCase(), DEFAULT_HIT_DIE);
    }

    /**
     * Fixed-average value taken from one hit die on level-up: {@code die/2 + 1}
     * (d6=4, d8=5, d10=6, d12=7). Rolled HP is a future seam handled at the call site.
     */
    public static int averageHitDieValue(int hitDie) {
        return hitDie / 2 + 1;
    }

    /**
     * Proficiency bonus for a total character level: {@code (level-1)/4 + 2}.
     * Bumps at levels 5, 9, 13, 17. Keyed on total level so it is already multiclass-correct.
     */
    public static int proficiencyBonusForLevel(int level) {
        return (level - 1) / 4 + 2;
    }

    /** Ability modifier, floored so low/odd scores resolve correctly (e.g. 3 -> -4, 9 -> -1). */
    public static int abilityModifier(int score) {
        return Math.floorDiv(score - 10, 2);
    }
}
