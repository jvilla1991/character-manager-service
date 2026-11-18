package com.moo.charactermanagerservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moo.charactermanagerservice.dto.FeatureGain;
import com.moo.charactermanagerservice.dto.HpMode;
import com.moo.charactermanagerservice.dto.LevelUpPreview;
import com.moo.charactermanagerservice.models.PC;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure unit tests for the level-up rules engine — no repo, no Spring, no security.
 * Covers Phase 1 gains (fixed-average HP, proficiency bumps at 5/9/13/17) and Phase 2
 * spell-slot progression (full-caster + pact tables, preserving the player's used count).
 */
class LevelUpServiceTest {

    private final LevelUpService service = new LevelUpService(new ObjectMapper());

    /** Build a minimal PC with the stats Phase 1 touches. */
    private static PC pc(String clazz, int level, int con, int hpMax, int hpCurrent) {
        PC pc = new PC();
        pc.setClazz(clazz);
        pc.setLevel((short) level);
        pc.setAbilityCon((short) con);
        pc.setHpMax((short) hpMax);
        pc.setHpCurrent((short) hpCurrent);
        return pc;
    }

    // --- proficiency bonus progression ---

    @Test
    void profBonus_bumpsAtThresholds() {
        // newLevel -> expected prof bonus
        assertThat(service.preview(pc("Wizard", 1, 10, 6, 6)).newProfBonus()).isEqualTo(2);  // ->2
        assertThat(service.preview(pc("Wizard", 4, 10, 6, 6)).newProfBonus()).isEqualTo(3);  // ->5
        assertThat(service.preview(pc("Wizard", 8, 10, 6, 6)).newProfBonus()).isEqualTo(4);  // ->9
        assertThat(service.preview(pc("Wizard", 12, 10, 6, 6)).newProfBonus()).isEqualTo(5); // ->13
        assertThat(service.preview(pc("Wizard", 16, 10, 6, 6)).newProfBonus()).isEqualTo(6); // ->17
    }

    @Test
    void profBonus_unchangedWithinBand() {
        LevelUpPreview p = service.preview(pc("Fighter", 1, 10, 12, 12)); // 1 -> 2, both band 2
        assertThat(p.currentProfBonus()).isEqualTo(2);
        assertThat(p.newProfBonus()).isEqualTo(2);
    }

    // --- HP gain (fixed average + CON mod) per hit die ---

    @Test
    void hpGain_wizardD6() {
        // d6 avg = 4, CON 14 (+2) -> 6
        assertThat(service.preview(pc("Wizard", 1, 14, 8, 8)).hpGained()).isEqualTo(6);
    }

    @Test
    void hpGain_fighterD10() {
        // d10 avg = 6, CON 16 (+3) -> 9
        assertThat(service.preview(pc("Fighter", 1, 16, 12, 12)).hpGained()).isEqualTo(9);
    }

    @Test
    void hpGain_barbarianD12() {
        // d12 avg = 7, CON 12 (+1) -> 8
        assertThat(service.preview(pc("Barbarian", 1, 12, 15, 15)).hpGained()).isEqualTo(8);
    }

    @Test
    void hpGain_unknownClassDefaultsToD8() {
        // default d8 avg = 5, CON 10 (+0) -> 5
        assertThat(service.preview(pc("Artificer", 1, 10, 8, 8)).hpGained()).isEqualTo(5);
    }

    @Test
    void hpGain_flooredAtOne_withHeavyConPenalty() {
        // d6 avg = 4, CON 3 (-4) -> 0, floored to 1
        assertThat(service.preview(pc("Sorcerer", 1, 3, 4, 4)).hpGained()).isEqualTo(1);
    }

    @Test
    void hpGain_nullConTreatedAsZeroScore() {
        PC pc = pc("Wizard", 1, 10, 8, 8);
        pc.setAbilityCon(null);
        // CON null -> score 0 -> mod floor((0-10)/2) = -5; d6 avg 4 - 5 = -1 -> floored to 1
        assertThat(service.preview(pc).hpGained()).isEqualTo(1);
    }

    // --- applyLevelUp mutates the entity ---

    @Test
    void applyLevelUp_mutatesLevelHpAndProf() {
        PC pc = pc("Fighter", 4, 16, 30, 22); // ->5: prof 2->3, HP +9
        service.applyLevelUp(pc);

        assertThat(pc.getLevel()).isEqualTo((short) 5);
        assertThat(pc.getHpMax()).isEqualTo((short) 39);
        assertThat(pc.getHpCurrent()).isEqualTo((short) 31);
        assertThat(pc.getProfBonus()).isEqualTo((short) 3);
    }

    @Test
    void preview_doesNotMutate() {
        PC pc = pc("Fighter", 4, 16, 30, 22);
        service.preview(pc);

        assertThat(pc.getLevel()).isEqualTo((short) 4);
        assertThat(pc.getHpMax()).isEqualTo((short) 30);
        assertThat(pc.getProfBonus()).isNull();
    }

    @Test
    void preview_reportsCurrentAndNewLevel() {
        LevelUpPreview p = service.preview(pc("Cleric", 7, 14, 50, 50));
        assertThat(p.currentLevel()).isEqualTo(7);
        assertThat(p.newLevel()).isEqualTo(8);
        assertThat(p.newHpMax()).isEqualTo(50 + p.hpGained());
    }

    @Test
    void nullLevel_treatedAsLevelOne() {
        PC pc = pc("Wizard", 1, 10, 6, 6);
        pc.setLevel(null);
        assertThat(service.preview(pc).newLevel()).isEqualTo(2);
    }

    // --- max-level guard ---

    @Test
    void applyLevelUp_throws409_atMaxLevel() {
        PC pc = pc("Wizard", 20, 14, 200, 200);
        assertThatThrownBy(() -> service.applyLevelUp(pc))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(409));
    }

    @Test
    void preview_throws409_atMaxLevel() {
        PC pc = pc("Wizard", 20, 14, 200, 200);
        assertThatThrownBy(() -> service.preview(pc))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(409));
    }

    // --- spell-slot progression (Phase 2) ---

    @Test
    void preview_fullCaster_reportsNewSlotTable() {
        PC bard = pc("Bard", 7, 14, 50, 50);
        bard.setSpellSlots("{\"1\":{\"max\":4,\"used\":2},\"2\":{\"max\":3,\"used\":0},\"3\":{\"max\":3,\"used\":0},\"4\":{\"max\":1,\"used\":0}}");

        LevelUpPreview p = service.preview(bard);

        // Bard 7 -> 8: full-caster row becomes 4/3/3/2
        assertThat(p.newSpellSlots()).containsExactlyInAnyOrderEntriesOf(
                java.util.Map.of(1, 4, 2, 3, 3, 3, 4, 2));
        assertThat(p.currentSpellSlots()).containsEntry(4, 1);
    }

    @Test
    void applyLevelUp_fullCaster_rebuildsSlotsPreservingUsed() {
        PC bard = pc("Bard", 8, 14, 50, 50); // 8 -> 9 (not an ASI level)
        bard.setSpellSlots("{\"1\":{\"max\":4,\"used\":2},\"4\":{\"max\":2,\"used\":1}}");

        service.applyLevelUp(bard);

        // L9: level-4 max grows 2 -> 3 but used (1) is preserved; level-1 used (2) preserved;
        // a new level-5 slot appears.
        assertThat(bard.getSpellSlots()).contains("\"4\":{\"max\":3,\"used\":1}");
        assertThat(bard.getSpellSlots()).contains("\"1\":{\"max\":4,\"used\":2}");
        assertThat(bard.getSpellSlots()).contains("\"5\":{\"max\":1,\"used\":0}");
    }

    @Test
    void applyLevelUp_pactCaster_advancesPactSlotLevel() {
        PC warlock = pc("Warlock", 4, 14, 30, 30);
        warlock.setSpellSlots("{\"2\":{\"max\":2,\"used\":1}}");

        service.applyLevelUp(warlock);

        // Warlock 4 -> 5: pact slots move to spell level 3, two slots, used resets (new level).
        assertThat(warlock.getSpellSlots()).isEqualTo("{\"3\":{\"max\":2,\"used\":0}}");
    }

    @Test
    void applyLevelUp_nonCaster_leavesSlotsUntouched() {
        PC fighter = pc("Fighter", 4, 16, 30, 30);
        fighter.setSpellSlots("{}");

        service.applyLevelUp(fighter);

        assertThat(fighter.getSpellSlots()).isEqualTo("{}");
        assertThat(service.preview(pc("Fighter", 5, 16, 39, 39)).newSpellSlots()).isEmpty();
    }

    @Test
    void applyLevelUp_clampsUsedToNewMax() {
        PC bard = pc("Bard", 8, 14, 50, 50); // 8 -> 9 (not an ASI level)
        // Corrupt/overspent used count well above the table max.
        bard.setSpellSlots("{\"1\":{\"max\":4,\"used\":9}}");

        service.applyLevelUp(bard);

        assertThat(bard.getSpellSlots()).contains("\"1\":{\"max\":4,\"used\":4}");
    }

    @Test
    void applyLevelUp_caster_survivesMalformedSlotJson() {
        PC wizard = pc("Wizard", 2, 14, 14, 14);
        wizard.setSubclass("Evoker"); // already has a subclass, so none is due at level 3
        wizard.setSpellSlots("not-valid-json");

        service.applyLevelUp(wizard);

        // Malformed prior slots -> rebuilt fresh from the L3 table (4/2), used = 0.
        assertThat(wizard.getSpellSlots()).isEqualTo("{\"1\":{\"max\":4,\"used\":0},\"2\":{\"max\":2,\"used\":0}}");
    }

    // --- subclass selection mechanism (Phase 3 — catalog intentionally empty) ---

    @Test
    void preview_subclassDue_atGrantLevelWithOptions() {
        // Cleric grant level is 3; leveling 2 -> 3 with no subclass yet.
        LevelUpPreview p = service.preview(pc("Cleric", 2, 14, 14, 14));
        assertThat(p.subclassDue()).isTrue();
        assertThat(p.subclassOptions()).contains("Life Domain", "War Domain");
    }

    @Test
    void preview_subclassNotDue_whenAlreadyHasSubclass() {
        PC cleric = pc("Cleric", 2, 14, 14, 14);
        cleric.setSubclass("Life Domain");
        assertThat(service.preview(cleric).subclassDue()).isFalse();
    }

    @Test
    void preview_subclassNotDue_atNonGrantLevel() {
        // Cleric 4 -> 5 is past the grant level.
        assertThat(service.preview(pc("Cleric", 4, 14, 30, 30)).subclassDue()).isFalse();
    }

    @Test
    void applyLevelUp_setsChosenSubclass_whenDue() {
        PC cleric = pc("Cleric", 2, 14, 14, 14);

        service.applyLevelUp(cleric, "Life Domain");

        assertThat(cleric.getLevel()).isEqualTo((short) 3);
        assertThat(cleric.getSubclass()).isEqualTo("Life Domain");
    }

    @Test
    void applyLevelUp_rejectsSubclass_whenNotDue() {
        PC cleric = pc("Cleric", 4, 14, 30, 30); // -> 5, past grant level
        assertThatThrownBy(() -> service.applyLevelUp(cleric, "Life Domain"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void applyLevelUp_requiresSubclassChoice_whenDue() {
        // Catalog now has options, so a subclass must be selected at the grant level.
        PC cleric = pc("Cleric", 2, 14, 14, 14);
        assertThatThrownBy(() -> service.applyLevelUp(cleric, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void applyLevelUp_rejectsUnknownSubclass() {
        PC cleric = pc("Cleric", 2, 14, 14, 14);
        assertThatThrownBy(() -> service.applyLevelUp(cleric, "Bogus Domain"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void applyLevelUp_pactCasterSubclassDueAtCreationLevel_neverFiresOnLevelUp() {
        // Sorcerer/Warlock grant at level 1, so a subclass is never "due" during a level-up.
        assertThat(service.preview(pc("Sorcerer", 4, 14, 24, 24)).subclassDue()).isFalse();
        assertThat(service.preview(pc("Warlock", 1, 14, 9, 9)).subclassDue()).isFalse();
    }

    // --- Ability Score Improvement (Phase 4) ---

    @Test
    void preview_asiDue_atAsiLevels() {
        assertThat(service.preview(pc("Fighter", 3, 14, 28, 28)).asiDue()).isTrue();  // -> 4
        assertThat(service.preview(pc("Fighter", 4, 14, 38, 38)).asiDue()).isFalse(); // -> 5
        assertThat(service.preview(pc("Fighter", 5, 14, 44, 44)).asiDue()).isTrue();  // -> 6 (Fighter extra)
        assertThat(service.preview(pc("Wizard", 3, 14, 18, 18)).asiDue()).isTrue();   // -> 4
        assertThat(service.preview(pc("Wizard", 5, 14, 30, 30)).asiDue()).isFalse();  // -> 6 (not Fighter)
        assertThat(service.preview(pc("Rogue", 9, 14, 60, 60)).asiDue()).isTrue();    // -> 10 (Rogue extra)
    }

    @Test
    void applyLevelUp_asi_plus2_oneAbility() {
        PC fighter = pc("Fighter", 3, 14, 28, 28);
        fighter.setAbilityStr((short) 16);

        service.applyLevelUp(fighter, null, java.util.Map.of("STR", 2));

        assertThat(fighter.getLevel()).isEqualTo((short) 4);
        assertThat(fighter.getAbilityStr()).isEqualTo((short) 18);
    }

    @Test
    void applyLevelUp_asi_plus1_twoAbilities() {
        PC fighter = pc("Fighter", 3, 14, 28, 28);
        fighter.setAbilityStr((short) 16);
        fighter.setAbilityDex((short) 13);

        service.applyLevelUp(fighter, null, java.util.Map.of("STR", 1, "DEX", 1));

        assertThat(fighter.getAbilityStr()).isEqualTo((short) 17);
        assertThat(fighter.getAbilityDex()).isEqualTo((short) 14);
    }

    @Test
    void applyLevelUp_asi_conIncrease_grantsRetroactiveHp() {
        // Fighter 3 -> 4, CON 15 (+2) -> 17 (+3). d10 avg 6 + new CON 3 = 9 this level;
        // retroactive (4-1) * 1 = 3 for the prior levels; total +12.
        PC fighter = pc("Fighter", 3, 15, 28, 28);

        service.applyLevelUp(fighter, null, java.util.Map.of("CON", 2));

        assertThat(fighter.getAbilityCon()).isEqualTo((short) 17);
        assertThat(fighter.getHpMax()).isEqualTo((short) 40);
        assertThat(fighter.getHpCurrent()).isEqualTo((short) 40);
    }

    @Test
    void applyLevelUp_asi_requiredAtAsiLevel() {
        PC fighter = pc("Fighter", 3, 14, 28, 28);
        assertThatThrownBy(() -> service.applyLevelUp(fighter, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void applyLevelUp_asi_rejectedWhenNotAnAsiLevel() {
        PC fighter = pc("Fighter", 4, 14, 38, 38); // -> 5, not an ASI level
        assertThatThrownBy(() -> service.applyLevelUp(fighter, null, java.util.Map.of("STR", 2)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void applyLevelUp_asi_rejectsWrongTotal() {
        PC fighter = pc("Fighter", 3, 14, 28, 28);
        assertThatThrownBy(() -> service.applyLevelUp(fighter, null, java.util.Map.of("STR", 1)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void applyLevelUp_asi_rejectsExceeding20() {
        PC fighter = pc("Fighter", 3, 14, 28, 28);
        fighter.setAbilityStr((short) 19);
        assertThatThrownBy(() -> service.applyLevelUp(fighter, null, java.util.Map.of("STR", 2)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void applyLevelUp_asi_rejectsUnknownAbility() {
        PC fighter = pc("Fighter", 3, 14, 28, 28);
        assertThatThrownBy(() -> service.applyLevelUp(fighter, null, java.util.Map.of("LUCK", 2)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(400));
    }

    // --- Feats (the ASI alternative) ---

    @Test
    void preview_featOptions_offeredOnlyAtAsiLevels() {
        assertThat(service.preview(pc("Fighter", 3, 14, 28, 28)).featOptions()).isNotEmpty(); // -> 4
        assertThat(service.preview(pc("Fighter", 4, 14, 38, 38)).featOptions()).isEmpty();    // -> 5
    }

    @Test
    void applyLevelUp_feat_recordedAmongFeatures_andNoAsiApplied() {
        PC fighter = pc("Fighter", 3, 14, 28, 28);
        fighter.setAbilityStr((short) 16);

        service.applyLevelUp(fighter, null, null, "Sentinel");

        assertThat(fighter.getLevel()).isEqualTo((short) 4);
        assertThat(fighter.getAbilityStr()).isEqualTo((short) 16); // unchanged — feat, not ASI
        assertThat(fighter.getFeatures()).contains("Sentinel").contains("Feat (Level 4)");
    }

    @Test
    void applyLevelUp_feat_appendsToExistingFeatures() {
        PC fighter = pc("Fighter", 3, 14, 28, 28);
        fighter.setFeatures("[{\"name\":\"Second Wind\",\"source\":\"Fighter 1\",\"desc\":\"Regain HP\"}]");

        service.applyLevelUp(fighter, null, null, "Great Weapon Master");

        assertThat(fighter.getFeatures()).contains("Second Wind").contains("Great Weapon Master");
    }

    @Test
    void applyLevelUp_feat_rejectsUnknownFeat() {
        PC fighter = pc("Fighter", 3, 14, 28, 28);
        assertThatThrownBy(() -> service.applyLevelUp(fighter, null, null, "Superman"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void applyLevelUp_feat_rejectsBothAsiAndFeat() {
        PC fighter = pc("Fighter", 3, 14, 28, 28);
        assertThatThrownBy(() -> service.applyLevelUp(fighter, null, java.util.Map.of("STR", 2), "Sentinel"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void applyLevelUp_feat_rejectedWhenNotAnAsiLevel() {
        PC fighter = pc("Fighter", 4, 14, 38, 38); // -> 5, not an ASI level
        assertThatThrownBy(() -> service.applyLevelUp(fighter, null, null, "Sentinel"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void preview_featOptions_fightingStylesOnlyForFightingStyleClasses() {
        assertThat(service.preview(pc("Fighter", 3, 14, 28, 28)).featOptions()).contains("Archery");   // -> 4
        assertThat(service.preview(pc("Paladin", 3, 14, 28, 28)).featOptions()).contains("Defense");   // -> 4
        assertThat(service.preview(pc("Ranger", 3, 14, 28, 28)).featOptions()).contains("Dueling");    // -> 4
        assertThat(service.preview(pc("Wizard", 3, 14, 18, 18)).featOptions())
                .contains("War Caster").doesNotContain("Archery");                                     // -> 4
    }

    @Test
    void preview_featOptions_epicBoonsOnlyAtLevel19() {
        assertThat(service.preview(pc("Wizard", 18, 14, 80, 80)).featOptions())
                .contains("Boon of Fate", "Boon of Spell Recall");                       // -> 19
        assertThat(service.preview(pc("Wizard", 3, 14, 18, 18)).featOptions())
                .doesNotContain("Boon of Fate");                                         // -> 4
    }

    @Test
    void applyLevelUp_feat_acceptsFightingStyleForFightingStyleClass() {
        PC ranger = pc("Ranger", 3, 14, 28, 28); // -> 4, an ASI level
        service.applyLevelUp(ranger, null, null, "Two-Weapon Fighting");
        assertThat(ranger.getFeatures()).contains("Two-Weapon Fighting").contains("Feat (Level 4)");
    }

    @Test
    void applyLevelUp_feat_rejectsFightingStyleForOtherClasses() {
        PC wizard = pc("Wizard", 3, 14, 18, 18); // -> 4, an ASI level
        assertThatThrownBy(() -> service.applyLevelUp(wizard, null, null, "Archery"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void applyLevelUp_feat_acceptsEpicBoonAtLevel19() {
        PC wizard = pc("Wizard", 18, 14, 80, 80); // -> 19, an ASI level
        service.applyLevelUp(wizard, null, null, "Boon of Fate");
        assertThat(wizard.getFeatures()).contains("Boon of Fate").contains("Feat (Level 19)");
    }

    @Test
    void applyLevelUp_feat_rejectsEpicBoonBeforeLevel19() {
        PC fighter = pc("Fighter", 3, 14, 28, 28); // -> 4, an ASI level
        assertThatThrownBy(() -> service.applyLevelUp(fighter, null, null, "Boon of Fate"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(400));
    }

    // --- Auto-granted class features ---

    @Test
    void preview_featuresGained_atSeededLevel() {
        // Barbarian 1 -> 2 grants Reckless Attack + Danger Sense (seeded).
        List<String> names = service.preview(pc("Barbarian", 1, 14, 14, 14)).featuresGained()
                .stream().map(FeatureGain::name).toList();
        assertThat(names).contains("Reckless Attack", "Danger Sense");
    }

    @Test
    void preview_featuresGained_emptyAtLevelsWithNoFeatures() {
        assertThat(service.preview(pc("Wizard", 3, 14, 18, 18)).featuresGained()).isEmpty();   // -> 4, no wizard feature
        assertThat(service.preview(pc("Barbarian", 5, 14, 50, 50)).featuresGained()).isEmpty(); // -> 6, no barbarian feature
    }

    @Test
    void preview_featuresGained_acrossClasses() {
        assertThat(service.preview(pc("Rogue", 1, 12, 9, 9)).featuresGained()
                .stream().map(FeatureGain::name).toList()).contains("Cunning Action");   // -> 2
        assertThat(service.preview(pc("Wizard", 1, 12, 8, 8)).featuresGained()
                .stream().map(FeatureGain::name).toList()).contains("Scholar");          // -> 2
        assertThat(service.preview(pc("Sorcerer", 1, 12, 8, 8)).featuresGained()
                .stream().map(FeatureGain::name).toList()).contains("Metamagic");        // -> 2
        assertThat(service.preview(pc("Monk", 1, 12, 9, 9)).featuresGained()
                .stream().map(FeatureGain::name).toList()).contains("Unarmored Movement"); // -> 2
    }

    @Test
    void applyLevelUp_grantsClassFeatures_taggedWithClassAndLevel() {
        PC barbarian = pc("Barbarian", 1, 14, 14, 14);

        service.applyLevelUp(barbarian);

        assertThat(barbarian.getLevel()).isEqualTo((short) 2);
        assertThat(barbarian.getFeatures()).contains("Reckless Attack").contains("Barbarian 2");
    }

    @Test
    void applyLevelUp_appendsFeaturesToExisting() {
        PC barbarian = pc("Barbarian", 1, 14, 14, 14);
        barbarian.setFeatures("[{\"name\":\"Rage\",\"source\":\"Barbarian 1\",\"desc\":\"...\"}]");

        service.applyLevelUp(barbarian);

        assertThat(barbarian.getFeatures()).contains("Rage").contains("Reckless Attack");
    }

    @Test
    void applyLevelUp_levelWithNoFeatures_leavesFeaturesUntouched() {
        // Sorcerer 2 -> 3 grants no generic class feature (and no choice is due).
        PC sorcerer = pc("Sorcerer", 2, 14, 12, 12);
        sorcerer.setFeatures("[]");

        service.applyLevelUp(sorcerer);

        assertThat(sorcerer.getFeatures()).isEqualTo("[]");
    }

    @Test
    void applyLevelUp_acceptsNewlyAddedFeat() {
        PC fighter = pc("Fighter", 3, 14, 28, 28); // -> 4, an ASI level
        service.applyLevelUp(fighter, null, null, "Crossbow Expert");
        assertThat(fighter.getFeatures()).contains("Crossbow Expert");
    }

    // --- Cantrips-known progression (preview only) ---

    @Test
    void preview_cantripsKnown_bumpsAtLevel4() {
        // Wizard base 3 cantrips; +1 at level 4.
        LevelUpPreview p = service.preview(pc("Wizard", 3, 14, 18, 18)); // -> 4
        assertThat(p.currentCantripsKnown()).isEqualTo(3);
        assertThat(p.newCantripsKnown()).isEqualTo(4);
    }

    @Test
    void preview_cantripsKnown_unchangedBetweenBreakpoints() {
        LevelUpPreview p = service.preview(pc("Sorcerer", 5, 14, 32, 32)); // -> 6; sorcerer base 4, +1 at 4
        assertThat(p.currentCantripsKnown()).isEqualTo(5);
        assertThat(p.newCantripsKnown()).isEqualTo(5);
    }

    @Test
    void preview_cantripsKnown_zeroForNonCaster() {
        LevelUpPreview p = service.preview(pc("Fighter", 1, 14, 12, 12));
        assertThat(p.currentCantripsKnown()).isEqualTo(0);
        assertThat(p.newCantripsKnown()).isEqualTo(0);
    }

    // --- Spell selection (learning new spells/cantrips) ---

    private static Map<String, Object> spell(int lvl, String name) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("lvl", lvl);
        m.put("name", name);
        return m;
    }

    @Test
    void preview_spellsKnown_progression() {
        // Bard prepared spells: level 4 -> 7, level 5 -> 9.
        LevelUpPreview p = service.preview(pc("Bard", 4, 14, 30, 30));
        assertThat(p.currentSpellsKnown()).isEqualTo(7);
        assertThat(p.newSpellsKnown()).isEqualTo(9);
    }

    @Test
    void applyLevelUp_appendsNewSpells_withinDelta() {
        // Bard 4 -> 5 (not an ASI level): prepared delta 7 -> 9 = 2 spells allowed, 0 cantrips.
        PC bard = pc("Bard", 4, 14, 30, 30);

        service.applyLevelUp(bard, null, null, null, List.of(spell(1, "Hold Person"), spell(2, "Invisibility")));

        assertThat(bard.getSpells()).contains("Hold Person").contains("Invisibility");
    }

    @Test
    void applyLevelUp_appendsCantrip_atCantripBreakpoint() {
        // Bard 9 -> 10 (not an ASI level): cantrips 3 -> 4 = 1 cantrip allowed.
        PC bard = pc("Bard", 9, 14, 60, 60);

        service.applyLevelUp(bard, null, null, null, List.of(spell(0, "Light")));

        assertThat(bard.getSpells()).contains("Light");
    }

    @Test
    void applyLevelUp_rejectsTooManySpells() {
        PC bard = pc("Bard", 4, 14, 30, 30); // 2 spells allowed
        assertThatThrownBy(() -> service.applyLevelUp(bard, null, null, null,
                List.of(spell(1, "A"), spell(1, "B"), spell(1, "C"))))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void applyLevelUp_rejectsCantripWhenNoneAllowed() {
        PC bard = pc("Bard", 4, 14, 30, 30); // cantrip delta 0 at level 5
        assertThatThrownBy(() -> service.applyLevelUp(bard, null, null, null, List.of(spell(0, "Light"))))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void applyLevelUp_rejectsDuplicateSpell() {
        PC bard = pc("Bard", 4, 14, 30, 30);
        bard.setSpells("[{\"lvl\":1,\"name\":\"Hold Person\"}]");
        assertThatThrownBy(() -> service.applyLevelUp(bard, null, null, null, List.of(spell(1, "Hold Person"))))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void applyLevelUp_rejectsSpellAboveSlotLevel() {
        // Bard 4 -> 5: highest slot at level 5 is spell level 3 — a level-4 spell is unlearnable.
        PC bard = pc("Bard", 4, 14, 30, 30);
        assertThatThrownBy(() -> service.applyLevelUp(bard, null, null, null,
                List.of(spell(4, "Polymorph"))))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(400))
                .hasMessageContaining("highest spell level");
    }

    @Test
    void applyLevelUp_acceptsSpellAtExactlyTheNewSlotLevel() {
        // Bard 4 -> 5 gains its first level-3 slot — a level-3 spell is now learnable.
        PC bard = pc("Bard", 4, 14, 30, 30);

        service.applyLevelUp(bard, null, null, null, List.of(spell(3, "Fear")));

        assertThat(bard.getSpells()).contains("Fear");
    }

    @Test
    void applyLevelUp_rejectsSpellAbovePactSlotLevel_forWarlock() {
        // Warlock 4 -> 5: pact slots move to spell level 3 — a level-5 spell is out of reach.
        PC warlock = pc("Warlock", 4, 14, 30, 30);
        assertThatThrownBy(() -> service.applyLevelUp(warlock, null, null, null,
                List.of(spell(5, "Hold Monster"))))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void applyLevelUp_acceptsSpellAtPactSlotLevel_forWarlock() {
        // Warlock 4 -> 5: pact slot level is 3 — a level-3 spell is learnable.
        PC warlock = pc("Warlock", 4, 14, 30, 30);

        service.applyLevelUp(warlock, null, null, null, List.of(spell(3, "Fly")));

        assertThat(warlock.getSpells()).contains("Fly");
    }

    @Test
    void applyLevelUp_cantripsUnaffectedBySlotLevelCap() {
        // Cantrips are level 0 and never blocked by the slot-level cap (count-gated only).
        PC bard = pc("Bard", 9, 14, 60, 60); // 9 -> 10: cantrip delta 1

        service.applyLevelUp(bard, null, null, null, List.of(spell(0, "Mending")));

        assertThat(bard.getSpells()).contains("Mending");
    }

    @Test
    void applyLevelUp_rejectsSpellsForNonCaster() {
        PC fighter = pc("Fighter", 1, 14, 12, 12); // -> 2, no spell allowance
        assertThatThrownBy(() -> service.applyLevelUp(fighter, null, null, null, List.of(spell(1, "Shield"))))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(400));
    }

    // --- Rolled HP (HpMode.ROLL) ---

    /** A service whose hit-die roll always returns {@code roll} (so HP outcomes are deterministic). */
    private static LevelUpService serviceRolling(int roll) {
        RandomGenerator fixed = new Random() {
            @Override
            public int nextInt(int bound) {
                return roll - 1; // rollHitDie() adds 1, yielding exactly `roll`
            }
        };
        return new LevelUpService(new ObjectMapper(), fixed);
    }

    @Test
    void applyLevelUp_roll_usesServerRoll_notAverage() {
        // Fighter 4 -> 5, d10, CON 16 (+3); forced max roll 10 -> 13 HP (avg path would be 9).
        PC fighter = pc("Fighter", 4, 16, 30, 22);
        serviceRolling(10).applyLevelUp(fighter, null, null, null, null, HpMode.ROLL);

        assertThat(fighter.getLevel()).isEqualTo((short) 5);
        assertThat(fighter.getHpMax()).isEqualTo((short) 43);     // 30 + 13
        assertThat(fighter.getHpCurrent()).isEqualTo((short) 35); // 22 + 13
    }

    @Test
    void applyLevelUp_roll_minRoll_addsConMod() {
        // Fighter 4 -> 5, d10, CON 16 (+3); forced min roll 1 -> 1 + 3 = 4 HP.
        PC fighter = pc("Fighter", 4, 16, 30, 30);
        serviceRolling(1).applyLevelUp(fighter, null, null, null, null, HpMode.ROLL);
        assertThat(fighter.getHpMax()).isEqualTo((short) 34);
    }

    @Test
    void applyLevelUp_roll_flooredAtOne_withHeavyConPenalty() {
        // Sorcerer d6, CON 3 (-4); even a min roll of 1 -> 1 - 4 = -3, floored to 1.
        PC sorcerer = pc("Sorcerer", 1, 3, 4, 4);
        serviceRolling(1).applyLevelUp(sorcerer, null, null, null, null, HpMode.ROLL);
        assertThat(sorcerer.getHpMax()).isEqualTo((short) 5); // 4 + 1
    }

    @Test
    void applyLevelUp_roll_alwaysWithinHitDieRange_overManyRolls() {
        // Real RNG: every rolled gain must land in [max(1, 1+conMod), max(1, hitDie+conMod)].
        LevelUpService rolling = new LevelUpService(new ObjectMapper(), new Random(42));
        int hitDie = 10, conMod = 3; // Fighter, CON 16
        int min = Math.max(1, 1 + conMod);
        int max = Math.max(1, hitDie + conMod);
        for (int i = 0; i < 500; i++) {
            PC fighter = pc("Fighter", 4, 16, 30, 30);
            rolling.applyLevelUp(fighter, null, null, null, null, HpMode.ROLL);
            int gained = fighter.getHpMax() - 30;
            assertThat(gained).isBetween(min, max);
        }
    }

    @Test
    void applyLevelUp_roll_conIncrease_stillGrantsRetroactiveHp() {
        // Fighter 3 -> 4 with ASI CON 15 (+2) -> 17 (+3); forced max roll 10.
        // This level: rolled 10 + new CON mod 3 = 13; retroactive (4-1) * (3-2) = 3; total +16.
        PC fighter = pc("Fighter", 3, 15, 28, 28);
        serviceRolling(10).applyLevelUp(fighter, null, Map.of("CON", 2), null, null, HpMode.ROLL);

        assertThat(fighter.getAbilityCon()).isEqualTo((short) 17);
        assertThat(fighter.getHpMax()).isEqualTo((short) 44); // 28 + 13 + 3
        assertThat(fighter.getHpCurrent()).isEqualTo((short) 44);
    }

    @Test
    void applyLevelUp_averageMode_matchesDefault_unchanged() {
        // Explicit AVERAGE must equal the legacy default path (d10 avg 6 + CON 3 = 9), and the
        // (forced-max-roll) RNG must be ignored entirely in AVERAGE mode.
        PC viaDefault = pc("Fighter", 4, 16, 30, 22);
        service.applyLevelUp(viaDefault); // legacy default

        PC viaExplicit = pc("Fighter", 4, 16, 30, 22);
        serviceRolling(10).applyLevelUp(viaExplicit, null, null, null, null, HpMode.AVERAGE);

        assertThat(viaExplicit.getHpMax()).isEqualTo(viaDefault.getHpMax());   // 39
        assertThat(viaExplicit.getHpCurrent()).isEqualTo(viaDefault.getHpCurrent());
    }

    @Test
    void preview_alwaysAverage_evenWhenServerWouldRollHigh() {
        // Preview takes no mode: it always reports the average, regardless of the RNG.
        LevelUpPreview p = serviceRolling(10).preview(pc("Fighter", 4, 16, 30, 30));
        assertThat(p.hpGained()).isEqualTo(9); // d10 avg 6 + CON 3, not the forced roll of 13
    }

    // --- Death Knight (Oath of the Ebon Blade hero subclass) ---

    private static final String EBON_BLADE = "Oath of the Ebon Blade";

    private static PC deathKnight(int level) {
        PC dk = pc("Paladin", level, 14, 10 * level, 10 * level);
        dk.setSubclass(EBON_BLADE);
        return dk;
    }

    private static List<String> names(List<FeatureGain> gains) {
        return gains.stream().map(FeatureGain::name).toList();
    }

    private static int status(Throwable e) {
        return ((ResponseStatusException) e).getStatusCode().value();
    }

    @Test
    void ebonBlade_notOfferedAtPaladinSubclassChoice() {
        LevelUpPreview p = service.preview(pc("Paladin", 2, 14, 20, 20)); // 2 -> 3, no subclass yet
        assertThat(p.subclassDue()).isTrue();
        assertThat(p.subclassOptions()).contains("Oath of Devotion").doesNotContain(EBON_BLADE);
    }

    @Test
    void ebonBlade_rejectedAsLevelUpSubclassChoice() {
        PC paladin = pc("Paladin", 2, 14, 20, 20);
        assertThatThrownBy(() -> service.applyLevelUp(paladin, EBON_BLADE))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        assertThat(paladin.getSubclass()).isNull();
    }

    @Test
    void ebonBlade_subclassNotDueAtLevel3_whenAlreadySetFromCreation() {
        PC dk = deathKnight(2); // 2 -> 3 is the paladin subclass level
        assertThat(service.preview(dk).subclassDue()).isFalse();

        service.applyLevelUp(dk); // no subclass choice required
        assertThat(dk.getLevel()).isEqualTo((short) 3);
        assertThat(dk.getSubclass()).isEqualTo(EBON_BLADE);
    }

    @Test
    void ebonBlade_level2_runicSmiteReplacesDivineSmite() {
        List<String> gained = names(service.preview(deathKnight(1)).featuresGained());
        assertThat(gained).contains("Fighting Style", "Runic Smite").doesNotContain("Divine Smite");
    }

    @Test
    void ebonBlade_level11_runicStrikesReplacesRadiantStrikes() {
        List<String> gained = names(service.preview(deathKnight(10)).featuresGained());
        assertThat(gained).containsExactly("Runic Strikes");
    }

    @Test
    void ebonBlade_level14_improvedDeathCoilReplacesRestoringTouch() {
        List<String> gained = names(service.preview(deathKnight(13)).featuresGained());
        assertThat(gained).containsExactly("Improved Death Coil");
    }

    @Test
    void ebonBlade_addsSubclassFeaturesAtLevels3_5_7_15_20() {
        assertThat(names(service.preview(deathKnight(2)).featuresGained()))
                .containsExactly("Oath of the Ebon Blade Spells", "Runeforging", "Runic Power");
        List<FeatureGain> l5 = service.preview(deathKnight(4)).featuresGained();
        assertThat(names(l5)).containsExactly("Extra Attack", "Faithful Steed"); // steed swapped, not duplicated
        assertThat(l5.get(1).desc()).contains("undead").contains("Ghostly Stride");
        assertThat(names(service.preview(deathKnight(6)).featuresGained()))
                .containsExactly("Aura of Deathly Endurance");
        assertThat(names(service.preview(deathKnight(14)).featuresGained()))
                .containsExactly("Runic Mastery");
        assertThat(names(service.preview(deathKnight(19)).featuresGained()))
                .containsExactly("Aspect of the Horsemen");
    }

    @Test
    void normalPaladin_keepsGenericFeatures() {
        assertThat(names(service.preview(pc("Paladin", 1, 14, 10, 10)).featuresGained()))
                .contains("Divine Smite").doesNotContain("Runic Smite");
        PC devotion = pc("Paladin", 10, 14, 100, 100);
        devotion.setSubclass("Oath of Devotion");
        assertThat(names(service.preview(devotion).featuresGained())).containsExactly("Radiant Strikes");
    }

    @Test
    void ebonBlade_applyLevelUp_recordsSubclassFeatures() {
        PC dk = deathKnight(10);
        service.applyLevelUp(dk);
        assertThat(dk.getFeatures()).contains("\"name\":\"Runic Strikes\"").doesNotContain("\"name\":\"Radiant Strikes\"");
    }

    @Test
    void applyLevelUp_changesSpecialization_forDeathKnight() {
        PC dk = deathKnight(5); // 5 -> 6: not an ASI level, so no other choice is required
        dk.setSpecialization("Blood");
        service.applyLevelUp(dk, null, null, null, null, HpMode.AVERAGE, "Frost");
        assertThat(dk.getSpecialization()).isEqualTo("Frost");
        assertThat(dk.getLevel()).isEqualTo((short) 6);
    }

    @Test
    void applyLevelUp_nullSpecialization_keepsCurrent() {
        PC dk = deathKnight(4);
        dk.setSpecialization("Unholy");
        service.applyLevelUp(dk, null, null, null, null, HpMode.AVERAGE, null);
        assertThat(dk.getSpecialization()).isEqualTo("Unholy");
    }

    @Test
    void applyLevelUp_rejectsInvalidEbonBladeSpecialization() {
        PC dk = deathKnight(4);
        dk.setSpecialization("Blood");
        assertThatThrownBy(() -> service.applyLevelUp(dk, null, null, null, null, HpMode.AVERAGE, "Fire"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        assertThat(dk.getSpecialization()).isEqualTo("Blood");
    }

    @Test
    void applyLevelUp_rejectsSpecialization_onNonHeroSubclass() {
        PC paladin = pc("Paladin", 4, 14, 40, 40);
        paladin.setSubclass("Oath of Vengeance");
        assertThatThrownBy(() -> service.applyLevelUp(paladin, null, null, null, null, HpMode.AVERAGE, "Frost"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        assertThat(paladin.getSpecialization()).isNull();
    }

    // --- Demon Hunter (ranger + Demon Hunter hero subclass) ---

    private static final String DEMON_HUNTER = "Demon Hunter";

    private static PC demonHunter(int level) {
        PC dh = pc("Ranger", level, 14, 10 * level, 10 * level);
        dh.setSubclass(DEMON_HUNTER);
        return dh;
    }

    private static Map<String, Object> cantrip(String name) {
        return Map.of("name", name, "lvl", 0);
    }

    @Test
    void demonHunter_notOfferedAtRangerSubclassChoice() {
        LevelUpPreview p = service.preview(pc("Ranger", 2, 14, 20, 20)); // 2 -> 3, no subclass yet
        assertThat(p.subclassDue()).isTrue();
        assertThat(p.subclassOptions()).contains("Hunter").doesNotContain(DEMON_HUNTER);
        assertThatThrownBy(() -> service.applyLevelUp(pc("Ranger", 2, 14, 20, 20), DEMON_HUNTER))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
    }

    @Test
    void demonHunter_level2_swapsDeftExplorerAndFightingStyle_withoutDuplicating() {
        List<FeatureGain> l2 = service.preview(demonHunter(1)).featuresGained();
        assertThat(names(l2)).containsExactly("Deft Explorer", "Fighting Style");
        assertThat(l2.get(0).desc()).contains("Eredun").contains("Nathrezim");
        assertThat(l2.get(1).desc()).contains("Void Warrior");

        List<FeatureGain> normal = service.preview(pc("Ranger", 1, 14, 10, 10)).featuresGained();
        assertThat(names(normal)).containsExactly("Deft Explorer", "Fighting Style");
        assertThat(normal.get(0).desc()).doesNotContain("Eredun");
    }

    @Test
    void demonHunter_addsSubclassFeaturesAtLevels3_7_11_15() {
        assertThat(names(service.preview(demonHunter(2)).featuresGained()))
                .containsExactly("Demon Hunter Spells", "Soul Fragments", "Spectral Sight");
        assertThat(names(service.preview(demonHunter(6)).featuresGained())).containsExactly("Torment");
        assertThat(names(service.preview(demonHunter(10)).featuresGained())).containsExactly("Illidari Knowledge");
        assertThat(names(service.preview(demonHunter(14)).featuresGained())).containsExactly("Soul Shatter");
    }

    @Test
    void demonHunter_level18_improvedSpectralSightReplacesFeralSenses() {
        List<FeatureGain> l18 = service.preview(demonHunter(17)).featuresGained();
        assertThat(names(l18)).containsExactly("Improved Spectral Sight");
        assertThat(l18.get(0).desc()).contains("Truesight");
        assertThat(names(service.preview(pc("Ranger", 17, 14, 170, 170)).featuresGained()))
                .containsExactly("Feral Senses");
    }

    @Test
    void demonHunter_applyLevelUp_recordsSubclassFeatures() {
        PC dh = demonHunter(17);
        service.applyLevelUp(dh);
        assertThat(dh.getFeatures()).contains("\"name\":\"Improved Spectral Sight\"")
                .doesNotContain("\"name\":\"Feral Senses\"");
    }

    // Fighting Style choice + Void Warrior

    @Test
    void preview_fightingStyleOptions_includeVoidWarriorOnlyForDemonHunter() {
        LevelUpPreview dh = service.preview(demonHunter(1));
        assertThat(dh.fightingStyleDue()).isTrue();
        assertThat(dh.fightingStyleOptions()).contains("Archery", "Void Warrior").isSorted();

        LevelUpPreview ranger = service.preview(pc("Ranger", 1, 14, 10, 10));
        assertThat(ranger.fightingStyleDue()).isTrue();
        assertThat(ranger.fightingStyleOptions()).contains("Archery").doesNotContain("Void Warrior");

        assertThat(service.preview(deathKnight(1)).fightingStyleOptions())
                .contains("Defense").doesNotContain("Void Warrior");

        // A "Demon Hunter" subclass on the wrong base class doesn't unlock it either.
        PC paladin = pc("Paladin", 1, 14, 10, 10);
        paladin.setSubclass(DEMON_HUNTER);
        assertThat(service.preview(paladin).fightingStyleOptions()).doesNotContain("Void Warrior");
    }

    @Test
    void preview_fightingStyleNotDue_awayFromTheFightingStyleLevel() {
        LevelUpPreview p = service.preview(demonHunter(2)); // 2 -> 3
        assertThat(p.fightingStyleDue()).isFalse();
        assertThat(p.fightingStyleOptions()).isEmpty();
        assertThat(service.preview(pc("Wizard", 1, 14, 6, 6)).fightingStyleDue()).isFalse();
        assertThat(service.preview(pc("Fighter", 1, 14, 10, 10)).fightingStyleDue()).isFalse(); // fighter's is at level 1
    }

    @Test
    void applyLevelUp_voidWarrior_withTwoCantrips_recordsStyleAndSavesCantrips() {
        PC dh = demonHunter(1);
        service.applyLevelUp(dh, null, null, null,
                List.of(cantrip("Eldritch Blast"), cantrip("Chill Touch")), HpMode.AVERAGE, null, "Void Warrior");

        assertThat(dh.getLevel()).isEqualTo((short) 2);
        assertThat(dh.getSpells()).contains("Eldritch Blast").contains("Chill Touch");
        assertThat(dh.getFeatures()).contains("\"name\":\"Void Warrior\"").contains("Warlock cantrips");
    }

    @Test
    void applyLevelUp_voidWarrior_requiresExactlyTwoCantrips() {
        for (List<Map<String, Object>> spells : List.<List<Map<String, Object>>>of(
                List.of(),
                List.of(cantrip("Eldritch Blast")),
                List.of(cantrip("Eldritch Blast"), cantrip("Chill Touch"), cantrip("Minor Illusion")))) {
            PC dh = demonHunter(1);
            assertThatThrownBy(() -> service.applyLevelUp(dh, null, null, null, spells, HpMode.AVERAGE, null, "Void Warrior"))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(status(e)).isEqualTo(400));
            assertThat(dh.getLevel()).isEqualTo((short) 1);
        }
        PC noSpells = demonHunter(1);
        assertThatThrownBy(() -> service.applyLevelUp(noSpells, null, null, null, null, HpMode.AVERAGE, null, "Void Warrior"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
    }

    @Test
    void applyLevelUp_voidWarrior_rejectedForAnyoneButADemonHunter() {
        List<Map<String, Object>> two = List.of(cantrip("Eldritch Blast"), cantrip("Chill Touch"));
        for (PC other : List.of(pc("Ranger", 1, 14, 10, 10), deathKnight(1), pc("Paladin", 1, 14, 10, 10))) {
            assertThatThrownBy(() -> service.applyLevelUp(other, null, null, null, two, HpMode.AVERAGE, null, "Void Warrior"))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        }
        // ...and is not a feat at an ASI level, even for a Demon Hunter.
        assertThatThrownBy(() -> service.applyLevelUp(demonHunter(3), null, null, "Void Warrior"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
    }

    @Test
    void applyLevelUp_fightingStyle_rejectedAwayFromTheFightingStyleLevel() {
        PC dh = demonHunter(2); // 2 -> 3
        assertThatThrownBy(() -> service.applyLevelUp(dh, null, null, null,
                List.of(cantrip("Eldritch Blast"), cantrip("Chill Touch")), HpMode.AVERAGE, null, "Void Warrior"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
    }

    @Test
    void applyLevelUp_regularFightingStyle_isRecorded_andGrantsNoCantrips() {
        PC ranger = pc("Ranger", 1, 14, 10, 10);
        service.applyLevelUp(ranger, null, null, null, null, HpMode.AVERAGE, null, "archery");
        assertThat(ranger.getFeatures()).contains("\"name\":\"Archery\"");

        PC another = pc("Ranger", 1, 14, 10, 10);
        assertThatThrownBy(() -> service.applyLevelUp(another, null, null, null,
                List.of(cantrip("Eldritch Blast")), HpMode.AVERAGE, null, "Archery"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
    }

    // Demon Hunter specialization on level-up (no respec)

    @Test
    void applyLevelUp_demonHunter_cannotChangeSpecialization() {
        PC dh = demonHunter(5);
        dh.setSpecialization("Havoc");
        assertThatThrownBy(() -> service.applyLevelUp(dh, null, null, null, null, HpMode.AVERAGE, "Vengeance"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        assertThat(dh.getSpecialization()).isEqualTo("Havoc");
        assertThat(dh.getLevel()).isEqualTo((short) 5);
    }

    @Test
    void applyLevelUp_demonHunter_mayRepeatOrFirstChooseSpecialization() {
        PC same = demonHunter(5);
        same.setSpecialization("Havoc");
        service.applyLevelUp(same, null, null, null, null, HpMode.AVERAGE, "Havoc");
        assertThat(same.getSpecialization()).isEqualTo("Havoc");

        PC unset = demonHunter(5);
        service.applyLevelUp(unset, null, null, null, null, HpMode.AVERAGE, "Devourer");
        assertThat(unset.getSpecialization()).isEqualTo("Devourer");
    }

    @Test
    void applyLevelUp_demonHunter_rejectsAnotherHeroClassesSpecialization() {
        PC dh = demonHunter(5);
        assertThatThrownBy(() -> service.applyLevelUp(dh, null, null, null, null, HpMode.AVERAGE, "Blood"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        assertThat(dh.getSpecialization()).isNull();
    }

    @Test
    void applyLevelUp_ebonBlade_stillMayRespec_andRejectsDemonHunterValues() {
        PC dk = deathKnight(5);
        dk.setSpecialization("Blood");
        service.applyLevelUp(dk, null, null, null, null, HpMode.AVERAGE, "Unholy");
        assertThat(dk.getSpecialization()).isEqualTo("Unholy");

        PC dk2 = deathKnight(5);
        assertThatThrownBy(() -> service.applyLevelUp(dk2, null, null, null, null, HpMode.AVERAGE, "Havoc"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
    }

    // --- WoW Druid (druid + class specialization, no subclass lock) ---

    private static PC druid(int level) {
        PC d = pc("Druid", level, 14, 8 * level, 8 * level);
        if (level >= 3) d.setSubclass("Circle of the Moon");
        return d;
    }

    @Test
    void applyLevelUp_druid_acceptsEachWowSpecialization() {
        for (String spec : List.of("Balance", "Feral", "Guardian", "Restoration")) {
            PC d = druid(5); // 5 -> 6: not an ASI level
            service.applyLevelUp(d, null, null, null, null, HpMode.AVERAGE, spec);
            assertThat(d.getSpecialization()).isEqualTo(spec);
            assertThat(d.getLevel()).isEqualTo((short) 6);
        }
    }

    @Test
    void applyLevelUp_druid_mayChangeSpecialization() {
        PC d = druid(5);
        d.setSpecialization("Balance");
        service.applyLevelUp(d, null, null, null, null, HpMode.AVERAGE, "Restoration");
        assertThat(d.getSpecialization()).isEqualTo("Restoration");
    }

    @Test
    void applyLevelUp_druid_nullSpecialization_keepsCurrent() {
        PC d = druid(5);
        d.setSpecialization("Feral");
        service.applyLevelUp(d, null, null, null, null, HpMode.AVERAGE, null);
        assertThat(d.getSpecialization()).isEqualTo("Feral");
    }

    @Test
    void applyLevelUp_druid_rejectsInvalidSpecialization() {
        for (String bad : List.of("balance", "Havoc", "Blood", "Shadow")) {
            PC d = druid(5);
            d.setSpecialization("Guardian");
            assertThatThrownBy(() -> service.applyLevelUp(d, null, null, null, null, HpMode.AVERAGE, bad))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(status(e)).isEqualTo(400));
            assertThat(d.getSpecialization()).isEqualTo("Guardian");
            assertThat(d.getLevel()).isEqualTo((short) 5);
        }
    }

    @Test
    void applyLevelUp_rejectsDruidSpecialization_onFighter() {
        PC fighter = pc("Fighter", 5, 14, 50, 50);
        fighter.setSubclass("Champion");
        assertThatThrownBy(() -> service.applyLevelUp(fighter, null, null, null, null, HpMode.AVERAGE, "Balance"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        assertThat(fighter.getSpecialization()).isNull();
    }

    @Test
    void applyLevelUp_wowDruid_stillPicksANormalCircleAtLevelThree() {
        PC d = druid(2);
        d.setSpecialization("Balance");
        service.applyLevelUp(d, "Circle of the Land", null, null, null, HpMode.AVERAGE, null);
        assertThat(d.getSubclass()).isEqualTo("Circle of the Land"); // recommendation is not enforced
        assertThat(d.getSpecialization()).isEqualTo("Balance");
    }

    @Test
    void preview_wowDruid_recommendsSubclassWhenDue() {
        String[][] cases = {
                {"Balance", "Circle of the Stars"},
                {"Feral", "Circle of the Moon"},
                {"Guardian", "Circle of the Moon"},
                {"Restoration", "Circle of the Land"},
        };
        for (String[] c : cases) {
            PC d = druid(2);
            d.setSpecialization(c[0]);
            LevelUpPreview p = service.preview(d);
            assertThat(p.subclassDue()).isTrue();
            assertThat(p.subclassOptions()).contains(c[1]);
            assertThat(p.recommendedSubclass()).isEqualTo(c[1]);
        }
    }

    @Test
    void preview_noRecommendedSubclass_forBaseDruid_orWhenNotDue() {
        assertThat(service.preview(druid(2)).recommendedSubclass()).isNull();

        PC later = druid(4);
        later.setSpecialization("Balance");
        assertThat(service.preview(later).recommendedSubclass()).isNull();

        assertThat(service.preview(pc("Cleric", 2, 14, 14, 14)).recommendedSubclass()).isNull();
    }

    // --- WoW Paladin (paladin + Holy/Protection/Retribution, Oath of the Holy) ---

    private static final String OATH_OF_THE_HOLY = "Oath of the Holy";
    private static final List<String> PALADIN_SPECS = List.of("Holy", "Protection", "Retribution");

    private static PC paladin(int level) {
        PC p = pc("Paladin", level, 14, 10 * level, 10 * level);
        if (level >= 3) p.setSubclass("Oath of Devotion");
        return p;
    }

    private static PC wowPaladin(int level, String spec) {
        PC p = paladin(level);
        p.setSpecialization(spec);
        return p;
    }

    private static PC holyPaladin(int level) {
        PC p = pc("Paladin", level, 14, 10 * level, 10 * level);
        p.setSubclass(OATH_OF_THE_HOLY);
        p.setSpecialization("Holy");
        return p;
    }

    @Test
    void applyLevelUp_paladin_acceptsEachWowSpecialization() {
        for (String spec : PALADIN_SPECS) {
            PC p = paladin(5); // 5 -> 6: not an ASI level
            service.applyLevelUp(p, null, null, null, null, HpMode.AVERAGE, spec);
            assertThat(p.getSpecialization()).isEqualTo(spec);
            assertThat(p.getLevel()).isEqualTo((short) 6);
        }
    }

    @Test
    void applyLevelUp_wowPaladin_mayRespec() {
        PC p = wowPaladin(5, "Holy");
        service.applyLevelUp(p, null, null, null, null, HpMode.AVERAGE, "Retribution");
        assertThat(p.getSpecialization()).isEqualTo("Retribution");
    }

    @Test
    void applyLevelUp_deathKnightCannotTakeHoly_andWowPaladinCannotTakeFrost() {
        PC dk = deathKnight(5);
        dk.setSpecialization("Blood");
        assertThatThrownBy(() -> service.applyLevelUp(dk, null, null, null, null, HpMode.AVERAGE, "Holy"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        assertThat(dk.getSpecialization()).isEqualTo("Blood");

        PC p = wowPaladin(5, "Protection");
        assertThatThrownBy(() -> service.applyLevelUp(p, null, null, null, null, HpMode.AVERAGE, "Frost"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        assertThat(p.getSpecialization()).isEqualTo("Protection");
    }

    @Test
    void preview_oathOfTheHoly_offeredOnlyToWowPaladins() {
        LevelUpPreview base = service.preview(paladin(2)); // 2 -> 3, no specialization
        assertThat(base.subclassDue()).isTrue();
        assertThat(base.subclassOptions())
                .contains("Oath of Devotion", "Oath of Glory", "Oath of the Ancients", "Oath of Vengeance")
                .doesNotContain(OATH_OF_THE_HOLY);

        for (String spec : PALADIN_SPECS) {
            LevelUpPreview wow = service.preview(wowPaladin(2, spec));
            assertThat(wow.subclassOptions())
                    .contains("Oath of Devotion", "Oath of Glory", "Oath of the Ancients", "Oath of Vengeance",
                            OATH_OF_THE_HOLY);
        }

        // A WoW druid doesn't get it either.
        PC d = druid(2);
        d.setSpecialization("Restoration");
        assertThat(service.preview(d).subclassOptions()).doesNotContain(OATH_OF_THE_HOLY);
    }

    @Test
    void applyLevelUp_oathOfTheHoly_acceptedForWowPaladin_andGrantsLevel3Features() {
        PC p = wowPaladin(2, "Protection");
        service.applyLevelUp(p, "Oath of the Holy");
        assertThat(p.getSubclass()).isEqualTo(OATH_OF_THE_HOLY);
        assertThat(p.getLevel()).isEqualTo((short) 3);
        assertThat(p.getFeatures()).contains("\"name\":\"Oath of the Holy Spells\"")
                .contains("\"name\":\"Beacon of Light\"");
    }

    @Test
    void applyLevelUp_oathOfTheHoly_rejectedForBasePaladin() {
        PC p = paladin(2);
        assertThatThrownBy(() -> service.applyLevelUp(p, OATH_OF_THE_HOLY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        assertThat(p.getSubclass()).isNull();
        assertThat(p.getLevel()).isEqualTo((short) 2);

        // Nor for another class with a WoW specialization.
        PC d = druid(2);
        d.setSpecialization("Restoration");
        assertThatThrownBy(() -> service.applyLevelUp(d, OATH_OF_THE_HOLY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
    }

    @Test
    void applyLevelUp_oathOfTheHoly_acceptedWhenTheSameRequestSetsTheSpecialization() {
        PC p = paladin(2);
        service.applyLevelUp(p, OATH_OF_THE_HOLY, null, null, null, HpMode.AVERAGE, "Holy");
        assertThat(p.getSubclass()).isEqualTo(OATH_OF_THE_HOLY);
        assertThat(p.getSpecialization()).isEqualTo("Holy");

        PC bad = paladin(2);
        assertThatThrownBy(() -> service.applyLevelUp(bad, OATH_OF_THE_HOLY, null, null, null, HpMode.AVERAGE, "Frost"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
    }

    @Test
    void applyLevelUp_wowPaladin_stillMayPickAPhbOath() {
        PC p = wowPaladin(2, "Holy");
        service.applyLevelUp(p, "Oath of Glory");
        assertThat(p.getSubclass()).isEqualTo("Oath of Glory"); // recommendation is not enforced
    }

    @Test
    void preview_wowPaladin_recommendsSubclassWhenDue() {
        String[][] cases = {
                {"Holy", OATH_OF_THE_HOLY},
                {"Protection", "Oath of Devotion"},
                {"Retribution", "Oath of Vengeance"},
        };
        for (String[] c : cases) {
            LevelUpPreview p = service.preview(wowPaladin(2, c[0]));
            assertThat(p.subclassDue()).isTrue();
            assertThat(p.subclassOptions()).contains(c[1]);
            assertThat(p.recommendedSubclass()).isEqualTo(c[1]);
        }
        assertThat(service.preview(paladin(2)).recommendedSubclass()).isNull();
        assertThat(service.preview(wowPaladin(4, "Holy")).recommendedSubclass()).isNull();
    }

    @Test
    void oathOfTheHoly_addsFeaturesAt3_7_15_20() {
        assertThat(names(service.preview(holyPaladin(6)).featuresGained())).containsExactly("Lightbringer's Aura");
        assertThat(names(service.preview(holyPaladin(14)).featuresGained())).containsExactly("Infusion of Light");
        assertThat(names(service.preview(holyPaladin(19)).featuresGained())).containsExactly("Holy Deliverance");
        // Generic paladin features at those levels are kept alongside (none at 3/7/15/20 today).
        assertThat(names(service.preview(holyPaladin(5)).featuresGained())).containsExactly("Aura of Protection");
        assertThat(names(service.preview(paladin(6)).featuresGained())).isEmpty();

        PC l3 = wowPaladin(2, "Holy");
        service.applyLevelUp(l3, OATH_OF_THE_HOLY);
        assertThat(l3.getFeatures()).contains("Cure Wounds").contains("Mass Cure Wounds").contains("60 feet");

        PC l20 = holyPaladin(19);
        service.applyLevelUp(l20);
        assertThat(l20.getFeatures()).contains("\"name\":\"Holy Deliverance\"").contains("sunlight");
    }

    // Blessed Warrior / Druidic Warrior (cantrip Fighting Styles)

    private static final List<Map<String, Object>> TWO_CANTRIPS =
            List.of(cantrip("Sacred Flame"), cantrip("Guidance"));

    @Test
    void preview_cantripFightingStyles_offeredByClass() {
        assertThat(service.preview(pc("Paladin", 1, 14, 10, 10)).fightingStyleOptions())
                .contains("Archery", "Blessed Warrior").doesNotContain("Druidic Warrior", "Void Warrior").isSorted();
        assertThat(service.preview(deathKnight(1)).fightingStyleOptions())
                .contains("Blessed Warrior").doesNotContain("Druidic Warrior", "Void Warrior");
        assertThat(service.preview(pc("Ranger", 1, 14, 10, 10)).fightingStyleOptions())
                .contains("Archery", "Druidic Warrior").doesNotContain("Blessed Warrior", "Void Warrior").isSorted();
        assertThat(service.preview(demonHunter(1)).fightingStyleOptions())
                .contains("Druidic Warrior", "Void Warrior").doesNotContain("Blessed Warrior");
    }

    @Test
    void applyLevelUp_blessedWarrior_withTwoCantrips_recordsStyleAndSavesCantrips() {
        PC p = pc("Paladin", 1, 14, 10, 10);
        service.applyLevelUp(p, null, null, null, TWO_CANTRIPS, HpMode.AVERAGE, null, "blessed warrior");
        assertThat(p.getLevel()).isEqualTo((short) 2);
        assertThat(p.getSpells()).contains("Sacred Flame").contains("Guidance");
        assertThat(p.getFeatures()).contains("\"name\":\"Blessed Warrior\"").contains("Cleric cantrips")
                .contains("Charisma");
    }

    @Test
    void applyLevelUp_druidicWarrior_withTwoCantrips_recordsStyleAndSavesCantrips() {
        for (PC r : List.of(pc("Ranger", 1, 14, 10, 10), demonHunter(1))) {
            service.applyLevelUp(r, null, null, null,
                    List.of(cantrip("Druidcraft"), cantrip("Thorn Whip")), HpMode.AVERAGE, null, "Druidic Warrior");
            assertThat(r.getLevel()).isEqualTo((short) 2);
            assertThat(r.getSpells()).contains("Druidcraft").contains("Thorn Whip");
            assertThat(r.getFeatures()).contains("\"name\":\"Druidic Warrior\"").contains("Druid cantrips")
                    .contains("Wisdom");
        }
    }

    @Test
    void applyLevelUp_cantripFightingStyles_requireExactlyTwoCantrips() {
        Object[][] cases = {{"Paladin", "Blessed Warrior"}, {"Ranger", "Druidic Warrior"}};
        for (Object[] c : cases) {
            for (List<Map<String, Object>> spells : List.<List<Map<String, Object>>>of(
                    List.of(),
                    List.of(cantrip("Sacred Flame")),
                    List.of(cantrip("Sacred Flame"), cantrip("Guidance"), cantrip("Thaumaturgy")))) {
                PC p = pc((String) c[0], 1, 14, 10, 10);
                assertThatThrownBy(() -> service.applyLevelUp(p, null, null, null, spells, HpMode.AVERAGE, null, (String) c[1]))
                        .isInstanceOf(ResponseStatusException.class)
                        .satisfies(e -> assertThat(status(e)).isEqualTo(400));
                assertThat(p.getLevel()).isEqualTo((short) 1);
            }
            PC noSpells = pc((String) c[0], 1, 14, 10, 10);
            assertThatThrownBy(() -> service.applyLevelUp(noSpells, null, null, null, null, HpMode.AVERAGE, null, (String) c[1]))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        }
    }

    @Test
    void applyLevelUp_cantripFightingStyles_rejectedForTheOtherClass_andAsFeats() {
        assertThatThrownBy(() -> service.applyLevelUp(pc("Ranger", 1, 14, 10, 10), null, null, null,
                TWO_CANTRIPS, HpMode.AVERAGE, null, "Blessed Warrior"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        assertThatThrownBy(() -> service.applyLevelUp(pc("Paladin", 1, 14, 10, 10), null, null, null,
                TWO_CANTRIPS, HpMode.AVERAGE, null, "Druidic Warrior"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        assertThatThrownBy(() -> service.applyLevelUp(pc("Fighter", 1, 14, 10, 10), null, null, null,
                TWO_CANTRIPS, HpMode.AVERAGE, null, "Blessed Warrior"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        assertThatThrownBy(() -> service.applyLevelUp(pc("Paladin", 1, 14, 10, 10), null, null, null,
                TWO_CANTRIPS, HpMode.AVERAGE, null, "Void Warrior"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));

        // Not feats at an ASI level.
        assertThat(service.preview(paladin(3)).featOptions()).doesNotContain("Blessed Warrior", "Druidic Warrior");
        assertThatThrownBy(() -> service.applyLevelUp(paladin(3), null, null, "Blessed Warrior"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        PC ranger = pc("Ranger", 3, 14, 30, 30);
        ranger.setSubclass("Hunter");
        assertThatThrownBy(() -> service.applyLevelUp(ranger, null, null, "Druidic Warrior"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
    }

    // Recommended Fighting Styles per WoW Paladin specialization

    @Test
    void preview_wowPaladin_recommendsFightingStylesWhenDue() {
        Map<String, List<String>> cases = Map.of(
                "Holy", List.of("Blessed Warrior"),
                "Protection", List.of("Interception", "Protection"),
                "Retribution", List.of("Great Weapon Fighting"));
        cases.forEach((spec, expected) -> {
            PC p = pc("Paladin", 1, 14, 10, 10);
            p.setSpecialization(spec);
            LevelUpPreview preview = service.preview(p);
            assertThat(preview.fightingStyleDue()).isTrue();
            assertThat(preview.recommendedFightingStyles()).containsExactlyElementsOf(expected);
            assertThat(preview.fightingStyleOptions()).containsAll(expected);
        });
    }

    @Test
    void preview_noRecommendedFightingStyles_forBasePaladin_otherClasses_orWhenNotDue() {
        assertThat(service.preview(pc("Paladin", 1, 14, 10, 10)).recommendedFightingStyles()).isEmpty();
        PC dk = deathKnight(1);
        dk.setSpecialization("Frost");
        assertThat(service.preview(dk).recommendedFightingStyles()).isEmpty();
        assertThat(service.preview(pc("Ranger", 1, 14, 10, 10)).recommendedFightingStyles()).isEmpty();
        assertThat(service.preview(wowPaladin(2, "Holy")).recommendedFightingStyles()).isEmpty();
        PC d = druid(1);
        d.setSpecialization("Feral");
        assertThat(service.preview(d).recommendedFightingStyles()).isEmpty();
    }

    // --- WoW Rogue (rogue + Assassination/Outlaw/Subtlety, Swashbuckler) ---

    private static final String SWASHBUCKLER = "Swashbuckler";
    private static final List<String> ROGUE_SPECS = List.of("Assassination", "Outlaw", "Subtlety");
    private static final List<String> PHB_ROGUE_SUBCLASSES =
            List.of("Arcane Trickster", "Assassin", "Soulknife", "Thief");

    private static PC rogue(int level) {
        PC r = pc("Rogue", level, 14, 8 * level, 8 * level);
        if (level >= 3) r.setSubclass("Thief");
        return r;
    }

    private static PC wowRogue(int level, String spec) {
        PC r = rogue(level);
        r.setSpecialization(spec);
        return r;
    }

    private static PC swashbuckler(int level) {
        PC r = pc("Rogue", level, 14, 8 * level, 8 * level);
        r.setSubclass(SWASHBUCKLER);
        r.setSpecialization("Outlaw");
        return r;
    }

    @Test
    void applyLevelUp_rogue_acceptsEachWowSpecialization_andRejectsOthers() {
        for (String spec : ROGUE_SPECS) {
            PC r = rogue(5); // 5 -> 6: not an ASI level
            service.applyLevelUp(r, null, null, null, null, HpMode.AVERAGE, spec);
            assertThat(r.getSpecialization()).isEqualTo(spec);
            assertThat(r.getLevel()).isEqualTo((short) 6);
        }
        for (String bad : List.of("outlaw", "Combat", "Holy", "Frost")) {
            PC r = wowRogue(5, "Outlaw");
            assertThatThrownBy(() -> service.applyLevelUp(r, null, null, null, null, HpMode.AVERAGE, bad))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(status(e)).isEqualTo(400));
            assertThat(r.getSpecialization()).isEqualTo("Outlaw");
            assertThat(r.getLevel()).isEqualTo((short) 5);
        }
    }

    @Test
    void applyLevelUp_wowRogue_mayRespec() {
        PC r = wowRogue(5, "Assassination");
        service.applyLevelUp(r, null, null, null, null, HpMode.AVERAGE, "Subtlety");
        assertThat(r.getSpecialization()).isEqualTo("Subtlety");
    }

    @Test
    void preview_swashbuckler_offeredOnlyToWowRogues() {
        LevelUpPreview base = service.preview(rogue(2)); // 2 -> 3, no specialization
        assertThat(base.subclassDue()).isTrue();
        assertThat(base.subclassOptions()).containsExactlyElementsOf(PHB_ROGUE_SUBCLASSES);

        for (String spec : ROGUE_SPECS) {
            assertThat(service.preview(wowRogue(2, spec)).subclassOptions())
                    .containsAll(PHB_ROGUE_SUBCLASSES).contains(SWASHBUCKLER)
                    .doesNotContain(OATH_OF_THE_HOLY);
        }

        // Other WoW classes don't get it.
        assertThat(service.preview(wowPaladin(2, "Holy")).subclassOptions()).doesNotContain(SWASHBUCKLER);
        PC d = druid(2);
        d.setSpecialization("Feral");
        assertThat(service.preview(d).subclassOptions()).doesNotContain(SWASHBUCKLER);
    }

    @Test
    void applyLevelUp_swashbuckler_acceptedForWowRogue_andGrantsLevel3Features() {
        PC r = wowRogue(2, "Subtlety");
        service.applyLevelUp(r, SWASHBUCKLER);
        assertThat(r.getSubclass()).isEqualTo(SWASHBUCKLER);
        assertThat(r.getLevel()).isEqualTo((short) 3);
        assertThat(r.getFeatures()).contains("\"name\":\"Steady Aim\"")
                .contains("\"name\":\"Fancy Footwork\"")
                .contains("\"name\":\"Rakish Audacity\"");
    }

    @Test
    void applyLevelUp_swashbuckler_acceptedWhenTheSameRequestSetsTheSpecialization() {
        PC r = rogue(2);
        service.applyLevelUp(r, SWASHBUCKLER, null, null, null, HpMode.AVERAGE, "Outlaw");
        assertThat(r.getSubclass()).isEqualTo(SWASHBUCKLER);
        assertThat(r.getSpecialization()).isEqualTo("Outlaw");
    }

    @Test
    void applyLevelUp_swashbuckler_rejectedForBaseRogue_andOtherWowClasses() {
        PC r = rogue(2);
        assertThatThrownBy(() -> service.applyLevelUp(r, SWASHBUCKLER))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        assertThat(r.getSubclass()).isNull();
        assertThat(r.getLevel()).isEqualTo((short) 2);

        PC p = wowPaladin(2, "Retribution");
        assertThatThrownBy(() -> service.applyLevelUp(p, SWASHBUCKLER))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));

        // And a WoW rogue can't take the paladin's WoW-only oath.
        PC wr = wowRogue(2, "Outlaw");
        assertThatThrownBy(() -> service.applyLevelUp(wr, OATH_OF_THE_HOLY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
    }

    @Test
    void applyLevelUp_wowRogue_stillMayPickAPhbSubclass() {
        PC r = wowRogue(2, "Outlaw");
        service.applyLevelUp(r, "Thief");
        assertThat(r.getSubclass()).isEqualTo("Thief"); // recommendation is not enforced
    }

    @Test
    void preview_wowRogue_recommendsSubclassWhenDue() {
        String[][] cases = {
                {"Assassination", "Assassin"},
                {"Outlaw", SWASHBUCKLER},
                {"Subtlety", "Soulknife"},
        };
        for (String[] c : cases) {
            LevelUpPreview p = service.preview(wowRogue(2, c[0]));
            assertThat(p.subclassDue()).isTrue();
            assertThat(p.subclassOptions()).contains(c[1]);
            assertThat(p.recommendedSubclass()).isEqualTo(c[1]);
            assertThat(p.recommendedFightingStyles()).isEmpty();
        }
        assertThat(service.preview(rogue(2)).recommendedSubclass()).isNull();
        assertThat(service.preview(wowRogue(4, "Outlaw")).recommendedSubclass()).isNull();
    }

    @Test
    void swashbuckler_addsFeaturesAt3_9_13_17() {
        assertThat(names(service.preview(swashbuckler(8)).featuresGained())).containsExactly("Panache");
        assertThat(names(service.preview(swashbuckler(12)).featuresGained())).containsExactly("Dashing Strikes");
        assertThat(names(service.preview(swashbuckler(16)).featuresGained())).containsExactly("Master Duelist");
        // Generic rogue features at other levels are unaffected, and a PHB rogue gains nothing at 9/13/17.
        assertThat(names(service.preview(swashbuckler(4)).featuresGained()))
                .containsExactly("Cunning Strike", "Uncanny Dodge");
        assertThat(names(service.preview(rogue(8)).featuresGained())).isEmpty();
        assertThat(names(service.preview(rogue(12)).featuresGained())).isEmpty();
        assertThat(names(service.preview(rogue(16)).featuresGained())).isEmpty();

        PC l17 = swashbuckler(16);
        service.applyLevelUp(l17);
        assertThat(l17.getFeatures()).contains("\"name\":\"Master Duelist\"").contains("Sneak Attack");

        PC l9 = swashbuckler(8);
        service.applyLevelUp(l9);
        assertThat(l9.getFeatures()).contains("Goad").contains("Awe");
    }
}
