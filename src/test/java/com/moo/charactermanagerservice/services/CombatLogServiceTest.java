package com.moo.charactermanagerservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moo.charactermanagerservice.dto.CombatLogPageView;
import com.moo.charactermanagerservice.models.CombatLogType;
import com.moo.charactermanagerservice.models.CombatSession;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.models.SessionCombatLog;
import com.moo.charactermanagerservice.models.SessionStatus;
import com.moo.charactermanagerservice.repositories.SessionCombatLogRepository;
import com.moo.charactermanagerservice.services.CombatLogService.Hp;
import com.moo.charactermanagerservice.services.CombatLogService.Vitals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CombatLogServiceTest {

    @Mock
    private SessionCombatLogRepository combatLogRepository;

    private CombatLogService combatLog;
    private CombatSession session;
    private UUID actor;

    @BeforeEach
    void setUp() {
        combatLog = new CombatLogService(combatLogRepository, new ObjectMapper());
        session = new CombatSession();
        session.setId(1L);
        session.setStatus(SessionStatus.ACTIVE);
        session.setRound((short) 2);
        actor = UUID.randomUUID();
    }

    private SessionCombatLog saved() {
        ArgumentCaptor<SessionCombatLog> row = ArgumentCaptor.forClass(SessionCombatLog.class);
        verify(combatLogRepository).save(row.capture());
        return row.getValue();
    }

    private static Vitals vitals(int cur, int temp, int max, List<String> conditions, int exhaustion) {
        return new Vitals(cur, temp, max, conditions, exhaustion);
    }

    // --- logHpDelta ---

    @Test
    void damage_fullyAbsorbedByTemporaryHp_saysSo_andLeavesHpOut() {
        combatLog.logHpDelta(session, "Gorath", 3, new Hp(12, 5), new Hp(12, 2), CombatLogService.DOWN, actor);

        SessionCombatLog row = saved();
        assertThat(row.getEventType()).isEqualTo(CombatLogType.DAMAGE);
        assertThat(row.getDescription()).isEqualTo("Took 3 damage, all absorbed by temporary HP (5 → 2 left).");
    }

    @Test
    void damage_onARowAlreadyAtZero_isNotLogged() {
        combatLog.logHpDelta(session, "Gorath", 6, new Hp(0, 0), new Hp(0, 0), CombatLogService.DOWN, actor);

        verify(combatLogRepository, never()).save(any());
    }

    @Test
    void damage_thatLeavesTheTargetStanding_doesNotAddTheAtZeroNote() {
        combatLog.logHpDelta(session, "Goblin", 4, new Hp(9, 0), new Hp(5, 0), CombatLogService.DEFEATED, actor);

        assertThat(saved().getDescription()).isEqualTo("Took 4 damage. HP 9 → 5.");
    }

    @Test
    void aZeroAmount_isNotLogged() {
        combatLog.logHpDelta(session, "Goblin", 0, new Hp(9, 0), new Hp(9, 0), null, actor);

        verify(combatLogRepository, never()).save(any());
    }

    @Test
    void aBlankSubject_isStoredAsUnknown_soTheRowNeverBreaksTheNotNullColumn() {
        combatLog.logHpDelta(session, " ", 4, new Hp(9, 0), new Hp(5, 0), null, actor);

        assertThat(saved().getSubjectName()).isEqualTo("Unknown");
    }

    // --- logHitDie / logLongRest ---

    @Test
    void hitDie_isLoggedAsHealing_andSkippedWhenItHealedNothing() {
        combatLog.logHitDie(session, "Gorath", new Hp(20, 0), new Hp(20, 0), actor);
        verify(combatLogRepository, never()).save(any());

        combatLog.logHitDie(session, "Gorath", new Hp(10, 0), new Hp(16, 0), actor);
        SessionCombatLog row = saved();
        assertThat(row.getEventType()).isEqualTo(CombatLogType.HEALING);
        assertThat(row.getDescription()).isEqualTo("Spent a Hit Die and healed 6. HP 10 → 16.");
    }

    @Test
    void longRest_listsEveryVitalItMoved() {
        combatLog.logLongRest(session, "Gorath",
                vitals(12, 4, 30, List.of(), 2), vitals(30, 0, 30, List.of(), 1), actor);

        SessionCombatLog row = saved();
        assertThat(row.getEventType()).isEqualTo(CombatLogType.HEALING);
        assertThat(row.getDescription())
                .isEqualTo("Finished a long rest. HP 12 → 30. Temporary HP 4 → 0. Exhaustion 2 → 1.");
    }

    @Test
    void longRest_thatMovedNothing_isNotLogged() {
        Vitals rested = vitals(30, 0, 30, List.of("Prone"), 0);

        combatLog.logLongRest(session, "Gorath", rested, rested, actor);

        verify(combatLogRepository, never()).save(any());
    }

    // --- logSheetChange ---

    @Test
    void sheetChange_ofConditionsOnly_isAConditionRow_namingWhoChangedIt() {
        boolean logged = combatLog.logSheetChange(session, "Gorath",
                vitals(12, 0, 30, List.of("Prone"), 0),
                vitals(12, 0, 30, List.of("Poisoned", "Blinded"), 0), true, actor);

        assertThat(logged).isTrue();
        SessionCombatLog row = saved();
        assertThat(row.getEventType()).isEqualTo(CombatLogType.CONDITION);
        assertThat(row.getDescription()).isEqualTo("Conditions added: Poisoned, Blinded. "
                + "Condition removed: Prone. Changed on the sheet by the DM.");
    }

    @Test
    void sheetChange_ofCurrentHp_winsTheRowType_overEverythingElse() {
        combatLog.logSheetChange(session, "Gorath",
                vitals(12, 0, 30, List.of(), 0), vitals(8, 0, 30, List.of("Prone"), 1), false, actor);

        SessionCombatLog row = saved();
        assertThat(row.getEventType()).isEqualTo(CombatLogType.DAMAGE);
        assertThat(row.getDescription()).isEqualTo(
                "HP 12 → 8. Condition added: Prone. Exhaustion 0 → 1. Changed on the sheet by the player.");
    }

    @Test
    void sheetChange_ofTemporaryOrMaximumHpOnly_isAHitPointsRow() {
        combatLog.logSheetChange(session, "Gorath",
                vitals(12, 0, 30, List.of(), 0), vitals(12, 5, 35, List.of(), 0), false, actor);

        SessionCombatLog row = saved();
        assertThat(row.getEventType()).isEqualTo(CombatLogType.HIT_POINTS);
        assertThat(row.getDescription()).isEqualTo(
                "Temporary HP 0 → 5. Maximum HP 30 → 35. Changed on the sheet by the player.");
    }

    @Test
    void sheetChange_withNothingMoved_logsNothing() {
        Vitals same = vitals(12, 0, 30, List.of("Prone"), 0);

        assertThat(combatLog.differ(same, vitals(12, 0, 30, List.of("Prone"), 0))).isFalse();
        assertThat(combatLog.logSheetChange(session, "Gorath", same, same, false, actor)).isFalse();
        verify(combatLogRepository, never()).save(any());
    }

    // --- the round ---

    @Test
    void theRound_isStoredOnlyWhileAnEncounterRuns() {
        session.setStatus(SessionStatus.LOBBY);

        combatLog.logHpDelta(session, "Goblin", 4, new Hp(9, 0), new Hp(5, 0), null, actor);

        assertThat(saved().getRound()).isNull();
    }

    // --- reading vitals ---

    @Test
    void vitalsOf_readsACharacter_treatingMissingNumbersAsZero_andBadConditionsAsNone() {
        PC pc = new PC();
        pc.setHpCurrent((short) 9);
        pc.setConditions("[\"Prone\",4,null,\"Poisoned\"]");

        Vitals vitals = combatLog.vitalsOf(pc);

        assertThat(vitals).isEqualTo(vitals(9, 0, 0, List.of("Prone", "Poisoned"), 0));

        pc.setConditions("not json");
        assertThat(combatLog.vitalsOf(pc).conditions()).isEmpty();
    }

    @Test
    void vitalsOfStatBlock_readsAFollowersSheet_andForgivesAnOddOne() {
        Vitals vitals = combatLog.vitalsOfStatBlock(Map.of(
                "hp", Map.of("cur", 9, "max", 16, "temp", 2), "conditions", List.of("Prone")));

        assertThat(vitals).isEqualTo(vitals(9, 2, 16, List.of("Prone"), 0));
        assertThat(combatLog.vitalsOfStatBlock(Map.of("hp", "lots")))
                .isEqualTo(vitals(0, 0, 0, List.of(), 0));
        assertThat(combatLog.vitalsOfStatBlock(null)).isEqualTo(vitals(0, 0, 0, List.of(), 0));
    }

    @Test
    void followerSubject_namesTheOwner_whenThereIsOne() {
        assertThat(CombatLogService.followerSubject("Wolf", "Thorin")).isEqualTo("Wolf (Thorin's follower)");
        assertThat(CombatLogService.followerSubject("Wolf", null)).isEqualTo("Wolf");
        assertThat(CombatLogService.followerSubject(" ", "Thorin")).isEqualTo("Follower (Thorin's follower)");
    }

    // --- page / purge ---

    @Test
    void page_defaultsToTheFirstTen_andClampsWhatItIsAsked() {
        when(combatLogRepository.findBySessionIdOrderByIdDesc(eq(1L), any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<>(List.of(), inv.getArgument(1), 0));

        CombatLogPageView defaults = combatLog.page(1L, null, null);
        assertThat(defaults.page()).isZero();
        assertThat(defaults.size()).isEqualTo(10);
        assertThat(defaults.total()).isZero();
        assertThat(defaults.entries()).isEmpty();

        CombatLogPageView clamped = combatLog.page(1L, -3, 5000);
        assertThat(clamped.page()).isZero();
        assertThat(clamped.size()).isEqualTo(50);

        assertThat(combatLog.page(1L, 4, 0).size()).isEqualTo(1);
    }

    @Test
    void purge_deletesTheSessionsRows() {
        combatLog.purge(1L);

        verify(combatLogRepository).deleteBySessionId(1L);
    }
}
