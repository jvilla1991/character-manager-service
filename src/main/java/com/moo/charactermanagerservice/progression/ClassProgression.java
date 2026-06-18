package com.moo.charactermanagerservice.progression;

import java.util.LinkedHashMap;
import java.util.List;
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

    /** Cantrips known at level 1 per caster class (2024 PHB); absent classes know none. */
    private static final Map<String, Integer> BASE_CANTRIPS = Map.of(
            "bard", 2, "cleric", 3, "druid", 2, "sorcerer", 4, "warlock", 2, "wizard", 3
    );

    /**
     * Cantrips known at the given level: the class base plus the standard increases at levels
     * 4 and 10. Returns 0 for non-casters / classes that don't learn cantrips.
     */
    public static int cantripsKnownFor(String clazz, int level) {
        if (clazz == null || level < 1) return 0;
        Integer base = BASE_CANTRIPS.get(clazz.trim().toLowerCase());
        if (base == null) return 0;
        return base + (level >= 4 ? 1 : 0) + (level >= 10 ? 1 : 0);
    }

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

    // ── Subclass timing & catalog (Phase 3 — mechanism only) ────────────────────
    // The level at which each class chooses its subclass. Sorcerer/Warlock are level 1
    // to match the app's existing creation flow; every other class is level 3 (2024 PHB).
    // Unmapped classes default to 3.
    //
    // The CATALOG (valid subclass names per class) is intentionally EMPTY for now: this
    // phase ships the selection *mechanism* (timing, preview signal, server validation,
    // request plumbing) without authoring subclass content. The flow stays dormant — the
    // preview reports no options and nothing is required — and activates automatically,
    // with no code change, once a class is given catalog entries here.

    private static final int DEFAULT_SUBCLASS_LEVEL = 3;

    private static final Map<String, Integer> SUBCLASS_LEVEL = Map.ofEntries(
            Map.entry("sorcerer", 1),
            Map.entry("warlock", 1),
            Map.entry("bard", 3),
            Map.entry("cleric", 3),
            Map.entry("druid", 3),
            Map.entry("wizard", 3),
            Map.entry("barbarian", 3),
            Map.entry("fighter", 3),
            Map.entry("monk", 3),
            Map.entry("paladin", 3),
            Map.entry("ranger", 3),
            Map.entry("rogue", 3)
    );

    /** Valid subclass names per class. Empty until content is authored (see note above). */
    private static final Map<String, List<String>> SUBCLASS_CATALOG = Map.of();

    /** The character level at which the given class selects its subclass. */
    public static int subclassLevelFor(String clazz) {
        if (clazz == null) return DEFAULT_SUBCLASS_LEVEL;
        return SUBCLASS_LEVEL.getOrDefault(clazz.trim().toLowerCase(), DEFAULT_SUBCLASS_LEVEL);
    }

    /** Selectable subclass names for a class (empty when no catalog content exists yet). */
    public static List<String> subclassesFor(String clazz) {
        if (clazz == null) return List.of();
        return SUBCLASS_CATALOG.getOrDefault(clazz.trim().toLowerCase(), List.of());
    }

    // ── Ability Score Improvement levels (Phase 4) ──────────────────────────────
    // Every class gains an ASI (or feat) at 4/8/12/16/19. Fighter and Rogue get extra
    // ones (Fighter at 6 & 14, Rogue at 10) per the 2024 PHB. Feats are deferred — this
    // phase implements the ASI choice only.

    private static final Set<Integer> DEFAULT_ASI_LEVELS = Set.of(4, 8, 12, 16, 19);

    private static final Map<String, Set<Integer>> ASI_LEVELS = Map.of(
            "fighter", Set.of(4, 6, 8, 12, 14, 16, 19),
            "rogue", Set.of(4, 8, 10, 12, 16, 19)
    );

    /** Whether the given class receives an Ability Score Improvement at the given level. */
    public static boolean isAsiLevel(String clazz, int level) {
        if (clazz == null) return DEFAULT_ASI_LEVELS.contains(level);
        return ASI_LEVELS.getOrDefault(clazz.trim().toLowerCase(), DEFAULT_ASI_LEVELS).contains(level);
    }

    /** Maximum value any single ability score can reach via normal advancement. */
    public static final int MAX_ABILITY_SCORE = 20;
}
