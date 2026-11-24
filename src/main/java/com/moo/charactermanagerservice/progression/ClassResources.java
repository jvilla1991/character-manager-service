package com.moo.charactermanagerservice.progression;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Limited-use class features a character tracks between rests (D&D 2024, plus
 * the World of Warcraft tabletop module's Death Knight and Demon Hunter): how
 * many uses or points the character has at most, and what a Short Rest gives
 * back. A Long Rest always gives everything back.
 *
 * <p>The maximum is always computed here from class, subclass, level and
 * ability scores — only the amount USED is stored on the character
 * ({@code pc.class_resources}). The client mirrors this table in
 * {@code utils/class-resources.ts}; both sides run the same list of cases
 * ({@code ClassResourcesTest} / {@code class-resources.spec.ts}) so the two
 * cannot drift apart unnoticed.
 *
 * <p>A World of Warcraft variant stored on a base class (a Shaman is a
 * Barbarian, an Evoker a Sorcerer) has the base class's resources. The one
 * replacement is the Death Knight (Paladin + Oath of the Ebon Blade): its Death
 * Coil pool takes the place of Lay On Hands.
 *
 * <p>Feats add counters of their own ({@link #forFeats}): the free casts and limited uses of the
 * Arcana Unleashed feats. They are the same kind of resource, stored in the same map, listed
 * after the class's and named for the feat they belong to.
 */
public final class ClassResources {

    private ClassResources() {}

    /** What a Short Rest gives back. A Long Rest always restores everything. */
    public enum ShortRest {
        /** Nothing: the resource returns on a Long Rest only. */
        NONE,
        /** One expended use. */
        ONE,
        /** Everything. */
        ALL
    }

    /**
     * One tracked resource of one character.
     *
     * @param key       stable key under which the used amount is stored ("secondWind")
     * @param name      what the sheet calls it ("Second Wind")
     * @param feature   the class feature it belongs to, as the feature lists name it
     *                  ("Font of Magic" for Sorcery Points)
     * @param max       the most the character can have, always at least 1
     * @param shortRest what a Short Rest gives back
     * @param pool      true for a pool of points or dice spent several at a time
     *                  (Lay On Hands, Focus Points); false for a handful of uses
     * @param unit      what one of it is called, for labels ("uses", "points", "d8")
     */
    public record Resource(String key, String name, String feature, int max, ShortRest shortRest,
                           boolean pool, String unit) {}

    /**
     * The resources a character has, in display order. Empty for a class with
     * none. Ability scores may be null (treated as 10).
     */
    public static List<Resource> forCharacter(String clazz, String subclass, Integer level,
                                              Integer charisma, Integer wisdom) {
        return forCharacter(clazz, subclass, level, charisma, wisdom, null, List.of());
    }

    /**
     * The resources a character has, in display order: the class's, then one or two per feat
     * ({@link #forFeats}). Ability scores may be null (treated as 10).
     *
     * @param feats names on the character that may be feats (its Origin feat and its features);
     *              names that are not a feat with a counter are ignored
     */
    public static List<Resource> forCharacter(String clazz, String subclass, Integer level,
                                              Integer charisma, Integer wisdom, Integer intelligence,
                                              Collection<String> feats) {
        List<Resource> out = new ArrayList<>(forClass(clazz, subclass, level, charisma, wisdom));
        out.addAll(forFeats(feats, level, intelligence, wisdom, charisma));
        return out;
    }

    /** One feat counter: every part but the maximum, which depends on the character. */
    private record FeatUse(String feat, String key, String name, FeatMax max, ShortRest shortRest) {}

    /** How a feat counter's maximum is worked out. */
    private enum FeatMax {
        /** One use. */
        ONE,
        /** As many as the Proficiency Bonus. */
        PROFICIENCY_BONUS,
        /** The highest of the Intelligence, Wisdom and Charisma modifiers, at least one. */
        BEST_MENTAL_MODIFIER
    }

    /**
     * The Arcana Unleashed feats with something to count, in the book's order (Origin, General,
     * Epic Boon). Everything returns on a Long Rest; Spell Overload on a Short Rest too. Boon of
     * Magic School Mastery is not listed: it has one counter per school taken, added in
     * {@link #forFeats}.
     */
    private static final List<FeatUse> FEAT_USES = List.of(
            new FeatUse("Arcane Artist", "inspiringMagic", "Inspiring Magic", FeatMax.ONE, ShortRest.NONE),
            new FeatUse("Arcane Infiltrator", "cunningDiversion", "Cunning Diversion",
                    FeatMax.PROFICIENCY_BONUS, ShortRest.NONE),
            new FeatUse("Arcane Omens", "helpfulPremonition", "Helpful Premonition",
                    FeatMax.PROFICIENCY_BONUS, ShortRest.NONE),
            new FeatUse("Arcane Overload", "powerSurge", "Power Surge", FeatMax.ONE, ShortRest.NONE),
            new FeatUse("Arcane Safeguard", "bonusActionResistance", "Bonus Action Resistance",
                    FeatMax.PROFICIENCY_BONUS, ShortRest.NONE),
            new FeatUse("Arcane Undertaker", "understandingOfDeath", "Understanding of Death",
                    FeatMax.ONE, ShortRest.NONE),
            new FeatUse("Familiar Friend", "findFamiliarFreeCast", "Find Familiar (free cast)",
                    FeatMax.ONE, ShortRest.NONE),
            new FeatUse("Familiar Friend", "helpfulFriend", "Helpful Friend",
                    FeatMax.PROFICIENCY_BONUS, ShortRest.NONE),
            new FeatUse("Portal Jumper", "portalStep", "Portal Step", FeatMax.PROFICIENCY_BONUS, ShortRest.NONE),
            new FeatUse("Transmuted Anatomy", "resilientAnatomy", "Resilient Anatomy",
                    FeatMax.PROFICIENCY_BONUS, ShortRest.NONE),
            new FeatUse("Divination Adept", "prescientIntervention", "Prescient Intervention",
                    FeatMax.ONE, ShortRest.NONE),
            new FeatUse("Magic Connoisseur", "connoisseurLevel1Spell", "Level 1 spell (free cast)",
                    FeatMax.ONE, ShortRest.NONE),
            new FeatUse("Magic Connoisseur", "connoisseurLevel2Spell", "Level 2 spell (free cast)",
                    FeatMax.ONE, ShortRest.NONE),
            new FeatUse("Spell Resistant", "magicResistant", "Magic Resistant",
                    FeatMax.PROFICIENCY_BONUS, ShortRest.NONE),
            new FeatUse("Spell Subterfuge", "shroudingSpells", "Shrouding Spells",
                    FeatMax.BEST_MENTAL_MODIFIER, ShortRest.NONE),
            new FeatUse("Warlike Familiar", "battleFamiliarFreeCast", "Battle Familiar (free cast)",
                    FeatMax.ONE, ShortRest.NONE),
            new FeatUse("Boon of Erupting Spellpower", "spellOverload", "Spell Overload",
                    FeatMax.ONE, ShortRest.ALL)
    );

    /**
     * The counters a character's feats give it: for each feat it holds, in the order held, that
     * feat's free casts and limited uses. The resource's {@code feature} is the feat's name, so
     * the sheet puts the counter under the feat.
     *
     * <p>"Proficiency Bonus" is the bonus for the character's level. Spell Subterfuge counts
     * "the modifier of the ability this feat increased"; that increase is made by hand and not
     * recorded, so the highest of the Intelligence, Wisdom and Charisma modifiers is used (at
     * least 1). Boon of Magic School Mastery has one Signature Arcanum cast per school taken.
     *
     * @param feats names on the character that may be feats; case and surrounding spaces do not
     *              matter, and a name listed twice counts once
     */
    public static List<Resource> forFeats(Collection<String> feats, Integer level, Integer intelligence,
                                          Integer wisdom, Integer charisma) {
        List<Resource> out = new ArrayList<>();
        if (feats == null || feats.isEmpty()) return out;
        int lvl = level == null ? 1 : Math.max(1, level);
        int proficiencyBonus = ClassProgression.proficiencyBonusForLevel(lvl);
        int bestMental = Math.max(1, Math.max(modifier(intelligence), Math.max(modifier(wisdom), modifier(charisma))));

        Set<String> held = new LinkedHashSet<>();
        for (String feat : feats) {
            if (feat != null && !feat.isBlank()) held.add(feat.trim().toLowerCase(Locale.ROOT));
        }
        for (String feat : held) {
            for (FeatUse use : FEAT_USES) {
                if (!use.feat().toLowerCase(Locale.ROOT).equals(feat)) continue;
                int max = switch (use.max()) {
                    case ONE -> 1;
                    case PROFICIENCY_BONUS -> proficiencyBonus;
                    case BEST_MENTAL_MODIFIER -> bestMental;
                };
                out.add(uses(use.key(), use.name(), use.feat(), max, use.shortRest()));
            }
            for (String school : FeatCatalog.SCHOOLS_OF_MAGIC) {
                String mastery = FeatCatalog.schoolMasteryFeat(school);
                if (mastery.toLowerCase(Locale.ROOT).equals(feat)) {
                    out.add(uses("signatureArcanum" + school, "Signature Arcanum (" + school + ")", mastery,
                            1, ShortRest.NONE));
                }
            }
        }
        return out;
    }

    private static int modifier(Integer score) {
        return ClassProgression.abilityModifier(score == null ? 10 : score);
    }

    /** The class's own resources; see {@link #forCharacter(String, String, Integer, Integer, Integer)}. */
    private static List<Resource> forClass(String clazz, String subclass, Integer level,
                                           Integer charisma, Integer wisdom) {
        List<Resource> out = new ArrayList<>();
        String c = clazz == null ? "" : clazz.trim().toLowerCase(Locale.ROOT);
        String sub = subclass == null ? "" : subclass.trim();
        int lvl = level == null ? 1 : Math.max(1, level);
        int cha = ClassProgression.abilityModifier(charisma == null ? 10 : charisma);
        int wis = ClassProgression.abilityModifier(wisdom == null ? 10 : wisdom);

        switch (c) {
            case "fighter" -> {
                out.add(uses("secondWind", "Second Wind", "Second Wind",
                        lvl >= 10 ? 4 : lvl >= 4 ? 3 : 2, ShortRest.ONE));
                if (lvl >= 2) {
                    out.add(uses("actionSurge", "Action Surge", "Action Surge", lvl >= 17 ? 2 : 1, ShortRest.ALL));
                }
            }
            case "barbarian" -> out.add(uses("rage", "Rage", "Rage",
                    lvl >= 17 ? 6 : lvl >= 12 ? 5 : lvl >= 6 ? 4 : lvl >= 3 ? 3 : 2, ShortRest.ONE));
            case "bard" -> out.add(uses("bardicInspiration", "Bardic Inspiration", "Bardic Inspiration",
                    Math.max(1, cha), lvl >= 5 ? ShortRest.ALL : ShortRest.NONE)); // Font of Inspiration at 5
            case "cleric" -> {
                if (lvl >= 2) {
                    out.add(uses("channelDivinity", "Channel Divinity", "Channel Divinity",
                            lvl >= 18 ? 4 : lvl >= 6 ? 3 : 2, ShortRest.ONE));
                }
            }
            case "druid" -> {
                if (lvl >= 2) {
                    out.add(uses("wildShape", "Wild Shape", "Wild Shape",
                            lvl >= 17 ? 4 : lvl >= 6 ? 3 : 2, ShortRest.ONE));
                }
            }
            case "monk" -> {
                if (lvl >= 2) {
                    out.add(pool("focusPoints", "Focus Points", "Monk's Focus", lvl, ShortRest.ALL, "points"));
                }
            }
            case "paladin" -> {
                if (ClassProgression.EBON_BLADE.equalsIgnoreCase(sub)) {
                    // Death Knight: Death Coil replaces Lay On Hands — a pool of d8s, 1 + Charisma modifier.
                    out.add(pool("deathCoil", "Death Coil", "Death Coil", Math.max(1, 1 + cha), ShortRest.NONE, "d8"));
                } else {
                    out.add(pool("layOnHands", "Lay On Hands", "Lay On Hands", 5 * lvl, ShortRest.NONE, "points"));
                }
                if (lvl >= 3) {
                    out.add(uses("channelDivinity", "Channel Divinity", "Channel Divinity",
                            lvl >= 11 ? 3 : 2, ShortRest.ONE));
                }
            }
            case "ranger" -> {
                if (ClassProgression.DEMON_HUNTER.equalsIgnoreCase(sub) && lvl >= 3) {
                    // Wisdom modifier (at least 1), one more from Ranger 13, two more from 17.
                    int bonus = lvl >= 17 ? 2 : lvl >= 13 ? 1 : 0;
                    out.add(pool("soulFragments", "Soul Fragments", "Soul Fragments",
                            Math.max(1, wis) + bonus, ShortRest.NONE, "fragments"));
                }
            }
            case "sorcerer" -> {
                if (lvl >= 2) {
                    out.add(pool("sorceryPoints", "Sorcery Points", "Font of Magic", lvl, ShortRest.NONE, "points"));
                }
            }
            default -> { }
        }
        return out;
    }

    /** True for the class whose spell slots (Pact Magic) all return on a Short Rest. */
    public static boolean regainsSpellSlotsOnShortRest(String clazz) {
        return clazz != null && ClassProgression.PACT_CASTER.equals(clazz.trim().toLowerCase(Locale.ROOT));
    }

    private static Resource uses(String key, String name, String feature, int max, ShortRest shortRest) {
        return new Resource(key, name, feature, max, shortRest, false, "uses");
    }

    private static Resource pool(String key, String name, String feature, int max, ShortRest shortRest, String unit) {
        return new Resource(key, name, feature, max, shortRest, true, unit);
    }
}
