package com.moo.charactermanagerservice.progression;

import com.moo.charactermanagerservice.dto.FeatureGain;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Subclass features for the Monk's 2024 Player's Handbook subclasses, keyed by the exact
 * subclass name in ClassProgression's subclass catalog, then by the Monk level at which
 * each feature is gained. Descriptions are concise summaries, not rules text.
 */
final class MonkSubclasses {

    private MonkSubclasses() {}

    private static FeatureGain f(String name, String desc) {
        return new FeatureGain(name, desc);
    }

    /** subclass name (exact catalog spelling) -> level -> features gained at that level. */
    static Map<String, Map<Integer, List<FeatureGain>>> all() {
        Map<String, Map<Integer, List<FeatureGain>>> m = new LinkedHashMap<>();
        m.put("Warrior of Mercy", warriorOfMercy());
        m.put("Warrior of Shadow", warriorOfShadow());
        m.put("Warrior of the Elements", warriorOfTheElements());
        m.put("Warrior of the Open Hand", warriorOfTheOpenHand());
        return m;
    }

    private static Map<Integer, List<FeatureGain>> warriorOfMercy() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(3, List.of(
                f("Hand of Harm", "Once per turn when you hit a creature with an Unarmed Strike and deal damage, you "
                        + "can expend 1 Focus Point to deal extra Necrotic damage equal to one roll of your Martial "
                        + "Arts die plus your Wisdom modifier."),
                f("Hand of Healing", "As a Magic action, you can expend 1 Focus Point to touch a creature and "
                        + "restore Hit Points equal to a Martial Arts die roll plus your Wisdom modifier. With Flurry "
                        + "of Blows, you can replace one Unarmed Strike with this healing at no Focus Point cost."),
                f("Implements of Mercy", "You gain proficiency in the Insight and Medicine skills and with the "
                        + "Herbalism Kit.")));
        f.put(6, List.of(
                f("Physician's Touch", "Hand of Harm can also give the target the Poisoned condition until the end "
                        + "of your next turn. Hand of Healing can also end one of these conditions on the creature: "
                        + "Blinded, Deafened, Paralyzed, Poisoned, or Stunned.")));
        f.put(11, List.of(
                f("Flurry of Healing and Harm", "When you use Flurry of Blows, you can replace each Unarmed Strike "
                        + "with Hand of Healing and use Hand of Harm on one of its hits without spending Focus "
                        + "Points for either. You can do this a number of times equal to your Wisdom modifier "
                        + "(minimum once) per Long Rest.")));
        f.put(17, List.of(
                f("Hand of Ultimate Mercy", "As a Magic action, you can expend 5 Focus Points and touch a creature "
                        + "that died within the past 24 hours to return it to life with 4d10 + your Wisdom modifier "
                        + "Hit Points, ending several conditions on it. Once per Long Rest.")));
        return f;
    }

    private static Map<Integer, List<FeatureGain>> warriorOfShadow() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(3, List.of(
                f("Shadow Arts", "You can expend 1 Focus Point to cast Darkness without components, see within its "
                        + "area, and move it to a space within 60 feet at the start of each of your turns. You gain "
                        + "Darkvision out to 60 feet (or 60 more feet) and know Minor Illusion, using Wisdom.")));
        f.put(6, List.of(
                f("Shadow Step", "While entirely within Dim Light or Darkness, you can use a Bonus Action to "
                        + "teleport up to 60 feet to an unoccupied space you can see that is also in Dim Light or "
                        + "Darkness. You then have Advantage on your next melee attack before the end of the "
                        + "turn.")));
        f.put(11, List.of(
                f("Improved Shadow Step", "When you use Shadow Step, you can expend 1 Focus Point to ignore the need "
                        + "to start and end in Dim Light or Darkness, and you can make an Unarmed Strike "
                        + "immediately after you teleport as part of the same Bonus Action.")));
        f.put(17, List.of(
                f("Cloak of Shadows", "As a Magic action while in Dim Light or Darkness, you can expend 3 Focus "
                        + "Points to become Invisible for 1 minute (ends if you end a turn in Bright Light). You "
                        + "can also move through occupied spaces as Difficult Terrain and use Flurry of Blows "
                        + "without spending Focus Points.")));
        return f;
    }

    private static Map<Integer, List<FeatureGain>> warriorOfTheElements() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(3, List.of(
                f("Elemental Attunement", "At the start of your turn, you can expend 1 Focus Point to attune for 10 "
                        + "minutes: your Unarmed Strikes gain 10 feet of reach, can deal Acid, Cold, Fire, "
                        + "Lightning, or Thunder damage, and can force a Strength save to move the target 10 feet "
                        + "toward or away from you."),
                f("Manipulate Elements", "You know the Elementalism cantrip. Wisdom is your spellcasting ability "
                        + "for it.")));
        f.put(6, List.of(
                f("Elemental Burst", "As a Magic action, you can expend 2 Focus Points to burst a 20-foot-radius "
                        + "Sphere of Acid, Cold, Fire, Lightning, or Thunder energy at a point within 120 feet. "
                        + "Creatures in it make a Dexterity save, taking three Martial Arts dice of damage, or half "
                        + "on a success.")));
        f.put(11, List.of(
                f("Stride of the Elements", "While your Elemental Attunement is active, you have a Fly Speed and a "
                        + "Swim Speed equal to your Speed.")));
        f.put(17, List.of(
                f("Elemental Epitome", "While Elemental Attunement is active, you gain Resistance to one of its "
                        + "damage types, Step of the Wind adds 20 feet of Speed and harms creatures you pass "
                        + "within 5 feet of, and once per turn an Unarmed Strike deals an extra Martial Arts die "
                        + "of damage.")));
        return f;
    }

    private static Map<Integer, List<FeatureGain>> warriorOfTheOpenHand() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(3, List.of(
                f("Open Hand Technique", "Whenever you hit a creature with a Flurry of Blows attack, you can impose "
                        + "one effect: Addle (it can't make Opportunity Attacks until its next turn starts), Push "
                        + "(Strength save or pushed up to 15 feet), or Topple (Dexterity save or Prone).")));
        f.put(6, List.of(
                f("Wholeness of Body", "As a Bonus Action, you roll your Martial Arts die and regain Hit Points "
                        + "equal to the roll plus your Wisdom modifier (minimum 1). You can use this a number of "
                        + "times equal to your Wisdom modifier (minimum once) per Long Rest.")));
        f.put(11, List.of(
                f("Fleet Step", "When you take a Bonus Action other than Step of the Wind, you can also use Step of "
                        + "the Wind immediately after that Bonus Action.")));
        f.put(17, List.of(
                f("Quivering Palm", "When you hit a creature with an Unarmed Strike, you can expend 4 Focus Points "
                        + "to start vibrations in it lasting days equal to your Monk level. You can later take an "
                        + "action, or forgo one attack of the Attack action, to end them: Constitution save, 10d12 "
                        + "Force damage, or half on a success.")));
        return f;
    }
}
