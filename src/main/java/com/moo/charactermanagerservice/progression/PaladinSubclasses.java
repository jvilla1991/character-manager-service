package com.moo.charactermanagerservice.progression;

import com.moo.charactermanagerservice.dto.FeatureGain;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Subclass features for the Paladin's 2024 Player's Handbook subclasses, keyed by the exact
 * subclass name in ClassProgression's subclass catalog, then by the Paladin level at which
 * each feature is gained. Descriptions are concise summaries, not rules text.
 */
final class PaladinSubclasses {

    private PaladinSubclasses() {}

    private static FeatureGain f(String name, String desc) {
        return new FeatureGain(name, desc);
    }

    /** subclass name (exact catalog spelling) -> level -> features gained at that level. */
    static Map<String, Map<Integer, List<FeatureGain>>> all() {
        Map<String, Map<Integer, List<FeatureGain>>> m = new LinkedHashMap<>();
        m.put("Oath of Devotion", oathOfDevotion());
        m.put("Oath of Glory", oathOfGlory());
        m.put("Oath of the Ancients", oathOfTheAncients());
        m.put("Oath of Vengeance", oathOfVengeance());
        return m;
    }

    private static Map<Integer, List<FeatureGain>> oathOfDevotion() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(3, List.of(
                f("Oath of Devotion Spells", "You always have these spells prepared. "
                        + "3rd — Protection from Evil and Good, Shield of Faith; "
                        + "5th — Aid, Zone of Truth; "
                        + "9th — Beacon of Hope, Dispel Magic; "
                        + "13th — Freedom of Movement, Guardian of Faith; "
                        + "17th — Commune, Flame Strike."),
                f("Sacred Weapon", "When you take the Attack action, you can expend a use of Channel Divinity to "
                        + "imbue a Melee weapon you hold for 10 minutes: add your Charisma modifier (minimum +1) to "
                        + "its attack rolls, deal Radiant damage with it if you wish, and it sheds Bright Light "
                        + "for 20 feet.")));
        f.put(7, List.of(
                f("Aura of Devotion", "You and your allies have Immunity to the Charmed condition while in your "
                        + "Aura of Protection. A Charmed ally who enters the aura is unaffected by the condition "
                        + "while there.")));
        f.put(15, List.of(
                f("Smite of Protection", "Whenever you cast Divine Smite, you and your allies have Half Cover while "
                        + "in your Aura of Protection until the start of your next turn.")));
        f.put(20, List.of(
                f("Holy Nimbus", "As a Bonus Action, your Aura of Protection blazes with sunlight for 10 minutes: "
                        + "enemies starting their turn in it take Radiant damage equal to your Charisma modifier + "
                        + "Proficiency Bonus, and you have Advantage on saves forced by Fiends or Undead. Once per "
                        + "Long Rest, or expend a level 5 spell slot.")));
        return f;
    }

    private static Map<Integer, List<FeatureGain>> oathOfGlory() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(3, List.of(
                f("Oath of Glory Spells", "You always have these spells prepared. "
                        + "3rd — Guiding Bolt, Heroism; "
                        + "5th — Enhance Ability, Magic Weapon; "
                        + "9th — Haste, Protection from Energy; "
                        + "13th — Compulsion, Freedom of Movement; "
                        + "17th — Legend Lore, Yolande's Regal Presence."),
                f("Inspiring Smite", "Immediately after you cast Divine Smite, you can expend a use of Channel "
                        + "Divinity to distribute Temporary Hit Points totaling 2d8 + your Paladin level among "
                        + "creatures of your choice within 30 feet, which can include you."),
                f("Peerless Athlete", "As a Bonus Action, you can expend a use of Channel Divinity to gain "
                        + "Advantage on Strength (Athletics) and Dexterity (Acrobatics) checks for 1 hour, and your "
                        + "Long and High Jumps cover 10 extra feet.")));
        f.put(7, List.of(
                f("Aura of Alacrity", "Your Speed increases by 10 feet. Whenever an ally enters your Aura of "
                        + "Protection for the first time on a turn or starts their turn there, their Speed "
                        + "increases by 10 feet until the end of their next turn.")));
        f.put(15, List.of(
                f("Glorious Defense", "When you or a creature you can see within 10 feet is hit by an attack, you "
                        + "can use your Reaction to add your Charisma modifier (minimum +1) to the target's AC. If "
                        + "the attack misses, you can make a weapon attack against the attacker. Uses equal your "
                        + "Charisma modifier per Long Rest.")));
        f.put(20, List.of(
                f("Living Legend", "As a Bonus Action, for 10 minutes you have Advantage on Charisma checks, can "
                        + "turn one missed weapon attack per turn into a hit, and can use your Reaction to reroll a "
                        + "failed saving throw. Once per Long Rest, or expend a level 5 spell slot.")));
        return f;
    }

    private static Map<Integer, List<FeatureGain>> oathOfTheAncients() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(3, List.of(
                f("Oath of the Ancients Spells", "You always have these spells prepared. "
                        + "3rd — Ensnaring Strike, Speak with Animals; "
                        + "5th — Misty Step, Moonbeam; "
                        + "9th — Plant Growth, Protection from Energy; "
                        + "13th — Ice Storm, Stoneskin; "
                        + "17th — Commune with Nature, Tree Stride."),
                f("Nature's Wrath", "As a Magic action, you can expend a use of Channel Divinity so that each "
                        + "creature of your choice you can see within 15 feet must succeed on a Strength save or be "
                        + "Restrained for 1 minute. A creature repeats the save at the end of each of its turns.")));
        f.put(7, List.of(
                f("Aura of Warding", "You and your allies have Resistance to Necrotic, Psychic, and Radiant damage "
                        + "while in your Aura of Protection.")));
        f.put(15, List.of(
                f("Undying Sentinel", "When you are reduced to 0 Hit Points and not killed outright, you can drop to "
                        + "1 Hit Point instead and regain Hit Points equal to three times your Paladin level, once "
                        + "per Long Rest. You also can't be aged magically and stop visibly aging.")));
        f.put(20, List.of(
                f("Elder Champion", "As a Bonus Action, empower your Aura of Protection for 1 minute: enemies in it "
                        + "have Disadvantage on saves against your spells and Channel Divinity, you regain 10 Hit "
                        + "Points at the start of each turn, and you can cast action spells as a Bonus Action. Once "
                        + "per Long Rest, or expend a level 5 spell slot.")));
        return f;
    }

    private static Map<Integer, List<FeatureGain>> oathOfVengeance() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(3, List.of(
                f("Oath of Vengeance Spells", "You always have these spells prepared. "
                        + "3rd — Bane, Hunter's Mark; "
                        + "5th — Hold Person, Misty Step; "
                        + "9th — Haste, Protection from Energy; "
                        + "13th — Banishment, Dimension Door; "
                        + "17th — Hold Monster, Scrying."),
                f("Vow of Enmity", "When you take the Attack action, you can expend a use of Channel Divinity to "
                        + "gain Advantage on attack rolls against a creature you can see within 30 feet for 1 "
                        + "minute. If it drops to 0 Hit Points, you can transfer the vow to another creature within "
                        + "30 feet.")));
        f.put(7, List.of(
                f("Relentless Avenger", "When you hit a creature with an Opportunity Attack, you can reduce its "
                        + "Speed to 0 until the end of the current turn, then move up to half your Speed as part of "
                        + "the same Reaction without provoking Opportunity Attacks.")));
        f.put(15, List.of(
                f("Soul of Vengeance", "Immediately after a creature under your Vow of Enmity hits or misses with "
                        + "an attack roll, you can use your Reaction to make a melee attack against it if it is "
                        + "within range.")));
        f.put(20, List.of(
                f("Avenging Angel", "As a Bonus Action, for 10 minutes you gain a Fly Speed of 60 feet and can "
                        + "hover, and enemies starting their turn in your Aura of Protection must succeed on a "
                        + "Wisdom save or be Frightened for 1 minute or until damaged; attacks against them have "
                        + "Advantage. Once per Long Rest, or expend a level 5 spell slot.")));
        return f;
    }
}
