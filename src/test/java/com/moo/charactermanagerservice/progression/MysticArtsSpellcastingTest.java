package com.moo.charactermanagerservice.progression;

import com.moo.charactermanagerservice.progression.FeatCatalog.Candidate;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Spellcasting of the Warrior of the Mystic Arts (Arcana Unleashed): its third-caster table
 * at every Monk level, the subclass-aware lookups of {@link ClassProgression} that read it, the
 * feat prerequisite that follows, and the Mystic Focus numbers. And the other side: no other
 * character gets anything from it. The client pins the same numbers in
 * {@code utils/mystic-arts.spec.ts}.
 */
class MysticArtsSpellcastingTest {

    private static final String MONK = "Monk";
    private static final String MYSTIC_ARTS = "Warrior of the Mystic Arts";

    /** The book's table, Monk level 1 to 20: slots of spell level 1 / 2 / 3 / 4. */
    private static final int[][] SLOTS = {
            {0, 0, 0, 0}, {0, 0, 0, 0},
            {2, 0, 0, 0},
            {3, 0, 0, 0}, {3, 0, 0, 0}, {3, 0, 0, 0},
            {4, 2, 0, 0}, {4, 2, 0, 0}, {4, 2, 0, 0},
            {4, 3, 0, 0}, {4, 3, 0, 0}, {4, 3, 0, 0},
            {4, 3, 2, 0}, {4, 3, 2, 0}, {4, 3, 2, 0},
            {4, 3, 3, 0}, {4, 3, 3, 0}, {4, 3, 3, 0},
            {4, 3, 3, 1}, {4, 3, 3, 1},
    };

    /** The Prepared Spells column, Monk level 1 to 20. */
    private static final int[] PREPARED = {0, 0, 3, 4, 4, 4, 5, 6, 6, 7, 8, 8, 9, 10, 10, 11, 11, 11, 12, 13};

    /** Cantrips known, Monk level 1 to 20: two from level 3, a third from level 10. */
    private static final int[] CANTRIPS = {0, 0, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3};

    private static Map<Integer, Integer> row(int[] slots) {
        Map<Integer, Integer> row = new LinkedHashMap<>();
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] > 0) row.put(i + 1, slots[i]);
        }
        return row;
    }

    // ── The table, level by level ────────────────────────────────────────────────

    @Test
    void spellSlots_followTheBooksTable_atEveryMonkLevel() {
        for (int level = 1; level <= 20; level++) {
            assertThat(ClassProgression.spellSlotsFor(MONK, MYSTIC_ARTS, level)).as("level " + level)
                    .isEqualTo(row(SLOTS[level - 1]));
        }
        // Spot rows, written out: nothing below 3, a second spell level at 7, a third at 13, a fourth at 19.
        assertThat(ClassProgression.spellSlotsFor(MONK, MYSTIC_ARTS, 2)).isEmpty();
        assertThat(ClassProgression.spellSlotsFor(MONK, MYSTIC_ARTS, 3)).isEqualTo(Map.of(1, 2));
        assertThat(ClassProgression.spellSlotsFor(MONK, MYSTIC_ARTS, 6)).isEqualTo(Map.of(1, 3));
        assertThat(ClassProgression.spellSlotsFor(MONK, MYSTIC_ARTS, 7)).isEqualTo(Map.of(1, 4, 2, 2));
        assertThat(ClassProgression.spellSlotsFor(MONK, MYSTIC_ARTS, 10)).isEqualTo(Map.of(1, 4, 2, 3));
        assertThat(ClassProgression.spellSlotsFor(MONK, MYSTIC_ARTS, 13)).isEqualTo(Map.of(1, 4, 2, 3, 3, 2));
        assertThat(ClassProgression.spellSlotsFor(MONK, MYSTIC_ARTS, 16)).isEqualTo(Map.of(1, 4, 2, 3, 3, 3));
        assertThat(ClassProgression.spellSlotsFor(MONK, MYSTIC_ARTS, 19)).isEqualTo(Map.of(1, 4, 2, 3, 3, 3, 4, 1));
        assertThat(ClassProgression.spellSlotsFor(MONK, MYSTIC_ARTS, 20).keySet()).containsExactly(1, 2, 3, 4);
    }

    @Test
    void preparedSpells_followThePreparedSpellsColumn_atEveryMonkLevel() {
        for (int level = 1; level <= 20; level++) {
            assertThat(ClassProgression.preparedSpellsFor(MONK, MYSTIC_ARTS, level)).as("level " + level)
                    .isEqualTo(PREPARED[level - 1]);
        }
    }

    @Test
    void cantrips_areTwoFromMonk3_andThreeFromMonk10() {
        for (int level = 1; level <= 20; level++) {
            assertThat(ClassProgression.cantripsKnownFor(MONK, MYSTIC_ARTS, level)).as("level " + level)
                    .isEqualTo(CANTRIPS[level - 1]);
        }
    }

    @Test
    void theLevelIsClamped_andThereIsNothingBelowLevelOne() {
        assertThat(ClassProgression.spellSlotsFor(MONK, MYSTIC_ARTS, 25))
                .isEqualTo(ClassProgression.spellSlotsFor(MONK, MYSTIC_ARTS, 20));
        assertThat(ClassProgression.preparedSpellsFor(MONK, MYSTIC_ARTS, 25)).isEqualTo(13);
        assertThat(ClassProgression.cantripsKnownFor(MONK, MYSTIC_ARTS, 25)).isEqualTo(3);
        assertThat(ClassProgression.spellSlotsFor(MONK, MYSTIC_ARTS, 0)).isEmpty();
        assertThat(ClassProgression.preparedSpellsFor(MONK, MYSTIC_ARTS, 0)).isZero();
        assertThat(ClassProgression.cantripsKnownFor(MONK, MYSTIC_ARTS, 0)).isZero();
    }

    @Test
    void theSubclassIsRecognisedWhateverTheCaseOrSpacing_andOnlyOnAMonk() {
        assertThat(ClassProgression.spellSlotsFor(" monk ", "  warrior of the mystic arts ", 7))
                .isEqualTo(Map.of(1, 4, 2, 2));
        assertThat(ClassProgression.isCaster("MONK", "WARRIOR OF THE MYSTIC ARTS")).isTrue();
        // The name on another class is just a name: the class's own answer stands.
        assertThat(ClassProgression.spellSlotsFor("Fighter", MYSTIC_ARTS, 7)).isEmpty();
        assertThat(ClassProgression.isCaster("Fighter", MYSTIC_ARTS)).isFalse();
        assertThat(ClassProgression.spellSlotsFor("Wizard", MYSTIC_ARTS, 3))
                .isEqualTo(ClassProgression.spellSlotsFor("Wizard", 3));
        assertThat(MysticArtsSpellcasting.applies(null, MYSTIC_ARTS)).isFalse();
        assertThat(MysticArtsSpellcasting.applies(MONK, null)).isFalse();
    }

    // ── Everyone else is exactly as before ───────────────────────────────────────

    @Test
    void theClassOnlyLookups_stillSayAMonkHasNoSpellcasting() {
        for (int level = 1; level <= 20; level++) {
            assertThat(ClassProgression.spellSlotsFor("monk", level)).as("level " + level).isEmpty();
            assertThat(ClassProgression.preparedSpellsFor("monk", level)).as("level " + level).isZero();
            assertThat(ClassProgression.cantripsKnownFor("monk", level)).as("level " + level).isZero();
        }
        assertThat(ClassProgression.isCaster("monk")).isFalse();
    }

    @Test
    void aMonkOfAnotherSubclass_orOfNone_hasNoSpellcasting() {
        for (String subclass : new String[] {null, "", "Warrior of the Open Hand", "Warrior of Shadow",
                "Warrior of Mercy", "Warrior of the Elements", "Warrior of Mystic Brews"}) {
            for (int level = 1; level <= 20; level++) {
                assertThat(ClassProgression.spellSlotsFor(MONK, subclass, level)).as(subclass + " " + level).isEmpty();
                assertThat(ClassProgression.preparedSpellsFor(MONK, subclass, level)).as(subclass + " " + level).isZero();
                assertThat(ClassProgression.cantripsKnownFor(MONK, subclass, level)).as(subclass + " " + level).isZero();
                assertThat(ClassProgression.hasSpellcastingOrPactMagic(MONK, subclass, level)).isFalse();
            }
            assertThat(ClassProgression.isCaster(MONK, subclass)).as(String.valueOf(subclass)).isFalse();
        }
    }

    @Test
    void eldritchKnightAndArcaneTrickster_stillHaveNoSlots_andDoNotQualify() {
        String[][] thirdCasters = {{"Fighter", "Eldritch Knight"}, {"Rogue", "Arcane Trickster"}};
        for (String[] c : thirdCasters) {
            for (int level = 1; level <= 20; level++) {
                String as = c[1] + " " + level;
                assertThat(ClassProgression.spellSlotsFor(c[0], c[1], level)).as(as).isEmpty();
                assertThat(ClassProgression.preparedSpellsFor(c[0], c[1], level)).as(as).isZero();
                assertThat(ClassProgression.cantripsKnownFor(c[0], c[1], level)).as(as).isZero();
                assertThat(ClassProgression.hasSpellcastingOrPactMagic(c[0], c[1], level)).as(as).isFalse();
                assertThat(ClassProgression.highestSpellSlotLevel(c[0], c[1], level)).as(as).isZero();
            }
            assertThat(ClassProgression.isCaster(c[0], c[1])).as(c[1]).isFalse();
        }
    }

    @Test
    void everyOtherClass_getsItsOwnAnswerThroughTheSubclassAwareLookups() {
        String[][] characters = {
                {"Wizard", "Evoker"}, {"Cleric", "Arcana Domain"}, {"Sorcerer", "Draconic Sorcery"},
                {"Bard", null}, {"Druid", "Circle of the Moon"}, {"Warlock", "Vestige Patron"},
                {"Paladin", "Oath of Devotion"}, {"Ranger", "Hunter"}, {"Fighter", "Arcane Archer"},
                {"Fighter", "Champion"}, {"Barbarian", null}, {"Rogue", "Thief"}};
        for (String[] c : characters) {
            for (int level = 1; level <= 20; level++) {
                String as = c[0] + "/" + c[1] + " " + level;
                assertThat(ClassProgression.spellSlotsFor(c[0], c[1], level)).as(as)
                        .isEqualTo(ClassProgression.spellSlotsFor(c[0], level));
                assertThat(ClassProgression.preparedSpellsFor(c[0], c[1], level)).as(as)
                        .isEqualTo(ClassProgression.preparedSpellsFor(c[0], level));
                assertThat(ClassProgression.cantripsKnownFor(c[0], c[1], level)).as(as)
                        .isEqualTo(ClassProgression.cantripsKnownFor(c[0], level));
            }
            assertThat(ClassProgression.isCaster(c[0], c[1])).as(c[0]).isEqualTo(ClassProgression.isCaster(c[0]));
        }
    }

    // ── The feat prerequisite: the Spellcasting or Pact Magic feature ────────────

    @Test
    void theMonkQualifiesFromMonk3_withItsHighestSlotLevel() {
        int[] highest = {0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 4, 4};
        for (int level = 1; level <= 20; level++) {
            assertThat(ClassProgression.hasSpellcastingOrPactMagic(MONK, MYSTIC_ARTS, level)).as("level " + level)
                    .isEqualTo(level >= 3);
            assertThat(ClassProgression.highestSpellSlotLevel(MONK, MYSTIC_ARTS, level)).as("level " + level)
                    .isEqualTo(highest[level - 1]);
        }
    }

    @Test
    void theSchoolAdeptFeats_areOfferedToTheMysticArtsMonk_andNotToAnotherMonk() {
        Candidate mystic = new Candidate(MONK, MYSTIC_ARTS, 4, Map.of(), List.of());
        Candidate openHand = new Candidate(MONK, "Warrior of the Open Hand", 4, Map.of(), List.of());
        Candidate eldritchKnight = new Candidate("Fighter", "Eldritch Knight", 4, Map.of(), List.of());

        assertThat(FeatCatalog.featOptions(mystic)).contains("Evocation Adept", "Abjuration Adept", "Spell Subterfuge");
        assertThat(FeatCatalog.matchFeat("Evocation Adept", mystic)).isEqualTo("Evocation Adept");
        assertThat(FeatCatalog.unmetPrerequisite("Evocation Adept", mystic)).isNull();

        for (Candidate other : List.of(openHand, eldritchKnight)) {
            assertThat(FeatCatalog.featOptions(other)).as(other.subclass())
                    .doesNotContain("Evocation Adept", "Abjuration Adept", "Spell Subterfuge");
            assertThat(FeatCatalog.unmetPrerequisite("Evocation Adept", other)).as(other.subclass())
                    .isEqualTo("requires the Spellcasting or Pact Magic feature");
        }
    }

    @Test
    void aSchoolAdeptsSpells_arriveWithTheMonksSlotLevels() {
        // One spell per spell slot level the character has: one row at Monk 4, two from Monk 7.
        assertThat(FeatSpellGrants.grantsFor("Evocation Adept", MONK, MYSTIC_ARTS, 4)).hasSize(1);
        assertThat(FeatSpellGrants.grantsFor("Evocation Adept", MONK, MYSTIC_ARTS, 7)).hasSize(2);
        assertThat(FeatSpellGrants.grantsFor("Evocation Adept", MONK, MYSTIC_ARTS, 19)).hasSize(4);
        assertThat(FeatSpellGrants.grantsFor("Evocation Adept", MONK, "Warrior of the Open Hand", 19)).isEmpty();
    }

    // ── Mystic Focus numbers ─────────────────────────────────────────────────────

    @Test
    void mysticFocus_costsAndMinimumLevels_areTheBooksRecoveringSpellSlotsTable() {
        int[][] table = {{1, 2, 6}, {2, 3, 7}, {3, 5, 13}, {4, 6, 19}}; // slot level, cost, minimum Monk level
        for (int[] r : table) {
            assertThat(MysticArtsSpellcasting.focusCostFor(r[0])).as("cost of level " + r[0]).isEqualTo(r[1]);
            assertThat(MysticArtsSpellcasting.minLevelToRecover(r[0])).as("level for " + r[0]).isEqualTo(r[2]);
            assertThat(MysticArtsSpellcasting.focusRegainedFor(r[0])).as("regained from " + r[0]).isEqualTo(r[0]);
            assertThat(MysticArtsSpellcasting.isFocusSlotLevel(r[0])).isTrue();
        }
        assertThat(MysticArtsSpellcasting.isFocusSlotLevel(0)).isFalse();
        assertThat(MysticArtsSpellcasting.isFocusSlotLevel(5)).isFalse();
        assertThat(MysticArtsSpellcasting.isFocusSlotLevel(null)).isFalse();
        assertThat(MysticArtsSpellcasting.MYSTIC_FOCUS_LEVEL).isEqualTo(6);
        assertThat(MysticArtsSpellcasting.SPELLCASTING_LEVEL).isEqualTo(3);
        assertThat(MysticArtsSpellcasting.MAX_SLOT_LEVEL).isEqualTo(4);
    }
}
