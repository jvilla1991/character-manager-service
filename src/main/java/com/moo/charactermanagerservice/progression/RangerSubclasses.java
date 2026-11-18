package com.moo.charactermanagerservice.progression;

import com.moo.charactermanagerservice.dto.FeatureGain;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Subclass features for the Ranger's 2024 Player's Handbook subclasses, keyed by the exact
 * subclass name in ClassProgression's subclass catalog, then by the Ranger level at which
 * each feature is gained. Descriptions are concise summaries, not rules text.
 */
final class RangerSubclasses {

    private RangerSubclasses() {}

    private static FeatureGain f(String name, String desc) {
        return new FeatureGain(name, desc);
    }

    /** subclass name (exact catalog spelling) -> level -> features gained at that level. */
    static Map<String, Map<Integer, List<FeatureGain>>> all() {
        Map<String, Map<Integer, List<FeatureGain>>> m = new LinkedHashMap<>();
        m.put("Beast Master", beastMaster());
        m.put("Fey Wanderer", feyWanderer());
        m.put("Gloom Stalker", gloomStalker());
        m.put("Hunter", hunter());
        return m;
    }

    private static Map<Integer, List<FeatureGain>> beastMaster() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(3, List.of(
                f("Primal Companion", "You summon a primal beast (Beast of the Land, Sea, or Sky) that acts on your "
                        + "turn. It takes the Dodge action unless you use a Bonus Action to command it, or give up "
                        + "one of your attacks to have it use Beast's Strike. If it died within the last hour, you "
                        + "can revive it by expending a spell slot.")));
        f.put(7, List.of(
                f("Exceptional Training", "When you use a Bonus Action to command your beast, it can also Dash, "
                        + "Disengage, Dodge, or Help as its own Bonus Action. Whenever it hits with an attack and "
                        + "deals damage, it can deal Force damage instead of its normal damage type.")));
        f.put(11, List.of(
                f("Bestial Fury", "When you command your beast to take the Beast's Strike action, it can use it "
                        + "twice. The first time each turn it hits a creature under your Hunter's Mark, it deals "
                        + "extra Force damage equal to that spell's bonus damage.")));
        f.put(15, List.of(
                f("Share Spells", "When you cast a spell targeting yourself, you can also affect your Primal "
                        + "Companion with the spell if the beast is within 30 feet of you.")));
        return f;
    }

    private static Map<Integer, List<FeatureGain>> feyWanderer() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(3, List.of(
                f("Dreadful Strikes", "When you hit a creature with a weapon, you can deal an extra 1d4 Psychic "
                        + "damage to it. A creature can take this extra damage only once per turn. The damage "
                        + "increases to 1d6 at Ranger level 11."),
                f("Fey Wanderer Spells", "You always have these spells prepared. "
                        + "3rd — Charm Person; "
                        + "5th — Misty Step; "
                        + "9th — Summon Fey; "
                        + "13th — Dimension Door; "
                        + "17th — Mislead. "
                        + "You also gain a minor otherworldly gift from the Feywild."),
                f("Otherworldly Glamour", "You add your Wisdom modifier (minimum +1) to your Charisma checks, and "
                        + "you gain proficiency in Deception, Performance, or Persuasion (your choice).")));
        f.put(7, List.of(
                f("Beguiling Twist", "You have Advantage on saves to avoid or end the Charmed or Frightened "
                        + "condition. When you or a creature you can see within 120 feet succeeds on such a save, "
                        + "you can use your Reaction to force another creature within 120 feet to make a Wisdom "
                        + "save or be Charmed or Frightened for 1 minute.")));
        f.put(11, List.of(
                f("Fey Reinforcements", "You can cast Summon Fey without a Material component, and once per Long "
                        + "Rest without a spell slot. When you cast it, you can remove its need for Concentration, "
                        + "in which case it lasts 1 minute.")));
        f.put(15, List.of(
                f("Misty Wanderer", "You can cast Misty Step without a spell slot a number of times equal to your "
                        + "Wisdom modifier (minimum once) per Long Rest. Whenever you cast it, you can bring along "
                        + "one willing creature within 5 feet of you, which arrives within 5 feet of your "
                        + "destination.")));
        return f;
    }

    private static Map<Integer, List<FeatureGain>> gloomStalker() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(3, List.of(
                f("Dread Ambusher", "You add your Wisdom modifier to Initiative rolls, and your Speed increases by "
                        + "10 feet on your first turn of each combat. Once per turn when you hit with a weapon, you "
                        + "can deal an extra 2d6 Psychic damage, a number of times equal to your Wisdom modifier "
                        + "(minimum once) per Long Rest."),
                f("Gloom Stalker Spells", "You always have these spells prepared. "
                        + "3rd — Disguise Self; "
                        + "5th — Rope Trick; "
                        + "9th — Fear; "
                        + "13th — Greater Invisibility; "
                        + "17th — Seeming."),
                f("Umbral Sight", "You gain Darkvision out to 60 feet, or 60 more feet if you already have it. "
                        + "While entirely in Darkness, you are Invisible to any creature that relies on Darkvision "
                        + "to see you there.")));
        f.put(7, List.of(
                f("Iron Mind", "You gain proficiency in Wisdom saving throws. If you already have it, you instead "
                        + "gain proficiency in Intelligence or Charisma saving throws (your choice).")));
        f.put(11, List.of(
                f("Stalker's Flurry", "Your Dreadful Strike damage becomes 2d8. When you use it, you can also either "
                        + "attack a different creature within 5 feet of the target with the same weapon, or force "
                        + "the target and creatures within 10 feet of it to make a Wisdom save or be Frightened "
                        + "until the start of your next turn.")));
        f.put(15, List.of(
                f("Shadowy Dodge", "When a creature makes an attack roll against you, you can use your Reaction to "
                        + "impose Disadvantage on it. Whether the attack hits or misses, you can then teleport up "
                        + "to 30 feet to an unoccupied space you can see.")));
        return f;
    }

    private static Map<Integer, List<FeatureGain>> hunter() {
        Map<Integer, List<FeatureGain>> f = new HashMap<>();
        f.put(3, List.of(
                f("Hunter's Lore", "While a creature is marked by your Hunter's Mark, you know whether it has any "
                        + "Immunities, Resistances, or Vulnerabilities, and what they are."),
                f("Hunter's Prey", "Choose Colossus Slayer (once per turn, a weapon hit deals an extra 1d8 damage "
                        + "to a creature missing Hit Points) or Horde Breaker (once per turn, make another attack "
                        + "with the same weapon against a different creature within 5 feet of the first target). "
                        + "You can swap on a Short or Long Rest.")));
        f.put(7, List.of(
                f("Defensive Tactics", "Choose Escape the Horde (Opportunity Attacks against you have Disadvantage) "
                        + "or Multiattack Defense (a creature that hits you with an attack roll has Disadvantage on "
                        + "its other attack rolls against you this turn). You can swap on a Short or Long Rest.")));
        f.put(11, List.of(
                f("Superior Hunter's Prey", "Once per turn when you deal damage to a creature marked by your "
                        + "Hunter's Mark, you can also deal that spell's extra damage to a different creature you "
                        + "can see within 30 feet of the first.")));
        f.put(15, List.of(
                f("Superior Hunter's Defense", "When you take damage, you can use your Reaction to gain Resistance "
                        + "to that damage and any other damage of the same type until the end of the current "
                        + "turn.")));
        return f;
    }
}
