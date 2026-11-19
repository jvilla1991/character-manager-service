package com.moo.charactermanagerservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moo.charactermanagerservice.dto.FeatureGain;
import com.moo.charactermanagerservice.dto.HpMode;
import com.moo.charactermanagerservice.dto.LevelUpPreview;
import com.moo.charactermanagerservice.dto.SpellGrant;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.progression.ClassProgression;
import com.moo.charactermanagerservice.progression.FeatCatalog;
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

    // --- half casters (Paladin, Ranger): slots and prepared-spell picks at level-up ---

    @Test
    void preview_halfCaster_reportsSlotChanges_andThePreparedSpellDelta() {
        PC paladin = pc("Paladin", 4, 14, 40, 40); // 4 -> 5: the first 2nd-level slots
        paladin.setSubclass("Oath of Devotion");
        paladin.setSpellSlots("{\"1\":{\"max\":3,\"used\":1}}");

        LevelUpPreview p = service.preview(paladin);

        assertThat(p.currentSpellSlots()).isEqualTo(Map.of(1, 3));
        assertThat(p.newSpellSlots()).isEqualTo(Map.of(1, 4, 2, 2));
        // The class teaches no cantrips; prepared spells go 5 -> 6.
        assertThat(p.currentCantripsKnown()).isZero();
        assertThat(p.newCantripsKnown()).isZero();
        assertThat(p.currentSpellsKnown()).isEqualTo(5);
        assertThat(p.newSpellsKnown()).isEqualTo(6);
    }

    @Test
    void preview_halfCaster_preparedSpellDelta_followsThe2024Table() {
        // New prepared spells gained on reaching each level 2..20 (2024 PHB Paladin / Ranger).
        int[] gained = {1, 1, 1, 1, 0, 1, 0, 2, 0, 1, 0, 1, 0, 1, 0, 2, 0, 1, 0};
        for (String clazz : List.of("Paladin", "Ranger")) {
            for (int level = 1; level < 20; level++) {
                LevelUpPreview p = service.preview(pc(clazz, level, 14, 10 * level, 10 * level));
                assertThat(p.newSpellsKnown() - p.currentSpellsKnown()).as(clazz + " " + level + " -> " + (level + 1))
                        .isEqualTo(gained[level - 1]);
            }
        }
    }

    @Test
    void preview_wowVariantsOnHalfCasters_getTheSamePreparedSpellDelta() {
        // Death Knight and WoW Paladin are stored as paladins; Demon Hunter and WoW Hunter as rangers.
        for (PC hero : List.of(deathKnight(1), demonHunter(1), wowPaladin(1, "Holy"), hunter(1, null, "Survival"))) {
            LevelUpPreview p = service.preview(hero);
            assertThat(p.currentSpellsKnown()).as(hero.getClazz()).isEqualTo(2);
            assertThat(p.newSpellsKnown()).as(hero.getClazz()).isEqualTo(3);
            assertThat(p.newSpellSlots()).isEqualTo(Map.of(1, 2));
        }
    }

    @Test
    void preview_halfCaster_withNoStoredSlots_showsTheTableAsNew() {
        // A paladin made before half casters had slots: nothing stored yet.
        LevelUpPreview p = service.preview(pc("Ranger", 1, 14, 12, 12));

        assertThat(p.currentSpellSlots()).isEmpty();
        assertThat(p.newSpellSlots()).isEqualTo(Map.of(1, 2));
    }

    @Test
    void applyLevelUp_halfCaster_rebuildsSlotsPreservingUsed() {
        PC ranger = pc("Ranger", 4, 14, 40, 40); // 4 -> 5 (not an ASI level)
        ranger.setSubclass("Hunter");
        ranger.setSpellSlots("{\"1\":{\"max\":3,\"used\":2}}");

        service.applyLevelUp(ranger);

        assertThat(ranger.getSpellSlots())
                .isEqualTo("{\"1\":{\"max\":4,\"used\":2},\"2\":{\"max\":2,\"used\":0}}");
    }

    @Test
    void applyLevelUp_halfCaster_withNoStoredSlots_getsTheTable() {
        PC paladin = pc("Paladin", 1, 14, 12, 12); // 1 -> 2
        paladin.setSpellSlots(null);

        service.applyLevelUp(paladin);

        assertThat(paladin.getSpellSlots()).isEqualTo("{\"1\":{\"max\":2,\"used\":0}}");
    }

    @Test
    void applyLevelUp_halfCaster_learnsItsNewPreparedSpell() {
        PC paladin = pc("Paladin", 1, 14, 12, 12); // 1 -> 2: prepared 2 -> 3
        paladin.setSpells("[{\"lvl\":1,\"name\":\"Bless\"},{\"lvl\":1,\"name\":\"Heroism\"}]");

        service.applyLevelUp(paladin, null, null, null, List.of(spell(1, "Cure Wounds")));

        assertThat(paladin.getLevel()).isEqualTo((short) 2);
        assertThat(paladin.getSpells()).contains("Bless").contains("Heroism").contains("Cure Wounds");
    }

    @Test
    void applyLevelUp_halfCaster_rejectsMoreSpellsThanTheDelta() {
        PC ranger = pc("Ranger", 1, 14, 12, 12); // 1 -> 2: one new spell
        assertThatThrownBy(() -> service.applyLevelUp(ranger, null, null, null,
                List.of(spell(1, "Cure Wounds"), spell(1, "Goodberry"))))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(400))
                .hasMessageContaining("at most 1 new spell");
        assertThat(ranger.getLevel()).isEqualTo((short) 1);

        // 5 -> 6 has no new prepared spell at all.
        PC paladin = pc("Paladin", 5, 14, 50, 50);
        paladin.setSubclass("Oath of Devotion");
        assertThatThrownBy(() -> service.applyLevelUp(paladin, null, null, null, List.of(spell(1, "Bless"))))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(400));
    }

    // --- DM bonus slot lines (statAdjustments slot1..slot9) count at level-up too ---

    @Test
    void applyLevelUp_keepsTheDmsBonusSlots_onTopOfTheNewTable() {
        PC bard = pc("Bard", 8, 14, 50, 50); // 8 -> 9 (not an ASI level)
        bard.setStatAdjustments("{\"slot1\":[{\"value\":1,\"label\":\"Boon\"}]}");
        bard.setSpellSlots("{\"1\":{\"max\":5,\"used\":5},\"4\":{\"max\":2,\"used\":1}}");

        service.applyLevelUp(bard);

        assertThat(bard.getSpellSlots()).contains("\"1\":{\"max\":5,\"used\":5}");
        assertThat(bard.getSpellSlots()).contains("\"4\":{\"max\":3,\"used\":1}");
        assertThat(bard.getSpellSlots()).contains("\"5\":{\"max\":1,\"used\":0}");
    }

    @Test
    void preview_newSpellSlots_includeTheDmsBonusSlots() {
        PC paladin = pc("Paladin", 4, 14, 40, 40); // 4 -> 5
        paladin.setSubclass("Oath of Devotion");
        paladin.setStatAdjustments("{\"slot1\":[{\"value\":1,\"label\":\"Boon\"}]}");
        paladin.setSpellSlots("{\"1\":{\"max\":4,\"used\":0}}");

        LevelUpPreview p = service.preview(paladin);

        assertThat(p.currentSpellSlots()).isEqualTo(Map.of(1, 4));
        assertThat(p.newSpellSlots()).isEqualTo(Map.of(1, 5, 2, 2));
    }

    @Test
    void applyLevelUp_aClassWithoutSlots_keepsItsDmGrantedSlots() {
        PC fighter = pc("Fighter", 4, 16, 30, 30); // 4 -> 5
        fighter.setStatAdjustments("{\"slot1\":[{\"value\":2,\"label\":\"Eldritch Knight\"}]}");
        fighter.setSpellSlots("{\"1\":{\"max\":2,\"used\":1}}");

        service.applyLevelUp(fighter);

        assertThat(fighter.getSpellSlots()).isEqualTo("{\"1\":{\"max\":2,\"used\":1}}");
    }

    @Test
    void applyLevelUp_halfCaster_capsSpellLevelAtItsHighestSlot() {
        // Paladin 3 -> 4 (an ASI level): still only 1st-level slots, so a 2nd-level spell is refused.
        PC low = pc("Paladin", 3, 14, 30, 30);
        low.setSubclass("Oath of Devotion");
        assertThatThrownBy(() -> service.applyLevelUp(low, null, Map.of("STR", 2), null, List.of(spell(2, "Aid"))))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(400))
                .hasMessageContaining("highest spell level");

        // Ranger 4 -> 5: the first 2nd-level slots arrive with the new prepared spell.
        PC ranger = pc("Ranger", 4, 14, 40, 40);
        ranger.setSubclass("Hunter");
        service.applyLevelUp(ranger, null, null, null, List.of(spell(2, "Pass without Trace")));
        assertThat(ranger.getSpells()).contains("Pass without Trace");
        assertThat(ranger.getSpellSlots()).isEqualTo("{\"1\":{\"max\":4,\"used\":0},\"2\":{\"max\":2,\"used\":0}}");
    }

    // --- always-prepared spells from class features (ClassSpellGrants) ---

    @Test
    void preview_listsTheAlwaysPreparedSpellsGainedThisLevel() {
        assertThat(service.preview(pc("Paladin", 1, 14, 12, 12)).alwaysPreparedSpells())
                .containsExactly(new SpellGrant("Divine Smite", "Divine Smite"));
        assertThat(service.preview(paladin(4)).alwaysPreparedSpells())
                .containsExactly(new SpellGrant("Find Steed", "Faithful Steed"));
        // A Death Knight's Runic Smite replaces Divine Smite, spell and all.
        assertThat(service.preview(deathKnight(1)).alwaysPreparedSpells()).isEmpty();
        assertThat(service.preview(deathKnight(4)).alwaysPreparedSpells())
                .containsExactly(new SpellGrant("Find Steed", "Faithful Steed"));
        assertThat(service.preview(paladin(5)).alwaysPreparedSpells()).isEmpty();
        assertThat(service.preview(pc("Ranger", 1, 14, 12, 12)).alwaysPreparedSpells()).isEmpty();
        assertThat(service.preview(pc("Fighter", 4, 14, 40, 40)).alwaysPreparedSpells()).isEmpty();
    }

    @Test
    void applyLevelUp_savesAGrantedSpellAsAlwaysPrepared_outsideThePickCount() {
        // Paladin 4 -> 5: one new prepared spell (5 -> 6) plus Find Steed from Faithful Steed.
        PC paladin = paladin(4);
        Map<String, Object> findSteed = new LinkedHashMap<>(spell(2, "Find Steed"));
        findSteed.put("school", "Conjuration");

        service.applyLevelUp(paladin, null, null, null, List.of(spell(2, "Aid"), findSteed));

        assertThat(paladin.getLevel()).isEqualTo((short) 5);
        assertThat(paladin.getSpells())
                .contains("{\"lvl\":2,\"name\":\"Aid\"}")
                .contains("{\"lvl\":2,\"name\":\"Find Steed\",\"school\":\"Conjuration\","
                        + "\"prepared\":true,\"alwaysPrepared\":true,\"grantedBy\":\"Faithful Steed\"}");
    }

    @Test
    void applyLevelUp_aGrantedSpellIsNotAPick_soThePickLimitStillHolds() {
        // Two picks plus the grant: the grant is free, the second pick is one too many.
        PC paladin = paladin(4);
        assertThatThrownBy(() -> service.applyLevelUp(paladin, null, null, null,
                List.of(spell(2, "Aid"), spell(1, "Bless"), spell(2, "Find Steed"))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("at most 1 new spell");

        // Paladin 1 -> 2: Divine Smite rides with the level 2 pick and a cantrip Fighting Style.
        PC novice = pc("Paladin", 1, 14, 10, 10);
        service.applyLevelUp(novice, null, null, null,
                List.of(cantrip("Sacred Flame"), cantrip("Guidance"), spell(1, "Bless"), spell(1, "divine smite")),
                HpMode.AVERAGE, null, "Blessed Warrior");
        assertThat(novice.getSpells()).contains("Sacred Flame").contains("Bless")
                .contains("\"name\":\"divine smite\",\"prepared\":true,\"alwaysPrepared\":true,\"grantedBy\":\"Divine Smite\"");
    }

    @Test
    void applyLevelUp_marksAGrantedSpellTheCharacterAlreadyHas_insteadOfRejectingIt() {
        // Picked (or DM-granted) earlier: it stops counting against the prepared limit.
        PC paladin = paladin(4);
        paladin.setSpells("[{\"lvl\":2,\"name\":\"Find Steed\",\"prepared\":false}]");

        service.applyLevelUp(paladin, null, null, null, List.of(spell(2, "Find Steed"), spell(1, "Bless")));

        assertThat(paladin.getSpells()).isEqualTo("[{\"lvl\":2,\"name\":\"Find Steed\",\"prepared\":true,"
                + "\"alwaysPrepared\":true,\"grantedBy\":\"Faithful Steed\"},{\"lvl\":1,\"name\":\"Bless\"}]");
    }

    @Test
    void applyLevelUp_aSpellThatIsNotGrantedThisLevel_isAnOrdinaryPick() {
        // Find Steed sent at 5 -> 6 (no grant, no new prepared spell) is just one pick too many;
        // a client cannot mint always-prepared spells by sending the marker itself.
        PC paladin = paladin(5);
        Map<String, Object> sneaky = new LinkedHashMap<>(spell(2, "Find Steed"));
        sneaky.put("alwaysPrepared", true);
        assertThatThrownBy(() -> service.applyLevelUp(paladin, null, null, null, List.of(sneaky)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(400));

        // A Death Knight has no Divine Smite grant at 2, so it counts as the level's one pick.
        PC knight = deathKnight(1);
        service.applyLevelUp(knight, null, null, null, List.of(spell(1, "Divine Smite")));
        assertThat(knight.getSpells()).isEqualTo("[{\"lvl\":1,\"name\":\"Divine Smite\"}]");
    }

    @Test
    void applyLevelUp_withoutTheGrantedSpellInTheRequest_addsNone() {
        PC paladin = paladin(4);
        service.applyLevelUp(paladin, null, null, null, List.of(spell(1, "Bless")));
        assertThat(paladin.getSpells()).isEqualTo("[{\"lvl\":1,\"name\":\"Bless\"}]");

        PC silent = paladin(4);
        service.applyLevelUp(silent);
        assertThat(silent.getSpells()).isNull();
    }

    @Test
    void applyLevelUp_halfCaster_rejectsACantripWithoutACantripFightingStyle() {
        PC paladin = pc("Paladin", 1, 14, 12, 12);
        assertThatThrownBy(() -> service.applyLevelUp(paladin, null, null, null, List.of(spell(0, "Light"))))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(400));
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
        // Generic paladin features at those levels are kept alongside (none at 3/7/15/20 today),
        // and a PHB paladin gets its own oath's feature there instead.
        assertThat(names(service.preview(holyPaladin(5)).featuresGained())).containsExactly("Aura of Protection");
        assertThat(names(service.preview(paladin(6)).featuresGained())).containsExactly("Aura of Devotion");

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
    void applyLevelUp_cantripFightingStyle_ridesWithTheLevelTwoPreparedSpell() {
        // Level 2 gives a half caster one new prepared spell AND the Fighting Style choice: the
        // style's two cantrips and the spell arrive in the same request.
        PC paladin = pc("Paladin", 1, 14, 10, 10);
        paladin.setSpells("[{\"lvl\":1,\"name\":\"Bless\"},{\"lvl\":1,\"name\":\"Heroism\"}]");
        List<Map<String, Object>> picks = List.of(cantrip("Sacred Flame"), cantrip("Guidance"), spell(1, "Cure Wounds"));
        service.applyLevelUp(paladin, null, null, null, picks, HpMode.AVERAGE, null, "Blessed Warrior");
        assertThat(paladin.getLevel()).isEqualTo((short) 2);
        assertThat(paladin.getSpells()).contains("Bless").contains("Sacred Flame").contains("Guidance").contains("Cure Wounds");
        assertThat(paladin.getFeatures()).contains("\"name\":\"Blessed Warrior\"");

        PC ranger = pc("Ranger", 1, 14, 10, 10);
        service.applyLevelUp(ranger, null, null, null,
                List.of(spell(1, "Goodberry"), cantrip("Druidcraft"), cantrip("Thorn Whip")),
                HpMode.AVERAGE, null, "Druidic Warrior");
        assertThat(ranger.getSpells()).contains("Goodberry").contains("Druidcraft").contains("Thorn Whip");

        // The style's cantrip count is still exact, and the spell count still capped.
        PC tooMany = pc("Paladin", 1, 14, 10, 10);
        assertThatThrownBy(() -> service.applyLevelUp(tooMany, null, null, null,
                List.of(cantrip("Sacred Flame"), cantrip("Guidance"), spell(1, "Cure Wounds"), spell(1, "Bless")),
                HpMode.AVERAGE, null, "Blessed Warrior"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        assertThat(tooMany.getLevel()).isEqualTo((short) 1);
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
        // Generic rogue features at other levels are unaffected, and a PHB rogue (a Thief here)
        // gains its own archetype's features at 9/13/17, never the Swashbuckler's.
        assertThat(names(service.preview(swashbuckler(4)).featuresGained()))
                .containsExactly("Cunning Strike", "Uncanny Dodge");
        assertThat(names(service.preview(rogue(8)).featuresGained())).containsExactly("Supreme Sneak");
        assertThat(names(service.preview(rogue(12)).featuresGained())).containsExactly("Use Magic Device");
        assertThat(names(service.preview(rogue(16)).featuresGained())).containsExactly("Thief's Reflexes");

        PC l17 = swashbuckler(16);
        service.applyLevelUp(l17);
        assertThat(l17.getFeatures()).contains("\"name\":\"Master Duelist\"").contains("Sneak Attack");

        PC l9 = swashbuckler(8);
        service.applyLevelUp(l9);
        assertThat(l9.getFeatures()).contains("Goad").contains("Awe");
    }

    // --- WoW Warrior (fighter + Arms/Fury/Protection) ---

    private static final List<String> WARRIOR_SPECS = List.of("Arms", "Fury", "Protection");

    private static PC fighter(int level) {
        PC f = pc("Fighter", level, 14, 10 * level, 10 * level);
        if (level >= 3) f.setSubclass("Champion");
        return f;
    }

    private static PC wowFighter(int level, String spec) {
        PC f = fighter(level);
        f.setSpecialization(spec);
        return f;
    }

    @Test
    void applyLevelUp_fighter_acceptsEachWarriorSpecialization_andRejectsOthers() {
        for (String spec : WARRIOR_SPECS) {
            PC f = fighter(4); // 4 -> 5: not an ASI level for a fighter
            service.applyLevelUp(f, null, null, null, null, HpMode.AVERAGE, spec);
            assertThat(f.getSpecialization()).isEqualTo(spec);
            assertThat(f.getLevel()).isEqualTo((short) 5);
        }
        for (String bad : List.of("arms", "Holy", "Elemental", "Outlaw", "Retribution")) {
            PC f = wowFighter(4, "Arms");
            assertThatThrownBy(() -> service.applyLevelUp(f, null, null, null, null, HpMode.AVERAGE, bad))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(status(e)).isEqualTo(400));
            assertThat(f.getSpecialization()).isEqualTo("Arms");
            assertThat(f.getLevel()).isEqualTo((short) 4);
        }
    }

    @Test
    void applyLevelUp_wowFighter_mayRespec() {
        PC f = wowFighter(4, "Arms");
        service.applyLevelUp(f, null, null, null, null, HpMode.AVERAGE, "Protection");
        assertThat(f.getSpecialization()).isEqualTo("Protection");
    }

    @Test
    void applyLevelUp_warriorSpecialization_rejectedOnOtherClasses() {
        for (String spec : List.of("Arms", "Fury")) {
            PC r = rogue(5);
            assertThatThrownBy(() -> service.applyLevelUp(r, null, null, null, null, HpMode.AVERAGE, spec))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(status(e)).isEqualTo(400));
            assertThat(r.getSpecialization()).isNull();
        }
        PC b = wowBarbarian(5, "Elemental");
        assertThatThrownBy(() -> service.applyLevelUp(b, null, null, null, null, HpMode.AVERAGE, "Fury"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        assertThat(b.getSpecialization()).isEqualTo("Elemental");
    }

    @Test
    void preview_wowFighter_recommendsSubclassWhenDue() {
        String[][] cases = {
                {"Arms", "Battle Master"},
                {"Fury", "Champion"},
                {"Protection", "Battle Master"},
        };
        for (String[] c : cases) {
            LevelUpPreview p = service.preview(wowFighter(2, c[0]));
            assertThat(p.subclassDue()).isTrue();
            assertThat(p.subclassOptions()).contains(c[1]);
            assertThat(p.recommendedSubclass()).isEqualTo(c[1]);
            // The fighter's Fighting Style is a level-1 creation choice, never due on level-up.
            assertThat(p.fightingStyleDue()).isFalse();
            assertThat(p.recommendedFightingStyles()).isEmpty();
        }
        assertThat(service.preview(fighter(2)).recommendedSubclass()).isNull();
        assertThat(service.preview(wowFighter(4, "Arms")).recommendedSubclass()).isNull();
    }

    @Test
    void applyLevelUp_wowFighter_stillMayPickAnyFighterSubclass() {
        PC f = wowFighter(2, "Fury");
        service.applyLevelUp(f, "Eldritch Knight");
        assertThat(f.getSubclass()).isEqualTo("Eldritch Knight"); // recommendation is not enforced
    }

    @Test
    void classProgression_warriorLookups_returnExactCatalogNames() {
        assertThat(ClassProgression.recommendedSubclass("fighter", "Arms")).isEqualTo("Battle Master");
        assertThat(ClassProgression.recommendedSubclass("Fighter", "Fury")).isEqualTo("Champion");
        assertThat(ClassProgression.recommendedSubclass("fighter", "Protection")).isEqualTo("Battle Master");
        assertThat(ClassProgression.recommendedSubclass("fighter", "arms")).isNull();
        assertThat(ClassProgression.recommendedSubclass("fighter", null)).isNull();

        assertThat(ClassProgression.recommendedFightingStyles("fighter", "Arms"))
                .containsExactly("Great Weapon Fighting");
        assertThat(ClassProgression.recommendedFightingStyles("fighter", "Fury"))
                .containsExactly("Two-Weapon Fighting");
        assertThat(ClassProgression.recommendedFightingStyles("fighter", "Protection"))
                .containsExactly("Interception", "Protection");
        assertThat(ClassProgression.recommendedFightingStyles("fighter", "Holy")).isEmpty();
        // Every recommended style is a real Fighting Style feat name (the fighter picks at creation,
        // so borrow the paladin level-2 option list, which holds every Fighting Style feat).
        List<String> options = FeatCatalog.fightingStyleOptions("paladin", null, 2);
        for (String spec : WARRIOR_SPECS) {
            assertThat(options).containsAll(ClassProgression.recommendedFightingStyles("fighter", spec));
            assertThat(ClassProgression.subclassesFor("fighter"))
                    .contains(ClassProgression.recommendedSubclass("fighter", spec));
        }

        assertThat(ClassProgression.classSpecializations("fighter")).get()
                .satisfies(s -> {
                    assertThat(s.specializations()).containsExactlyElementsOf(WARRIOR_SPECS);
                    assertThat(s.respecOnLevelUp()).isTrue();
                });
        assertThat(ClassProgression.isClassSpecialization("fighter", "Arms")).isTrue();
        assertThat(ClassProgression.isClassSpecialization("paladin", "Arms")).isFalse();

        // Other variants unchanged — including the paladin's same-named "Protection".
        assertThat(ClassProgression.recommendedSubclass("paladin", "Protection")).isEqualTo("Oath of Devotion");
        assertThat(ClassProgression.recommendedFightingStyles("paladin", "Protection"))
                .containsExactly("Interception", "Protection");
        assertThat(ClassProgression.recommendedSubclass("rogue", "Outlaw")).isEqualTo(SWASHBUCKLER);
        assertThat(ClassProgression.recommendedSubclass("druid", "Balance")).isEqualTo("Circle of the Stars");
        assertThat(ClassProgression.recommendedSubclass("barbarian", "Elemental")).isEqualTo(PATH_OF_THE_ELEMENTS);
        assertThat(ClassProgression.recommendedSubclass("ranger", "Arms")).isNull();
    }

    // --- WoW Shaman (barbarian + Elemental/Enhancement/Restoration, two WoW-only paths, Totems) ---

    private static final String PATH_OF_THE_ELEMENTS = "Path of the Elements";
    private static final String PATH_OF_THE_SPIRITUAL_GUARDIAN = "Path of the Spiritual Guardian";
    private static final List<String> SHAMAN_SPECS = List.of("Elemental", "Enhancement", "Restoration");
    private static final List<String> PHB_BARBARIAN_SUBCLASSES =
            List.of("Path of the Berserker", "Path of the Wild Heart", "Path of the World Tree", "Path of the Zealot");

    private static PC barbarian(int level) {
        PC b = pc("Barbarian", level, 14, 10 * level, 10 * level);
        if (level >= 3) b.setSubclass("Path of the Berserker");
        return b;
    }

    private static PC wowBarbarian(int level, String spec) {
        PC b = barbarian(level);
        b.setSpecialization(spec);
        return b;
    }

    private static PC shamanOnPath(int level, String path, String spec) {
        PC b = pc("Barbarian", level, 14, 10 * level, 10 * level);
        b.setSubclass(path);
        b.setSpecialization(spec);
        return b;
    }

    @Test
    void applyLevelUp_barbarian_acceptsEachShamanSpecialization_andRejectsOthers() {
        for (String spec : SHAMAN_SPECS) {
            PC b = barbarian(5); // 5 -> 6: not an ASI level
            service.applyLevelUp(b, null, null, null, null, HpMode.AVERAGE, spec);
            assertThat(b.getSpecialization()).isEqualTo(spec);
            assertThat(b.getLevel()).isEqualTo((short) 6);
        }
        for (String bad : List.of("elemental", "Enhance", "Holy", "Frost", "Outlaw", "Balance")) {
            PC b = wowBarbarian(5, "Elemental");
            assertThatThrownBy(() -> service.applyLevelUp(b, null, null, null, null, HpMode.AVERAGE, bad))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(status(e)).isEqualTo(400));
            assertThat(b.getSpecialization()).isEqualTo("Elemental");
            assertThat(b.getLevel()).isEqualTo((short) 5);
        }
    }

    @Test
    void applyLevelUp_wowBarbarian_mayRespec() {
        PC b = wowBarbarian(5, "Elemental");
        service.applyLevelUp(b, null, null, null, null, HpMode.AVERAGE, "Restoration");
        assertThat(b.getSpecialization()).isEqualTo("Restoration");
    }

    @Test
    void preview_shamanPaths_offeredOnlyToWowBarbarians() {
        LevelUpPreview base = service.preview(barbarian(2)); // 2 -> 3, no specialization
        assertThat(base.subclassDue()).isTrue();
        assertThat(base.subclassOptions()).containsExactlyElementsOf(PHB_BARBARIAN_SUBCLASSES);

        for (String spec : SHAMAN_SPECS) {
            assertThat(service.preview(wowBarbarian(2, spec)).subclassOptions())
                    .containsAll(PHB_BARBARIAN_SUBCLASSES)
                    .contains(PATH_OF_THE_ELEMENTS, PATH_OF_THE_SPIRITUAL_GUARDIAN)
                    .doesNotContain(SWASHBUCKLER, OATH_OF_THE_HOLY)
                    .hasSize(PHB_BARBARIAN_SUBCLASSES.size() + 2);
        }

        // Other WoW classes don't get them.
        assertThat(service.preview(wowRogue(2, "Outlaw")).subclassOptions())
                .doesNotContain(PATH_OF_THE_ELEMENTS, PATH_OF_THE_SPIRITUAL_GUARDIAN);
        assertThat(service.preview(wowPaladin(2, "Holy")).subclassOptions())
                .doesNotContain(PATH_OF_THE_ELEMENTS, PATH_OF_THE_SPIRITUAL_GUARDIAN);
        PC d = druid(2);
        d.setSpecialization("Restoration");
        assertThat(service.preview(d).subclassOptions())
                .doesNotContain(PATH_OF_THE_ELEMENTS, PATH_OF_THE_SPIRITUAL_GUARDIAN);
    }

    @Test
    void applyLevelUp_shamanPaths_acceptedForWowBarbarian() {
        for (String path : List.of(PATH_OF_THE_ELEMENTS, PATH_OF_THE_SPIRITUAL_GUARDIAN)) {
            PC b = wowBarbarian(2, "Enhancement");
            service.applyLevelUp(b, path);
            assertThat(b.getSubclass()).isEqualTo(path);
            assertThat(b.getLevel()).isEqualTo((short) 3);
        }
    }

    @Test
    void applyLevelUp_shamanPath_acceptedWhenTheSameRequestSetsTheSpecialization() {
        PC b = barbarian(2);
        service.applyLevelUp(b, PATH_OF_THE_ELEMENTS, null, null, null, HpMode.AVERAGE, "Elemental");
        assertThat(b.getSubclass()).isEqualTo(PATH_OF_THE_ELEMENTS);
        assertThat(b.getSpecialization()).isEqualTo("Elemental");
        // The specialization set in this request also makes it a Shaman for this level's features.
        assertThat(b.getFeatures()).contains("\"name\":\"Totems\"").doesNotContain("\"name\":\"Primal Knowledge\"");
    }

    @Test
    void applyLevelUp_shamanPaths_rejectedForBaseBarbarian_andOtherWowClasses() {
        for (String path : List.of(PATH_OF_THE_ELEMENTS, PATH_OF_THE_SPIRITUAL_GUARDIAN)) {
            PC b = barbarian(2);
            assertThatThrownBy(() -> service.applyLevelUp(b, path))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(status(e)).isEqualTo(400));
            assertThat(b.getSubclass()).isNull();
            assertThat(b.getLevel()).isEqualTo((short) 2);

            PC r = wowRogue(2, "Outlaw");
            assertThatThrownBy(() -> service.applyLevelUp(r, path))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        }
        // And a WoW barbarian can't take another class's WoW-only subclass.
        PC wb = wowBarbarian(2, "Elemental");
        assertThatThrownBy(() -> service.applyLevelUp(wb, SWASHBUCKLER))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
    }

    @Test
    void preview_wowBarbarian_recommendsSubclassWhenDue() {
        String[][] cases = {
                {"Elemental", PATH_OF_THE_ELEMENTS},
                {"Enhancement", PATH_OF_THE_SPIRITUAL_GUARDIAN},
                {"Restoration", "Path of the World Tree"},
        };
        for (String[] c : cases) {
            LevelUpPreview p = service.preview(wowBarbarian(2, c[0]));
            assertThat(p.subclassDue()).isTrue();
            assertThat(p.subclassOptions()).contains(c[1]);
            assertThat(p.recommendedSubclass()).isEqualTo(c[1]);
            assertThat(p.recommendedFightingStyles()).isEmpty();
        }
        assertThat(service.preview(barbarian(2)).recommendedSubclass()).isNull();
        assertThat(service.preview(wowBarbarian(4, "Elemental")).recommendedSubclass()).isNull();
    }

    @Test
    void totems_replacePrimalKnowledgeAtLevel3_forEveryShamanSpecAndSubclass() {
        // Preview at 2 -> 3 (subclass not chosen yet).
        for (String spec : SHAMAN_SPECS) {
            assertThat(names(service.preview(wowBarbarian(2, spec)).featuresGained())).containsExactly("Totems");
        }
        // Applied with every subclass a WoW barbarian can take.
        List<String> paths = List.of(PATH_OF_THE_ELEMENTS, PATH_OF_THE_SPIRITUAL_GUARDIAN,
                "Path of the Berserker", "Path of the Wild Heart", "Path of the World Tree", "Path of the Zealot");
        for (String spec : SHAMAN_SPECS) {
            for (String path : paths) {
                PC b = wowBarbarian(2, spec);
                service.applyLevelUp(b, path);
                assertThat(b.getFeatures()).as(spec + " / " + path)
                        .contains("\"name\":\"Totems\"")
                        .contains("Earthbind").contains("Tremor").contains("Wind Rush")
                        .doesNotContain("\"name\":\"Primal Knowledge\"");
            }
        }
        // A base barbarian still gets Primal Knowledge.
        assertThat(names(service.preview(barbarian(2)).featuresGained())).containsExactly("Primal Knowledge");
        PC base = barbarian(2);
        service.applyLevelUp(base, "Path of the Berserker");
        assertThat(base.getFeatures()).contains("\"name\":\"Primal Knowledge\"").doesNotContain("Totems");
    }

    @Test
    void totems_onlyAtLevel3_andGenericBarbarianFeaturesOtherwiseUnchanged() {
        assertThat(names(service.preview(wowBarbarian(1, "Elemental")).featuresGained()))
                .containsExactly("Reckless Attack", "Danger Sense");
        assertThat(names(service.preview(wowBarbarian(4, "Enhancement")).featuresGained()))
                .containsExactly("Extra Attack", "Fast Movement");
        // Level 6 has no generic barbarian feature: only the path's own (a Berserker here).
        assertThat(names(service.preview(wowBarbarian(5, "Restoration")).featuresGained()))
                .containsExactly("Mindless Rage");
    }

    @Test
    void pathOfTheElements_addsFeaturesAt3_6_10_14() {
        PC l3 = wowBarbarian(2, "Elemental");
        service.applyLevelUp(l3, PATH_OF_THE_ELEMENTS);
        assertThat(l3.getFeatures()).contains("\"name\":\"Raging Elements\"")
                .contains("Icefury").contains("Lava Burst").contains("Lightning Bolt");

        assertThat(names(service.preview(shamanOnPath(5, PATH_OF_THE_ELEMENTS, "Elemental")).featuresGained()))
                .containsExactly("Echo of the Elements");
        assertThat(names(service.preview(shamanOnPath(9, PATH_OF_THE_ELEMENTS, "Elemental")).featuresGained()))
                .containsExactly("Primordial Bond");
        assertThat(names(service.preview(shamanOnPath(13, PATH_OF_THE_ELEMENTS, "Elemental")).featuresGained()))
                .containsExactly("Elemental Overload");
        // A PHB-path barbarian (a Berserker here) gains its own path's features at 6/10/14.
        assertThat(names(service.preview(barbarian(5)).featuresGained())).containsExactly("Mindless Rage");
        assertThat(names(service.preview(barbarian(9)).featuresGained())).containsExactly("Retaliation");
        assertThat(names(service.preview(barbarian(13)).featuresGained())).containsExactly("Intimidating Presence");
    }

    @Test
    void pathOfTheSpiritualGuardian_addsFeaturesAt3_6_10_14() {
        PC l3 = wowBarbarian(2, "Enhancement");
        service.applyLevelUp(l3, PATH_OF_THE_SPIRITUAL_GUARDIAN);
        assertThat(l3.getFeatures()).contains("\"name\":\"Spiritual Protectors\"")
                .contains("Distract").contains("Hinder").contains("Strike");

        assertThat(names(service.preview(shamanOnPath(5, PATH_OF_THE_SPIRITUAL_GUARDIAN, "Enhancement")).featuresGained()))
                .containsExactly("Spirit Shield");
        assertThat(names(service.preview(shamanOnPath(9, PATH_OF_THE_SPIRITUAL_GUARDIAN, "Enhancement")).featuresGained()))
                .containsExactly("Consult the Spirits");
        assertThat(names(service.preview(shamanOnPath(13, PATH_OF_THE_SPIRITUAL_GUARDIAN, "Enhancement")).featuresGained()))
                .containsExactly("Spirits' Fury");

        PC l14 = shamanOnPath(13, PATH_OF_THE_SPIRITUAL_GUARDIAN, "Enhancement");
        service.applyLevelUp(l14);
        assertThat(l14.getFeatures()).contains("\"name\":\"Spirits' Fury\"");
    }

    @Test
    void variantFeatureSwap_doesNotAffectOtherClasses() {
        // Other WoW variants keep their generic level-3 features; a Death Knight's spec is not a class spec.
        assertThat(names(service.preview(wowRogue(2, "Subtlety")).featuresGained())).containsExactly("Steady Aim");
        PC d = druid(2);
        d.setSpecialization("Restoration");
        assertThat(names(service.preview(d).featuresGained()))
                .containsExactlyElementsOf(names(service.preview(druid(2)).featuresGained()));
        assertThat(names(service.preview(wowPaladin(1, "Holy")).featuresGained()))
                .containsExactly("Fighting Style", "Divine Smite");
        // The specialization-less overload still yields the generic barbarian list.
        assertThat(names(com.moo.charactermanagerservice.progression.ClassFeatures
                .featuresAt("barbarian", PATH_OF_THE_ELEMENTS, 3)))
                .containsExactly("Primal Knowledge", "Raging Elements");
        assertThat(names(com.moo.charactermanagerservice.progression.ClassFeatures
                .featuresAt("barbarian", PATH_OF_THE_ELEMENTS, "Elemental", 3)))
                .containsExactly("Totems", "Raging Elements");
    }

    // --- WoW Warlock (warlock + Affliction/Demonology/Destruction; patron chosen at level 1) ---

    private static final List<String> WARLOCK_SPECS = List.of("Affliction", "Demonology", "Destruction");

    private static PC warlock(int level) {
        PC w = pc("Warlock", level, 14, 8 * level, 8 * level);
        w.setSubclass("Fiend Patron"); // warlocks choose their patron at level 1
        return w;
    }

    private static PC wowWarlock(int level, String spec) {
        PC w = warlock(level);
        w.setSpecialization(spec);
        return w;
    }

    @Test
    void applyLevelUp_warlock_acceptsEachWowSpecialization_andRejectsOthers() {
        for (String spec : WARLOCK_SPECS) {
            PC w = warlock(5); // 5 -> 6: not an ASI level
            service.applyLevelUp(w, null, null, null, null, HpMode.AVERAGE, spec);
            assertThat(w.getSpecialization()).isEqualTo(spec);
            assertThat(w.getLevel()).isEqualTo((short) 6);
        }
        for (String bad : List.of("affliction", "Fire", "Holy", "Outlaw", "Frost")) {
            PC w = wowWarlock(5, "Affliction");
            assertThatThrownBy(() -> service.applyLevelUp(w, null, null, null, null, HpMode.AVERAGE, bad))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(status(e)).isEqualTo(400));
            assertThat(w.getSpecialization()).isEqualTo("Affliction");
            assertThat(w.getLevel()).isEqualTo((short) 5);
        }
    }

    @Test
    void applyLevelUp_warlockSpecialization_rejectedOnOtherClasses() {
        PC r = rogue(5);
        assertThatThrownBy(() -> service.applyLevelUp(r, null, null, null, null, HpMode.AVERAGE, "Destruction"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        assertThat(r.getSpecialization()).isNull();
        assertThat(r.getLevel()).isEqualTo((short) 5);
    }

    @Test
    void applyLevelUp_wowWarlock_mayRespec() {
        PC w = wowWarlock(5, "Affliction");
        service.applyLevelUp(w, null, null, null, null, HpMode.AVERAGE, "Destruction");
        assertThat(w.getSpecialization()).isEqualTo("Destruction");
        assertThat(w.getSubclass()).isEqualTo("Fiend Patron");
    }

    @Test
    void wowWarlock_recommendedSubclassData_andNoSubclassEverDueOnLevelUp() {
        assertThat(com.moo.charactermanagerservice.progression.ClassProgression
                .recommendedSubclass("warlock", "Affliction")).isEqualTo("Great Old One Patron");
        assertThat(com.moo.charactermanagerservice.progression.ClassProgression
                .recommendedSubclass("warlock", "Demonology")).isEqualTo("Fiend Patron");
        assertThat(com.moo.charactermanagerservice.progression.ClassProgression
                .recommendedSubclass("Warlock", "Destruction")).isEqualTo("Fiend Patron");
        assertThat(com.moo.charactermanagerservice.progression.ClassProgression
                .recommendedSubclass("warlock", "affliction")).isNull();
        assertThat(com.moo.charactermanagerservice.progression.ClassProgression
                .recommendedFightingStyles("warlock", "Destruction")).isEmpty();
        // Every recommendation is a real catalog patron; no WoW-only patrons are added.
        for (String spec : WARLOCK_SPECS) {
            assertThat(com.moo.charactermanagerservice.progression.ClassProgression.subclassesFor("warlock", spec))
                    .containsExactlyElementsOf(com.moo.charactermanagerservice.progression.ClassProgression
                            .subclassesFor("warlock"))
                    .contains(com.moo.charactermanagerservice.progression.ClassProgression
                            .recommendedSubclass("warlock", spec));
        }
        // The patron is picked at creation, so the level-up preview never recommends one.
        for (String spec : WARLOCK_SPECS) {
            for (int level : List.of(1, 2, 4)) {
                LevelUpPreview p = service.preview(wowWarlock(level, spec));
                assertThat(p.subclassDue()).isFalse();
                assertThat(p.recommendedSubclass()).isNull();
                assertThat(p.recommendedFightingStyles()).isEmpty();
            }
        }
    }

    @Test
    void wowWarlock_addsNoFeatureOverrides_andOtherVariantsUnchanged() {
        for (int level : List.of(1, 2, 4, 8)) {
            assertThat(names(service.preview(wowWarlock(level, "Demonology")).featuresGained()))
                    .containsExactlyElementsOf(names(service.preview(warlock(level)).featuresGained()));
        }
        assertThat(com.moo.charactermanagerservice.progression.ClassProgression
                .recommendedSubclass("rogue", "Outlaw")).isEqualTo(SWASHBUCKLER);
        assertThat(com.moo.charactermanagerservice.progression.ClassProgression
                .recommendedSubclass("druid", "Balance")).isEqualTo("Circle of the Stars");
        assertThat(com.moo.charactermanagerservice.progression.ClassProgression
                .recommendedSubclass("paladin", "Holy")).isEqualTo(OATH_OF_THE_HOLY);
        assertThat(com.moo.charactermanagerservice.progression.ClassProgression
                .recommendedSubclass("barbarian", "Elemental")).isEqualTo(PATH_OF_THE_ELEMENTS);
        assertThat(com.moo.charactermanagerservice.progression.ClassProgression
                .isClassSpecialization("sorcerer", "Destruction")).isFalse();
    }

    // --- WoW Priest (cleric + Discipline/Holy/Shadow, two WoW-only domains, no overrides) ---

    private static final String DISCIPLINE_DOMAIN = "Discipline Domain";
    private static final String SHADOW_DOMAIN = "Shadow Domain";
    private static final List<String> PRIEST_SPECS = List.of("Discipline", "Holy", "Shadow");
    private static final List<String> PHB_CLERIC_SUBCLASSES =
            List.of("Life Domain", "Light Domain", "Trickery Domain", "Twilight Domain", "War Domain");

    private static PC cleric(int level) {
        PC c = pc("Cleric", level, 14, 8 * level, 8 * level);
        if (level >= 3) c.setSubclass("Life Domain");
        return c;
    }

    private static PC wowCleric(int level, String spec) {
        PC c = cleric(level);
        c.setSpecialization(spec);
        return c;
    }

    private static PC priestInDomain(int level, String domain, String spec) {
        PC c = pc("Cleric", level, 14, 8 * level, 8 * level);
        c.setSubclass(domain);
        c.setSpecialization(spec);
        return c;
    }

    @Test
    void applyLevelUp_cleric_acceptsEachPriestSpecialization_andRejectsOthers() {
        for (String spec : PRIEST_SPECS) {
            PC c = cleric(5); // 5 -> 6: not an ASI level
            service.applyLevelUp(c, null, null, null, null, HpMode.AVERAGE, spec);
            assertThat(c.getSpecialization()).isEqualTo(spec);
            assertThat(c.getLevel()).isEqualTo((short) 6);
        }
        for (String bad : List.of("holy", "Protection", "Elemental", "Frost", "Outlaw", "Restoration")) {
            PC c = wowCleric(5, "Holy");
            assertThatThrownBy(() -> service.applyLevelUp(c, null, null, null, null, HpMode.AVERAGE, bad))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(status(e)).isEqualTo(400));
            assertThat(c.getSpecialization()).isEqualTo("Holy");
            assertThat(c.getLevel()).isEqualTo((short) 5);
        }
    }

    @Test
    void applyLevelUp_wowCleric_mayRespec() {
        PC c = wowCleric(5, "Discipline");
        service.applyLevelUp(c, null, null, null, null, HpMode.AVERAGE, "Shadow");
        assertThat(c.getSpecialization()).isEqualTo("Shadow");
        assertThat(c.getSubclass()).isEqualTo("Life Domain");
    }

    @Test
    void applyLevelUp_priestSpecialization_rejectedOnOtherClasses() {
        for (String spec : List.of("Discipline", "Shadow")) {
            PC r = rogue(5);
            assertThatThrownBy(() -> service.applyLevelUp(r, null, null, null, null, HpMode.AVERAGE, spec))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(status(e)).isEqualTo(400));
            assertThat(r.getSpecialization()).isNull();
        }
        PC d = druid(5);
        assertThatThrownBy(() -> service.applyLevelUp(d, null, null, null, null, HpMode.AVERAGE, "Discipline"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
    }

    @Test
    void preview_priestDomains_offeredOnlyToWowClerics() {
        LevelUpPreview base = service.preview(cleric(2)); // 2 -> 3, no specialization
        assertThat(base.subclassDue()).isTrue();
        assertThat(base.subclassOptions()).containsExactlyElementsOf(PHB_CLERIC_SUBCLASSES);

        for (String spec : PRIEST_SPECS) {
            assertThat(service.preview(wowCleric(2, spec)).subclassOptions())
                    .containsAll(PHB_CLERIC_SUBCLASSES)
                    .contains(DISCIPLINE_DOMAIN, SHADOW_DOMAIN)
                    .doesNotContain(SWASHBUCKLER, OATH_OF_THE_HOLY, PATH_OF_THE_ELEMENTS)
                    .hasSize(PHB_CLERIC_SUBCLASSES.size() + 2);
        }

        // Other WoW classes don't get them.
        assertThat(service.preview(wowPaladin(2, "Holy")).subclassOptions())
                .doesNotContain(DISCIPLINE_DOMAIN, SHADOW_DOMAIN);
        assertThat(service.preview(wowRogue(2, "Subtlety")).subclassOptions())
                .doesNotContain(DISCIPLINE_DOMAIN, SHADOW_DOMAIN);
        assertThat(service.preview(wowBarbarian(2, "Restoration")).subclassOptions())
                .doesNotContain(DISCIPLINE_DOMAIN, SHADOW_DOMAIN);
    }

    @Test
    void applyLevelUp_priestDomains_acceptedForWowCleric() {
        for (String spec : PRIEST_SPECS) {
            for (String domain : List.of(DISCIPLINE_DOMAIN, SHADOW_DOMAIN)) {
                PC c = wowCleric(2, spec);
                service.applyLevelUp(c, domain);
                assertThat(c.getSubclass()).isEqualTo(domain);
                assertThat(c.getLevel()).isEqualTo((short) 3);
            }
        }
    }

    @Test
    void applyLevelUp_priestDomain_acceptedWhenTheSameRequestSetsTheSpecialization() {
        PC c = cleric(2);
        service.applyLevelUp(c, SHADOW_DOMAIN, null, null, null, HpMode.AVERAGE, "Shadow");
        assertThat(c.getSubclass()).isEqualTo(SHADOW_DOMAIN);
        assertThat(c.getSpecialization()).isEqualTo("Shadow");
    }

    @Test
    void applyLevelUp_priestDomains_rejectedForBaseCleric_otherClasses_andOffLevel() {
        for (String domain : List.of(DISCIPLINE_DOMAIN, SHADOW_DOMAIN)) {
            PC c = cleric(2);
            assertThatThrownBy(() -> service.applyLevelUp(c, domain))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(status(e)).isEqualTo(400));
            assertThat(c.getSubclass()).isNull();
            assertThat(c.getLevel()).isEqualTo((short) 2);

            PC p = wowPaladin(2, "Holy");
            assertThatThrownBy(() -> service.applyLevelUp(p, domain))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(status(e)).isEqualTo(400));
            PC b = wowBarbarian(2, "Elemental");
            assertThatThrownBy(() -> service.applyLevelUp(b, domain))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(status(e)).isEqualTo(400));

            // Only at the level-3 choice: a WoW cleric past it can't pick one.
            PC late = pc("Cleric", 4, 14, 32, 32);
            late.setSpecialization("Shadow");
            assertThatThrownBy(() -> service.applyLevelUp(late, domain))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        }
        // And a WoW cleric can't take another class's WoW-only subclass.
        PC wc = wowCleric(2, "Holy");
        assertThatThrownBy(() -> service.applyLevelUp(wc, OATH_OF_THE_HOLY))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
    }

    @Test
    void preview_wowCleric_recommendsSubclassWhenDue() {
        String[][] cases = {
                {"Discipline", DISCIPLINE_DOMAIN},
                {"Holy", "Life Domain"},
                {"Shadow", SHADOW_DOMAIN},
        };
        for (String[] c : cases) {
            LevelUpPreview p = service.preview(wowCleric(2, c[0]));
            assertThat(p.subclassDue()).isTrue();
            assertThat(p.subclassOptions()).contains(c[1]);
            assertThat(p.recommendedSubclass()).isEqualTo(c[1]);
            assertThat(p.recommendedFightingStyles()).isEmpty();
        }
        assertThat(service.preview(cleric(2)).recommendedSubclass()).isNull();
        assertThat(service.preview(wowCleric(4, "Shadow")).recommendedSubclass()).isNull();
    }

    @Test
    void classProgression_priestLookups_returnExactNames() {
        assertThat(ClassProgression.recommendedSubclass("cleric", "Discipline")).isEqualTo("Discipline Domain");
        assertThat(ClassProgression.recommendedSubclass("Cleric", "Holy")).isEqualTo("Life Domain");
        assertThat(ClassProgression.recommendedSubclass("cleric", "Shadow")).isEqualTo("Shadow Domain");
        assertThat(ClassProgression.recommendedSubclass("cleric", "holy")).isNull();
        assertThat(ClassProgression.recommendedSubclass("cleric", null)).isNull();
        assertThat(ClassProgression.recommendedFightingStyles("cleric", "Holy")).isEmpty();
        assertThat(ClassProgression.subclassesFor("cleric")).contains("Life Domain")
                .doesNotContain(DISCIPLINE_DOMAIN, SHADOW_DOMAIN);
        assertThat(ClassProgression.classSpecializations("cleric")).get()
                .satisfies(s -> {
                    assertThat(s.specializations()).containsExactlyElementsOf(PRIEST_SPECS);
                    assertThat(s.respecOnLevelUp()).isTrue();
                });
        assertThat(ClassProgression.isClassSpecialization("cleric", "Holy")).isTrue();
        assertThat(ClassProgression.isClassSpecialization("cleric", "Protection")).isFalse();
        assertThat(ClassProgression.isClassSpecialization("druid", "Shadow")).isFalse();

        // Other variants unchanged, including the paladin's same-named "Holy".
        assertThat(ClassProgression.recommendedSubclass("paladin", "Holy")).isEqualTo(OATH_OF_THE_HOLY);
        assertThat(ClassProgression.recommendedFightingStyles("paladin", "Holy")).containsExactly("Blessed Warrior");
        assertThat(ClassProgression.recommendedSubclass("rogue", "Outlaw")).isEqualTo(SWASHBUCKLER);
        assertThat(ClassProgression.recommendedSubclass("druid", "Balance")).isEqualTo("Circle of the Stars");
        assertThat(ClassProgression.recommendedSubclass("barbarian", "Elemental")).isEqualTo(PATH_OF_THE_ELEMENTS);
        assertThat(ClassProgression.recommendedSubclass("fighter", "Arms")).isEqualTo("Battle Master");
        assertThat(ClassProgression.recommendedSubclass("warlock", "Affliction")).isEqualTo("Great Old One Patron");
    }

    @Test
    void disciplineDomain_addsFeaturesAt3_6_17() {
        PC l3 = wowCleric(2, "Discipline");
        service.applyLevelUp(l3, DISCIPLINE_DOMAIN);
        assertThat(l3.getFeatures()).contains("\"name\":\"Discipline Spells\"")
                .contains("\"name\":\"Holy Bolt\"").contains("\"name\":\"Atonement\"")
                .contains("Prayer of Mending").contains("Destructive Wave");

        assertThat(names(service.preview(priestInDomain(5, DISCIPLINE_DOMAIN, "Discipline")).featuresGained()))
                .containsExactly("Luminous Barrier");
        assertThat(names(service.preview(priestInDomain(16, DISCIPLINE_DOMAIN, "Discipline")).featuresGained()))
                .containsExactly("Ultimate Penitence");

        PC l17 = priestInDomain(16, DISCIPLINE_DOMAIN, "Holy");
        service.applyLevelUp(l17);
        assertThat(l17.getFeatures()).contains("\"name\":\"Ultimate Penitence\"")
                .contains("Ascending Step").contains("Ascending Volley");
    }

    @Test
    void shadowDomain_addsFeaturesAt3_6_17() {
        PC l3 = wowCleric(2, "Shadow");
        service.applyLevelUp(l3, SHADOW_DOMAIN);
        assertThat(l3.getFeatures()).contains("\"name\":\"Shadow Spells\"")
                .contains("\"name\":\"Shadowform\"").contains("\"name\":\"Devouring Plague\"")
                .contains("Shadow Word: Pain").contains("Void Eruption")
                .contains("Shadow Stealth").contains("Void Empowerment");

        assertThat(names(service.preview(priestInDomain(5, SHADOW_DOMAIN, "Shadow")).featuresGained()))
                .containsExactly("Dispersion");
        assertThat(names(service.preview(priestInDomain(16, SHADOW_DOMAIN, "Shadow")).featuresGained()))
                .containsExactly("Vampiric Embrace");

        PC l6 = priestInDomain(5, SHADOW_DOMAIN, "Shadow");
        service.applyLevelUp(l6);
        assertThat(l6.getFeatures()).contains("\"name\":\"Dispersion\"");
    }

    @Test
    void wowCleric_addsNoFeatureOverrides_andPhbDomainsUnchanged() {
        for (int level : List.of(1, 2, 4, 5, 6, 9, 13, 16, 19)) {
            assertThat(names(service.preview(wowCleric(level, "Holy")).featuresGained()))
                    .as("level %d", level)
                    .containsExactlyElementsOf(names(service.preview(cleric(level)).featuresGained()));
        }
        // A Life Domain cleric keeps its own features at 6 and 17.
        assertThat(names(service.preview(cleric(5)).featuresGained())).containsExactly("Blessed Healer");
        assertThat(names(service.preview(cleric(16)).featuresGained())).containsExactly("Supreme Healing");
        // The specialization-aware overload adds nothing for a cleric.
        assertThat(com.moo.charactermanagerservice.progression.ClassFeatures
                .featuresAt("cleric", SHADOW_DOMAIN, "Shadow", 3))
                .isEqualTo(com.moo.charactermanagerservice.progression.ClassFeatures
                        .featuresAt("cleric", SHADOW_DOMAIN, 3));
    }

    // --- WoW Evoker (sorcerer + Augmentation/Devastation/Preservation; requires Draconic Sorcery) ---

    private static final String DRACONIC_SORCERY = "Draconic Sorcery";
    private static final List<String> EVOKER_SPECS = List.of("Augmentation", "Devastation", "Preservation");

    private static PC sorcerer(int level, String subclass) {
        PC s = pc("Sorcerer", level, 14, 6 * level, 6 * level);
        s.setSubclass(subclass); // sorcerers choose their subclass at level 1
        return s;
    }

    private static PC evoker(int level, String spec) {
        PC s = sorcerer(level, DRACONIC_SORCERY);
        s.setSpecialization(spec);
        return s;
    }

    @Test
    void applyLevelUp_draconicSorcerer_acceptsEachEvokerSpecialization_andRejectsOthers() {
        for (String spec : EVOKER_SPECS) {
            PC s = sorcerer(4, DRACONIC_SORCERY); // 4 -> 5: not an ASI level
            service.applyLevelUp(s, null, null, null, null, HpMode.AVERAGE, spec);
            assertThat(s.getSpecialization()).isEqualTo(spec);
            assertThat(s.getLevel()).isEqualTo((short) 5);
        }
        for (String bad : List.of("devastation", "Destruction", "Holy", "Frost", "Fire", "Balance")) {
            PC s = evoker(4, "Devastation");
            assertThatThrownBy(() -> service.applyLevelUp(s, null, null, null, null, HpMode.AVERAGE, bad))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(status(e)).isEqualTo(400));
            assertThat(s.getSpecialization()).isEqualTo("Devastation");
            assertThat(s.getLevel()).isEqualTo((short) 4);
        }
    }

    @Test
    void applyLevelUp_evokerSpecialization_requiresDraconicSorcery() {
        for (String subclass : List.of("Aberrant Sorcery", "Clockwork Sorcery", "Wild Magic Sorcery")) {
            PC s = sorcerer(4, subclass);
            assertThatThrownBy(() -> service.applyLevelUp(s, null, null, null, null, HpMode.AVERAGE, "Preservation"))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(status(e)).isEqualTo(400));
            assertThat(s.getSpecialization()).isNull();
            assertThat(s.getLevel()).isEqualTo((short) 4);
        }
        // A sorcerer with no subclass can't become an Evoker either.
        PC none = sorcerer(4, null);
        assertThatThrownBy(() -> service.applyLevelUp(none, null, null, null, null, HpMode.AVERAGE, "Augmentation"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        // An (invalid) stored Evoker on the wrong subclass can't level up without fixing it.
        PC broken = sorcerer(4, "Wild Magic Sorcery");
        broken.setSpecialization("Devastation");
        assertThatThrownBy(() -> service.applyLevelUp(broken))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        assertThat(broken.getLevel()).isEqualTo((short) 4);
        // Matching is case-insensitive like the rest of the subclass checks.
        PC lower = sorcerer(4, "draconic sorcery");
        service.applyLevelUp(lower, null, null, null, null, HpMode.AVERAGE, "Devastation");
        assertThat(lower.getSpecialization()).isEqualTo("Devastation");
    }

    @Test
    void applyLevelUp_baseSorcerer_anySubclass_unaffected() {
        for (String subclass : List.of("Aberrant Sorcery", "Clockwork Sorcery", DRACONIC_SORCERY, "Wild Magic Sorcery")) {
            PC s = sorcerer(4, subclass);
            service.applyLevelUp(s);
            assertThat(s.getLevel()).isEqualTo((short) 5);
            assertThat(s.getSpecialization()).isNull();
        }
    }

    @Test
    void applyLevelUp_evoker_mayRespec() {
        PC s = evoker(4, "Devastation");
        service.applyLevelUp(s, null, null, null, null, HpMode.AVERAGE, "Preservation");
        assertThat(s.getSpecialization()).isEqualTo("Preservation");
        assertThat(s.getSubclass()).isEqualTo(DRACONIC_SORCERY);
    }

    @Test
    void classProgression_evokerLookups_returnExactNames() {
        for (String spec : EVOKER_SPECS) {
            assertThat(ClassProgression.recommendedSubclass("sorcerer", spec)).isEqualTo(DRACONIC_SORCERY);
            assertThat(ClassProgression.requiredSubclass("Sorcerer", spec)).isEqualTo(DRACONIC_SORCERY);
            assertThat(ClassProgression.recommendedFightingStyles("sorcerer", spec)).isEmpty();
            assertThat(ClassProgression.subclassesFor("sorcerer", spec)).containsExactly(DRACONIC_SORCERY);
            assertThat(ClassProgression.subclassesFor("sorcerer")).contains(DRACONIC_SORCERY);
        }
        assertThat(ClassProgression.classSpecializations("sorcerer")).get()
                .satisfies(s -> {
                    assertThat(s.specializations()).containsExactlyElementsOf(EVOKER_SPECS);
                    assertThat(s.respecOnLevelUp()).isTrue();
                    assertThat(s.requiredSubclass()).isEqualTo(DRACONIC_SORCERY);
                });
        assertThat(ClassProgression.requiredSubclass("sorcerer", null)).isNull();
        assertThat(ClassProgression.requiredSubclass("sorcerer", "devastation")).isNull();
        assertThat(ClassProgression.subclassesFor("sorcerer", null)).hasSize(4);
        // No other variant requires a subclass.
        for (String[] c : new String[][] {{"barbarian", "Elemental"}, {"cleric", "Holy"}, {"druid", "Balance"},
                {"fighter", "Arms"}, {"paladin", "Holy"}, {"rogue", "Outlaw"}, {"warlock", "Affliction"}}) {
            assertThat(ClassProgression.requiredSubclass(c[0], c[1])).as(c[0]).isNull();
        }
        assertThat(ClassProgression.isClassSpecialization("sorcerer", "Destruction")).isFalse();
        assertThat(ClassProgression.isClassSpecialization("wizard", "Devastation")).isFalse();
    }

    @Test
    void preview_evoker_neverHasASubclassDue() {
        for (String spec : EVOKER_SPECS) {
            for (int level : List.of(1, 2, 4)) {
                LevelUpPreview p = service.preview(evoker(level, spec));
                assertThat(p.subclassDue()).isFalse();
                assertThat(p.recommendedSubclass()).isNull();
                assertThat(p.recommendedFightingStyles()).isEmpty();
            }
        }
    }

    @Test
    void evokerSpells_replaceDraconicSpellsAtLevel3_forEveryEvokerSpec() {
        for (String spec : EVOKER_SPECS) {
            assertThat(names(service.preview(evoker(2, spec)).featuresGained())).as(spec)
                    .containsExactly("Evoker Spells", "Draconic Resilience");
            PC s = evoker(2, spec);
            service.applyLevelUp(s);
            assertThat(s.getFeatures()).as(spec)
                    .contains("\"name\":\"Evoker Spells\"")
                    .contains("\"name\":\"Draconic Resilience\"")
                    .doesNotContain("\"name\":\"Draconic Spells\"");
        }
        // A base Draconic sorcerer keeps Draconic Spells and never gets Evoker Spells.
        assertThat(names(service.preview(sorcerer(2, DRACONIC_SORCERY)).featuresGained()))
                .containsExactly("Draconic Resilience", "Draconic Spells");
        PC base = sorcerer(2, DRACONIC_SORCERY);
        service.applyLevelUp(base);
        assertThat(base.getFeatures()).contains("\"name\":\"Draconic Spells\"").doesNotContain("Evoker Spells");
        // Other base sorcerers are unchanged too.
        assertThat(names(service.preview(sorcerer(2, "Aberrant Sorcery")).featuresGained()))
                .doesNotContain("Evoker Spells");
    }

    @Test
    void evokerSpells_onlyAtLevel3_andOtherLevelsUnchanged() {
        for (int level : List.of(1, 4, 5, 6, 9, 13, 17, 19)) {
            assertThat(names(service.preview(evoker(level, "Augmentation")).featuresGained()))
                    .as("level %d", level)
                    .containsExactlyElementsOf(names(service.preview(sorcerer(level, DRACONIC_SORCERY)).featuresGained()));
        }
        // The specialization-less overload yields the generic + Draconic list; the catalog
        // progression for an Evoker shows Evoker Spells instead.
        assertThat(names(com.moo.charactermanagerservice.progression.ClassFeatures
                .featuresAt("sorcerer", DRACONIC_SORCERY, 3)))
                .containsExactly("Draconic Resilience", "Draconic Spells");
        assertThat(com.moo.charactermanagerservice.progression.ClassFeatures
                .progression("sorcerer", DRACONIC_SORCERY, "Preservation"))
                .filteredOn(e -> e.name().equals("Evoker Spells"))
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.level()).isEqualTo(3);
                    assertThat(e.subclass()).isFalse();
                });
        // Without a subclass (reference data only) Evoker Spells is still listed as the class feature.
        assertThat(names(com.moo.charactermanagerservice.progression.ClassFeatures
                .featuresAt("sorcerer", null, "Devastation", 3)))
                .containsExactly("Evoker Spells");
    }

    @Test
    void evokerVariantFeature_doesNotAffectOtherVariants() {
        // The Shaman's in-place swap is unchanged.
        assertThat(names(com.moo.charactermanagerservice.progression.ClassFeatures
                .featuresAt("barbarian", PATH_OF_THE_ELEMENTS, "Elemental", 3)))
                .containsExactly("Totems", "Raging Elements");
        // Other WoW variants keep their level-3 features exactly.
        assertThat(names(service.preview(wowWarlock(2, "Destruction")).featuresGained()))
                .containsExactlyElementsOf(names(service.preview(warlock(2)).featuresGained()));
        assertThat(names(service.preview(wowRogue(2, "Subtlety")).featuresGained())).containsExactly("Steady Aim");
        assertThat(names(service.preview(wowCleric(2, "Holy")).featuresGained()))
                .containsExactlyElementsOf(names(service.preview(cleric(2)).featuresGained()));
        // A wizard (another arcane caster) is untouched by Evoker spec names.
        assertThat(names(com.moo.charactermanagerservice.progression.ClassFeatures
                .featuresAt("wizard", "Evoker", "Devastation", 3)))
                .containsExactlyElementsOf(names(com.moo.charactermanagerservice.progression.ClassFeatures
                        .featuresAt("wizard", "Evoker", 3)));
    }

    // --- WoW Hunter (ranger + Beast Mastery/Marksmanship/Survival; Pet Specialization for BM) ---

    private static final String BEAST_MASTER = "Beast Master";
    private static final List<String> HUNTER_SPECS = List.of("Beast Mastery", "Marksmanship", "Survival");
    private static final List<String> DH_SPECS = List.of("Devourer", "Havoc", "Vengeance");

    private static PC ranger(int level, String subclass) {
        PC r = pc("Ranger", level, 14, 10 * level, 10 * level);
        r.setSubclass(subclass);
        return r;
    }

    private static PC hunter(int level, String subclass, String spec) {
        PC r = ranger(level, subclass);
        r.setSpecialization(spec);
        return r;
    }

    @Test
    void applyLevelUp_ranger_acceptsEachHunterSpecialization_andRejectsOthers() {
        for (String spec : HUNTER_SPECS) {
            PC r = ranger(5, "Hunter"); // 5 -> 6: not an ASI level
            service.applyLevelUp(r, null, null, null, null, HpMode.AVERAGE, spec);
            assertThat(r.getSpecialization()).isEqualTo(spec);
            assertThat(r.getLevel()).isEqualTo((short) 6);
        }
        for (String bad : List.of("marksmanship", "Beast Master", "Havoc", "Vengeance", "Arms", "Balance")) {
            PC r = hunter(5, "Hunter", "Marksmanship");
            assertThatThrownBy(() -> service.applyLevelUp(r, null, null, null, null, HpMode.AVERAGE, bad))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(status(e)).isEqualTo(400));
            assertThat(r.getSpecialization()).isEqualTo("Marksmanship");
            assertThat(r.getLevel()).isEqualTo((short) 5);
        }
    }

    @Test
    void applyLevelUp_wowHunter_mayRespec() {
        PC r = hunter(5, BEAST_MASTER, "Beast Mastery");
        service.applyLevelUp(r, null, null, null, null, HpMode.AVERAGE, "Survival");
        assertThat(r.getSpecialization()).isEqualTo("Survival");
    }

    @Test
    void demonHunterAndHunterSpecializations_stayApart() {
        // A Demon Hunter keeps its hero list: no hunter specs.
        for (String spec : HUNTER_SPECS) {
            PC dh = demonHunter(5);
            assertThatThrownBy(() -> service.applyLevelUp(dh, null, null, null, null, HpMode.AVERAGE, spec))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(status(e)).isEqualTo(400));
            assertThat(dh.getSpecialization()).isNull();
        }
        // A non-DH ranger can't take a Demon Hunter spec.
        for (String spec : DH_SPECS) {
            PC r = ranger(5, "Hunter");
            assertThatThrownBy(() -> service.applyLevelUp(r, null, null, null, null, HpMode.AVERAGE, spec))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(status(e)).isEqualTo(400));
            assertThat(r.getSpecialization()).isNull();
        }
        // The DH still accepts its own.
        PC dh = demonHunter(5);
        service.applyLevelUp(dh, null, null, null, null, HpMode.AVERAGE, "Havoc");
        assertThat(dh.getSpecialization()).isEqualTo("Havoc");
        assertThat(ClassProgression.isClassSpecialization("ranger", "Havoc")).isFalse();
        assertThat(ClassProgression.isClassSpecialization("ranger", "Marksmanship")).isTrue();
    }

    @Test
    void classProgression_hunterLookups_returnExactNames() {
        assertThat(ClassProgression.recommendedSubclass("ranger", "Beast Mastery")).isEqualTo(BEAST_MASTER);
        assertThat(ClassProgression.recommendedSubclass("Ranger", "Marksmanship")).isEqualTo("Hunter");
        assertThat(ClassProgression.recommendedSubclass("ranger", "Survival")).isEqualTo(BEAST_MASTER);
        assertThat(ClassProgression.recommendedSubclass("ranger", "survival")).isNull();
        assertThat(ClassProgression.recommendedSubclass("ranger", "Havoc")).isNull();
        assertThat(ClassProgression.recommendedFightingStyles("ranger", "Beast Mastery")).containsExactly("Archery");
        assertThat(ClassProgression.recommendedFightingStyles("ranger", "Marksmanship")).containsExactly("Archery");
        assertThat(ClassProgression.recommendedFightingStyles("ranger", "Survival"))
                .containsExactly("Great Weapon Fighting");
        assertThat(ClassProgression.classSpecializations("ranger")).get()
                .satisfies(s -> {
                    assertThat(s.specializations()).containsExactlyElementsOf(HUNTER_SPECS);
                    assertThat(s.respecOnLevelUp()).isTrue();
                    assertThat(s.requiredSubclass()).isNull();
                });
        // Recommendations are real catalog names; no WoW-only ranger subclass is added.
        List<String> styles = FeatCatalog.fightingStyleOptions("ranger", null, 2);
        for (String spec : HUNTER_SPECS) {
            assertThat(ClassProgression.subclassesFor("ranger", spec))
                    .containsExactlyElementsOf(ClassProgression.subclassesFor("ranger"))
                    .contains(ClassProgression.recommendedSubclass("ranger", spec));
            assertThat(styles).containsAll(ClassProgression.recommendedFightingStyles("ranger", spec));
        }
    }

    @Test
    void preview_wowHunter_recommendsFightingStyleAt2_andSubclassAt3() {
        String[][] styleCases = {{"Beast Mastery", "Archery"}, {"Marksmanship", "Archery"},
                {"Survival", "Great Weapon Fighting"}};
        for (String[] c : styleCases) {
            LevelUpPreview p = service.preview(hunter(1, null, c[0])); // 1 -> 2: Fighting Style due
            assertThat(p.fightingStyleDue()).isTrue();
            assertThat(p.recommendedFightingStyles()).containsExactly(c[1]);
            assertThat(p.recommendedSubclass()).isNull();
        }
        String[][] subclassCases = {{"Beast Mastery", BEAST_MASTER}, {"Marksmanship", "Hunter"},
                {"Survival", BEAST_MASTER}};
        for (String[] c : subclassCases) {
            LevelUpPreview p = service.preview(hunter(2, null, c[0])); // 2 -> 3: subclass due
            assertThat(p.subclassDue()).isTrue();
            assertThat(p.subclassOptions()).contains(c[1]).doesNotContain(DEMON_HUNTER);
            assertThat(p.recommendedSubclass()).isEqualTo(c[1]);
            assertThat(p.recommendedFightingStyles()).isEmpty();
        }
        // A base ranger gets no recommendations.
        assertThat(service.preview(ranger(1, null)).recommendedFightingStyles()).isEmpty();
        assertThat(service.preview(ranger(2, null)).recommendedSubclass()).isNull();
    }

    @Test
    void petSpecialization_replacesRovingAt6_onlyForBeastMastery() {
        for (String subclass : List.of(BEAST_MASTER, "Hunter", "Fey Wanderer", "Gloom Stalker")) {
            assertThat(names(service.preview(hunter(5, subclass, "Beast Mastery")).featuresGained()))
                    .as(subclass).containsExactly("Pet Specialization");
            PC r = hunter(5, subclass, "Beast Mastery");
            service.applyLevelUp(r);
            assertThat(r.getFeatures()).as(subclass)
                    .contains("\"name\":\"Pet Specialization\"")
                    .contains("Cunning").contains("Ferocity").contains("Tenacity")
                    .doesNotContain("\"name\":\"Roving\"");
        }
        // Marksmanship and Survival hunters keep Roving, as does a base ranger and a Demon Hunter.
        for (String spec : List.of("Marksmanship", "Survival")) {
            assertThat(names(service.preview(hunter(5, BEAST_MASTER, spec)).featuresGained()))
                    .as(spec).containsExactly("Roving");
        }
        assertThat(names(service.preview(ranger(5, BEAST_MASTER)).featuresGained())).containsExactly("Roving");
        PC dh = demonHunter(5);
        dh.setSpecialization("Havoc");
        assertThat(names(service.preview(dh).featuresGained())).containsExactly("Roving");
        // A respec into Beast Mastery on the same level-up applies it at once.
        PC respec = hunter(5, BEAST_MASTER, "Survival");
        service.applyLevelUp(respec, null, null, null, null, HpMode.AVERAGE, "Beast Mastery");
        assertThat(respec.getFeatures()).contains("\"name\":\"Pet Specialization\"").doesNotContain("\"name\":\"Roving\"");
    }

    @Test
    void petSpecialization_onlyAtLevel6_andOtherVariantsUnchanged() {
        for (int level : List.of(1, 2, 4, 6, 8, 9, 12, 13, 16, 17, 19)) {
            assertThat(names(service.preview(hunter(level, BEAST_MASTER, "Beast Mastery")).featuresGained()))
                    .as("level %d", level)
                    .containsExactlyElementsOf(names(service.preview(ranger(level, BEAST_MASTER)).featuresGained()));
        }
        assertThat(com.moo.charactermanagerservice.progression.ClassFeatures
                .progression("ranger", BEAST_MASTER, "Beast Mastery"))
                .filteredOn(e -> e.name().equals("Pet Specialization"))
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.level()).isEqualTo(6);
                    assertThat(e.subclass()).isFalse();
                });
        List<String> marksmanship = com.moo.charactermanagerservice.progression.ClassFeatures
                .progression("ranger", BEAST_MASTER, "Marksmanship").stream().map(e -> e.name()).toList();
        assertThat(marksmanship).contains("Roving").doesNotContain("Pet Specialization");
        // Unrestricted variant features still apply to every spec of their class.
        for (String spec : SHAMAN_SPECS) {
            assertThat(names(com.moo.charactermanagerservice.progression.ClassFeatures
                    .featuresAt("barbarian", PATH_OF_THE_ELEMENTS, spec, 3)))
                    .containsExactly("Totems", "Raging Elements");
        }
        for (String spec : EVOKER_SPECS) {
            assertThat(names(service.preview(evoker(2, spec)).featuresGained())).contains("Evoker Spells");
        }
    }

    // --- WoW Mage (wizard + Arcane/Fire/Frost; recommends Evoker; WoW General feats) ---

    private static final List<String> MAGE_SPECS = List.of("Arcane", "Fire", "Frost");
    private static final List<String> MAGE_FEATS = List.of("Clearcasting", "Fingers of Frost", "Hot Streak");

    private static PC wizard(int level) {
        PC w = pc("Wizard", level, 14, 6 * level, 6 * level);
        if (level >= 3) w.setSubclass("Evoker");
        return w;
    }

    private static PC mage(int level, String spec) {
        PC w = wizard(level);
        w.setSpecialization(spec);
        return w;
    }

    @Test
    void applyLevelUp_wizard_acceptsEachMageSpecialization_andRejectsOthers() {
        for (String spec : MAGE_SPECS) {
            PC w = wizard(5); // 5 -> 6: not an ASI level
            service.applyLevelUp(w, null, null, null, null, HpMode.AVERAGE, spec);
            assertThat(w.getSpecialization()).isEqualTo(spec);
            assertThat(w.getLevel()).isEqualTo((short) 6);
        }
        for (String bad : List.of("arcane", "Holy", "Destruction", "Outlaw", "Brewmaster")) {
            PC w = mage(5, "Arcane");
            assertThatThrownBy(() -> service.applyLevelUp(w, null, null, null, null, HpMode.AVERAGE, bad))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(status(e)).isEqualTo(400));
            assertThat(w.getSpecialization()).isEqualTo("Arcane");
            assertThat(w.getLevel()).isEqualTo((short) 5);
        }
    }

    @Test
    void applyLevelUp_mageSpecialization_rejectedOnOtherClasses() {
        PC r = rogue(5);
        assertThatThrownBy(() -> service.applyLevelUp(r, null, null, null, null, HpMode.AVERAGE, "Arcane"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        assertThat(r.getSpecialization()).isNull();
        assertThat(r.getLevel()).isEqualTo((short) 5);
    }

    @Test
    void applyLevelUp_mage_mayRespec() {
        PC w = mage(5, "Fire");
        service.applyLevelUp(w, null, null, null, null, HpMode.AVERAGE, "Frost");
        assertThat(w.getSpecialization()).isEqualTo("Frost");
        assertThat(w.getSubclass()).isEqualTo("Evoker");
    }

    @Test
    void mage_recommendsEvoker_atTheLevel3Choice_andAddsNoWowOnlySchool() {
        for (String spec : MAGE_SPECS) {
            assertThat(ClassProgression.recommendedSubclass("wizard", spec)).isEqualTo("Evoker");
            assertThat(ClassProgression.subclassesFor("wizard", spec))
                    .containsExactlyElementsOf(ClassProgression.subclassesFor("wizard"));
            assertThat(ClassProgression.requiredSubclass("wizard", spec)).isNull();
            assertThat(ClassProgression.recommendedFightingStyles("wizard", spec)).isEmpty();

            LevelUpPreview p = service.preview(mage(2, spec)); // 2 -> 3: subclass due
            assertThat(p.subclassDue()).isTrue();
            assertThat(p.recommendedSubclass()).isEqualTo("Evoker");
            assertThat(p.subclassOptions()).contains("Evoker");
        }
        assertThat(ClassProgression.recommendedSubclass("Wizard", "Arcane")).isEqualTo("Evoker");
        assertThat(ClassProgression.recommendedSubclass("wizard", "fire")).isNull();
        assertThat(service.preview(wizard(2)).recommendedSubclass()).isNull();

        // Any wizard subclass is still accepted: the recommendation is advisory.
        PC w = mage(2, "Frost");
        service.applyLevelUp(w, "Illusionist");
        assertThat(w.getSubclass()).isEqualTo("Illusionist");
    }

    @Test
    void mage_addsNoFeatureOverrides() {
        for (int level : List.of(1, 2, 3, 5, 6, 10, 14, 17)) {
            for (String spec : MAGE_SPECS) {
                assertThat(names(service.preview(mage(level, spec)).featuresGained()))
                        .as("level %d %s", level, spec)
                        .containsExactlyElementsOf(names(service.preview(wizard(level)).featuresGained()));
            }
        }
    }

    @Test
    void mageFeats_offeredToEveryClassAtAsiLevels() {
        for (String clazz : List.of("Wizard", "Fighter", "Rogue", "Monk", "Cleric")) {
            PC p = pc(clazz, 3, 14, 20, 20); // 3 -> 4
            if (!clazz.equals("Wizard")) p.setSubclass(ClassProgression.subclassesFor(clazz).get(0));
            assertThat(service.preview(p).featOptions()).as(clazz).containsAll(MAGE_FEATS);
        }
        assertThat(service.preview(mage(7, "Fire")).featOptions()).containsAll(MAGE_FEATS);   // -> 8
        assertThat(service.preview(mage(18, "Frost")).featOptions()).containsAll(MAGE_FEATS); // -> 19
        assertThat(FeatCatalog.featOptions("wizard", 4)).containsAll(MAGE_FEATS).isSorted();
    }

    @Test
    void mageFeats_acceptedAtLevel4_andRecordedAsFeat() {
        for (String feat : MAGE_FEATS) {
            PC w = mage(3, "Arcane");
            w.setAbilityInt((short) 16);
            service.applyLevelUp(w, null, null, feat);
            assertThat(w.getLevel()).isEqualTo((short) 4);
            assertThat(w.getFeatures()).contains(feat).contains("Feat (Level 4)");
            assertThat(w.getAbilityInt()).isEqualTo((short) 16); // the +1 INT is left to the sheet flow
        }
        // Case-insensitive like every other feat, and open to non-mages.
        PC f = pc("Fighter", 3, 14, 28, 28);
        service.applyLevelUp(f, null, null, "hot streak");
        assertThat(f.getFeatures()).contains("Feat (Level 4)");
    }

    @Test
    void mageFeats_rejectedBelowLevel4_andOffNonAsiLevels() {
        for (String feat : MAGE_FEATS) {
            assertThat(FeatCatalog.isValidFeat(feat, "wizard", 3)).isFalse();
            assertThat(FeatCatalog.isValidFeat(feat, "wizard", 4)).isTrue();
            assertThat(FeatCatalog.featOptions("wizard", 3)).doesNotContain(feat);

            PC two = mage(2, "Arcane"); // 2 -> 3: no ASI/feat available
            assertThatThrownBy(() -> service.applyLevelUp(two, "Evoker", null, feat))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(status(e)).isEqualTo(400));
            assertThat(two.getLevel()).isEqualTo((short) 2);

            PC five = mage(4, "Arcane"); // 4 -> 5: not an ASI level
            assertThatThrownBy(() -> service.applyLevelUp(five, null, null, feat))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(status(e)).isEqualTo(400));
            assertThat(five.getLevel()).isEqualTo((short) 4);
        }
    }

    @Test
    void mage_otherVariantsUnchanged() {
        assertThat(ClassProgression.recommendedSubclass("warlock", "Destruction")).isEqualTo("Fiend Patron");
        assertThat(ClassProgression.recommendedSubclass("sorcerer", "Devastation")).isEqualTo("Draconic Sorcery");
        assertThat(ClassProgression.recommendedSubclass("rogue", "Outlaw")).isEqualTo(SWASHBUCKLER);
        assertThat(ClassProgression.isClassSpecialization("sorcerer", "Fire")).isFalse();
        assertThat(ClassProgression.isClassSpecialization("paladin", "Frost")).isFalse();
        assertThat(ClassProgression.isClassSpecialization("wizard", "Destruction")).isFalse();
        // The PHB catalog is untouched alongside the WoW feats.
        assertThat(FeatCatalog.featOptions("wizard", 4)).contains("War Caster", "Sentinel").doesNotContain("Archery");
        assertThat(FeatCatalog.featOptions("fighter", 4)).contains("Archery");
    }

    // --- WoW Monk (monk + Brewmaster/Mistweaver/Windwalker, Warrior of Mystic Brews, DEX/WIS feats) ---

    private static final List<String> MONK_SPECS = List.of("Brewmaster", "Mistweaver", "Windwalker");
    private static final List<String> MONK_FEATS =
            List.of("Elusive Brawler", "Enveloping Mist", "Fists of Fury", "Stagger and Sway");
    private static final String MYSTIC_BREWS = "Warrior of Mystic Brews";

    private static PC monk(int level) {
        PC m = pc("Monk", level, 14, 8 * level, 8 * level);
        if (level >= 3) m.setSubclass("Warrior of the Open Hand");
        m.setAbilityDex((short) 16);
        m.setAbilityWis((short) 14);
        return m;
    }

    private static PC wowMonk(int level, String spec) {
        PC m = monk(level);
        m.setSpecialization(spec);
        return m;
    }

    private static PC brewmaster(int level) {
        PC m = wowMonk(level, "Brewmaster");
        m.setSubclass(MYSTIC_BREWS);
        return m;
    }

    @Test
    void applyLevelUp_monk_acceptsEachWowSpecialization_andRejectsOthers() {
        for (String spec : MONK_SPECS) {
            PC m = monk(5); // 5 -> 6: not an ASI level
            service.applyLevelUp(m, null, null, null, null, HpMode.AVERAGE, spec);
            assertThat(m.getSpecialization()).isEqualTo(spec);
            assertThat(m.getLevel()).isEqualTo((short) 6);
        }
        for (String bad : List.of("brewmaster", "Arcane", "Holy", "Outlaw", "Restoration")) {
            PC m = wowMonk(5, "Windwalker");
            assertThatThrownBy(() -> service.applyLevelUp(m, null, null, null, null, HpMode.AVERAGE, bad))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(status(e)).isEqualTo(400));
            assertThat(m.getSpecialization()).isEqualTo("Windwalker");
            assertThat(m.getLevel()).isEqualTo((short) 5);
        }
        PC r = rogue(5);
        assertThatThrownBy(() -> service.applyLevelUp(r, null, null, null, null, HpMode.AVERAGE, "Mistweaver"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
    }

    @Test
    void applyLevelUp_wowMonk_mayRespec() {
        PC m = wowMonk(5, "Mistweaver");
        service.applyLevelUp(m, null, null, null, null, HpMode.AVERAGE, "Brewmaster");
        assertThat(m.getSpecialization()).isEqualTo("Brewmaster");
        assertThat(m.getSubclass()).isEqualTo("Warrior of the Open Hand");
    }

    @Test
    void wowMonk_recommendedSubclass_inPreviewAtLevel3() {
        assertThat(ClassProgression.recommendedSubclass("monk", "Brewmaster")).isEqualTo(MYSTIC_BREWS);
        assertThat(ClassProgression.recommendedSubclass("monk", "Mistweaver")).isEqualTo("Warrior of Mercy");
        assertThat(ClassProgression.recommendedSubclass("Monk", "Windwalker")).isEqualTo("Warrior of the Open Hand");
        assertThat(ClassProgression.recommendedSubclass("monk", "windwalker")).isNull();
        for (String spec : MONK_SPECS) {
            LevelUpPreview p = service.preview(wowMonk(2, spec)); // 2 -> 3
            assertThat(p.subclassDue()).isTrue();
            assertThat(p.recommendedSubclass()).isEqualTo(ClassProgression.recommendedSubclass("monk", spec));
            assertThat(p.subclassOptions()).contains(p.recommendedSubclass());
            assertThat(p.recommendedFightingStyles()).isEmpty();
        }
        assertThat(service.preview(monk(2)).recommendedSubclass()).isNull();
    }

    @Test
    void mysticBrews_offeredOnlyToWowMonks() {
        for (String spec : MONK_SPECS) {
            assertThat(service.preview(wowMonk(2, spec)).subclassOptions())
                    .containsAll(ClassProgression.subclassesFor("monk")).contains(MYSTIC_BREWS);
        }
        assertThat(service.preview(monk(2)).subclassOptions()).doesNotContain(MYSTIC_BREWS);
        assertThat(ClassProgression.subclassesFor("monk")).doesNotContain(MYSTIC_BREWS);
        assertThat(service.preview(wowRogue(2, "Outlaw")).subclassOptions()).doesNotContain(MYSTIC_BREWS);
    }

    @Test
    void mysticBrews_acceptedForWowMonkAtLevel3_andRejectedOtherwise() {
        PC m = wowMonk(2, "Brewmaster");
        service.applyLevelUp(m, "warrior of mystic brews");
        assertThat(m.getSubclass()).isEqualTo("warrior of mystic brews");
        assertThat(m.getFeatures()).contains("Bonus Proficiencies", "Drunken Technique");

        // A monk choosing its specialization in the same request may take it.
        PC base = monk(2);
        service.applyLevelUp(base, MYSTIC_BREWS, null, null, null, HpMode.AVERAGE, "Windwalker");
        assertThat(base.getSubclass()).isEqualTo(MYSTIC_BREWS);

        PC plain = monk(2);
        assertThatThrownBy(() -> service.applyLevelUp(plain, MYSTIC_BREWS))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        assertThat(plain.getSubclass()).isNull();

        PC rogue = wowRogue(2, "Outlaw");
        assertThatThrownBy(() -> service.applyLevelUp(rogue, MYSTIC_BREWS))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));

        PC late = wowMonk(3, "Brewmaster"); // subclass already chosen; 3 -> 4
        late.setSubclass(null);
        assertThatThrownBy(() -> service.applyLevelUp(late, MYSTIC_BREWS))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
    }

    @Test
    void mysticBrews_featuresAt3_6_11_17() {
        assertThat(names(service.preview(brewmaster(2)).featuresGained()))
                .contains("Bonus Proficiencies", "Drunken Technique");
        assertThat(names(service.preview(brewmaster(5)).featuresGained()))
                .contains("Tipsy Sway", "Mystic Brew");
        assertThat(names(service.preview(brewmaster(10)).featuresGained())).contains("Wizened Brewer");
        assertThat(names(service.preview(brewmaster(16)).featuresGained()))
                .contains("Master Brewer", "Intoxicated Frenzy");
        // Purely additive: the generic Monk features at each level are kept.
        for (int level : List.of(2, 5, 10, 16)) {
            assertThat(names(service.preview(brewmaster(level)).featuresGained()))
                    .containsAll(names(com.moo.charactermanagerservice.progression.ClassFeatures.featuresAt("monk", level + 1)));
        }
    }

    @Test
    void wowMonk_addsNoFeatureOverrides() {
        for (int level : List.of(1, 2, 4, 5, 9, 13, 19)) {
            for (String spec : MONK_SPECS) {
                assertThat(names(service.preview(wowMonk(level, spec)).featuresGained()))
                        .as("level %d %s", level, spec)
                        .containsExactlyElementsOf(names(service.preview(monk(level)).featuresGained()));
            }
        }
    }

    @Test
    void monkFeats_offeredAtLevel4_withDexAndWis13() {
        for (String clazz : List.of("Monk", "Rogue", "Wizard", "Fighter")) {
            PC p = pc(clazz, 3, 14, 20, 20); // 3 -> 4
            p.setAbilityDex((short) 13);
            p.setAbilityWis((short) 13);
            assertThat(service.preview(p).featOptions()).as(clazz).containsAll(MONK_FEATS);
        }
        assertThat(service.preview(wowMonk(7, "Windwalker")).featOptions()).containsAll(MONK_FEATS); // -> 8
        assertThat(service.preview(monk(3)).featOptions()).containsAll(MONK_FEATS).isSorted();
    }

    @Test
    void monkFeats_notOfferedWhenDexOrWisBelow13() {
        PC lowDex = monk(3);
        lowDex.setAbilityDex((short) 12);
        PC lowWis = monk(3);
        lowWis.setAbilityWis((short) 12);
        PC unset = pc("Monk", 3, 14, 20, 20); // no DEX/WIS recorded
        for (PC p : List.of(lowDex, lowWis, unset)) {
            assertThat(service.preview(p).featOptions())
                    .doesNotContainAnyElementsOf(MONK_FEATS)
                    .contains("Sentinel", "Clearcasting");
        }
        assertThat(FeatCatalog.featOptions("monk", 4)).doesNotContainAnyElementsOf(MONK_FEATS);
    }

    @Test
    void monkFeats_acceptedAtLevel4_withDexAndWis13() {
        for (String feat : MONK_FEATS) {
            PC m = wowMonk(3, "Brewmaster");
            m.setAbilityDex((short) 13);
            m.setAbilityWis((short) 13);
            service.applyLevelUp(m, null, null, feat);
            assertThat(m.getLevel()).isEqualTo((short) 4);
            assertThat(m.getFeatures()).contains(feat).contains("Feat (Level 4)");
            assertThat(m.getAbilityDex()).isEqualTo((short) 13); // the +1 DEX/WIS is left to the sheet flow
        }
        PC r = pc("Rogue", 3, 14, 20, 20);
        r.setAbilityDex((short) 18);
        r.setAbilityWis((short) 13);
        service.applyLevelUp(r, null, null, "fists of fury");
        assertThat(r.getFeatures()).contains("Feat (Level 4)");
    }

    @Test
    void monkFeats_rejectedWhenDexOrWisBelow13_withAClearMessage() {
        for (String feat : MONK_FEATS) {
            for (int[] dexWis : new int[][] {{12, 16}, {16, 12}, {10, 10}}) {
                PC m = monk(3);
                m.setAbilityDex((short) dexWis[0]);
                m.setAbilityWis((short) dexWis[1]);
                assertThatThrownBy(() -> service.applyLevelUp(m, null, null, feat))
                        .isInstanceOf(ResponseStatusException.class)
                        .satisfies(e -> assertThat(status(e)).isEqualTo(400))
                        .hasMessageContaining("requires Dexterity 13 and Wisdom 13");
                assertThat(m.getLevel()).isEqualTo((short) 3);
                assertThat(m.getFeatures()).isNull();
            }
        }
        assertThat(FeatCatalog.unmetAbilityPrerequisite("Fists of Fury", "monk", 4, Map.of("DEX", 13, "WIS", 13)))
                .isNull();
        assertThat(FeatCatalog.unmetAbilityPrerequisite("Sentinel", "monk", 4, Map.of())).isNull();
        assertThat(FeatCatalog.unmetAbilityPrerequisite("Fists of Fury", "monk", 3, Map.of())).isNull();
    }

    @Test
    void monkFeats_rejectedBelowLevel4_andOffNonAsiLevels() {
        Map<String, Integer> qualified = Map.of("DEX", 16, "WIS", 16);
        for (String feat : MONK_FEATS) {
            assertThat(FeatCatalog.isValidFeat(feat, "monk", 3, qualified)).isFalse();
            assertThat(FeatCatalog.isValidFeat(feat, "monk", 4, qualified)).isTrue();
            assertThat(FeatCatalog.featOptions("monk", 3, qualified)).doesNotContain(feat);

            PC two = wowMonk(2, "Windwalker"); // 2 -> 3: no ASI/feat available
            assertThatThrownBy(() -> service.applyLevelUp(two, "Warrior of the Open Hand", null, feat))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(status(e)).isEqualTo(400));

            PC five = monk(4); // 4 -> 5: not an ASI level
            assertThatThrownBy(() -> service.applyLevelUp(five, null, null, feat))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        }
    }

    @Test
    void wowMonk_otherVariantsUnchanged() {
        for (String spec : MAGE_SPECS) {
            assertThat(ClassProgression.recommendedSubclass("wizard", spec)).isEqualTo("Evoker");
        }
        assertThat(ClassProgression.recommendedSubclass("rogue", "Outlaw")).isEqualTo(SWASHBUCKLER);
        assertThat(ClassProgression.subclassesFor("rogue", "Outlaw")).doesNotContain(MYSTIC_BREWS);
        assertThat(ClassProgression.subclassesFor("wizard", "Arcane")).doesNotContain(MYSTIC_BREWS);
        assertThat(ClassProgression.isClassSpecialization("druid", "Brewmaster")).isFalse();
        assertThat(ClassProgression.isClassSpecialization("cleric", "Mistweaver")).isFalse();
        // Ability prerequisites touch only the monk feats: PHB and mage feats still need no scores.
        assertThat(FeatCatalog.featOptions("monk", 4)).contains("Sentinel", "Clearcasting", "Hot Streak");
        for (String feat : MAGE_FEATS) {
            assertThat(FeatCatalog.isValidFeat(feat, "wizard", 4)).isTrue();
        }
    }
}
