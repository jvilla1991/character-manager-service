package com.moo.charactermanagerservice.progression;

import com.moo.charactermanagerservice.dto.FeatureGain;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Subclass features for the Barbarian's 2024 Player's Handbook subclasses, keyed by the exact
 * subclass name in ClassProgression's subclass catalog, then by the Barbarian level at which
 * each feature is gained. Descriptions are concise summaries, not rules text.
 */
final class BarbarianSubclasses {

    private BarbarianSubclasses() {}

    private static FeatureGain f(String name, String desc) {
        return new FeatureGain(name, desc);
    }

    /** subclass name (exact catalog spelling) -> level -> features gained at that level. */
    static Map<String, Map<Integer, List<FeatureGain>>> all() {
        Map<String, Map<Integer, List<FeatureGain>>> m = new LinkedHashMap<>();
        m.put("Path of the Berserker", berserker());
        m.put("Path of the Wild Heart", wildHeart());
        m.put("Path of the World Tree", worldTree());
        m.put("Path of the Zealot", zealot());
        return m;
    }

    private static Map<Integer, List<FeatureGain>> berserker() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(3, List.of(
                f("Frenzy",
                        "When you use Reckless Attack while raging, the first target you hit on your "
                                + "turn with a Strength-based attack takes extra damage: a number of "
                                + "d6s equal to your Rage Damage bonus.")));
        f.put(6, List.of(
                f("Mindless Rage",
                        "You are immune to the Charmed and Frightened conditions while your Rage is "
                                + "active. If you are Charmed or Frightened when you enter your Rage, "
                                + "the condition ends.")));
        f.put(10, List.of(
                f("Retaliation",
                        "When a creature within 5 feet of you damages you, you can use your Reaction "
                                + "to make one melee attack against it with a weapon or an Unarmed "
                                + "Strike.")));
        f.put(14, List.of(
                f("Intimidating Presence",
                        "As a Bonus Action, creatures of your choice in a 30-foot Emanation must pass "
                                + "a Wisdom save (DC 8 + Strength modifier + Proficiency Bonus) or be "
                                + "Frightened for 1 minute. Once per Long Rest, or spend a Rage use to "
                                + "do it again.")));
        return f;
    }

    private static Map<Integer, List<FeatureGain>> wildHeart() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(3, List.of(
                f("Animal Speaker",
                        "You can cast Beast Sense and Speak with Animals, but only as Rituals. Wisdom "
                                + "is your spellcasting ability for them."),
                f("Rage of the Wilds",
                        "Each time you Rage, choose Bear (Resistance to all damage but Force, "
                                + "Necrotic, Psychic, and Radiant), Eagle (Dash and Disengage as a "
                                + "Bonus Action, including the one that starts the Rage), or Wolf "
                                + "(allies have Advantage to hit enemies within 5 feet of you).")));
        f.put(6, List.of(
                f("Aspect of the Wilds",
                        "Choose Owl (Darkvision 60 feet, or 60 feet more if you already have it), "
                                + "Panther (Climb Speed equal to your Speed), or Salmon (Swim Speed "
                                + "equal to your Speed). You can change the choice after each Long "
                                + "Rest.")));
        f.put(10, List.of(
                f("Nature Speaker",
                        "You can cast Commune with Nature, but only as a Ritual. Wisdom is your "
                                + "spellcasting ability for it.")));
        f.put(14, List.of(
                f("Power of the Wilds",
                        "Each time you Rage, choose Falcon (Fly Speed equal to your Speed while you "
                                + "wear no armor), Lion (enemies within 5 feet of you have "
                                + "Disadvantage on attacks against targets other than you), or Ram "
                                + "(your melee hits can knock a Large or smaller creature Prone).")));
        return f;
    }

    private static Map<Integer, List<FeatureGain>> worldTree() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(3, List.of(
                f("Vitality of the Tree",
                        "When you Rage, you gain Temporary Hit Points equal to your Barbarian level. "
                                + "At the start of each of your turns while raging, give another "
                                + "creature within 10 feet Temporary Hit Points: roll d6s equal to "
                                + "your Rage Damage bonus.")));
        f.put(6, List.of(
                f("Branches of the Tree",
                        "While raging, when a creature starts its turn within 30 feet, you can use "
                                + "your Reaction to force a Strength save (DC 8 + Strength modifier + "
                                + "Proficiency Bonus). If it fails, it teleports next to you and you "
                                + "can drop its Speed to 0 that turn.")));
        f.put(10, List.of(
                f("Battering Roots",
                        "On your turn, your reach is 10 feet greater with Heavy or Versatile melee "
                                + "weapons. When you hit with one on your turn, you can apply the "
                                + "Push or Topple mastery in addition to another mastery property "
                                + "you use.")));
        f.put(14, List.of(
                f("Travel Along the Tree",
                        "When you activate your Rage, and as a Bonus Action while raging, you can "
                                + "teleport up to 60 feet. Once per Rage, the range can be 150 feet "
                                + "and you can bring up to six willing creatures from within 10 feet "
                                + "of you.")));
        return f;
    }

    private static Map<Integer, List<FeatureGain>> zealot() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(3, List.of(
                f("Divine Fury",
                        "On each of your turns while raging, the first creature you hit with a weapon "
                                + "or Unarmed Strike takes extra damage equal to 1d6 + half your "
                                + "Barbarian level (rounded down), Necrotic or Radiant as you choose."),
                f("Warrior of the Gods",
                        "You have a pool of four d12s. As a Bonus Action, spend any number of them to "
                                + "heal yourself by the total rolled. The pool refills on a Long Rest "
                                + "and grows to five dice at level 6, six at level 12, and seven at "
                                + "level 17.")));
        f.put(6, List.of(
                f("Fanatical Focus",
                        "Once per Rage, when you fail a saving throw, you can reroll it with a bonus "
                                + "equal to your Rage Damage bonus. You must use the new roll.")));
        f.put(10, List.of(
                f("Zealous Presence",
                        "As a Bonus Action, give up to ten other creatures of your choice within 60 "
                                + "feet Advantage on attack rolls and saving throws until the start "
                                + "of your next turn. Once per Long Rest, or spend a Rage use to do "
                                + "it again.")));
        f.put(14, List.of(
                f("Rage of the Gods",
                        "Once per Long Rest when you Rage, take a divine form for 1 minute: fly at "
                                + "your Speed (hover), resist Necrotic, Psychic, and Radiant damage, "
                                + "and spend a Reaction and a Rage use to leave a creature within 30 "
                                + "feet that would drop to 0 Hit Points at your Barbarian level "
                                + "instead.")));
        return f;
    }
}
