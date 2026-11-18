package com.moo.charactermanagerservice.progression;

import com.moo.charactermanagerservice.dto.FeatureGain;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Subclass features for the Rogue's 2024 Player's Handbook subclasses, keyed by the exact
 * subclass name in ClassProgression's subclass catalog, then by the Rogue level at which
 * each feature is gained. Descriptions are concise summaries, not rules text.
 */
final class RogueSubclasses {

    private RogueSubclasses() {}

    private static FeatureGain f(String name, String desc) {
        return new FeatureGain(name, desc);
    }

    /** subclass name (exact catalog spelling) -> level -> features gained at that level. */
    static Map<String, Map<Integer, List<FeatureGain>>> all() {
        Map<String, Map<Integer, List<FeatureGain>>> m = new LinkedHashMap<>();
        m.put("Arcane Trickster", arcaneTrickster());
        m.put("Assassin", assassin());
        m.put("Soulknife", soulknife());
        m.put("Thief", thief());
        return m;
    }

    private static Map<Integer, List<FeatureGain>> arcaneTrickster() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(3, List.of(
                f("Spellcasting",
                        "You cast spells from the Wizard list using Intelligence. You know Mage Hand "
                                + "plus two other Wizard cantrips, prepare a small number of Wizard "
                                + "spells, and gain spell slots as a one-third caster."),
                f("Mage Hand Legerdemain",
                        "You can cast Mage Hand as a Bonus Action and make the hand Invisible. You "
                                + "can control it as a Bonus Action and make Dexterity (Sleight of "
                                + "Hand) checks through it.")));
        f.put(9, List.of(
                f("Magical Ambush",
                        "If you have the Invisible condition when you cast a spell on a creature, "
                                + "it has Disadvantage on any saving throw it makes against that "
                                + "spell on the same turn.")));
        f.put(13, List.of(
                f("Versatile Trickster",
                        "When you use the Trip option of Cunning Strike on a creature, you can "
                                + "also apply that option to another creature within 5 feet of "
                                + "your Mage Hand.")));
        f.put(17, List.of(
                f("Spell Thief",
                        "As a Reaction when a spell targets you or includes you in its area, the "
                                + "caster makes an Intelligence save against your spell DC. On a "
                                + "failure the spell doesn't affect you, and if you could cast "
                                + "it, you steal it for 8 hours. Once per Long Rest.")));
        return f;
    }

    private static Map<Integer, List<FeatureGain>> assassin() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(3, List.of(
                f("Assassinate",
                        "You have Advantage on Initiative rolls. In the first round of combat you "
                                + "have Advantage on attacks against creatures that haven't taken "
                                + "a turn, and a Sneak Attack hit that round deals extra damage "
                                + "equal to your Rogue level."),
                f("Assassin's Tools",
                        "You gain a Disguise Kit and a Poisoner's Kit, and you have proficiency "
                                + "with both.")));
        f.put(9, List.of(
                f("Infiltration Expertise",
                        "After studying a person for at least 1 hour, you can flawlessly mimic "
                                + "their speech, handwriting, or both. Also, Steady Aim no longer "
                                + "reduces your Speed to 0.")));
        f.put(13, List.of(
                f("Envenom Weapons",
                        "When you use the Poison option of Cunning Strike, the target also takes "
                                + "2d6 Poison damage if it fails the saving throw. This damage "
                                + "ignores Resistance to Poison.")));
        f.put(17, List.of(
                f("Death Strike",
                        "When you hit with Sneak Attack in the first round of combat, the target "
                                + "must succeed on a Constitution save (DC 8 + Dexterity modifier "
                                + "+ Proficiency Bonus) or the attack's damage against it is "
                                + "doubled.")));
        return f;
    }

    private static Map<Integer, List<FeatureGain>> soulknife() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(3, List.of(
                f("Psionic Power",
                        "You have a pool of Psionic Energy Dice (four d6s at level 3, growing in "
                                + "size and number as you level). You regain one on a Short Rest "
                                + "and all on a Long Rest. They fuel Psi-Bolstered Knack and "
                                + "Psychic Whispers."),
                f("Psi-Bolstered Knack",
                        "When you fail an ability check using a skill or tool you're proficient "
                                + "with, you can roll a Psionic Energy Die and add it to the "
                                + "check. The die is spent only if this turns the failure into a "
                                + "success."),
                f("Psychic Whispers",
                        "As a Magic action, pick creatures you can see up to your Proficiency "
                                + "Bonus and roll a Psionic Energy Die; for that many hours you "
                                + "can speak telepathically with them within 1 mile. The first "
                                + "use after each Long Rest costs no die."),
                f("Psychic Blades",
                        "When you take the Attack action or make an Opportunity Attack, you can "
                                + "manifest a blade: 1d6 Psychic, Finesse, Thrown (60/120), Vex "
                                + "mastery. After attacking with it you can make a Bonus Action "
                                + "attack with a second blade (1d4).")));
        f.put(9, List.of(
                f("Soul Blades",
                        "Homing Strikes: when you miss with a Psychic Blade, you can add a "
                                + "Psionic Energy Die to the attack roll (spent only if it then "
                                + "hits). Psychic Teleportation: as a Bonus Action, spend and "
                                + "roll a die to teleport up to 10 feet times the roll.")));
        f.put(13, List.of(
                f("Psychic Veil",
                        "As a Magic action, you gain the Invisible condition for 1 hour or until "
                                + "you deal damage or force a saving throw. Once per Long Rest, "
                                + "or spend a Psionic Energy Die to use it again.")));
        f.put(17, List.of(
                f("Rend Mind",
                        "When your Psychic Blades deal Sneak Attack damage, you can force a "
                                + "Wisdom save (DC 8 + Dexterity modifier + Proficiency Bonus) or "
                                + "the target is Stunned for up to 1 minute. Once per Long Rest, "
                                + "or spend three Psionic Energy Dice.")));
        return f;
    }

    private static Map<Integer, List<FeatureGain>> thief() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(3, List.of(
                f("Fast Hands",
                        "As a Bonus Action, you can make a Dexterity (Sleight of Hand) check to "
                                + "pick a lock, disarm a trap, or pick a pocket, or you can take "
                                + "the Utilize action or the Magic action to use a magic item."),
                f("Second-Story Work",
                        "You gain a Climb Speed equal to your Speed, and you can use Dexterity "
                                + "instead of Strength to determine how far you jump.")));
        f.put(9, List.of(
                f("Supreme Sneak",
                        "You gain the Stealth Attack option for Cunning Strike (cost 1d6): if "
                                + "you're Invisible from the Hide action, the attack doesn't end "
                                + "that condition if you end the turn behind Three-Quarters or "
                                + "Total Cover.")));
        f.put(13, List.of(
                f("Use Magic Device",
                        "You can attune to four magic items. When you use an item's charges, "
                                + "roll 1d6; on a 6 no charges are spent. You can use any Spell "
                                + "Scroll with Intelligence; spells above level 1 need an Arcana "
                                + "check (DC 10 + spell level).")));
        f.put(17, List.of(
                f("Thief's Reflexes",
                        "You take two turns in the first round of any combat: one at your normal "
                                + "Initiative and a second at your Initiative minus 10.")));
        return f;
    }
}
