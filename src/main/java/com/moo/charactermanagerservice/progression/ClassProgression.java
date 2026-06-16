package com.moo.charactermanagerservice.progression;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

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

    // ── Spell-slot progression (Phase 2) ────────────────────────────────────────
    // Caster set intentionally matches what the app already models as spellcasters
    // (frontend SPELLCASTING_CLASSES + the create wizard's starting slots): five full
    // casters plus warlock pact magic. Paladin/Ranger are NOT included — the app does
    // not represent them as casters, and consistency with the existing data model wins
    // over 2024-PHB half-caster rules. Add them here (and in the app) when that changes.

    private static final Set<String> FULL_CASTERS =
            Set.of("bard", "cleric", "druid", "sorcerer", "wizard");

    /** Warlock — pact magic: a single slot level with a small number of short-rest slots. */
    public static final String PACT_CASTER = "warlock";

    /**
     * Full-caster slots by character level (rows, 1-indexed via {@code level-1}) and spell
     * level (columns, spell levels 1-9). Standard 5e full-caster table; zeros = no slots.
     */
    private static final int[][] FULL_CASTER_SLOTS = {
            //  1  2  3  4  5  6  7  8  9   <- spell level
            {  2, 0, 0, 0, 0, 0, 0, 0, 0 }, // L1
            {  3, 0, 0, 0, 0, 0, 0, 0, 0 }, // L2
            {  4, 2, 0, 0, 0, 0, 0, 0, 0 }, // L3
            {  4, 3, 0, 0, 0, 0, 0, 0, 0 }, // L4
            {  4, 3, 2, 0, 0, 0, 0, 0, 0 }, // L5
            {  4, 3, 3, 0, 0, 0, 0, 0, 0 }, // L6
            {  4, 3, 3, 1, 0, 0, 0, 0, 0 }, // L7
            {  4, 3, 3, 2, 0, 0, 0, 0, 0 }, // L8
            {  4, 3, 3, 3, 1, 0, 0, 0, 0 }, // L9
            {  4, 3, 3, 3, 2, 0, 0, 0, 0 }, // L10
            {  4, 3, 3, 3, 2, 1, 0, 0, 0 }, // L11
            {  4, 3, 3, 3, 2, 1, 0, 0, 0 }, // L12
            {  4, 3, 3, 3, 2, 1, 1, 0, 0 }, // L13
            {  4, 3, 3, 3, 2, 1, 1, 0, 0 }, // L14
            {  4, 3, 3, 3, 2, 1, 1, 1, 0 }, // L15
            {  4, 3, 3, 3, 2, 1, 1, 1, 0 }, // L16
            {  4, 3, 3, 3, 2, 1, 1, 1, 1 }, // L17
            {  4, 3, 3, 3, 3, 1, 1, 1, 1 }, // L18
            {  4, 3, 3, 3, 3, 2, 1, 1, 1 }, // L19
            {  4, 3, 3, 3, 3, 2, 2, 1, 1 }, // L20
    };

    /** Warlock pact slots by character level: {@code {pactSlotLevel, slotCount}}. */
    private static final int[][] PACT_SLOTS = {
            { 1, 1 }, // L1
            { 1, 2 }, // L2
            { 2, 2 }, // L3
            { 2, 2 }, // L4
            { 3, 2 }, // L5
            { 3, 2 }, // L6
            { 4, 2 }, // L7
            { 4, 2 }, // L8
            { 5, 2 }, // L9
            { 5, 2 }, // L10
            { 5, 3 }, // L11
            { 5, 3 }, // L12
            { 5, 3 }, // L13
            { 5, 3 }, // L14
            { 5, 3 }, // L15
            { 5, 3 }, // L16
            { 5, 4 }, // L17
            { 5, 4 }, // L18
            { 5, 4 }, // L19
            { 5, 4 }, // L20
    };

    /** True when the class is one the app treats as a spellcaster (full or pact). */
    public static boolean isCaster(String clazz) {
        if (clazz == null) return false;
        String key = clazz.trim().toLowerCase();
        return FULL_CASTERS.contains(key) || PACT_CASTER.equals(key);
    }

    /**
     * Maximum spell slots available at a class/level, as {@code spellLevel -> maxSlots}
     * (insertion-ordered by spell level). Empty for non-casters. Levels are clamped to
     * {@value #MAX_LEVEL}.
     */
    public static Map<Integer, Integer> spellSlotsFor(String clazz, int level) {
        Map<Integer, Integer> slots = new LinkedHashMap<>();
        if (clazz == null || level < 1) return slots;
        String key = clazz.trim().toLowerCase();
        int idx = Math.min(level, MAX_LEVEL) - 1;

        if (FULL_CASTERS.contains(key)) {
            int[] row = FULL_CASTER_SLOTS[idx];
            for (int i = 0; i < row.length; i++) {
                if (row[i] > 0) slots.put(i + 1, row[i]);
            }
        } else if (PACT_CASTER.equals(key)) {
            int[] pact = PACT_SLOTS[idx];
            slots.put(pact[0], pact[1]);
        }
        return slots;
    }
}
