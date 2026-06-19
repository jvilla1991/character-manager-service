package com.moo.charactermanagerservice.progression;

import com.moo.charactermanagerservice.dto.FeatureGain;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class features granted automatically on level-up (D&D 5e 2024), keyed by class then by the
 * level at which they are gained. Server-authoritative and server-owned (names and descriptions
 * both live here, unlike feats whose descriptions are presentation).
 *
 * <p>Scope: the generic (non-subclass) class features each class gains at levels 2–20. Level-1
 * features are excluded (granted at character creation), as are Ability Score Improvements
 * (handled by the ASI/feat choice) and subclass features (the subclass catalog is the seam for
 * those). Descriptions are concise summaries — see the 2024 Player's Handbook for full text.
 */
public final class ClassFeatures {

    private ClassFeatures() {}

    private static FeatureGain f(String name, String desc) {
        return new FeatureGain(name, desc);
    }

    private static final Map<String, Map<Integer, List<FeatureGain>>> FEATURES = build();

    private static Map<String, Map<Integer, List<FeatureGain>>> build() {
        Map<String, Map<Integer, List<FeatureGain>>> m = new HashMap<>();
        m.put("barbarian", barbarian());
        m.put("bard", bard());
        m.put("cleric", cleric());
        m.put("druid", druid());
        m.put("fighter", fighter());
        m.put("monk", monk());
        m.put("paladin", paladin());
        m.put("ranger", ranger());
        m.put("rogue", rogue());
        m.put("sorcerer", sorcerer());
        m.put("warlock", warlock());
        m.put("wizard", wizard());
        return m;
    }

    private static Map<Integer, List<FeatureGain>> barbarian() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(2, List.of(
                f("Reckless Attack", "On your first attack of your turn you can attack with advantage; attack rolls against you have advantage until your next turn."),
                f("Danger Sense", "You have advantage on Dexterity saving throws against effects you can see, such as traps and spells.")));
        f.put(3, List.of(f("Primal Knowledge", "You gain an extra skill proficiency and can channel rage into certain ability checks.")));
        f.put(5, List.of(
                f("Extra Attack", "You can attack twice whenever you take the Attack action on your turn."),
                f("Fast Movement", "Your speed increases by 10 feet while you aren't wearing heavy armor.")));
        f.put(7, List.of(
                f("Feral Instinct", "You have advantage on Initiative rolls."),
                f("Instinctive Pounce", "When you enter your Rage, you can move up to half your speed.")));
        f.put(9, List.of(f("Brutal Strike", "When you use Reckless Attack you can forgo advantage to deal extra damage and a debilitating effect.")));
        f.put(11, List.of(f("Relentless Rage", "If you drop to 0 HP while raging and don't die, you can make a save to drop to 1 HP instead.")));
        f.put(15, List.of(f("Persistent Rage", "Your Rage ends early only if you choose to end it or fall unconscious.")));
        f.put(18, List.of(f("Indomitable Might", "If your total for a Strength check is less than your Strength score, use the score instead.")));
        f.put(20, List.of(f("Primal Champion", "Your Strength and Constitution scores increase by 4, to a maximum of 25.")));
        return f;
    }

    private static Map<Integer, List<FeatureGain>> bard() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(2, List.of(
                f("Expertise", "Choose two skill proficiencies; your proficiency bonus is doubled for checks with them."),
                f("Jack of All Trades", "You add half your proficiency bonus to ability checks that don't already include it.")));
        f.put(5, List.of(f("Font of Inspiration", "You regain all expended Bardic Inspiration on a Short or Long Rest.")));
        f.put(7, List.of(f("Countercharm", "As an action you can grant nearby allies advantage on saves against being frightened or charmed.")));
        f.put(9, List.of(f("Expertise", "Choose two more skill proficiencies to gain Expertise with.")));
        f.put(10, List.of(f("Magical Secrets", "You can learn spells from any class's spell list when you learn new bard spells.")));
        f.put(18, List.of(f("Superior Inspiration", "When you roll Initiative, you regain expended uses of Bardic Inspiration.")));
        f.put(20, List.of(f("Words of Creation", "You always have Power Word Heal and Power Word Kill prepared.")));
        return f;
    }

    private static Map<Integer, List<FeatureGain>> cleric() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(2, List.of(f("Channel Divinity", "You can channel divine energy to fuel magical effects, regaining uses on a rest.")));
        f.put(5, List.of(f("Sear Undead", "When you Turn Undead, you also deal radiant damage to affected creatures.")));
        f.put(7, List.of(f("Blessed Strikes", "Once per turn you add radiant damage to a weapon hit or a cantrip's damage.")));
        f.put(10, List.of(f("Divine Intervention", "You can call on your deity to intervene, casting a Cleric spell of level 5 or lower for free.")));
        f.put(14, List.of(f("Improved Blessed Strikes", "Your Blessed Strikes damage and effects improve.")));
        f.put(20, List.of(f("Greater Divine Intervention", "Your Divine Intervention improves and can replicate the Wish spell.")));
        return f;
    }

    private static Map<Integer, List<FeatureGain>> druid() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(2, List.of(
                f("Wild Shape", "You can use a Bonus Action to transform into a Beast form you've learned."),
                f("Wild Companion", "You can expend a Wild Shape use to cast Find Familiar as a spirit animal.")));
        f.put(5, List.of(f("Wild Resurgence", "Once per turn you can convert a spell slot into a Wild Shape use, or vice versa once per Long Rest.")));
        f.put(7, List.of(f("Elemental Fury", "Your cantrips deal extra damage, or your Wild Shape attacks deal extra elemental damage.")));
        f.put(15, List.of(f("Improved Elemental Fury", "The extra damage from Elemental Fury increases.")));
        f.put(18, List.of(f("Beast Spells", "You can cast spells while in a Wild Shape form.")));
        f.put(20, List.of(f("Archdruid", "Your Wild Shape uses are nearly unlimited and you can ignore some spell components.")));
        return f;
    }

    private static Map<Integer, List<FeatureGain>> fighter() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(2, List.of(
                f("Action Surge", "Once per Short or Long Rest, you can take one additional action on your turn."),
                f("Tactical Mind", "When you fail an ability check you can expend a use of Second Wind to add 1d10 to it.")));
        f.put(5, List.of(
                f("Extra Attack", "You can attack twice whenever you take the Attack action on your turn."),
                f("Tactical Shift", "When you activate Second Wind, you can move up to half your speed without provoking opportunity attacks.")));
        f.put(9, List.of(
                f("Indomitable", "You can reroll a failed saving throw, regaining uses on a rest."),
                f("Tactical Master", "When you attack with a weapon whose mastery you have, you can swap to Push, Sap, or Slow for that attack.")));
        f.put(11, List.of(f("Two Extra Attacks", "You can attack three times whenever you take the Attack action.")));
        f.put(13, List.of(f("Studied Attacks", "If you miss a creature, you have advantage on your next attack against it.")));
        f.put(20, List.of(f("Three Extra Attacks", "You can attack four times whenever you take the Attack action.")));
        return f;
    }

    private static Map<Integer, List<FeatureGain>> monk() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(2, List.of(
                f("Monk's Focus", "You gain Focus Points to fuel Flurry of Blows, Patient Defense, and Step of the Wind."),
                f("Unarmored Movement", "Your speed increases while you aren't wearing armor or wielding a shield.")));
        f.put(3, List.of(f("Deflect Attacks", "You can use a reaction to reduce damage from an attack and potentially redirect it.")));
        f.put(5, List.of(
                f("Extra Attack", "You can attack twice whenever you take the Attack action on your turn."),
                f("Stunning Strike", "When you hit with an attack you can spend a Focus Point to attempt to stun the target.")));
        f.put(6, List.of(f("Empowered Strikes", "Your Unarmed Strikes can deal Force damage instead of Bludgeoning.")));
        f.put(7, List.of(f("Evasion", "When you make a Dexterity save for half damage, you take none on a success and half on a failure.")));
        f.put(10, List.of(f("Self-Restoration", "At the end of your turn you can end one condition affecting you, and you ignore the frightened condition's downsides.")));
        f.put(13, List.of(f("Deflect Energy", "Your Deflect Attacks works against any damage type, not just physical.")));
        f.put(14, List.of(f("Disciplined Survivor", "You gain proficiency in all saving throws and can spend a Focus Point to reroll a failed save.")));
        f.put(15, List.of(f("Perfect Focus", "When you roll Initiative with fewer than 4 Focus Points, you regain up to 4.")));
        f.put(18, List.of(f("Superior Defense", "You can spend Focus Points to gain resistance to all damage except Force for a minute.")));
        f.put(20, List.of(f("Body and Mind", "Your Dexterity and Wisdom scores increase by 4, to a maximum of 25.")));
        return f;
    }

    private static Map<Integer, List<FeatureGain>> paladin() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(2, List.of(
                f("Fighting Style", "You adopt a particular style of fighting as your specialty."),
                f("Divine Smite", "When you hit with a melee weapon you can expend a spell slot to deal extra radiant damage.")));
        f.put(5, List.of(
                f("Extra Attack", "You can attack twice whenever you take the Attack action on your turn."),
                f("Faithful Steed", "You always have the Find Steed spell prepared and can cast it once per rest for free.")));
        f.put(6, List.of(f("Aura of Protection", "You and allies within 10 feet add your Charisma modifier to saving throws.")));
        f.put(9, List.of(f("Abjure Foes", "You can use Channel Divinity to frighten and hinder nearby enemies.")));
        f.put(10, List.of(f("Aura of Courage", "You and allies within your aura can't be frightened.")));
        f.put(11, List.of(f("Radiant Strikes", "Your melee weapon attacks deal an extra 1d8 radiant damage on a hit.")));
        f.put(14, List.of(f("Restoring Touch", "You can use Lay on Hands to also end conditions affecting a creature.")));
        f.put(18, List.of(f("Aura Expansion", "The range of your auras increases to 30 feet.")));
        return f;
    }

    private static Map<Integer, List<FeatureGain>> ranger() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(2, List.of(
                f("Deft Explorer", "You gain Expertise in a skill and learn additional languages."),
                f("Fighting Style", "You adopt a particular style of fighting as your specialty.")));
        f.put(5, List.of(f("Extra Attack", "You can attack twice whenever you take the Attack action on your turn.")));
        f.put(6, List.of(f("Roving", "Your speed increases and you gain a Climb and Swim speed.")));
        f.put(9, List.of(f("Expertise", "Choose two skill proficiencies to gain Expertise with.")));
        f.put(10, List.of(f("Tireless", "You can give yourself temporary HP and reduce your Exhaustion on a Short Rest.")));
        f.put(13, List.of(f("Relentless Hunter", "Taking damage can't break your concentration on Hunter's Mark.")));
        f.put(14, List.of(f("Nature's Veil", "You can use a Bonus Action to become Invisible until your next turn.")));
        f.put(17, List.of(f("Precise Hunter", "You have advantage on attacks against your Hunter's Mark target.")));
        f.put(18, List.of(f("Feral Senses", "You gain Blindsight out to 30 feet.")));
        f.put(20, List.of(f("Foe Slayer", "Your Hunter's Mark damage die increases to 1d10.")));
        return f;
    }

    private static Map<Integer, List<FeatureGain>> rogue() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(2, List.of(f("Cunning Action", "You can Dash, Disengage, or Hide as a Bonus Action.")));
        f.put(3, List.of(f("Steady Aim", "As a Bonus Action you can give yourself advantage on your next attack if you don't move that turn.")));
        f.put(5, List.of(
                f("Cunning Strike", "You can forgo Sneak Attack dice to add effects such as Poison, Trip, or Withdraw."),
                f("Uncanny Dodge", "When an attacker you can see hits you, you can use a reaction to halve the damage.")));
        f.put(7, List.of(
                f("Evasion", "When you make a Dexterity save for half damage, you take none on a success and half on a failure."),
                f("Reliable Talent", "When you make an ability check with a proficient skill, treat a d20 roll of 9 or lower as a 10.")));
        f.put(11, List.of(f("Improved Cunning Strike", "You can apply two Cunning Strike effects with the same Sneak Attack.")));
        f.put(14, List.of(f("Devious Strikes", "You gain powerful new Cunning Strike options: Daze, Knock Out, and Obscure.")));
        f.put(15, List.of(f("Slippery Mind", "You gain proficiency in Wisdom and Charisma saving throws.")));
        f.put(18, List.of(f("Elusive", "No attack roll has advantage against you while you aren't incapacitated.")));
        f.put(20, List.of(f("Stroke of Luck", "Once per Short or Long Rest you can turn a missed attack into a hit or a failed check into a 20.")));
        return f;
    }

    private static Map<Integer, List<FeatureGain>> sorcerer() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(2, List.of(
                f("Font of Magic", "You gain Sorcery Points you can convert to and from spell slots."),
                f("Metamagic", "You learn to twist your spells with options such as Twinned, Quickened, or Subtle Spell.")));
        f.put(5, List.of(f("Sorcerous Restoration", "You regain some expended Sorcery Points on a Short Rest.")));
        f.put(7, List.of(f("Sorcery Incarnate", "You can use two Metamagic options on a single spell.")));
        f.put(20, List.of(f("Arcane Apotheosis", "While Innate Sorcery is active you can use one Metamagic each turn without spending Sorcery Points.")));
        return f;
    }

    private static Map<Integer, List<FeatureGain>> warlock() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(2, List.of(f("Magical Cunning", "You can perform a 1-minute rite to regain expended Pact Magic slots, once per Long Rest.")));
        f.put(9, List.of(f("Contact Patron", "You can cast Contact Other Plane to speak with your patron, once per Long Rest for free.")));
        f.put(11, List.of(f("Mystic Arcanum (6th Level)", "You learn one 6th-level spell you can cast once per Long Rest without a slot.")));
        f.put(13, List.of(f("Mystic Arcanum (7th Level)", "You learn one 7th-level spell you can cast once per Long Rest without a slot.")));
        f.put(15, List.of(f("Mystic Arcanum (8th Level)", "You learn one 8th-level spell you can cast once per Long Rest without a slot.")));
        f.put(17, List.of(f("Mystic Arcanum (9th Level)", "You learn one 9th-level spell you can cast once per Long Rest without a slot.")));
        f.put(20, List.of(f("Eldritch Master", "You can spend 1 minute entreating your patron to regain all expended Pact Magic slots.")));
        return f;
    }

    private static Map<Integer, List<FeatureGain>> wizard() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(2, List.of(f("Scholar", "You gain Expertise in one Intelligence-based skill you're proficient in (Arcana, History, Investigation, Medicine, Nature, or Religion).")));
        f.put(5, List.of(f("Memorize Spell", "After a Short Rest you can swap one prepared wizard spell for another from your spellbook.")));
        f.put(18, List.of(f("Spell Mastery", "You can cast one 1st-level and one 2nd-level spell from your spellbook at will.")));
        f.put(20, List.of(f("Signature Spells", "You always have two chosen 3rd-level spells prepared and can cast each once per rest for free.")));
        return f;
    }

    /** Features gained by a class at a specific level; empty when none exist for that class/level. */
    public static List<FeatureGain> featuresAt(String clazz, int level) {
        if (clazz == null) return List.of();
        Map<Integer, List<FeatureGain>> byLevel = FEATURES.get(clazz.trim().toLowerCase());
        if (byLevel == null) return List.of();
        return byLevel.getOrDefault(level, List.of());
    }
}
