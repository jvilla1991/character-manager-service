package com.moo.charactermanagerservice.progression;

import com.moo.charactermanagerservice.dto.FeatureGain;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Subclass features for the Sorcerer's 2024 Player's Handbook subclasses, keyed by the exact
 * subclass name in ClassProgression's subclass catalog, then by the Sorcerer level at which
 * each feature is gained. Descriptions are concise summaries, not rules text.
 */
final class SorcererSubclasses {

    private SorcererSubclasses() {}

    private static FeatureGain f(String name, String desc) {
        return new FeatureGain(name, desc);
    }

    /** subclass name (exact catalog spelling) -> level -> features gained at that level. */
    static Map<String, Map<Integer, List<FeatureGain>>> all() {
        Map<String, Map<Integer, List<FeatureGain>>> m = new LinkedHashMap<>();
        m.put("Aberrant Sorcery", aberrantSorcery());
        m.put("Clockwork Sorcery", clockworkSorcery());
        m.put("Draconic Sorcery", draconicSorcery());
        m.put("Wild Magic Sorcery", wildMagicSorcery());
        return m;
    }

    private static Map<Integer, List<FeatureGain>> aberrantSorcery() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(3, List.of(
                f("Psionic Spells",
                        "You always have these spells prepared. 3rd — Arms of Hadar, Calm "
                                + "Emotions, Detect Thoughts, Dissonant Whispers, Mind Sliver; "
                                + "5th — Hunger of Hadar, Sending; 7th — Evard's Black "
                                + "Tentacles, Summon Aberration; 9th — Rary's Telepathic Bond, "
                                + "Telekinesis."),
                f("Telepathic Speech",
                        "As a Bonus Action, link minds with a creature you can see within 30 "
                                + "feet. For minutes equal to your Sorcerer level you can speak "
                                + "telepathically while within miles equal to your Charisma "
                                + "modifier, if you share a language.")));
        f.put(6, List.of(
                f("Psionic Sorcery",
                        "You can cast a level 1+ spell from your Psionic Spells by spending "
                                + "Sorcery Points equal to its level instead of a spell slot. "
                                + "Cast that way, it needs no Verbal or Somatic components, and "
                                + "no Material ones unless consumed or costly."),
                f("Psychic Defenses",
                        "You have Resistance to Psychic damage, and you have Advantage on saving "
                                + "throws to avoid or end the Charmed or Frightened condition.")));
        f.put(14, List.of(
                f("Revelation in Flesh",
                        "As a Bonus Action, spend 1 or more Sorcery Points to transform for 10 "
                                + "minutes. Per point, gain one benefit: swim and breathe "
                                + "underwater, fly and hover, see Invisible creatures within 60 "
                                + "feet, or squeeze through 1-inch gaps.")));
        f.put(18, List.of(
                f("Warping Implosion",
                        "As a Magic action, teleport up to 120 feet. Creatures within 30 feet of "
                                + "the space you left make a Strength save: 3d10 Force damage "
                                + "and pulled toward that space on a failure, half damage on a "
                                + "success. Once per Long Rest, or 5 Sorcery Points.")));
        return f;
    }

    private static Map<Integer, List<FeatureGain>> clockworkSorcery() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(3, List.of(
                f("Clockwork Spells",
                        "You always have these spells prepared. 3rd — Aid, Alarm, Lesser "
                                + "Restoration, Protection from Evil and Good; 5th — Dispel "
                                + "Magic, Protection from Energy; 7th — Freedom of Movement, "
                                + "Summon Construct; 9th — Greater Restoration, Wall of Force."),
                f("Restore Balance",
                        "As a Reaction when a creature you can see within 60 feet is about to "
                                + "roll a d20 with Advantage or Disadvantage, you can cancel both "
                                + "for that roll. Uses equal your Charisma modifier (minimum 1) "
                                + "per Long Rest.")));
        f.put(6, List.of(
                f("Bastion of Law",
                        "As a Magic action, spend 1 to 5 Sorcery Points to ward yourself or a "
                                + "creature within 30 feet with that many d8s. When it takes "
                                + "damage, it can roll any of those dice to reduce the damage. "
                                + "Lasts until a Long Rest or you use this again.")));
        f.put(14, List.of(
                f("Trance of Order",
                        "As a Bonus Action, for 1 minute attack rolls against you can't have "
                                + "Advantage, and you treat a roll of 9 or lower on any D20 Test "
                                + "as a 10. Once per Long Rest, or spend 5 Sorcery Points to use "
                                + "it again.")));
        f.put(18, List.of(
                f("Clockwork Cavalcade",
                        "As a Magic action, in a 30-foot Cube you restore up to 100 Hit Points "
                                + "divided among creatures you choose, repair damaged objects, "
                                + "and end spells of level 6 or lower on targets you choose. "
                                + "Once per Long Rest, or 7 Sorcery Points.")));
        return f;
    }

    private static Map<Integer, List<FeatureGain>> draconicSorcery() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(3, List.of(
                f("Draconic Resilience",
                        "Your Hit Point maximum increases by 3, and by 1 more each time you gain "
                                + "a Sorcerer level. While you aren't wearing armor, your base "
                                + "Armor Class is 10 + your Dexterity and Charisma modifiers."),
                f("Draconic Spells",
                        "You always have these spells prepared. 3rd — Alter Self, Chromatic Orb, "
                                + "Command, Dragon's Breath; 5th — Fear, Fly; 7th — Arcane Eye, "
                                + "Charm Monster; 9th — Legend Lore, Summon Dragon.")));
        f.put(6, List.of(
                f("Elemental Affinity",
                        "Choose Acid, Cold, Fire, Lightning, or Poison. You have Resistance to "
                                + "that damage type, and when you cast a spell that deals it, "
                                + "you add your Charisma modifier to one damage roll of that "
                                + "spell.")));
        f.put(14, List.of(
                f("Dragon Wings",
                        "As a Bonus Action, you sprout wings for 1 hour or until you dismiss "
                                + "them, gaining a Fly Speed of 60 feet. Once per Long Rest, or "
                                + "spend 3 Sorcery Points to use it again.")));
        f.put(18, List.of(
                f("Dragon Companion",
                        "You can cast Summon Dragon without a Material component, and once per "
                                + "Long Rest without a spell slot. When you cast it, you can "
                                + "drop its Concentration requirement; if you do, it lasts 1 "
                                + "minute.")));
        return f;
    }

    private static Map<Integer, List<FeatureGain>> wildMagicSorcery() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(3, List.of(
                f("Wild Magic Surge",
                        "Once per turn, right after you cast a Sorcerer spell with a spell slot, "
                                + "you can roll 1d20. On a 20, you roll on the Wild Magic Surge "
                                + "table for a random magical effect."),
                f("Tides of Chaos",
                        "You can give yourself Advantage on one D20 Test before rolling. You "
                                + "regain the use on a Long Rest, or when you next cast a "
                                + "Sorcerer spell with a spell slot, which automatically "
                                + "triggers a roll on the Wild Magic Surge table.")));
        f.put(6, List.of(
                f("Bend Luck",
                        "Right after another creature you can see rolls a d20 for a D20 Test, "
                                + "you can use your Reaction and spend 1 Sorcery Point to roll "
                                + "1d4 and apply it as a bonus or penalty to that roll.")));
        f.put(14, List.of(
                f("Controlled Chaos",
                        "Whenever you roll on the Wild Magic Surge table, you can roll twice and "
                                + "use either result.")));
        f.put(18, List.of(
                f("Tamed Surge",
                        "Right after you cast a Sorcerer spell with a spell slot, you can choose "
                                + "an effect from the Wild Magic Surge table instead of rolling, "
                                + "except the table's final row. Once per Long Rest.")));
        return f;
    }
}
