package com.moo.charactermanagerservice.progression;

import com.moo.charactermanagerservice.dto.FeatureGain;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Subclass features for the Wizard's 2024 Player's Handbook subclasses (plus the Conjurer,
 * Enchanter, Necromancer and Transmuter from Arcana Unleashed), keyed by the exact subclass name
 * in ClassProgression's subclass catalog, then by the Wizard level at which each feature is
 * gained. Descriptions are concise summaries, not rules text.
 */
final class WizardSubclasses {

    private WizardSubclasses() {}

    private static FeatureGain f(String name, String desc) {
        return new FeatureGain(name, desc);
    }

    /** subclass name (exact catalog spelling) -> level -> features gained at that level. */
    static Map<String, Map<Integer, List<FeatureGain>>> all() {
        Map<String, Map<Integer, List<FeatureGain>>> m = new LinkedHashMap<>();
        m.put("Abjurer", abjurer());
        m.put("Conjurer", conjurer());
        m.put("Diviner", diviner());
        m.put("Enchanter", enchanter());
        m.put("Evoker", evoker());
        m.put("Illusionist", illusionist());
        m.put("Necromancer", necromancer());
        m.put("Transmuter", transmuter());
        return m;
    }

    private static Map<Integer, List<FeatureGain>> abjurer() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(3, List.of(
                f("Abjuration Savant",
                        "You add two Abjuration Wizard spells of level 2 or lower to your "
                                + "spellbook for free. Each time you gain access to a new level "
                                + "of Wizard spell slots, you add one more Abjuration spell for "
                                + "free."),
                f("Arcane Ward",
                        "Once per Long Rest, when you cast an Abjuration spell with a spell "
                                + "slot, you can raise a ward that absorbs damage you take. Its "
                                + "Hit Points equal twice your Wizard level + Intelligence "
                                + "modifier, and it regains twice the level of slots spent on "
                                + "Abjuration spells.")));
        f.put(6, List.of(
                f("Projected Ward",
                        "When a creature you can see within 30 feet takes damage, you can use "
                                + "your Reaction to have your Arcane Ward absorb that damage "
                                + "instead. Any damage beyond the ward's Hit Points still hits "
                                + "the creature.")));
        f.put(10, List.of(
                f("Spell Breaker",
                        "You always have Counterspell and Dispel Magic prepared. You can cast "
                                + "Dispel Magic as a Bonus Action and add your Proficiency Bonus "
                                + "to its ability check. A slot spent on either spell isn't "
                                + "expended if the spell fails to stop a spell.")));
        f.put(14, List.of(
                f("Spell Resistance",
                        "You have Advantage on saving throws against spells, and you have "
                                + "Resistance to the damage of spells.")));
        return f;
    }

    private static Map<Integer, List<FeatureGain>> diviner() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(3, List.of(
                f("Divination Savant",
                        "You add two Divination Wizard spells of level 2 or lower to your "
                                + "spellbook for free. Each time you gain access to a new level "
                                + "of Wizard spell slots, you add one more Divination spell for "
                                + "free."),
                f("Portent",
                        "After each Long Rest, roll two d20s and record them. You can replace "
                                + "any D20 Test made by you or a creature you can see with one "
                                + "of these rolls, chosen before the roll, once per turn. Each "
                                + "recorded roll can be used only once.")));
        f.put(6, List.of(
                f("Expert Divination",
                        "When you cast a Divination spell using a level 2+ spell slot, you "
                                + "regain one expended slot of a lower level than the one you "
                                + "used, up to level 5.")));
        f.put(10, List.of(
                f("The Third Eye",
                        "As a Bonus Action, gain one benefit until you start a Short or Long "
                                + "Rest: Darkvision out to 120 feet, the ability to read any "
                                + "language, or a casting of See Invisibility without a spell "
                                + "slot. Once per Short or Long Rest.")));
        f.put(14, List.of(
                f("Greater Portent",
                        "You roll three d20s for your Portent feature instead of two.")));
        return f;
    }

    private static Map<Integer, List<FeatureGain>> evoker() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(3, List.of(
                f("Evocation Savant",
                        "You add two Evocation Wizard spells of level 2 or lower to your "
                                + "spellbook for free. Each time you gain access to a new level "
                                + "of Wizard spell slots, you add one more Evocation spell for "
                                + "free."),
                f("Potent Cantrip",
                        "When you miss with a cantrip's attack roll or the target succeeds on "
                                + "its saving throw against your cantrip, the target still takes "
                                + "half the cantrip's damage but suffers no other effect.")));
        f.put(6, List.of(
                f("Sculpt Spells",
                        "When you cast an Evocation spell that affects other creatures you can "
                                + "see, choose a number of them equal to 1 + the spell's level. "
                                + "They automatically succeed on their saves and take no damage "
                                + "if a success would normally halve it.")));
        f.put(10, List.of(
                f("Empowered Evocation",
                        "Whenever you cast a Wizard spell from the Evocation school, you can add "
                                + "your Intelligence modifier to one damage roll of that spell.")));
        f.put(14, List.of(
                f("Overchannel",
                        "When you cast a damaging Wizard spell with a level 1-5 spell slot, you "
                                + "can deal maximum damage with it that turn. Each use after the "
                                + "first before a Long Rest deals you 2d12 Necrotic damage per "
                                + "slot level, rising by 1d12 per level each time.")));
        return f;
    }

    private static Map<Integer, List<FeatureGain>> illusionist() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(3, List.of(
                f("Illusion Savant",
                        "You add two Illusion Wizard spells of level 2 or lower to your "
                                + "spellbook for free. Each time you gain access to a new level "
                                + "of Wizard spell slots, you add one more Illusion spell for "
                                + "free."),
                f("Improved Illusions",
                        "You cast Illusion spells without Verbal components, and those with a "
                                + "range of 10+ feet reach 60 feet farther. You know Minor "
                                + "Illusion, can cast it as a Bonus Action, and can create both "
                                + "a sound and an image with one casting.")));
        f.put(6, List.of(
                f("Phantasmal Creatures",
                        "You always have Summon Beast and Summon Fey prepared and can cast them "
                                + "as Illusion spells. You can cast each Illusion version once "
                                + "per Long Rest without a spell slot, but the summoned creature "
                                + "then has half its Hit Points.")));
        f.put(10, List.of(
                f("Illusory Self",
                        "When a creature hits you with an attack roll, you can use your Reaction "
                                + "to make the attack miss by interposing an illusory duplicate. "
                                + "Once per Short or Long Rest, or spend a level 2+ spell slot "
                                + "to use it again.")));
        f.put(14, List.of(
                f("Illusory Reality",
                        "While an Illusion spell you cast with a spell slot is ongoing, you can "
                                + "use a Bonus Action to make one inanimate, nonmagical object "
                                + "in it real for 1 minute. The object can't deal damage or "
                                + "impose conditions.")));
        return f;
    }

    // -- Arcana Unleashed --------------------------------------------------------
    // The free casts and limited uses below are counted in ClassResources; the spells a feature
    // keeps prepared (Animate Dead, Find Familiar, Alter Self, Polymorph) are in ClassSpellGrants.

    private static Map<Integer, List<FeatureGain>> conjurer() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(3, List.of(
                f("Benign Transposition",
                        "As a Bonus Action, you teleport up to 30 feet to an unoccupied space you "
                                + "can see, or trade places with a willing Medium or smaller "
                                + "creature in that range. Uses equal your Intelligence modifier "
                                + "(minimum 1) per Long Rest."),
                f("Conjuration Savant",
                        "You add two Conjuration Wizard spells of level 2 or lower to your "
                                + "spellbook for free. Each time you gain access to a new level "
                                + "of Wizard spell slots, you add one more Conjuration spell for "
                                + "free.")));
        f.put(6, List.of(
                f("Distant Transposition",
                        "Benign Transposition reaches 60 feet, and you can expend a level 3+ spell "
                                + "slot (no action) to regain one use of it."),
                f("Durable Summons",
                        "A creature you summon or create with a Conjuration spell cast with a spell "
                                + "slot appears with Temporary Hit Points equal to twice your Wizard "
                                + "level. While it has them, it has Resistance to every damage type "
                                + "except Force, Necrotic, Psychic and Radiant.")));
        f.put(10, List.of(
                f("Focused Conjuration",
                        "Taking damage can't break your Concentration on Conjuration spells.")));
        f.put(14, List.of(
                f("Splintered Summons",
                        "When you cast a Conjuration spell with a spell slot to summon a spirit "
                                + "whose stat block is in the spell (such as Summon Aberration), you "
                                + "can summon two of that creature, each with half the Hit Points. "
                                + "Losing Concentration ends both. Once per Long Rest, or expend a "
                                + "level 5+ spell slot to regain the use.")));
        return f;
    }

    private static Map<Integer, List<FeatureGain>> enchanter() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(3, List.of(
                f("Enchanting Conversationalist",
                        "You gain proficiency in Deception, Intimidation or Persuasion (your "
                                + "choice) and add your Intelligence modifier (minimum +1) to "
                                + "ability checks with that skill."),
                f("Enchantment Savant",
                        "You add two Enchantment Wizard spells of level 2 or lower to your "
                                + "spellbook for free. Each time you gain access to a new level "
                                + "of Wizard spell slots, you add one more Enchantment spell for "
                                + "free."),
                f("Hypnotic Presence",
                        "As a Magic action, choose a creature within 10 feet that can see or hear "
                                + "you. It makes a Wisdom save against your spell save DC or is "
                                + "Charmed for 1 minute, Incapacitated with a Speed of 0. This ends "
                                + "if it takes damage, is more than 10 feet from you, or can no "
                                + "longer see or hear you. Uses equal your Intelligence modifier "
                                + "(minimum 1) per Long Rest.")));
        f.put(6, List.of(
                f("Split Enchantment",
                        "When you cast an Enchantment spell with a spell slot and the spell can "
                                + "target one more creature from a higher-level slot (such as Charm "
                                + "Person), you can treat it as cast one level higher. Uses equal "
                                + "your Intelligence modifier per Long Rest.")));
        f.put(10, List.of(
                f("Instinctive Charm",
                        "When a creature you can see within 30 feet hits you with an attack roll, "
                                + "you can use your Reaction to force a Wisdom save against your "
                                + "spell save DC. On a failure the attack misses you and, if "
                                + "another creature is in its range, hits one of your choice with "
                                + "the same roll. Once per Long Rest, or cast an Enchantment spell "
                                + "with a spell slot to regain the use.")));
        f.put(14, List.of(
                f("Alter Memories",
                        "When you cast an Enchantment spell with a spell slot that Charms, one "
                                + "target of your choice doesn't know it was Charmed. Once before "
                                + "the spell ends, a Magic action forces that creature to make an "
                                + "Intelligence save against your spell save DC; on a failure it "
                                + "forgets up to 1 + your Intelligence modifier hours (minimum 1), "
                                + "no more than the spell has lasted.")));
        return f;
    }

    private static Map<Integer, List<FeatureGain>> necromancer() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(3, List.of(
                f("Necromancy Savant",
                        "You add two Necromancy Wizard spells of level 2 or lower to your "
                                + "spellbook for free. Each time you gain access to a new level "
                                + "of Wizard spell slots, you add one more Necromancy spell for "
                                + "free."),
                f("Necromancy Spellbook",
                        "You have Resistance to Necrotic damage. Find Familiar is in your "
                                + "spellbook: the familiar can be a Skeleton or a Zombie, or an "
                                + "Undead of a normal form, and you can give up one attack of your "
                                + "Attack action to let it attack with its Reaction. When you cast "
                                + "a Necromancy spell with a spell slot, one Undead you can see "
                                + "within 60 feet regains Hit Points equal to the slot's level + "
                                + "your Wizard level.")));
        f.put(6, List.of(
                f("Grave Power",
                        "While you hold your spellbook, using Arcane Recovery lowers your "
                                + "Exhaustion level by 1, and damage from your Wizard spells and "
                                + "features ignores Resistance to Necrotic damage."),
                f("Undead Thralls",
                        "You always have Animate Dead prepared and can cast it once per Long Rest "
                                + "without a spell slot; whenever you cast it, you can treat it as "
                                + "one level higher. While you hold your spellbook, an Undead you "
                                + "create or summon with a Necromancy spell gains Hit Points equal "
                                + "to your Intelligence modifier + half your Wizard level, and an "
                                + "Undead you control within 60 feet deals extra Necrotic damage "
                                + "equal to your Intelligence modifier (minimum 1) on a hit.")));
        f.put(10, List.of(
                f("Harvest Undead",
                        "Right after damage leaves you Bloodied but above 0 Hit Points, you can use "
                                + "your Reaction to reduce an Undead you control and can see to 0 "
                                + "Hit Points and regain Hit Points equal to your Wizard level.")));
        f.put(14, List.of(
                f("Death's Master",
                        "While you hold your spellbook, you gain two options. Bolster Undead: as a "
                                + "Bonus Action, any number of Undead within 60 feet that you made "
                                + "with Necromancy spells gain Temporary Hit Points equal to your "
                                + "Wizard level, once per Long Rest. Extinguish Undead: when an "
                                + "Undead you can see drops to 0 Hit Points, it bursts in a 10-foot "
                                + "Emanation for one d6 of Necrotic damage per two of its unspent "
                                + "Hit Dice (Dexterity save for half; on a failure, no Reactions "
                                + "until its next turn). For an Undead you don't control this costs "
                                + "your Reaction and a level 5+ spell slot.")));
        return f;
    }

    private static Map<Integer, List<FeatureGain>> transmuter() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(3, List.of(
                f("Transmutation Savant",
                        "You add two Transmutation Wizard spells of level 2 or lower to your "
                                + "spellbook for free. Each time you gain access to a new level "
                                + "of Wizard spell slots, you add one more Transmutation spell for "
                                + "free."),
                f("Transmuter's Stone",
                        "After a Long Rest you can make a magic stone, usable as a Spellcasting "
                                + "Focus, that lasts until you make another. Whoever carries it has "
                                + "proficiency in Constitution saving throws and one benefit you "
                                + "choose: Darkvision 60 feet (or 60 feet more), Resistance to "
                                + "Acid, Cold, Fire, Lightning, Poison or Thunder damage, or 10 "
                                + "feet more Speed. You can change the benefit when you cast a "
                                + "Transmutation spell with a spell slot."),
                f("Wondrous Alteration",
                        "You always have Alter Self prepared and can cast it once per Long Rest "
                                + "without a spell slot. Each of its options gains a benefit for "
                                + "you. Aquatic Adaptation: Dash as a Bonus Action underwater. "
                                + "Change Appearance: Advantage on Charisma (Deception) checks. "
                                + "Natural Weapons: 2d6 damage, and Advantage on Constitution saves "
                                + "to keep Concentration.")));
        f.put(6, List.of(
                f("Empowered Transmutation",
                        "When you cast a Transmutation spell with a spell slot and it makes no "
                                + "attack roll and forces no saving throw (such as Fly or Magic "
                                + "Weapon), you can treat it as cast one level higher. Uses equal "
                                + "your Intelligence modifier (minimum 1) per Long Rest.")));
        f.put(10, List.of(
                f("Potent Stone",
                        "Your Transmuter's Stone holds two benefits (only Resistance can be chosen "
                                + "twice, for two damage types), and you can change either when you "
                                + "cast a Transmutation spell with a spell slot. Two more benefits "
                                + "can be chosen: Mighty Build (Advantage on Strength saves and one "
                                + "size larger for carrying capacity) and Tremorsense 30 feet."),
                f("Shape-Shifter",
                        "You always have Polymorph prepared and can cast it once per Long Rest "
                                + "without a spell slot. Once per Long Rest when you cast it on "
                                + "yourself, you keep your memories, speech, Intelligence, Wisdom "
                                + "and Charisma scores, proficiencies, class features and feats, "
                                + "and can cast Transmutation spells that need no costly or "
                                + "consumed Material component.")));
        f.put(14, List.of(
                f("Master Transmuter",
                        "As a Magic action while you carry your Transmuter's Stone, you use it up "
                                + "for one effect. Major Transformation: after 10 minutes of "
                                + "handling, a nonmagical object up to a 10-foot Cube becomes "
                                + "another of similar size and equal or lesser value. Panacea: a "
                                + "creature you touch regains half its Hit Point maximum and is rid "
                                + "of curses, magical contagions, Poisoned and Petrified. Restore "
                                + "Life: you cast Raise Dead without a spell slot or components. "
                                + "Restore Youth: a willing creature's Exhaustion drops to 0 and it "
                                + "looks 3d10 years younger. The stone crumbles unless you expend a "
                                + "level 7+ spell slot.")));
        return f;
    }
}
