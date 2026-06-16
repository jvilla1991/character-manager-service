package com.moo.charactermanagerservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moo.charactermanagerservice.dto.LevelUpPreview;
import com.moo.charactermanagerservice.models.PC;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

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
        PC bard = pc("Bard", 7, 14, 50, 50);
        bard.setSpellSlots("{\"1\":{\"max\":4,\"used\":2},\"4\":{\"max\":1,\"used\":1}}");

        service.applyLevelUp(bard);

        // L8: level-4 max grows 1 -> 2 but used (1) is preserved; level-1 used (2) preserved.
        assertThat(bard.getSpellSlots()).contains("\"4\":{\"max\":2,\"used\":1}");
        assertThat(bard.getSpellSlots()).contains("\"1\":{\"max\":4,\"used\":2}");
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
        PC bard = pc("Bard", 7, 14, 50, 50);
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
}
