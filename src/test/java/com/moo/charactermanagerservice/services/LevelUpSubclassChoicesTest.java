package com.moo.charactermanagerservice.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moo.charactermanagerservice.dto.HpMode;
import com.moo.charactermanagerservice.models.PC;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A level-up of the Arcana Unleashed subclasses that let the player choose something: an Arcana
 * Domain cleric (a skill, two Wizard cantrips, four Wizard spells at 17), an Arcane Archer
 * (skills, Arcane Shot options, a cantrip) and a Vestige Patron (a type and a domain). And the
 * other side of it: a character without one of them levels up exactly as it did before.
 */
class LevelUpSubclassChoicesTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final LevelUpService service = new LevelUpService(JSON);

    private static PC pc(String clazz, int level) {
        PC pc = new PC();
        pc.setClazz(clazz);
        pc.setLevel((short) level);
        pc.setAbilityCon((short) 14);
        pc.setAbilityInt((short) 14);
        pc.setHpMax((short) 30);
        pc.setHpCurrent((short) 30);
        return pc;
    }

    private static PC pc(String clazz, String subclass, int level) {
        PC pc = pc(clazz, level);
        pc.setSubclass(subclass);
        return pc;
    }

    private static Map<String, Object> spell(int level, String name) {
        Map<String, Object> spell = new LinkedHashMap<>();
        spell.put("lvl", level);
        spell.put("name", name);
        spell.put("prepared", true);
        return spell;
    }

    /** A spell as the app sends a pick made for a subclass feature: marked with the feature's name. */
    private static Map<String, Object> pick(int level, String name, String feature) {
        Map<String, Object> spell = spell(level, name);
        spell.put("grantedBy", feature);
        return spell;
    }

    private static String held(int level, String name, String feature) {
        return "{\"lvl\":" + level + ",\"name\":\"" + name + "\",\"prepared\":true,"
                + (level > 0 ? "\"alwaysPrepared\":true," : "") + "\"grantedBy\":\"" + feature + "\"}";
    }

    private static List<Map<String, Object>> spellsOf(PC pc) {
        try {
            return pc.getSpells() == null ? List.of() : JSON.readValue(pc.getSpells(), new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, Object> saved(PC pc, String name) {
        return spellsOf(pc).stream().filter(s -> name.equals(s.get("name"))).findFirst().orElseThrow();
    }

    private static List<String> spellNames(PC pc) {
        return spellsOf(pc).stream().map(s -> String.valueOf(s.get("name"))).toList();
    }

    private static Map<String, Object> skillsOf(PC pc) {
        try {
            return pc.getSkills() == null ? Map.of() : JSON.readValue(pc.getSkills(), new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** The full level-up call, with only what these tests vary. */
    private PC levelUp(PC pc, String subclass, List<Map<String, Object>> newSpells,
                       Map<String, Object> subclassChoices, List<String> forgetSpells) {
        return service.applyLevelUp(pc, subclass, null, null, newSpells, HpMode.AVERAGE, null, null,
                subclassChoices, forgetSpells);
    }

    private static void assertRefused(Runnable levelUp, String message) {
        assertThatThrownBy(levelUp::run)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException rse = (ResponseStatusException) e;
                    assertThat(rse.getStatusCode().value()).isEqualTo(400);
                    assertThat(rse.getReason()).isEqualTo(message);
                });
    }

    // ── Arcana Domain at Cleric 3: a skill and two Wizard cantrips ───────────────

    private static final String STUDENT = "Student of Arcana";
    private static final String MASTERY = "Magical Mastery";

    private static List<Map<String, Object>> twoCantrips() {
        return List.of(pick(0, "Mage Hand", STUDENT), pick(0, "Fire Bolt", STUDENT));
    }

    @Test
    void arcanaDomain_atThree_takesTheSkillAndTheTwoCantrips() {
        PC cleric = pc("Cleric", 2);
        cleric.setSkills("{\"Medicine\":\"prof\",\"Religion\":\"expert\"}");
        cleric.setSpells("[{\"lvl\":0,\"name\":\"Sacred Flame\",\"prepared\":true}]");

        List<Map<String, Object>> newSpells = new ArrayList<>(twoCantrips());
        newSpells.add(spell(2, "Aid"));          // the one prepared spell a Cleric gains at 3
        newSpells.add(spell(1, "Detect Magic")); // a domain spell: always prepared, not a pick
        levelUp(cleric, "Arcana Domain", newSpells, Map.of("skills", List.of("Insight")), null);

        assertThat(cleric.getLevel()).isEqualTo((short) 3);
        assertThat(cleric.getSubclass()).isEqualTo("Arcana Domain");
        assertThat(cleric.getSubclassChoices()).isEqualTo("{\"skills\":[\"Insight\"]}");
        // The pick is marked proficient; what was there stays as it was.
        assertThat(skillsOf(cleric)).containsOnly(
                Map.entry("Medicine", "prof"), Map.entry("Religion", "expert"), Map.entry("Insight", "prof"));

        // The cantrips sit outside the Cleric's own cantrip count (which does not rise at 3).
        assertThat(saved(cleric, "Mage Hand")).containsEntry("grantedBy", STUDENT).containsEntry("prepared", true)
                .doesNotContainKey("alwaysPrepared");
        assertThat(saved(cleric, "Fire Bolt")).containsEntry("grantedBy", STUDENT);
        assertThat(saved(cleric, "Aid")).doesNotContainKey("grantedBy");
        assertThat(saved(cleric, "Detect Magic")).containsEntry("grantedBy", "Arcana Domain Spells")
                .containsEntry("alwaysPrepared", true);
        assertThat(spellNames(cleric)).containsExactly("Sacred Flame", "Mage Hand", "Fire Bolt", "Aid", "Detect Magic");
        assertThat(cleric.getFeatures()).contains("\"Student of Arcana\"", "\"Modify Magic\"", "\"Arcana Domain Spells\"");
    }

    @Test
    void arcanaDomain_atThree_refusesToGoThroughWithoutTheSkill() {
        assertRefused(() -> levelUp(pc("Cleric", 2), "Arcana Domain", twoCantrips(), null, null),
                "Arcana Domain requires exactly 1 skill choice");
        assertRefused(() -> levelUp(pc("Cleric", 2), "Arcana Domain", twoCantrips(), Map.of(), null),
                "Arcana Domain requires exactly 1 skill choice");
        assertRefused(() -> levelUp(pc("Cleric", 2), "Arcana Domain", twoCantrips(),
                Map.of("skills", List.of("Arcana", "History")), null), "Arcana Domain requires exactly 1 skill choice");
        assertRefused(() -> levelUp(pc("Cleric", 2), "Arcana Domain", twoCantrips(),
                Map.of("skills", List.of("Athletics")), null), "'Athletics' is not a skill Arcana Domain can choose");

        PC knowsArcana = pc("Cleric", 2);
        knowsArcana.setSkills("{\"Arcana\":\"expert\"}");
        assertRefused(() -> levelUp(knowsArcana, "Arcana Domain", twoCantrips(),
                Map.of("skills", List.of("Arcana")), null), "'Arcana' is already a skill proficiency");
    }

    @Test
    void arcanaDomain_atThree_refusesToGoThroughWithoutExactlyTwoCantrips() {
        Map<String, Object> skill = Map.of("skills", List.of("Arcana"));
        String message = "Student of Arcana requires exactly two Wizard cantrips";
        assertRefused(() -> levelUp(pc("Cleric", 2), "Arcana Domain", null, skill, null), message);
        assertRefused(() -> levelUp(pc("Cleric", 2), "Arcana Domain", List.of(), skill, null), message);
        assertRefused(() -> levelUp(pc("Cleric", 2), "Arcana Domain",
                List.of(pick(0, "Mage Hand", STUDENT)), skill, null), message);
        assertRefused(() -> levelUp(pc("Cleric", 2), "Arcana Domain",
                List.of(pick(0, "Mage Hand", STUDENT), pick(0, "Fire Bolt", STUDENT), pick(0, "Light", STUDENT)),
                skill, null), message);
        // A leveled spell is not a cantrip, and the same cantrip twice is one cantrip.
        assertRefused(() -> levelUp(pc("Cleric", 2), "Arcana Domain",
                List.of(pick(0, "Mage Hand", STUDENT), pick(1, "Shield", STUDENT)), skill, null), message);
        assertRefused(() -> levelUp(pc("Cleric", 2), "Arcana Domain",
                List.of(pick(0, "Mage Hand", STUDENT), pick(0, "mage hand", STUDENT)), skill, null), message);
        // Unmarked cantrips are the Cleric's own picks, and a Cleric learns none at level 3.
        assertRefused(() -> levelUp(pc("Cleric", 2), "Arcana Domain",
                List.of(spell(0, "Mage Hand"), spell(0, "Fire Bolt")), skill, null), message);
    }

    @Test
    void arcanaDomain_aCantripTheCharacterAlreadyKnows_isAWastedPick() {
        PC cleric = pc("Cleric", 2);
        cleric.setSpells("[{\"lvl\":0,\"name\":\"Light\",\"prepared\":true}]");
        assertRefused(() -> levelUp(cleric, "Arcana Domain",
                List.of(pick(0, "Light", STUDENT), pick(0, "Fire Bolt", STUDENT)),
                Map.of("skills", List.of("Arcana")), null), "'Light' is already known");
    }

    @Test
    void arcanaDomain_afterThree_needsNothingMore_andKeepsItsChoices() {
        PC cleric = pc("Cleric", "Arcana Domain", 4);
        cleric.setSubclassChoices("{\"skills\":[\"Insight\"]}");
        cleric.setSkills("{\"Insight\":\"prof\"}");
        cleric.setSpells("[" + held(0, "Mage Hand", STUDENT) + "," + held(0, "Fire Bolt", STUDENT) + "]");

        levelUp(cleric, null, null, null, null); // 4 -> 5

        assertThat(cleric.getLevel()).isEqualTo((short) 5);
        assertThat(cleric.getSubclassChoices()).isEqualTo("{\"skills\":[\"Insight\"]}");
        assertThat(cleric.getSkills()).isEqualTo("{\"Insight\":\"prof\"}");
        assertThat(spellNames(cleric)).containsExactly("Mage Hand", "Fire Bolt");

        // The skill is settled: a later level-up cannot change it, and a third cantrip is one too many.
        assertRefused(() -> levelUp(cleric, null, null, Map.of("skills", List.of("Religion")), null),
                "The Arcana Domain skill choice has already been made");
        assertRefused(() -> levelUp(cleric, null, List.of(pick(0, "Light", STUDENT)), null, null),
                "Student of Arcana requires exactly two Wizard cantrips");
    }

    @Test
    void arcanaDomain_madeByHandAtAHigherLevel_catchesUpAtItsNextLevelUp() {
        // The DM changed the subclass on the sheet: nothing chosen, nothing picked.
        PC cleric = pc("Cleric", "Arcana Domain", 8);
        assertRefused(() -> levelUp(cleric, null, null, null, null), "Arcana Domain requires exactly 1 skill choice");
        assertRefused(() -> levelUp(cleric, null, null, Map.of("skills", List.of("History")), null),
                "Student of Arcana requires exactly two Wizard cantrips");

        PC caughtUp = pc("Cleric", "Arcana Domain", 8);
        levelUp(caughtUp, null, twoCantrips(), Map.of("skills", List.of("History")), null);
        assertThat(caughtUp.getSubclassChoices()).isEqualTo("{\"skills\":[\"History\"]}");
        assertThat(skillsOf(caughtUp)).containsEntry("History", "prof");
        assertThat(spellNames(caughtUp)).containsExactly("Mage Hand", "Fire Bolt");
    }

    // ── Replacing a pick when a Cleric level is gained ───────────────────────────

    private static PC clericWithCantrips(int level) {
        PC cleric = pc("Cleric", "Arcana Domain", level);
        cleric.setSubclassChoices("{\"skills\":[\"Insight\"]}");
        cleric.setSkills("{\"Insight\":\"prof\"}");
        cleric.setSpells("[{\"lvl\":0,\"name\":\"Sacred Flame\",\"prepared\":true},"
                + held(0, "Mage Hand", STUDENT) + "," + held(0, "Fire Bolt", STUDENT) + "]");
        return cleric;
    }

    @Test
    void studentOfArcana_oneCantripMayBeReplacedPerLevel() {
        PC cleric = clericWithCantrips(5); // 5 -> 6
        levelUp(cleric, null, List.of(pick(0, "Light", STUDENT)), null, List.of("mage hand"));

        assertThat(spellNames(cleric)).containsExactly("Sacred Flame", "Fire Bolt", "Light");
        assertThat(saved(cleric, "Light")).containsEntry("grantedBy", STUDENT);
    }

    @Test
    void studentOfArcana_replacementRefusals() {
        // Given up without a replacement, or with one too many.
        assertRefused(() -> levelUp(clericWithCantrips(5), null, null, null, List.of("Mage Hand")),
                "Student of Arcana requires exactly two Wizard cantrips");
        assertRefused(() -> levelUp(clericWithCantrips(5), null,
                List.of(pick(0, "Light", STUDENT), pick(0, "Message", STUDENT)), null, List.of("Mage Hand")),
                "Student of Arcana requires exactly two Wizard cantrips");
        // The replacement must be marked as the feature's: unmarked, it is a Cleric cantrip, and none is due at 6.
        assertRefused(() -> levelUp(clericWithCantrips(5), null, List.of(spell(0, "Light")), null,
                List.of("Mage Hand")), "Student of Arcana requires exactly two Wizard cantrips");
        // One per feature per level.
        assertRefused(() -> levelUp(clericWithCantrips(5), null,
                List.of(pick(0, "Light", STUDENT), pick(0, "Message", STUDENT)), null,
                List.of("Mage Hand", "Fire Bolt")), "Only one Student of Arcana spell can be replaced per level");
        // Only a pick of the feature can be given up.
        assertRefused(() -> levelUp(clericWithCantrips(5), null, List.of(pick(0, "Light", STUDENT)), null,
                List.of("Sacred Flame")), "'Sacred Flame' is not a spell that can be replaced");
        assertRefused(() -> levelUp(clericWithCantrips(5), null, List.of(pick(0, "Light", STUDENT)), null,
                List.of("Prestidigitation")), "'Prestidigitation' is not a spell that can be replaced");
        // The cantrip kept cannot be picked again as the replacement.
        assertRefused(() -> levelUp(clericWithCantrips(5), null, List.of(pick(0, "Fire Bolt", STUDENT)), null,
                List.of("Mage Hand")), "Student of Arcana requires exactly two Wizard cantrips");
    }

    @Test
    void blankNamesInForgetSpells_areIgnored() {
        PC cleric = clericWithCantrips(5);
        levelUp(cleric, null, null, null, java.util.Arrays.asList(" ", null));
        assertThat(spellNames(cleric)).containsExactly("Sacred Flame", "Mage Hand", "Fire Bolt");
    }

    // ── Arcana Domain at Cleric 17: Magical Mastery ──────────────────────────────

    private static List<Map<String, Object>> fourMasterySpells() {
        return List.of(pick(6, "Disintegrate", MASTERY), pick(7, "Teleport", MASTERY),
                pick(8, "Maze", MASTERY), pick(9, "Wish", MASTERY));
    }

    @Test
    void magicalMastery_atSeventeen_takesOneWizardSpellOfEachOfTheFourLevels() {
        PC cleric = clericWithCantrips(16);
        List<Map<String, Object>> newSpells = new ArrayList<>(fourMasterySpells());
        newSpells.add(spell(9, "Mass Heal")); // the Cleric's own one new prepared spell at 17
        levelUp(cleric, null, newSpells, null, null);

        assertThat(cleric.getLevel()).isEqualTo((short) 17);
        for (String name : List.of("Disintegrate", "Teleport", "Maze", "Wish")) {
            assertThat(saved(cleric, name)).as(name).containsEntry("grantedBy", MASTERY)
                    .containsEntry("alwaysPrepared", true).containsEntry("prepared", true);
        }
        // Four spells beyond the one the class allows: they are not counted against it.
        assertThat(saved(cleric, "Mass Heal")).doesNotContainKey("grantedBy").doesNotContainKey("alwaysPrepared");
        assertThat(cleric.getFeatures()).contains("\"Magical Mastery\"");
    }

    @Test
    void magicalMastery_atSeventeen_refusesAnythingButOneSpellPerLevel() {
        String message = "Magical Mastery requires four Wizard spells, one each of level 6, 7, 8 and 9";
        assertRefused(() -> levelUp(clericWithCantrips(16), null, null, null, null), message);
        assertRefused(() -> levelUp(clericWithCantrips(16), null,
                List.of(pick(6, "Disintegrate", MASTERY), pick(7, "Teleport", MASTERY), pick(8, "Maze", MASTERY)),
                null, null), message);
        assertRefused(() -> levelUp(clericWithCantrips(16), null,
                List.of(pick(6, "Disintegrate", MASTERY), pick(6, "Chain Lightning", MASTERY),
                        pick(8, "Maze", MASTERY), pick(9, "Wish", MASTERY)), null, null), message);
        assertRefused(() -> levelUp(clericWithCantrips(16), null,
                List.of(pick(5, "Cone of Cold", MASTERY), pick(7, "Teleport", MASTERY),
                        pick(8, "Maze", MASTERY), pick(9, "Wish", MASTERY)), null, null), message);
    }

    @Test
    void magicalMastery_isNotOnOffer_belowSeventeen() {
        // Below 17 the mark means nothing: the spells are ordinary picks, and far above the slot cap.
        assertThatThrownBy(() -> levelUp(clericWithCantrips(10), null, fourMasterySpells(), null, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(400));
    }

    private static PC clericWithMastery(int level) {
        PC cleric = clericWithCantrips(level);
        cleric.setSpells("[" + held(0, "Mage Hand", STUDENT) + "," + held(0, "Fire Bolt", STUDENT) + ","
                + held(6, "Disintegrate", MASTERY) + "," + held(7, "Teleport", MASTERY) + ","
                + held(8, "Maze", MASTERY) + "," + held(9, "Wish", MASTERY) + "]");
        return cleric;
    }

    @Test
    void magicalMastery_oneSpellMayBeReplacedPerLevel_byOneOfTheSameLevel() {
        PC cleric = clericWithMastery(17); // 17 -> 18
        levelUp(cleric, null, List.of(pick(7, "Plane Shift", MASTERY)), null, List.of("Teleport"));

        assertThat(spellNames(cleric))
                .containsExactly("Mage Hand", "Fire Bolt", "Disintegrate", "Maze", "Wish", "Plane Shift");
        assertThat(saved(cleric, "Plane Shift")).containsEntry("grantedBy", MASTERY)
                .containsEntry("alwaysPrepared", true);

        String message = "Magical Mastery requires four Wizard spells, one each of level 6, 7, 8 and 9";
        assertRefused(() -> levelUp(clericWithMastery(17), null, List.of(pick(8, "Sunburst", MASTERY)), null,
                List.of("Teleport")), message);
        assertRefused(() -> levelUp(clericWithMastery(17), null, null, null, List.of("Teleport")), message);
        assertRefused(() -> levelUp(clericWithMastery(17), null,
                List.of(pick(7, "Plane Shift", MASTERY), pick(8, "Sunburst", MASTERY)), null,
                List.of("Teleport", "Maze")), "Only one Magical Mastery spell can be replaced per level");
    }

    @Test
    void aCantripAndAMasterySpell_mayBothBeReplacedInOneLevelUp() {
        PC cleric = clericWithMastery(17); // 17 -> 18
        levelUp(cleric, null, List.of(pick(0, "Light", STUDENT), pick(9, "Meteor Swarm", MASTERY)), null,
                List.of("Mage Hand", "Wish"));

        assertThat(spellNames(cleric))
                .containsExactly("Fire Bolt", "Disintegrate", "Teleport", "Maze", "Light", "Meteor Swarm");
    }

    // ── Arcane Archer: skills ────────────────────────────────────────────────────

    @Test
    void arcaneArcher_withNeitherSkill_gainsBoth_andOwesNoPick() {
        PC fighter = pc("Fighter", 2);
        fighter.setSkills("{\"Athletics\":\"prof\"}");

        levelUp(fighter, "Arcane Archer", null, null, null); // the key may be left out

        assertThat(fighter.getSubclass()).isEqualTo("Arcane Archer");
        assertThat(fighter.getSubclassChoices()).isEqualTo("{\"skills\":[]}");
        assertThat(skillsOf(fighter)).containsOnly(
                Map.entry("Athletics", "prof"), Map.entry("Arcana", "prof"), Map.entry("Nature", "prof"));

        // A character with no skills recorded at all gets its first two.
        PC blank = pc("Fighter", 2);
        levelUp(blank, "Arcane Archer", null, Map.of("skills", List.of()), null);
        assertThat(blank.getSkills()).isEqualTo("{\"Arcana\":\"prof\",\"Nature\":\"prof\"}");

        assertRefused(() -> levelUp(pc("Fighter", 2), "Arcane Archer", null,
                Map.of("skills", List.of("Athletics")), null), "Arcane Archer requires exactly 0 skill choices");
    }

    @Test
    void arcaneArcher_withOneOfTheTwo_picksOneFighterSkillInItsPlace() {
        PC fighter = pc("Fighter", 2);
        fighter.setSkills("{\"Nature\":\"prof\",\"Athletics\":\"prof\"}");

        levelUp(fighter, "Arcane Archer", null, Map.of("skills", List.of("Perception")), null);

        assertThat(fighter.getSubclassChoices()).isEqualTo("{\"skills\":[\"Perception\"]}");
        assertThat(skillsOf(fighter)).containsOnly(Map.entry("Nature", "prof"), Map.entry("Athletics", "prof"),
                Map.entry("Arcana", "prof"), Map.entry("Perception", "prof"));

        java.util.function.Supplier<PC> knowsNature = () -> {
            PC f = pc("Fighter", 2);
            f.setSkills("{\"Nature\":\"prof\"}");
            return f;
        };
        assertRefused(() -> levelUp(knowsNature.get(), "Arcane Archer", null, null, null),
                "Arcane Archer requires exactly 1 skill choice");
        assertRefused(() -> levelUp(knowsNature.get(), "Arcane Archer", null, Map.of("skills", List.of()), null),
                "Arcane Archer requires exactly 1 skill choice");
        assertRefused(() -> levelUp(knowsNature.get(), "Arcane Archer", null,
                Map.of("skills", List.of("Stealth")), null), "'Stealth' is not a skill Arcane Archer can choose");
    }

    @Test
    void arcaneArcher_withBoth_picksTwo_andExpertiseIsNeverLowered() {
        PC fighter = pc("Fighter", 2);
        fighter.setSkills("{\"Arcana\":\"expert\",\"Nature\":\"prof\",\"Insight\":\"prof\"}");

        levelUp(fighter, "Arcane Archer", null, Map.of("skills", List.of("Survival", "Acrobatics")), null);

        assertThat(fighter.getSubclassChoices()).isEqualTo("{\"skills\":[\"Survival\",\"Acrobatics\"]}");
        assertThat(skillsOf(fighter)).containsOnly(Map.entry("Arcana", "expert"), Map.entry("Nature", "prof"),
                Map.entry("Insight", "prof"), Map.entry("Survival", "prof"), Map.entry("Acrobatics", "prof"));

        java.util.function.Supplier<PC> knowsBoth = () -> {
            PC f = pc("Fighter", 2);
            f.setSkills("{\"Arcana\":\"prof\",\"Nature\":\"prof\",\"Insight\":\"prof\"}");
            return f;
        };
        assertRefused(() -> levelUp(knowsBoth.get(), "Arcane Archer", null,
                Map.of("skills", List.of("Survival")), null), "Arcane Archer requires exactly 2 skill choices");
        assertRefused(() -> levelUp(knowsBoth.get(), "Arcane Archer", null,
                Map.of("skills", List.of("Survival", "Insight")), null), "'Insight' is already a skill proficiency");
    }

    // ── Arcane Archer: Arcane Shot options ───────────────────────────────────────

    private static PC archer(int level, String choices) {
        PC fighter = pc("Fighter", "Arcane Archer", level);
        fighter.setSubclassChoices(choices);
        fighter.setSkills("{\"Arcana\":\"prof\",\"Nature\":\"prof\"}");
        return fighter;
    }

    @Test
    void arcaneShots_mayWait_whileTheirPickerIsNotBuilt() {
        // Level 3 without the key, level 7 without the key: both go through.
        PC fighter = pc("Fighter", 2);
        levelUp(fighter, "Arcane Archer", null, null, null);
        assertThat(fighter.getSubclassChoices()).isEqualTo("{\"skills\":[]}");

        PC seven = archer(6, "{\"skills\":[],\"arcaneShots\":[\"Banishing Shot\",\"Seeking Shot\"]}");
        service.applyLevelUp(seven, null, null, null, null, HpMode.AVERAGE, null, null, null, null);
        assertThat(seven.getLevel()).isEqualTo((short) 7);
        assertThat(seven.getSubclassChoices())
                .isEqualTo("{\"skills\":[],\"arcaneShots\":[\"Banishing Shot\",\"Seeking Shot\"]}");
    }

    @Test
    void arcaneShots_whenSent_mustBeTheCountForTheNewLevel() {
        PC fighter = pc("Fighter", 2);
        levelUp(fighter, "Arcane Archer", null,
                Map.of("arcaneShots", List.of("seeking shot", "Banishing Shot")), null);
        assertThat(fighter.getSubclassChoices())
                .isEqualTo("{\"skills\":[],\"arcaneShots\":[\"Seeking Shot\",\"Banishing Shot\"]}");

        assertRefused(() -> levelUp(pc("Fighter", 2), "Arcane Archer", null,
                Map.of("arcaneShots", List.of("Seeking Shot")), null),
                "An Arcane Archer knows exactly 2 Arcane Shot options at level 3");
        assertRefused(() -> levelUp(pc("Fighter", 2), "Arcane Archer", null,
                Map.of("arcaneShots", List.of("Seeking Shot", "Exploding Shot")), null),
                "'Exploding Shot' is not an Arcane Shot option");
        assertRefused(() -> levelUp(pc("Fighter", 2), "Arcane Archer", null,
                Map.of("arcaneShots", List.of("Seeking Shot", "Seeking Shot")), null),
                "'Seeking Shot' is chosen twice");
        assertRefused(() -> levelUp(pc("Fighter", 2), "Arcane Archer", null,
                Map.of("arcaneShots", "Seeking Shot"), null), "'arcaneShots' must be a list of names");
    }

    @Test
    void arcaneShots_atSeven_oneMoreIsLearned_andOneKnownMayBeSwapped() {
        String two = "{\"skills\":[],\"arcaneShots\":[\"Banishing Shot\",\"Seeking Shot\"]}";

        PC kept = archer(6, two);
        levelUp(kept, null, null, Map.of("arcaneShots", List.of("Banishing Shot", "Seeking Shot", "Shadow Shot")),
                null);
        assertThat(kept.getSubclassChoices()).isEqualTo(
                "{\"skills\":[],\"arcaneShots\":[\"Banishing Shot\",\"Seeking Shot\",\"Shadow Shot\"]}");

        PC swapped = archer(6, two);
        levelUp(swapped, null, null,
                Map.of("arcaneShots", List.of("Banishing Shot", "Bursting Shot", "Shadow Shot")), null);
        assertThat(swapped.getSubclassChoices()).isEqualTo(
                "{\"skills\":[],\"arcaneShots\":[\"Banishing Shot\",\"Bursting Shot\",\"Shadow Shot\"]}");

        assertRefused(() -> levelUp(archer(6, two), null, null,
                Map.of("arcaneShots", List.of("Grasping Shot", "Bursting Shot", "Shadow Shot")), null),
                "Only one known Arcane Shot option can be replaced at level 7");
        assertRefused(() -> levelUp(archer(6, two), null, null,
                Map.of("arcaneShots", List.of("Banishing Shot", "Seeking Shot")), null),
                "An Arcane Archer knows exactly 3 Arcane Shot options at level 7");
    }

    @Test
    void arcaneShots_atALevelThatAddsNone_nothingKnownMayBeSwapped_butAShortListMayBeFilled() {
        String two = "{\"skills\":[],\"arcaneShots\":[\"Banishing Shot\",\"Seeking Shot\"]}";
        assertRefused(() -> levelUp(archer(4, two), null, null,
                Map.of("arcaneShots", List.of("Banishing Shot", "Shadow Shot")), null),
                "A known Arcane Shot option can only be replaced at a level that adds a new one");

        // Nothing stored yet at level 4: both are picked at 5.
        PC late = archer(4, "{\"skills\":[]}");
        levelUp(late, null, null, Map.of("arcaneShots", List.of("Piercing Shot", "Shadow Shot")), null);
        assertThat(late.getSubclassChoices())
                .isEqualTo("{\"skills\":[],\"arcaneShots\":[\"Piercing Shot\",\"Shadow Shot\"]}");
    }

    // ── Arcane Archer Lore: the cantrip ──────────────────────────────────────────

    private static final String LORE = "Arcane Archer Lore";

    @Test
    void arcaneArcherLore_theCantripMayWait_andWhenSentIsOneOfTheTwo() {
        PC without = pc("Fighter", 2);
        levelUp(without, "Arcane Archer", null, null, null);
        assertThat(without.getSpells()).isNull();

        PC fighter = pc("Fighter", 2); // a Fighter has no cantrips of its own and no spell slots
        levelUp(fighter, "Arcane Archer", List.of(pick(0, "Druidcraft", LORE)), null, null);
        assertThat(saved(fighter, "Druidcraft")).containsEntry("grantedBy", LORE).containsEntry("prepared", true)
                .doesNotContainKey("alwaysPrepared");

        // A character that skipped it at 3 picks it at a later level-up.
        PC late = archer(4, "{\"skills\":[]}");
        levelUp(late, null, List.of(pick(0, "prestidigitation", LORE)), null, null);
        assertThat(spellNames(late)).containsExactly("prestidigitation");

        String message = "Arcane Archer Lore requires one cantrip, Druidcraft or Prestidigitation";
        assertRefused(() -> levelUp(pc("Fighter", 2), "Arcane Archer", List.of(pick(0, "Fire Bolt", LORE)),
                null, null), message);
        assertRefused(() -> levelUp(pc("Fighter", 2), "Arcane Archer",
                List.of(pick(0, "Druidcraft", LORE), pick(0, "Prestidigitation", LORE)), null, null), message);
        // It already has its cantrip: a second one is refused, and the cantrip is not replaceable.
        PC has = archer(4, "{\"skills\":[]}");
        has.setSpells("[" + held(0, "Druidcraft", LORE) + "]");
        assertRefused(() -> levelUp(has, null, List.of(pick(0, "Prestidigitation", LORE)), null, null), message);
        assertRefused(() -> levelUp(has, null, List.of(pick(0, "Prestidigitation", LORE)), null,
                List.of("Druidcraft")), "'Druidcraft' is not a spell that can be replaced");
    }

    // ── Vestige Patron ───────────────────────────────────────────────────────────

    @Test
    void vestigePatron_typeAndDomainMayWait_andAreCheckedWhenSent() {
        PC waiting = pc("Warlock", "Vestige Patron", 2);
        levelUp(waiting, null, null, null, null);
        assertThat(waiting.getLevel()).isEqualTo((short) 3);
        assertThat(waiting.getSubclassChoices()).isNull();

        PC warlock = pc("Warlock", "Vestige Patron", 2);
        levelUp(warlock, null, null, Map.of("vestigeType", "celestial", "vestigeDomain", "Life"), null);
        assertThat(warlock.getSubclassChoices()).isEqualTo("{\"vestigeType\":\"Celestial\",\"vestigeDomain\":\"Life\"}");
        assertThat(warlock.getSkills()).isNull();

        assertRefused(() -> levelUp(pc("Warlock", "Vestige Patron", 2), null, null,
                Map.of("vestigeType", "Dragon"), null), "'Dragon' is not a Vestige type (Celestial, Fiend or Undead)");
        assertRefused(() -> levelUp(pc("Warlock", "Vestige Patron", 2), null, null,
                Map.of("vestigeDomain", "Arcana"), null),
                "'Arcana' is not a Vestige domain (Life, Light, Trickery or War)");
        // Its features, and so its choices, arrive at Warlock 3: at 2 there is nothing to choose.
        assertRefused(() -> levelUp(pc("Warlock", "Vestige Patron", 1), null, null,
                Map.of("vestigeType", "Fiend"), null), "'vestigeType' is not a choice this character makes at level 2");
    }

    @Test
    void vestigePatron_aLaterLevelUpMayChangeTheType_butNotTheDomain() {
        PC warlock = pc("Warlock", "Vestige Patron", 5);
        warlock.setSubclassChoices("{\"vestigeType\":\"Celestial\",\"vestigeDomain\":\"Life\"}");

        levelUp(warlock, null, null, Map.of("vestigeType", "Undead"), null);
        assertThat(warlock.getSubclassChoices()).isEqualTo("{\"vestigeType\":\"Undead\",\"vestigeDomain\":\"Life\"}");

        assertRefused(() -> levelUp(warlock, null, null, Map.of("vestigeDomain", "War"), null),
                "The Vestige domain cannot be changed once it is chosen");
        // A domain chosen late is still a first choice.
        PC late = pc("Warlock", "Vestige Patron", 5);
        late.setSubclassChoices("{\"vestigeType\":\"Fiend\"}");
        levelUp(late, null, null, Map.of("vestigeDomain", "war"), null);
        assertThat(late.getSubclassChoices()).isEqualTo("{\"vestigeType\":\"Fiend\",\"vestigeDomain\":\"War\"}");
    }

    // ── Everyone else levels up exactly as before ────────────────────────────────

    /** Level a copy up through the old signature and one through the new: every column must match. */
    private void assertSameAsBefore(java.util.function.Supplier<PC> character, String subclass,
                                    List<Map<String, Object>> newSpells) {
        PC before = character.get();
        service.applyLevelUp(before, subclass, null, null, newSpells, HpMode.AVERAGE, null, null);
        for (Map<String, Object> choices : java.util.Arrays.<Map<String, Object>>asList(null, Map.of())) {
            for (List<String> forget : java.util.Arrays.<List<String>>asList(null, List.of())) {
                PC after = character.get();
                levelUp(after, subclass, newSpells, choices, forget);
                assertThat(after).usingRecursiveComparison().isEqualTo(before);
            }
        }
        assertThat(before.getSubclassChoices()).isNull();
    }

    @Test
    void aLifeDomainCleric_levelsUpAsBefore() {
        java.util.function.Supplier<PC> cleric = () -> {
            PC c = pc("Cleric", 2);
            c.setSkills("{\"Insight\":\"prof\"}");
            c.setSpells("[{\"lvl\":0,\"name\":\"Sacred Flame\",\"prepared\":true}]");
            return c;
        };
        List<Map<String, Object>> newSpells = List.of(spell(2, "Hold Person"), spell(1, "Bless"));
        assertSameAsBefore(cleric, "Life Domain", newSpells);

        PC leveled = cleric.get();
        levelUp(leveled, "Life Domain", newSpells, null, null);
        assertThat(leveled.getLevel()).isEqualTo((short) 3);
        assertThat(leveled.getSubclass()).isEqualTo("Life Domain");
        assertThat(leveled.getSkills()).isEqualTo("{\"Insight\":\"prof\"}");
        assertThat(spellNames(leveled)).containsExactly("Sacred Flame", "Hold Person", "Bless");
        assertThat(saved(leveled, "Bless")).containsEntry("grantedBy", "Life Domain Spells");
        assertThat(leveled.getFeatures()).contains("\"Disciple of Life\"").doesNotContain("Student of Arcana");
    }

    @Test
    void aChampionFighter_levelsUpAsBefore() {
        java.util.function.Supplier<PC> fighter = () -> {
            PC f = pc("Fighter", 2);
            f.setSkills("{\"Athletics\":\"prof\"}");
            return f;
        };
        assertSameAsBefore(fighter, "Champion", null);

        PC leveled = fighter.get();
        levelUp(leveled, "Champion", null, null, null);
        assertThat(leveled.getSkills()).isEqualTo("{\"Athletics\":\"prof\"}"); // no Arcana, no Nature
        assertThat(leveled.getSpells()).isNull();
        assertThat(leveled.getFeatures()).contains("\"Improved Critical\"");
    }

    @Test
    void anEvokerWizard_levelsUpAsBefore() {
        java.util.function.Supplier<PC> wizard = () -> {
            PC w = pc("Wizard", "Evoker", 5);
            w.setSpells("[{\"lvl\":0,\"name\":\"Fire Bolt\",\"prepared\":true}]");
            return w;
        };
        List<Map<String, Object>> newSpells = List.of(spell(3, "Fireball"));
        assertSameAsBefore(wizard, null, newSpells);

        PC leveled = wizard.get();
        levelUp(leveled, null, newSpells, null, null);
        assertThat(leveled.getLevel()).isEqualTo((short) 6);
        assertThat(spellNames(leveled)).containsExactly("Fire Bolt", "Fireball");
        assertThat(leveled.getFeatures()).contains("\"Sculpt Spells\"");
        assertThat(leveled.getSkills()).isNull();
    }

    @Test
    void aCharacterWithoutChoices_isRefusedOnlyWhenItSendsSome() {
        assertRefused(() -> levelUp(pc("Cleric", 2), "Life Domain", null, Map.of("skills", List.of("Insight")), null),
                "'skills' is not a choice this character makes at level 3");
        assertRefused(() -> levelUp(pc("Fighter", 2), "Champion", null,
                Map.of("arcaneShots", List.of("Seeking Shot", "Shadow Shot")), null),
                "'arcaneShots' is not a choice this character makes at level 3");
        assertRefused(() -> levelUp(pc("Wizard", "Evoker", 5), null, null, Map.of("vestigeType", "Fiend"), null),
                "'vestigeType' is not a choice this character makes at level 6");
        assertRefused(() -> levelUp(pc("Wizard", "Evoker", 5), null, null, Map.of("maneuvers", List.of()), null),
                "'maneuvers' is not a subclass choice");
        // Another subclass's key is as foreign as an unknown one.
        assertRefused(() -> levelUp(pc("Cleric", 2), "Arcana Domain", null,
                Map.of("skills", List.of("Arcana"), "vestigeDomain", "Life"), null),
                "'vestigeDomain' is not a choice this character makes at level 3");

        // A key sent as null is no key.
        Map<String, Object> nulls = new LinkedHashMap<>();
        nulls.put("skills", null);
        PC wizard = pc("Wizard", "Evoker", 5);
        levelUp(wizard, null, null, nulls, null);
        assertThat(wizard.getLevel()).isEqualTo((short) 6);
        assertThat(wizard.getSubclassChoices()).isNull();
    }

    @Test
    void aCharacterWithoutSpellPickFeatures_treatsTheMarksAsBefore_andCannotGiveUpASpell() {
        // On a Life Domain cleric a spell marked "Student of Arcana" is an ordinary Cleric cantrip:
        // none is due at level 3, so it is refused the way any extra cantrip always was.
        assertRefused(() -> levelUp(pc("Cleric", 2), "Life Domain", List.of(pick(0, "Mage Hand", STUDENT)), null,
                null), "You can learn at most 0 new cantrip(s) at level 3");
        // At level 4 a Cleric learns one: the marked cantrip is taken as that pick, mark and all, as before.
        PC cleric = pc("Cleric", "Life Domain", 3);
        service.applyLevelUp(cleric, null, Map.of("WIS", 2), null, List.of(pick(0, "Mage Hand", STUDENT)),
                HpMode.AVERAGE, null, null, null, null);
        assertThat(saved(cleric, "Mage Hand")).containsEntry("grantedBy", STUDENT);

        PC wizard = pc("Wizard", "Evoker", 5);
        wizard.setSpells("[{\"lvl\":0,\"name\":\"Fire Bolt\",\"prepared\":true}]");
        assertRefused(() -> levelUp(wizard, null, null, null, List.of("Fire Bolt")),
                "'Fire Bolt' is not a spell that can be replaced");
        assertThat(wizard.getSpells()).isEqualTo("[{\"lvl\":0,\"name\":\"Fire Bolt\",\"prepared\":true}]");
    }
}
