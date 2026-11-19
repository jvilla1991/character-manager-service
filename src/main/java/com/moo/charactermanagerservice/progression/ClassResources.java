package com.moo.charactermanagerservice.progression;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
