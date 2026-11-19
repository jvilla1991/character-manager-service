package com.moo.charactermanagerservice.progression;

import com.moo.charactermanagerservice.dto.FeatureGain;
import com.moo.charactermanagerservice.dto.FeatureProgressionEntry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Class features granted automatically on level-up (D&D 5e 2024), keyed by class then by the
 * level at which they are gained. Server-authoritative and server-owned (names and descriptions
 * both live here, unlike feats whose descriptions are presentation).
 *
 * <p>Scope: the generic class features each class gains at levels 2–20, plus — via
 * {@link #featuresAt(String, String, int)} — the features of every subclass a character can have: the
 * catalog subclasses (one {@code <Class>Subclasses} file per class, purely additive) and the World
 * of Warcraft module's hero and WoW-only subclasses (some of which also replace generic
 * Paladin/Ranger features), plus — via {@link #featuresAt(String, String, String, int)} — the
 * features a World of Warcraft class variant swaps in or adds (the WoW Shaman's Totems, the WoW
 * Evoker's Evoker Spells, a Beast Mastery hunter's Pet Specialization).
 * Level-1 features are excluded (granted at character creation), as are Ability Score
 * Improvements (handled by the ASI/feat choice). Descriptions are concise summaries — see the
 * 2024 Player's Handbook (or the relevant module) for full text.
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

    // ── Subclass features ──────────────────────────────────────────────────────
    // Every subclass a character can have. The catalog subclasses (2024 PHB plus Twilight Domain)
    // live in one <Class>Subclasses file per class; the World of Warcraft module's subclasses (the
    // Death Knight and Demon Hunter hero subclasses, the WoW Paladin's Oath of the Holy, the WoW
    // Rogue's Swashbuckler, the WoW Shaman's Path of the Elements and Path of the Spiritual
    // Guardian, the WoW Priest's Discipline and Shadow Domains, and the WoW Monk's Warrior of Mystic
    // Brews) are defined below. Each level entry may REPLACE generic class features by name
    // and/or ADD new ones. An unknown subclass gets the generic features unchanged.

    /** A subclass's changes at one level: generic feature names it removes, and features it adds. */
    private record SubclassGains(Set<String> replaces, List<FeatureGain> adds) {}

    private static SubclassGains adds(FeatureGain... gains) {
        return new SubclassGains(Set.of(), List.of(gains));
    }

    private static SubclassGains replaces(String replaced, FeatureGain... gains) {
        return new SubclassGains(Set.of(replaced), List.of(gains));
    }

    private static SubclassGains replaces(Set<String> replaced, FeatureGain... gains) {
        return new SubclassGains(replaced, List.of(gains));
    }

    /** class (lower-case) -> subclass (lower-case) -> level -> gains. */
    private static final Map<String, Map<String, Map<Integer, SubclassGains>>> SUBCLASS_FEATURES = buildSubclassFeatures();

    private static Map<String, Map<String, Map<Integer, SubclassGains>>> buildSubclassFeatures() {
        Map<String, Map<String, Map<Integer, SubclassGains>>> m = new HashMap<>();
        // The subclasses every member of a class can pick (ClassProgression's subclass catalog).
        // Purely additive: the generic class tables carry no subclass placeholders.
        addCatalogSubclasses(m, "barbarian", BarbarianSubclasses.all());
        addCatalogSubclasses(m, "bard", BardSubclasses.all());
        addCatalogSubclasses(m, "cleric", ClericSubclasses.all());
        addCatalogSubclasses(m, "druid", DruidSubclasses.all());
        addCatalogSubclasses(m, "fighter", FighterSubclasses.all());
        addCatalogSubclasses(m, "monk", MonkSubclasses.all());
        addCatalogSubclasses(m, "paladin", PaladinSubclasses.all());
        addCatalogSubclasses(m, "ranger", RangerSubclasses.all());
        addCatalogSubclasses(m, "rogue", RogueSubclasses.all());
        addCatalogSubclasses(m, "sorcerer", SorcererSubclasses.all());
        addCatalogSubclasses(m, "warlock", WarlockSubclasses.all());
        addCatalogSubclasses(m, "wizard", WizardSubclasses.all());
        // World of Warcraft module subclasses (hero and WoW-only); these may also REPLACE
        // generic features.
        addSubclass(m, "barbarian", ClassProgression.PATH_OF_THE_ELEMENTS, pathOfTheElements());
        addSubclass(m, "barbarian", ClassProgression.PATH_OF_THE_SPIRITUAL_GUARDIAN, pathOfTheSpiritualGuardian());
        addSubclass(m, "cleric", ClassProgression.DISCIPLINE_DOMAIN, disciplineDomain());
        addSubclass(m, "cleric", ClassProgression.SHADOW_DOMAIN, shadowDomain());
        addSubclass(m, "monk", ClassProgression.WARRIOR_OF_MYSTIC_BREWS, warriorOfMysticBrews());
        addSubclass(m, "paladin", ClassProgression.EBON_BLADE, ebonBlade());
        addSubclass(m, "paladin", ClassProgression.OATH_OF_THE_HOLY, oathOfTheHoly());
        addSubclass(m, "ranger", ClassProgression.DEMON_HUNTER, demonHunter());
        addSubclass(m, "rogue", ClassProgression.SWASHBUCKLER, swashbuckler());
        return m;
    }

    private static void addSubclass(Map<String, Map<String, Map<Integer, SubclassGains>>> m,
                                    String clazz, String subclass, Map<Integer, SubclassGains> byLevel) {
        m.computeIfAbsent(clazz, k -> new HashMap<>()).put(subclass.toLowerCase(), byLevel);
    }

    /** Registers a class's catalog subclasses (name -> level -> features), all purely additive. */
    private static void addCatalogSubclasses(Map<String, Map<String, Map<Integer, SubclassGains>>> m,
                                             String clazz, Map<String, Map<Integer, List<FeatureGain>>> subclasses) {
        subclasses.forEach((subclass, byLevel) -> {
            Map<Integer, SubclassGains> gains = new HashMap<>();
            byLevel.forEach((level, features) -> gains.put(level, new SubclassGains(Set.of(), features)));
            addSubclass(m, clazz, subclass, gains);
        });
    }

    /**
     * Path of the Elements, the WoW Shaman's Elemental path (barbarian; offered only to WoW
     * barbarians). Purely additive: the generic Barbarian table has no subclass placeholders.
     */
    private static Map<Integer, SubclassGains> pathOfTheElements() {
        Map<Integer, SubclassGains> f = new HashMap<>();
        f.put(3, adds(
                f("Raging Elements", "When you enter your Rage, pick one of the options below; while the Rage lasts you can "
                        + "take a Bonus Action to use that option again or switch to another. Save DC = 8 + your Constitution "
                        + "modifier + your Proficiency Bonus. Icefury: roll d8s equal to your Rage Damage bonus and make a melee "
                        + "or ranged spell attack (Constitution is your casting ability) against a creature within 30 feet, dealing "
                        + "that much Cold damage on a hit; the next Fire damage it takes before the end of your next turn gains "
                        + "an extra 1d10. Lava Burst: a creature within 30 feet makes a Dexterity save, taking Fire damage equal "
                        + "to d4s equal to your Rage Damage bonus on a failure or half as much on a success; on a failure, the "
                        + "next Lightning damage it takes before the end of your next turn gains an extra 1d8. Lightning Bolt: a "
                        + "creature within 30 feet makes a Dexterity save, taking Lightning damage equal to d6s equal to your Rage "
                        + "Damage bonus on a failure or half as much on a success; on a failure, the next Cold damage it takes "
                        + "before the end of your next turn gains an extra 1d6.")));
        f.put(6, adds(
                f("Echo of the Elements", "You know the Elementalism cantrip. You can also expend one use of your Rage to cast "
                        + "Daylight, Water Walk, or Wind Wall; Wisdom is your spellcasting ability for these spells.")));
        f.put(10, adds(
                f("Primordial Bond", "While your Rage is active, you have Resistance to Cold, Fire, and Lightning damage.")));
        f.put(14, adds(
                f("Elemental Overload", "Your Raging Elements options grow stronger. Icefury: hit or miss, the target subtracts "
                        + "half the number you rolled (round down) from its next damage roll made before the start of your next "
                        + "turn. Lava Burst: whether or not it succeeds on the save, the target and each enemy within 10 feet of "
                        + "it start burning. Lightning Bolt: whether or not it succeeds on the save, the lightning leaps to a "
                        + "second target of your choice within 30 feet of the first, which makes a Dexterity save, taking the "
                        + "full damage on a failure or half as much on a success.")));
        return f;
    }

    /**
     * Path of the Spiritual Guardian, the WoW Shaman's Enhancement path (barbarian; offered only to
     * WoW barbarians). Purely additive.
     */
    private static Map<Integer, SubclassGains> pathOfTheSpiritualGuardian() {
        Map<Integer, SubclassGains> f = new HashMap<>();
        f.put(3, adds(
                f("Spiritual Protectors", "While your Rage is active, whenever you hit a creature with a weapon or an Unarmed "
                        + "Strike, choose one effect. Distract: until the start of your next turn, the target has Disadvantage "
                        + "on attack rolls against anyone other than you or another Barbarian with this feature. Hinder: you "
                        + "also apply the Slow mastery property, in addition to the weapon's own mastery property. Strike: the "
                        + "hit deals an extra 1d4 Cold, Fire, or Lightning damage (your choice).")));
        f.put(6, adds(
                f("Spirit Shield", "While your Rage is active, when a creature you can see within 30 feet of you takes damage, "
                        + "you can take a Reaction to reduce that damage by d6s equal to your Rage Damage bonus.")));
        f.put(10, adds(
                f("Consult the Spirits", "You can cast Augury or Clairvoyance without a spell slot or Material components; when "
                        + "you cast Clairvoyance, an invisible guardian spirit appears as its sensor. Wisdom is your spellcasting "
                        + "ability for these spells. Once you use this feature, you can't do so again until you finish a Short "
                        + "or Long Rest.")));
        f.put(14, adds(
                f("Spirits' Fury", "Once per turn, when you make a melee weapon attack roll as part of the Attack action and "
                        + "the d20 shows 18, 19, or 20, you can make one additional attack with the same weapon as part of that "
                        + "action.")));
        return f;
    }

    /**
     * Discipline Domain, the WoW Priest's Discipline domain (cleric; offered only to WoW clerics).
     * Purely additive: the generic Cleric table has no subclass placeholders.
     */
    private static Map<Integer, SubclassGains> disciplineDomain() {
        Map<Integer, SubclassGains> f = new HashMap<>();
        f.put(3, adds(
                f("Discipline Spells", "You always have these spells prepared once you reach the listed Cleric level: "
                        + "3rd — Arcane Vigor, Guiding Bolt, Magic Weapon, Shield of Faith; "
                        + "5th — Prayer of Mending, Protection from Energy; "
                        + "7th — Guardian of Faith, Stoneskin; "
                        + "9th — Circle of Power, Destructive Wave."),
                f("Holy Bolt", "As a Bonus Action, spend one use of your Channel Divinity to make a ranged spell attack "
                        + "against a creature within 60 feet. On a hit it takes 2d10 + your Wisdom modifier Radiant damage, "
                        + "and a creature you can see within 60 feet regains 1d10 + your Wisdom modifier Hit Points."),
                f("Atonement", "When a level 1+ spell you cast restores Hit Points, every creature it heals is blessed "
                        + "until the end of your next turn. Whenever you deal damage with a spell cast using a spell slot, "
                        + "each blessed creature regains extra Hit Points equal to that slot's level.")));
        f.put(6, adds(
                f("Luminous Barrier", "As a Bonus Action, spend one use of your Channel Divinity: each creature currently "
                        + "blessed by your Atonement gains Temporary Hit Points equal to your Cleric level plus your Wisdom "
                        + "modifier, and has Resistance to all damage for as long as those Temporary Hit Points last.")));
        f.put(17, adds(
                f("Ultimate Penitence", "Your Holy Bolt gains two options. Ascending Step: before making the attack roll, "
                        + "you can teleport up to 30 feet to an unoccupied space you can see. Ascending Volley: as part of the "
                        + "same Bonus Action, expend a spell slot to fire additional bolts equal to the slot's level, aimed at "
                        + "one creature or split among several, each with its own attack roll.")));
        return f;
    }

    /**
     * Shadow Domain, the WoW Priest's Shadow domain (cleric; offered only to WoW clerics). Purely
     * additive.
     */
    private static Map<Integer, SubclassGains> shadowDomain() {
        Map<Integer, SubclassGains> f = new HashMap<>();
        f.put(3, adds(
                f("Shadow Spells", "You always have these spells prepared once you reach the listed Cleric level: "
                        + "3rd — Crown of Madness, Dissonant Whispers, Mind Spike, Shadow Word: Pain; "
                        + "5th — Summon Undead, Vampiric Touch; "
                        + "7th — Evard's Black Tentacles, Psychic Horror; "
                        + "9th — Dominate Person, Void Eruption."),
                f("Shadowform", "As a Bonus Action, spend one use of your Channel Divinity to take on a shadowy form for "
                        + "1 minute. Shadow Stealth: while in Dim Light or Darkness, you can take the Hide action as a Bonus "
                        + "Action. Void Empowerment: when a spell you cast with a spell slot deals Psychic or Necrotic damage, "
                        + "add 1d6 to one of its damage rolls. The form ends early if you dismiss it, are Incapacitated, or "
                        + "deal Radiant damage with a spell."),
                f("Devouring Plague", "When a creature you can see within 30 feet fails a saving throw, you can deal it "
                        + "1d10 Necrotic damage and regain Hit Points equal to the damage dealt. You can do this a number of "
                        + "times equal to your Wisdom modifier (minimum 1), regaining all uses on a Long Rest.")));
        f.put(6, adds(
                f("Dispersion", "When an attack roll hits you, you can take a Reaction to disperse: until the end of your "
                        + "next turn you have Resistance to all damage (including the triggering hit), can't take actions, "
                        + "Bonus Actions, or Reactions, and have a Fly Speed of 40 feet. When it ends, you can spend up to "
                        + "half your Hit Point Dice as if finishing a Short Rest. Once used, you can't do so again until you "
                        + "finish a Short or Long Rest.")));
        f.put(17, adds(
                f("Vampiric Embrace", "While your Shadowform is active, whenever you roll its extra 1d6, a creature of your "
                        + "choice within 30 feet regains Hit Points equal to the number rolled.")));
        return f;
    }

    /**
     * Warrior of Mystic Brews, the WoW Monk's Brewmaster tradition (offered only to WoW monks).
     * Purely additive: the generic Monk table has no subclass placeholders at these levels.
     */
    private static Map<Integer, SubclassGains> warriorOfMysticBrews() {
        Map<Integer, SubclassGains> f = new HashMap<>();
        f.put(3, adds(
                f("Bonus Proficiencies", "You gain proficiency in the Performance skill (or another Monk skill of your "
                        + "choice if you already have it) and with Brewer's Supplies."),
                f("Drunken Technique", "Whenever you spend a Focus Point on Patient Defense, your Speed increases by 10 "
                        + "feet until the end of the turn, and you can make one Unarmed Strike immediately after that "
                        + "Bonus Action.")));
        f.put(6, adds(
                f("Tipsy Sway", "Leap to Your Feet: standing up from Prone costs you only 5 feet of movement. Redirect "
                        + "Attack: when an enemy misses you with a melee attack roll, you can take a Reaction and spend 1 "
                        + "Focus Point to make that attack hit another creature of your choice within 5 feet of you."),
                f("Mystic Brew", "When you finish a Short or Long Rest while holding Brewer's Supplies, you can brew one "
                        + "magical beverage with one of these options. Cinnamon Dragon: as a Magic action, exhale a 30-foot "
                        + "Cone; each creature in it makes a Dexterity save (DC 8 + your Wisdom modifier + Proficiency "
                        + "Bonus), taking Fire damage equal to four rolls of your Martial Arts die and gaining the Poisoned "
                        + "condition until the end of its next turn on a failure, or half damage only on a success. "
                        + "Heavenly Spirit: you gain Resistance to two of Cold, Necrotic, Psychic, or Radiant damage. "
                        + "Refreshing Dip: you gain a pool of Martial Arts dice equal to your Wisdom modifier plus half "
                        + "your Monk level; when you regain Hit Points you can spend one to add it to the healing. Only "
                        + "you can drink the brew, which takes 1 minute; its effect lasts 1 hour or until you drink "
                        + "another, and you can spend 1 Focus Point to extend it to 8 hours. Any undrunk brew vanishes "
                        + "when you next finish a rest.")));
        f.put(11, adds(
                f("Wizened Brewer", "Your Mystic Brew gains new options. Blue Lightning: whenever you take a Reaction "
                        + "that isn't an Opportunity Attack or a spell, you can also make one Unarmed Strike. Drunkard's "
                        + "Luck: you gain Heroic Inspiration if you lack it, and when you make an ability check or saving "
                        + "throw without it you can give it to yourself. Stumbling Monk: after you take a Bonus Action "
                        + "other than Patient Defense, you can also use Patient Defense.")));
        f.put(17, adds(
                f("Master Brewer", "When you brew a Mystic Brew, you can combine two of its options in one beverage."),
                f("Intoxicated Frenzy", "Immediately after you use Patient Defense, you can move up to your Speed, making "
                        + "one Unarmed Strike against each creature you move within 5 feet of; a creature you hit must "
                        + "succeed on a Dexterity save or fall Prone. Each creature can be targeted only once per turn.")));
        return f;
    }

    /**
     * Swashbuckler, the WoW Rogue's own level-3 archetype (offered only to WoW rogues). Purely
     * additive: the generic Rogue table has no subclass placeholders at these levels.
     */
    private static Map<Integer, SubclassGains> swashbuckler() {
        Map<Integer, SubclassGains> f = new HashMap<>();
        f.put(3, adds(
                f("Fancy Footwork", "Once you make a melee attack roll against a creature on your turn, that creature can't make "
                        + "Opportunity Attacks against you for the rest of the turn."),
                f("Rakish Audacity", "You add your Charisma modifier to your Initiative rolls. You can also deal Sneak Attack damage "
                        + "without Advantage when the target is within 5 feet of you, no other creature is within 5 feet of you, "
                        + "and you don't have Disadvantage on the roll; all other Sneak Attack rules still apply.")));
        f.put(9, adds(
                f("Panache", "You gain two new Cunning Strike options. Goad (cost 1d6): the target makes a Wisdom save or, until the end "
                        + "of its next turn, has Disadvantage on attack rolls against anyone but you and can't make Opportunity Attacks "
                        + "against anyone but you. Awe (cost 3d6): each creature of your choice within 30 feet makes a Wisdom save or is "
                        + "Charmed until the end of your next turn.")));
        f.put(13, adds(
                f("Dashing Strikes", "You gain two more Cunning Strike options. Invigorate (cost 2d6): a creature you can see within "
                        + "30 feet adds 1d6 to its attack rolls and saving throws until the end of its next turn. Parrying Stance "
                        + "(cost 2d6): roll 1d6 and add the result to your AC until the start of your next turn.")));
        f.put(17, adds(
                f("Master Duelist", "Immediately after you deal Sneak Attack damage, you can make one more attack against the same "
                        + "target, provided you're within 5 feet of it and no other creature is within 5 feet of you.")));
        return f;
    }

    /**
     * Oath of the Ebon Blade (Death Knight, World of Warcraft tabletop module). Level 1's Death
     * Coil (which replaces Lay On Hands) is a creation-time feature and so is not in this table.
     */
    private static Map<Integer, SubclassGains> ebonBlade() {
        Map<Integer, SubclassGains> f = new HashMap<>();
        f.put(2, replaces("Divine Smite",
                f("Runic Smite", "Replaces Divine Smite. When you hit with a melee weapon, you can expend a spell slot to drive runic power into the blow for extra necrotic damage.")));
        f.put(3, adds(
                f("Oath of the Ebon Blade Spells", "Your oath grants a list of spells that are always prepared for you at the listed Paladin levels."),
                f("Runeforging", "You etch a rune of Blood, Frost, or Unholy into your weapon, choosing your Death Knight specialization. Each time you gain a level, you may swap it for another rune."),
                f("Runic Power", "Your runes fuel your Death Knight abilities, granting benefits tied to the rune you have forged.")));
        f.put(5, replaces("Faithful Steed",
                f("Faithful Steed", "You always have Find Steed prepared and can cast it without a slot once per Long Rest. Your steed may take an undead form, and it gains Ghostly Stride.")));
        f.put(7, adds(
                f("Aura of Deathly Endurance", "You and allies within your Aura of Protection have resistance to Cold, Necrotic, and Psychic damage.")));
        f.put(11, replaces("Radiant Strikes",
                f("Runic Strikes", "Replaces Radiant Strikes. Your hits with melee weapons and Unarmed Strikes deal an extra 1d8 Necrotic damage.")));
        f.put(14, replaces("Restoring Touch",
                f("Improved Death Coil", "Replaces Restoring Touch. Your Death Coil grows stronger and gains additional effects when you use it.")));
        f.put(15, adds(
                f("Runic Mastery", "Your command of runes deepens, strengthening the benefits of your forged rune.")));
        f.put(20, adds(
                f("Aspect of the Horsemen", "You can take on the aspect of the legendary Horsemen of the Ebon Blade, unleashing a burst of deathly power for a short time.")));
        return f;
    }

    /**
     * Oath of the Holy (World of Warcraft tabletop module), the WoW Paladin's own level-3 oath.
     * Purely additive: the generic Paladin table has no subclass placeholders at these levels.
     */
    private static Map<Integer, SubclassGains> oathOfTheHoly() {
        Map<Integer, SubclassGains> f = new HashMap<>();
        f.put(3, adds(
                f("Oath of the Holy Spells", "These spells are always prepared for you once you reach the listed Paladin level: "
                        + "3rd — Cure Wounds, Healing Word; 5th — Lesser Restoration, Prayer of Healing; "
                        + "9th — Dispel Magic, Mass Healing Word; 13th — Aura of Life, Death Ward; "
                        + "17th — Greater Restoration, Mass Cure Wounds."),
                f("Beacon of Light", "As a Magic action, expend one use of your Channel Divinity to mark an ally you can see within 60 feet for 1 minute. "
                        + "Whenever you restore Hit Points to another creature with a spell or Lay On Hands, the marked ally also regains half that amount.")));
        f.put(7, adds(
                f("Lightbringer's Aura", "When you restore Hit Points with a spell or Lay On Hands to a Bloodied creature within your Aura of Protection, "
                        + "it regains an extra 1d6 Hit Points.")));
        f.put(15, adds(
                f("Infusion of Light", "When a spell you cast deals Radiant damage, your Lay On Hands pool recovers Hit Points equal to the Radiant damage rolled, "
                        + "but the pool can't exceed five times your Paladin level.")));
        f.put(20, adds(
                f("Holy Deliverance", "As a Bonus Action, you radiate holy power for 10 minutes: each ally who starts its turn Bloodied within your aura "
                        + "regains Hit Points equal to your Charisma modifier plus your Proficiency Bonus, and the aura sheds Bright Light that counts as sunlight. "
                        + "Once used, you can't do so again until you finish a Long Rest unless you expend a level 5 spell slot to use it again.")));
        return f;
    }

    /**
     * Demon Hunter (World of Warcraft tabletop module), a Ranger hero subclass. Level 1's
     * Spellcasting change (an Arcane Focus may stand in for a Druidic Focus) is a creation-time
     * feature and so is not in this table.
     */
    private static Map<Integer, SubclassGains> demonHunter() {
        Map<Integer, SubclassGains> f = new HashMap<>();
        f.put(2, replaces(Set.of("Deft Explorer", "Fighting Style"),
                f("Deft Explorer", "You gain Expertise in a skill and learn additional languages. Eredun and Nathrezim, the tongues of demonkind, are the recommended picks for a Demon Hunter."),
                f("Fighting Style", "You adopt a particular style of fighting as your specialty. As a Demon Hunter you may take the Void Warrior option instead of a Fighting Style feat.")));
        f.put(3, adds(
                f("Demon Hunter Spells", "You always have certain spells prepared once you reach the listed Ranger levels: every spell in the All column, plus those in the column for your chosen specialization."),
                f("Soul Fragments", "When you finish a Long Rest you gain Soul Fragments equal to your Wisdom modifier (minimum 1), one more at Ranger level 13 and two more at 17; any left over vanish at your next Long Rest. Spend one for Double Jump (cast Feather Fall or Jump without a spell slot) or Soul Cleave (once per turn, trade an attack for a 10-foot emanation where each creature makes a Dexterity save against your ranger spell save DC, taking 1d12 + your Wisdom modifier psychic damage on a failure while you heal that amount). When an enemy within 30 feet dies, you can use your Reaction to regain one fragment."),
                f("Spectral Sight", "You gain Blindsight out to 10 feet.")));
        f.put(7, adds(
                f("Torment", "A creature marked by your Hunter's Mark has Disadvantage on attack rolls against anyone other than you. Once per turn when you hit a marked creature, choose Aura of Pain (it subtracts 1d4 from its next saving throw before the end of your next turn) or Chaos Strike (the hit deals an extra 1d8 Fire damage).")));
        f.put(11, adds(
                f("Illidari Knowledge", "Disrupt: spend 2 Soul Fragments to cast Counterspell as a Reaction, using your spellcasting ability. Warglaive Mastery: as a Bonus Action, spend 1 Soul Fragment to cast Throw Glaive without a spell slot.")));
        f.put(15, adds(
                f("Soul Shatter", "Your Soul Cleave now deals 2d12 + your Wisdom modifier psychic damage. When you roll Initiative and have no Soul Fragments, you regain one.")));
        f.put(18, replaces("Feral Senses",
                f("Improved Spectral Sight", "Replaces Feral Senses. You gain Truesight out to 30 feet.")));
        return f;
    }

    // ── Variant features ───────────────────────────────────────────────────────
    // Features a World of Warcraft class variant gains on top of (or in place of) the ordinary ones,
    // keyed by class. Unlike subclass features these depend on the SPECIALIZATION: they apply to
    // every member whose specialization is one of the class's WoW specializations (see
    // ClassProgression#isClassSpecialization), whatever its subclass. Each entry names the feature
    // it replaces: when that is a generic class feature at the level it is swapped in place (the WoW
    // Shaman's Totems for Primal Knowledge); otherwise the entry is ADDED among the class features
    // and a subclass feature of that name, if any, is dropped (the WoW Evoker's Evoker Spells for
    // Draconic Sorcery's Draconic Spells). A null name is a pure addition. An entry may also be
    // limited to some of the class's specializations (the WoW Hunter's Pet Specialization replaces
    // Roving only for Beast Mastery); an unrestricted entry applies to every specialization.

    /**
     * One variant feature: at {@code level}, {@code gain} replaces the feature named
     * {@code replaces} (generic or subclass), or is simply added when there is none. {@code onlyFor}
     * lists the specializations it applies to (exact names); empty means every WoW specialization of
     * the class.
     */
    private record VariantFeature(int level, String replaces, FeatureGain gain, Set<String> onlyFor) {
        VariantFeature(int level, String replaces, FeatureGain gain) {
            this(level, replaces, gain, Set.of());
        }

        boolean appliesTo(String specialization) {
            return onlyFor.isEmpty() || onlyFor.contains(specialization);
        }
    }

    /** class (lower-case) -> its WoW variant's features. */
    private static final Map<String, List<VariantFeature>> VARIANT_FEATURES = Map.of(
            "barbarian", List.of(new VariantFeature(3, "Primal Knowledge",
                    f("Totems", "Replaces Primal Knowledge. As a Bonus Action, you plant a Small totem (AC 10, 1 HP) in an "
                            + "unoccupied space within 10 feet of you. It lasts for 1 minute, until it is destroyed, or until "
                            + "you plant another. When you plant it, choose one effect; at the end of each of your turns the "
                            + "totem pulses that effect through a 30-foot Emanation. Earthbind: enemies in the Emanation have "
                            + "their Speed reduced by 10 feet until the end of your next turn. Primal Knowledge: allies in the "
                            + "Emanation can use Strength instead of the usual ability for Acrobatics, Intimidation, Perception, "
                            + "Stealth, or Survival checks until the end of your next turn; once you choose this effect, you "
                            + "can't choose it again until you finish a Long Rest unless you expend a use of your Rage. Tremor: "
                            + "allies in the Emanation have Immunity to the Charmed and Frightened conditions until the end of "
                            + "your next turn, and those conditions already on them are suppressed. Wind Rush: allies in the "
                            + "Emanation gain a 10-foot bonus to their Speed until the end of your next turn."))),
            // The Evoker is always a Draconic Sorcery sorcerer, so Evoker Spells always stands in
            // for that subclass's Draconic Spells.
            "sorcerer", List.of(new VariantFeature(3, "Draconic Spells",
                    f("Evoker Spells", "Replaces Draconic Spells. You always have certain spells prepared once you reach "
                            + "the listed Sorcerer levels on the Evoker Spells table: every spell in the All column, plus "
                            + "those in the column for your specialization."))),
            // Marksmanship and Survival hunters keep Roving.
            "ranger", List.of(new VariantFeature(6, "Roving",
                    f("Pet Specialization", "Replaces Roving. While your Primal Companion beast is within 60 feet of "
                            + "you, you gain a benefit based on its pet family's specialization. Cunning: you and your "
                            + "beast gain a 10-foot bonus to your Speed, and once per Long Rest the beast can take its "
                            + "action to cast Freedom of Movement, without components, on itself and on you if you are in "
                            + "range. Ferocity: when your beast is Bloodied and hits with its Beast's Strike, you and it "
                            + "each regain Hit Points equal to your Wisdom modifier, and once per Long Rest it can cast "
                            + "Haste on itself and you. Tenacity: your Hit Point maximum increases by 6, plus 1 more each "
                            + "time you gain a Ranger level, and your beast's Hit Point maximum increases by your Ranger "
                            + "level; once per Long Rest it can cast Stoneskin on itself and you."),
                    Set.of("Beast Mastery")))
    );

    /** The class's WoW variant features at a level — only when the specialization marks that variant. */
    private static List<VariantFeature> variantFeaturesAt(String clazz, String specialization, int level) {
        if (clazz == null || !ClassProgression.isClassSpecialization(clazz, specialization)) return List.of();
        return VARIANT_FEATURES.getOrDefault(clazz.trim().toLowerCase(), List.of()).stream()
                .filter(v -> v.level() == level && v.appliesTo(specialization))
                .toList();
    }

    /**
     * Features gained at a level by a class with the given subclass: the generic class features,
     * minus any the subclass replaces at that level, followed by the subclass's own features.
     * A null/unknown subclass (or one with no modeled features) yields the generic list. Ignores
     * WoW variant feature swaps — see {@link #featuresAt(String, String, String, int)}.
     */
    public static List<FeatureGain> featuresAt(String clazz, String subclass, int level) {
        return featuresAt(clazz, subclass, null, level);
    }

    /**
     * Features gained at a level by a class with the given subclass and specialization: the
     * generic class features with the class's WoW variant features applied (when the specialization
     * is one of the class's WoW specializations — e.g. a WoW Shaman's Totems in place of Primal
     * Knowledge, or a WoW Evoker's Evoker Spells), minus any the subclass replaces at that level,
     * followed by the subclass's own features (less any a variant feature replaces — e.g. the
     * Evoker's Draconic Spells).
     */
    public static List<FeatureGain> featuresAt(String clazz, String subclass, String specialization, int level) {
        return taggedFeaturesAt(clazz, subclass, specialization, level).stream()
                .map(e -> new FeatureGain(e.name(), e.desc()))
                .toList();
    }

    /** The features at one level, each tagged with whether the subclass (rather than the class) grants it. */
    private static List<FeatureProgressionEntry> taggedFeaturesAt(String clazz, String subclass,
                                                                  String specialization, int level) {
        // Class features: the generic list with the WoW variant's swaps applied in place, then the
        // variant features that replaced no generic feature.
        List<VariantFeature> variants = variantFeaturesAt(clazz, specialization, level);
        List<VariantFeature> unmatched = new ArrayList<>(variants);
        List<FeatureGain> classFeatures = new ArrayList<>();
        for (FeatureGain g : featuresAt(clazz, level)) {
            VariantFeature swap = variants.stream()
                    .filter(v -> g.name().equals(v.replaces())).findFirst().orElse(null);
            classFeatures.add(swap == null ? g : swap.gain());
            if (swap != null) unmatched.remove(swap);
        }
        Set<String> supersededSubclassFeatures = new HashSet<>();
        for (VariantFeature v : unmatched) {
            classFeatures.add(v.gain());
            if (v.replaces() != null) supersededSubclassFeatures.add(v.replaces());
        }

        SubclassGains gains = (clazz == null || subclass == null) ? null : SUBCLASS_FEATURES
                .getOrDefault(clazz.trim().toLowerCase(), Map.of())
                .getOrDefault(subclass.trim().toLowerCase(), Map.of())
                .get(level);
        List<FeatureProgressionEntry> result = new ArrayList<>();
        for (FeatureGain g : classFeatures) {
            if (gains == null || !gains.replaces().contains(g.name())) {
                result.add(new FeatureProgressionEntry(level, g.name(), g.desc(), false));
            }
        }
        if (gains != null) {
            for (FeatureGain g : gains.adds()) {
                if (supersededSubclassFeatures.contains(g.name())) continue;
                result.add(new FeatureProgressionEntry(level, g.name(), g.desc(), true));
            }
        }
        return result;
    }

    /** The highest character level; the progression runs from level 2 up to and including it. */
    private static final int MAX_LEVEL = 20;

    /**
     * Every feature a class + subclass + specialization gains from level 2 to level 20, in level
     * order — exactly what {@link #featuresAt(String, String, String, int)} would grant at each
     * level-up, flattened into one list so a character sheet can show what is still to come.
     * A null subclass yields the class's own features only; an unknown class yields an empty list.
     */
    public static List<FeatureProgressionEntry> progression(String clazz, String subclass, String specialization) {
        List<FeatureProgressionEntry> all = new ArrayList<>();
        for (int level = 2; level <= MAX_LEVEL; level++) {
            all.addAll(taggedFeaturesAt(clazz, subclass, specialization, level));
        }
        return List.copyOf(all);
    }
}
