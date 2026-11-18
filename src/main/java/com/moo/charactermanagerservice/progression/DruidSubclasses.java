package com.moo.charactermanagerservice.progression;

import com.moo.charactermanagerservice.dto.FeatureGain;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Subclass features for the Druid's 2024 Player's Handbook subclasses, keyed by the exact
 * subclass name in ClassProgression's subclass catalog, then by the Druid level at which
 * each feature is gained. Descriptions are concise summaries, not rules text.
 */
final class DruidSubclasses {

    private DruidSubclasses() {}

    private static FeatureGain f(String name, String desc) {
        return new FeatureGain(name, desc);
    }

    /** subclass name (exact catalog spelling) -> level -> features gained at that level. */
    static Map<String, Map<Integer, List<FeatureGain>>> all() {
        Map<String, Map<Integer, List<FeatureGain>>> m = new LinkedHashMap<>();
        m.put("Circle of the Land", land());
        m.put("Circle of the Moon", moon());
        m.put("Circle of the Sea", sea());
        m.put("Circle of the Stars", stars());
        return m;
    }

    private static Map<Integer, List<FeatureGain>> land() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(3, List.of(
                f("Circle of the Land Spells",
                        "After each Long Rest, choose a land type: Arid, Polar, Temperate, or "
                                + "Tropical. You always have that land's circle spells for your Druid "
                                + "level and lower prepared (new spells unlock at Druid levels 3, 5, "
                                + "7, and 9) until you choose again."),
                f("Land's Aid",
                        "As a Magic action, spend a Wild Shape use on a 10-foot-radius Sphere within "
                                + "60 feet. Creatures you choose make a Constitution save for 2d6 "
                                + "Necrotic (half on a success) and one creature you choose heals "
                                + "2d6. Both become 3d6 at level 10 and 4d6 at level 14.")));
        f.put(6, List.of(
                f("Natural Recovery",
                        "Once per Long Rest, you can cast one of your prepared Circle Spells without "
                                + "a spell slot. Also once per Long Rest, after a Short Rest you "
                                + "recover spell slots with a combined level up to half your Druid "
                                + "level (rounded up), none level 6 or higher.")));
        f.put(10, List.of(
                f("Nature's Ward",
                        "You are immune to the Poisoned condition and have Resistance to a damage "
                                + "type set by your current land: Arid is Fire, Polar is Cold, "
                                + "Temperate is Lightning, Tropical is Poison.")));
        f.put(14, List.of(
                f("Nature's Sanctuary",
                        "As a Magic action, spend a Wild Shape use to grow spectral trees in a "
                                + "15-foot Cube within 120 feet for 1 minute. You and allies there "
                                + "have Half Cover, and allies gain your Nature's Ward Resistance. A "
                                + "Bonus Action moves it up to 60 feet.")));
        return f;
    }

    private static Map<Integer, List<FeatureGain>> moon() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(3, List.of(
                f("Circle Forms",
                        "Your Wild Shape improves: the maximum Challenge Rating is your Druid level "
                                + "divided by 3 (rounded down), your AC is 13 + Wisdom modifier if "
                                + "that beats the Beast's AC, and you gain Temporary Hit Points equal "
                                + "to three times your Druid level."),
                f("Circle of the Moon Spells",
                        "You always have these spells prepared and can cast them while in a Wild "
                                + "Shape form. "
                                + "3rd — Cure Wounds, Moonbeam, Starry Wisp; "
                                + "5th — Conjure Animals; "
                                + "7th — Fount of Moonlight; "
                                + "9th — Mass Cure Wounds.")));
        f.put(6, List.of(
                f("Improved Circle Forms",
                        "In a Wild Shape form, each attack can deal its normal damage type or Radiant "
                                + "damage, your choice on each hit, and you add your Wisdom modifier "
                                + "to your Constitution saving throws.")));
        f.put(10, List.of(
                f("Moonlight Step",
                        "As a Bonus Action, teleport up to 30 feet and gain Advantage on your next "
                                + "attack roll this turn. Uses equal your Wisdom modifier (minimum 1) "
                                + "per Long Rest; you can also spend a level 2+ spell slot to restore "
                                + "one use.")));
        f.put(14, List.of(
                f("Lunar Form",
                        "Once per turn, a Wild Shape attack that hits deals an extra 2d10 Radiant "
                                + "damage. When you use Moonlight Step, you can also teleport one "
                                + "willing creature within 10 feet of you to a space within 10 feet "
                                + "of your destination.")));
        return f;
    }

    private static Map<Integer, List<FeatureGain>> sea() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(3, List.of(
                f("Circle of the Sea Spells",
                        "You always have these spells prepared. "
                                + "3rd — Fog Cloud, Gust of Wind, Ray of Frost, Shatter, Thunderwave; "
                                + "5th — Lightning Bolt, Water Breathing; "
                                + "7th — Control Water, Ice Storm; "
                                + "9th — Conjure Elemental, Hold Monster."),
                f("Wrath of the Sea",
                        "As a Bonus Action, spend a Wild Shape use for a 5-foot Emanation of sea "
                                + "spray lasting 10 minutes. Then, and as a Bonus Action later, one "
                                + "creature in it must pass a Constitution save or take d6s of Cold "
                                + "damage equal to your Wisdom modifier and, if Large or smaller, be "
                                + "pushed up to 15 feet.")));
        f.put(6, List.of(
                f("Aquatic Affinity",
                        "Your Wrath of the Sea Emanation grows to 10 feet, and you gain a Swim Speed "
                                + "equal to your Speed.")));
        f.put(10, List.of(
                f("Stormborn",
                        "While Wrath of the Sea is active, you have a Fly Speed equal to your Speed "
                                + "and Resistance to Cold, Lightning, and Thunder damage.")));
        f.put(14, List.of(
                f("Oceanic Gift",
                        "You can manifest Wrath of the Sea around a willing creature within 60 feet "
                                + "instead of yourself; it uses your spell save DC and Wisdom "
                                + "modifier. Spend two Wild Shape uses to manifest it around both of "
                                + "you.")));
        return f;
    }

    private static Map<Integer, List<FeatureGain>> stars() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(3, List.of(
                f("Star Map",
                        "You have a star map that works as a Spellcasting Focus. While holding it, "
                                + "Guidance and Guiding Bolt are prepared, and you can cast Guiding "
                                + "Bolt without a spell slot a number of times equal to your Wisdom "
                                + "modifier (minimum 1) per Long Rest."),
                f("Starry Form",
                        "As a Bonus Action, spend a Wild Shape use for a 10-minute starry form: "
                                + "Archer (Bonus Action ranged spell attack, 60 feet, 1d8 + Wisdom "
                                + "modifier Radiant), Chalice (when you heal with a spell slot, a "
                                + "creature within 30 feet regains 1d8 + Wisdom modifier), or Dragon "
                                + "(treat 9 or lower as 10 on Intelligence and Wisdom checks and "
                                + "Concentration saves).")));
        f.put(6, List.of(
                f("Cosmic Omen",
                        "After each Long Rest, roll a die. Until your next Long Rest you can use your "
                                + "Reaction when a creature within 30 feet makes a D20 Test to add "
                                + "1d6 (even roll, Weal) or subtract 1d6 (odd roll, Woe). Uses equal "
                                + "your Wisdom modifier (minimum 1) per Long Rest.")));
        f.put(10, List.of(
                f("Twinkling Constellations",
                        "Archer and Chalice use 2d8 instead of 1d8, and Dragon gives you a 20-foot "
                                + "Fly Speed with hover. At the start of each of your turns in Starry "
                                + "Form, you can switch constellations.")));
        f.put(14, List.of(
                f("Full of Stars",
                        "While in your Starry Form, you have Resistance to Bludgeoning, Piercing, and "
                                + "Slashing damage.")));
        return f;
    }
}
