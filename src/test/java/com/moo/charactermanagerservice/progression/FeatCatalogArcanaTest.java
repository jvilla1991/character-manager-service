package com.moo.charactermanagerservice.progression;

import com.moo.charactermanagerservice.progression.FeatCatalog.Candidate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The Arcana Unleashed feats in the catalog: who is offered which, and why not. */
class FeatCatalogArcanaTest {

    private static final List<String> SCHOOL_ADEPTS = List.of(
            "Abjuration Adept", "Conjuration Adept", "Divination Adept", "Enchantment Adept",
            "Evocation Adept", "Illusion Adept", "Necromancy Adept", "Transmutation Adept");

    private static final List<String> FAMILIAR_FEATS = List.of(
            "Elemental Familiar", "Otherworldly Familiar", "Soothing Familiar", "Warlike Familiar");

    private static final List<String> SCHOOL_MASTERIES = FeatCatalog.SCHOOLS_OF_MAGIC.stream()
            .map(FeatCatalog::schoolMasteryFeat).toList();

    private static final String NEEDS_SPELLCASTING = "requires the Spellcasting or Pact Magic feature";

    private static Candidate candidate(String clazz, int newLevel, String... feats) {
        return new Candidate(clazz, null, newLevel, Map.of(), List.of(feats));
    }

    // ── What is on offer ─────────────────────────────────────────────────────────

    @Test
    void theFifteenGeneralFeats_areOfferedAtLevel4_toACharacterWhoMeetsEveryPrerequisite() {
        Candidate wizard = candidate("Wizard", 4, "Familiar Friend", "Magic Initiate (Wizard)");
        assertThat(FeatCatalog.featOptions(wizard))
                .containsAll(SCHOOL_ADEPTS)
                .containsAll(FAMILIAR_FEATS)
                .contains("Magic Connoisseur", "Spell Resistant", "Spell Subterfuge")
                .isSorted();
        // Fourteen of them need something a Barbarian without feats lacks; Spell Resistant does not.
        List<String> gated = FeatCatalog.featOptions(wizard).stream()
                .filter(f -> !FeatCatalog.featOptions("Barbarian", 4).contains(f)).toList();
        assertThat(gated).hasSize(14).doesNotContain("Spell Resistant");
    }

    @Test
    void spellResistant_needsNothingButTheLevel() {
        assertThat(FeatCatalog.featOptions(candidate("Barbarian", 4))).contains("Spell Resistant");
        assertThat(FeatCatalog.featOptions("Rogue", 4)).contains("Spell Resistant");
    }

    @Test
    void originFeats_areNeverOfferedAtALevelUp() {
        assertThat(FeatCatalog.featOptions(candidate("Wizard", 19, "Familiar Friend")))
                .doesNotContain("Arcane Artist", "Arcane Eloquence", "Arcane Infiltrator", "Arcane Omens",
                        "Arcane Overload", "Arcane Safeguard", "Arcane Undertaker", "Familiar Friend",
                        "Portal Jumper", "Transmuted Anatomy");
    }

    // ── Prerequisite 1: the Spellcasting or Pact Magic feature ───────────────────

    @Test
    void spellcastingOrPactMagic_isByClass_fromTheLevelTheClassGetsIt() {
        for (String caster : List.of("Bard", "Cleric", "Druid", "Sorcerer", "Wizard", "Warlock", "Paladin", "Ranger")) {
            for (int level = 1; level <= 20; level++) {
                assertThat(ClassProgression.hasSpellcastingOrPactMagic(caster, null, level))
                        .as(caster + " " + level).isTrue();
            }
        }
        for (String other : List.of("Barbarian", "Fighter", "Monk", "Rogue")) {
            for (int level = 1; level <= 20; level++) {
                assertThat(ClassProgression.hasSpellcastingOrPactMagic(other, null, level))
                        .as(other + " " + level).isFalse();
            }
        }
        assertThat(ClassProgression.hasSpellcastingOrPactMagic(" wizard ", null, 1)).isTrue();
        assertThat(ClassProgression.hasSpellcastingOrPactMagic(null, null, 5)).isFalse();
        // The subclass is not read yet: no subclass brings spell slots of its own today.
        assertThat(ClassProgression.hasSpellcastingOrPactMagic("Fighter", "Eldritch Knight", 7)).isFalse();
        assertThat(ClassProgression.hasSpellcastingOrPactMagic("Paladin", "Oath of the Ebon Blade", 1)).isTrue();
    }

    @Test
    void highestSpellSlotLevel_followsTheClassSlotTable() {
        assertThat(ClassProgression.highestSpellSlotLevel("Wizard", null, 1)).isEqualTo(1);
        assertThat(ClassProgression.highestSpellSlotLevel("Wizard", null, 9)).isEqualTo(5);
        assertThat(ClassProgression.highestSpellSlotLevel("Wizard", null, 20)).isEqualTo(9);
        assertThat(ClassProgression.highestSpellSlotLevel("Paladin", null, 4)).isEqualTo(1);
        assertThat(ClassProgression.highestSpellSlotLevel("Paladin", null, 5)).isEqualTo(2);
        assertThat(ClassProgression.highestSpellSlotLevel("Ranger", null, 17)).isEqualTo(5);
        assertThat(ClassProgression.highestSpellSlotLevel("Warlock", null, 2)).isEqualTo(1);
        assertThat(ClassProgression.highestSpellSlotLevel("Warlock", null, 7)).isEqualTo(4);
        assertThat(ClassProgression.highestSpellSlotLevel("Warlock", null, 20)).isEqualTo(5);
        assertThat(ClassProgression.highestSpellSlotLevel("Fighter", null, 20)).isZero();
    }

    @Test
    void theFeatsThatNeedSpellcasting_areOfferedToCasters_andRefusedToOthersWithTheReason() {
        List<String> needSpellcasting = new java.util.ArrayList<>(SCHOOL_ADEPTS);
        needSpellcasting.add("Spell Subterfuge");
        for (String caster : List.of("Wizard", "Cleric", "Warlock", "Paladin", "Ranger")) {
            assertThat(FeatCatalog.featOptions(candidate(caster, 4))).as(caster).containsAll(needSpellcasting);
        }
        for (String other : List.of("Fighter", "Barbarian", "Monk", "Rogue")) {
            Candidate c = candidate(other, 4);
            assertThat(FeatCatalog.featOptions(c)).as(other).doesNotContainAnyElementsOf(needSpellcasting);
            for (String feat : needSpellcasting) {
                assertThat(FeatCatalog.matchFeat(feat, c)).as(other + " " + feat).isNull();
                assertThat(FeatCatalog.unmetPrerequisite(feat, c)).as(other + " " + feat).isEqualTo(NEEDS_SPELLCASTING);
            }
        }
        // The older entry points know no feats; class and level still gate.
        assertThat(FeatCatalog.featOptions("wizard", 4)).containsAll(SCHOOL_ADEPTS);
        assertThat(FeatCatalog.featOptions("fighter", 4)).doesNotContainAnyElementsOf(SCHOOL_ADEPTS);
        assertThat(FeatCatalog.isValidFeat("evocation adept", "Sorcerer", 8)).isTrue();
        assertThat(FeatCatalog.isValidFeat("Evocation Adept", "Rogue", 8)).isFalse();
    }

    // ── Prerequisite 2: the Familiar Friend feat ─────────────────────────────────

    @Test
    void theFamiliarFeats_needTheFamiliarFriendFeat() {
        Candidate without = candidate("Fighter", 4, "Alert", "Second Wind");
        assertThat(FeatCatalog.featOptions(without)).doesNotContainAnyElementsOf(FAMILIAR_FEATS);
        for (String feat : FAMILIAR_FEATS) {
            assertThat(FeatCatalog.matchFeat(feat, without)).as(feat).isNull();
            assertThat(FeatCatalog.unmetPrerequisite(feat, without)).as(feat)
                    .isEqualTo("requires the Familiar Friend feat");
        }
        // Any class qualifies once it has the feat; the stored name's case does not matter.
        for (Candidate with : List.of(candidate("Fighter", 4, "Familiar Friend"),
                candidate("Rogue", 8, "Sneak Attack", " familiar friend "))) {
            assertThat(FeatCatalog.featOptions(with)).containsAll(FAMILIAR_FEATS);
            for (String feat : FAMILIAR_FEATS) {
                assertThat(FeatCatalog.matchFeat(feat.toUpperCase(), with)).isEqualTo(feat);
                assertThat(FeatCatalog.unmetPrerequisite(feat, with)).isNull();
            }
        }
        assertThat(FeatCatalog.featOptions("Wizard", 4)).doesNotContainAnyElementsOf(FAMILIAR_FEATS);
    }

    // ── Prerequisite 3: a Magic Initiate feat ────────────────────────────────────

    @Test
    void magicConnoisseur_needsAnyMagicInitiateFeat() {
        Candidate without = candidate("Wizard", 4, "Familiar Friend");
        assertThat(FeatCatalog.featOptions(without)).doesNotContain("Magic Connoisseur");
        assertThat(FeatCatalog.unmetPrerequisite("Magic Connoisseur", without))
                .isEqualTo("requires a Magic Initiate feat");
        for (String initiate : List.of("Magic Initiate (Cleric)", "Magic Initiate (Druid)", "magic initiate (wizard)")) {
            Candidate with = candidate("Fighter", 4, initiate);
            assertThat(FeatCatalog.featOptions(with)).as(initiate).contains("Magic Connoisseur");
            assertThat(FeatCatalog.matchFeat("magic connoisseur", with)).isEqualTo("Magic Connoisseur");
        }
    }

    // ── Arcane Warrior (Fighting Style) ──────────────────────────────────────────

    @Test
    void arcaneWarrior_isAFightingStyleFeat_andAFightingStyleChoice() {
        for (String clazz : List.of("Fighter", "Paladin", "Ranger")) {
            assertThat(FeatCatalog.featOptions(candidate(clazz, 4))).as(clazz).contains("Arcane Warrior");
        }
        for (String clazz : List.of("Wizard", "Barbarian", "Rogue")) {
            assertThat(FeatCatalog.featOptions(candidate(clazz, 4))).as(clazz).doesNotContain("Arcane Warrior");
            assertThat(FeatCatalog.unmetPrerequisite("Arcane Warrior", candidate(clazz, 4))).isNull(); // not a feat for them at all
        }
        assertThat(FeatCatalog.fightingStyleOptions("Paladin", null, 2))
                .contains("Arcane Warrior", "Blessed Warrior", "Defense").doesNotContain("Druidic Warrior").isSorted();
        assertThat(FeatCatalog.fightingStyleOptions("Ranger", "Demon Hunter", 2))
                .contains("Arcane Warrior", "Druidic Warrior", "Void Warrior");
        assertThat(FeatCatalog.fightingStyleOptions("Fighter", null, 2)).isEmpty(); // a Fighter's is at creation
        assertThat(FeatCatalog.matchFightingStyle(" arcane warrior ", "Paladin", null, 2)).isEqualTo("Arcane Warrior");

        FeatCatalog.CantripFightingStyle style = FeatCatalog.cantripFightingStyle("Arcane Warrior").orElseThrow();
        assertThat(style.extraCantrips()).isEqualTo(2);
        assertThat(style.desc()).contains("two Wizard cantrips");
        assertThat(style.eligible().test("Ranger", null)).isTrue();
        assertThat(style.eligible().test("Wizard", null)).isFalse();
    }

    // ── Epic Boons ───────────────────────────────────────────────────────────────

    @Test
    void theThreeEpicBoons_areOfferedAtLevel19_theSpellOnesToCastersOnly() {
        assertThat(FeatCatalog.featOptions(candidate("Wizard", 19)))
                .contains("Boon of Erupting Spellpower", "Boon of the Iron Mind")
                .containsAll(SCHOOL_MASTERIES)
                .doesNotContain("Boon of Magic School Mastery")
                .isSorted();
        assertThat(FeatCatalog.featOptions(candidate("Warlock", 19))).containsAll(SCHOOL_MASTERIES);
        assertThat(FeatCatalog.featOptions(candidate("Paladin", 19))).contains("Boon of Erupting Spellpower");

        Candidate fighter = candidate("Fighter", 19);
        assertThat(FeatCatalog.featOptions(fighter))
                .contains("Boon of the Iron Mind")
                .doesNotContain("Boon of Erupting Spellpower")
                .doesNotContainAnyElementsOf(SCHOOL_MASTERIES);
        assertThat(FeatCatalog.unmetPrerequisite("Boon of Erupting Spellpower", fighter)).isEqualTo(NEEDS_SPELLCASTING);
        assertThat(FeatCatalog.unmetPrerequisite("Boon of Magic School Mastery (Evocation)", fighter))
                .isEqualTo(NEEDS_SPELLCASTING);

        for (int level : List.of(4, 8, 12, 16)) {
            assertThat(FeatCatalog.featOptions(candidate("Wizard", level))).as("level " + level)
                    .doesNotContain("Boon of Erupting Spellpower", "Boon of the Iron Mind")
                    .doesNotContainAnyElementsOf(SCHOOL_MASTERIES);
        }
        // A bare boon name, or a school that does not exist, is not a feat.
        assertThat(FeatCatalog.matchFeat("Boon of Magic School Mastery", candidate("Wizard", 19))).isNull();
        assertThat(FeatCatalog.matchFeat("Boon of Magic School Mastery (Chronurgy)", candidate("Wizard", 19))).isNull();
    }

    @Test
    void schoolMastery_isOfferedOncePerSchool() {
        Candidate wizard = candidate("Wizard", 19, "Boon of Magic School Mastery (Evocation)",
                "boon of magic school mastery (illusion)");
        List<String> left = FeatCatalog.featOptions(wizard).stream()
                .filter(f -> f.startsWith(FeatCatalog.SCHOOL_MASTERY)).toList();
        assertThat(left).containsExactly(
                "Boon of Magic School Mastery (Abjuration)", "Boon of Magic School Mastery (Conjuration)",
                "Boon of Magic School Mastery (Divination)", "Boon of Magic School Mastery (Enchantment)",
                "Boon of Magic School Mastery (Necromancy)", "Boon of Magic School Mastery (Transmutation)");
        assertThat(FeatCatalog.unmetPrerequisite("Boon of Magic School Mastery (Evocation)", wizard))
                .isEqualTo("can be taken only once for each school of magic");
        assertThat(FeatCatalog.schoolMasteryFeat("Evocation")).isEqualTo("Boon of Magic School Mastery (Evocation)");
    }

    // ── One of each ──────────────────────────────────────────────────────────────

    @Test
    void anArcanaFeatTheCharacterHas_isNotOfferedAgain() {
        Candidate wizard = candidate("Wizard", 8, "Evocation Adept", "Spell Resistant", "Familiar Friend",
                "Warlike Familiar");
        assertThat(FeatCatalog.featOptions(wizard))
                .doesNotContain("Evocation Adept", "Spell Resistant", "Warlike Familiar")
                .contains("Abjuration Adept", "Soothing Familiar", "War Caster");
        assertThat(FeatCatalog.unmetPrerequisite("Evocation Adept", wizard)).isEqualTo("can be taken only once");
        // Arcane Warrior taken as the level 2 Fighting Style is not offered as a feat later.
        assertThat(FeatCatalog.featOptions(candidate("Paladin", 4, "Arcane Warrior"))).doesNotContain("Arcane Warrior");
        // The books before this one are as they were: a feat the character has is still listed.
        assertThat(FeatCatalog.featOptions(candidate("Wizard", 8, "War Caster", "Clearcasting")))
                .contains("War Caster", "Clearcasting");
    }

    @Test
    void anAbilityPrerequisite_stillReadsAsBefore_throughTheNewEntryPoint() {
        Candidate monk = new Candidate("Monk", null, 4, Map.of("DEX", 12, "WIS", 16), List.of());
        assertThat(FeatCatalog.unmetPrerequisite("Fists of Fury", monk)).isEqualTo("requires Dexterity 13 and Wisdom 13");
        assertThat(FeatCatalog.unmetPrerequisite("Sentinel", monk)).isNull();
        assertThat(FeatCatalog.unmetPrerequisite("No Such Feat", monk)).isNull();
        // Nulls are an empty character, not an error.
        Candidate bare = new Candidate(null, null, 4, null, null);
        assertThat(FeatCatalog.featOptions(bare)).contains("Spell Resistant").doesNotContainAnyElementsOf(SCHOOL_ADEPTS);
    }
}
