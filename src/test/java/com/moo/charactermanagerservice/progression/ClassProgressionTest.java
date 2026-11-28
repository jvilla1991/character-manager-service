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
    void preparedSpellsFor_halfCasters_followThePreparedSpellsColumn() {
        // 2024 PHB Paladin / Ranger Features tables, levels 1-20.
        int[] expected = {2, 3, 4, 5, 6, 6, 7, 7, 9, 9, 10, 10, 11, 11, 12, 12, 14, 14, 15, 15};
        for (int level = 1; level <= ClassProgression.MAX_LEVEL; level++) {
            assertThat(ClassProgression.preparedSpellsFor("paladin", level)).as("paladin " + level)
                    .isEqualTo(expected[level - 1]);
            assertThat(ClassProgression.preparedSpellsFor("Ranger", level)).as("ranger " + level)
                    .isEqualTo(expected[level - 1]);
        }
        assertThat(ClassProgression.preparedSpellsFor("paladin", 25)).isEqualTo(15);
        assertThat(ClassProgression.preparedSpellsFor("ranger", 0)).isZero();
    }

    @Test
    void halfCasters_areCasters_withNoClassCantrips() {
        assertThat(ClassProgression.isCaster("paladin")).isTrue();
        assertThat(ClassProgression.isCaster(" Ranger ")).isTrue();
        assertThat(ClassProgression.isCaster("wizard")).isTrue();
        assertThat(ClassProgression.isCaster("warlock")).isTrue();
        assertThat(ClassProgression.isCaster("fighter")).isFalse();
        assertThat(ClassProgression.isCaster(null)).isFalse();
        // The class teaches no cantrips; a cantrip Fighting Style adds them at level 2.
        for (int level = 1; level <= ClassProgression.MAX_LEVEL; level++) {
            assertThat(ClassProgression.cantripsKnownFor("paladin", level)).as("paladin " + level).isZero();
            assertThat(ClassProgression.cantripsKnownFor("ranger", level)).as("ranger " + level).isZero();
        }
    }

    @Test
    void preparedSpellsFor_nonCasters_isZero() {
        for (String clazz : new String[] {"fighter", "barbarian", "monk", "rogue", "artificer", ""}) {
            assertThat(ClassProgression.preparedSpellsFor(clazz, 10)).as(clazz).isZero();
        }
        assertThat(ClassProgression.preparedSpellsFor(null, 10)).isZero();
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

    // --- the subclass catalog: Arcana Unleashed ---

    @Test
    void subclassCatalog_carriesTheEightArcanaUnleashedSubclasses() {
        assertThat(ClassProgression.subclassesFor("cleric")).containsExactly(
                "Arcana Domain", "Life Domain", "Light Domain", "Trickery Domain", "Twilight Domain", "War Domain");
        assertThat(ClassProgression.subclassesFor("fighter")).containsExactly(
                "Arcane Archer", "Battle Master", "Champion", "Eldritch Knight", "Psi Warrior");
        assertThat(ClassProgression.subclassesFor("monk")).containsExactly(
                "Warrior of Mercy", "Warrior of Shadow", "Warrior of the Elements", "Warrior of the Mystic Arts",
                "Warrior of the Open Hand");
        assertThat(ClassProgression.subclassesFor("warlock")).containsExactly(
                "Archfey Patron", "Celestial Patron", "Fiend Patron", "Great Old One Patron", "Vestige Patron");
        assertThat(ClassProgression.subclassesFor("Wizard")).containsExactly(
                "Abjurer", "Conjurer", "Diviner", "Enchanter", "Evoker", "Illusionist", "Necromancer", "Transmuter");
        // The classes the book adds nothing to keep their four.
        for (String clazz : new String[] {"barbarian", "bard", "druid", "paladin", "ranger", "rogue", "sorcerer"}) {
            assertThat(ClassProgression.subclassesFor(clazz)).as(clazz).hasSize(4);
        }
    }

    @Test
    void arcanaUnleashedSubclasses_areOfferedToTheWorldOfWarcraftVariantsToo() {
        // Ordinary catalog subclasses: the WoW variant sees them beside its WoW-only ones.
        assertThat(ClassProgression.subclassesFor("cleric", "Shadow"))
                .contains("Arcana Domain", ClassProgression.SHADOW_DOMAIN);
        assertThat(ClassProgression.subclassesFor("fighter", "Arms")).contains("Arcane Archer");
        assertThat(ClassProgression.subclassesFor("monk", "Windwalker"))
                .contains("Warrior of the Mystic Arts", ClassProgression.WARRIOR_OF_MYSTIC_BREWS);
        assertThat(ClassProgression.subclassesFor("warlock", "Affliction")).contains("Vestige Patron");
        assertThat(ClassProgression.subclassesFor("wizard", "Frost"))
                .contains("Conjurer", "Enchanter", "Necromancer", "Transmuter");
        // None of them is a hero subclass, and their pick level is the class's.
        for (String name : new String[] {"Arcana Domain", "Arcane Archer", "Warrior of the Mystic Arts",
                "Vestige Patron", "Conjurer", "Enchanter", "Necromancer", "Transmuter"}) {
            assertThat(ClassProgression.isHeroSubclass(name)).as(name).isFalse();
        }
        assertThat(ClassProgression.subclassLevelFor("warlock")).isEqualTo(1);
        assertThat(ClassProgression.subclassLevelFor("cleric")).isEqualTo(3);
    }

    @Test
    void warriorOfTheMysticArts_bringsItsOwnSlots_throughTheSubclassAwareLookups() {
        // The class alone still has none; the subclass does (the whole table: MysticArtsSpellcastingTest).
        assertThat(ClassProgression.spellSlotsFor("monk", 10)).isEmpty();
        assertThat(ClassProgression.spellSlotsFor("Monk", "Warrior of the Mystic Arts", 10))
                .isEqualTo(Map.of(1, 4, 2, 3));
        assertThat(ClassProgression.hasSpellcastingOrPactMagic("Monk", "Warrior of the Mystic Arts", 10)).isTrue();
        assertThat(ClassProgression.hasSpellcastingOrPactMagic("Monk", "Warrior of the Mystic Arts", 2)).isFalse();
        assertThat(ClassProgression.highestSpellSlotLevel("Monk", "Warrior of the Mystic Arts", 20)).isEqualTo(4);
        assertThat(ClassProgression.hasSpellcastingOrPactMagic("Fighter", "Arcane Archer", 10)).isFalse();
    }

    @Test
    void spellSlotsFor_listsSpellLevelsInOrder() {
        assertThat(ClassProgression.spellSlotsFor("paladin", 17).keySet()).containsExactly(1, 2, 3, 4, 5);
        assertThat(ClassProgression.spellSlotsFor("wizard", 17).keySet())
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9);
    }
}
