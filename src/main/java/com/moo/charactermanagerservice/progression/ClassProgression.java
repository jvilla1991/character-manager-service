package com.moo.charactermanagerservice.progression;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    // Three slot tables: the five full casters, warlock pact magic, and the half casters
    // (Paladin and Ranger — and with them the World of Warcraft classes stored under those
    // two names: Death Knight, Demon Hunter, WoW Paladin, WoW Hunter).
    //
    // Half casters also PREPARE spells from level 1 (2024 PHB): preparedSpellsFor carries their
    // Prepared Spells column, so a Paladin or Ranger level-up offers the new picks, capped at the
    // highest spell level they have slots for. They learn no cantrips from the class itself
    // (cantripsKnownFor is 0); the Blessed / Druidic / Void Warrior fighting styles add theirs
    // through the Fighting Style choice (see FeatCatalog).

    private static final Set<String> FULL_CASTERS =
            Set.of("bard", "cleric", "druid", "sorcerer", "wizard");

    /** Warlock — pact magic: a single slot level with a small number of short-rest slots. */
    public static final String PACT_CASTER = "warlock";

    /** Paladin and Ranger — half casters (2024 PHB): slots from level 1, up to spell level 5. */
    private static final Set<String> HALF_CASTERS = Set.of("paladin", "ranger");

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

    /**
     * Half-caster slots (Paladin, Ranger) by character level (rows) and spell level (columns,
     * spell levels 1-5). 2024 PHB / SRD 5.2 table; zeros = no slots.
     */
    private static final int[][] HALF_CASTER_SLOTS = {
            //  1  2  3  4  5   <- spell level
            {  2, 0, 0, 0, 0 }, // L1
            {  2, 0, 0, 0, 0 }, // L2
            {  3, 0, 0, 0, 0 }, // L3
            {  3, 0, 0, 0, 0 }, // L4
            {  4, 2, 0, 0, 0 }, // L5
            {  4, 2, 0, 0, 0 }, // L6
            {  4, 3, 0, 0, 0 }, // L7
            {  4, 3, 0, 0, 0 }, // L8
            {  4, 3, 2, 0, 0 }, // L9
            {  4, 3, 2, 0, 0 }, // L10
            {  4, 3, 3, 0, 0 }, // L11
            {  4, 3, 3, 0, 0 }, // L12
            {  4, 3, 3, 1, 0 }, // L13
            {  4, 3, 3, 1, 0 }, // L14
            {  4, 3, 3, 2, 0 }, // L15
            {  4, 3, 3, 2, 0 }, // L16
            {  4, 3, 3, 3, 1 }, // L17
            {  4, 3, 3, 3, 1 }, // L18
            {  4, 3, 3, 3, 2 }, // L19
            {  4, 3, 3, 3, 2 }, // L20
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

    /**
     * Prepared/known spells by level (2024 PHB). Full casters share the standard progression;
     * warlock uses the smaller pact table and the half casters (Paladin, Ranger) their own
     * Prepared Spells column. Returns 0 for non-casters. This is the total a caster may have
     * prepared/known at that level; the level-up delta is how many new ones they may add.
     */
    private static final int[] FULL_CASTER_PREPARED = {
            4, 5, 6, 7, 9, 10, 11, 12, 14, 15, 16, 16, 17, 17, 18, 18, 19, 20, 21, 22
    };
    private static final int[] PACT_PREPARED = {
            2, 3, 4, 5, 6, 7, 8, 9, 10, 10, 11, 11, 12, 12, 13, 13, 14, 14, 15, 15
    };
    private static final int[] HALF_CASTER_PREPARED = {
            2, 3, 4, 5, 6, 6, 7, 7, 9, 9, 10, 10, 11, 11, 12, 12, 14, 14, 15, 15
    };

    public static int preparedSpellsFor(String clazz, int level) {
        if (clazz == null || level < 1) return 0;
        String key = clazz.trim().toLowerCase();
        int idx = Math.min(level, MAX_LEVEL) - 1;
        if (FULL_CASTERS.contains(key)) return FULL_CASTER_PREPARED[idx];
        if (PACT_CASTER.equals(key)) return PACT_PREPARED[idx];
        if (HALF_CASTERS.contains(key)) return HALF_CASTER_PREPARED[idx];
        return 0;
    }

    /**
     * True when the class is a spellcaster — full, pact or half: the classes with spell slots
     * whose level-up offers spells to learn (and, for the full and pact casters, cantrips).
     */
    public static boolean isCaster(String clazz) {
        if (clazz == null) return false;
        String key = clazz.trim().toLowerCase();
        return FULL_CASTERS.contains(key) || PACT_CASTER.equals(key) || HALF_CASTERS.contains(key);
    }

    /**
     * Maximum spell slots available at a class/level, as {@code spellLevel -> maxSlots}
     * (insertion-ordered by spell level): the full-caster, half-caster (Paladin, Ranger) or
     * pact table. Empty for a class with no slots. Levels are clamped to {@value #MAX_LEVEL}.
     */
    public static Map<Integer, Integer> spellSlotsFor(String clazz, int level) {
        Map<Integer, Integer> slots = new LinkedHashMap<>();
        if (clazz == null || level < 1) return slots;
        String key = clazz.trim().toLowerCase();
        int idx = Math.min(level, MAX_LEVEL) - 1;

        if (FULL_CASTERS.contains(key) || HALF_CASTERS.contains(key)) {
            int[] row = (FULL_CASTERS.contains(key) ? FULL_CASTER_SLOTS : HALF_CASTER_SLOTS)[idx];
            for (int i = 0; i < row.length; i++) {
                if (row[i] > 0) slots.put(i + 1, row[i]);
            }
        } else if (PACT_CASTER.equals(key)) {
            int[] pact = PACT_SLOTS[idx];
            slots.put(pact[0], pact[1]);
        }
        return slots;
    }

    /**
     * True when the character has the Spellcasting or Pact Magic feature at this level — the
     * prerequisite of the Arcana Unleashed School Adept feats, Spell Subterfuge and two of its
     * Epic Boons.
     *
     * <p>Class-based today: the full casters, the Warlock, and the half casters Paladin and Ranger
     * (who have Spellcasting from level 1 in the 2024 rules), i.e. every class with a slot table
     * here. {@code subclass} is not read yet. It is the seam for a subclass that brings its own
     * Spellcasting feature and spell slots: extend this method (and {@link #highestSpellSlotLevel})
     * for it, and every feat prerequisite and feat spell table follows.
     */
    public static boolean hasSpellcastingOrPactMagic(String clazz, String subclass, int level) {
        return !spellSlotsFor(clazz, level).isEmpty();
    }

    /**
     * The highest spell slot level the character has at this level (a Warlock's Pact Magic slot
     * level counts), or 0 with no slots. From the class table alone: slots the DM added by hand do
     * not count. {@code subclass} is not read yet; see {@link #hasSpellcastingOrPactMagic}.
     */
    public static int highestSpellSlotLevel(String clazz, String subclass, int level) {
        return spellSlotsFor(clazz, level).keySet().stream().max(Integer::compareTo).orElse(0);
    }

    // ── Subclass timing & catalog (Phase 3 — mechanism only) ────────────────────
    // The level at which each class chooses its subclass. Sorcerer/Warlock are level 1
    // to match the app's existing creation flow; every other class is level 3 (2024 PHB).
    // Unmapped classes default to 3.
    //
    // The CATALOG holds the valid subclass NAMES per class (server-authoritative). Names only;
    // descriptions are presentation and live in the frontend. Sorcerer/Warlock pick at level 1
    // during character creation, so their entries here are never reached by the level-up flow
    // (kept for a complete, correct catalog); every other class activates the picker at level 3.

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

    /** Valid subclass names per class (2024 PHB, plus Twilight Domain from Tasha's). */
    private static final Map<String, List<String>> SUBCLASS_CATALOG = Map.ofEntries(
            // The WoW Shaman's two paths are offered on top of these to WoW barbarians only — see
            // WOW_ONLY_SUBCLASSES.
            Map.entry("barbarian", List.of(
                    "Path of the Berserker", "Path of the Wild Heart", "Path of the World Tree", "Path of the Zealot")),
            Map.entry("bard", List.of(
                    "College of Dance", "College of Glamour", "College of Lore", "College of Valor")),
            // The WoW Priest's Discipline and Shadow Domains are offered on top of these to WoW
            // clerics only — see WOW_ONLY_SUBCLASSES.
            Map.entry("cleric", List.of(
                    "Life Domain", "Light Domain", "Trickery Domain", "Twilight Domain", "War Domain")),
            Map.entry("druid", List.of(
                    "Circle of the Land", "Circle of the Moon", "Circle of the Sea", "Circle of the Stars")),
            Map.entry("fighter", List.of(
                    "Battle Master", "Champion", "Eldritch Knight", "Psi Warrior")),
            Map.entry("monk", List.of(
                    "Warrior of Mercy", "Warrior of Shadow", "Warrior of the Elements", "Warrior of the Open Hand")),
            // Oath of the Ebon Blade (Death Knight) is a creation-only hero subclass — see
            // HERO_SUBCLASSES — and is intentionally absent from this level-up list. Oath of the
            // Holy is offered on top of these to WoW paladins only — see WOW_ONLY_SUBCLASSES.
            Map.entry("paladin", List.of(
                    "Oath of Devotion", "Oath of Glory", "Oath of the Ancients", "Oath of Vengeance")),
            // Demon Hunter is likewise a creation-only hero subclass, absent from this list.
            Map.entry("ranger", List.of(
                    "Beast Master", "Fey Wanderer", "Gloom Stalker", "Hunter")),
            // Swashbuckler is offered on top of these to WoW rogues only — see WOW_ONLY_SUBCLASSES.
            Map.entry("rogue", List.of(
                    "Arcane Trickster", "Assassin", "Soulknife", "Thief")),
            Map.entry("wizard", List.of(
                    "Abjurer", "Diviner", "Evoker", "Illusionist")),
            // Sorcerer/Warlock choose at level 1 (creation); listed for catalog completeness.
            Map.entry("sorcerer", List.of(
                    "Aberrant Sorcery", "Clockwork Sorcery", "Draconic Sorcery", "Wild Magic Sorcery")),
            Map.entry("warlock", List.of(
                    "Archfey Patron", "Celestial Patron", "Fiend Patron", "Great Old One Patron"))
    );

    /** The character level at which the given class selects its subclass. */
    public static int subclassLevelFor(String clazz) {
        if (clazz == null) return DEFAULT_SUBCLASS_LEVEL;
        return SUBCLASS_LEVEL.getOrDefault(clazz.trim().toLowerCase(), DEFAULT_SUBCLASS_LEVEL);
    }

    /**
     * Selectable subclass names for a class at its level-up subclass choice (empty when no catalog
     * content exists yet). Hero subclasses are deliberately NOT in this list — see
     * {@link #isHeroSubclass}.
     */
    public static List<String> subclassesFor(String clazz) {
        if (clazz == null) return List.of();
        return SUBCLASS_CATALOG.getOrDefault(clazz.trim().toLowerCase(), List.of());
    }

    /** The WoW Paladin's own oath (World of Warcraft tabletop module), offered only to WoW paladins. */
    public static final String OATH_OF_THE_HOLY = "Oath of the Holy";

    /** The WoW Rogue's Outlaw archetype, offered only to WoW rogues. */
    public static final String SWASHBUCKLER = "Swashbuckler";

    /** The WoW Shaman's Elemental path (barbarian), offered only to WoW barbarians. */
    public static final String PATH_OF_THE_ELEMENTS = "Path of the Elements";

    /** The WoW Shaman's Enhancement path (barbarian), offered only to WoW barbarians. */
    public static final String PATH_OF_THE_SPIRITUAL_GUARDIAN = "Path of the Spiritual Guardian";

    /** The WoW Priest's Discipline domain (cleric), offered only to WoW clerics. */
    public static final String DISCIPLINE_DOMAIN = "Discipline Domain";

    /** The WoW Priest's Shadow domain (cleric), offered only to WoW clerics. */
    public static final String SHADOW_DOMAIN = "Shadow Domain";

    /** The WoW Monk's Brewmaster tradition, offered only to WoW monks. */
    public static final String WARRIOR_OF_MYSTIC_BREWS = "Warrior of Mystic Brews";

    /** The PHB ranger subclass whose Primal Companion a WoW Hunter may give a pet family. */
    public static final String BEAST_MASTER = "Beast Master";

    /** The PHB sorcerer subclass every WoW Evoker must have (see {@link ClassSpecializations#requiredSubclass}). */
    public static final String DRACONIC_SORCERY = "Draconic Sorcery";

    /**
     * Subclasses offered at the level-up subclass choice ONLY to a World of Warcraft variant of the
     * class — i.e. a member whose specialization is one of the class's {@link ClassSpecializations}
     * values. A base-game member (specialization null) never sees these.
     */
    private static final Map<String, List<String>> WOW_ONLY_SUBCLASSES = Map.of(
            "barbarian", List.of(PATH_OF_THE_ELEMENTS, PATH_OF_THE_SPIRITUAL_GUARDIAN),
            "cleric", List.of(DISCIPLINE_DOMAIN, SHADOW_DOMAIN),
            "monk", List.of(WARRIOR_OF_MYSTIC_BREWS),
            "paladin", List.of(OATH_OF_THE_HOLY),
            "rogue", List.of(SWASHBUCKLER)
    );

    /**
     * Selectable subclass names at the level-up subclass choice for a class + specialization: the
     * class's {@link #subclassesFor(String) catalog}, plus its WoW-only subclasses when the
     * specialization marks the character as that class's WoW variant (see
     * {@link #isClassSpecialization}). When that variant requires a subclass (e.g. the Evoker's
     * Draconic Sorcery — see {@link #requiredSubclass}) only that subclass is offered. Hero
     * subclasses are never included.
     */
    public static List<String> subclassesFor(String clazz, String specialization) {
        List<String> base = subclassesFor(clazz);
        if (!isClassSpecialization(clazz, specialization)) return base;
        String required = requiredSubclass(clazz, specialization);
        if (required != null) return List.of(required);
        List<String> wowOnly = WOW_ONLY_SUBCLASSES.getOrDefault(clazz.trim().toLowerCase(), List.of());
        if (wowOnly.isEmpty()) return base;
        List<String> all = new java.util.ArrayList<>(base);
        all.addAll(wowOnly);
        return List.copyOf(all);
    }

    // ── Hero subclasses (World of Warcraft tabletop module) ─────────────────────
    // A "hero class" is chosen at CHARACTER CREATION and stored as a base class + a locked
    // subclass — e.g. a Death Knight is clazz "paladin" + subclass "Oath of the Ebon Blade", a
    // Demon Hunter is clazz "ranger" + subclass "Demon Hunter". Hero subclasses are valid only at
    // creation: they are kept out of SUBCLASS_CATALOG so a normal member of the base class is never
    // offered one at its level-3 subclass choice, and because the subclass is already set from
    // level 1, LevelUpService never asks for another. Each hero subclass carries a specialization
    // (persisted as pc.specialization) drawn from its own list.

    /**
     * One hero subclass as data.
     *
     * @param name             exact subclass name (matched case-insensitively)
     * @param baseClass        the class it belongs to (lower-case)
     * @param specializations  allowed specialization values (exact, case-sensitive)
     * @param respecOnLevelUp  whether a level-up may change an already-chosen specialization
     */
    public record HeroSubclass(String name, String baseClass, List<String> specializations,
                               boolean respecOnLevelUp) {}

    /** The Death Knight hero subclass (base class: paladin). */
    public static final String EBON_BLADE = "Oath of the Ebon Blade";

    /** The Demon Hunter hero subclass (base class: ranger). */
    public static final String DEMON_HUNTER = "Demon Hunter";

    private static final List<HeroSubclass> HERO_SUBCLASSES = List.of(
            // Runeforging: a Death Knight may swap runes whenever they gain a level.
            new HeroSubclass(EBON_BLADE, "paladin", List.of("Blood", "Frost", "Unholy"), true),
            // No respec rule in the module; a DM can still change it via the DM update path.
            new HeroSubclass(DEMON_HUNTER, "ranger", List.of("Devourer", "Havoc", "Vengeance"), false)
    );

    /** The hero subclass with this name (case-insensitive, trimmed), if it is one. */
    public static Optional<HeroSubclass> heroSubclass(String subclass) {
        if (subclass == null) return Optional.empty();
        String s = subclass.trim();
        return HERO_SUBCLASSES.stream().filter(h -> h.name().equalsIgnoreCase(s)).findFirst();
    }

    /** True when the subclass name is a creation-only hero subclass (case-insensitive). */
    public static boolean isHeroSubclass(String subclass) {
        return heroSubclass(subclass).isPresent();
    }

    /**
     * Whether the hero subclass may be taken by the given class at creation. False for non-hero
     * subclasses (callers check {@link #isHeroSubclass} first).
     */
    public static boolean heroSubclassAllowedFor(String subclass, String clazz) {
        return clazz != null && heroSubclass(subclass)
                .map(h -> h.baseClass().equals(clazz.trim().toLowerCase()))
                .orElse(false);
    }

    // ── Class specializations (World of Warcraft variants of a base class) ──────
    // Unlike a hero subclass, a WoW class variant does NOT lock the subclass: e.g. the WoW Druid is
    // the ordinary druid class plus a specialization (Balance/Feral/Guardian/Restoration) that only
    // guides choices — its subclass is still picked normally at level 3 from SUBCLASS_CATALOG. A
    // druid whose specialization is null is a base-game druid. The specialization is persisted in
    // the same pc.specialization column the hero subclasses use. The WoW Paladin
    // (Holy/Protection/Retribution) works the same way, except that it also unlocks a WoW-only
    // subclass (Oath of the Holy) at its level-3 choice; a paladin with the Ebon Blade hero
    // subclass is a Death Knight and keeps the hero subclass's list instead. The WoW Rogue
    // (Assassination/Outlaw/Subtlety) likewise unlocks the Swashbuckler at its level-3 choice, and
    // the WoW Shaman — a barbarian with Elemental/Enhancement/Restoration — unlocks the Path of the
    // Elements and the Path of the Spiritual Guardian. The Shaman is also the first variant whose
    // specialization swaps a generic class feature (Totems for Primal Knowledge) — see
    // ClassFeatures' variant feature overrides. The WoW Warlock (Affliction/Demonology/
    // Destruction) adds only the specialization: no WoW-only patron and no feature overrides, and the
    // WoW Mage (wizard + Arcane/Fire/Frost) is the same — its WoW General feats live in FeatCatalog.
    // The WoW Monk (Brewmaster/Mistweaver/Windwalker) unlocks the Warrior of Mystic Brews at its
    // level-3 choice, with no feature overrides; its General feats also live in FeatCatalog. The
    // WoW Priest — a cleric with Discipline/Holy/Shadow — unlocks the Discipline Domain and the
    // Shadow Domain at its level-3 choice, with no feature overrides. The WoW Evoker — a sorcerer
    // with Augmentation/Devastation/Preservation — is the first variant that REQUIRES a subclass:
    // it must be a Draconic Sorcery sorcerer (see ClassSpecializations#requiredSubclass), and it
    // gains Evoker Spells in place of Draconic Spells (see ClassFeatures' variant features). The WoW
    // Hunter — a ranger with Beast Mastery/Marksmanship/Survival and no hero subclass — may take any
    // ranger subclass; a Beast Mastery hunter gains Pet Specialization in place of Roving, and a
    // Beast Mastery or Survival hunter whose subclass is Beast Master may record its companion's
    // pet family (see PET_FAMILIES). A ranger with the Demon Hunter hero subclass keeps the hero subclass's
    // Devourer/Havoc/Vengeance list instead.

    /**
     * WoW specializations available to a base class that has no hero subclass.
     *
     * @param baseClass              the class (lower-case)
     * @param specializations        allowed specialization values (exact, case-sensitive)
     * @param respecOnLevelUp        whether a level-up may change an already-chosen specialization
     * @param recommendedSubclasses  specialization → the subclass it recommends (advisory only,
     *                               never enforced)
     * @param recommendedFightingStyles specialization → the Fighting Style options it recommends at
     *                               the class's Fighting Style choice (advisory only, never enforced;
     *                               empty for classes without that choice)
     * @param requiredSubclass       the subclass every member of this WoW variant MUST have (e.g. the
     *                               Evoker's Draconic Sorcery), enforced on create, update and
     *                               level-up; {@code null} when the variant may take any subclass. A
     *                               base-game member (specialization null) is never restricted.
     */
    public record ClassSpecializations(String baseClass, List<String> specializations,
                                       boolean respecOnLevelUp,
                                       Map<String, String> recommendedSubclasses,
                                       Map<String, List<String>> recommendedFightingStyles,
                                       String requiredSubclass) {

        /** A class whose specializations recommend no Fighting Style and require no subclass. */
        public ClassSpecializations(String baseClass, List<String> specializations, boolean respecOnLevelUp,
                                    Map<String, String> recommendedSubclasses) {
            this(baseClass, specializations, respecOnLevelUp, recommendedSubclasses, Map.of(), null);
        }

        /** A class whose specializations require no subclass. */
        public ClassSpecializations(String baseClass, List<String> specializations, boolean respecOnLevelUp,
                                    Map<String, String> recommendedSubclasses,
                                    Map<String, List<String>> recommendedFightingStyles) {
            this(baseClass, specializations, respecOnLevelUp, recommendedSubclasses, recommendedFightingStyles, null);
        }
    }

    private static final List<ClassSpecializations> CLASS_SPECIALIZATIONS = List.of(
            // WoW Shaman. Barbarians have no Fighting Style choice, so no recommended styles.
            new ClassSpecializations("barbarian", List.of("Elemental", "Enhancement", "Restoration"), true,
                    Map.of("Elemental", PATH_OF_THE_ELEMENTS,
                            "Enhancement", PATH_OF_THE_SPIRITUAL_GUARDIAN,
                            "Restoration", "Path of the World Tree")),
            // WoW Priest. Clerics have no Fighting Style choice, so no recommended styles. Holy
            // recommends the PHB Life Domain; Discipline and Shadow recommend their WoW-only domains.
            new ClassSpecializations("cleric", List.of("Discipline", "Holy", "Shadow"), true,
                    Map.of("Discipline", DISCIPLINE_DOMAIN,
                            "Holy", "Life Domain",
                            "Shadow", SHADOW_DOMAIN)),
            new ClassSpecializations("druid", List.of("Balance", "Feral", "Guardian", "Restoration"), true,
                    Map.of("Balance", "Circle of the Stars",
                            "Feral", "Circle of the Moon",
                            "Guardian", "Circle of the Moon",
                            "Restoration", "Circle of the Land")),
            // WoW Warrior. The fighter's Fighting Style is a level-1 choice made at creation (never
            // due on level-up), so the preview's recommended styles are normally empty; the data is
            // kept for consistency with the paladin.
            new ClassSpecializations("fighter", List.of("Arms", "Fury", "Protection"), true,
                    Map.of("Arms", "Battle Master",
                            "Fury", "Champion",
                            "Protection", "Battle Master"),
                    Map.of("Arms", List.of("Great Weapon Fighting"),
                            "Fury", List.of("Two-Weapon Fighting"),
                            "Protection", List.of("Interception", "Protection"))),
            // WoW Paladin. A Death Knight (paladin + Oath of the Ebon Blade) is NOT covered by this
            // entry: its hero subclass's own list (Blood/Frost/Unholy) always wins.
            new ClassSpecializations("paladin", List.of("Holy", "Protection", "Retribution"), true,
                    Map.of("Holy", OATH_OF_THE_HOLY,
                            "Protection", "Oath of Devotion",
                            "Retribution", "Oath of Vengeance"),
                    Map.of("Holy", List.of("Blessed Warrior"),
                            "Protection", List.of("Interception", "Protection"),
                            "Retribution", List.of("Great Weapon Fighting"))),
            // WoW Rogue. Rogues have no Fighting Style choice, so no recommended styles.
            new ClassSpecializations("rogue", List.of("Assassination", "Outlaw", "Subtlety"), true,
                    Map.of("Assassination", "Assassin",
                            "Outlaw", SWASHBUCKLER,
                            "Subtlety", "Soulknife")),
            // WoW Hunter. A Demon Hunter (ranger + Demon Hunter) is NOT covered by this entry: its
            // hero subclass's own list (Devourer/Havoc/Vengeance) always wins. The ranger's Fighting
            // Style is due at level 2, so these styles show in the level-up preview.
            new ClassSpecializations("ranger", List.of("Beast Mastery", "Marksmanship", "Survival"), true,
                    Map.of("Beast Mastery", BEAST_MASTER,
                            "Marksmanship", "Hunter",
                            "Survival", BEAST_MASTER),
                    Map.of("Beast Mastery", List.of("Archery"),
                            "Marksmanship", List.of("Archery"),
                            "Survival", List.of("Great Weapon Fighting"))),
            // WoW Evoker. Every Evoker is a Draconic Sorcery sorcerer (required, not just
            // recommended); sorcerers pick their subclass at level 1 (creation), so the level-up
            // preview never has a subclass due. No Fighting Style choice.
            new ClassSpecializations("sorcerer", List.of("Augmentation", "Devastation", "Preservation"), true,
                    Map.of("Augmentation", DRACONIC_SORCERY,
                            "Devastation", DRACONIC_SORCERY,
                            "Preservation", DRACONIC_SORCERY),
                    Map.of(), DRACONIC_SORCERY),
            // WoW Warlock. No WoW-only patron and no Fighting Style choice. Warlocks pick their
            // patron at level 1 (creation), so the level-up preview never has a subclass due; the
            // recommendations are kept for consistency with the other variants.
            new ClassSpecializations("warlock", List.of("Affliction", "Demonology", "Destruction"), true,
                    Map.of("Affliction", "Great Old One Patron",
                            "Demonology", "Fiend Patron",
                            "Destruction", "Fiend Patron")),
            // WoW Mage. No WoW-only school, no Fighting Style choice and no feature overrides; every
            // specialization recommends the PHB Evoker. The mage's WoW content is its General feats
            // (Clearcasting, Fingers of Frost, Hot Streak — see FeatCatalog), open to any class.
            new ClassSpecializations("wizard", List.of("Arcane", "Fire", "Frost"), true,
                    Map.of("Arcane", "Evoker",
                            "Fire", "Evoker",
                            "Frost", "Evoker")),
            // WoW Monk. Monks have no Fighting Style choice, so no recommended styles. Brewmaster
            // recommends its WoW-only Warrior of Mystic Brews; the others recommend PHB subclasses.
            new ClassSpecializations("monk", List.of("Brewmaster", "Mistweaver", "Windwalker"), true,
                    Map.of("Brewmaster", WARRIOR_OF_MYSTIC_BREWS,
                            "Mistweaver", "Warrior of Mercy",
                            "Windwalker", "Warrior of the Open Hand"))
    );

    /** The WoW specializations for a class (case-insensitive, trimmed), if it has any. */
    public static Optional<ClassSpecializations> classSpecializations(String clazz) {
        if (clazz == null) return Optional.empty();
        String c = clazz.trim().toLowerCase();
        return CLASS_SPECIALIZATIONS.stream().filter(s -> s.baseClass().equals(c)).findFirst();
    }

    /**
     * The subclass a class specialization recommends (e.g. a Balance druid → Circle of the Stars),
     * or null when the class has no specializations or the value is not one of them.
     */
    public static String recommendedSubclass(String clazz, String specialization) {
        if (specialization == null) return null;
        return classSpecializations(clazz)
                .map(s -> s.recommendedSubclasses().get(specialization))
                .orElse(null);
    }

    /**
     * The Fighting Style options a class specialization recommends (e.g. a Retribution paladin →
     * Great Weapon Fighting), or an empty list when there is no such recommendation. Callers filter
     * against the options actually offered.
     */
    public static List<String> recommendedFightingStyles(String clazz, String specialization) {
        if (specialization == null) return List.of();
        return classSpecializations(clazz)
                .map(s -> s.recommendedFightingStyles().getOrDefault(specialization, List.of()))
                .orElse(List.of());
    }

    /**
     * The subclass a class specialization REQUIRES (e.g. any Evoker sorcerer → Draconic Sorcery), or
     * null when the specialization is not one of the class's WoW specializations or its variant may
     * take any subclass. A base-game member (null specialization) is never restricted.
     */
    public static String requiredSubclass(String clazz, String specialization) {
        if (!isClassSpecialization(clazz, specialization)) return null;
        return classSpecializations(clazz).map(ClassSpecializations::requiredSubclass).orElse(null);
    }

    /**
     * True when the specialization is one of the class's WoW {@link ClassSpecializations} values
     * (exact match) — i.e. the character is that class's World of Warcraft variant. False for a
     * null specialization and for hero-subclass specializations (e.g. a Death Knight's "Frost").
     */
    public static boolean isClassSpecialization(String clazz, String specialization) {
        if (specialization == null) return false;
        return classSpecializations(clazz)
                .map(s -> s.specializations().contains(specialization))
                .orElse(false);
    }

    // ── Hunter pet families (World of Warcraft tabletop module) ─────────────────
    // A WoW Hunter with the Beast Mastery or Survival specialization whose subclass is Beast Master
    // may record the pet family of its Primal Companion (pc.pet_family, V51). A Marksmanship hunter
    // keeps no pet (the module's pets belong to Beast Mastery and Survival hunters). The family decides which
    // pet specialization (Cunning/Ferocity/Tenacity) a Beast Mastery hunter's Pet Specialization
    // feature uses; that mapping is presentation and lives in the frontend.

    /** The exact pet family names a WoW Hunter's beast may have (matched case-sensitively). */
    public static final List<String> PET_FAMILIES = List.of(
            "Basilisk", "Bat", "Bear", "Beetle", "Bird of Prey", "Blood Beast", "Boar", "Camel",
            "Carrion Bird", "Cat", "Courser", "Crab", "Crocolisk", "Direhorn", "Dolphin", "Dragonhawk",
            "Feathermane", "Fox", "Frenzy", "Gorilla", "Gruffhorn", "Hopper", "Hound", "Hydra", "Hyena",
            "Lizard", "Mammoth", "Mechanized Animal", "Monkey", "Moth", "Oxen", "Raptor", "Ray",
            "Riverbeast", "Rodent", "Scalehide", "Scorpid", "Serpent", "Shark", "Spider", "Stag",
            "Tallstrider", "Threshadon", "Turtle", "Wasp", "Waterfowl", "Wolf");

    /** True when the name is exactly one of {@link #PET_FAMILIES}. */
    public static boolean isPetFamily(String name) {
        return name != null && PET_FAMILIES.contains(name);
    }

    /** The WoW Hunter specializations whose Beast Master keeps a pet (exact, case-sensitive). */
    public static final List<String> PET_SPECIALIZATIONS = List.of("Beast Mastery", "Survival");

    /**
     * Whether a PC may record a pet family: only a WoW Hunter (a ranger — never a Demon Hunter —
     * whose specialization is one of {@link #PET_SPECIALIZATIONS}) whose subclass is
     * {@value #BEAST_MASTER} (case-insensitive).
     */
    public static boolean petFamilyAllowed(String clazz, String subclass, String specialization) {
        return clazz != null && "ranger".equals(clazz.trim().toLowerCase())
                && isClassSpecialization(clazz, specialization)
                && PET_SPECIALIZATIONS.contains(specialization)
                && subclass != null && BEAST_MASTER.equalsIgnoreCase(subclass.trim());
    }

    /** True when the subclass is the Demon Hunter hero subclass (case-insensitive). */
    public static boolean isDemonHunter(String subclass) {
        return subclass != null && DEMON_HUNTER.equalsIgnoreCase(subclass.trim());
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
