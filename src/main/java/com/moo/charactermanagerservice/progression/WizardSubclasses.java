package com.moo.charactermanagerservice.progression;

import com.moo.charactermanagerservice.dto.FeatureGain;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Subclass features for the Wizard's 2024 Player's Handbook subclasses, keyed by the exact
 * subclass name in ClassProgression's subclass catalog, then by the Wizard level at which
 * each feature is gained. Descriptions are concise summaries, not rules text.
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
        m.put("Diviner", diviner());
        m.put("Evoker", evoker());
        m.put("Illusionist", illusionist());
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
}
