package com.moo.charactermanagerservice.progression;

import com.moo.charactermanagerservice.dto.FeatureGain;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Subclass features for the Fighter's 2024 Player's Handbook subclasses (plus the Arcane Archer
 * from Arcana Unleashed), keyed by the exact subclass name in ClassProgression's subclass
 * catalog, then by the Fighter level at which each feature is gained. Descriptions are concise
 * summaries, not rules text.
 */
final class FighterSubclasses {

    private FighterSubclasses() {}

    private static FeatureGain f(String name, String desc) {
        return new FeatureGain(name, desc);
    }

    /** subclass name (exact catalog spelling) -> level -> features gained at that level. */
    static Map<String, Map<Integer, List<FeatureGain>>> all() {
        Map<String, Map<Integer, List<FeatureGain>>> m = new LinkedHashMap<>();
        m.put(SubclassChoices.ARCANE_ARCHER, arcaneArcher());
        m.put("Battle Master", battleMaster());
        m.put("Champion", champion());
        m.put("Eldritch Knight", eldritchKnight());
        m.put("Psi Warrior", psiWarrior());
        return m;
    }

    /**
     * Arcane Archer (Arcana Unleashed). The cantrip, the replacement skills and the Arcane Shot
     * options known are choices the player makes: see {@link SubclassChoices}. The options are
     * described in one feature of their own so the sheet can show them.
     */
    private static Map<Integer, List<FeatureGain>> arcaneArcher() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(3, List.of(
                f(SubclassChoices.ARCANE_ARCHER_LORE, "You know Druidcraft or Prestidigitation (your choice) and "
                        + "cast it with Intelligence. You gain proficiency in Arcana and Nature; for each of the two "
                        + "you already have, you choose another skill from the Fighter's level 1 skill list "
                        + "instead."),
                f("Arcane Shot", "You know two Arcane Shot options and learn one more at Fighter levels 7, 10, 15 "
                        + "and 18; each time you learn one, you can also swap one you know. Once per turn, when a "
                        + "ranged attack with an Ammunition weapon hits a creature and deals damage, you can apply "
                        + "one option (an option without an attack roll is chosen as you attack). Uses equal your "
                        + "Intelligence modifier (minimum 1), regained on a Short or Long Rest. Your Arcane Shot Die "
                        + "is a d6 (d8 at level 10, d10 at 15, d12 at 18), and saves use DC 8 + your Intelligence "
                        + "modifier + your Proficiency Bonus."),
                f("Arcane Shot Options", "Banishing Shot: one die of extra Psychic damage, and a Charisma save or "
                        + "the target is banished (Incapacitated, Speed 0) until the end of its next turn. "
                        + "Beguiling Shot: two dice of extra Psychic damage, and a Wisdom save or the target is "
                        + "Charmed by you or by an ally within 30 feet of it until the start of your next turn. "
                        + "Bursting Shot: the target and every creature within 10 feet of it take Force damage "
                        + "equal to two dice. Enfeebling Shot: two dice of extra Necrotic damage, and a "
                        + "Constitution save or the target is Poisoned until the end of its next turn and deals "
                        + "one die less damage on each hit. Grasping Shot: one die of extra Slashing damage, and a "
                        + "Strength save or the target is Restrained for 1 minute or until you use this option "
                        + "again; an action and a Strength (Athletics) check against your save DC frees it. "
                        + "Piercing Shot: no attack roll; the shot is a 30-foot Line that ignores cover, and each "
                        + "creature in it makes a Dexterity save, taking the weapon's damage plus two dice of "
                        + "Piercing damage, or half on a success. Seeking Shot: no attack roll; choose a creature "
                        + "you saw in the last minute, and the shot goes around corners to it. Within long range "
                        + "it makes a Dexterity save, taking the weapon's damage plus two dice of Force damage and "
                        + "revealing its location to you, or half damage only on a success. Shadow Shot: one die "
                        + "of extra Psychic damage, and a Wisdom save or the target is Blinded until the end of "
                        + "its next turn.")));
        f.put(7, List.of(
                f("Curving Shot", "When you miss with a ranged attack from an Ammunition weapon, you can use a Bonus "
                        + "Action to send the shot at another creature you can see, within the weapon's range and "
                        + "within 60 feet of the first target, with a new attack roll."),
                f("Magical Ammunition", "As a Magic action, you enchant a piece of nonmagical ammunition and fire "
                        + "it at a solid surface in range, where it sticks and takes effect. Darkening: a 15-foot "
                        + "Emanation of shadow for 1 minute that puts out nonmagical flames and gives -5 to Wisdom "
                        + "(Perception) checks and Passive Perception. Unlocking: with a loud knock, each object in "
                        + "a 15-foot Emanation that is locked without magic, stuck or barred opens (one lock per "
                        + "object). Vine: a 120-foot vine to climb, gone after 10 minutes. Once per Short or Long "
                        + "Rest, or spend a Second Wind use to regain the use.")));
        f.put(10, List.of(
                f("Ever-Ready Shot", "When you roll Initiative, you regain one expended use of Arcane Shot.")));
        f.put(15, List.of(
                f("Indomitable Teleport", "When you use Indomitable and succeed on the saving throw, you can "
                        + "teleport up to 60 feet to an unoccupied space you can see.")));
        f.put(18, List.of(
                f("Masterful Shots", "When a creature you can see misses you with an attack roll, you can use your "
                        + "Reaction to move up to half your Speed away from it without provoking Opportunity "
                        + "Attacks, then make a ranged attack against it if it is within your weapon's range.")));
        return f;
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
