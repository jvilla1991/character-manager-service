package com.moo.charactermanagerservice.progression;

import com.moo.charactermanagerservice.dto.FeatureGain;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Subclass features for the Warlock's 2024 Player's Handbook subclasses (plus the Vestige Patron
 * from Arcana Unleashed), keyed by the exact subclass name in ClassProgression's subclass
 * catalog, then by the Warlock level at which each feature is gained. Descriptions are concise
 * summaries, not rules text.
 */
final class WarlockSubclasses {

    private WarlockSubclasses() {}

    private static FeatureGain f(String name, String desc) {
        return new FeatureGain(name, desc);
    }

    /** subclass name (exact catalog spelling) -> level -> features gained at that level. */
    static Map<String, Map<Integer, List<FeatureGain>>> all() {
        Map<String, Map<Integer, List<FeatureGain>>> m = new LinkedHashMap<>();
        m.put("Archfey Patron", archfeyPatron());
        m.put("Celestial Patron", celestialPatron());
        m.put("Fiend Patron", fiendPatron());
        m.put("Great Old One Patron", greatOldOnePatron());
        m.put(SubclassChoices.VESTIGE_PATRON, vestigePatron());
        return m;
    }

    private static Map<Integer, List<FeatureGain>> archfeyPatron() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(3, List.of(
                f("Archfey Spells",
                        "You always have these spells prepared. 3rd — Calm Emotions, Faerie "
                                + "Fire, Misty Step, Phantasmal Force, Sleep; 5th — Blink, Plant "
                                + "Growth; 7th — Dominate Beast, Greater Invisibility; 9th — "
                                + "Dominate Person, Seeming."),
                f("Steps of the Fey",
                        "You can cast Misty Step without a spell slot a number of times equal to "
                                + "your Charisma modifier per Long Rest. Each casting adds "
                                + "Refreshing Step (1d10 Temporary Hit Points) or Taunting Step "
                                + "(Wisdom save or Disadvantage on attacks against others).")));
        f.put(6, List.of(
                f("Misty Escape",
                        "You can cast Misty Step as a Reaction when you take damage. Steps of the "
                                + "Fey gains two options: Disappearing Step (you're Invisible "
                                + "until your next turn starts) and Dreadful Step (Wisdom save "
                                + "or 2d10 Psychic damage within 5 feet).")));
        f.put(10, List.of(
                f("Beguiling Defenses",
                        "You're immune to the Charmed condition. As a Reaction when an attack "
                                + "hits you, halve the damage and force a Wisdom save; on a "
                                + "failure the attacker takes Psychic damage equal to what you "
                                + "took. Once per Long Rest, or spend a Pact Magic slot.")));
        f.put(14, List.of(
                f("Bewitching Magic",
                        "Right after you cast an Enchantment or Illusion spell using an action "
                                + "and a spell slot, you can cast Misty Step as part of the same "
                                + "action without spending a spell slot.")));
        return f;
    }

    private static Map<Integer, List<FeatureGain>> celestialPatron() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(3, List.of(
                f("Celestial Spells",
                        "You always have these spells prepared. 3rd — Aid, Cure Wounds, Guiding "
                                + "Bolt, Lesser Restoration, Light, Sacred Flame; 5th — "
                                + "Daylight, Revivify; 7th — Guardian of Faith, Wall of Fire; "
                                + "9th — Greater Restoration, Summon Celestial."),
                f("Healing Light",
                        "You have a pool of d6s equal to 1 + your Warlock level, restored on a "
                                + "Long Rest. As a Bonus Action, heal yourself or a creature "
                                + "within 60 feet by rolling up to your Charisma modifier in "
                                + "dice from the pool.")));
        f.put(6, List.of(
                f("Radiant Soul",
                        "You have Resistance to Radiant damage. Once per turn, when a spell you "
                                + "cast deals Radiant or Fire damage, you can add your Charisma "
                                + "modifier to that damage against one target.")));
        f.put(10, List.of(
                f("Celestial Resilience",
                        "When you use Magical Cunning or finish a Short or Long Rest, you gain "
                                + "Temporary Hit Points equal to your Warlock level + Charisma "
                                + "modifier, and up to five creatures you can see gain half "
                                + "your Warlock level + Charisma modifier.")));
        f.put(14, List.of(
                f("Searing Vengeance",
                        "When you or an ally within 60 feet would make a Death Saving Throw, it "
                                + "instead regains half its Hit Points. Creatures you choose "
                                + "within 30 feet of it take 2d8 + Charisma modifier Radiant "
                                + "damage and are briefly Blinded. Once per Long Rest.")));
        return f;
    }

    private static Map<Integer, List<FeatureGain>> fiendPatron() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(3, List.of(
                f("Dark One's Blessing",
                        "When you reduce an enemy to 0 Hit Points, or someone else does so to an "
                                + "enemy within 10 feet of you, you gain Temporary Hit Points "
                                + "equal to your Charisma modifier + Warlock level."),
                f("Fiend Spells",
                        "You always have these spells prepared. 3rd — Burning Hands, Command, "
                                + "Scorching Ray, Suggestion; 5th — Fireball, Stinking Cloud; "
                                + "7th — Fire Shield, Wall of Fire; 9th — Geas, Insect "
                                + "Plague.")));
        f.put(6, List.of(
                f("Dark One's Own Luck",
                        "When you make an ability check or saving throw, you can add 1d10 to the "
                                + "roll after seeing it but before its effects occur. Uses equal "
                                + "your Charisma modifier (minimum 1) per Long Rest, once per "
                                + "roll.")));
        f.put(10, List.of(
                f("Fiendish Resilience",
                        "When you finish a Short or Long Rest, choose one damage type other than "
                                + "Force. You have Resistance to that type until you choose a "
                                + "different one with this feature.")));
        f.put(14, List.of(
                f("Hurl Through Hell",
                        "Once per turn when you hit with an attack roll, the target must succeed "
                                + "on a Charisma save or vanish until the end of your next turn, "
                                + "Incapacitated and taking 8d10 Psychic damage unless it's a "
                                + "Fiend. Once per Long Rest, or spend a Pact Magic slot.")));
        return f;
    }

    private static Map<Integer, List<FeatureGain>> greatOldOnePatron() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(3, List.of(
                f("Awakened Mind",
                        "As a Bonus Action, link minds with a creature you can see within 30 "
                                + "feet. For minutes equal to your Warlock level you can speak "
                                + "telepathically while within miles equal to your Charisma "
                                + "modifier (minimum 1)."),
                f("Great Old One Spells",
                        "You always have these spells prepared. 3rd — Detect Thoughts, Dissonant "
                                + "Whispers, Phantasmal Force, Tasha's Hideous Laughter; 5th — "
                                + "Clairvoyance, Hunger of Hadar; 7th — Confusion, Summon "
                                + "Aberration; 9th — Modify Memory, Telekinesis."),
                f("Psychic Spells",
                        "When you cast a Warlock spell that deals damage, you can change its "
                                + "damage type to Psychic. You can cast Warlock Enchantment and "
                                + "Illusion spells without Verbal or Somatic components.")));
        f.put(6, List.of(
                f("Clairvoyant Combatant",
                        "When you bond with a creature using Awakened Mind, you can force a "
                                + "Wisdom save. On a failure, for the bond's duration it has "
                                + "Disadvantage on attacks against you and you have Advantage "
                                + "against it. Once per Short or Long Rest, or a Pact Magic "
                                + "slot.")));
        f.put(10, List.of(
                f("Eldritch Hex",
                        "You always have the Hex spell prepared. When you cast Hex and choose an "
                                + "ability, the target also has Disadvantage on saving throws "
                                + "using that ability for the duration."),
                f("Thought Shield",
                        "Your thoughts can't be read unless you allow it. You have Resistance to "
                                + "Psychic damage, and a creature that deals Psychic damage to "
                                + "you takes the same amount you take.")));
        f.put(14, List.of(
                f("Create Thrall",
                        "You can cast Summon Aberration without Concentration; it then lasts 1 "
                                + "minute and the Aberration gains Temporary Hit Points equal to "
                                + "your Warlock level + Charisma modifier. Once per turn, it "
                                + "deals Hex's bonus damage as Psychic to a Hexed target.")));
        return f;
    }

    /**
     * Vestige Patron (Arcana Unleashed). The companion's type and the domain behind Vestige Spells
     * are choices the player makes: see {@link SubclassChoices}. The companion is described in
     * words here; nothing creates it as a follower yet, and the domain's spells are not granted.
     */
    private static Map<Integer, List<FeatureGain>> vestigePatron() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(3, List.of(
                f("Vestige Companion",
                        "The vestige of a dying god appears as your companion: a Celestial, a "
                                + "Fiend or an Undead (your choice), Friendly to you and your allies. "
                                + "It acts on your turn and only Dodges unless you use a Bonus Action "
                                + "to command it, or give up one attack of your Attack action for its "
                                + "Vestige's Strike. At 0 Hit Points it vanishes until you perform a "
                                + "1-minute ceremony. A Magic action dismisses or recalls it, and "
                                + "after a Long Rest you can give it a new form and type."),
                f("Vestige Companion Stat Block",
                        "Small Celestial, Fiend or Undead. AC 13 + your Charisma modifier. Hit "
                                + "Points 4 + four times your Warlock level. Speed 5 ft., Fly 30 ft. "
                                + "(hover). STR 1, DEX 14, CON 10, INT 15, WIS 15, CHA 16; it adds "
                                + "your Proficiency Bonus to its ability checks and saving throws. "
                                + "Resistance by type: Radiant (Celestial), Fire (Fiend) or Necrotic "
                                + "(Undead). Immune to Charmed, Frightened and Prone. Vestige's "
                                + "Strike: a melee (5 ft.) or ranged (60 ft.) attack with your spell "
                                + "attack modifier for 1d6 + 3 + your Charisma modifier damage of "
                                + "the type it resists. Divine Power (Bonus Action, one use): "
                                + "Healing Touch (Celestial) restores 2d8 + your Charisma modifier "
                                + "Hit Points to another creature and ends Blinded, Deafened or "
                                + "Poisoned on it; Fiendish Swap (Fiend) makes you and the vestige "
                                + "trade places within 60 feet; Cursed Invocation (Undead) gives a "
                                + "creature within 30 feet Disadvantage on attack rolls against you "
                                + "and the vestige for 1 minute."),
                f("Vestige Spells",
                        "Choose a Cleric domain: Life, Light, Trickery or War. That domain's spells "
                                + "are Warlock spells for you, and you always have each of them "
                                + "prepared once your Warlock level reaches the Cleric level the "
                                + "domain lists it at.")));
        f.put(6, List.of(
                f("Vestige Power",
                        "Your vestige regains its Divine Power when you finish a Short or Long Rest "
                                + "or use Magical Cunning. While you are within 30 feet of it, you "
                                + "have Resistance to the damage types it resists.")));
        f.put(10, List.of(
                f("Vestige Recovery",
                        "When your vestige would drop to 0 Hit Points, you can use your Reaction "
                                + "and expend a Pact Magic slot to set it to its Hit Point maximum "
                                + "instead; it then teleports up to 30 feet to a space you can see. "
                                + "Once per Long Rest.")));
        f.put(14, List.of(
                f("Semblance of Life",
                        "As a Magic action while your vestige is within 90 feet, you turn it for 1 "
                                + "hour into the spirit of its type: a Celestial, Fiendish or Undead "
                                + "Spirit, as the matching Summon spell cast at half your Warlock "
                                + "level (maximum 9). It keeps its personality, Divine Power, Hit "
                                + "Points and Hit Dice and gains Temporary Hit Points equal to the "
                                + "spirit's Hit Points; the form ends early when those are gone. "
                                + "Once per Long Rest.")));
        return f;
    }
}
