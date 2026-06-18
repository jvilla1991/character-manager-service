package com.moo.charactermanagerservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moo.charactermanagerservice.dto.FeatureGain;
import com.moo.charactermanagerservice.dto.LevelUpPreview;
import com.moo.charactermanagerservice.models.PC;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

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
        wizard.setSpellSlots("not-valid-json");

        service.applyLevelUp(wizard);

        // Malformed prior slots -> rebuilt fresh from the L3 table (4/2), used = 0.
        assertThat(wizard.getSpellSlots()).isEqualTo("{\"1\":{\"max\":4,\"used\":0},\"2\":{\"max\":2,\"used\":0}}");
    }

    // --- subclass selection mechanism (Phase 3 — catalog intentionally empty) ---

    @Test
    void preview_subclassDue_atGrantLevelWithNoSubclass() {
        // Cleric grant level is 3; leveling 2 -> 3 with no subclass yet.
        LevelUpPreview p = service.preview(pc("Cleric", 2, 14, 14, 14));
        assertThat(p.subclassDue()).isTrue();
        assertThat(p.subclassOptions()).isEmpty(); // no catalog content yet
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
        // Catalog empty -> server can't validate membership, so any non-blank choice is accepted.
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
    void applyLevelUp_dueButNoChoiceAndEmptyCatalog_proceedsWithoutSubclass() {
        // With no catalog content the flow is dormant: a level-up still succeeds with no subclass.
        PC cleric = pc("Cleric", 2, 14, 14, 14);

        service.applyLevelUp(cleric, null);

        assertThat(cleric.getLevel()).isEqualTo((short) 3);
        assertThat(cleric.getSubclass()).isNull();
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

    // --- Auto-granted class features ---

    @Test
    void preview_featuresGained_atSeededLevel() {
        // Barbarian 1 -> 2 grants Reckless Attack + Danger Sense (seeded).
        List<String> names = service.preview(pc("Barbarian", 1, 14, 14, 14)).featuresGained()
                .stream().map(FeatureGain::name).toList();
        assertThat(names).contains("Reckless Attack", "Danger Sense");
    }

    @Test
    void preview_featuresGained_emptyForUnseededClassOrLevel() {
        assertThat(service.preview(pc("Wizard", 1, 14, 8, 8)).featuresGained()).isEmpty();   // Wizard not seeded
        assertThat(service.preview(pc("Barbarian", 5, 14, 50, 50)).featuresGained()).isEmpty(); // -> 6 not seeded
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
    void applyLevelUp_unseededClass_leavesFeaturesUntouched() {
        PC wizard = pc("Wizard", 1, 14, 8, 8); // -> 2, not seeded
        wizard.setFeatures("[]");

        service.applyLevelUp(wizard);

        assertThat(wizard.getFeatures()).isEqualTo("[]");
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
}
