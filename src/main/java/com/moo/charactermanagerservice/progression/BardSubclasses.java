package com.moo.charactermanagerservice.progression;

import com.moo.charactermanagerservice.dto.FeatureGain;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Subclass features for the Bard's 2024 Player's Handbook subclasses, keyed by the exact
 * subclass name in ClassProgression's subclass catalog, then by the Bard level at which
 * each feature is gained. Descriptions are concise summaries, not rules text.
 */
final class BardSubclasses {

    private BardSubclasses() {}

    private static FeatureGain f(String name, String desc) {
        return new FeatureGain(name, desc);
    }

    /** subclass name (exact catalog spelling) -> level -> features gained at that level. */
    static Map<String, Map<Integer, List<FeatureGain>>> all() {
        Map<String, Map<Integer, List<FeatureGain>>> m = new LinkedHashMap<>();
        m.put("College of Dance", dance());
        m.put("College of Glamour", glamour());
        m.put("College of Lore", lore());
        m.put("College of Valor", valor());
        return m;
    }

    private static Map<Integer, List<FeatureGain>> dance() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(3, List.of(
                f("Dazzling Footwork",
                        "With no armor or Shield, your AC is 10 + Dexterity + Charisma modifiers, you "
                                + "have Advantage on Performance checks to dance, and Unarmed Strikes "
                                + "can use Dexterity and deal Bardic Inspiration die + Dexterity "
                                + "modifier Bludgeoning. Spending Bardic Inspiration also lets you "
                                + "make one Unarmed Strike.")));
        f.put(6, List.of(
                f("Inspiring Movement",
                        "When an enemy ends its turn within 5 feet of you, you can use your Reaction "
                                + "and one Bardic Inspiration to move up to half your Speed. One ally "
                                + "within 30 feet can then use their Reaction to do the same. Neither "
                                + "move provokes Opportunity Attacks."),
                f("Tandem Footwork",
                        "When you roll Initiative, you can spend one Bardic Inspiration and roll the "
                                + "die. You and each ally within 30 feet who can see or hear you add "
                                + "the number rolled to Initiative.")));
        f.put(14, List.of(
                f("Leading Evasion",
                        "When a Dexterity save would let you take half damage, you take none on a "
                                + "success and half on a failure. Creatures within 5 feet of you "
                                + "making the same save can share this benefit. It doesn't work "
                                + "while you are Incapacitated.")));
        return f;
    }

    private static Map<Integer, List<FeatureGain>> glamour() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(3, List.of(
                f("Beguiling Magic",
                        "You always have Charm Person and Mirror Image prepared. After you cast an "
                                + "Enchantment or Illusion spell with a spell slot, a creature within "
                                + "60 feet must pass a Wisdom save or be Charmed or Frightened for 1 "
                                + "minute. Once per Long Rest, or spend a Bardic Inspiration to reuse "
                                + "it."),
                f("Mantle of Inspiration",
                        "As a Bonus Action, spend one Bardic Inspiration and roll the die. Up to your "
                                + "Charisma modifier (minimum 1) other creatures within 60 feet gain "
                                + "Temporary Hit Points equal to twice the roll and can use a "
                                + "Reaction to move their Speed without provoking Opportunity "
                                + "Attacks.")));
        f.put(6, List.of(
                f("Mantle of Majesty",
                        "You always have Command prepared. As a Bonus Action, cast it without a slot "
                                + "and, for 1 minute (Concentration), keep casting it that way each "
                                + "turn; creatures Charmed by you automatically fail the save. Once "
                                + "per Long Rest, or spend a level 3+ spell slot to reuse it.")));
        f.put(14, List.of(
                f("Unbreakable Majesty",
                        "As a Bonus Action, assume a majestic presence for 1 minute. The first time "
                                + "each turn a creature hits you with an attack roll, it must pass a "
                                + "Charisma save against your spell save DC or the attack misses. "
                                + "Once per Short or Long Rest.")));
        return f;
    }

    private static Map<Integer, List<FeatureGain>> lore() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(3, List.of(
                f("Bonus Proficiencies",
                        "You gain proficiency with three skills of your choice."),
                f("Cutting Words",
                        "When a creature you can see within 60 feet makes a damage roll or succeeds "
                                + "on an ability check or attack roll, you can use your Reaction and "
                                + "one Bardic Inspiration to subtract the die roll from its roll.")));
        f.put(6, List.of(
                f("Magical Discoveries",
                        "You learn two spells of your choice from the Cleric, Druid, and Wizard spell "
                                + "lists (cantrips or levels you have slots for). They are always "
                                + "prepared, and you can replace one whenever you gain a Bard level.")));
        f.put(14, List.of(
                f("Peerless Skill",
                        "When you fail an ability check or attack roll, you can spend one Bardic "
                                + "Inspiration and add the die roll to the d20, possibly turning it "
                                + "into a success. If it still fails, the Bardic Inspiration is not "
                                + "spent.")));
        return f;
    }

    private static Map<Integer, List<FeatureGain>> valor() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(3, List.of(
                f("Combat Inspiration",
                        "A creature holding your Bardic Inspiration die can also roll it to add to "
                                + "its AC against one attack that hits it (using its Reaction), or to "
                                + "add to the damage of an attack it just hit with."),
                f("Martial Training",
                        "You gain proficiency with Martial weapons and training with Medium armor and "
                                + "Shields. You can use a Simple or Martial weapon as a Spellcasting "
                                + "Focus for your Bard spells.")));
        f.put(6, List.of(
                f("Extra Attack",
                        "You can attack twice instead of once when you take the Attack action, and "
                                + "you can replace one of those attacks with a cantrip that has a "
                                + "casting time of an action.")));
        f.put(14, List.of(
                f("Battle Magic",
                        "After you cast a spell that has a casting time of an action, you can make "
                                + "one weapon attack as a Bonus Action.")));
        return f;
    }
}
