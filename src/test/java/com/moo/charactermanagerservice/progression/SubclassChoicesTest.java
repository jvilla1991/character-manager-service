package com.moo.charactermanagerservice.progression;

import com.moo.charactermanagerservice.progression.SubclassChoices.Change;
import com.moo.charactermanagerservice.progression.SubclassChoices.Choices;
import com.moo.charactermanagerservice.progression.SubclassChoices.InvalidChoice;
import com.moo.charactermanagerservice.progression.SubclassChoices.SpellPicks;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The rules of the choices a subclass lets the player make (Arcana Unleashed): which keys a
 * character has, what may be chosen, how a stored value is cleaned, and every refusal with the
 * words the player reads.
 */
class SubclassChoicesTest {

    private static final List<String> NO_SKILLS = List.of();

    private static Choices skills(String... names) {
        return new Choices(List.of(names), null, null, null);
    }

    private static Choices shots(String... names) {
        return new Choices(null, List.of(names), null, null);
    }

    private static Choices vestige(String type, String domain) {
        return new Choices(null, null, type, domain);
    }

    private static void assertRefused(ThrowingCallable call, String message) {
        assertThatThrownBy(call).isInstanceOf(InvalidChoice.class).hasMessage(message);
    }

    // ── Which character has which choice ─────────────────────────────────────────

    @Test
    void keysFor_onlyTheThreeSubclasses_fromLevelThree() {
        assertThat(SubclassChoices.keysFor("Cleric", "Arcana Domain", 3)).containsExactly("skills");
        assertThat(SubclassChoices.keysFor("Fighter", "Arcane Archer", 3)).containsExactly("skills", "arcaneShots");
        assertThat(SubclassChoices.keysFor("Warlock", "Vestige Patron", 3))
                .containsExactly("vestigeType", "vestigeDomain");
        assertThat(SubclassChoices.keysFor(" cleric ", " arcana domain ", 20)).containsExactly("skills");

        // A Vestige Patron is picked at Warlock 1, but has nothing to choose before its features at 3.
        assertThat(SubclassChoices.keysFor("Warlock", "Vestige Patron", 2)).isEmpty();
        assertThat(SubclassChoices.keysFor("Fighter", "Arcane Archer", 2)).isEmpty();
        // Everyone else has none, and the class counts as well as the name.
        assertThat(SubclassChoices.keysFor("Cleric", "Life Domain", 10)).isEmpty();
        assertThat(SubclassChoices.keysFor("Fighter", "Champion", 10)).isEmpty();
        assertThat(SubclassChoices.keysFor("Wizard", "Evoker", 10)).isEmpty();
        assertThat(SubclassChoices.keysFor("Wizard", "Conjurer", 10)).isEmpty();
        assertThat(SubclassChoices.keysFor("Monk", "Warrior of the Mystic Arts", 10)).isEmpty();
        assertThat(SubclassChoices.keysFor("Wizard", "Arcane Archer", 10)).isEmpty();
        assertThat(SubclassChoices.keysFor("Cleric", null, 10)).isEmpty();
        assertThat(SubclassChoices.keysFor(null, "Arcana Domain", 10)).isEmpty();
    }

    @Test
    void arcaneShotCount_followsTheFighterLevel() {
        int[] expected = {0, 0, 2, 2, 2, 2, 3, 3, 3, 4, 4, 4, 4, 4, 5, 5, 5, 6, 6, 6};
        for (int level = 1; level <= 20; level++) {
            assertThat(SubclassChoices.arcaneShotCount(level)).as("level " + level).isEqualTo(expected[level - 1]);
        }
    }

    @Test
    void optionLists_areTheBooks() {
        assertThat(SubclassChoices.KEYS).containsExactly("skills", "arcaneShots", "vestigeType", "vestigeDomain");
        assertThat(SubclassChoices.ARCANE_SHOT_OPTIONS).containsExactly(
                "Banishing Shot", "Beguiling Shot", "Bursting Shot", "Enfeebling Shot", "Grasping Shot",
                "Piercing Shot", "Seeking Shot", "Shadow Shot");
        assertThat(SubclassChoices.VESTIGE_TYPES).containsExactly("Celestial", "Fiend", "Undead");
        assertThat(SubclassChoices.VESTIGE_DOMAINS).containsExactly("Life", "Light", "Trickery", "War");
        assertThat(SubclassChoices.ARCANA_DOMAIN_SKILLS).containsExactly(
                "Arcana", "History", "Insight", "Medicine", "Persuasion", "Religion");
        assertThat(SubclassChoices.FIGHTER_SKILLS).containsExactly(
                "Acrobatics", "Animal Handling", "Athletics", "History", "Insight", "Intimidation",
                "Persuasion", "Perception", "Survival");
    }

    @Test
    void onlySkillsAndTheTwoArcanaDomainSpellFeatures_areRequiredForNow() {
        assertThat(SubclassChoices.isRequired("skills")).isTrue();
        assertThat(SubclassChoices.isRequired("Student of Arcana")).isTrue();
        assertThat(SubclassChoices.isRequired("Magical Mastery")).isTrue();
        assertThat(SubclassChoices.isRequired("arcaneShots")).isFalse();
        assertThat(SubclassChoices.isRequired("vestigeType")).isFalse();
        assertThat(SubclassChoices.isRequired("vestigeDomain")).isFalse();
        assertThat(SubclassChoices.isRequired("Arcane Archer Lore")).isFalse();
    }

    // ── Reading, writing and cleaning the stored value ───────────────────────────

    @Test
    void write_putsTheKeysInOrder_andNothingIsNull() {
        assertThat(SubclassChoices.write(new Choices(List.of("Insight"),
                List.of("Banishing Shot", "Seeking Shot"), "Celestial", "Life")))
                .isEqualTo("{\"skills\":[\"Insight\"],\"arcaneShots\":[\"Banishing Shot\",\"Seeking Shot\"],"
                        + "\"vestigeType\":\"Celestial\",\"vestigeDomain\":\"Life\"}");
        assertThat(SubclassChoices.write(skills())).isEqualTo("{\"skills\":[]}");
        assertThat(SubclassChoices.write(Choices.NONE)).isNull();
        assertThat(SubclassChoices.write(null)).isNull();
    }

    @Test
    void parse_isForgiving() {
        assertThat(SubclassChoices.parse(null)).isEqualTo(Choices.NONE);
        assertThat(SubclassChoices.parse("  ")).isEqualTo(Choices.NONE);
        assertThat(SubclassChoices.parse("not json")).isEqualTo(Choices.NONE);
        assertThat(SubclassChoices.parse("[1,2]")).isEqualTo(Choices.NONE);
        assertThat(SubclassChoices.parse("{\"skills\":\"Insight\",\"arcaneShots\":[1],\"vestigeType\":7,"
                + "\"vestigeDomain\":\" War \",\"other\":true}"))
                .isEqualTo(vestige(null, "War"));
        assertThat(SubclassChoices.parse("{\"skills\":[],\"vestigeType\":\"Fiend\"}"))
                .isEqualTo(new Choices(List.of(), null, "Fiend", null));
    }

    @Test
    void clean_dropsKeysTheCharacterDoesNotHave() {
        String all = "{\"skills\":[\"Insight\"],\"arcaneShots\":[\"Banishing Shot\",\"Seeking Shot\"],"
                + "\"vestigeType\":\"Celestial\",\"vestigeDomain\":\"Life\"}";
        assertThat(SubclassChoices.clean(all, "Cleric", "Arcana Domain", 5)).isEqualTo("{\"skills\":[\"Insight\"]}");
        assertThat(SubclassChoices.clean(all, "Fighter", "Arcane Archer", 5))
                .isEqualTo("{\"skills\":[\"Insight\"],\"arcaneShots\":[\"Banishing Shot\",\"Seeking Shot\"]}");
        assertThat(SubclassChoices.clean(all, "Warlock", "Vestige Patron", 5))
                .isEqualTo("{\"vestigeType\":\"Celestial\",\"vestigeDomain\":\"Life\"}");
        // The subclass changed, the level dropped below 3, or the class never had choices.
        assertThat(SubclassChoices.clean(all, "Cleric", "Life Domain", 5)).isNull();
        assertThat(SubclassChoices.clean(all, "Fighter", "Arcane Archer", 2)).isNull();
        assertThat(SubclassChoices.clean(all, "Wizard", "Evoker", 5)).isNull();
        assertThat(SubclassChoices.clean(all, "Cleric", null, 5)).isNull();
        assertThat(SubclassChoices.clean(null, "Cleric", "Arcana Domain", 5)).isNull();
    }

    @Test
    void clean_dropsNamesThatAreNotOptions_andCutsListsToTheLevel() {
        // Unknown names go, spelling is put right, a repeat counts once.
        assertThat(SubclassChoices.clean("{\"arcaneShots\":[\"banishing shot\",\"Exploding Shot\",\"Banishing Shot\","
                + "\"Seeking Shot\"]}", "Fighter", "Arcane Archer", 6))
                .isEqualTo("{\"arcaneShots\":[\"Banishing Shot\",\"Seeking Shot\"]}");
        // Three known at level 7 are two again when the level is lowered to 6.
        assertThat(SubclassChoices.clean("{\"arcaneShots\":[\"Banishing Shot\",\"Seeking Shot\",\"Shadow Shot\"]}",
                "Fighter", "Arcane Archer", 6))
                .isEqualTo("{\"arcaneShots\":[\"Banishing Shot\",\"Seeking Shot\"]}");
        // No option left is the same as never chosen; a settled, empty skill choice is kept.
        assertThat(SubclassChoices.clean("{\"skills\":[],\"arcaneShots\":[\"Nope\"]}", "Fighter", "Arcane Archer", 6))
                .isEqualTo("{\"skills\":[]}");
        assertThat(SubclassChoices.clean("{\"skills\":[\"Insight\",\"Religion\"]}", "Cleric", "Arcana Domain", 6))
                .isEqualTo("{\"skills\":[\"Insight\"]}");
        assertThat(SubclassChoices.clean("{\"skills\":[\"Stealth\"]}", "Fighter", "Arcane Archer", 6))
                .isEqualTo("{\"skills\":[]}");
        assertThat(SubclassChoices.clean("{\"vestigeType\":\"Dragon\",\"vestigeDomain\":\"war\"}",
                "Warlock", "Vestige Patron", 6))
                .isEqualTo("{\"vestigeDomain\":\"War\"}");
    }

    @Test
    void fromRequest_refusesUnknownKeysAndWrongShapes() {
        assertThat(SubclassChoices.fromRequest(null)).isEqualTo(Choices.NONE);
        assertThat(SubclassChoices.fromRequest(Map.of())).isEqualTo(Choices.NONE);
        assertThat(SubclassChoices.fromRequest(Map.of("skills", List.of(" Insight "), "vestigeType", "Fiend")))
                .isEqualTo(new Choices(List.of("Insight"), null, "Fiend", null));
        assertThat(SubclassChoices.fromRequest(Map.of("skills", List.of()))).isEqualTo(skills());
        // A key sent as null is a key not sent.
        Map<String, Object> withNull = new LinkedHashMap<>();
        withNull.put("arcaneShots", null);
        assertThat(SubclassChoices.fromRequest(withNull)).isEqualTo(Choices.NONE);

        assertRefused(() -> SubclassChoices.fromRequest(Map.of("maneuvers", List.of("Riposte"))),
                "'maneuvers' is not a subclass choice");
        assertRefused(() -> SubclassChoices.fromRequest(Map.of("skills", "Insight")),
                "'skills' must be a list of names");
        assertRefused(() -> SubclassChoices.fromRequest(Map.of("arcaneShots", List.of("Seeking Shot", 3))),
                "'arcaneShots' must be a list of names");
        assertRefused(() -> SubclassChoices.fromRequest(Map.of("arcaneShots", Arrays.asList("Seeking Shot", " "))),
                "'arcaneShots' must be a list of names");
        assertRefused(() -> SubclassChoices.fromRequest(Map.of("vestigeType", List.of("Fiend"))),
                "'vestigeType' must be a single name");
        assertRefused(() -> SubclassChoices.fromRequest(Map.of("vestigeDomain", 4)),
                "'vestigeDomain' must be a single name");
    }

    @Test
    void aKeyTheCharacterDoesNotHave_isRefused() {
        assertRefused(() -> SubclassChoices.atLevelUp("Cleric", "Life Domain", 3, Choices.NONE, skills("Insight"),
                NO_SKILLS), "'skills' is not a choice this character makes at level 3");
        assertRefused(() -> SubclassChoices.atLevelUp("Cleric", "Arcana Domain", 3, Choices.NONE,
                shots("Seeking Shot", "Shadow Shot"), NO_SKILLS),
                "'arcaneShots' is not a choice this character makes at level 3");
        assertRefused(() -> SubclassChoices.atLevelUp("Warlock", "Vestige Patron", 2, Choices.NONE,
                vestige("Fiend", null), NO_SKILLS), "'vestigeType' is not a choice this character makes at level 2");
        assertRefused(() -> SubclassChoices.direct("Fighter", "Champion", 9, Choices.NONE,
                vestige(null, "War"), NO_SKILLS), "'vestigeDomain' is not a choice this character makes at level 9");
        // Nothing sent is always fine, and leaves nothing to store.
        Change none = SubclassChoices.atLevelUp("Wizard", "Evoker", 9, skills("Insight"), Choices.NONE, NO_SKILLS);
        assertThat(none.choices()).isEqualTo(Choices.NONE);
        assertThat(none.skillsGained()).isEmpty();
    }

    // ── skills: Arcana Domain ────────────────────────────────────────────────────

    @Test
    void arcanaDomain_picksExactlyOneSkill_whenItReachesLevelThree() {
        Change change = SubclassChoices.atLevelUp("Cleric", "Arcana Domain", 3, Choices.NONE, skills("arcana"),
                List.of("Insight", "Medicine"));
        assertThat(change.choices()).isEqualTo(skills("Arcana"));
        assertThat(change.skillsGained()).containsExactly("Arcana");
        assertThat(change.skillsLost()).isEmpty();
        assertThat(SubclassChoices.skillsOwed("Cleric", "Arcana Domain", List.of("Insight"))).isEqualTo(1);

        // Any of the Cleric's level 1 skills will do as well.
        for (String skill : SubclassChoices.CLERIC_SKILLS) {
            assertThat(SubclassChoices.atLevelUp("Cleric", "Arcana Domain", 3, Choices.NONE, skills(skill), NO_SKILLS)
                    .skillsGained()).containsExactly(skill);
        }
    }

    @Test
    void arcanaDomain_skillRefusals() {
        assertRefused(() -> SubclassChoices.atLevelUp("Cleric", "Arcana Domain", 3, Choices.NONE, Choices.NONE,
                NO_SKILLS), "Arcana Domain requires exactly 1 skill choice");
        assertRefused(() -> SubclassChoices.atLevelUp("Cleric", "Arcana Domain", 3, Choices.NONE, skills(),
                NO_SKILLS), "Arcana Domain requires exactly 1 skill choice");
        assertRefused(() -> SubclassChoices.atLevelUp("Cleric", "Arcana Domain", 3, Choices.NONE,
                skills("Arcana", "Religion"), NO_SKILLS), "Arcana Domain requires exactly 1 skill choice");
        assertRefused(() -> SubclassChoices.atLevelUp("Cleric", "Arcana Domain", 3, Choices.NONE,
                skills("Stealth"), NO_SKILLS), "'Stealth' is not a skill Arcana Domain can choose");
        assertRefused(() -> SubclassChoices.atLevelUp("Cleric", "Arcana Domain", 3, Choices.NONE,
                skills("Insight"), List.of("insight")), "'Insight' is already a skill proficiency");
        assertRefused(() -> SubclassChoices.atLevelUp("Cleric", "Arcana Domain", 3, Choices.NONE,
                skills("Insight", "insight"), NO_SKILLS), "'Insight' is chosen twice");
    }

    @Test
    void arcanaDomain_stillOwesTheSkill_atALaterLevelUp_untilItIsStored() {
        // The DM made the character an Arcana Domain cleric by hand at level 8: the next level-up asks.
        assertRefused(() -> SubclassChoices.atLevelUp("Cleric", "Arcana Domain", 9, Choices.NONE, Choices.NONE,
                NO_SKILLS), "Arcana Domain requires exactly 1 skill choice");
        assertThat(SubclassChoices.atLevelUp("Cleric", "Arcana Domain", 9, Choices.NONE, skills("History"),
                NO_SKILLS).choices()).isEqualTo(skills("History"));
    }

    @Test
    void skills_onceStored_areNotChangedByALevelUp() {
        Choices stored = skills("Insight");
        // Not sending it, or sending the same again, changes nothing and grants nothing.
        Change kept = SubclassChoices.atLevelUp("Cleric", "Arcana Domain", 4, stored, Choices.NONE,
                List.of("Insight"));
        assertThat(kept.choices()).isEqualTo(stored);
        assertThat(kept.skillsGained()).isEmpty();
        assertThat(SubclassChoices.atLevelUp("Cleric", "Arcana Domain", 4, stored, skills("insight"),
                List.of("Insight")).choices()).isEqualTo(stored);

        assertRefused(() -> SubclassChoices.atLevelUp("Cleric", "Arcana Domain", 4, stored, skills("Religion"),
                List.of("Insight")), "The Arcana Domain skill choice has already been made");
        assertRefused(() -> SubclassChoices.atLevelUp("Fighter", "Arcane Archer", 4, skills(), skills("Athletics"),
                List.of("Arcana", "Nature")), "The Arcane Archer skill choice has already been made");
    }

    @Test
    void aCharacterAlreadyProficientInEveryOption_owesNoSkill() {
        Change change = SubclassChoices.atLevelUp("Cleric", "Arcana Domain", 3, Choices.NONE, Choices.NONE,
                SubclassChoices.ARCANA_DOMAIN_SKILLS);
        assertThat(change.choices()).isEqualTo(skills());
        assertThat(change.skillsGained()).isEmpty();
    }

    // ── skills: Arcane Archer ────────────────────────────────────────────────────

    @Test
    void arcaneArcher_withNeitherSkill_gainsArcanaAndNature_andPicksNone() {
        // The key may be left out; it is stored as an empty list so the choice counts as made.
        Change omitted = SubclassChoices.atLevelUp("Fighter", "Arcane Archer", 3, Choices.NONE, Choices.NONE,
                List.of("Athletics"));
        assertThat(omitted.choices().skills()).isEmpty();
        assertThat(omitted.skillsGained()).containsExactly("Arcana", "Nature");

        Change sentEmpty = SubclassChoices.atLevelUp("Fighter", "Arcane Archer", 3, Choices.NONE, skills(),
                List.of("Athletics"));
        assertThat(sentEmpty.choices().skills()).isEmpty();
        assertThat(sentEmpty.skillsGained()).containsExactly("Arcana", "Nature");
        assertThat(SubclassChoices.skillsOwed("Fighter", "Arcane Archer", List.of("Athletics"))).isZero();

        assertRefused(() -> SubclassChoices.atLevelUp("Fighter", "Arcane Archer", 3, Choices.NONE,
                skills("Acrobatics"), List.of("Athletics")), "Arcane Archer requires exactly 0 skill choices");
    }

    @Test
    void arcaneArcher_withOneOfTheTwo_picksOneFighterSkillInstead() {
        Change change = SubclassChoices.atLevelUp("Fighter", "Arcane Archer", 3, Choices.NONE,
                skills("animal handling"), List.of("Nature", "Athletics"));
        assertThat(change.choices().skills()).containsExactly("Animal Handling");
        assertThat(change.skillsGained()).containsExactly("Arcana", "Nature", "Animal Handling");
        assertThat(SubclassChoices.skillsOwed("Fighter", "Arcane Archer", List.of("Nature"))).isEqualTo(1);

        assertRefused(() -> SubclassChoices.atLevelUp("Fighter", "Arcane Archer", 3, Choices.NONE, Choices.NONE,
                List.of("Arcana")), "Arcane Archer requires exactly 1 skill choice");
        assertRefused(() -> SubclassChoices.atLevelUp("Fighter", "Arcane Archer", 3, Choices.NONE,
                skills("Acrobatics", "Survival"), List.of("Arcana")), "Arcane Archer requires exactly 1 skill choice");
        assertRefused(() -> SubclassChoices.atLevelUp("Fighter", "Arcane Archer", 3, Choices.NONE,
                skills("Athletics"), List.of("Arcana", "Athletics")), "'Athletics' is already a skill proficiency");
        // Arcana and Nature are given, never picked: they are not on the Fighter's list.
        assertRefused(() -> SubclassChoices.atLevelUp("Fighter", "Arcane Archer", 3, Choices.NONE,
                skills("Nature"), List.of("Arcana")), "'Nature' is not a skill Arcane Archer can choose");
    }

    @Test
    void arcaneArcher_withBoth_picksTwoFighterSkills() {
        Change change = SubclassChoices.atLevelUp("Fighter", "Arcane Archer", 3, Choices.NONE,
                skills("Perception", "Survival"), List.of("Arcana", "Nature"));
        assertThat(change.choices().skills()).containsExactly("Perception", "Survival");
        assertThat(change.skillsGained()).containsExactly("Arcana", "Nature", "Perception", "Survival");
        assertThat(SubclassChoices.skillsOwed("Fighter", "Arcane Archer", List.of("arcana", " Nature "))).isEqualTo(2);

        assertRefused(() -> SubclassChoices.atLevelUp("Fighter", "Arcane Archer", 3, Choices.NONE,
                skills("Perception"), List.of("Arcana", "Nature")), "Arcane Archer requires exactly 2 skill choices");
        assertRefused(() -> SubclassChoices.atLevelUp("Fighter", "Arcane Archer", 3, Choices.NONE, Choices.NONE,
                List.of("Arcana", "Nature")), "Arcane Archer requires exactly 2 skill choices");
        assertRefused(() -> SubclassChoices.atLevelUp("Fighter", "Arcane Archer", 3, Choices.NONE,
                skills("Perception", "perception"), List.of("Arcana", "Nature")), "'Perception' is chosen twice");
    }

    // ── arcaneShots ──────────────────────────────────────────────────────────────

    @Test
    void arcaneShots_mustBeRealOptions_noneTwice_exactlyTheCountForTheLevel() {
        Change change = SubclassChoices.atLevelUp("Fighter", "Arcane Archer", 3, Choices.NONE,
                shots("seeking shot", "Banishing Shot"), NO_SKILLS);
        assertThat(change.choices().arcaneShots()).containsExactly("Seeking Shot", "Banishing Shot");

        assertRefused(() -> SubclassChoices.atLevelUp("Fighter", "Arcane Archer", 3, Choices.NONE,
                shots("Seeking Shot", "Exploding Shot"), NO_SKILLS), "'Exploding Shot' is not an Arcane Shot option");
        assertRefused(() -> SubclassChoices.atLevelUp("Fighter", "Arcane Archer", 3, Choices.NONE,
                shots("Seeking Shot", "seeking shot"), NO_SKILLS), "'Seeking Shot' is chosen twice");
        assertRefused(() -> SubclassChoices.atLevelUp("Fighter", "Arcane Archer", 3, Choices.NONE,
                shots("Seeking Shot"), NO_SKILLS), "An Arcane Archer knows exactly 2 Arcane Shot options at level 3");
        assertRefused(() -> SubclassChoices.atLevelUp("Fighter", "Arcane Archer", 3, Choices.NONE,
                shots("Seeking Shot", "Shadow Shot", "Piercing Shot"), NO_SKILLS),
                "An Arcane Archer knows exactly 2 Arcane Shot options at level 3");
        assertRefused(() -> SubclassChoices.atLevelUp("Fighter", "Arcane Archer", 7, Choices.NONE,
                shots("Seeking Shot", "Shadow Shot"), NO_SKILLS),
                "An Arcane Archer knows exactly 3 Arcane Shot options at level 7");
    }

    @Test
    void arcaneShots_atALevelThatAddsAnOption_oneKnownOptionMayBeSwapped() {
        Choices known = new Choices(List.of(), List.of("Banishing Shot", "Seeking Shot"), null, null);
        // Learn one more and keep both.
        assertThat(SubclassChoices.atLevelUp("Fighter", "Arcane Archer", 7, known,
                shots("Banishing Shot", "Seeking Shot", "Shadow Shot"), NO_SKILLS).choices().arcaneShots())
                .containsExactly("Banishing Shot", "Seeking Shot", "Shadow Shot");
        // Learn one more and swap one.
        assertThat(SubclassChoices.atLevelUp("Fighter", "Arcane Archer", 7, known,
                shots("Banishing Shot", "Bursting Shot", "Shadow Shot"), NO_SKILLS).choices().arcaneShots())
                .containsExactly("Banishing Shot", "Bursting Shot", "Shadow Shot");
        // Swapping both is one too many.
        assertRefused(() -> SubclassChoices.atLevelUp("Fighter", "Arcane Archer", 7, known,
                shots("Grasping Shot", "Bursting Shot", "Shadow Shot"), NO_SKILLS),
                "Only one known Arcane Shot option can be replaced at level 7");
    }

    @Test
    void arcaneShots_atAnyOtherLevel_everyKnownOptionStays() {
        Choices known = new Choices(List.of(), List.of("Banishing Shot", "Seeking Shot"), null, null);
        assertThat(SubclassChoices.atLevelUp("Fighter", "Arcane Archer", 4, known,
                shots("Seeking Shot", "Banishing Shot"), NO_SKILLS).choices().arcaneShots())
                .containsExactly("Seeking Shot", "Banishing Shot");
        assertRefused(() -> SubclassChoices.atLevelUp("Fighter", "Arcane Archer", 4, known,
                shots("Banishing Shot", "Shadow Shot"), NO_SKILLS),
                "A known Arcane Shot option can only be replaced at a level that adds a new one");
    }

    @Test
    void arcaneShots_aShortStoredList_mayBeFilledUpAtAnyLevel() {
        // Nothing stored at level 5 (the option picker did not exist yet): pick both at 6.
        assertThat(SubclassChoices.atLevelUp("Fighter", "Arcane Archer", 6, skills(),
                shots("Piercing Shot", "Shadow Shot"), NO_SKILLS).choices().arcaneShots())
                .containsExactly("Piercing Shot", "Shadow Shot");
        // Two stored at level 8, where three are known: the third is added, the two stay.
        Choices two = new Choices(List.of(), List.of("Banishing Shot", "Seeking Shot"), null, null);
        assertThat(SubclassChoices.atLevelUp("Fighter", "Arcane Archer", 9, two,
                shots("Banishing Shot", "Seeking Shot", "Shadow Shot"), NO_SKILLS).choices().arcaneShots()).hasSize(3);
        assertRefused(() -> SubclassChoices.atLevelUp("Fighter", "Arcane Archer", 9, two,
                shots("Banishing Shot", "Bursting Shot", "Shadow Shot"), NO_SKILLS),
                "A known Arcane Shot option can only be replaced at a level that adds a new one");
    }

    @Test
    void arcaneShots_notSent_areLeftAsTheyAre() {
        Choices two = new Choices(List.of(), List.of("Banishing Shot", "Seeking Shot"), null, null);
        // Even at a level that adds an option: the choice may wait.
        assertThat(SubclassChoices.atLevelUp("Fighter", "Arcane Archer", 7, two, Choices.NONE, NO_SKILLS).choices())
                .isEqualTo(two);
        assertThat(SubclassChoices.atLevelUp("Fighter", "Arcane Archer", 3, Choices.NONE, Choices.NONE, NO_SKILLS)
                .choices().arcaneShots()).isNull();
    }

    // ── vestigeType, vestigeDomain ───────────────────────────────────────────────

    @Test
    void vestige_typeAndDomain_mustBeOptions() {
        Change change = SubclassChoices.atLevelUp("Warlock", "Vestige Patron", 3, Choices.NONE,
                vestige("celestial", " life "), NO_SKILLS);
        assertThat(change.choices()).isEqualTo(vestige("Celestial", "Life"));
        assertThat(change.skillsGained()).isEmpty();

        assertRefused(() -> SubclassChoices.atLevelUp("Warlock", "Vestige Patron", 3, Choices.NONE,
                vestige("Dragon", null), NO_SKILLS), "'Dragon' is not a Vestige type (Celestial, Fiend or Undead)");
        assertRefused(() -> SubclassChoices.atLevelUp("Warlock", "Vestige Patron", 3, Choices.NONE,
                vestige(null, "Twilight"), NO_SKILLS),
                "'Twilight' is not a Vestige domain (Life, Light, Trickery or War)");
    }

    @Test
    void vestige_theTypeMayChangeAtALevelUp_theDomainMayNot() {
        Choices stored = vestige("Celestial", "Life");
        assertThat(SubclassChoices.atLevelUp("Warlock", "Vestige Patron", 4, stored, vestige("Undead", null),
                NO_SKILLS).choices()).isEqualTo(vestige("Undead", "Life"));
        // The same domain again is not a change.
        assertThat(SubclassChoices.atLevelUp("Warlock", "Vestige Patron", 4, stored, vestige(null, "life"),
                NO_SKILLS).choices()).isEqualTo(stored);
        assertRefused(() -> SubclassChoices.atLevelUp("Warlock", "Vestige Patron", 4, stored, vestige(null, "War"),
                NO_SKILLS), "The Vestige domain cannot be changed once it is chosen");
    }

    @Test
    void vestige_notSent_isAccepted() {
        assertThat(SubclassChoices.atLevelUp("Warlock", "Vestige Patron", 3, Choices.NONE, Choices.NONE, NO_SKILLS)
                .choices()).isEqualTo(Choices.NONE);
        assertThat(SubclassChoices.atLevelUp("Warlock", "Vestige Patron", 6, vestige("Fiend", null), Choices.NONE,
                NO_SKILLS).choices()).isEqualTo(vestige("Fiend", null));
    }

    // ── A key turned required ────────────────────────────────────────────────────

    @Test
    void aKeyAddedToTheRequiredSet_mustBeCompleteAfterTheLevelUp() {
        Set<String> all = Set.of("skills", "arcaneShots", "vestigeType", "vestigeDomain");
        assertRefused(() -> SubclassChoices.atLevelUp("Fighter", "Arcane Archer", 3, Choices.NONE, Choices.NONE,
                NO_SKILLS, all), "An Arcane Archer knows exactly 2 Arcane Shot options at level 3");
        Choices two = new Choices(List.of(), List.of("Banishing Shot", "Seeking Shot"), null, null);
        // Complete for the level: nothing to send. Short at 7: the third is owed.
        assertThat(SubclassChoices.atLevelUp("Fighter", "Arcane Archer", 6, two, Choices.NONE, NO_SKILLS, all)
                .choices()).isEqualTo(two);
        assertRefused(() -> SubclassChoices.atLevelUp("Fighter", "Arcane Archer", 7, two, Choices.NONE, NO_SKILLS,
                all), "An Arcane Archer knows exactly 3 Arcane Shot options at level 7");

        assertRefused(() -> SubclassChoices.atLevelUp("Warlock", "Vestige Patron", 3, Choices.NONE,
                vestige(null, "War"), NO_SKILLS, all), "A Vestige Patron must choose a Vestige type");
        assertRefused(() -> SubclassChoices.atLevelUp("Warlock", "Vestige Patron", 3, Choices.NONE,
                vestige("Fiend", null), NO_SKILLS, all), "A Vestige Patron must choose a Vestige domain");
        assertThat(SubclassChoices.atLevelUp("Warlock", "Vestige Patron", 4, vestige("Fiend", "War"), Choices.NONE,
                NO_SKILLS, all).choices()).isEqualTo(vestige("Fiend", "War"));

        // And with nothing required, even the skill may wait.
        assertThat(SubclassChoices.atLevelUp("Cleric", "Arcana Domain", 3, Choices.NONE, Choices.NONE, NO_SKILLS,
                Set.of()).choices()).isEqualTo(Choices.NONE);
    }

    // ── A direct edit ────────────────────────────────────────────────────────────

    @Test
    void direct_hasNoSwapLimit_andMayChangeTheDomain() {
        Choices two = new Choices(List.of(), List.of("Banishing Shot", "Seeking Shot"), null, null);
        assertThat(SubclassChoices.direct("Fighter", "Arcane Archer", 4, two, shots("Shadow Shot", "Piercing Shot"),
                NO_SKILLS).choices().arcaneShots()).containsExactly("Shadow Shot", "Piercing Shot");
        assertRefused(() -> SubclassChoices.direct("Fighter", "Arcane Archer", 4, two, shots("Shadow Shot"),
                NO_SKILLS), "An Arcane Archer knows exactly 2 Arcane Shot options at level 4");

        assertThat(SubclassChoices.direct("Warlock", "Vestige Patron", 5, vestige("Fiend", "Life"),
                vestige(null, "War"), NO_SKILLS).choices()).isEqualTo(vestige("Fiend", "War"));
        assertRefused(() -> SubclassChoices.direct("Warlock", "Vestige Patron", 5, Choices.NONE,
                vestige("Angel", null), NO_SKILLS), "'Angel' is not a Vestige type (Celestial, Fiend or Undead)");
    }

    @Test
    void direct_doesNotAskForWhatIsNotSent() {
        // An Arcane Archer whose skills were never settled sets its shots: the skills are left alone.
        Change change = SubclassChoices.direct("Fighter", "Arcane Archer", 4, Choices.NONE,
                shots("Shadow Shot", "Piercing Shot"), NO_SKILLS);
        assertThat(change.choices().skills()).isNull();
        assertThat(change.skillsGained()).isEmpty();
    }

    @Test
    void direct_settlesSkillsThatWereNeverChosen() {
        Change cleric = SubclassChoices.direct("Cleric", "Arcana Domain", 8, Choices.NONE, skills("Religion"),
                List.of("Insight"));
        assertThat(cleric.choices()).isEqualTo(skills("Religion"));
        assertThat(cleric.skillsGained()).containsExactly("Religion");

        Change archer = SubclassChoices.direct("Fighter", "Arcane Archer", 8, Choices.NONE, skills(),
                List.of("Athletics"));
        assertThat(archer.choices().skills()).isEmpty();
        assertThat(archer.skillsGained()).containsExactly("Arcana", "Nature");

        assertRefused(() -> SubclassChoices.direct("Cleric", "Arcana Domain", 8, Choices.NONE, skills("Insight"),
                List.of("Insight")), "'Insight' is already a skill proficiency");
    }

    @Test
    void direct_replacingStoredSkills_releasesTheOldPicks() {
        // Insight came from the stored pick, so it is free again and may even be picked again.
        Change replaced = SubclassChoices.direct("Cleric", "Arcana Domain", 8, skills("Insight"),
                skills("Religion"), List.of("Insight", "Medicine"));
        assertThat(replaced.choices()).isEqualTo(skills("Religion"));
        assertThat(replaced.skillsGained()).containsExactly("Religion");
        assertThat(replaced.skillsLost()).containsExactly("Insight");

        Change same = SubclassChoices.direct("Cleric", "Arcana Domain", 8, skills("Insight"), skills("Insight"),
                List.of("Insight"));
        assertThat(same.skillsLost()).isEmpty();
        assertThat(same.choices()).isEqualTo(skills("Insight"));

        assertRefused(() -> SubclassChoices.direct("Cleric", "Arcana Domain", 8, skills("Insight"),
                skills("Medicine"), List.of("Insight", "Medicine")), "'Medicine' is already a skill proficiency");

        // An Arcane Archer replaces as many as it had picked: Arcana and Nature are both its own by now.
        Choices stored = skills("Perception");
        Change archer = SubclassChoices.direct("Fighter", "Arcane Archer", 8, stored, skills("Survival"),
                List.of("Arcana", "Nature", "Perception"));
        assertThat(archer.choices().skills()).containsExactly("Survival");
        assertThat(archer.skillsLost()).containsExactly("Perception");
        assertThat(archer.skillsGained()).containsExactly("Survival");
        assertRefused(() -> SubclassChoices.direct("Fighter", "Arcane Archer", 8, stored,
                skills("Survival", "Acrobatics"), List.of("Arcana", "Nature", "Perception")),
                "Arcane Archer requires exactly 1 skill choice");
        assertRefused(() -> SubclassChoices.direct("Fighter", "Arcane Archer", 8, skills(), skills("Survival"),
                List.of("Arcana", "Nature")), "Arcane Archer requires exactly 0 skill choices");
    }

    @Test
    void isComplete_saysWhenAStoredKeyIsAllTheLevelAsksFor() {
        Choices two = new Choices(List.of(), List.of("Banishing Shot", "Seeking Shot"), null, null);
        assertThat(SubclassChoices.isComplete("skills", two, "Fighter", "Arcane Archer", 6)).isTrue();
        assertThat(SubclassChoices.isComplete("arcaneShots", two, "Fighter", "Arcane Archer", 6)).isTrue();
        assertThat(SubclassChoices.isComplete("arcaneShots", two, "Fighter", "Arcane Archer", 7)).isFalse();
        assertThat(SubclassChoices.isComplete("skills", Choices.NONE, "Fighter", "Arcane Archer", 6)).isFalse();
        assertThat(SubclassChoices.isComplete("arcaneShots", Choices.NONE, "Fighter", "Arcane Archer", 6)).isFalse();

        assertThat(SubclassChoices.isComplete("vestigeDomain", vestige(null, "War"), "Warlock", "Vestige Patron", 3))
                .isTrue();
        assertThat(SubclassChoices.isComplete("vestigeType", vestige(null, "War"), "Warlock", "Vestige Patron", 3))
                .isFalse();
        // A key the character does not have is never complete.
        assertThat(SubclassChoices.isComplete("vestigeDomain", vestige(null, "War"), "Wizard", "Evoker", 3)).isFalse();
    }

    // ── Spells the player picks ──────────────────────────────────────────────────

    @Test
    void spellPicksFor_theThreeFeatures_atTheirLevels() {
        assertThat(SubclassChoices.spellPicksFor("Cleric", "Arcana Domain", 2)).isEmpty();
        List<SpellPicks> cleric = SubclassChoices.spellPicksFor("Cleric", "Arcana Domain", 3);
        assertThat(cleric).extracting(SpellPicks::feature).containsExactly("Student of Arcana");
        assertThat(cleric.get(0).spellLevels()).containsExactly(0, 0);
        assertThat(cleric.get(0).replaceable()).isTrue();
        assertThat(cleric.get(0).message()).isEqualTo("Student of Arcana requires exactly two Wizard cantrips");
        assertThat(SubclassChoices.spellPicksFor("Cleric", "Arcana Domain", 16)).hasSize(1);

        List<SpellPicks> high = SubclassChoices.spellPicksFor("cleric", "arcana domain", 17);
        assertThat(high).extracting(SpellPicks::feature).containsExactly("Student of Arcana", "Magical Mastery");
        assertThat(high.get(1).spellLevels()).containsExactly(6, 7, 8, 9);
        assertThat(high.get(1).count()).isEqualTo(4);
        assertThat(high.get(1).replaceable()).isTrue();
        assertThat(high.get(1).message())
                .isEqualTo("Magical Mastery requires four Wizard spells, one each of level 6, 7, 8 and 9");

        List<SpellPicks> archer = SubclassChoices.spellPicksFor("Fighter", "Arcane Archer", 3);
        assertThat(archer).extracting(SpellPicks::feature).containsExactly("Arcane Archer Lore");
        assertThat(archer.get(0).spellLevels()).containsExactly(0);
        assertThat(archer.get(0).replaceable()).isFalse();
        assertThat(archer.get(0).message())
                .isEqualTo("Arcane Archer Lore requires one cantrip, Druidcraft or Prestidigitation");

        assertThat(SubclassChoices.spellPicksFor("Fighter", "Arcane Archer", 2)).isEmpty();
        assertThat(SubclassChoices.spellPicksFor("Warlock", "Vestige Patron", 20)).isEmpty();
        assertThat(SubclassChoices.spellPicksFor("Cleric", "Life Domain", 20)).isEmpty();
        assertThat(SubclassChoices.spellPicksFor("Wizard", "Necromancer", 20)).isEmpty();
    }

    @Test
    void spellPicks_checkLevelsAndNames() {
        SpellPicks mastery = SubclassChoices.spellPicksFor("Cleric", "Arcana Domain", 17).get(1);
        assertThat(mastery.satisfiedBy(List.of(9, 6, 8, 7))).isTrue();
        assertThat(mastery.satisfiedBy(List.of(6, 7, 8))).isFalse();
        assertThat(mastery.satisfiedBy(List.of(6, 6, 8, 9))).isFalse();
        assertThat(mastery.satisfiedBy(List.of(6, 7, 8, 9, 9))).isFalse();
        assertThat(mastery.allows("Any Spell At All")).isTrue();

        SpellPicks cantrips = SubclassChoices.spellPicksFor("Cleric", "Arcana Domain", 3).get(0);
        assertThat(cantrips.satisfiedBy(List.of(0, 0))).isTrue();
        assertThat(cantrips.satisfiedBy(List.of(0))).isFalse();
        assertThat(cantrips.satisfiedBy(List.of(0, 1))).isFalse();

        SpellPicks lore = SubclassChoices.spellPicksFor("Fighter", "Arcane Archer", 3).get(0);
        assertThat(lore.allows("druidcraft")).isTrue();
        assertThat(lore.allows(" Prestidigitation ")).isTrue();
        assertThat(lore.allows("Fire Bolt")).isFalse();
    }
}
