package com.moo.charactermanagerservice.progression;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A Vestige Patron's companion (Arcana Unleashed, Warlock 3): who has one, and its stat block.
 *
 * <p>The companion is a follower the server owns. It exists exactly while its warlock is a
 * Vestige Patron of level 3 or higher with a Vestige type chosen ({@link #typeFor}), and every
 * number on it is worked out from the warlock: Armor Class from the warlock's Charisma, Hit
 * Points from its level, the attack from its spell attack modifier. So the block is rebuilt
 * whenever the warlock changes, and nothing here is ever taken from a request.
 *
 * <p>The block is the same sheet-shaped JSON object every follower carries (the client renders it
 * as a cut-down character sheet), laid out the way the client lays out a creature a spell makes:
 * the attack, the trait and the bonus action are features named by their {@code source}; facts
 * about the creature (size and type, the fly speed, the resistance, its Hit Dice) are "other"
 * features; the condition immunities are the sheet's own immunity chips. On top of that it
 * carries {@code "companion": "vestige"}, which is how this companion is told from any other.
 *
 * <p>The client builds the same block for its demo mode ({@code models/vestige-companion.ts});
 * the two are pinned to each other by the cases in {@code VestigeCompanionTest}.
 *
 * <p>Pure rules: no Spring, no entity. Feature text is a summary in our own words.
 */
public final class VestigeCompanion {

    private VestigeCompanion() {}

    /** The name a new companion is given; the player may rename it. */
    public static final String NAME = "Vestige";

    /** The stat block key that marks a server-owned companion, and this companion's value for it. */
    public static final String MARKER_KEY = "companion";
    public static final String MARKER = "vestige";

    /** The Warlock level the companion arrives at. */
    public static final int FROM_LEVEL = SubclassChoices.CHOICE_LEVEL;

    static final int BASE_ARMOR_CLASS = 13;
    static final int HIT_POINTS_PER_LEVEL = 4;
    static final int STRIKE_DAMAGE_BONUS = 3;

    /** Conditions the companion cannot have, as the sheet's condition chips name them. */
    public static final List<String> CONDITION_IMMUNITIES = List.of("charmed", "frightened", "prone");

    private static final String USE = " One use, counted as Divine Power on your sheet.";

    private static final String BIO = "The vestige of a dying god, bound to you by your pact. It acts on "
            + "your turn: it moves and uses its Reaction by itself, but only Dodges unless you command it "
            + "with a Bonus Action or give up one attack of your Attack action for its Vestige's Strike. "
            + "At 0 Hit Points it disappears until you perform a 1-minute ceremony, which brings it back "
            + "with all its Hit Points.";

    /** True for a Warlock whose subclass is the Vestige Patron, whatever its level or choices. */
    public static boolean isPatron(String clazz, String subclass) {
        return SubclassChoices.VESTIGE_PATRON.equals(SubclassChoices.subclassWithChoices(clazz, subclass));
    }

    /**
     * The type of the companion this character has (Celestial, Fiend or Undead), or null when it
     * has none: it is not a Vestige Patron warlock, it is below Warlock {@value #FROM_LEVEL}, or
     * its stored choices hold no valid Vestige type.
     *
     * @param subclassChoices the character's stored {@code subclass_choices} JSON
     */
    public static String typeFor(String clazz, String subclass, int level, String subclassChoices) {
        if (!isPatron(clazz, subclass) || level < FROM_LEVEL) return null;
        return SubclassChoices.cleaned(SubclassChoices.parse(subclassChoices), clazz, subclass, level)
                .vestigeType();
    }

    /** The damage a vestige of this type deals and resists. */
    static String damageType(String type) {
        return switch (type) {
            case "Celestial" -> "Radiant";
            case "Undead" -> "Necrotic";
            default -> "Fire";
        };
    }

    /** {@code +3} or {@code -1}: a modifier with its sign. */
    private static String signed(int value) {
        return value >= 0 ? "+" + value : String.valueOf(value);
    }

    /** {@code 1d6 + 7}, {@code 1d6 - 2}, or the bare dice when the bonus is zero. */
    private static String dice(String dice, int bonus) {
        if (bonus == 0) return dice;
        return dice + (bonus > 0 ? " + " : " - ") + Math.abs(bonus);
    }

    private static Map<String, Object> feature(String name, String source, String desc) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("name", name);
        entry.put("source", source);
        entry.put("desc", desc);
        return entry;
    }

    /** A fact about the creature, shown under Other Features. */
    private static Map<String, Object> fact(String name, String desc) {
        Map<String, Object> entry = feature(name, "Stat block", desc);
        entry.put("category", "other");
        return entry;
    }

    /** The one Divine Power option a vestige of this type has. */
    private static Map<String, Object> divinePower(String type, int charismaModifier) {
        return switch (type) {
            case "Celestial" -> feature("Divine Power: Healing Touch", "Bonus Action",
                    "It touches another creature, which regains " + dice("2d8", charismaModifier)
                            + " Hit Points and loses one condition: Blinded, Deafened or Poisoned." + USE);
            case "Undead" -> feature("Divine Power: Cursed Invocation", "Bonus Action",
                    "It curses a creature you can see within 30 ft. of it for 1 minute. The cursed creature "
                            + "has Disadvantage on attack rolls against you and the vestige." + USE);
            default -> feature("Divine Power: Fiendish Swap", "Bonus Action",
                    "While you and the vestige are within 60 ft. of each other, you teleport into each "
                            + "other's space." + USE);
        };
    }

    /**
     * The companion's stat block at full Hit Points, with no temporary Hit Points and no
     * conditions.
     *
     * @param type             Celestial, Fiend or Undead (anything else is built as a Fiend; the
     *                         callers only pass a value {@link #typeFor} returned)
     * @param warlockLevel     the warlock's level: its Hit Points and Hit Dice follow it
     * @param charisma         the warlock's Charisma score
     * @param proficiencyBonus the warlock's Proficiency Bonus
     * @param languages        the languages the warlock knows, which the vestige speaks
     */
    public static Map<String, Object> statBlock(String type, int warlockLevel, int charisma,
                                                int proficiencyBonus, List<String> languages) {
        int charismaModifier = ClassProgression.abilityModifier(charisma);
        int hitPoints = HIT_POINTS_PER_LEVEL + HIT_POINTS_PER_LEVEL * warlockLevel;
        String damage = damageType(type);

        Map<String, Object> hp = new LinkedHashMap<>();
        hp.put("max", hitPoints);
        hp.put("cur", hitPoints);
        hp.put("temp", 0);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("STR", 1);
        stats.put("DEX", 14);
        stats.put("CON", 10);
        stats.put("INT", 15);
        stats.put("WIS", 15);
        stats.put("CHA", 16);

        Map<String, Object> coins = new LinkedHashMap<>();
        coins.put("cp", 0);
        coins.put("sp", 0);
        coins.put("gp", 0);

        List<Map<String, Object>> features = new ArrayList<>();
        features.add(feature("Pact Bond", "Trait", "Adds " + signed(proficiencyBonus)
                + " (your Proficiency Bonus) to every ability check and saving throw it makes."));
        features.add(feature("Vestige's Strike", "Action", "Melee or ranged attack: "
                + signed(proficiencyBonus + charismaModifier) + " to hit, reach 5 ft. or range 60 ft. Hit: "
                + dice("1d6", STRIKE_DAMAGE_BONUS + charismaModifier) + " " + damage + " damage."));
        features.add(divinePower(type, charismaModifier));
        features.add(fact("Creature", "Small " + type + ", Neutral"));
        features.add(fact("Movement", "Fly 30 ft. (hover)"));
        features.add(fact("Resistance", damage + " damage."));
        features.add(fact("Hit Dice", warlockLevel + "d6"));

        Map<String, Object> block = new LinkedHashMap<>();
        block.put(MARKER_KEY, MARKER);
        block.put("clazz", "Vestige");
        block.put("race", type);
        block.put("level", warlockLevel);
        block.put("alignment", "Neutral");
        block.put("hp", hp);
        block.put("ac", BASE_ARMOR_CLASS + charismaModifier);
        block.put("init", 2);
        block.put("speed", 5);
        block.put("prof", proficiencyBonus);
        block.put("stats", stats);
        block.put("saves", new ArrayList<>());
        block.put("skills", new LinkedHashMap<>());
        block.put("conditions", new ArrayList<>());
        block.put("conditionImmunities", new ArrayList<>(CONDITION_IMMUNITIES));
        block.put("coins", coins);
        block.put("spellSlots", new LinkedHashMap<>());
        block.put("spells", new ArrayList<>());
        block.put("inventory", new ArrayList<>());
        block.put("features", features);
        block.put("languages", languages == null ? new ArrayList<>() : new ArrayList<>(languages));
        block.put("toolProfs", new ArrayList<>());
        block.put("bio", BIO);
        return block;
    }

    /** True when a follower's stat block is this companion's. */
    public static boolean isVestige(Map<String, Object> statBlock) {
        return statBlock != null && MARKER.equals(statBlock.get(MARKER_KEY));
    }
}
