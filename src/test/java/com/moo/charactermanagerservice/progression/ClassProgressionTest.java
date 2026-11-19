package com.moo.charactermanagerservice.progression;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The spell-slot tables, row by row: full casters, warlock pact magic and the
 * half casters (Paladin, Ranger). Rows are 2024 PHB / SRD 5.2.
 */
class ClassProgressionTest {

    // --- full casters ---

    @Test
    void spellSlotsFor_fullCaster_spotRows() {
        assertThat(ClassProgression.spellSlotsFor("wizard", 1)).isEqualTo(Map.of(1, 2));
        assertThat(ClassProgression.spellSlotsFor("cleric", 3)).isEqualTo(Map.of(1, 4, 2, 2));
        assertThat(ClassProgression.spellSlotsFor("bard", 5)).isEqualTo(Map.of(1, 4, 2, 3, 3, 2));
        assertThat(ClassProgression.spellSlotsFor("druid", 9))
                .isEqualTo(Map.of(1, 4, 2, 3, 3, 3, 4, 3, 5, 1));
        assertThat(ClassProgression.spellSlotsFor("sorcerer", 20))
                .isEqualTo(Map.of(1, 4, 2, 3, 3, 3, 4, 3, 5, 3, 6, 2, 7, 2, 8, 1, 9, 1));
    }

    @Test
    void spellSlotsFor_everyFullCasterSharesTheTable() {
        Map<Integer, Integer> wizard = ClassProgression.spellSlotsFor("wizard", 11);
        assertThat(wizard).isEqualTo(Map.of(1, 4, 2, 3, 3, 3, 4, 3, 5, 2, 6, 1));
        for (String clazz : new String[] {"bard", "cleric", "druid", "sorcerer"}) {
            assertThat(ClassProgression.spellSlotsFor(clazz, 11)).as(clazz).isEqualTo(wizard);
        }
    }

    // --- warlock pact magic: one slot level, a few slots ---

    @Test
    void spellSlotsFor_warlock_pactRows() {
        assertThat(ClassProgression.spellSlotsFor("warlock", 1)).isEqualTo(Map.of(1, 1));
        assertThat(ClassProgression.spellSlotsFor("warlock", 2)).isEqualTo(Map.of(1, 2));
        assertThat(ClassProgression.spellSlotsFor("warlock", 3)).isEqualTo(Map.of(2, 2));
        assertThat(ClassProgression.spellSlotsFor("warlock", 5)).isEqualTo(Map.of(3, 2));
        assertThat(ClassProgression.spellSlotsFor("warlock", 9)).isEqualTo(Map.of(5, 2));
        assertThat(ClassProgression.spellSlotsFor("warlock", 11)).isEqualTo(Map.of(5, 3));
        assertThat(ClassProgression.spellSlotsFor("warlock", 17)).isEqualTo(Map.of(5, 4));
        assertThat(ClassProgression.spellSlotsFor("warlock", 20)).isEqualTo(Map.of(5, 4));
    }

    // --- half casters: Paladin and Ranger ---

    @Test
    void spellSlotsFor_halfCaster_rows() {
        assertThat(ClassProgression.spellSlotsFor("paladin", 1)).isEqualTo(Map.of(1, 2));
        assertThat(ClassProgression.spellSlotsFor("paladin", 2)).isEqualTo(Map.of(1, 2));
        assertThat(ClassProgression.spellSlotsFor("paladin", 3)).isEqualTo(Map.of(1, 3));
        assertThat(ClassProgression.spellSlotsFor("paladin", 5)).isEqualTo(Map.of(1, 4, 2, 2));
        assertThat(ClassProgression.spellSlotsFor("paladin", 7)).isEqualTo(Map.of(1, 4, 2, 3));
        assertThat(ClassProgression.spellSlotsFor("paladin", 9)).isEqualTo(Map.of(1, 4, 2, 3, 3, 2));
        assertThat(ClassProgression.spellSlotsFor("paladin", 11)).isEqualTo(Map.of(1, 4, 2, 3, 3, 3));
        assertThat(ClassProgression.spellSlotsFor("paladin", 13))
                .isEqualTo(Map.of(1, 4, 2, 3, 3, 3, 4, 1));
        assertThat(ClassProgression.spellSlotsFor("paladin", 15))
                .isEqualTo(Map.of(1, 4, 2, 3, 3, 3, 4, 2));
        assertThat(ClassProgression.spellSlotsFor("paladin", 17))
                .isEqualTo(Map.of(1, 4, 2, 3, 3, 3, 4, 3, 5, 1));
        assertThat(ClassProgression.spellSlotsFor("paladin", 19))
                .isEqualTo(Map.of(1, 4, 2, 3, 3, 3, 4, 3, 5, 2));
        assertThat(ClassProgression.spellSlotsFor("paladin", 20))
                .isEqualTo(Map.of(1, 4, 2, 3, 3, 3, 4, 3, 5, 2));
    }

    @Test
    void spellSlotsFor_rangerSharesTheHalfCasterTable() {
        for (int level = 1; level <= ClassProgression.MAX_LEVEL; level++) {
            assertThat(ClassProgression.spellSlotsFor("ranger", level)).as("level " + level)
                    .isEqualTo(ClassProgression.spellSlotsFor("paladin", level));
        }
    }

    @Test
    void spellSlotsFor_halfCaster_evenLevelsRepeatTheOddLevelBelow() {
        // The half-caster table only moves on odd levels (and at level 2 → 3).
        for (int level = 5; level < ClassProgression.MAX_LEVEL; level += 2) {
            assertThat(ClassProgression.spellSlotsFor("ranger", level + 1)).as("level " + (level + 1))
                    .isEqualTo(ClassProgression.spellSlotsFor("ranger", level));
        }
    }

    @Test
    void halfCastersHaveSlots_butAreStillNotCastersForSpellChoices() {
        // isCaster answers "does the level-up offer cantrips and spells", which the app does
        // not model for half casters yet.
        assertThat(ClassProgression.isCaster("paladin")).isFalse();
        assertThat(ClassProgression.isCaster("ranger")).isFalse();
        assertThat(ClassProgression.preparedSpellsFor("paladin", 5)).isZero();
        assertThat(ClassProgression.cantripsKnownFor("ranger", 5)).isZero();
        assertThat(ClassProgression.isCaster("wizard")).isTrue();
        assertThat(ClassProgression.isCaster("warlock")).isTrue();
    }

    // --- everything else ---

    @Test
    void spellSlotsFor_classWithoutSlots_isEmpty() {
        for (String clazz : new String[] {"fighter", "barbarian", "monk", "rogue", "artificer", ""}) {
            assertThat(ClassProgression.spellSlotsFor(clazz, 10)).as(clazz).isEmpty();
        }
        assertThat(ClassProgression.spellSlotsFor(null, 10)).isEmpty();
    }

    @Test
    void spellSlotsFor_ignoresCaseAndSurroundingWhitespace() {
        assertThat(ClassProgression.spellSlotsFor("  Paladin ", 5)).isEqualTo(Map.of(1, 4, 2, 2));
        assertThat(ClassProgression.spellSlotsFor("RANGER", 5)).isEqualTo(Map.of(1, 4, 2, 2));
        assertThat(ClassProgression.spellSlotsFor(" Wizard", 3)).isEqualTo(Map.of(1, 4, 2, 2));
        assertThat(ClassProgression.spellSlotsFor("WarLock\t", 3)).isEqualTo(Map.of(2, 2));
    }

    @Test
    void spellSlotsFor_clampsTheLevel_andHasNothingBelowLevelOne() {
        assertThat(ClassProgression.spellSlotsFor("paladin", 25))
                .isEqualTo(ClassProgression.spellSlotsFor("paladin", 20));
        assertThat(ClassProgression.spellSlotsFor("wizard", 99))
                .isEqualTo(ClassProgression.spellSlotsFor("wizard", 20));
        assertThat(ClassProgression.spellSlotsFor("paladin", 0)).isEmpty();
    }

    @Test
    void spellSlotsFor_listsSpellLevelsInOrder() {
        assertThat(ClassProgression.spellSlotsFor("paladin", 17).keySet()).containsExactly(1, 2, 3, 4, 5);
        assertThat(ClassProgression.spellSlotsFor("wizard", 17).keySet())
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9);
    }
}
