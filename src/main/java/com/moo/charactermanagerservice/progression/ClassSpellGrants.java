package com.moo.charactermanagerservice.progression;

import com.moo.charactermanagerservice.dto.FeatureProgressionEntry;
import com.moo.charactermanagerservice.dto.SpellGrant;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Spells a class or subclass feature always keeps prepared (D&D 5e 2024), and the level each one
 * arrives at. Three sources, all tied to the feature that grants the spell:
 * <ul>
 *   <li><b>Level-1 class features</b> ({@link #LEVEL_ONE}): a Ranger's Hunter's Mark (Favored
 *       Enemy) and a Druid's Speak with Animals (Druidic). The create wizard adds these to a new
 *       character; they are listed here so a character made before it did picks them up at its
 *       next level-up.</li>
 *   <li><b>Single-feature grants</b> ({@link #FEATURE_SPELLS}): a feature that names one or two
 *       spells — a Paladin's Divine Smite and Find Steed, a Warlock's Contact Other Plane, a
 *       Glamour Bard's Charm Person and Mirror Image. Delivered with the feature, so a subclass
 *       that replaces the feature drops the spell too: a Death Knight's Runic Smite replaces
 *       Divine Smite, spell and all.</li>
 *   <li><b>Subclass spell lists</b>: the "… Spells" feature of a catalog subclass (Oath of
 *       Devotion Spells, Life Domain Spells, Fiend Spells, …). The list is read from the feature's
 *       own text — {@code "3rd — Aid, Bless; 5th — Revivify."} — so the spells shown to the player
 *       and the spells granted can never drift apart; each row arrives at its own level.</li>
 * </ul>
 *
 * <p>Names only: the spell text lives in the app, which sends the full spell with the level-up.
 * The World of Warcraft subclasses (hero subclasses and the WoW-only oaths, domains and paths) are
 * left out on purpose: the app shows their always-prepared spells from its own tables, by
 * specialization and level. Circle of the Land is left out too — its spells are a choice made
 * again after each Long Rest.
 */
public final class ClassSpellGrants {

    private ClassSpellGrants() {}

    /** One grant and the character level it arrives at. */
    private record LeveledGrant(int level, SpellGrant grant) {}

    /** class (lower-case) -> level-1 feature -> the spells it always keeps prepared. */
    private static final Map<String, Map<String, List<String>>> LEVEL_ONE = Map.of(
            "ranger", Map.of("Favored Enemy", List.of("Hunter's Mark")),
            "druid", Map.of("Druidic", List.of("Speak with Animals"))
    );

    /**
     * class (lower-case) -> feature name (class or subclass feature) -> the spells it grants.
     * A cantrip may be listed (a Warrior of the Elements' Elementalism); it is simply known.
     */
    private static final Map<String, Map<String, List<String>>> FEATURE_SPELLS = Map.of(
            "bard", Map.of(
                    "Words of Creation", List.of("Power Word Heal", "Power Word Kill"),
                    // College of Glamour.
                    "Beguiling Magic", List.of("Charm Person", "Mirror Image"),
                    "Mantle of Majesty", List.of("Command")),
            "monk", Map.of(
                    // Warrior of the Elements.
                    "Manipulate Elements", List.of("Elementalism")),
            "paladin", Map.of(
                    "Divine Smite", List.of("Divine Smite"),
                    "Faithful Steed", List.of("Find Steed")),
            "warlock", Map.of(
                    "Contact Patron", List.of("Contact Other Plane"),
                    // Great Old One Patron.
                    "Eldritch Hex", List.of("Hex")),
            "wizard", Map.of(
                    // Abjurer.
                    "Spell Breaker", List.of("Counterspell", "Dispel Magic"),
                    // Illusionist.
                    "Phantasmal Creatures", List.of("Summon Beast", "Summon Fey"))
    );

    /** One row of a subclass spell list in a feature's text: {@code "5th — Aid, Zone of Truth;"}. */
    private static final Pattern LIST_ROW = Pattern.compile("(\\d+)(?:st|nd|rd|th) — ([^;.]+)[;.]");

    /**
     * The rows of a subclass spell list written in a feature description, as
     * {@code level -> spell names} in text order; empty when the text holds no such list.
     */
    static Map<Integer, List<String>> spellListRows(String desc) {
        Map<Integer, List<String>> rows = new LinkedHashMap<>();
        if (desc == null) return rows;
        Matcher m = LIST_ROW.matcher(desc);
        while (m.find()) {
            List<String> names = new ArrayList<>();
            for (String name : m.group(2).split(",")) {
                if (!name.isBlank()) names.add(name.trim());
            }
            rows.computeIfAbsent(Integer.parseInt(m.group(1)), k -> new ArrayList<>()).addAll(names);
        }
        return rows;
    }

    /** True when the subclass is one every member of the class can pick (not a World of Warcraft one). */
    private static boolean isCatalogSubclass(String clazz, String subclass) {
        if (subclass == null) return false;
        String s = subclass.trim();
        return ClassProgression.subclassesFor(clazz).stream().anyMatch(o -> o.equalsIgnoreCase(s));
    }

    /** Every grant the character's class, subclass and specialization give, each with its level. */
    private static List<LeveledGrant> allGrants(String clazz, String subclass, String specialization) {
        List<LeveledGrant> grants = new ArrayList<>();
        if (clazz == null) return grants;
        String key = clazz.trim().toLowerCase();

        LEVEL_ONE.getOrDefault(key, Map.of()).forEach((feature, spells) ->
                spells.forEach(spell -> grants.add(new LeveledGrant(1, new SpellGrant(spell, feature)))));

        Map<String, List<String>> byFeature = FEATURE_SPELLS.getOrDefault(key, Map.of());
        boolean readLists = isCatalogSubclass(clazz, subclass);
        for (FeatureProgressionEntry feature : ClassFeatures.progression(clazz, subclass, specialization)) {
            for (String spell : byFeature.getOrDefault(feature.name(), List.of())) {
                grants.add(new LeveledGrant(feature.level(), new SpellGrant(spell, feature.name())));
            }
            if (readLists && feature.subclass()) {
                spellListRows(feature.desc()).forEach((rowLevel, spells) -> {
                    // A row can't arrive before the feature that carries the list.
                    int level = Math.max(rowLevel, feature.level());
                    spells.forEach(spell ->
                            grants.add(new LeveledGrant(level, new SpellGrant(spell, feature.name()))));
                });
            }
        }
        return grants;
    }

    /**
     * The always-prepared spells that arrive exactly on reaching {@code level}, in feature order;
     * empty for most classes and levels.
     */
    public static List<SpellGrant> grantsAt(String clazz, String subclass, String specialization, int level) {
        return allGrants(clazz, subclass, specialization).stream()
                .filter(g -> g.level() == level)
                .map(LeveledGrant::grant)
                .toList();
    }

    /**
     * Every always-prepared spell a character of {@code level} should have, earliest first and
     * each spell once (the first feature to grant it names it). A level-up offers the ones the
     * character still lacks, so a character that passed a level before its grant existed catches up.
     */
    public static List<SpellGrant> grantsThrough(String clazz, String subclass, String specialization, int level) {
        Map<String, LeveledGrant> byName = new LinkedHashMap<>();
        allGrants(clazz, subclass, specialization).stream()
                .filter(g -> g.level() <= level)
                .sorted(java.util.Comparator.comparingInt(LeveledGrant::level))
                .forEach(g -> byName.putIfAbsent(g.grant().name().toLowerCase(), g));
        return byName.values().stream().map(LeveledGrant::grant).toList();
    }

    /** Feature names this table grants spells for by name, per class (for the coverage test). */
    static Set<String> featureNames(String clazz) {
        return FEATURE_SPELLS.getOrDefault(clazz, Map.of()).keySet();
    }
}
