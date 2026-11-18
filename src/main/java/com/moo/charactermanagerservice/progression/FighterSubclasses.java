package com.moo.charactermanagerservice.progression;

import com.moo.charactermanagerservice.dto.FeatureGain;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Subclass features for the Fighter's 2024 Player's Handbook subclasses, keyed by the exact
 * subclass name in ClassProgression's subclass catalog, then by the Fighter level at which
 * each feature is gained. Descriptions are concise summaries, not rules text.
 */
final class FighterSubclasses {

    private FighterSubclasses() {}

    private static FeatureGain f(String name, String desc) {
        return new FeatureGain(name, desc);
    }

    /** subclass name (exact catalog spelling) -> level -> features gained at that level. */
    static Map<String, Map<Integer, List<FeatureGain>>> all() {
        Map<String, Map<Integer, List<FeatureGain>>> m = new LinkedHashMap<>();
        m.put("Battle Master", battleMaster());
        m.put("Champion", champion());
        m.put("Eldritch Knight", eldritchKnight());
        m.put("Psi Warrior", psiWarrior());
        return m;
    }

    private static Map<Integer, List<FeatureGain>> battleMaster() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(3, List.of(
                f("Combat Superiority", "You learn three maneuvers fueled by four d8 Superiority Dice, which you "
                        + "regain on a Short or Long Rest. Maneuver saves use DC 8 + your Proficiency Bonus + your "
                        + "Strength or Dexterity modifier. You gain more maneuvers and dice at higher levels."),
                f("Student of War", "You gain proficiency with one type of Artisan's Tools of your choice and one "
                        + "skill of your choice from the Fighter skill list.")));
        f.put(7, List.of(
                f("Know Your Enemy", "As a Bonus Action, you learn whether a creature you can see within 30 feet has "
                        + "any Immunities, Resistances, or Vulnerabilities and what they are. Once per Long Rest, or "
                        + "expend a Superiority Die to use it again.")));
        f.put(10, List.of(
                f("Improved Combat Superiority", "Your Superiority Dice become d10s.")));
        f.put(15, List.of(
                f("Relentless", "Once per turn when you use a maneuver, you can roll 1d8 and use that number instead "
                        + "of expending a Superiority Die.")));
        f.put(18, List.of(
                f("Ultimate Combat Superiority", "Your Superiority Dice become d12s.")));
        return f;
    }

    private static Map<Integer, List<FeatureGain>> champion() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(3, List.of(
                f("Improved Critical", "Your attack rolls with weapons and Unarmed Strikes score a Critical Hit on a "
                        + "roll of 19 or 20 on the d20."),
                f("Remarkable Athlete", "You have Advantage on Initiative rolls and Strength (Athletics) checks. "
                        + "Immediately after you score a Critical Hit, you can move up to half your Speed without "
                        + "provoking Opportunity Attacks.")));
        f.put(7, List.of(
                f("Additional Fighting Style", "You gain another Fighting Style feat of your choice.")));
        f.put(10, List.of(
                f("Heroic Warrior", "During combat, you can give yourself Heroic Inspiration whenever you start your "
                        + "turn without it.")));
        f.put(15, List.of(
                f("Superior Critical", "Your attack rolls with weapons and Unarmed Strikes now score a Critical Hit "
                        + "on a roll of 18, 19, or 20 on the d20.")));
        f.put(18, List.of(
                f("Survivor", "You have Advantage on Death Saving Throws, and a roll of 18-20 on one counts as a 20. "
                        + "At the start of each of your turns while you are Bloodied and have at least 1 Hit Point, "
                        + "you regain Hit Points equal to 5 + your Constitution modifier.")));
        return f;
    }

    private static Map<Integer, List<FeatureGain>> eldritchKnight() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(3, List.of(
                f("Spellcasting", "You cast Wizard spells using Intelligence as your spellcasting ability. You start "
                        + "with two Wizard cantrips and three prepared level 1 Wizard spells, gain more spells and "
                        + "spell slots as you level, and can use an Arcane Focus."),
                f("War Bond", "Over a 1-hour ritual you bond with a weapon, up to two at a time. You can't be "
                        + "disarmed of a bonded weapon unless you are Incapacitated, and you can summon it to your "
                        + "hand as a Bonus Action if it is on the same plane of existence.")));
        f.put(7, List.of(
                f("War Magic", "When you take the Attack action, you can replace one of the attacks with a casting "
                        + "of one of your Wizard cantrips that has a casting time of an action.")));
        f.put(10, List.of(
                f("Eldritch Strike", "When you hit a creature with a weapon attack, it has Disadvantage on the next "
                        + "saving throw it makes against a spell you cast before the end of your next turn.")));
        f.put(15, List.of(
                f("Arcane Charge", "When you use Action Surge, you can teleport up to 30 feet to an unoccupied space "
                        + "you can see, either before or after the additional action.")));
        f.put(18, List.of(
                f("Improved War Magic", "When you take the Attack action, you can replace two of the attacks with a "
                        + "casting of one of your level 1 or level 2 Wizard spells that has a casting time of an "
                        + "action.")));
        return f;
    }

    private static Map<Integer, List<FeatureGain>> psiWarrior() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(3, List.of(
                f("Psionic Power", "You gain a pool of Psionic Energy Dice: four d6s at level 3, growing in number "
                        + "and size as you level. You regain one expended die on a Short Rest and all of them on a "
                        + "Long Rest."),
                f("Psionic Power: Protective Field", "When you or another creature you can see within 30 feet takes "
                        + "damage, you can use your Reaction and expend a Psionic Energy Die to reduce the damage by "
                        + "the roll plus your Intelligence modifier (minimum reduction of 1)."),
                f("Psionic Power: Psionic Strike", "Once on each of your turns, right after you hit a target within "
                        + "30 feet with a weapon attack and deal damage, you can expend a Psionic Energy Die to deal "
                        + "extra Force damage equal to the roll plus your Intelligence modifier."),
                f("Psionic Power: Telekinetic Movement", "As a Magic action, you move a Large or smaller loose "
                        + "object or one willing creature other than you, within 30 feet, up to 30 feet. Once per "
                        + "Short or Long Rest, or expend a Psionic Energy Die to use it again.")));
        f.put(7, List.of(
                f("Telekinetic Adept", "Psi-Powered Leap: as a Bonus Action, gain a Fly Speed of twice your Speed "
                        + "until the end of the turn (once per Short or Long Rest, or expend a die). Telekinetic "
                        + "Thrust: Psionic Strike can force a Strength save or knock the target Prone or push it "
                        + "10 feet.")));
        f.put(10, List.of(
                f("Guarded Mind", "You have Resistance to Psychic damage. If you start your turn Charmed or "
                        + "Frightened, you can expend a Psionic Energy Die (no action required) to end every effect "
                        + "on you causing those conditions.")));
        f.put(15, List.of(
                f("Bulwark of Force", "As a Bonus Action, choose creatures (which can include you) within 30 feet, "
                        + "up to your Intelligence modifier (minimum one). Each has Half Cover for 1 minute or until "
                        + "you are Incapacitated. Once per Long Rest, or expend a Psionic Energy Die.")));
        f.put(18, List.of(
                f("Telekinetic Master", "You can cast Telekinesis without components, using Intelligence. While you "
                        + "concentrate on it, you can make one weapon attack as a Bonus Action on each of your "
                        + "turns. Once per Long Rest, or expend a Psionic Energy Die to cast it again.")));
        return f;
    }
}
