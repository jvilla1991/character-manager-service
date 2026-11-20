package com.moo.charactermanagerservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moo.charactermanagerservice.dto.FollowerCombatantView;
import com.moo.charactermanagerservice.dto.HitDieSpendResult;
import com.moo.charactermanagerservice.dto.LogRollRequest;
import com.moo.charactermanagerservice.dto.LongRestChoices;
import com.moo.charactermanagerservice.dto.LongRestView;
import com.moo.charactermanagerservice.dto.SetTimeRequest;
import com.moo.charactermanagerservice.dto.ParticipantView;
import com.moo.charactermanagerservice.dto.SessionRollView;
import com.moo.charactermanagerservice.dto.SessionStateView;
import com.moo.charactermanagerservice.dto.XpAwardResult;
import com.moo.charactermanagerservice.models.Campaign;
import com.moo.charactermanagerservice.models.CombatSession;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.models.PcActivityType;
import com.moo.charactermanagerservice.models.PcFollower;
import com.moo.charactermanagerservice.models.RestType;
import com.moo.charactermanagerservice.models.RestVoteStatus;
import com.moo.charactermanagerservice.models.SessionLongRestResult;
import com.moo.charactermanagerservice.models.SessionLoot;
import com.moo.charactermanagerservice.models.SessionParticipant;
import com.moo.charactermanagerservice.models.SessionRestVote;
import com.moo.charactermanagerservice.models.SessionRestVoteBallot;
import com.moo.charactermanagerservice.models.SessionRoll;
import com.moo.charactermanagerservice.models.SessionStatus;
import com.moo.charactermanagerservice.models.Encounter;
import com.moo.charactermanagerservice.models.EncounterCreature;
import com.moo.charactermanagerservice.repositories.CampaignRepository;
import com.moo.charactermanagerservice.repositories.CombatSessionRepository;
import com.moo.charactermanagerservice.repositories.EncounterCreatureRepository;
import com.moo.charactermanagerservice.repositories.EncounterRepository;
import com.moo.charactermanagerservice.repositories.PCRepository;
import com.moo.charactermanagerservice.repositories.PcFollowerRepository;
import com.moo.charactermanagerservice.repositories.SessionLootItemRepository;
import com.moo.charactermanagerservice.repositories.SessionLootRepository;
import com.moo.charactermanagerservice.repositories.SessionParticipantRepository;
import com.moo.charactermanagerservice.repositories.SessionLongRestResultRepository;
import com.moo.charactermanagerservice.repositories.SessionLootResultRepository;
import com.moo.charactermanagerservice.repositories.SessionRestVoteBallotRepository;
import com.moo.charactermanagerservice.repositories.SessionRestVoteRepository;
import com.moo.charactermanagerservice.models.SessionCombatLog;
import com.moo.charactermanagerservice.models.CombatLogType;
import com.moo.charactermanagerservice.dto.CombatLogPageView;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import com.moo.charactermanagerservice.repositories.SessionCombatLogRepository;
import com.moo.charactermanagerservice.repositories.SessionRollRepository;
import com.moo.charactermanagerservice.repositories.SessionShopAttendeeRepository;
import com.moo.charactermanagerservice.repositories.SessionShopRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the DM XP-award flow ({@link SessionService#awardXp} /
 * {@link SessionService#awardXpToAll}) and the initiative-tracker turn engine
 * ({@link SessionService#startEncounter} / {@link SessionService#advanceTurn} /
 * pointer-safe removal). Pure Mockito — mirrors {@code ShopServiceTest}'s
 * style for DM-owned, write-through session actions.
 */
@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock private CombatSessionRepository sessionRepository;
    @Mock private SessionParticipantRepository participantRepository;
    @Mock private SessionShopRepository shopRepository;
    @Mock private SessionShopAttendeeRepository shopAttendeeRepository;
    @Mock private SessionLootRepository lootRepository;
    @Mock private SessionLootItemRepository lootItemRepository;
    @Mock private PCRepository pcRepository;
    @Mock private PCService pcService;
    @Mock private CampaignService campaignService;
    @Mock private CampaignRepository campaignRepository;
    @Mock private EncounterRepository encounterRepository;
    @Mock private EncounterCreatureRepository encounterCreatureRepository;
    @Mock private PcActivityLogService activityLogService;
    @Mock private SessionRollRepository sessionRollRepository;
    @Mock private SessionRestVoteRepository restVoteRepository;
    @Mock private SessionRestVoteBallotRepository restVoteBallotRepository;
    @Mock private SessionLootResultRepository lootResultRepository;
    @Mock private SessionLongRestResultRepository longRestResultRepository;
    @Mock private PcFollowerRepository followerRepository;
    @Mock private SessionCombatLogRepository combatLogRepository;

    private SessionService sessionService;

    private UUID dmId;
    private UUID playerId;
    private UUID strangerId;
    private CombatSession session;
    private Campaign campaign;

    @BeforeEach
    void setUp() {
        sessionService = new SessionService(sessionRepository, participantRepository, shopRepository,
                shopAttendeeRepository, lootRepository, lootItemRepository, pcRepository, pcService,
                campaignService, campaignRepository, encounterRepository, encounterCreatureRepository,
                activityLogService, sessionRollRepository, restVoteRepository,
                restVoteBallotRepository, lootResultRepository, longRestResultRepository,
                followerRepository, new CombatLogService(combatLogRepository, new ObjectMapper()),
                new ObjectMapper());

        dmId = UUID.randomUUID();
        playerId = UUID.randomUUID();
        strangerId = UUID.randomUUID();

        session = new CombatSession();
        session.setId(1L);
        session.setCampaignId(1L);
        session.setDmUserId(dmId);
        session.setStatus(SessionStatus.ACTIVE);

        // buildState reads the campaign clock on every snapshot; variant checks
        // read variantRules. Lenient: not every test path builds a snapshot.
        campaign = new Campaign();
        campaign.setId(1L);
        campaign.setDmUserId(dmId);
        lenient().when(campaignService.findById(1L)).thenReturn(campaign);
        // No defined week unless a test sets one — Mockito's default would be an
        // empty list, but the real service contract is null for "no definition".
        lenient().when(campaignService.parseWeekDays(any(Campaign.class))).thenReturn(null);
        lenient().when(campaignService.isVariantEnabled(any(Campaign.class), anyString()))
                .thenAnswer(inv -> {
                    Campaign c = inv.getArgument(0);
                    String key = inv.getArgument(1);
                    String rules = c.getVariantRules();
                    return rules != null && rules.contains("\"" + key + "\":true");
                });
        // buildState's roll-log fetch — empty unless a test stubs specific rows.
        lenient().when(sessionRollRepository.findTop50BySessionIdOrderByCreatedAtDescIdDesc(anyLong()))
                .thenReturn(List.of());
    }

    // --- awardXp (single) ---

    @Test
    void awardXp_writesThroughToPc_andBumpsVersion() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(participantRepository.findById(5L)).thenReturn(Optional.of(participant(5L, 7L)));
        PC pc = pc(7L, "Gorath", 1000);
        when(pcService.findPCById(7L)).thenReturn(pc);

        XpAwardResult result = sessionService.awardXp(1L, 5L, 500, dmId);

        assertThat(pc.getXp()).isEqualTo(1500);
        assertThat(result.awarded()).hasSize(1);
        assertThat(result.awarded().get(0).pcId()).isEqualTo(7L);
        assertThat(result.awarded().get(0).name()).isEqualTo("Gorath");
        assertThat(result.awarded().get(0).xp()).isEqualTo(1500);
        assertThat(result.awarded().get(0).delta()).isEqualTo(500);
        verify(pcRepository).save(pc);
        verify(sessionRepository).save(session); // version bumped
        verify(activityLogService).log(7L, PcActivityType.XP_AWARD, "Awarded 500 XP", dmId);
    }

    @Test
    void awardXp_floorsAtZero_onNegativeCorrection() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(participantRepository.findById(5L)).thenReturn(Optional.of(participant(5L, 7L)));
        PC pc = pc(7L, "Gorath", 100);
        when(pcService.findPCById(7L)).thenReturn(pc);

        XpAwardResult result = sessionService.awardXp(1L, 5L, -250, dmId);

        assertThat(pc.getXp()).isZero();
        assertThat(result.awarded().get(0).xp()).isZero();
        assertThat(result.awarded().get(0).delta()).isEqualTo(-100); // actual change, not requested -250
        // Logs the APPLIED delta (100, after flooring), not the requested -250.
        verify(activityLogService).log(7L, PcActivityType.XP_AWARD, "Removed 100 XP", dmId);
    }

    @Test
    void awardXp_skipsLog_whenFlooringMakesTheAppliedDeltaZero() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(participantRepository.findById(5L)).thenReturn(Optional.of(participant(5L, 7L)));
        PC pc = pc(7L, "Gorath", 0); // already at zero — a further negative award applies no change

        when(pcService.findPCById(7L)).thenReturn(pc);

        sessionService.awardXp(1L, 5L, -100, dmId);

        verify(activityLogService, never()).log(any(), any(), any(), any());
    }

    @Test
    void awardXp_throws400_forNonPcCombatant() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(participantRepository.findById(5L)).thenReturn(Optional.of(participant(5L, null))); // NPC

        assertThatThrownBy(() -> sessionService.awardXp(1L, 5L, 500, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        verify(pcRepository, never()).save(any());
    }

    @Test
    void awardXp_throws403_whenNotDm() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.awardXp(1L, 5L, 500, strangerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
    }

    @Test
    void awardXp_throws409_whenSessionEnded() {
        session.setStatus(SessionStatus.ENDED);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.awardXp(1L, 5L, 500, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
    }

    @Test
    void awardXp_throws400_whenAmountNull() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.awardXp(1L, 5L, null, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
    }

    // --- awardXpToAll ---

    @Test
    void awardXpToAll_awardsSameAmountToEachPc_andSkipsNpcs() {
        SessionParticipant pc7 = participant(5L, 7L);
        SessionParticipant npc = participant(6L, null);
        SessionParticipant pc8 = participant(9L, 8L);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(participantRepository.findBySessionIdOrderByOrderIndexAsc(1L))
                .thenReturn(List.of(pc7, npc, pc8));
        when(pcRepository.findAllById(any()))
                .thenReturn(List.of(pc(7L, "Gorath", 1000), pc(8L, "Mira", 0)));

        XpAwardResult result = sessionService.awardXpToAll(1L, 300, dmId);

        assertThat(result.awarded()).hasSize(2); // NPC skipped
        assertThat(result.awarded()).extracting("name").containsExactlyInAnyOrder("Gorath", "Mira");
        assertThat(result.awarded()).allSatisfy(e -> assertThat(e.delta()).isEqualTo(300));
        verify(pcRepository, times(2)).save(any(PC.class));
        verify(sessionRepository).save(session); // single version bump
        verify(activityLogService).log(7L, PcActivityType.XP_AWARD, "Awarded 300 XP", dmId);
        verify(activityLogService).log(8L, PcActivityType.XP_AWARD, "Awarded 300 XP", dmId);
    }

    // --- awardXpToMany (the reward bar's ticked characters) ---

    @Test
    void awardXpToMany_awardsOnlyTheTickedParticipants() {
        SessionParticipant pc7 = participant(5L, 7L);
        SessionParticipant pc8 = participant(9L, 8L);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(participantRepository.findBySessionIdOrderByOrderIndexAsc(1L))
                .thenReturn(List.of(pc7, participant(6L, null), pc8));
        when(pcRepository.findAllById(List.of(8L))).thenReturn(List.of(pc(8L, "Mira", 0)));

        XpAwardResult result = sessionService.awardXpToMany(1L, 300, List.of(9L), dmId);

        assertThat(result.awarded()).extracting("name").containsExactly("Mira");
        verify(pcRepository, times(1)).save(any(PC.class));
        verify(activityLogService).log(8L, PcActivityType.XP_AWARD, "Awarded 300 XP", dmId);
        verifyNoMoreInteractions(activityLogService);
    }

    @Test
    void awardXpToMany_throws400_andAwardsNothing_forAnEmptyStaleOrNpcTick() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(participantRepository.findBySessionIdOrderByOrderIndexAsc(1L))
                .thenReturn(List.of(participant(5L, 7L), participant(6L, null)));

        // nobody ticked; a participant who has left; an enemy row
        for (List<Long> ticked : List.of(List.<Long>of(), List.of(5L, 99L), List.of(5L, 6L))) {
            assertThatThrownBy(() -> sessionService.awardXpToMany(1L, 300, ticked, dmId))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        }
        verify(pcRepository, never()).save(any(PC.class));
        verify(sessionRepository, never()).save(any());
    }

    // --- startEncounter ---

    @Test
    void start_activates_andPointsTurnAtTopOfSortedOrder() {
        session.setStatus(SessionStatus.LOBBY);
        // Insertion order: mid initiative, high initiative, none entered yet.
        SessionParticipant mid = combatant(1L, (short) 12, 0);
        SessionParticipant high = combatant(2L, (short) 20, 1);
        SessionParticipant unrolled = combatant(3L, null, 2);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(participantRepository.findBySessionIdOrderByOrderIndexAsc(1L))
                .thenReturn(new ArrayList<>(List.of(mid, high, unrolled)));

        SessionStateView state = sessionService.startEncounter(1L, dmId);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.ACTIVE);
        assertThat(session.getCurrentTurnParticipantId()).isEqualTo(2L); // highest initiative
        assertThat(state.activeParticipantId()).isEqualTo(2L);
        assertThat(high.getOrderIndex()).isEqualTo((short) 0);
        assertThat(mid.getOrderIndex()).isEqualTo((short) 1);
        assertThat(unrolled.getOrderIndex()).isEqualTo((short) 2); // no initiative → bottom
        assertThat(session.getVersion()).isEqualTo(1L);
    }

    @Test
    void start_throws409_whenNotInLobby() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session)); // ACTIVE

        assertThatThrownBy(() -> sessionService.startEncounter(1L, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
    }

    @Test
    void start_throws409_whenNoCombatants() {
        session.setStatus(SessionStatus.LOBBY);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(participantRepository.findBySessionIdOrderByOrderIndexAsc(1L))
                .thenReturn(new ArrayList<>());

        assertThatThrownBy(() -> sessionService.startEncounter(1L, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
        assertThat(session.getCurrentTurnParticipantId()).isNull();
    }

    @Test
    void start_throws403_whenNotDm() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.startEncounter(1L, strangerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
    }

    // --- turn order: tie-breaking ---

    @Test
    void order_breaksInitiativeTies_byInsertionOrder() {
        session.setStatus(SessionStatus.LOBBY);
        // All initiative 15. There is no DEX tie-break layer any more (enemies
        // carry AC, which plays no part in initiative) — full ties resolve by
        // id asc (BIGSERIAL = insertion order), PCs and NPCs alike.
        SessionParticipant slowPc = pcCombatant(1L, 7L, (short) 15, 0);
        SessionParticipant avgPc = pcCombatant(2L, 8L, (short) 15, 1);
        SessionParticipant npcLate = combatant(4L, (short) 15, 3);
        SessionParticipant npcEarly = combatant(3L, (short) 15, 2);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(participantRepository.findBySessionIdOrderByOrderIndexAsc(1L))
                .thenReturn(new ArrayList<>(List.of(slowPc, avgPc, npcLate, npcEarly)));
        when(pcRepository.findAllById(any()))
                .thenReturn(List.of(pcWithDex(7L, "Slow", 9), pcWithDex(8L, "Average", 10)));

        sessionService.startEncounter(1L, dmId);

        assertThat(slowPc.getOrderIndex()).isEqualTo((short) 0);   // id 1 first
        assertThat(avgPc.getOrderIndex()).isEqualTo((short) 1);
        assertThat(npcEarly.getOrderIndex()).isEqualTo((short) 2); // id asc continues
        assertThat(npcLate.getOrderIndex()).isEqualTo((short) 3);
        assertThat(session.getCurrentTurnParticipantId()).isEqualTo(1L);
    }

    // --- endEncounter ---

    @Test
    void endEncounter_returnsToLobby_clearsPointerAndInitiative() {
        List<SessionParticipant> list = combatants(2);
        arm(session, 1L, list);
        session.setRound((short) 3);

        SessionStateView state = sessionService.endEncounter(1L, dmId);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.LOBBY);
        assertThat(session.getCurrentTurnParticipantId()).isNull();
        assertThat(session.getRound()).isEqualTo((short) 1);
        assertThat(state.activeParticipantId()).isNull();
        assertThat(list).allSatisfy(p -> {
            assertThat(p.getInitiative()).isNull();
            assertThat(p.getInitRolled()).isFalse();
        });
        verify(participantRepository).saveAll(list);
    }

    @Test
    void endEncounter_throws409_whenNotActive() {
        session.setStatus(SessionStatus.LOBBY);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.endEncounter(1L, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
    }

    @Test
    void endEncounter_throws403_whenNotDm() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.endEncounter(1L, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
    }

    @Test
    void restartAfterEndEncounter_opensOnRoundOne() {
        // Encounter 1 ended on round 3; encounter 2 must not inherit the count.
        List<SessionParticipant> list = combatants(2);
        arm(session, 1L, list);
        session.setRound((short) 3);
        sessionService.endEncounter(1L, dmId);

        list.get(0).setInitiative((short) 10);
        list.get(0).setInitRolled(true);
        sessionService.startEncounter(1L, dmId);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.ACTIVE);
        assertThat(session.getRound()).isEqualTo((short) 1);
        assertThat(session.getCurrentTurnParticipantId()).isEqualTo(1L);
    }

    // --- advanceTurn ---

    @Test
    void advance_movesPointerToNextInOrder() {
        arm(session, 1L, combatants(3));

        SessionStateView state = sessionService.advanceTurn(1L, 1L, dmId);

        assertThat(session.getCurrentTurnParticipantId()).isEqualTo(2L);
        assertThat(state.activeParticipantId()).isEqualTo(2L);
        assertThat(session.getRound()).isEqualTo((short) 1); // no wrap, no increment
        assertThat(session.getVersion()).isEqualTo(1L);
    }

    @Test
    void advance_wrapsToTop_andIncrementsRound() {
        arm(session, 3L, combatants(3));

        sessionService.advanceTurn(1L, 3L, dmId);

        assertThat(session.getCurrentTurnParticipantId()).isEqualTo(1L);
        assertThat(session.getRound()).isEqualTo((short) 2);
    }

    @Test
    void advance_singleCombatant_wrapsOntoSelf_andIncrementsRound() {
        arm(session, 1L, combatants(1));

        sessionService.advanceTurn(1L, 1L, dmId);

        assertThat(session.getCurrentTurnParticipantId()).isEqualTo(1L);
        assertThat(session.getRound()).isEqualTo((short) 2);
    }

    @Test
    void advance_throws409_onStaleExpectedId_andDoesNotMove() {
        session.setCurrentTurnParticipantId(2L);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        // A racing DM-Next / player-End-Turn: the loser still expects combatant 1.
        assertThatThrownBy(() -> sessionService.advanceTurn(1L, 1L, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
        assertThat(session.getCurrentTurnParticipantId()).isEqualTo(2L);
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void advance_throws400_whenExpectedIdMissing() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.advanceTurn(1L, null, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
    }

    @Test
    void advance_throws409_whenNotActive() {
        session.setStatus(SessionStatus.LOBBY);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.advanceTurn(1L, 1L, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
    }

    @Test
    void advance_throws403_whenCallerNeitherDmNorOwnerOfActiveCombatant() {
        // Active combatant is an unowned NPC — only the DM may advance its turn.
        arm(session, 1L, combatants(2));

        assertThatThrownBy(() -> sessionService.advanceTurn(1L, 1L, strangerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
    }

    // --- late entry: initiative assigned mid-encounter ---

    @Test
    void lateInitiative_sortingAbovePointer_leavesPointerAlone_andActsNextRound() {
        // Round 1, B's turn (pointer = 2). C has no initiative yet (bottom).
        SessionParticipant a = combatant(1L, (short) 20, 0);
        SessionParticipant b = combatant(2L, (short) 15, 1);
        SessionParticipant c = combatant(3L, null, 2);
        List<SessionParticipant> list = new ArrayList<>(List.of(a, b, c));
        arm(session, 2L, list);
        when(participantRepository.findById(3L)).thenReturn(Optional.of(c));

        // DM enters 25 for C — sorts above the pointer (already-passed territory).
        sessionService.setInitiative(1L, 3L, (short) 25, dmId);

        assertThat(c.getOrderIndex()).isEqualTo((short) 0);
        assertThat(session.getCurrentTurnParticipantId()).isEqualTo(2L); // pointer never jumps back

        // B was last in the new order, so ending B's turn wraps: C acts, next round.
        sessionService.advanceTurn(1L, 2L, dmId);

        assertThat(session.getCurrentTurnParticipantId()).isEqualTo(3L);
        assertThat(session.getRound()).isEqualTo((short) 2);
    }

    // --- removeParticipant: pointer safety ---

    @Test
    void remove_activeCombatant_advancesPointerBeforeDelete() {
        SessionParticipant first = combatant(1L, (short) 20, 0);
        SessionParticipant second = combatant(2L, (short) 10, 1);
        arm(session, 1L, new ArrayList<>(List.of(first, second)));
        when(participantRepository.findById(1L)).thenReturn(Optional.of(first));

        sessionService.removeParticipant(1L, 1L, dmId);

        assertThat(session.getCurrentTurnParticipantId()).isEqualTo(2L);
        verify(participantRepository).delete(first);
    }

    @Test
    void remove_lastRemainingCombatant_clearsPointer() {
        SessionParticipant only = combatant(1L, (short) 20, 0);
        arm(session, 1L, new ArrayList<>(List.of(only)));
        when(participantRepository.findById(1L)).thenReturn(Optional.of(only));

        sessionService.removeParticipant(1L, 1L, dmId);

        assertThat(session.getCurrentTurnParticipantId()).isNull();
        verify(participantRepository).delete(only);
    }

    @Test
    void remove_nonActiveCombatant_leavesPointerAlone() {
        SessionParticipant first = combatant(1L, (short) 20, 0);
        SessionParticipant second = combatant(2L, (short) 10, 1);
        arm(session, 1L, new ArrayList<>(List.of(first, second)));
        when(participantRepository.findById(2L)).thenReturn(Optional.of(second));

        sessionService.removeParticipant(1L, 2L, dmId);

        assertThat(session.getCurrentTurnParticipantId()).isEqualTo(1L);
        verify(participantRepository).delete(second);
    }

    // --- setInitiative: player self-entry rules ---

    @Test
    void player_setsOwnInitiative_inLobby() {
        session.setStatus(SessionStatus.LOBBY);
        SessionParticipant mine = combatant(1L, null, 0);
        mine.setOwnerUserId(playerId);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(participantRepository.findById(1L)).thenReturn(Optional.of(mine));
        when(participantRepository.findBySessionIdOrderByOrderIndexAsc(1L))
                .thenReturn(new ArrayList<>(List.of(mine)));

        sessionService.setInitiative(1L, 1L, (short) 17, playerId);

        assertThat(mine.getInitiative()).isEqualTo((short) 17);
        assertThat(mine.getInitRolled()).isTrue();
    }

    @Test
    void player_entersOwnInitiative_firstTime_whileActive() {
        // Late joiner: encounter running, their initiative still unset.
        SessionParticipant mine = combatant(2L, null, 1);
        mine.setOwnerUserId(playerId);
        SessionParticipant other = combatant(1L, (short) 20, 0);
        arm(session, 1L, new ArrayList<>(List.of(other, mine)));
        when(participantRepository.findById(2L)).thenReturn(Optional.of(mine));

        sessionService.setInitiative(1L, 2L, (short) 12, playerId);

        assertThat(mine.getInitRolled()).isTrue();
        assertThat(session.getCurrentTurnParticipantId()).isEqualTo(1L); // pointer untouched
    }

    @Test
    void player_cannotAlterOwnInitiative_onceActive() {
        SessionParticipant mine = combatant(1L, (short) 15, 0);
        mine.setOwnerUserId(playerId);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session)); // ACTIVE
        when(participantRepository.findById(1L)).thenReturn(Optional.of(mine));

        assertThatThrownBy(() -> sessionService.setInitiative(1L, 1L, (short) 20, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
        assertThat(mine.getInitiative()).isEqualTo((short) 15);
    }

    @Test
    void player_cannotSetSomeoneElsesInitiative() {
        session.setStatus(SessionStatus.LOBBY);
        SessionParticipant theirs = combatant(1L, null, 0);
        theirs.setOwnerUserId(strangerId);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(participantRepository.findById(1L)).thenReturn(Optional.of(theirs));

        assertThatThrownBy(() -> sessionService.setInitiative(1L, 1L, (short) 20, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
    }

    @Test
    void dm_canAlterAnyInitiative_whileActive() {
        SessionParticipant locked = combatant(1L, (short) 15, 0);
        locked.setOwnerUserId(playerId);
        arm(session, 1L, new ArrayList<>(List.of(locked)));
        when(participantRepository.findById(1L)).thenReturn(Optional.of(locked));

        sessionService.setInitiative(1L, 1L, (short) 22, dmId);

        assertThat(locked.getInitiative()).isEqualTo((short) 22);
    }

    // --- advanceTurn: player End Turn ---

    @Test
    void player_endsOwnTurn_advances() {
        List<SessionParticipant> list = combatants(2);
        list.get(0).setOwnerUserId(playerId);
        arm(session, 1L, list);

        sessionService.advanceTurn(1L, 1L, playerId);

        assertThat(session.getCurrentTurnParticipantId()).isEqualTo(2L);
    }

    @Test
    void player_cannotEndSomeoneElsesTurn() {
        List<SessionParticipant> list = combatants(2);
        list.get(0).setOwnerUserId(strangerId);
        arm(session, 1L, list);

        assertThatThrownBy(() -> sessionService.advanceTurn(1L, 1L, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
        assertThat(session.getCurrentTurnParticipantId()).isEqualTo(1L);
        verify(sessionRepository, never()).save(any());
    }

    // --- addEnemy ---

    @Test
    void addEnemy_parksAtBottomUntilInitiativeEntered() {
        List<SessionParticipant> list = new ArrayList<>();
        SessionParticipant pc = combatant(1L, (short) 3, 0); // low initiative, but entered
        list.add(pc);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(participantRepository.findBySessionIdOrderByOrderIndexAsc(1L)).thenReturn(list);
        when(participantRepository.save(any(SessionParticipant.class))).thenAnswer(inv -> {
            SessionParticipant p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(99L);
                list.add(p);
            }
            return p;
        });

        sessionService.addEnemy(1L, "Goblin Boss", (short) 17, (short) 21, dmId);

        SessionParticipant enemy = list.stream()
                .filter(p -> Long.valueOf(99L).equals(p.getId())).findFirst().orElseThrow();
        assertThat(enemy.getPcId()).isNull();
        assertThat(enemy.getNpcArmorClass()).isEqualTo((short) 17);
        assertThat(enemy.getNpcHpMax()).isEqualTo((short) 21);
        assertThat(enemy.getNpcHpCurrent()).isEqualTo((short) 21);
        assertThat(enemy.getInitiative()).isNull();
        // No initiative yet → below even the lowest entered initiative.
        assertThat(enemy.getOrderIndex()).isEqualTo((short) 1);
        assertThat(pc.getOrderIndex()).isEqualTo((short) 0);
    }

    @Test
    void addEnemy_throws403_whenNotDm() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.addEnemy(1L, "Goblin", (short) 2, null, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
    }

    @Test
    void addEnemy_throws400_withoutName() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.addEnemy(1L, "  ", (short) 15, null, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
    }

    @Test
    void addEnemy_allowsUnknownArmorClass() {
        // AC is optional reference info — a null must not be rejected.
        List<SessionParticipant> list = new ArrayList<>();
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(participantRepository.findBySessionIdOrderByOrderIndexAsc(1L)).thenReturn(list);
        when(participantRepository.save(any(SessionParticipant.class))).thenAnswer(inv -> {
            SessionParticipant p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(99L);
                list.add(p);
            }
            return p;
        });

        sessionService.addEnemy(1L, "Mysterious Figure", null, null, dmId);

        assertThat(list).singleElement().satisfies(e -> {
            assertThat(e.getNpcArmorClass()).isNull();
            assertThat(e.getDisplayName()).isEqualTo("Mysterious Figure");
        });
    }

    // --- loadEncounter ---

    @Test
    void loadEncounter_appendsCreatures_expandsQuantity_numbersRows_noInitiative() {
        List<SessionParticipant> list = new ArrayList<>();
        list.add(combatant(1L, (short) 15, 0)); // an existing combatant, left untouched
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(encounterRepository.findById(2L)).thenReturn(Optional.of(encounter(2L, 1L, dmId)));
        when(encounterCreatureRepository.findByEncounterIdOrderByIdAsc(2L)).thenReturn(List.of(
                creature("Goblin", (short) 15, (short) 7, 4),
                creature("Goblin Boss", (short) 17, (short) 21, 1)));
        when(participantRepository.save(any(SessionParticipant.class))).thenAnswer(inv -> {
            SessionParticipant p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(100L + list.size());
                list.add(p);
            }
            return p;
        });
        when(participantRepository.findBySessionIdOrderByOrderIndexAsc(1L)).thenReturn(list);

        sessionService.loadEncounter(1L, 2L, dmId);

        assertThat(list).hasSize(6); // 1 existing + 4 goblins + 1 boss
        List<SessionParticipant> enemies = list.stream()
                .filter(p -> p.getId() != null && p.getId() >= 100L).toList();
        assertThat(enemies).hasSize(5); // 4 goblins + 1 boss appended
        assertThat(enemies).extracting(SessionParticipant::getDisplayName)
                .containsExactly("Goblin 1", "Goblin 2", "Goblin 3", "Goblin 4", "Goblin Boss");
        // Single-quantity creature is not suffixed; multi-quantity is numbered.
        assertThat(enemies).allSatisfy(e -> assertThat(e.getInitiative()).isNull());
        SessionParticipant boss = enemies.stream()
                .filter(e -> "Goblin Boss".equals(e.getDisplayName())).findFirst().orElseThrow();
        assertThat(boss.getNpcArmorClass()).isEqualTo((short) 17);
        assertThat(boss.getNpcHpMax()).isEqualTo((short) 21);
        assertThat(boss.getNpcHpCurrent()).isEqualTo((short) 21);
        verify(sessionRepository).save(session); // version bumped once
    }

    @Test
    void loadEncounter_throws403_whenNotDm() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.loadEncounter(1L, 2L, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
        verify(participantRepository, never()).save(any());
    }

    @Test
    void loadEncounter_throws403_forEncounterFromAnotherCampaign() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(encounterRepository.findById(2L)).thenReturn(Optional.of(encounter(2L, 99L, dmId)));

        assertThatThrownBy(() -> sessionService.loadEncounter(1L, 2L, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
        verify(participantRepository, never()).save(any());
    }

    @Test
    void loadEncounter_throws404_whenEncounterMissing() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(encounterRepository.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.loadEncounter(1L, 2L, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(404));
    }

    @Test
    void loadEncounter_throws409_whenSessionEnded() {
        session.setStatus(SessionStatus.ENDED);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.loadEncounter(1L, 2L, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
        verify(participantRepository, never()).save(any());
    }

    @Test
    void enemyPcInitiativeTie_resolvesByInsertionOrder_ignoringDexAndAc() {
        session.setStatus(SessionStatus.LOBBY);
        // Same initiative: neither the PC's DEX nor the enemy's AC plays any
        // part — the earlier row (lower id) simply goes first.
        SessionParticipant pc = pcCombatant(1L, 7L, (short) 15, 0);
        SessionParticipant enemy = combatant(2L, (short) 15, 1);
        enemy.setNpcArmorClass((short) 18);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(participantRepository.findBySessionIdOrderByOrderIndexAsc(1L))
                .thenReturn(new ArrayList<>(List.of(pc, enemy)));
        when(pcRepository.findAllById(any())).thenReturn(List.of(pcWithDex(7L, "Rogue", 12)));

        sessionService.startEncounter(1L, dmId);

        assertThat(pc.getOrderIndex()).isEqualTo((short) 0);
        assertThat(enemy.getOrderIndex()).isEqualTo((short) 1);
    }

    // --- visibility + sound settings ---

    @Test
    void setVisibility_togglesFlag_andBumpsVersion() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(participantRepository.findBySessionIdOrderByOrderIndexAsc(1L))
                .thenReturn(new ArrayList<>());

        SessionStateView state = sessionService.setVisibility(1L, false, null, dmId);

        assertThat(session.getEnemiesHidden()).isFalse();
        assertThat(state.enemiesHidden()).isFalse();
        assertThat(session.getVersion()).isEqualTo(1L);
    }

    @Test
    void setVisibility_throws403_whenNotDm() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.setVisibility(1L, false, null, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
    }

    @Test
    void setTurnSound_setsAndClears() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(participantRepository.findBySessionIdOrderByOrderIndexAsc(1L))
                .thenReturn(new ArrayList<>());

        assertThat(sessionService.setTurnSound(1L, "chime", dmId).turnSound()).isEqualTo("chime");
        assertThat(sessionService.setTurnSound(1L, null, dmId).turnSound()).isNull();
        assertThat(session.getVersion()).isEqualTo(2L);
    }

    // --- per-viewer snapshot: visibility, glow targets, on-deck ---

    @Test
    void playerView_omitsHiddenEnemies_entirely() {
        // enemies_hidden defaults TRUE. A(PC, mine) → enemy → B(PC, other).
        arm(session, 1L, hiddenEnemySetup());
        stubPcs();

        SessionStateView state = sessionService.getState(1L, playerId, null);

        assertThat(state.participants()).extracting("participantId").containsExactly(1L, 3L);
        assertThat(state.enemiesHidden()).isTrue();
    }

    @Test
    void playerView_onDeck_skipsHiddenEnemy_toNextVisible() {
        // Active = my PC (id 1); true next is the hidden enemy (id 2); the player's
        // yellow must land on B (id 3) while the DM's stays on the enemy.
        arm(session, 1L, hiddenEnemySetup());
        stubPcs();

        SessionStateView playerState = sessionService.getState(1L, playerId, null);
        SessionStateView dmState = sessionService.getState(1L, dmId, null);

        assertThat(playerState.activeParticipantId()).isEqualTo(1L);
        assertThat(playerState.onDeckParticipantId()).isEqualTo(3L);
        assertThat(dmState.activeParticipantId()).isEqualTo(1L);
        assertThat(dmState.onDeckParticipantId()).isEqualTo(2L);
        assertThat(dmState.participants()).hasSize(3);
    }

    @Test
    void playerView_noGlowTarget_whileHiddenEnemyActs() {
        // Turn sits on the hidden enemy: player gets no active (no green, no
        // sound), and yellow points at the next combatant they can see.
        arm(session, 2L, hiddenEnemySetup());
        stubPcs();

        SessionStateView playerState = sessionService.getState(1L, playerId, null);
        SessionStateView dmState = sessionService.getState(1L, dmId, null);

        assertThat(playerState.activeParticipantId()).isNull();
        assertThat(playerState.onDeckParticipantId()).isEqualTo(3L);
        assertThat(playerState.participants()).allSatisfy(p -> assertThat(p.currentTurn()).isFalse());
        assertThat(dmState.activeParticipantId()).isEqualTo(2L);
        assertThat(dmState.onDeckParticipantId()).isEqualTo(3L);
    }

    @Test
    void playerView_seesEnemies_whenVisibilityToggledOff() {
        session.setEnemiesHidden(false);
        arm(session, 2L, hiddenEnemySetup());
        stubPcs();

        SessionStateView state = sessionService.getState(1L, playerId, null);

        assertThat(state.participants()).hasSize(3);
        assertThat(state.activeParticipantId()).isEqualTo(2L);
        assertThat(state.onDeckParticipantId()).isEqualTo(3L);
    }

    @Test
    void onDeck_isNull_whenOnlyOneVisibleCombatant() {
        // My PC and one hidden enemy: green on me, never yellow on me too.
        SessionParticipant mine = pcCombatant(1L, 7L, (short) 20, 0);
        mine.setOwnerUserId(playerId);
        SessionParticipant enemy = combatant(2L, (short) 10, 1);
        arm(session, 1L, new ArrayList<>(List.of(mine, enemy)));
        stubPcs();

        SessionStateView state = sessionService.getState(1L, playerId, null);

        assertThat(state.activeParticipantId()).isEqualTo(1L);
        assertThat(state.onDeckParticipantId()).isNull();
    }

    @Test
    void getState_returns204Signal_whenVersionUnchanged() {
        SessionParticipant mine = pcCombatant(1L, 7L, (short) 20, 0);
        mine.setOwnerUserId(playerId);
        arm(session, 1L, new ArrayList<>(List.of(mine)));

        assertThat(sessionService.getState(1L, playerId, session.getVersion())).isNull();
        stubPcs();
        assertThat(sessionService.getState(1L, playerId, session.getVersion() - 1)).isNotNull();
        assertThat(sessionService.getState(1L, playerId, null)).isNotNull();
    }

    // --- myLevelGrant (caller-scoped, like myXp) ---

    /**
     * {@link #hiddenEnemySetup()}'s two PCs with explicit grant flags: PC 7 is
     * the player's own, PC 8 belongs to a teammate ({@code strangerId}).
     */
    private void stubPcsWithGrants(Boolean mineGranted, Boolean theirsGranted) {
        PC mine = pcWithDex(7L, "Mine", 10);
        mine.setPendingLevelGrant(mineGranted);
        PC theirs = pcWithDex(8L, "Theirs", 10);
        theirs.setPendingLevelGrant(theirsGranted);
        when(pcRepository.findAllById(any())).thenReturn(List.of(mine, theirs));
    }

    @Test
    void myLevelGrant_isTrue_forTheOwnerOfASeatedPcWithAPendingGrant() {
        arm(session, 1L, hiddenEnemySetup());
        stubPcsWithGrants(true, false);

        assertThat(sessionService.getState(1L, playerId, null).myLevelGrant()).isTrue();
    }

    @Test
    void myLevelGrant_isFalse_whenTheSeatedPcHasNoGrant() {
        arm(session, 1L, hiddenEnemySetup());
        stubPcsWithGrants(false, false);

        assertThat(sessionService.getState(1L, playerId, null).myLevelGrant()).isFalse();
    }

    @Test
    void myLevelGrant_isFalse_whenTheGrantColumnIsNull() {
        arm(session, 1L, hiddenEnemySetup());
        stubPcsWithGrants(null, null);

        assertThat(sessionService.getState(1L, playerId, null).myLevelGrant()).isFalse();
    }

    @Test
    void myLevelGrant_isNull_forTheDm() {
        arm(session, 1L, hiddenEnemySetup());
        stubPcsWithGrants(true, true);

        assertThat(sessionService.getState(1L, dmId, null).myLevelGrant()).isNull();
    }

    @Test
    void myLevelGrant_neverReflectsATeammatesGrant() {
        // The teammate (PC 8) is granted; the requester's own PC (7) is not.
        arm(session, 1L, hiddenEnemySetup());
        stubPcsWithGrants(false, true);

        assertThat(sessionService.getState(1L, playerId, null).myLevelGrant()).isFalse();
        // ...and the teammate sees only their own grant.
        assertThat(sessionService.getState(1L, strangerId, null).myLevelGrant()).isTrue();
    }

    // --- endSession (loot cleanup) ---

    @Test
    void endSession_discardsUnclaimedLoot() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(CombatSession.class))).thenAnswer(inv -> inv.getArgument(0));
        SessionLoot pool = new SessionLoot();
        pool.setId(20L);
        pool.setSessionId(1L);
        when(lootRepository.findBySessionId(1L)).thenReturn(Optional.of(pool));

        sessionService.endSession(1L, dmId);

        verify(lootItemRepository).deleteBySessionLootId(20L);
        verify(lootRepository).delete(pool);
    }

    // --- advanceTime / setTime (campaign clock) ---

    @Test
    void advanceTime_throws403_forNonDm() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.advanceTime(1L, strangerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
    }

    @Test
    void advanceTime_throws409_whenSessionEnded() {
        session.setStatus(SessionStatus.ENDED);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.advanceTime(1L, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
    }

    @Test
    void advanceTime_initializesANeverSetClock_withoutBumps() {
        campaign.setVariantRules("{\"survivalConditions\":true}");
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        SessionStateView state = sessionService.advanceTime(1L, dmId);

        // first click establishes day 1 morning — it doesn't pass time
        assertThat(campaign.getGameTime())
                .contains("\"day\":\"1\"").contains("\"timeOfDay\":\"morning\"");
        assertThat(state.gameTime()).containsEntry("timeOfDay", "morning");
        verify(pcRepository, never()).findByCampaignId(any());
        verify(campaignRepository).save(campaign);
        verify(sessionRepository).save(session); // version bumped
    }

    @Test
    void advanceTime_intoNoon_bumpsFatigueOnEveryMemberPc() {
        campaign.setVariantRules("{\"survivalConditions\":true}");
        // deliberately the PRE-v2 numeric shape — normalize converts dawn → morning
        campaign.setGameTime("{\"year\":1,\"month\":1,\"day\":1,\"timeOfDay\":\"dawn\"}");
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        PC tracked = pc(7L, "Gorath", 0);
        tracked.setSurvival("{\"hunger\":1,\"thirst\":1,\"fatigue\":1}");
        PC fresh = pc(8L, "Pip", 0); // never tracked → stages default to Ok (2)
        when(pcRepository.findByCampaignId(1L)).thenReturn(List.of(fresh, tracked));
        when(pcRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(tracked));
        when(pcRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(fresh));

        sessionService.advanceTime(1L, dmId);

        assertThat(campaign.getGameTime()).contains("\"timeOfDay\":\"noon\"");
        assertThat(tracked.getSurvival()).contains("\"fatigue\":2").contains("\"hunger\":1");
        assertThat(fresh.getSurvival()).contains("\"fatigue\":3").contains("\"hunger\":2");
        verify(pcRepository).save(tracked);
        verify(pcRepository).save(fresh);
    }

    @Test
    void advanceTime_intoNight_seedsSupplies_andRaisesHungerThirstFatigue() {
        campaign.setVariantRules("{\"survivalConditions\":true}");
        campaign.setGameTime("{\"year\":\"1\",\"month\":\"1\",\"day\":\"1\",\"timeOfDay\":\"noon\"}");
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        PC member = pc(7L, "Gorath", 0);
        member.setSurvival("{\"hunger\":2,\"thirst\":2,\"fatigue\":2}"); // unseeded → gets supplies
        when(pcRepository.findByCampaignId(1L)).thenReturn(List.of(member));
        when(pcRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(member));

        sessionService.advanceTime(1L, dmId);

        assertThat(campaign.getGameTime()).contains("\"timeOfDay\":\"night\"");
        // Night is a dawn/night boundary: all three stages climb. Supplies are
        // no longer eaten automatically — relief is the explicit consume flow.
        assertThat(member.getSurvival())
                .contains("\"hunger\":3").contains("\"thirst\":3").contains("\"fatigue\":3")
                .contains("\"seeded\":true");
        // Starting kit seeded (1 box, 1 skin, 5 rations, 5 water) and untouched.
        assertThat(member.getInventory())
                .contains("\"catalogKey\":\"rations\"").contains("\"catalogKey\":\"waterskin\"")
                .contains("\"catalogKey\":\"ration-box\"").contains("\"catalogKey\":\"water\"")
                .contains("\"qty\":5");
    }

    @Test
    void advanceTime_appliesBothDailyBumps_acrossAFullDay() {
        // Dawn AND night each give +1 hunger +1 thirst — a full day of advances
        // (noon → night → next morning) applies both boundaries.
        campaign.setVariantRules("{\"survivalConditions\":true}");
        campaign.setGameTime("{\"year\":\"1\",\"month\":\"1\",\"day\":\"1\",\"timeOfDay\":\"noon\"}");
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        PC member = pc(7L, "Gorath", 0);
        member.setSurvival("{\"hunger\":0,\"thirst\":0,\"fatigue\":0,\"seeded\":true}");
        member.setInventory("[]");
        when(pcRepository.findByCampaignId(1L)).thenReturn(List.of(member));
        when(pcRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(member));

        sessionService.advanceTime(1L, dmId); // noon → night (+1 H/T, +1 F)
        sessionService.advanceTime(1L, dmId); // night → morning (+1 H/T)

        assertThat(member.getSurvival())
                .contains("\"hunger\":2").contains("\"thirst\":2").contains("\"fatigue\":1");
        assertThat(member.getInventory()).doesNotContain("rations"); // seeded flag blocks a refill
    }

    @Test
    void advanceTime_pastNight_wrapsToMorning_stepsTheNumericDay_noBumpsWhenVariantOff() {
        campaign.setGameTime(
                "{\"year\":\"1492 DR\",\"month\":\"Hammer\",\"day\":\"3rd\",\"timeOfDay\":\"night\"}");
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        sessionService.advanceTime(1L, dmId);

        // a countable day steps forward on the rollover; month/year are free
        // text only the DM changes; bumps skipped without the variant
        assertThat(campaign.getGameTime())
                .contains("\"year\":\"1492 DR\"").contains("\"month\":\"Hammer\"")
                .contains("\"day\":\"4th\"").contains("\"timeOfDay\":\"morning\"");
        verify(pcRepository, never()).findByCampaignId(any());
    }

    @Test
    void advanceTime_pastNight_leavesAFreeTextDayAlone() {
        campaign.setGameTime(
                "{\"year\":\"1\",\"month\":\"1\",\"day\":\"Midwinter\",\"timeOfDay\":\"night\"}");
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        sessionService.advanceTime(1L, dmId);

        assertThat(campaign.getGameTime())
                .contains("\"day\":\"Midwinter\"").contains("\"timeOfDay\":\"morning\"");
    }

    // --- consumeSurvival (player Eat/Drink/Sleep relief) ---

    private PC survivalPc(String survival, String inventory) {
        PC pc = pc(7L, "Gorath", 0);
        pc.setSurvival(survival);
        pc.setInventory(inventory);
        return pc;
    }

    private void seatConsumer(PC pc) {
        campaign.setVariantRules("{\"survivalConditions\":true}");
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        SessionParticipant seat = participant(50L, 7L);
        seat.setOwnerUserId(playerId);
        when(participantRepository.findBySessionIdAndPcId(1L, 7L)).thenReturn(Optional.of(seat));
        when(pcRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(pc));
    }

    @Test
    void consume_eat_decrementsARationCharge_andRelievesHunger() {
        PC pc = survivalPc("{\"hunger\":4,\"thirst\":2,\"fatigue\":1,\"seeded\":true}",
                "[{\"catalogKey\":\"rations\",\"name\":\"Rations (1 day)\",\"qty\":2}]");
        seatConsumer(pc);

        var result = sessionService.consumeSurvival(1L, 7L, "EAT", playerId);

        assertThat(result.survival()).containsEntry("hunger", 3).containsEntry("seeded", true);
        assertThat(result.inventory()).hasSize(1);
        assertThat(result.inventory().get(0).get("qty")).isEqualTo(1);
        assertThat(pc.getSurvival()).contains("\"hunger\":3");
        verify(pcRepository).save(pc);
        verify(sessionRepository).save(session); // version bumped for every viewer
    }

    @Test
    void consume_eat_keepsTheLine_atZeroCharges() {
        // Container model: an emptied charge line persists at qty 0 for refills.
        PC pc = survivalPc("{\"hunger\":1,\"thirst\":0,\"fatigue\":0}",
                "[{\"catalogKey\":\"rations\",\"qty\":1}]");
        seatConsumer(pc);

        var result = sessionService.consumeSurvival(1L, 7L, "EAT", playerId);

        assertThat(result.inventory()).hasSize(1);
        assertThat(result.inventory().get(0).get("qty")).isEqualTo(0);
    }

    @Test
    void consume_eat_withoutRations_stillRelievesHunger() {
        PC pc = survivalPc("{\"hunger\":4,\"thirst\":0,\"fatigue\":0}", "[]");
        seatConsumer(pc);

        var result = sessionService.consumeSurvival(1L, 7L, "EAT", playerId);

        assertThat(result.survival()).containsEntry("hunger", 3);
        assertThat(result.inventory()).isEmpty();
    }

    @Test
    void consume_drink_decrementsAWaterCharge_neverTheSkin_andSkipsDroppedLines() {
        PC pc = survivalPc("{\"hunger\":0,\"thirst\":5,\"fatigue\":0}",
                "[{\"catalogKey\":\"waterskin\",\"qty\":1},"
                        + "{\"catalogKey\":\"water\",\"qty\":2,\"status\":\"dropped\"},"
                        + "{\"catalogKey\":\"water\",\"qty\":3}]");
        seatConsumer(pc);

        var result = sessionService.consumeSurvival(1L, 7L, "DRINK", playerId);

        assertThat(result.survival()).containsEntry("thirst", 4);
        assertThat(result.inventory().get(0).get("qty")).isEqualTo(1); // skin untouched
        assertThat(result.inventory().get(1).get("qty")).isEqualTo(2); // dropped line untouched
        assertThat(result.inventory().get(2).get("qty")).isEqualTo(2);
    }

    @Test
    void consume_sleep_adjustsFatigueOnly_flooredAtZero() {
        PC pc = survivalPc("{\"hunger\":2,\"thirst\":2,\"fatigue\":2}", "[]");
        seatConsumer(pc);

        var result = sessionService.consumeSurvival(1L, 7L, "SLEEP_GOOD", playerId);

        assertThat(result.survival())
                .containsEntry("fatigue", 0)  // −3 floored
                .containsEntry("hunger", 2);
    }

    @Test
    void consume_throws409_whenTheCampaignHasNoSurvivalVariant() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session)); // no variant rules

        assertThatThrownBy(() -> sessionService.consumeSurvival(1L, 7L, "EAT", playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
    }

    @Test
    void consume_throws403_whenThePcIsNotSeated() {
        campaign.setVariantRules("{\"survivalConditions\":true}");
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(participantRepository.findBySessionIdAndPcId(1L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.consumeSurvival(1L, 7L, "EAT", playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
    }

    @Test
    void consume_throws403_forSomeoneElsesCharacter() {
        campaign.setVariantRules("{\"survivalConditions\":true}");
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        SessionParticipant seat = participant(50L, 7L);
        seat.setOwnerUserId(playerId);
        when(participantRepository.findBySessionIdAndPcId(1L, 7L)).thenReturn(Optional.of(seat));

        assertThatThrownBy(() -> sessionService.consumeSurvival(1L, 7L, "EAT", strangerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
    }

    @Test
    void setTime_writesFreeTextLabels_withoutBumps() {
        campaign.setVariantRules("{\"survivalConditions\":true}");
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        sessionService.setTime(1L, new com.moo.charactermanagerservice.dto.SetTimeRequest(
                "1492 DR", "Hammer", "3rd", "night", "Far", null), dmId);

        assertThat(campaign.getGameTime())
                .contains("\"year\":\"1492 DR\"").contains("\"month\":\"Hammer\"")
                .contains("\"day\":\"3rd\"").contains("\"timeOfDay\":\"night\"")
                .contains("\"weekday\":\"Far\"").contains("\"week\":1");
        verify(pcRepository, never()).findByCampaignId(any());
        verify(campaignRepository).save(campaign);
    }

    @Test
    void setTime_ticksTheWeek_whenAnEnteredWeekdayRepeats() {
        campaign.setGameTime("{\"year\":\"1\",\"month\":\"1\",\"day\":\"9th\",\"timeOfDay\":\"morning\","
                + "\"weekday\":\"Mol\",\"weekdaysSeen\":[\"Sul\",\"Mol\"],\"week\":1}");
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        // "sul" was seen before (case-insensitive) → a full week has passed
        sessionService.setTime(1L, new com.moo.charactermanagerservice.dto.SetTimeRequest(
                "1", "1", "10th", "morning", "sul", null), dmId);

        assertThat(campaign.getGameTime())
                .contains("\"week\":2")
                .contains("\"weekdaysSeen\":[\"sul\"]"); // history resets to the new week's first day
    }

    @Test
    void setTime_unchangedWeekday_neverDoubleCounts() {
        campaign.setGameTime("{\"year\":\"1\",\"month\":\"1\",\"day\":\"9th\",\"timeOfDay\":\"morning\","
                + "\"weekday\":\"Mol\",\"weekdaysSeen\":[\"Sul\",\"Mol\"],\"week\":1}");
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        // a date correction resubmits the same weekday — history must not move
        sessionService.setTime(1L, new com.moo.charactermanagerservice.dto.SetTimeRequest(
                "1", "1", "9th (corrected)", "morning", "Mol", null), dmId);

        assertThat(campaign.getGameTime())
                .contains("\"week\":1")
                .contains("\"weekdaysSeen\":[\"Sul\",\"Mol\"]");
    }

    @Test
    void setTime_newWeekday_isAppendedToTheHistory() {
        campaign.setGameTime("{\"year\":\"1\",\"month\":\"1\",\"day\":\"9th\",\"timeOfDay\":\"morning\","
                + "\"weekday\":\"Mol\",\"weekdaysSeen\":[\"Sul\",\"Mol\"],\"week\":1}");
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        sessionService.setTime(1L, new com.moo.charactermanagerservice.dto.SetTimeRequest(
                "1", "1", "10th", "morning", "Zol", null), dmId);

        assertThat(campaign.getGameTime())
                .contains("\"week\":1")
                .contains("\"weekdaysSeen\":[\"Sul\",\"Mol\",\"Zol\"]");
    }

    @Test
    void setTime_throws400_onAnInvalidSegmentOrOversizedLabel() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        for (com.moo.charactermanagerservice.dto.SetTimeRequest bad : List.of(
                new com.moo.charactermanagerservice.dto.SetTimeRequest("1", "1", "1", "dawn", null, null),
                new com.moo.charactermanagerservice.dto.SetTimeRequest("1", "1", "1", "midnight", null, null),
                new com.moo.charactermanagerservice.dto.SetTimeRequest("x".repeat(41), "1", "1", "morning", null, null))) {
            assertThatThrownBy(() -> sessionService.setTime(1L, bad, dmId))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        }
        verify(campaignRepository, never()).save(any());
    }

    // --- defined week (campaign weekDays) ---

    @Test
    void advanceTime_withDefinedWeek_autoAdvancesTheWeekdayOnTheRollover() {
        campaign.setGameTime("{\"year\":\"1\",\"month\":\"1\",\"day\":\"3rd\",\"timeOfDay\":\"night\","
                + "\"weekday\":\"Sul\",\"weekdaysSeen\":[],\"week\":1}");
        when(campaignService.parseWeekDays(campaign)).thenReturn(List.of("Sul", "Mol", "Zol"));
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        sessionService.advanceTime(1L, dmId); // night → next day's morning

        assertThat(campaign.getGameTime())
                .contains("\"timeOfDay\":\"morning\"")
                .contains("\"weekday\":\"Mol\"")
                .contains("\"week\":1");
    }

    @Test
    void advanceTime_withDefinedWeek_wrapPastTheLastDay_incrementsTheWeek() {
        campaign.setGameTime("{\"year\":\"1\",\"month\":\"1\",\"day\":\"3rd\",\"timeOfDay\":\"night\","
                + "\"weekday\":\"Zol\",\"weekdaysSeen\":[],\"week\":4}");
        when(campaignService.parseWeekDays(campaign)).thenReturn(List.of("Sul", "Mol", "Zol"));
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        sessionService.advanceTime(1L, dmId);

        assertThat(campaign.getGameTime())
                .contains("\"weekday\":\"Sul\"")
                .contains("\"week\":5");
    }

    @Test
    void setTime_withDefinedWeek_throws400_onAWeekdayOutsideTheDefinition() {
        when(campaignService.parseWeekDays(campaign)).thenReturn(List.of("Sul", "Mol", "Zol"));
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.setTime(1L,
                new com.moo.charactermanagerservice.dto.SetTimeRequest(
                        "1", "1", "1", "morning", "Far", null), dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        verify(campaignRepository, never()).save(any());
    }

    @Test
    void setTime_withDefinedWeek_canonicalizesTheWeekday_withoutRepetitionTicksOrSeenWrites() {
        // "mol" repeats a previously seen weekday — the fallback would tick the
        // week and reset the history; a defined week must do neither.
        campaign.setGameTime("{\"year\":\"1\",\"month\":\"1\",\"day\":\"9th\",\"timeOfDay\":\"morning\","
                + "\"weekday\":\"Zol\",\"weekdaysSeen\":[\"Mol\",\"Zol\"],\"week\":1}");
        when(campaignService.parseWeekDays(campaign)).thenReturn(List.of("Sul", "Mol", "Zol"));
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        sessionService.setTime(1L, new com.moo.charactermanagerservice.dto.SetTimeRequest(
                "1", "1", "10th", "morning", "mol", null), dmId);

        assertThat(campaign.getGameTime())
                .contains("\"weekday\":\"Mol\"")                 // defined casing, not "mol"
                .contains("\"week\":1")                          // never inferred
                .contains("\"weekdaysSeen\":[\"Mol\",\"Zol\"]"); // frozen, not reset
    }

    @Test
    void setTime_withDefinedWeek_appliesAnExplicitWeek_flooredAtOne() {
        campaign.setGameTime("{\"year\":\"1\",\"month\":\"1\",\"day\":\"9th\",\"timeOfDay\":\"morning\","
                + "\"weekday\":\"Sul\",\"weekdaysSeen\":[],\"week\":2}");
        when(campaignService.parseWeekDays(campaign)).thenReturn(List.of("Sul", "Mol", "Zol"));
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        sessionService.setTime(1L, new com.moo.charactermanagerservice.dto.SetTimeRequest(
                "1", "1", "10th", "morning", "Sul", 7), dmId);
        assertThat(campaign.getGameTime()).contains("\"week\":7");

        sessionService.setTime(1L, new com.moo.charactermanagerservice.dto.SetTimeRequest(
                "1", "1", "10th", "morning", "Sul", -3), dmId);
        assertThat(campaign.getGameTime()).contains("\"week\":1"); // floored
    }

    @Test
    void setTime_withoutADefinition_ignoresTheWeekField() {
        campaign.setGameTime("{\"year\":\"1\",\"month\":\"1\",\"day\":\"9th\",\"timeOfDay\":\"morning\","
                + "\"weekday\":\"Mol\",\"weekdaysSeen\":[\"Mol\"],\"week\":1}");
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        // no defined week (parseWeekDays → null): the fallback is byte-identical
        // to today, so an explicit week in the body must change nothing.
        sessionService.setTime(1L, new com.moo.charactermanagerservice.dto.SetTimeRequest(
                "1", "1", "10th", "morning", "Zol", 9), dmId);

        assertThat(campaign.getGameTime())
                .contains("\"week\":1")
                .contains("\"weekdaysSeen\":[\"Mol\",\"Zol\"]");
    }

    // --- setLocation (party location) ---

    @Test
    void setLocation_writesNameAndType() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        sessionService.setLocation(1L,
                new com.moo.charactermanagerservice.dto.SetLocationRequest("Neverwinter", "Settlement"), dmId);

        assertThat(campaign.getLocation())
                .contains("\"name\":\"Neverwinter\"").contains("\"type\":\"Settlement\"");
        verify(campaignRepository).save(campaign);
    }

    @Test
    void setLocation_throws400_onAnUnknownType() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.setLocation(1L,
                new com.moo.charactermanagerservice.dto.SetLocationRequest("X", "Tavern"), dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        verify(campaignRepository, never()).save(any());
    }

    @Test
    void setLocation_throws403_forNonDm() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.setLocation(1L,
                new com.moo.charactermanagerservice.dto.SetLocationRequest("Neverwinter", "Settlement"), strangerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
    }

    // --- long rest phase ---

    /** A hero with every number a long rest touches set to something worth restoring. */
    private static PC tiredPc(Long id, String name) {
        PC pc = pc(id, name, 0);
        pc.setLevel((short) 5);
        pc.setHpMax((short) 30);
        pc.setHpCurrent((short) 12);
        pc.setHpTemp((short) 5);
        pc.setHitDiceUsed((short) 4);
        pc.setExhaustion((short) 2);
        pc.setSpellSlots("{\"1\":{\"max\":4,\"used\":3},\"2\":{\"max\":2,\"used\":2}}");
        return pc;
    }

    /**
     * A LOBBY session with an OPEN long rest phase and the standard party
     * (mine: seat 5 → pc 7, theirs: seat 6 → pc 8, plus an NPC). The phase
     * claim behaves like the real conditional update, and a saved outcome row
     * is served back to the snapshot.
     */
    private List<SessionParticipant> armLongRest(PC mine, PC theirs) {
        session.setStatus(SessionStatus.LOBBY);
        session.setLongRestOpen(true);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        List<SessionParticipant> party = restVoteParty();
        lenient().when(participantRepository.findBySessionIdOrderByOrderIndexAsc(1L)).thenReturn(party);
        lenient().when(participantRepository.findBySessionIdAndPcId(1L, 7L))
                .thenReturn(Optional.of(party.get(0)));
        lenient().when(participantRepository.findBySessionIdAndPcId(1L, 8L))
                .thenReturn(Optional.of(party.get(1)));
        lenient().when(pcRepository.findAllById(any())).thenReturn(List.of(mine, theirs));
        lenient().when(pcRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(mine));
        lenient().when(pcRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(theirs));
        lenient().when(sessionRepository.closeLongRest(1L))
                .thenAnswer(inv -> Boolean.TRUE.equals(session.getLongRestOpen()) ? 1 : 0);
        armLongRestResult();
        return party;
    }

    /** Serve a saved long rest outcome row back to the snapshot (id 77). */
    private void armLongRestResult() {
        lenient().when(longRestResultRepository.save(any(SessionLongRestResult.class))).thenAnswer(inv -> {
            SessionLongRestResult r = inv.getArgument(0);
            r.setId(77L);
            lenient().when(longRestResultRepository.findBySessionId(1L)).thenReturn(Optional.of(r));
            return r;
        });
    }

    @Test
    void openLongRest_opensThePhase_andRecoversNothingYet() {
        session.setStatus(SessionStatus.LOBBY);
        session.setShortRestOpen(true);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        List<SessionParticipant> party = restVoteParty();
        party.get(0).setLongRestReady(true); // stale flag from an earlier rest
        when(participantRepository.findBySessionIdOrderByOrderIndexAsc(1L)).thenReturn(party);
        PC mine = tiredPc(7L, "Gorath");
        PC theirs = tiredPc(8L, "Lyra");
        when(pcRepository.findAllById(any())).thenReturn(List.of(mine, theirs));

        SessionStateView state = sessionService.openLongRest(1L, null, dmId);

        assertThat(state.longRest()).isNotNull();
        assertThat(state.longRest().advanceClock()).isTrue();
        assertThat(state.longRest().undisturbed()).isTrue();
        assertThat(state.longRest().readyCount()).isZero();
        assertThat(state.longRest().ableCount()).isEqualTo(2);
        assertThat(state.longRest().readiness()).hasSize(2); // the NPC is not listed
        assertThat(state.longRest().readiness().get(0).displayName()).isEqualTo("Gorath");
        assertThat(state.longRest().readiness().get(0).ready()).isFalse(); // reset on open
        assertThat(state.shortRestOpen()).isFalse(); // the longer rest supersedes it
        assertThat(mine.getHpCurrent()).isEqualTo((short) 12); // nothing recovered yet
        assertThat(mine.getHitDiceUsed()).isEqualTo((short) 4);
        verify(pcRepository, never()).save(any());
        verify(activityLogService, never()).log(any(), any(), any(), any());
        assertThat(session.getVersion()).isEqualTo(1L);
    }

    @Test
    void openLongRest_canBeSeededAsDisturbed() {
        session.setStatus(SessionStatus.LOBBY);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(participantRepository.findBySessionIdOrderByOrderIndexAsc(1L)).thenReturn(restVoteParty());
        when(pcRepository.findAllById(any()))
                .thenReturn(List.of(tiredPc(7L, "Gorath"), tiredPc(8L, "Lyra")));

        SessionStateView state = sessionService.openLongRest(1L, false, dmId);

        assertThat(state.longRest().undisturbed()).isFalse();
    }

    @Test
    void openLongRest_throws403_forNonDm() {
        session.setStatus(SessionStatus.LOBBY);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.openLongRest(1L, true, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
        assertThat(Boolean.TRUE.equals(session.getLongRestOpen())).isFalse();
    }

    @Test
    void openLongRest_throws409_duringAnEncounter() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session)); // ACTIVE

        assertThatThrownBy(() -> sessionService.openLongRest(1L, true, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
    }

    @Test
    void openLongRest_throws409_whileAVoteIsBeingTallied() {
        session.setStatus(SessionStatus.LOBBY);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(restVoteRepository.findBySessionIdAndStatus(1L, RestVoteStatus.ACTIVE))
                .thenReturn(Optional.of(new SessionRestVote()));

        assertThatThrownBy(() -> sessionService.openLongRest(1L, true, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
    }

    @Test
    void openLongRest_throws409_whenAPhaseIsAlreadyOpen() {
        session.setStatus(SessionStatus.LOBBY);
        session.setLongRestOpen(true);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.openLongRest(1L, true, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
    }

    @Test
    void setLongRestReady_marksTheSeat_andWaitsForTheRestOfTheParty() {
        PC mine = tiredPc(7L, "Gorath");
        PC theirs = tiredPc(8L, "Lyra");
        List<SessionParticipant> party = armLongRest(mine, theirs);

        SessionStateView state = sessionService.setLongRestReady(1L, 7L, true, null, playerId);

        assertThat(party.get(0).getLongRestReady()).isTrue();
        assertThat(state.longRest()).isNotNull(); // still open — Lyra is getting ready
        assertThat(state.longRest().readyCount()).isEqualTo(1);
        assertThat(state.longRest().ableCount()).isEqualTo(2);
        assertThat(mine.getHpCurrent()).isEqualTo((short) 12);
        verify(sessionRepository, never()).closeLongRest(anyLong());
        verify(pcRepository, never()).save(any());
    }

    @Test
    void setLongRestReady_theLastReady_finishesTheRestForEveryone() {
        PC mine = tiredPc(7L, "Gorath");
        PC theirs = tiredPc(8L, "Lyra");
        List<SessionParticipant> party = armLongRest(mine, theirs);
        session.setLongRestAdvanceClock(false);
        party.get(1).setLongRestReady(true);

        SessionStateView state = sessionService.setLongRestReady(1L, 7L, true, null, playerId);

        assertThat(state.longRest()).isNull();
        assertThat(state.longRestResult().outcome()).isEqualTo("FINISHED");
        assertThat(mine.getHpCurrent()).isEqualTo((short) 30);
        assertThat(theirs.getHpCurrent()).isEqualTo((short) 30);
        verify(sessionRepository).closeLongRest(1L);
    }

    @Test
    void setLongRestReady_canBeTakenBack() {
        PC mine = tiredPc(7L, "Gorath");
        PC theirs = tiredPc(8L, "Lyra");
        List<SessionParticipant> party = armLongRest(mine, theirs);
        party.get(0).setLongRestReady(true);
        party.get(1).setLongRestReady(true); // would be settled — but this call un-readies

        SessionStateView state = sessionService.setLongRestReady(1L, 7L, false, null, playerId);

        assertThat(party.get(0).getLongRestReady()).isFalse();
        assertThat(state.longRest().readyCount()).isEqualTo(1);
        verify(sessionRepository, never()).closeLongRest(anyLong());
    }

    @Test
    void setLongRestReady_throws403_forSomeoneElsesCharacter() {
        armLongRest(tiredPc(7L, "Gorath"), tiredPc(8L, "Lyra"));

        assertThatThrownBy(() -> sessionService.setLongRestReady(1L, 8L, true, null, playerId)) // Lyra is the stranger's
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
    }

    @Test
    void setLongRestReady_throws403_whenThePcIsNotSeated() {
        armLongRest(tiredPc(7L, "Gorath"), tiredPc(8L, "Lyra"));
        when(participantRepository.findBySessionIdAndPcId(1L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.setLongRestReady(1L, 99L, true, null, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
    }

    @Test
    void setLongRestReady_throws409_whenNoLongRestIsOpen() {
        session.setStatus(SessionStatus.LOBBY);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.setLongRestReady(1L, 7L, true, null, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
    }

    @Test
    void finishLongRest_appliesFullRecovery_andLogsWhatChanged() {
        PC mine = tiredPc(7L, "Gorath");
        PC theirs = tiredPc(8L, "Lyra");
        armLongRest(mine, theirs);
        session.setLongRestAdvanceClock(false);

        SessionStateView state = sessionService.finishLongRest(1L, dmId); // nobody was ready — the DM decides

        assertThat(mine.getHpCurrent()).isEqualTo((short) 30);   // to maximum
        assertThat(mine.getHpTemp()).isEqualTo((short) 0);       // temporary HP fades
        assertThat(mine.getHitDiceUsed()).isEqualTo((short) 0);  // ALL hit dice, not half
        assertThat(mine.getExhaustion()).isEqualTo((short) 1);   // down one level
        assertThat(mine.getSpellSlots()).contains("\"used\":0").doesNotContain("\"used\":3")
                .doesNotContain("\"used\":2");
        assertThat(mine.getSurvival()).isNull(); // not a survival campaign — no fatigue to shed
        assertThat(state.longRest()).isNull();
        assertThat(state.longRestResult().outcome()).isEqualTo("FINISHED");
        assertThat(state.longRestResult().skipped()).isEmpty();
        assertThat(state.longRestResult().clockAdvanced()).isFalse();
        verify(pcRepository).save(mine);
        verify(pcRepository).save(theirs);
        verify(activityLogService).log(7L, PcActivityType.LONG_REST,
                "Completed a long rest: HP 12 → 30, 5 temporary HP faded, 4 Hit Dice back, "
                        + "spell slots restored, Exhaustion 2 → 1", dmId);
        assertThat(session.getVersion()).isEqualTo(1L);
    }

    @Test
    void finishLongRest_aRestedHero_getsThePlainLogLine_andExhaustionStaysAtZero() {
        PC fresh = pc(7L, "Gorath", 0);
        fresh.setHpMax((short) 30);
        fresh.setHpCurrent((short) 30);
        armLongRest(fresh, tiredPc(8L, "Lyra"));
        session.setLongRestAdvanceClock(false);

        sessionService.finishLongRest(1L, dmId);

        assertThat(fresh.getExhaustion()).isNull(); // never below zero, never invented
        verify(activityLogService).log(7L, PcActivityType.LONG_REST, "Completed a long rest", dmId);
    }

    @Test
    void finishLongRest_skipsAHeroAtZeroHitPoints_andNamesThem() {
        PC mine = tiredPc(7L, "Gorath");
        PC down = tiredPc(8L, "Lyra");
        down.setHpCurrent((short) 0);
        armLongRest(mine, down);
        session.setLongRestAdvanceClock(false);

        SessionStateView before = sessionService.getState(1L, dmId, null);
        assertThat(before.longRest().readiness().get(1).cannotRest()).isTrue();
        assertThat(before.longRest().ableCount()).isEqualTo(1); // Lyra is not waited on

        SessionStateView state = sessionService.finishLongRest(1L, dmId);

        assertThat(mine.getHpCurrent()).isEqualTo((short) 30);
        assertThat(down.getHpCurrent()).isEqualTo((short) 0);    // untouched
        assertThat(down.getHitDiceUsed()).isEqualTo((short) 4);
        assertThat(down.getExhaustion()).isEqualTo((short) 2);
        assertThat(down.getSpellSlots()).contains("\"used\":3");
        assertThat(state.longRestResult().skipped()).containsExactly("Lyra");
        verify(pcRepository, never()).save(down);
        verify(activityLogService, never()).log(eq(8L), any(), any(), any());
    }

    @Test
    void finishLongRest_survivalCampaign_shedsThreeFatigue_whenUndisturbed() {
        campaign.setVariantRules("{\"survivalConditions\":true}");
        PC mine = tiredPc(7L, "Gorath");
        mine.setSurvival("{\"hunger\":2,\"thirst\":2,\"fatigue\":5}");
        armLongRest(mine, tiredPc(8L, "Lyra"));
        session.setLongRestAdvanceClock(false);

        sessionService.finishLongRest(1L, dmId);

        assertThat(mine.getSurvival()).contains("\"fatigue\":2"); // 5 - 3
        ArgumentCaptor<String> line = ArgumentCaptor.forClass(String.class);
        verify(activityLogService).log(eq(7L), eq(PcActivityType.LONG_REST), line.capture(), eq(dmId));
        assertThat(line.getValue()).endsWith("Fatigue 5 → 2");
    }

    @Test
    void finishLongRest_survivalCampaign_shedsOneFatigue_whenDisturbed() {
        campaign.setVariantRules("{\"survivalConditions\":true}");
        PC mine = tiredPc(7L, "Gorath");
        mine.setSurvival("{\"hunger\":2,\"thirst\":2,\"fatigue\":5}");
        armLongRest(mine, tiredPc(8L, "Lyra"));
        session.setLongRestAdvanceClock(false);
        session.setLongRestUndisturbed(false);

        sessionService.finishLongRest(1L, dmId);

        assertThat(mine.getSurvival()).contains("\"fatigue\":4"); // 5 - 1
    }

    @Test
    void finishLongRest_clockOn_walksToTheNextMorning_applyingEachSegmentsSurvivalBumps() {
        campaign.setVariantRules("{\"survivalConditions\":true}");
        campaign.setGameTime("{\"year\":\"1\",\"month\":\"1\",\"day\":\"1\",\"timeOfDay\":\"noon\"}");
        PC mine = tiredPc(7L, "Gorath");
        mine.setSurvival("{\"hunger\":2,\"thirst\":2,\"fatigue\":4,\"seeded\":true}");
        PC theirs = tiredPc(8L, "Lyra");
        theirs.setSurvival("{\"hunger\":2,\"thirst\":2,\"fatigue\":4,\"seeded\":true}");
        armLongRest(mine, theirs);
        when(pcRepository.findByCampaignId(1L)).thenReturn(List.of(mine, theirs));

        SessionStateView state = sessionService.finishLongRest(1L, dmId);

        // noon → night (+1 hunger/thirst/fatigue) → next morning (+1 hunger/thirst)
        assertThat(campaign.getGameTime()).contains("\"timeOfDay\":\"morning\"").contains("\"day\":\"2\"");
        assertThat(mine.getSurvival()).contains("\"hunger\":4").contains("\"thirst\":4");
        assertThat(mine.getSurvival()).contains("\"fatigue\":2"); // 4 + 1 (night) - 3 (rest)
        assertThat(state.gameTime()).containsEntry("timeOfDay", "morning");
        assertThat(state.longRestResult().clockAdvanced()).isTrue();
        verify(campaignRepository).save(campaign);
    }

    @Test
    void finishLongRest_clockOn_fromMorning_passesAFullDay() {
        campaign.setGameTime("{\"year\":\"1\",\"month\":\"1\",\"day\":\"3\",\"timeOfDay\":\"morning\"}");
        armLongRest(tiredPc(7L, "Gorath"), tiredPc(8L, "Lyra"));

        sessionService.finishLongRest(1L, dmId);

        assertThat(campaign.getGameTime()).contains("\"timeOfDay\":\"morning\"").contains("\"day\":\"4\"");
        verify(pcRepository, never()).findByCampaignId(any()); // no survival variant — no bumps
    }

    @Test
    void finishLongRest_clockOn_establishesANeverSetClock_withoutBumps() {
        campaign.setVariantRules("{\"survivalConditions\":true}");
        PC mine = tiredPc(7L, "Gorath");
        mine.setSurvival("{\"hunger\":2,\"thirst\":2,\"fatigue\":5}");
        PC theirs = tiredPc(8L, "Lyra");
        armLongRest(mine, theirs);
        when(pcRepository.findByCampaignId(1L)).thenReturn(List.of(mine, theirs));

        sessionService.finishLongRest(1L, dmId);

        assertThat(campaign.getGameTime()).contains("\"day\":\"1\"").contains("\"timeOfDay\":\"morning\"");
        assertThat(mine.getSurvival()).contains("\"hunger\":2").contains("\"fatigue\":2"); // rest only
    }

    @Test
    void finishLongRest_clockOff_leavesTheClockAlone() {
        campaign.setGameTime("{\"year\":\"1\",\"month\":\"1\",\"day\":\"1\",\"timeOfDay\":\"noon\"}");
        armLongRest(tiredPc(7L, "Gorath"), tiredPc(8L, "Lyra"));
        session.setLongRestAdvanceClock(false);

        sessionService.finishLongRest(1L, dmId);

        assertThat(campaign.getGameTime()).contains("\"timeOfDay\":\"noon\"");
        // The campaign is still saved: it remembers when the rest finished.
        verify(campaignRepository).save(campaign);
    }

    @Test
    void finishLongRest_locksPcRowsInAscendingIdOrder_beforeClaimingThePhase() {
        armLongRest(tiredPc(7L, "Gorath"), tiredPc(8L, "Lyra"));
        session.setLongRestAdvanceClock(false);

        sessionService.finishLongRest(1L, dmId);

        org.mockito.InOrder order = inOrder(pcRepository, sessionRepository);
        order.verify(pcRepository).findByIdForUpdate(7L);
        order.verify(pcRepository).findByIdForUpdate(8L);
        order.verify(sessionRepository).closeLongRest(1L);
    }

    @Test
    void finishLongRest_changesNothing_whenAnotherRequestClosedThePhaseFirst() {
        PC mine = tiredPc(7L, "Gorath");
        armLongRest(mine, tiredPc(8L, "Lyra"));
        when(sessionRepository.closeLongRest(1L)).thenReturn(0); // lost the race

        assertThatThrownBy(() -> sessionService.finishLongRest(1L, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
        assertThat(mine.getHpCurrent()).isEqualTo((short) 12);
        verify(pcRepository, never()).save(any());
    }

    @Test
    void finishLongRest_throws403_forAPlayer() {
        armLongRest(tiredPc(7L, "Gorath"), tiredPc(8L, "Lyra"));

        assertThatThrownBy(() -> sessionService.finishLongRest(1L, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
    }

    @Test
    void finishLongRest_throws409_whenNoLongRestIsOpen() {
        session.setStatus(SessionStatus.LOBBY);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.finishLongRest(1L, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
    }

    @Test
    void interruptLongRest_closesThePhase_andNobodyRecovers() {
        PC mine = tiredPc(7L, "Gorath");
        List<SessionParticipant> party = armLongRest(mine, tiredPc(8L, "Lyra"));
        party.get(0).setLongRestReady(true);

        SessionStateView state = sessionService.interruptLongRest(1L, dmId);

        assertThat(state.longRest()).isNull();
        assertThat(state.longRestResult().outcome()).isEqualTo("INTERRUPTED");
        assertThat(mine.getHpCurrent()).isEqualTo((short) 12);
        assertThat(mine.getHitDiceUsed()).isEqualTo((short) 4);
        verify(pcRepository, never()).save(any());
        verify(activityLogService, never()).log(any(), any(), any(), any());
    }

    @Test
    void interruptLongRest_throws403_forAPlayer() {
        armLongRest(tiredPc(7L, "Gorath"), tiredPc(8L, "Lyra"));

        assertThatThrownBy(() -> sessionService.interruptLongRest(1L, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
        assertThat(session.getLongRestOpen()).isTrue();
    }

    @Test
    void setLongRestOptions_changesOnlyTheFieldsSent() {
        armLongRest(tiredPc(7L, "Gorath"), tiredPc(8L, "Lyra"));

        SessionStateView state = sessionService.setLongRestOptions(1L, false, null, dmId);

        assertThat(state.longRest().advanceClock()).isFalse();
        assertThat(state.longRest().undisturbed()).isTrue(); // null = unchanged
        assertThat(session.getVersion()).isEqualTo(1L);
    }

    @Test
    void setLongRestOptions_throws403_forAPlayer() {
        armLongRest(tiredPc(7L, "Gorath"), tiredPc(8L, "Lyra"));

        assertThatThrownBy(() -> sessionService.setLongRestOptions(1L, false, false, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
    }

    @Test
    void startEncounter_closesAnOpenLongRest_withoutRecovery() {
        session.setStatus(SessionStatus.LOBBY);
        session.setLongRestOpen(true);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(participantRepository.findBySessionIdOrderByOrderIndexAsc(1L)).thenReturn(combatants(2));

        SessionStateView state = sessionService.startEncounter(1L, dmId);

        assertThat(session.getLongRestOpen()).isFalse();
        assertThat(state.longRest()).isNull();
        ArgumentCaptor<SessionLongRestResult> saved = ArgumentCaptor.forClass(SessionLongRestResult.class);
        verify(longRestResultRepository).save(saved.capture());
        assertThat(saved.getValue().getOutcome()).isEqualTo("INTERRUPTED");
        verify(pcRepository, never()).save(any());
    }

    @Test
    void getState_lazilyFinishesARestEveryoneIsReadyFor_andBumpsVersion() {
        // Two Readys landed at the same instant (each saw the other as not
        // ready), or the last holdout left — no request settled it.
        PC mine = tiredPc(7L, "Gorath");
        PC theirs = tiredPc(8L, "Lyra");
        List<SessionParticipant> party = armLongRest(mine, theirs);
        session.setLongRestAdvanceClock(false);
        party.get(0).setLongRestReady(true);
        party.get(1).setLongRestReady(true);

        SessionStateView state = sessionService.getState(1L, playerId, 0L);

        assertThat(state).isNotNull(); // version moved, so no 204
        assertThat(state.longRest()).isNull();
        assertThat(state.longRestResult().outcome()).isEqualTo("FINISHED");
        assertThat(mine.getHpCurrent()).isEqualTo((short) 30);
        assertThat(session.getVersion()).isEqualTo(1L);
    }

    @Test
    void getState_leavesAnUnsettledRestOpen_andStays204() {
        List<SessionParticipant> party = armLongRest(tiredPc(7L, "Gorath"), tiredPc(8L, "Lyra"));
        party.get(0).setLongRestReady(true);

        assertThat(sessionService.getState(1L, playerId, 0L)).isNull();
        verify(sessionRepository, never()).closeLongRest(anyLong());
    }

    @Test
    void toggleShortRest_throws409_whileALongRestIsOpen() {
        session.setLongRestOpen(true);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.toggleShortRest(1L, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
    }

    @Test
    void endSession_closesAnOpenLongRest_andPurgesItsOutcome() {
        session.setLongRestOpen(true);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(CombatSession.class))).thenAnswer(inv -> inv.getArgument(0));
        when(participantRepository.findBySessionIdOrderByOrderIndexAsc(1L))
                .thenReturn(new ArrayList<>());

        SessionStateView state = sessionService.endSession(1L, dmId);

        assertThat(session.getLongRestOpen()).isFalse();
        assertThat(state.longRest()).isNull();
        verify(longRestResultRepository).deleteBySessionId(1L);
        verify(pcRepository, never()).save(any());
    }

    // --- long rest choices (prepared spells, rune weapon) ---

    private static final String CLERIC_SPELLS = "["
            + "{\"name\":\"Guidance\",\"lvl\":0,\"prepared\":true},"
            + "{\"name\":\"Bless\",\"lvl\":1,\"prepared\":true},"
            + "{\"name\":\"Command\",\"lvl\":1,\"prepared\":false},"
            + "{\"name\":\"Aid\",\"lvl\":2,\"prepared\":true}]";

    /** Mine (pc 7): a tired level 5 cleric with a spell to swap in. */
    private static PC tiredCleric() {
        PC pc = tiredPc(7L, "Gorath");
        pc.setClazz("Cleric");
        pc.setSpells(CLERIC_SPELLS);
        return pc;
    }

    /** Mine (pc 7): a tired level 5 Death Knight (CHA 16) carrying a Greatsword, a Handaxe and a Longbow. */
    private static PC tiredDeathKnight(String rune) {
        PC pc = tiredPc(7L, "Gorath");
        pc.setClazz("Paladin");
        pc.setSubclass("Oath of the Ebon Blade");
        pc.setSpecialization(rune);
        pc.setAbilityCha((short) 16);
        pc.setInventory("["
                + "{\"name\":\"Greatsword\",\"category\":\"weapon\",\"qty\":1,\"properties\":[\"heavy\"]},"
                + "{\"name\":\"Handaxe\",\"category\":\"weapon\",\"qty\":1,\"properties\":[\"light\"]},"
                + "{\"name\":\"Longbow\",\"category\":\"weapon\",\"qty\":1,\"properties\":[\"ammunition (150/600)\"]}]");
        return pc;
    }

    private static LongRestChoices prepared(String... names) {
        return new LongRestChoices(List.of(names), null);
    }

    private static LongRestChoices rune(String weapon) {
        return new LongRestChoices(null, weapon);
    }

    @Test
    void ready_withChoices_storesThemOnTheSeat_andChangesNothingYet() {
        PC mine = tiredCleric();
        List<SessionParticipant> party = armLongRest(mine, tiredPc(8L, "Lyra"));

        SessionStateView state = sessionService.setLongRestReady(1L, 7L, true, prepared("Command", "Aid"), playerId);

        assertThat(party.get(0).getLongRestChoices()).isEqualTo("{\"preparedSpells\":[\"Command\",\"Aid\"]}");
        assertThat(mine.getSpells()).isEqualTo(CLERIC_SPELLS); // applied only when the rest finishes
        assertThat(state.longRest().myChoices().preparedSpells()).containsExactly("Command", "Aid");
    }

    @Test
    void myChoices_areNeverShownToAnotherPlayer_orTheDm() {
        List<SessionParticipant> party = armLongRest(tiredCleric(), tiredPc(8L, "Lyra"));
        sessionService.setLongRestReady(1L, 7L, true, prepared("Command", "Aid"), playerId);

        assertThat(sessionService.getState(1L, strangerId, null).longRest().myChoices()).isNull();
        assertThat(sessionService.getState(1L, dmId, null).longRest().myChoices()).isNull();
        assertThat(sessionService.getState(1L, playerId, null).longRest().myChoices()).isNotNull();
        assertThat(party.get(1).getLongRestChoices()).isNull();
    }

    @Test
    void ready_rejectsInvalidChoices_with400_andDoesNotMarkTheSeatReady() {
        List<SessionParticipant> party = armLongRest(tiredCleric(), tiredPc(8L, "Lyra"));

        assertThatThrownBy(() -> sessionService.setLongRestReady(1L, 7L, true, prepared("Fireball"), playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400))
                .hasMessageContaining("Fireball is not on Gorath's spell list");
        assertThat(Boolean.TRUE.equals(party.get(0).getLongRestReady())).isFalse();
        assertThat(party.get(0).getLongRestChoices()).isNull();
        verify(participantRepository, never()).save(any(SessionParticipant.class));
    }

    @Test
    void ready_rejectsASpellChange_fromAClassThatCannotChangeAtALongRest() {
        PC bard = tiredCleric();
        bard.setClazz("Bard");
        armLongRest(bard, tiredPc(8L, "Lyra"));

        assertThatThrownBy(() -> sessionService.setLongRestReady(1L, 7L, true, prepared("Command", "Aid"), playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
    }

    @Test
    void ready_rejectsARuneWeapon_fromSomeoneWhoIsNotADeathKnight() {
        armLongRest(tiredCleric(), tiredPc(8L, "Lyra"));

        assertThatThrownBy(() -> sessionService.setLongRestReady(1L, 7L, true, rune("Greatsword"), playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
    }

    @Test
    void unready_discardsTheChoices() {
        List<SessionParticipant> party = armLongRest(tiredCleric(), tiredPc(8L, "Lyra"));
        sessionService.setLongRestReady(1L, 7L, true, prepared("Command", "Aid"), playerId);

        SessionStateView state = sessionService.setLongRestReady(1L, 7L, false, prepared("Command", "Aid"), playerId);

        assertThat(party.get(0).getLongRestChoices()).isNull(); // choices on an un-ready are ignored
        assertThat(state.longRest().myChoices()).isNull();
    }

    @Test
    void aLaterReady_withoutChoices_replacesTheEarlierOnes() {
        List<SessionParticipant> party = armLongRest(tiredCleric(), tiredPc(8L, "Lyra"));
        sessionService.setLongRestReady(1L, 7L, true, prepared("Command", "Aid"), playerId);

        sessionService.setLongRestReady(1L, 7L, true, null, playerId);

        assertThat(party.get(0).getLongRestChoices()).isNull();
    }

    @Test
    void openingAPhase_clearsChoicesLeftFromAnEarlierRest() {
        session.setStatus(SessionStatus.LOBBY);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        List<SessionParticipant> party = restVoteParty();
        party.get(0).setLongRestChoices("{\"preparedSpells\":[\"Command\"]}");
        when(participantRepository.findBySessionIdOrderByOrderIndexAsc(1L)).thenReturn(party);
        when(pcRepository.findAllById(any())).thenReturn(List.of(tiredCleric(), tiredPc(8L, "Lyra")));

        sessionService.openLongRest(1L, null, dmId);

        assertThat(party.get(0).getLongRestChoices()).isNull();
    }

    @Test
    void finish_appliesAReadyHerosPreparedSpells_withRecovery_andLogsBoth() {
        PC mine = tiredCleric();
        List<SessionParticipant> party = armLongRest(mine, tiredPc(8L, "Lyra"));
        session.setLongRestAdvanceClock(false);
        sessionService.setLongRestReady(1L, 7L, true, prepared("Command", "Aid"), playerId);

        sessionService.finishLongRest(1L, dmId);

        assertThat(mine.getSpells())
                .contains("{\"name\":\"Command\",\"lvl\":1,\"prepared\":true}")
                .contains("{\"name\":\"Bless\",\"lvl\":1,\"prepared\":false}")
                .contains("{\"name\":\"Aid\",\"lvl\":2,\"prepared\":true}");
        assertThat(mine.getHpCurrent()).isEqualTo((short) 30); // recovery landed in the same pass
        assertThat(party.get(0).getLongRestChoices()).isNull(); // spent
        ArgumentCaptor<String> line = ArgumentCaptor.forClass(String.class);
        verify(activityLogService).log(eq(7L), eq(PcActivityType.LONG_REST), line.capture(), eq(dmId));
        assertThat(line.getValue())
                .startsWith("Completed a long rest: HP 12 → 30")
                .endsWith("prepared Command; unprepared Bless");
    }

    @Test
    void finish_whileAHeroIsNotReady_recoversThem_butKeepsTheirSpellsAndRuneWeapon() {
        PC mine = tiredCleric();
        List<SessionParticipant> party = armLongRest(mine, tiredPc(8L, "Lyra"));
        session.setLongRestAdvanceClock(false);
        // Choices sitting on a seat that is NOT ready (as if un-ready had not cleared them).
        party.get(0).setLongRestChoices("{\"preparedSpells\":[\"Command\",\"Aid\"]}");

        sessionService.finishLongRest(1L, dmId);

        assertThat(mine.getSpells()).isEqualTo(CLERIC_SPELLS);
        assertThat(mine.getHpCurrent()).isEqualTo((short) 30);
        assertThat(mine.getHitDiceUsed()).isEqualTo((short) 0);
    }

    @Test
    void interrupt_leavesChoicesUnapplied() {
        PC mine = tiredCleric();
        armLongRest(mine, tiredPc(8L, "Lyra"));
        sessionService.setLongRestReady(1L, 7L, true, prepared("Command", "Aid"), playerId);

        sessionService.interruptLongRest(1L, dmId);

        assertThat(mine.getSpells()).isEqualTo(CLERIC_SPELLS);
        verify(pcRepository, never()).save(any());
    }

    @Test
    void finish_doesNotApplyChoices_forAHeroAtZeroHitPoints() {
        PC mine = tiredCleric();
        List<SessionParticipant> party = armLongRest(mine, tiredPc(8L, "Lyra"));
        session.setLongRestAdvanceClock(false);
        party.get(0).setLongRestReady(true);
        party.get(0).setLongRestChoices("{\"preparedSpells\":[\"Command\",\"Aid\"]}");
        mine.setHpCurrent((short) 0); // dropped after readying

        sessionService.finishLongRest(1L, dmId);

        assertThat(mine.getSpells()).isEqualTo(CLERIC_SPELLS);
        verify(pcRepository, never()).save(mine);
    }

    @Test
    void finish_dropsChoicesThatNoLongerFitTheSheet_withoutFailingTheRest() {
        PC mine = tiredCleric();
        PC theirs = tiredPc(8L, "Lyra");
        List<SessionParticipant> party = armLongRest(mine, theirs);
        session.setLongRestAdvanceClock(false);
        sessionService.setLongRestReady(1L, 7L, true, prepared("Command", "Aid"), playerId);
        // The DM removes Command from the sheet before the rest finishes.
        mine.setSpells(CLERIC_SPELLS.replace("{\"name\":\"Command\",\"lvl\":1,\"prepared\":false},", ""));
        String withoutCommand = mine.getSpells();

        sessionService.finishLongRest(1L, dmId);

        assertThat(mine.getSpells()).isEqualTo(withoutCommand);
        assertThat(mine.getHpCurrent()).isEqualTo((short) 30);   // still rested
        assertThat(theirs.getHpCurrent()).isEqualTo((short) 30); // and so did everyone else
        ArgumentCaptor<String> line = ArgumentCaptor.forClass(String.class);
        verify(activityLogService).log(eq(7L), eq(PcActivityType.LONG_REST), line.capture(), eq(dmId));
        assertThat(line.getValue()).endsWith("long rest choices no longer fit the sheet and were not applied");
    }

    @Test
    void finish_inscribesTheRune_andABloodRuneGivesTemporaryHitPoints() {
        PC knight = tiredDeathKnight("Blood"); // 5 temp HP from before, CHA +3, level 5
        armLongRest(knight, tiredPc(8L, "Lyra"));
        session.setLongRestAdvanceClock(false);
        sessionService.setLongRestReady(1L, 7L, true, rune("greatsword"), playerId);

        sessionService.finishLongRest(1L, dmId);

        assertThat(knight.getRuneWeapon()).isEqualTo("Greatsword");
        assertThat(knight.getHpTemp()).isEqualTo((short) 8); // old 5 faded, then +3 CHA + level 5
        ArgumentCaptor<String> line = ArgumentCaptor.forClass(String.class);
        verify(activityLogService).log(eq(7L), eq(PcActivityType.LONG_REST), line.capture(), eq(dmId));
        assertThat(line.getValue())
                .contains("5 temporary HP faded, 8 temporary HP from the Blood Rune")
                .endsWith("Blood Rune inscribed on Greatsword");
    }

    @Test
    void finish_movesTheRune_whenTheKnightChoosesAnotherWeapon() {
        PC knight = tiredDeathKnight("Frost");
        knight.setRuneWeapon("Greatsword");
        armLongRest(knight, tiredPc(8L, "Lyra"));
        session.setLongRestAdvanceClock(false);
        sessionService.setLongRestReady(1L, 7L, true, rune("Handaxe"), playerId);

        sessionService.finishLongRest(1L, dmId);

        assertThat(knight.getRuneWeapon()).isEqualTo("Handaxe");
        assertThat(knight.getHpTemp()).isEqualTo((short) 0); // Frost gives no temporary hit points
        ArgumentCaptor<String> line = ArgumentCaptor.forClass(String.class);
        verify(activityLogService).log(eq(7L), eq(PcActivityType.LONG_REST), line.capture(), eq(dmId));
        assertThat(line.getValue()).endsWith("Frost Rune moved from Greatsword to Handaxe");
    }

    @Test
    void finish_givesBloodRuneTempHp_toAKnightAlreadyInscribed_evenIfNotReady() {
        PC knight = tiredDeathKnight("Blood");
        knight.setRuneWeapon("Greatsword");
        armLongRest(knight, tiredPc(8L, "Lyra"));
        session.setLongRestAdvanceClock(false);

        sessionService.finishLongRest(1L, dmId); // nobody readied

        assertThat(knight.getRuneWeapon()).isEqualTo("Greatsword");
        assertThat(knight.getHpTemp()).isEqualTo((short) 8);
    }

    @Test
    void finish_givesNoBloodRuneTempHp_whenNoWeaponCarriesTheRune() {
        PC knight = tiredDeathKnight("Blood"); // never inscribed, and not ready
        armLongRest(knight, tiredPc(8L, "Lyra"));
        session.setLongRestAdvanceClock(false);

        sessionService.finishLongRest(1L, dmId);

        assertThat(knight.getRuneWeapon()).isNull();
        assertThat(knight.getHpTemp()).isEqualTo((short) 0);
    }

    @Test
    void ready_rejectsARangedRuneWeapon() {
        armLongRest(tiredDeathKnight("Blood"), tiredPc(8L, "Lyra"));

        assertThatThrownBy(() -> sessionService.setLongRestReady(1L, 7L, true, rune("Longbow"), playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400))
                .hasMessageContaining("Longbow is not a melee weapon Gorath has");
    }

    @Test
    void theLastReady_appliesItsOwnChoices_asItFinishesTheRest() {
        PC mine = tiredCleric();
        List<SessionParticipant> party = armLongRest(mine, tiredPc(8L, "Lyra"));
        session.setLongRestAdvanceClock(false);
        party.get(1).setLongRestReady(true);

        SessionStateView state = sessionService.setLongRestReady(1L, 7L, true, prepared("Command", "Aid"), playerId);

        assertThat(state.longRest()).isNull();
        assertThat(mine.getSpells()).contains("{\"name\":\"Command\",\"lvl\":1,\"prepared\":true}");
    }

    // --- one long rest every 24 hours (campaign clock) ---

    private static final String NOON = "{\"year\":\"1\",\"month\":\"1\",\"day\":\"1\",\"timeOfDay\":\"noon\"}";
    private static final String MORNING = "{\"year\":\"1\",\"month\":\"1\",\"day\":\"1\",\"timeOfDay\":\"morning\"}";

    @Test
    void finishLongRest_remembersWhenItFinished_afterTheClockMoved() {
        campaign.setGameTime(NOON);
        armLongRest(tiredPc(7L, "Gorath"), tiredPc(8L, "Lyra"));

        SessionStateView state = sessionService.finishLongRest(1L, dmId); // clock on: walks to the next morning

        assertThat(campaign.getLastLongRest()).isEqualTo("{\"timeOfDay\":\"morning\",\"segmentsSince\":0}");
        assertThat(state.longRestAvailable()).isFalse();
        assertThat(state.longRestWait().finishedTimeOfDay()).isEqualTo("morning");
        assertThat(state.longRestWait().availableTimeOfDay()).isEqualTo("night");
        assertThat(state.longRestWait().segmentsLeft()).isEqualTo(2);
        assertThat(state.longRestWait().reason())
                .isEqualTo("The party finished a long rest this morning. The next one can start tonight.");
        verify(campaignRepository).save(campaign);
    }

    @Test
    void finishLongRest_clockOff_remembersTheSegmentItFinishedIn() {
        campaign.setGameTime(NOON);
        armLongRest(tiredPc(7L, "Gorath"), tiredPc(8L, "Lyra"));
        session.setLongRestAdvanceClock(false);

        sessionService.finishLongRest(1L, dmId);

        assertThat(campaign.getLastLongRest()).isEqualTo("{\"timeOfDay\":\"noon\",\"segmentsSince\":0}");
    }

    @Test
    void finishLongRest_onAClockNobodySet_keepsNoRecord_soThereIsNoLimit() {
        armLongRest(tiredPc(7L, "Gorath"), tiredPc(8L, "Lyra")); // clock never set
        session.setLongRestAdvanceClock(false);

        SessionStateView state = sessionService.finishLongRest(1L, dmId);

        assertThat(campaign.getLastLongRest()).isNull();
        assertThat(state.longRestAvailable()).isTrue();
        assertThat(state.longRestWait()).isNull();
        verify(campaignRepository, never()).save(any());
    }

    @Test
    void finishLongRest_thatStartsTheClock_countsFromThatMorning() {
        armLongRest(tiredPc(7L, "Gorath"), tiredPc(8L, "Lyra")); // clock never set, advance on

        sessionService.finishLongRest(1L, dmId);

        assertThat(campaign.getLastLongRest()).isEqualTo("{\"timeOfDay\":\"morning\",\"segmentsSince\":0}");
    }

    @Test
    void interruptLongRest_doesNotCount() {
        campaign.setGameTime(NOON);
        armLongRest(tiredPc(7L, "Gorath"), tiredPc(8L, "Lyra"));

        SessionStateView state = sessionService.interruptLongRest(1L, dmId);

        assertThat(campaign.getLastLongRest()).isNull();
        assertThat(state.longRestAvailable()).isTrue();
    }

    @Test
    void openLongRest_throws409_withTheReason_insideTheWait() {
        campaign.setGameTime(MORNING);
        campaign.setLastLongRest("{\"timeOfDay\":\"morning\",\"segmentsSince\":0}");
        session.setStatus(SessionStatus.LOBBY);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.openLongRest(1L, null, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(409))
                .hasMessageContaining("The party finished a long rest this morning. The next one can start tonight.");
        assertThat(Boolean.TRUE.equals(session.getLongRestOpen())).isFalse();
    }

    @Test
    void openLongRest_isAllowedAgain_onceTwoSegmentsHavePassed() {
        campaign.setGameTime("{\"year\":\"1\",\"month\":\"1\",\"day\":\"1\",\"timeOfDay\":\"night\"}");
        campaign.setLastLongRest("{\"timeOfDay\":\"morning\",\"segmentsSince\":2}");
        session.setStatus(SessionStatus.LOBBY);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(participantRepository.findBySessionIdOrderByOrderIndexAsc(1L)).thenReturn(restVoteParty());
        when(pcRepository.findAllById(any())).thenReturn(List.of(tiredPc(7L, "Gorath"), tiredPc(8L, "Lyra")));

        SessionStateView state = sessionService.openLongRest(1L, null, dmId);

        assertThat(state.longRest()).isNotNull();
        assertThat(state.longRestAvailable()).isTrue();
    }

    @Test
    void initiateRestVote_long_throws409_insideTheWait_butAShortRestIsNeverLimited() {
        armRestVote();
        campaign.setGameTime(MORNING);
        campaign.setLastLongRest("{\"timeOfDay\":\"morning\",\"segmentsSince\":1}");

        assertThatThrownBy(() -> sessionService.initiateRestVote(1L, RestType.LONG, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(409))
                .hasMessageContaining("The next one can start");
        verify(restVoteRepository, never()).save(any());

        SessionStateView state = sessionService.initiateRestVote(1L, RestType.SHORT, playerId);
        assertThat(state.restVote().restType()).isEqualTo("SHORT");
    }

    @Test
    void advanceTime_countsTowardTheWait() {
        campaign.setGameTime(MORNING);
        campaign.setLastLongRest("{\"timeOfDay\":\"morning\",\"segmentsSince\":0}");
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        SessionStateView noon = sessionService.advanceTime(1L, dmId);
        assertThat(campaign.getLastLongRest()).isEqualTo("{\"timeOfDay\":\"morning\",\"segmentsSince\":1}");
        assertThat(noon.longRestAvailable()).isFalse();
        assertThat(noon.longRestWait().segmentsLeft()).isEqualTo(1);

        SessionStateView night = sessionService.advanceTime(1L, dmId);
        assertThat(night.longRestAvailable()).isTrue();
        assertThat(night.longRestWait()).isNull();
    }

    @Test
    void advanceTime_leavesACampaignWithNoRecordAlone() {
        campaign.setGameTime(MORNING);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        sessionService.advanceTime(1L, dmId);

        assertThat(campaign.getLastLongRest()).isNull();
    }

    @Test
    void setTime_movingTheTimeOfDayForward_countsThoseSegments() {
        campaign.setGameTime(MORNING);
        campaign.setLastLongRest("{\"timeOfDay\":\"morning\",\"segmentsSince\":0}");
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        SessionStateView state = sessionService.setTime(1L,
                new SetTimeRequest("1", "1", "1", "night", null, null), dmId); // morning → night: two segments

        assertThat(campaign.getLastLongRest()).isEqualTo("{\"timeOfDay\":\"morning\",\"segmentsSince\":2}");
        assertThat(state.longRestAvailable()).isTrue();
    }

    @Test
    void setTime_changingOnlyTheDate_leavesTheWaitWhereItWas() {
        // The DM relabels the new day right after a rest: that is not time passing.
        campaign.setGameTime(MORNING);
        campaign.setLastLongRest("{\"timeOfDay\":\"morning\",\"segmentsSince\":0}");
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        SessionStateView state = sessionService.setTime(1L,
                new SetTimeRequest("1492 DR", "Hammer", "4th", "morning", null, null), dmId);

        assertThat(campaign.getLastLongRest()).isEqualTo("{\"timeOfDay\":\"morning\",\"segmentsSince\":0}");
        assertThat(state.longRestAvailable()).isFalse();
    }

    // --- Death Knight must choose the rune's weapon to ready ---

    @Test
    void ready_throws400_forADeathKnightWithAMeleeWeapon_noRuneOnOne_andNoChoice() {
        List<SessionParticipant> party = armLongRest(tiredDeathKnight("Blood"), tiredPc(8L, "Lyra"));

        assertThatThrownBy(() -> sessionService.setLongRestReady(1L, 7L, true, null, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400))
                .hasMessageContaining("Choose the weapon that carries Gorath's rune before readying");
        assertThat(Boolean.TRUE.equals(party.get(0).getLongRestReady())).isFalse();
    }

    @Test
    void ready_isFine_forADeathKnightWhoseRuneIsAlreadyOnAWeapon_orWhoHasNoMeleeWeapon() {
        PC inscribed = tiredDeathKnight("Blood");
        inscribed.setRuneWeapon("Greatsword");
        List<SessionParticipant> party = armLongRest(inscribed, tiredPc(8L, "Lyra"));
        sessionService.setLongRestReady(1L, 7L, true, null, playerId);
        assertThat(party.get(0).getLongRestReady()).isTrue();

        PC unarmed = tiredDeathKnight("Blood");
        unarmed.setInventory("[]");
        List<SessionParticipant> second = armLongRest(unarmed, tiredPc(8L, "Lyra"));
        sessionService.setLongRestReady(1L, 7L, true, null, playerId);
        assertThat(second.get(0).getLongRestReady()).isTrue();
    }

    @Test
    void unready_isNeverBlockedByTheRuneRule() {
        List<SessionParticipant> party = armLongRest(tiredDeathKnight("Blood"), tiredPc(8L, "Lyra"));

        sessionService.setLongRestReady(1L, 7L, false, null, playerId);

        assertThat(Boolean.TRUE.equals(party.get(0).getLongRestReady())).isFalse();
    }

    @Test
    void readiness_carriesWhatTheClientNeedsToNameTheHerosClass() {
        armLongRest(tiredDeathKnight("Frost"), tiredPc(8L, "Lyra"));

        SessionStateView state = sessionService.getState(1L, dmId, null);

        LongRestView.ReadinessView knight = state.longRest().readiness().get(0);
        assertThat(knight.clazz()).isEqualTo("Paladin");
        assertThat(knight.subclass()).isEqualTo("Oath of the Ebon Blade");
        assertThat(knight.specialization()).isEqualTo("Frost");
        assertThat(knight.level()).isEqualTo((short) 5);
    }

    // --- class resources: long rest restores all, short rest restores what the rules give ---

    /** Mine (pc 7): a level 5 fighter with Second Wind and Action Surge spent. */
    private static PC spentFighter() {
        PC pc = tiredPc(7L, "Gorath");
        pc.setClazz("Fighter");
        pc.setSpellSlots(null);
        pc.setClassResources("{\"secondWind\":2,\"actionSurge\":1}"); // 1 of 3, 0 of 1 left
        return pc;
    }

    /** Wire a LOBBY session with the standard party seated, row-lockable, for a short rest. */
    private void armShortRest(PC mine, PC theirs) {
        session.setStatus(SessionStatus.LOBBY);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        lenient().when(participantRepository.findBySessionIdOrderByOrderIndexAsc(1L)).thenReturn(restVoteParty());
        lenient().when(pcRepository.findAllById(any())).thenReturn(List.of(mine, theirs));
        lenient().when(pcRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(mine));
        lenient().when(pcRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(theirs));
    }

    @Test
    void finishLongRest_restoresEveryClassResource_andLogsIt() {
        PC mine = spentFighter();
        armLongRest(mine, tiredPc(8L, "Lyra"));
        session.setLongRestAdvanceClock(false);

        sessionService.finishLongRest(1L, dmId);

        assertThat(mine.getClassResources()).isNull(); // nothing used
        ArgumentCaptor<String> line = ArgumentCaptor.forClass(String.class);
        verify(activityLogService).log(eq(7L), eq(PcActivityType.LONG_REST), line.capture(), eq(dmId));
        assertThat(line.getValue()).contains("Second Wind 1 → 3, Action Surge 0 → 1");
    }

    @Test
    void finishLongRest_leavesAHeroAtZeroHitPoints_withTheirResourcesSpent() {
        PC mine = spentFighter();
        mine.setHpCurrent((short) 0);
        armLongRest(mine, tiredPc(8L, "Lyra"));
        session.setLongRestAdvanceClock(false);

        sessionService.finishLongRest(1L, dmId);

        assertThat(mine.getClassResources()).isEqualTo("{\"secondWind\":2,\"actionSurge\":1}");
    }

    @Test
    void interruptLongRest_restoresNoClassResources() {
        PC mine = spentFighter();
        armLongRest(mine, tiredPc(8L, "Lyra"));

        sessionService.interruptLongRest(1L, dmId);

        assertThat(mine.getClassResources()).isEqualTo("{\"secondWind\":2,\"actionSurge\":1}");
    }

    @Test
    void openingAShortRest_givesBackWhatTheRulesSay_andLogsIt() {
        PC mine = spentFighter();
        PC theirs = tiredPc(8L, "Lyra");
        theirs.setClazz("Paladin");
        theirs.setClassResources("{\"layOnHands\":10,\"channelDivinity\":2}");
        armShortRest(mine, theirs);

        SessionStateView state = sessionService.toggleShortRest(1L, dmId);

        assertThat(state.shortRestOpen()).isTrue();
        assertThat(mine.getClassResources()).isEqualTo("{\"secondWind\":1}"); // one use back; Action Surge all back
        assertThat(theirs.getClassResources()).isEqualTo("{\"layOnHands\":10,\"channelDivinity\":1}");
        assertThat(mine.getHpCurrent()).isEqualTo((short) 12); // a short rest heals nothing by itself
        verify(activityLogService).log(7L, PcActivityType.SHORT_REST,
                "Short rest: Second Wind 1 → 2, Action Surge 0 → 1", dmId);
        verify(activityLogService).log(8L, PcActivityType.SHORT_REST,
                "Short rest: Channel Divinity 0 → 1", dmId);
        verify(pcRepository).save(mine);
        verify(pcRepository).save(theirs);
    }

    @Test
    void openingAShortRest_locksPcRowsInAscendingIdOrder() {
        armShortRest(spentFighter(), tiredPc(8L, "Lyra"));

        sessionService.toggleShortRest(1L, dmId);

        org.mockito.InOrder order = inOrder(pcRepository);
        order.verify(pcRepository).findByIdForUpdate(7L);
        order.verify(pcRepository).findByIdForUpdate(8L);
    }

    @Test
    void closingAShortRest_givesNothingBack() {
        PC mine = spentFighter();
        armShortRest(mine, tiredPc(8L, "Lyra"));
        session.setShortRestOpen(true);

        SessionStateView state = sessionService.toggleShortRest(1L, dmId);

        assertThat(state.shortRestOpen()).isFalse();
        assertThat(mine.getClassResources()).isEqualTo("{\"secondWind\":2,\"actionSurge\":1}");
        verify(pcRepository, never()).findByIdForUpdate(any());
        verify(activityLogService, never()).log(any(), any(), any(), any());
    }

    @Test
    void openingAShortRest_writesNothing_forAHeroWithNothingToRegain() {
        PC rested = tiredPc(7L, "Gorath");
        rested.setClazz("Fighter");
        rested.setSpellSlots(null);
        armShortRest(rested, tiredPc(8L, "Lyra"));

        sessionService.toggleShortRest(1L, dmId);

        verify(pcRepository, never()).save(any());
        verify(activityLogService, never()).log(any(), any(), any(), any());
    }

    @Test
    void openingAShortRest_restoresAWarlocksPactMagicSlots_butNotAWizardsSlots() {
        PC warlock = tiredPc(7L, "Gorath");
        warlock.setClazz("Warlock"); // tiredPc has slots used
        PC wizard = tiredPc(8L, "Lyra");
        wizard.setClazz("Wizard");
        String wizardSlots = wizard.getSpellSlots();
        armShortRest(warlock, wizard);

        sessionService.toggleShortRest(1L, dmId);

        assertThat(warlock.getSpellSlots()).contains("\"used\":0").doesNotContain("\"used\":3");
        assertThat(wizard.getSpellSlots()).isEqualTo(wizardSlots);
        verify(activityLogService).log(7L, PcActivityType.SHORT_REST, "Short rest: Pact Magic slots restored", dmId);
        verify(pcRepository, never()).save(wizard);
    }

    @Test
    void openingAShortRest_skipsAHeroAtZeroHitPoints() {
        PC mine = spentFighter();
        mine.setHpCurrent((short) 0);
        armShortRest(mine, tiredPc(8L, "Lyra"));

        sessionService.toggleShortRest(1L, dmId);

        assertThat(mine.getClassResources()).isEqualTo("{\"secondWind\":2,\"actionSurge\":1}");
        verify(pcRepository, never()).save(mine);
    }

    @Test
    void aPassedShortRestVote_givesTheSameRecovery() {
        PC mine = spentFighter();
        armShortRest(mine, tiredPc(8L, "Lyra"));
        List<SessionRestVoteBallot> ballots = new ArrayList<>(List.of(
                ballot(5L, "Gorath", true), ballot(6L, "Lyra", null)));
        activeVote(ballots, Instant.now().plus(Duration.ofSeconds(60)));

        SessionStateView state = sessionService.castRestVote(1L, true, strangerId);

        assertThat(state.shortRestOpen()).isTrue();
        assertThat(mine.getClassResources()).isEqualTo("{\"secondWind\":1}");
        verify(activityLogService).log(7L, PcActivityType.SHORT_REST,
                "Short rest: Second Wind 1 → 2, Action Surge 0 → 1", dmId);
    }

    @Test
    void aFailedShortRestVote_givesNothingBack() {
        PC mine = spentFighter();
        armShortRest(mine, tiredPc(8L, "Lyra"));
        List<SessionRestVoteBallot> ballots = new ArrayList<>(List.of(
                ballot(5L, "Gorath", true), ballot(6L, "Lyra", null)));
        activeVote(ballots, Instant.now().plus(Duration.ofSeconds(60)));

        sessionService.castRestVote(1L, false, strangerId);

        assertThat(mine.getClassResources()).isEqualTo("{\"secondWind\":2,\"actionSurge\":1}");
    }

    @Test
    void aPassedLongRestVote_doesNotApplyShortRestRecovery() {
        PC mine = spentFighter();
        armShortRest(mine, tiredPc(8L, "Lyra"));
        List<SessionRestVoteBallot> ballots = new ArrayList<>(List.of(
                ballot(5L, "Gorath", true), ballot(6L, "Lyra", null)));
        activeVote(ballots, Instant.now().plus(Duration.ofSeconds(60))).setRestType(RestType.LONG);

        sessionService.castRestVote(1L, true, strangerId);

        assertThat(mine.getClassResources()).isEqualTo("{\"secondWind\":2,\"actionSurge\":1}");
    }

    // --- rest vote: rest type ---

    @Test
    void initiateRestVote_carriesTheRestType_andDefaultsToShort() {
        armRestVote();

        SessionStateView longVote = sessionService.initiateRestVote(1L, RestType.LONG, playerId);
        assertThat(longVote.restVote().restType()).isEqualTo("LONG");

        SessionStateView shortVote = sessionService.initiateRestVote(1L, null, playerId); // an older client
        assertThat(shortVote.restVote().restType()).isEqualTo("SHORT");
    }

    @Test
    void restTypeParse_isLenient_andRejectsUnknownValues() {
        assertThat(RestType.parse(null)).isEqualTo(RestType.SHORT);
        assertThat(RestType.parse(" ")).isEqualTo(RestType.SHORT);
        assertThat(RestType.parse("long")).isEqualTo(RestType.LONG);
        assertThatThrownBy(() -> RestType.parse("NAP"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
    }

    @Test
    void initiateRestVote_long_soloParty_opensTheLongRestPhase_notTheShortRestWindow() {
        armRestVote();
        SessionParticipant mine = participant(5L, 7L);
        mine.setOwnerUserId(playerId);
        when(participantRepository.findBySessionIdOrderByOrderIndexAsc(1L))
                .thenReturn(new ArrayList<>(List.of(mine)));

        SessionStateView state = sessionService.initiateRestVote(1L, RestType.LONG, playerId);

        assertThat(state.restVote().status()).isEqualTo("PASSED");
        assertThat(state.shortRestOpen()).isFalse();
        assertThat(state.longRest()).isNotNull();
        assertThat(state.longRest().readyCount()).isZero(); // voting yes is not being ready
        verify(pcRepository, never()).save(any()); // nothing recovered by the vote itself
    }

    @Test
    void castRestVote_lastYes_onALongVote_opensTheLongRestPhase() {
        session.setStatus(SessionStatus.LOBBY);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(participantRepository.findBySessionIdOrderByOrderIndexAsc(1L)).thenReturn(restVoteParty());
        when(pcRepository.findAllById(any()))
                .thenReturn(List.of(pc(7L, "Gorath", 0), pc(8L, "Lyra", 0)));
        List<SessionRestVoteBallot> ballots = new ArrayList<>(List.of(
                ballot(5L, "Gorath", true), ballot(6L, "Lyra", null)));
        SessionRestVote vote = activeVote(ballots, Instant.now().plus(Duration.ofSeconds(60)));
        vote.setRestType(RestType.LONG);

        SessionStateView state = sessionService.castRestVote(1L, true, strangerId);

        assertThat(vote.getStatus()).isEqualTo(RestVoteStatus.PASSED);
        assertThat(state.restVote().restType()).isEqualTo("LONG");
        assertThat(state.longRest()).isNotNull();
        assertThat(state.longRest().readiness()).hasSize(2);
        assertThat(state.shortRestOpen()).isFalse();
    }

    @Test
    void castRestVote_no_onALongVote_opensNothing() {
        session.setStatus(SessionStatus.LOBBY);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(participantRepository.findBySessionIdOrderByOrderIndexAsc(1L)).thenReturn(restVoteParty());
        when(pcRepository.findAllById(any()))
                .thenReturn(List.of(pc(7L, "Gorath", 0), pc(8L, "Lyra", 0)));
        List<SessionRestVoteBallot> ballots = new ArrayList<>(List.of(
                ballot(5L, "Gorath", true), ballot(6L, "Lyra", null)));
        SessionRestVote vote = activeVote(ballots, Instant.now().plus(Duration.ofSeconds(60)));
        vote.setRestType(RestType.LONG);

        SessionStateView state = sessionService.castRestVote(1L, false, strangerId);

        assertThat(vote.getStatus()).isEqualTo(RestVoteStatus.FAILED);
        assertThat(state.longRest()).isNull();
    }

    @Test
    void overrideRestVote_yes_onALongVote_opensTheLongRestPhase() {
        session.setStatus(SessionStatus.LOBBY);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(participantRepository.findBySessionIdOrderByOrderIndexAsc(1L)).thenReturn(restVoteParty());
        when(pcRepository.findAllById(any()))
                .thenReturn(List.of(pc(7L, "Gorath", 0), pc(8L, "Lyra", 0)));
        SessionRestVote vote = activeVote(new ArrayList<>(List.of(
                ballot(5L, "Gorath", true), ballot(6L, "Lyra", null))),
                Instant.now().plus(Duration.ofSeconds(60)));
        vote.setRestType(RestType.LONG);

        SessionStateView state = sessionService.overrideRestVote(1L, true, dmId);

        assertThat(vote.getStatus()).isEqualTo(RestVoteStatus.PASSED);
        assertThat(state.longRest()).isNotNull();
        assertThat(state.shortRestOpen()).isFalse();
    }

    @Test
    void initiateRestVote_throws409_whileALongRestIsOpen() {
        session.setStatus(SessionStatus.LOBBY);
        session.setLongRestOpen(true);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.initiateRestVote(1L, RestType.SHORT, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
        verify(restVoteRepository, never()).save(any());
    }

    // --- short rest window + hit-die spend ---

    @Test
    void toggleShortRest_opensThenCloses_andBumpsVersion() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(participantRepository.findBySessionIdOrderByOrderIndexAsc(1L))
                .thenReturn(new ArrayList<>());

        assertThat(sessionService.toggleShortRest(1L, dmId).shortRestOpen()).isTrue();
        assertThat(sessionService.toggleShortRest(1L, dmId).shortRestOpen()).isFalse();
        assertThat(session.getVersion()).isEqualTo(2L);
    }

    @Test
    void toggleShortRest_throws403_forNonDm() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.toggleShortRest(1L, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
    }

    @Test
    void startEncounter_closesTheShortRestWindow() {
        session.setStatus(SessionStatus.LOBBY);
        session.setShortRestOpen(true);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(participantRepository.findBySessionIdOrderByOrderIndexAsc(1L))
                .thenReturn(combatants(2));

        sessionService.startEncounter(1L, dmId);

        assertThat(session.getShortRestOpen()).isFalse();
    }

    /** Seat pc 7 (owned by the player) with the short-rest window open. */
    private PC seatForShortRest(int level, String clazz, int con, int hpCurrent, Integer hpMax,
                                int hitDiceUsed) {
        session.setShortRestOpen(true);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        SessionParticipant seat = participant(50L, 7L);
        seat.setOwnerUserId(playerId);
        when(participantRepository.findBySessionIdAndPcId(1L, 7L)).thenReturn(Optional.of(seat));

        PC pc = pc(7L, "Gorath", 0);
        pc.setLevel((short) level);
        pc.setClazz(clazz);
        pc.setAbilityCon((short) con);
        pc.setHpCurrent((short) hpCurrent);
        if (hpMax != null) pc.setHpMax(hpMax.shortValue());
        pc.setHitDiceUsed((short) hitDiceUsed);
        when(pcRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(pc));
        return pc;
    }

    /** Pin the service RNG so the "1d[X]" roll is deterministic. */
    private void pinRoll(int zeroBasedValue) {
        sessionService.rng = new java.util.random.RandomGenerator() {
            @Override public long nextLong() { return 0L; }
            @Override public int nextInt(int bound) { return zeroBasedValue; }
        };
    }

    @Test
    void spendHitDie_rollsHealsIncrements_andLogsEverywhere() {
        PC pc = seatForShortRest(5, "Fighter", 14, 10, 44, 1); // d10, CON +2
        pinRoll(6); // roll = 7

        HitDieSpendResult result = sessionService.spendHitDie(1L, 7L, playerId);

        assertThat(result.roll()).isEqualTo(7);
        assertThat(result.healed()).isEqualTo(9); // 7 + 2
        assertThat(result.hpCurrent()).isEqualTo((short) 19);
        assertThat(result.hitDiceUsed()).isEqualTo((short) 2);
        assertThat(pc.getHpCurrent()).isEqualTo((short) 19);
        verify(pcRepository).save(pc);
        verify(activityLogService).log(eq(7L), eq(PcActivityType.SHORT_REST), contains("healed 9 HP"),
                eq(playerId));
        verify(sessionRollRepository).save(any(SessionRoll.class)); // DM sees the roll
        verify(sessionRepository).save(session); // version bumped
    }

    @Test
    void spendHitDie_capsAtMaxHp_butStillSpendsTheDie() {
        PC pc = seatForShortRest(5, "Fighter", 14, 42, 44, 0);
        pinRoll(9); // roll = 10, +2 CON = 12 — but only 2 HP of headroom

        HitDieSpendResult result = sessionService.spendHitDie(1L, 7L, playerId);

        assertThat(result.healed()).isEqualTo(2);
        assertThat(result.hpCurrent()).isEqualTo((short) 44);
        assertThat(pc.getHitDiceUsed()).isEqualTo((short) 1);
    }

    @Test
    void spendHitDie_floorsHealingAtOne_withANegativeConMod() {
        seatForShortRest(3, "Wizard", 6, 5, 20, 0); // d6, CON -2
        pinRoll(0); // roll = 1, 1 - 2 = -1 → floored to 1

        HitDieSpendResult result = sessionService.spendHitDie(1L, 7L, playerId);

        assertThat(result.roll()).isEqualTo(1);
        assertThat(result.healed()).isEqualTo(1);
        assertThat(result.hpCurrent()).isEqualTo((short) 6);
    }

    @Test
    void spendHitDie_throws409_whenNoShortRestIsOpen() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session)); // window closed

        assertThatThrownBy(() -> sessionService.spendHitDie(1L, 7L, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
        verify(pcRepository, never()).save(any());
    }

    @Test
    void spendHitDie_throws409_whenAllHitDiceAreSpent() {
        seatForShortRest(3, "Fighter", 14, 10, 30, 3); // used == level

        assertThatThrownBy(() -> sessionService.spendHitDie(1L, 7L, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
        verify(pcRepository, never()).save(any());
    }

    @Test
    void spendHitDie_throws403_whenNotTheOwner() {
        // The ownership check fires before the row lock, so no PC stub here.
        session.setShortRestOpen(true);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        SessionParticipant seat = participant(50L, 7L);
        seat.setOwnerUserId(playerId);
        when(participantRepository.findBySessionIdAndPcId(1L, 7L)).thenReturn(Optional.of(seat));

        assertThatThrownBy(() -> sessionService.spendHitDie(1L, 7L, strangerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
        verify(pcRepository, never()).save(any());
    }

    // --- inspiration meter (in-session) ---

    @Test
    void setInspiration_delegatesToTheSharedMeterLogic_andBumpsVersion() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        SessionParticipant seat = participant(5L, 7L);
        when(participantRepository.findById(5L)).thenReturn(Optional.of(seat));
        PC pc = pc(7L, "Gorath", 0);
        when(pcService.findPCById(7L)).thenReturn(pc);
        when(participantRepository.findBySessionIdOrderByOrderIndexAsc(1L))
                .thenReturn(new ArrayList<>(List.of(seat)));
        when(pcRepository.findAllById(any())).thenReturn(List.of(pc));

        sessionService.setInspiration(1L, 5L, 3, dmId);

        verify(pcService).applyInspirationValue(pc, 3, dmId);
        verify(pcRepository).save(pc);
        assertThat(session.getVersion()).isEqualTo(1L);
    }

    /** Lowering the meter travels the same path — the DM can take pips back mid-session. */
    @Test
    void setInspiration_passesALowerValueThrough() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        SessionParticipant seat = participant(5L, 7L);
        when(participantRepository.findById(5L)).thenReturn(Optional.of(seat));
        PC pc = pc(7L, "Gorath", 0);
        when(pcService.findPCById(7L)).thenReturn(pc);
        when(participantRepository.findBySessionIdOrderByOrderIndexAsc(1L))
                .thenReturn(new ArrayList<>(List.of(seat)));
        when(pcRepository.findAllById(any())).thenReturn(List.of(pc));

        sessionService.setInspiration(1L, 5L, 0, dmId);

        verify(pcService).applyInspirationValue(pc, 0, dmId);
    }

    @Test
    void setInspiration_throws400_forNonPcCombatant() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(participantRepository.findById(5L)).thenReturn(Optional.of(participant(5L, null)));

        assertThatThrownBy(() -> sessionService.setInspiration(1L, 5L, 2, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        verify(pcRepository, never()).save(any());
    }

    @Test
    void setInspiration_throws403_whenNotDm() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.setInspiration(1L, 5L, 2, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
    }

    // --- enemy AC never reaches a player ---

    @Test
    void playerView_neverCarriesEnemyAc_inAnyVisibilityMode() {
        session.setEnemiesHidden(false); // enemies fully visible
        arm(session, 1L, hiddenEnemySetup()); // enemy row has AC 15
        stubPcs();

        SessionStateView playerState = sessionService.getState(1L, playerId, null);
        SessionStateView dmState = sessionService.getState(1L, dmId, null);

        assertThat(playerState.participants())
                .filteredOn(ParticipantView::npc)
                .allSatisfy(p -> assertThat(p.ac()).isNull());
        assertThat(dmState.participants())
                .filteredOn(ParticipantView::npc)
                .allSatisfy(p -> assertThat(p.ac()).isEqualTo((short) 15));
    }

    @Test
    void playerView_hideHealthMode_stripsHpAndAcTogether() {
        session.setEnemiesHidden(false);
        session.setEnemyHpHidden(true);
        List<SessionParticipant> setup = hiddenEnemySetup();
        setup.get(1).setNpcHpMax((short) 30);
        setup.get(1).setNpcHpCurrent((short) 22);
        arm(session, 1L, setup);
        stubPcs();

        SessionStateView playerState = sessionService.getState(1L, playerId, null);

        assertThat(playerState.participants())
                .filteredOn(ParticipantView::npc)
                .allSatisfy(p -> {
                    assertThat(p.ac()).isNull();
                    assertThat(p.hpCurrent()).isNull();
                    assertThat(p.hpMax()).isNull();
                });
    }

    // --- castSpell ---

    private PC casterPc(String spells, String spellSlots, String inventory) {
        PC pc = pc(7L, "Elaria", 0);
        pc.setSpells(spells);
        pc.setSpellSlots(spellSlots);
        pc.setInventory(inventory);
        return pc;
    }

    /** Seat pc 7 owned by the player and stub the row lock — no variant gate on casting. */
    private void seatCaster(PC pc) {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        SessionParticipant seat = participant(50L, 7L);
        seat.setOwnerUserId(playerId);
        when(participantRepository.findBySessionIdAndPcId(1L, 7L)).thenReturn(Optional.of(seat));
        when(pcRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(pc));
    }

    @Test
    void cast_spendsASlot_andBumpsVersion() {
        PC pc = casterPc("[{\"name\":\"Cure Wounds\",\"lvl\":1}]",
                "{\"1\":{\"max\":4,\"used\":1}}", "[]");
        seatCaster(pc);

        var result = sessionService.castSpell(1L, 7L, "Cure Wounds", 1, playerId);

        assertThat(((Map<?, ?>) result.spellSlots().get("1")).get("used")).isEqualTo(2);
        assertThat(result.warning()).isNull();
        assertThat(pc.getSpellSlots()).contains("\"used\":2");
        verify(pcRepository).save(pc);
        verify(sessionRepository).save(session); // version bumped so the DM sees the spend
    }

    @Test
    void cast_cantrip_spendsNoSlot() {
        PC pc = casterPc("[{\"name\":\"Fire Bolt\",\"lvl\":0}]",
                "{\"1\":{\"max\":4,\"used\":0}}", "[]");
        seatCaster(pc);

        var result = sessionService.castSpell(1L, 7L, "Fire Bolt", 0, playerId);

        assertThat(((Map<?, ?>) result.spellSlots().get("1")).get("used")).isEqualTo(0);
        verify(pcRepository).save(pc);
    }

    @Test
    void cast_upcast_spendsTheChosenLevel_notTheSpellLevel() {
        PC pc = casterPc("[{\"name\":\"Cure Wounds\",\"lvl\":1}]",
                "{\"1\":{\"max\":2,\"used\":0},\"3\":{\"max\":2,\"used\":0}}", "[]");
        seatCaster(pc);

        var result = sessionService.castSpell(1L, 7L, "Cure Wounds", 3, playerId);

        assertThat(((Map<?, ?>) result.spellSlots().get("1")).get("used")).isEqualTo(0);
        assertThat(((Map<?, ?>) result.spellSlots().get("3")).get("used")).isEqualTo(1);
    }

    // --- castSpell as a ritual: no slot spent ---

    private static final String DETECT_MAGIC = "{\"name\":\"Detect Magic\",\"lvl\":1,\"ritual\":true}";

    @Test
    void cast_asARitual_spendsNoSlot_andStillBumpsVersion() {
        PC pc = casterPc("[" + DETECT_MAGIC + "]", "{\"1\":{\"max\":4,\"used\":1}}", "[]");
        seatCaster(pc);

        var result = sessionService.castSpell(1L, 7L, "Detect Magic", 1, true, playerId);

        assertThat(((Map<?, ?>) result.spellSlots().get("1")).get("used")).isEqualTo(1);
        assertThat(result.warning()).isNull();
        assertThat(pc.getSpellSlots()).isEqualTo("{\"1\":{\"max\":4,\"used\":1}}");
        verify(pcRepository).save(pc);
        verify(sessionRepository).save(session);
    }

    @Test
    void cast_asARitual_worksWithEverySlotSpent_andIgnoresAtLevel() {
        PC pc = casterPc("[" + DETECT_MAGIC + "]", "{\"1\":{\"max\":2,\"used\":2},\"3\":{\"max\":1,\"used\":0}}", "[]");
        seatCaster(pc);

        // No atLevel, a level with no slot at all, a level with a free slot: none is spent.
        sessionService.castSpell(1L, 7L, "Detect Magic", null, true, playerId);
        sessionService.castSpell(1L, 7L, "Detect Magic", 9, true, playerId);
        var result = sessionService.castSpell(1L, 7L, "Detect Magic", 3, true, playerId);

        assertThat(((Map<?, ?>) result.spellSlots().get("1")).get("used")).isEqualTo(2);
        assertThat(((Map<?, ?>) result.spellSlots().get("3")).get("used")).isEqualTo(0);
    }

    @Test
    void cast_asARitual_throws409_forASpellThatIsNotARitual() {
        PC pc = casterPc("[{\"name\":\"Cure Wounds\",\"lvl\":1},{\"name\":\"Shield\",\"lvl\":1,\"ritual\":false}]",
                "{\"1\":{\"max\":4,\"used\":0}}", "[]");
        seatCaster(pc);

        assertThatThrownBy(() -> sessionService.castSpell(1L, 7L, "Cure Wounds", 1, true, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
        assertThatThrownBy(() -> sessionService.castSpell(1L, 7L, "Shield", 1, true, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
        verify(pcRepository, never()).save(any());
        assertThat(pc.getSpellSlots()).isEqualTo("{\"1\":{\"max\":4,\"used\":0}}");
    }

    @Test
    void cast_asARitual_throws409_forACantrip_evenOneFlaggedRitual() {
        PC pc = casterPc("[{\"name\":\"Fire Bolt\",\"lvl\":0},{\"name\":\"Odd Cantrip\",\"lvl\":0,\"ritual\":true}]",
                "{\"1\":{\"max\":4,\"used\":0}}", "[]");
        seatCaster(pc);

        assertThatThrownBy(() -> sessionService.castSpell(1L, 7L, "Fire Bolt", 0, true, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
        assertThatThrownBy(() -> sessionService.castSpell(1L, 7L, "Odd Cantrip", 0, true, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
        verify(pcRepository, never()).save(any());
    }

    @Test
    void cast_asARitual_stillAnswers400_forASpellTheCharacterDoesNotKnow_and403_forAStranger() {
        PC pc = casterPc("[" + DETECT_MAGIC + "]", "{\"1\":{\"max\":4,\"used\":0}}", "[]");
        seatCaster(pc);

        assertThatThrownBy(() -> sessionService.castSpell(1L, 7L, "Identify", 1, true, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        assertThatThrownBy(() -> sessionService.castSpell(1L, 7L, "Detect Magic", 1, true, strangerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
    }

    @Test
    void cast_asARitual_stillResolvesMaterialComponents() {
        String spells = "[{\"name\":\"Augury\",\"lvl\":2,\"ritual\":true,\"components\":[\"v\",\"s\",\"m\"],"
                + "\"material\":\"marked sticks worth 25+ GP\"}]";
        PC pc = casterPc(spells, "{\"2\":{\"max\":2,\"used\":0}}",
                "[{\"category\":\"material-component\",\"spell\":\"Augury\",\"qty\":1,\"consumedOnCast\":true}]");
        seatCaster(pc);

        var consumed = sessionService.castSpell(1L, 7L, "Augury", 2, true, playerId);
        assertThat(consumed.inventory()).isEmpty();
        assertThat(consumed.warning()).isNull();

        // The component is gone now: a lenient campaign lets the ritual through with a warning.
        var warned = sessionService.castSpell(1L, 7L, "Augury", 2, true, playerId);
        assertThat(warned.warning()).contains("Missing material component");
        assertThat(((Map<?, ?>) warned.spellSlots().get("2")).get("used")).isEqualTo(0);
    }

    @Test
    void cast_asARitual_isBlockedByAMissingCostlyComponent_whenStrict() {
        campaign.setVariantRules("{\"strictComponents\":true}");
        String spells = "[{\"name\":\"Augury\",\"lvl\":2,\"ritual\":true,\"components\":[\"v\",\"s\",\"m\"],"
                + "\"material\":\"marked sticks worth 25+ GP\"}]";
        PC pc = casterPc(spells, "{\"2\":{\"max\":2,\"used\":0}}", "[]");
        seatCaster(pc);

        assertThatThrownBy(() -> sessionService.castSpell(1L, 7L, "Augury", 2, true, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
        verify(pcRepository, never()).save(any());
    }

    @Test
    void cast_aRitualSpell_castTheOrdinaryWay_spendsASlotAsBefore() {
        PC pc = casterPc("[" + DETECT_MAGIC + "]", "{\"1\":{\"max\":4,\"used\":1}}", "[]");
        seatCaster(pc);

        // The five-argument call and an explicit "not a ritual" are the same ordinary cast.
        sessionService.castSpell(1L, 7L, "Detect Magic", 1, playerId);
        var result = sessionService.castSpell(1L, 7L, "Detect Magic", 1, false, playerId);

        assertThat(((Map<?, ?>) result.spellSlots().get("1")).get("used")).isEqualTo(3);
    }

    @Test
    void cast_throws409_whenNoSlotAtThatLevelIsFree() {
        PC pc = casterPc("[{\"name\":\"Cure Wounds\",\"lvl\":1}]",
                "{\"1\":{\"max\":2,\"used\":2}}", "[]");
        seatCaster(pc);

        assertThatThrownBy(() -> sessionService.castSpell(1L, 7L, "Cure Wounds", 1, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
        verify(pcRepository, never()).save(any());
    }

    @Test
    void cast_throws409_whenAskedToCastBelowTheSpellLevel() {
        PC pc = casterPc("[{\"name\":\"Revivify\",\"lvl\":3}]",
                "{\"1\":{\"max\":4,\"used\":0}}", "[]");
        seatCaster(pc);

        assertThatThrownBy(() -> sessionService.castSpell(1L, 7L, "Revivify", 1, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
    }

    @Test
    void cast_throws400_whenTheCharacterDoesNotKnowTheSpell() {
        PC pc = casterPc("[{\"name\":\"Cure Wounds\",\"lvl\":1}]",
                "{\"1\":{\"max\":4,\"used\":0}}", "[]");
        seatCaster(pc);

        assertThatThrownBy(() -> sessionService.castSpell(1L, 7L, "Wish", 9, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
    }

    @Test
    void cast_throws403_whenThePcIsNotSeated() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(participantRepository.findBySessionIdAndPcId(1L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.castSpell(1L, 7L, "Cure Wounds", 1, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
    }

    @Test
    void cast_throws403_forSomeoneElsesCharacter() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        SessionParticipant seat = participant(50L, 7L);
        seat.setOwnerUserId(playerId);
        when(participantRepository.findBySessionIdAndPcId(1L, 7L)).thenReturn(Optional.of(seat));

        assertThatThrownBy(() -> sessionService.castSpell(1L, 7L, "Cure Wounds", 1, strangerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
        verify(pcRepository, never()).save(any());
    }

    @Test
    void cast_throws409_whenTheSessionHasEnded() {
        session.setStatus(SessionStatus.ENDED);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.castSpell(1L, 7L, "Cure Wounds", 1, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
    }

    @Test
    void cast_consumesAConsumedOnCastComponent_andRemovesItAtZero() {
        String spells = "[{\"name\":\"Revivify\",\"lvl\":3,\"components\":[\"v\",\"m\"],"
                + "\"material\":\"diamonds worth 300+ GP\"}]";
        PC pc = casterPc(spells, "{\"3\":{\"max\":2,\"used\":0}}",
                "[{\"category\":\"material-component\",\"spell\":\"Revivify\",\"qty\":1,\"consumedOnCast\":true}]");
        seatCaster(pc);

        var result = sessionService.castSpell(1L, 7L, "Revivify", 3, playerId);

        assertThat(result.inventory()).isEmpty();
        assertThat(result.warning()).isNull();
    }

    @Test
    void cast_leavesAReusableComponentUntouched() {
        String spells = "[{\"name\":\"Chromatic Orb\",\"lvl\":1,\"components\":[\"v\",\"s\",\"m\"],"
                + "\"material\":\"a diamond worth 50+ GP\"}]";
        PC pc = casterPc(spells, "{\"1\":{\"max\":2,\"used\":0}}",
                "[{\"category\":\"material-component\",\"spell\":\"Chromatic Orb\",\"qty\":1,\"consumedOnCast\":false}]");
        seatCaster(pc);

        var result = sessionService.castSpell(1L, 7L, "Chromatic Orb", 1, playerId);

        assertThat(result.inventory()).hasSize(1);
        assertThat(result.inventory().get(0).get("qty")).isEqualTo(1);
    }

    @Test
    void cast_missingCostlyComponent_warnsWhenLenient() {
        String spells = "[{\"name\":\"Revivify\",\"lvl\":3,\"components\":[\"v\",\"m\"],"
                + "\"material\":\"diamonds worth 300+ GP\"}]";
        PC pc = casterPc(spells, "{\"3\":{\"max\":2,\"used\":0}}", "[]");
        seatCaster(pc); // campaign has no strictComponents variant

        var result = sessionService.castSpell(1L, 7L, "Revivify", 3, playerId);

        assertThat(result.warning()).contains("Missing material component");
        assertThat(((Map<?, ?>) result.spellSlots().get("3")).get("used")).isEqualTo(1);
        verify(pcRepository).save(pc); // cast still went through
    }

    @Test
    void cast_missingCostlyComponent_blocksWhenStrict() {
        campaign.setVariantRules("{\"strictComponents\":true}");
        String spells = "[{\"name\":\"Revivify\",\"lvl\":3,\"components\":[\"v\",\"m\"],"
                + "\"material\":\"diamonds worth 300+ GP\"}]";
        PC pc = casterPc(spells, "{\"3\":{\"max\":2,\"used\":0}}", "[]");
        seatCaster(pc);

        assertThatThrownBy(() -> sessionService.castSpell(1L, 7L, "Revivify", 3, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
        verify(pcRepository, never()).save(any());
    }

    // --- applyDamage: dropping to 0 HP is an exhausting shock ---

    @Test
    void damage_droppingAPcToZero_addsFatigue_whenVariantOn() {
        campaign.setVariantRules("{\"survivalConditions\":true}");
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(participantRepository.findById(5L)).thenReturn(Optional.of(participant(5L, 7L)));
        PC pc = pc(7L, "Gorath", 0);
        pc.setHpCurrent((short) 5);
        pc.setHpMax((short) 10);
        when(pcService.findPCById(7L)).thenReturn(pc);

        sessionService.applyDamage(1L, 5L, 9, dmId);

        assertThat(pc.getHpCurrent()).isZero();
        assertThat(pc.getSurvival()).contains("\"fatigue\":3"); // Ok default (2) + 1
    }

    @Test
    void damage_atZeroAlready_doesNotStackFatigue() {
        campaign.setVariantRules("{\"survivalConditions\":true}");
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(participantRepository.findById(5L)).thenReturn(Optional.of(participant(5L, 7L)));
        PC pc = pc(7L, "Gorath", 0);
        pc.setHpCurrent((short) 0);
        pc.setHpMax((short) 10);
        when(pcService.findPCById(7L)).thenReturn(pc);

        sessionService.applyDamage(1L, 5L, 4, dmId);

        assertThat(pc.getSurvival()).isNull(); // no transition into 0 → untouched
    }

    @Test
    void damage_toZero_leavesSurvivalAlone_whenVariantOff() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(participantRepository.findById(5L)).thenReturn(Optional.of(participant(5L, 7L)));
        PC pc = pc(7L, "Gorath", 0);
        pc.setHpCurrent((short) 3);
        pc.setHpMax((short) 10);
        when(pcService.findPCById(7L)).thenReturn(pc);

        sessionService.applyDamage(1L, 5L, 5, dmId);

        assertThat(pc.getHpCurrent()).isZero();
        assertThat(pc.getSurvival()).isNull();
    }

    // --- combat log: health changes made through the session are recorded ---

    /** The single combat log row a call saved. */
    private SessionCombatLog loggedRow() {
        ArgumentCaptor<SessionCombatLog> row = ArgumentCaptor.forClass(SessionCombatLog.class);
        verify(combatLogRepository).save(row.capture());
        return row.getValue();
    }

    @Test
    void damage_toAPc_isLogged_withTheRound_andWhoDidIt() {
        session.setRound((short) 3);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(participantRepository.findById(5L)).thenReturn(Optional.of(participant(5L, 7L)));
        PC pc = pc(7L, "Gorath", 0);
        pc.setHpCurrent((short) 12);
        pc.setHpTemp((short) 3);
        pc.setHpMax((short) 20);
        when(pcService.findPCById(7L)).thenReturn(pc);

        sessionService.applyDamage(1L, 5L, 7, dmId);

        SessionCombatLog row = loggedRow();
        assertThat(row.getSessionId()).isEqualTo(1L);
        assertThat(row.getEventType()).isEqualTo(CombatLogType.DAMAGE);
        assertThat(row.getSubjectName()).isEqualTo("Gorath");
        assertThat(row.getDescription()).isEqualTo("Took 7 damage, 3 absorbed by temporary HP. HP 12 → 8.");
        assertThat(row.getRound()).isEqualTo((short) 3);
        assertThat(row.getActorUserId()).isEqualTo(dmId);
    }

    @Test
    void damage_droppingAPcToZero_isLoggedAsDown() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(participantRepository.findById(5L)).thenReturn(Optional.of(participant(5L, 7L)));
        PC pc = pc(7L, "Gorath", 0);
        pc.setHpCurrent((short) 5);
        pc.setHpMax((short) 10);
        when(pcService.findPCById(7L)).thenReturn(pc);

        sessionService.applyDamage(1L, 5L, 9, dmId);

        assertThat(loggedRow().getDescription()).isEqualTo("Took 9 damage. HP 5 → 0. Down at 0 HP.");
    }

    @Test
    void healing_aPc_isLogged_withWhatItActuallyRestored_andNoRoundOutsideAnEncounter() {
        session.setStatus(SessionStatus.LOBBY);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(participantRepository.findById(5L)).thenReturn(Optional.of(participant(5L, 7L)));
        PC pc = pc(7L, "Gorath", 0);
        pc.setHpCurrent((short) 17);
        pc.setHpMax((short) 20);
        when(pcService.findPCById(7L)).thenReturn(pc);

        sessionService.applyDamage(1L, 5L, -8, dmId); // only 3 fit under the maximum

        SessionCombatLog row = loggedRow();
        assertThat(row.getEventType()).isEqualTo(CombatLogType.HEALING);
        assertThat(row.getDescription()).isEqualTo("Healed 3. HP 17 → 20.");
        assertThat(row.getRound()).isNull();
    }

    @Test
    void healing_atFullHp_changesNothing_andLogsNothing() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(participantRepository.findById(5L)).thenReturn(Optional.of(participant(5L, 7L)));
        PC pc = pc(7L, "Gorath", 0);
        pc.setHpCurrent((short) 20);
        pc.setHpMax((short) 20);
        when(pcService.findPCById(7L)).thenReturn(pc);

        sessionService.applyDamage(1L, 5L, -5, dmId);

        verify(combatLogRepository, never()).save(any());
    }

    @Test
    void damage_killingAnEnemy_isLoggedAsDefeated_thoughItsRowIsGone() {
        SessionParticipant goblin = enemy(6L, 10, 4);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(participantRepository.findById(6L)).thenReturn(Optional.of(goblin));
        when(participantRepository.findBySessionIdOrderByOrderIndexAsc(1L)).thenReturn(new ArrayList<>());

        sessionService.applyDamage(1L, 6L, 6, dmId);

        verify(participantRepository).delete(goblin);
        SessionCombatLog row = loggedRow();
        assertThat(row.getSubjectName()).isEqualTo("Goblin");
        assertThat(row.getDescription()).isEqualTo("Took 6 damage. HP 4 → 0. Defeated.");
    }

    @Test
    void followerDamage_isLogged_underTheFollowerAndItsOwner() {
        seatFollower("{\"hp\":{\"cur\":9,\"max\":16,\"temp\":0}}");
        SessionParticipant ownerSeat = participant(5L, 7L);
        ownerSeat.setDisplayName("Gorath");
        when(participantRepository.findBySessionIdAndPcId(1L, 7L)).thenReturn(Optional.of(ownerSeat));

        sessionService.applyFollowerDamage(1L, 40L, 4, dmId);

        SessionCombatLog row = loggedRow();
        assertThat(row.getSubjectName()).isEqualTo("Brannoc (Gorath's follower)");
        assertThat(row.getDescription()).isEqualTo("Took 4 damage. HP 9 → 5.");
    }

    @Test
    void endSession_purgesTheCombatLog() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(CombatSession.class))).thenReturn(session);
        when(participantRepository.findBySessionIdOrderByOrderIndexAsc(1L)).thenReturn(new ArrayList<>());

        sessionService.endSession(1L, dmId);

        verify(combatLogRepository).deleteBySessionId(1L);
    }

    @Test
    void ttlExpiry_purgesTheCombatLog() {
        session.setUpdatedAt(Instant.now().minus(Duration.ofHours(5)));
        when(campaignService.findById(1L)).thenReturn(campaign);
        when(pcRepository.findByCampaignId(1L)).thenReturn(List.of());
        when(sessionRepository.findByCampaignIdAndStatusNot(1L, SessionStatus.ENDED))
                .thenReturn(Optional.of(session));

        sessionService.getActiveSessionForCampaign(1L, dmId);

        verify(combatLogRepository).deleteBySessionId(1L);
    }

    @Test
    void combatLog_givesTheDmOnePage_newestFirst() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        SessionCombatLog row = new SessionCombatLog();
        row.setId(9L);
        row.setEventType(CombatLogType.DAMAGE);
        row.setSubjectName("Goblin");
        row.setDescription("Took 4 damage. HP 9 → 5.");
        row.setRound((short) 2);
        when(combatLogRepository.findBySessionIdOrderByIdDesc(eq(1L), any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<>(List.of(row), inv.getArgument(1), 21));

        CombatLogPageView page = sessionService.combatLog(1L, 2, 10, dmId);

        assertThat(page.page()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(10);
        assertThat(page.total()).isEqualTo(21);
        assertThat(page.entries()).hasSize(1);
        assertThat(page.entries().get(0).type()).isEqualTo("DAMAGE");
        assertThat(page.entries().get(0).subject()).isEqualTo("Goblin");
        assertThat(page.entries().get(0).round()).isEqualTo((short) 2);
    }

    @Test
    void combatLog_isDmOnly() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.combatLog(1L, 0, 10, playerId))
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
        verify(combatLogRepository, never()).findBySessionIdOrderByIdDesc(any(), any());
    }

    // --- applyDamage: a defeated enemy leaves the initiative order ---

    private SessionParticipant enemy(Long id, int hpMax, int hpCurrent) {
        SessionParticipant e = participant(id, null);
        e.setDisplayName("Goblin");
        e.setNpcHpMax((short) hpMax);
        e.setNpcHpCurrent((short) hpCurrent);
        return e;
    }

    @Test
    void damage_killingAnEnemy_removesItFromTheOrder_withoutTouchingThePointer() {
        SessionParticipant goblin = enemy(6L, 10, 4);
        SessionParticipant hero = participant(5L, 7L);
        session.setCurrentTurnParticipantId(5L); // someone else's turn
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(participantRepository.findById(6L)).thenReturn(Optional.of(goblin));
        when(participantRepository.findBySessionIdOrderByOrderIndexAsc(1L))
                .thenReturn(new ArrayList<>(List.of(hero)));
        when(pcRepository.findAllById(any())).thenReturn(List.of(pc(7L, "Hero", 0)));

        sessionService.applyDamage(1L, 6L, 4, dmId);

        verify(participantRepository).delete(goblin);
        verify(participantRepository, never()).save(goblin);
        assertThat(session.getCurrentTurnParticipantId()).isEqualTo(5L);
    }

    @Test
    void damage_killingTheActiveEnemy_advancesThePointerBeforeRemoval() {
        SessionParticipant goblin = enemy(6L, 10, 3);
        goblin.setOrderIndex((short) 0);
        SessionParticipant hero = participant(5L, 7L);
        hero.setOrderIndex((short) 1);
        session.setCurrentTurnParticipantId(6L); // the dying goblin holds the turn
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(participantRepository.findById(6L)).thenReturn(Optional.of(goblin));
        when(participantRepository.findBySessionIdOrderByOrderIndexAsc(1L))
                .thenReturn(new ArrayList<>(List.of(goblin, hero)));
        when(pcRepository.findAllById(any())).thenReturn(List.of(pc(7L, "Hero", 0)));

        sessionService.applyDamage(1L, 6L, 5, dmId);

        assertThat(session.getCurrentTurnParticipantId()).isEqualTo(5L);
        verify(participantRepository).delete(goblin);
    }

    @Test
    void damage_killingTheOnlyCombatant_clearsThePointer() {
        SessionParticipant goblin = enemy(6L, 10, 2);
        session.setCurrentTurnParticipantId(6L);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(participantRepository.findById(6L)).thenReturn(Optional.of(goblin));
        when(participantRepository.findBySessionIdOrderByOrderIndexAsc(1L))
                .thenReturn(new ArrayList<>(List.of(goblin)))  // pointer check
                .thenReturn(new ArrayList<>());                // buildState after delete
        sessionService.applyDamage(1L, 6L, 2, dmId);

        assertThat(session.getCurrentTurnParticipantId()).isNull();
        verify(participantRepository).delete(goblin);
    }

    @Test
    void damage_woundedEnemy_staysInTheOrder() {
        SessionParticipant goblin = enemy(6L, 10, 8);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(participantRepository.findById(6L)).thenReturn(Optional.of(goblin));
        when(participantRepository.findBySessionIdOrderByOrderIndexAsc(1L))
                .thenReturn(new ArrayList<>(List.of(goblin)));

        sessionService.applyDamage(1L, 6L, 3, dmId);

        assertThat(goblin.getNpcHpCurrent()).isEqualTo((short) 5);
        verify(participantRepository).save(goblin);
        verify(participantRepository, never()).delete(any(SessionParticipant.class));
    }

    @Test
    void damage_droppingAPcToZero_keepsThePcInTheOrder() {
        // PCs at 0 HP stay seated (dying / death saves) — removal is enemies only.
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(participantRepository.findById(5L)).thenReturn(Optional.of(participant(5L, 7L)));
        PC pc = pc(7L, "Gorath", 0);
        pc.setHpCurrent((short) 3);
        pc.setHpMax((short) 10);
        when(pcService.findPCById(7L)).thenReturn(pc);

        sessionService.applyDamage(1L, 5L, 5, dmId);

        assertThat(pc.getHpCurrent()).isZero();
        verify(participantRepository, never()).delete(any(SessionParticipant.class));
    }

    // --- logRoll ---

    private static LogRollRequest rollRequest(int sides, Integer... rolls) {
        return new LogRollRequest(List.of(new LogRollRequest.DieGroup(sides, List.of(rolls))));
    }

    @Test
    void logRoll_byOwner_succeeds_bumpsVersion_andSumsServerSide() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        SessionParticipant mine = participant(5L, 7L);
        mine.setOwnerUserId(playerId);
        when(participantRepository.findById(5L)).thenReturn(Optional.of(mine));
        when(participantRepository.findBySessionIdOrderByOrderIndexAsc(1L))
                .thenReturn(new ArrayList<>(List.of(mine)));
        PC pc = pc(7L, "Gorath", 0);
        when(pcService.findPCById(7L)).thenReturn(pc);
        stubPcs();
        when(pcRepository.findAllById(any())).thenReturn(List.of(pc));

        // Client-sent total would be wrong (12) — the server ignores it and sums 4+2+6=12... use a
        // deliberately-mismatched case: rolls sum to 12 regardless, so assert via captured entity.
        sessionService.logRoll(1L, 5L, rollRequest(6, 4, 2, 6), playerId);

        ArgumentCaptor<SessionRoll> captor = ArgumentCaptor.forClass(SessionRoll.class);
        verify(sessionRollRepository).save(captor.capture());
        SessionRoll saved = captor.getValue();
        assertThat(saved.getGrandTotal()).isEqualTo(12); // server-summed from the validated rolls
        assertThat(saved.getOwnerUserId()).isEqualTo(playerId);
        assertThat(saved.getDisplayName()).isEqualTo("Gorath");
        assertThat(saved.getSessionId()).isEqualTo(1L);
        assertThat(saved.getParticipantId()).isEqualTo(5L);
        verify(sessionRepository).save(session); // version bumped
        assertThat(session.getVersion()).isEqualTo(1L);
    }

    @Test
    void logRoll_byDm_forSomeoneElsesParticipant_succeeds() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        SessionParticipant theirs = participant(5L, 7L);
        theirs.setOwnerUserId(playerId);
        when(participantRepository.findById(5L)).thenReturn(Optional.of(theirs));
        when(participantRepository.findBySessionIdOrderByOrderIndexAsc(1L))
                .thenReturn(new ArrayList<>(List.of(theirs)));
        PC pc = pc(7L, "Gorath", 0);
        when(pcService.findPCById(7L)).thenReturn(pc);
        when(pcRepository.findAllById(any())).thenReturn(List.of(pc));

        sessionService.logRoll(1L, 5L, rollRequest(20, 15), dmId);

        verify(sessionRollRepository).save(any(SessionRoll.class));
    }

    @Test
    void logRoll_byStranger_throws403() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        SessionParticipant theirs = participant(5L, 7L);
        theirs.setOwnerUserId(playerId);
        when(participantRepository.findById(5L)).thenReturn(Optional.of(theirs));

        assertThatThrownBy(() -> sessionService.logRoll(1L, 5L, rollRequest(6, 4), strangerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
        verify(sessionRollRepository, never()).save(any());
    }

    @Test
    void logRoll_invalidDieSize_throws400() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        SessionParticipant mine = participant(5L, 7L);
        mine.setOwnerUserId(playerId);
        when(participantRepository.findById(5L)).thenReturn(Optional.of(mine));

        assertThatThrownBy(() -> sessionService.logRoll(1L, 5L, rollRequest(7, 3), playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        verify(sessionRollRepository, never()).save(any());
    }

    @Test
    void logRoll_rollOutOfRange_throws400() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        SessionParticipant mine = participant(5L, 7L);
        mine.setOwnerUserId(playerId);
        when(participantRepository.findById(5L)).thenReturn(Optional.of(mine));

        assertThatThrownBy(() -> sessionService.logRoll(1L, 5L, rollRequest(6, 7), playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        verify(sessionRollRepository, never()).save(any());
    }

    @Test
    void logRoll_onEndedSession_throws409() {
        session.setStatus(SessionStatus.ENDED);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.logRoll(1L, 5L, rollRequest(6, 4), playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
        verify(sessionRollRepository, never()).save(any());
    }

    @Test
    void buildState_dmSeesAllRolls_playerSeesOnlyOwn_mineFlagCorrect() {
        SessionParticipant mine = pcCombatant(1L, 7L, (short) 20, 0);
        mine.setOwnerUserId(playerId);
        SessionParticipant theirs = pcCombatant(2L, 8L, (short) 10, 1);
        theirs.setOwnerUserId(strangerId);
        arm(session, 1L, new ArrayList<>(List.of(mine, theirs)));
        stubPcs();
        when(pcRepository.findAllById(any())).thenReturn(List.of(pcWithDex(7L, "Mine", 10), pcWithDex(8L, "Theirs", 10)));

        SessionRoll myRoll = rollRow(1L, 1L, playerId, "Mine", "[{\"sides\":6,\"rolls\":[4]}]", 4);
        SessionRoll theirRoll = rollRow(2L, 2L, strangerId, "Theirs", "[{\"sides\":6,\"rolls\":[5]}]", 5);
        when(sessionRollRepository.findTop50BySessionIdOrderByCreatedAtDescIdDesc(1L))
                .thenReturn(List.of(theirRoll, myRoll));

        SessionStateView dmState = sessionService.getState(1L, dmId, null);
        SessionStateView playerState = sessionService.getState(1L, playerId, null);

        assertThat(dmState.rolls()).hasSize(2);
        assertThat(playerState.rolls()).hasSize(1);
        assertThat(playerState.rolls().get(0).rollId()).isEqualTo(1L);
        assertThat(playerState.rolls().get(0).mine()).isTrue();
        SessionRollView dmSeesTheirs = dmState.rolls().stream()
                .filter(r -> r.rollId().equals(2L)).findFirst().orElseThrow();
        assertThat(dmSeesTheirs.mine()).isFalse();
    }

    @Test
    void endSession_purgesRollLog() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(CombatSession.class))).thenReturn(session);
        when(participantRepository.findBySessionIdOrderByOrderIndexAsc(1L)).thenReturn(new ArrayList<>());

        sessionService.endSession(1L, dmId);

        verify(sessionRollRepository).deleteBySessionId(1L);
        assertThat(session.getStatus()).isEqualTo(SessionStatus.ENDED);
    }

    @Test
    void ttlExpiry_purgesRollLog() {
        // A session idle well past the 4h TTL is auto-ended on next access via
        // getActiveSessionForCampaign → activeSession's lazy expiry branch.
        session.setUpdatedAt(Instant.now().minus(Duration.ofHours(5)));
        when(campaignService.findById(1L)).thenReturn(campaign);
        when(pcRepository.findByCampaignId(1L)).thenReturn(List.of());
        when(sessionRepository.findByCampaignIdAndStatusNot(1L, SessionStatus.ENDED))
                .thenReturn(Optional.of(session));

        SessionStateView state = sessionService.getActiveSessionForCampaign(1L, dmId);

        assertThat(state).isNull(); // expired → treated as "no active session"
        assertThat(session.getStatus()).isEqualTo(SessionStatus.ENDED);
        verify(sessionRollRepository).deleteBySessionId(1L);
    }

    // --- rest vote (player-initiated short rest) ---

    /** Two seated PCs (mine + theirs, ids 5/6 → pcs 7/8) and one ad-hoc NPC (9). */
    private List<SessionParticipant> restVoteParty() {
        SessionParticipant mine = participant(5L, 7L);
        mine.setOwnerUserId(playerId);
        SessionParticipant theirs = participant(6L, 8L);
        theirs.setOwnerUserId(strangerId);
        SessionParticipant npc = participant(9L, null);
        npc.setDisplayName("Goblin");
        return new ArrayList<>(List.of(mine, theirs, npc));
    }

    /** Wire a LOBBY session with the standard party and vote-row persistence:
     *  save assigns id 10; saved ballots are served back by the ballot query. */
    private List<SessionRestVoteBallot> armRestVote() {
        session.setStatus(SessionStatus.LOBBY);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(participantRepository.findBySessionIdOrderByOrderIndexAsc(1L)).thenReturn(restVoteParty());
        when(pcRepository.findAllById(any()))
                .thenReturn(List.of(pc(7L, "Gorath", 0), pc(8L, "Lyra", 0)));
        List<SessionRestVoteBallot> saved = new ArrayList<>();
        lenient().when(restVoteRepository.save(any(SessionRestVote.class))).thenAnswer(inv -> {
            SessionRestVote v = inv.getArgument(0);
            if (v.getId() == null) {
                v.setId(10L);
            }
            lenient().when(restVoteRepository.findTopBySessionIdOrderByIdDesc(1L))
                    .thenReturn(Optional.of(v));
            return v;
        });
        lenient().when(restVoteBallotRepository.saveAll(any())).thenAnswer(inv -> {
            saved.addAll(inv.getArgument(0));
            return inv.getArgument(0);
        });
        lenient().when(restVoteBallotRepository.findByVoteIdOrderByIdAsc(10L))
                .thenAnswer(inv -> new ArrayList<>(saved));
        return saved;
    }

    /** An ACTIVE vote row (id 10) already stored, lock-loadable, with the given ballots. */
    private SessionRestVote activeVote(List<SessionRestVoteBallot> ballots, Instant expiresAt) {
        SessionRestVote vote = new SessionRestVote();
        vote.setId(10L);
        vote.setSessionId(1L);
        vote.setInitiatorParticipantId(5L);
        vote.setInitiatorName("Gorath");
        vote.setExpiresAt(expiresAt);
        when(restVoteRepository.findBySessionIdAndStatus(1L, RestVoteStatus.ACTIVE))
                .thenReturn(Optional.of(vote));
        lenient().when(restVoteRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(vote));
        lenient().when(restVoteBallotRepository.findByVoteIdOrderByIdAsc(10L))
                .thenAnswer(inv -> new ArrayList<>(ballots));
        lenient().when(restVoteRepository.findTopBySessionIdOrderByIdDesc(1L))
                .thenReturn(Optional.of(vote));
        return vote;
    }

    private static SessionRestVoteBallot ballot(Long participantId, String name, Boolean vote) {
        SessionRestVoteBallot b = new SessionRestVoteBallot();
        b.setId(participantId + 100);
        b.setVoteId(10L);
        b.setParticipantId(participantId);
        b.setDisplayName(name);
        b.setVote(vote);
        return b;
    }

    @Test
    void initiateRestVote_ballotsForSeatedPcsOnly_initiatorPreYes() {
        List<SessionRestVoteBallot> saved = armRestVote();

        SessionStateView state = sessionService.initiateRestVote(1L, RestType.SHORT, playerId);

        assertThat(saved).hasSize(2); // the NPC holds no ballot
        SessionRestVoteBallot mine = saved.stream()
                .filter(b -> b.getParticipantId().equals(5L)).findFirst().orElseThrow();
        SessionRestVoteBallot theirs = saved.stream()
                .filter(b -> b.getParticipantId().equals(6L)).findFirst().orElseThrow();
        assertThat(mine.getVote()).isTrue(); // calling the rest is a yes
        assertThat(mine.getDisplayName()).isEqualTo("Gorath");
        assertThat(theirs.getVote()).isNull(); // the yellow "?"
        assertThat(state.restVote()).isNotNull();
        assertThat(state.restVote().status()).isEqualTo("ACTIVE");
        assertThat(state.restVote().initiatorName()).isEqualTo("Gorath");
        assertThat(state.restVote().votes()).hasSize(2);
        assertThat(state.shortRestOpen()).isFalse(); // not unanimous yet
        assertThat(session.getVersion()).isEqualTo(1L);
        verify(restVoteRepository).deleteBySessionId(1L); // prior terminal vote replaced
    }

    @Test
    void initiateRestVote_soloParty_isUnanimityOfOne_andOpensTheWindow() {
        armRestVote();
        SessionParticipant mine = participant(5L, 7L);
        mine.setOwnerUserId(playerId);
        when(participantRepository.findBySessionIdOrderByOrderIndexAsc(1L))
                .thenReturn(new ArrayList<>(List.of(mine)));

        SessionStateView state = sessionService.initiateRestVote(1L, RestType.SHORT, playerId);

        assertThat(state.restVote().status()).isEqualTo("PASSED");
        assertThat(state.shortRestOpen()).isTrue();
    }

    @Test
    void initiateRestVote_throws409_whenAVoteIsAlreadyActive() {
        session.setStatus(SessionStatus.LOBBY);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(restVoteRepository.findBySessionIdAndStatus(1L, RestVoteStatus.ACTIVE))
                .thenReturn(Optional.of(new SessionRestVote()));

        assertThatThrownBy(() -> sessionService.initiateRestVote(1L, RestType.SHORT, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
    }

    @Test
    void initiateRestVote_throws409_whileTheWindowIsAlreadyOpen() {
        session.setStatus(SessionStatus.LOBBY);
        session.setShortRestOpen(true);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.initiateRestVote(1L, RestType.SHORT, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
    }

    @Test
    void initiateRestVote_throws409_duringAnEncounter() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session)); // ACTIVE

        assertThatThrownBy(() -> sessionService.initiateRestVote(1L, RestType.SHORT, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
    }

    @Test
    void initiateRestVote_throws403_forACallerWithoutASeatedPc() {
        session.setStatus(SessionStatus.LOBBY);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(participantRepository.findBySessionIdOrderByOrderIndexAsc(1L)).thenReturn(restVoteParty());

        assertThatThrownBy(() -> sessionService.initiateRestVote(1L, RestType.SHORT, dmId)) // DM holds no seat
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
        verify(restVoteRepository, never()).save(any());
    }

    @Test
    void castRestVote_no_failsTheVoteInstantly() {
        session.setStatus(SessionStatus.LOBBY);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(participantRepository.findBySessionIdOrderByOrderIndexAsc(1L)).thenReturn(restVoteParty());
        when(pcRepository.findAllById(any()))
                .thenReturn(List.of(pc(7L, "Gorath", 0), pc(8L, "Lyra", 0)));
        List<SessionRestVoteBallot> ballots = new ArrayList<>(List.of(
                ballot(5L, "Gorath", true), ballot(6L, "Lyra", null)));
        SessionRestVote vote = activeVote(ballots, Instant.now().plus(Duration.ofSeconds(60)));

        SessionStateView state = sessionService.castRestVote(1L, false, strangerId);

        assertThat(vote.getStatus()).isEqualTo(RestVoteStatus.FAILED);
        assertThat(state.shortRestOpen()).isFalse();
        assertThat(session.getVersion()).isEqualTo(1L);
        verify(restVoteRepository).save(vote);
    }

    @Test
    void castRestVote_lastYes_passes_andOpensTheWindow() {
        session.setStatus(SessionStatus.LOBBY);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(participantRepository.findBySessionIdOrderByOrderIndexAsc(1L)).thenReturn(restVoteParty());
        when(pcRepository.findAllById(any()))
                .thenReturn(List.of(pc(7L, "Gorath", 0), pc(8L, "Lyra", 0)));
        List<SessionRestVoteBallot> ballots = new ArrayList<>(List.of(
                ballot(5L, "Gorath", true), ballot(6L, "Lyra", null)));
        SessionRestVote vote = activeVote(ballots, Instant.now().plus(Duration.ofSeconds(60)));

        SessionStateView state = sessionService.castRestVote(1L, true, strangerId);

        assertThat(vote.getStatus()).isEqualTo(RestVoteStatus.PASSED);
        assertThat(state.shortRestOpen()).isTrue();
    }

    @Test
    void castRestVote_throws403_forACallerWithoutABallot() {
        session.setStatus(SessionStatus.LOBBY);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(participantRepository.findBySessionIdOrderByOrderIndexAsc(1L)).thenReturn(restVoteParty());
        activeVote(new ArrayList<>(List.of(ballot(5L, "Gorath", true))), // only participant 5 holds one
                Instant.now().plus(Duration.ofSeconds(60)));

        assertThatThrownBy(() -> sessionService.castRestVote(1L, true, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
    }

    @Test
    void castRestVote_throws409_whenNoVoteIsActive() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.castRestVote(1L, true, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
    }

    @Test
    void getState_lazilyFailsAnExpiredVote_andBumpsVersion_soIdlePollersWake() {
        session.setStatus(SessionStatus.LOBBY);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(participantRepository.findBySessionIdOrderByOrderIndexAsc(1L)).thenReturn(restVoteParty());
        when(pcRepository.findAllById(any()))
                .thenReturn(List.of(pc(7L, "Gorath", 0), pc(8L, "Lyra", 0)));
        List<SessionRestVoteBallot> ballots = new ArrayList<>(List.of(
                ballot(5L, "Gorath", true), ballot(6L, "Lyra", null))); // a straggler
        SessionRestVote vote = activeVote(ballots, Instant.now().minus(Duration.ofSeconds(5)));

        // sinceVersion matches the pre-resolution version: without the lazy
        // check this poll would 204 and the client would never learn the outcome.
        SessionStateView state = sessionService.getState(1L, dmId, session.getVersion());

        assertThat(state).isNotNull();
        assertThat(vote.getStatus()).isEqualTo(RestVoteStatus.FAILED); // silence is not consent
        assertThat(state.shortRestOpen()).isFalse();
        assertThat(session.getVersion()).isEqualTo(1L);
        verify(restVoteRepository).save(vote);
    }

    @Test
    void getState_settlesAVoteWhoseLastHoldoutLeft_asPassed() {
        session.setStatus(SessionStatus.LOBBY);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(participantRepository.findBySessionIdOrderByOrderIndexAsc(1L)).thenReturn(restVoteParty());
        when(pcRepository.findAllById(any()))
                .thenReturn(List.of(pc(7L, "Gorath", 0), pc(8L, "Lyra", 0)));
        // The undecided ballot cascade-deleted with the leaver — all remaining are yes.
        List<SessionRestVoteBallot> ballots = new ArrayList<>(List.of(ballot(5L, "Gorath", true)));
        SessionRestVote vote = activeVote(ballots, Instant.now().plus(Duration.ofSeconds(60)));

        SessionStateView state = sessionService.getState(1L, dmId, null);

        assertThat(vote.getStatus()).isEqualTo(RestVoteStatus.PASSED);
        assertThat(state.shortRestOpen()).isTrue();
    }

    @Test
    void overrideRestVote_yes_passesRegardlessOfMissingBallots() {
        session.setStatus(SessionStatus.LOBBY);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(participantRepository.findBySessionIdOrderByOrderIndexAsc(1L)).thenReturn(restVoteParty());
        when(pcRepository.findAllById(any()))
                .thenReturn(List.of(pc(7L, "Gorath", 0), pc(8L, "Lyra", 0)));
        SessionRestVote vote = activeVote(new ArrayList<>(List.of(
                ballot(5L, "Gorath", true), ballot(6L, "Lyra", null))),
                Instant.now().plus(Duration.ofSeconds(60)));

        SessionStateView state = sessionService.overrideRestVote(1L, true, dmId);

        assertThat(vote.getStatus()).isEqualTo(RestVoteStatus.PASSED);
        assertThat(state.shortRestOpen()).isTrue();
    }

    @Test
    void overrideRestVote_no_failsTheVote() {
        session.setStatus(SessionStatus.LOBBY);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(participantRepository.findBySessionIdOrderByOrderIndexAsc(1L)).thenReturn(restVoteParty());
        when(pcRepository.findAllById(any()))
                .thenReturn(List.of(pc(7L, "Gorath", 0), pc(8L, "Lyra", 0)));
        SessionRestVote vote = activeVote(new ArrayList<>(List.of(
                ballot(5L, "Gorath", true), ballot(6L, "Lyra", null))),
                Instant.now().plus(Duration.ofSeconds(60)));

        SessionStateView state = sessionService.overrideRestVote(1L, false, dmId);

        assertThat(vote.getStatus()).isEqualTo(RestVoteStatus.FAILED);
        assertThat(state.shortRestOpen()).isFalse();
    }

    @Test
    void overrideRestVote_throws403_forNonDm() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.overrideRestVote(1L, true, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
    }

    @Test
    void toggleShortRest_throws409_whileAVoteIsBeingTallied() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(restVoteRepository.findBySessionIdAndStatus(1L, RestVoteStatus.ACTIVE))
                .thenReturn(Optional.of(new SessionRestVote()));

        assertThatThrownBy(() -> sessionService.toggleShortRest(1L, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
        assertThat(Boolean.TRUE.equals(session.getShortRestOpen())).isFalse();
    }

    @Test
    void startEncounter_cancelsAnActiveRestVote() {
        session.setStatus(SessionStatus.LOBBY);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(participantRepository.findBySessionIdOrderByOrderIndexAsc(1L))
                .thenReturn(combatants(2));
        SessionRestVote vote = new SessionRestVote();
        vote.setId(10L);
        when(restVoteRepository.findBySessionIdAndStatus(1L, RestVoteStatus.ACTIVE))
                .thenReturn(Optional.of(vote));

        sessionService.startEncounter(1L, dmId);

        assertThat(vote.getStatus()).isEqualTo(RestVoteStatus.CANCELLED);
        verify(restVoteRepository).save(vote);
    }

    @Test
    void endSession_purgesRestVoteRows() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(CombatSession.class))).thenAnswer(inv -> inv.getArgument(0));
        when(participantRepository.findBySessionIdOrderByOrderIndexAsc(1L))
                .thenReturn(new ArrayList<>());

        sessionService.endSession(1L, dmId);

        verify(restVoteRepository).deleteBySessionId(1L);
    }

    private static SessionRoll rollRow(Long id, Long participantId, UUID ownerUserId, String displayName,
                                       String breakdown, int grandTotal) {
        SessionRoll r = new SessionRoll();
        r.setId(id);
        r.setSessionId(1L);
        r.setParticipantId(participantId);
        r.setOwnerUserId(ownerUserId);
        r.setDisplayName(displayName);
        r.setBreakdown(breakdown);
        r.setGrandTotal(grandTotal);
        return r;
    }

    // --- followers ride under their owner's row ---

    private static PcFollower follower(Long id, Long pcId, String name, String statBlock) {
        PcFollower f = new PcFollower();
        f.setId(id);
        f.setPcId(pcId);
        f.setName(name);
        f.setStatBlock(statBlock);
        return f;
    }

    /** Seat PC 7 (participant 5) in the session and hand back follower 40 with the given block. */
    private PcFollower seatFollower(String statBlock) {
        PcFollower guard = follower(40L, 7L, "Brannoc", statBlock);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        lenient().when(followerRepository.findByIdForUpdate(40L)).thenReturn(Optional.of(guard));
        lenient().when(participantRepository.findBySessionIdAndPcId(1L, 7L))
                .thenReturn(Optional.of(participant(5L, 7L)));
        return guard;
    }

    @Test
    void snapshot_nestsFollowersUnderTheirSeatedOwner_andEnemiesCarryNone() {
        session.setEnemiesHidden(false);
        arm(session, 1L, hiddenEnemySetup());
        stubPcs();
        when(followerRepository.findByPcIdInOrderByIdAsc(any())).thenReturn(List.of(
                follower(40L, 7L, "Brannoc", "{\"hp\":{\"cur\":9,\"max\":16,\"temp\":2},\"ac\":16,"
                        + "\"clazz\":\"Guard\",\"level\":2,\"conditions\":[\"prone\"],"
                        + "\"portraitTint\":\"gold\",\"portraitInitials\":\"GU\",\"inventory\":[]}"),
                follower(41L, 7L, "Wolf", "{\"hp\":{\"cur\":5,\"max\":5}}")));

        // A player sees exactly what the DM sees: PC rows are never hidden.
        for (UUID viewer : List.of(dmId, playerId)) {
            SessionStateView state = sessionService.getState(1L, viewer, null);

            ParticipantView mine = state.participants().get(0);
            assertThat(mine.followers()).extracting(FollowerCombatantView::followerId)
                    .containsExactly(40L, 41L);
            assertThat(mine.followers().get(0)).isEqualTo(new FollowerCombatantView(
                    40L, 7L, "Brannoc", "Guard", 2, "gold", "GU", 16, 9, 2, 16, List.of("prone")));
            assertThat(state.participants().get(1).npc()).isTrue();
            assertThat(state.participants().get(1).followers()).isEmpty();
            assertThat(state.participants().get(2).followers()).isEmpty(); // a PC with none
        }

        // One batched lookup per snapshot, for both seated PCs at once.
        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Collection<Long>> ids = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(followerRepository, times(2)).findByPcIdInOrderByIdAsc(ids.capture());
        assertThat(ids.getValue()).containsExactlyInAnyOrder(7L, 8L);
    }

    @Test
    void snapshot_keepsFollowersOnEnemyRowsStrippedForPlayers() {
        session.setEnemiesHidden(false);
        session.setEnemyHpHidden(true);
        arm(session, 1L, hiddenEnemySetup());
        stubPcs();

        SessionStateView state = sessionService.getState(1L, playerId, null);

        // withoutHp()/withoutAc() copies must still carry a real (empty) list.
        assertThat(state.participants().get(1).ac()).isNull();
        assertThat(state.participants().get(1).followers()).isNotNull().isEmpty();
    }

    @Test
    void snapshot_toleratesSparseAndMalformedFollowerStatBlocks() {
        arm(session, 1L, hiddenEnemySetup());
        stubPcs();
        when(followerRepository.findByPcIdInOrderByIdAsc(any())).thenReturn(List.of(
                follower(40L, 7L, "Empty", "{}"),
                follower(41L, 7L, "Broken", "{not json"),
                follower(42L, 7L, "Null", null),
                follower(43L, 7L, "NotAnObject", "[1,2]"),
                follower(44L, 7L, "WrongTypes", "{\"hp\":\"lots\",\"ac\":\"17\",\"level\":\"high\","
                        + "\"clazz\":5,\"conditions\":\"prone\"}"),
                follower(45L, 7L, "MixedConditions", "{\"hp\":{\"cur\":3.0},\"conditions\":[\"stunned\",4,null]}")));

        List<FollowerCombatantView> followers =
                sessionService.getState(1L, dmId, null).participants().get(0).followers();

        assertThat(followers).hasSize(6);
        for (FollowerCombatantView bare : followers.subList(0, 4)) {
            assertThat(bare).isEqualTo(new FollowerCombatantView(bare.followerId(), 7L, bare.name(),
                    null, null, null, null, null, null, null, null, List.of()));
        }
        FollowerCombatantView wrong = followers.get(4);
        assertThat(wrong.hpCurrent()).isNull();
        assertThat(wrong.ac()).isEqualTo(17); // a numeric string still reads
        assertThat(wrong.level()).isNull();
        assertThat(wrong.clazz()).isNull();
        assertThat(wrong.conditions()).isEmpty();
        assertThat(followers.get(5).hpCurrent()).isEqualTo(3);
        assertThat(followers.get(5).conditions()).containsExactly("stunned");
    }

    @Test
    void followerDamage_tempAbsorbsFirst_andEveryOtherFieldSurvives() throws Exception {
        PcFollower guard = seatFollower("{\"clazz\":\"Guard\",\"hp\":{\"cur\":9,\"max\":16,\"temp\":3,\"dice\":\"2d8\"},"
                + "\"ac\":16,\"inventory\":[{\"name\":\"Spear\",\"qty\":1}],\"conditions\":[\"prone\"]}");

        SessionStateView state = sessionService.applyFollowerDamage(1L, 40L, 5, dmId);

        Map<String, Object> saved = new ObjectMapper().readValue(guard.getStatBlock(),
                new com.fasterxml.jackson.core.type.TypeReference<>() {});
        assertThat(saved.get("hp")).isEqualTo(Map.of("cur", 7, "max", 16, "temp", 0, "dice", "2d8"));
        assertThat(saved).containsEntry("clazz", "Guard").containsEntry("ac", 16)
                .containsEntry("conditions", List.of("prone"))
                .containsEntry("inventory", List.of(Map.of("name", "Spear", "qty", 1)));
        assertThat(saved.keySet()).containsExactly("clazz", "hp", "ac", "inventory", "conditions");
        verify(followerRepository).save(guard);
        verify(sessionRepository).save(session);
        assertThat(session.getVersion()).isEqualTo(1L);
        assertThat(state.version()).isEqualTo(1L);
    }

    @Test
    void followerDamage_floorsAtZero_andTheFollowerStaysListed() {
        PcFollower guard = seatFollower("{\"hp\":{\"cur\":4,\"max\":16,\"temp\":0}}");
        when(participantRepository.findBySessionIdOrderByOrderIndexAsc(1L))
                .thenReturn(new ArrayList<>(List.of(participant(5L, 7L))));
        when(pcRepository.findAllById(any())).thenReturn(List.of(pc(7L, "Hero", 0)));
        when(followerRepository.findByPcIdInOrderByIdAsc(any())).thenReturn(List.of(guard));

        SessionStateView state = sessionService.applyFollowerDamage(1L, 40L, 99, dmId);

        assertThat(guard.getStatBlock()).contains("\"cur\":0");
        assertThat(state.participants().get(0).followers()).singleElement()
                .satisfies(f -> assertThat(f.hpCurrent()).isZero());
        verify(followerRepository, never()).delete(any());
        verify(pcRepository, never()).save(any()); // the owner's own sheet is untouched
    }

    @Test
    void followerHeal_capsAtMax_andLeavesTempAlone() {
        PcFollower guard = seatFollower("{\"hp\":{\"cur\":9,\"max\":16,\"temp\":4}}");

        sessionService.applyFollowerDamage(1L, 40L, -50, dmId);

        assertThat(guard.getStatBlock()).isEqualTo("{\"hp\":{\"cur\":16,\"max\":16,\"temp\":4}}");
    }

    @Test
    void followerDamage_onABlockWithNoHp_startsFromZero() {
        PcFollower guard = seatFollower("{\"clazz\":\"Guard\"}");

        sessionService.applyFollowerDamage(1L, 40L, -6, dmId);

        assertThat(guard.getStatBlock()).isEqualTo("{\"clazz\":\"Guard\",\"hp\":{\"cur\":6,\"temp\":0}}");
    }

    @Test
    void followerDamage_isDmOnly() {
        PcFollower guard = seatFollower("{\"hp\":{\"cur\":9,\"max\":16}}");

        for (UUID caller : List.of(playerId, strangerId)) {
            assertThatThrownBy(() -> sessionService.applyFollowerDamage(1L, 40L, 3, caller))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(status(e)).isEqualTo(403));
        }
        assertThat(guard.getStatBlock()).isEqualTo("{\"hp\":{\"cur\":9,\"max\":16}}");
        verify(followerRepository, never()).save(any());
        assertThat(session.getVersion()).isZero();
    }

    @Test
    void followerDamage_404sWhenTheFollowersPcIsNotSeatedHere_orTheFollowerIsUnknown() {
        seatFollower("{\"hp\":{\"cur\":9,\"max\":16}}");
        when(participantRepository.findBySessionIdAndPcId(1L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.applyFollowerDamage(1L, 40L, 3, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(404));
        assertThatThrownBy(() -> sessionService.applyFollowerDamage(1L, 99L, 3, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(404));
        verify(followerRepository, never()).save(any());
        assertThat(session.getVersion()).isZero();
    }

    @Test
    void followerDamage_409sOnAnEndedSession_and400sWithoutAnAmount() {
        seatFollower("{\"hp\":{\"cur\":9,\"max\":16}}");

        assertThatThrownBy(() -> sessionService.applyFollowerDamage(1L, 40L, null, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        session.setStatus(SessionStatus.ENDED);
        assertThatThrownBy(() -> sessionService.applyFollowerDamage(1L, 40L, 3, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
        verify(followerRepository, never()).save(any());
    }

    private static int status(Throwable e) {
        return ((ResponseStatusException) e).getStatusCode().value();
    }

    private static SessionParticipant participant(Long id, Long pcId) {
        SessionParticipant p = new SessionParticipant();
        p.setId(id);
        p.setSessionId(1L);
        p.setPcId(pcId);
        return p;
    }

    /** An NPC combatant (no canonical PC) — enough for pointer-walk tests. */
    private static SessionParticipant combatant(Long id, Short initiative, int orderIndex) {
        SessionParticipant p = participant(id, null);
        p.setInitiative(initiative);
        p.setInitRolled(initiative != null);
        p.setOrderIndex((short) orderIndex);
        return p;
    }

    private static SessionParticipant pcCombatant(Long id, Long pcId, Short initiative, int orderIndex) {
        SessionParticipant p = participant(id, pcId);
        p.setInitiative(initiative);
        p.setInitRolled(initiative != null);
        p.setOrderIndex((short) orderIndex);
        return p;
    }

    private static Encounter encounter(Long id, Long campaignId, UUID dmUserId) {
        Encounter e = new Encounter();
        e.setId(id);
        e.setCampaignId(campaignId);
        e.setDmUserId(dmUserId);
        e.setName("Ambush");
        return e;
    }

    private static EncounterCreature creature(String name, Short armorClass, Short hpMax, int quantity) {
        EncounterCreature c = new EncounterCreature();
        c.setName(name);
        c.setArmorClass(armorClass);
        c.setHpMax(hpMax);
        c.setQuantity(quantity);
        return c;
    }

    /** n NPC combatants, ids 1..n, initiative descending, already in order. */
    private static List<SessionParticipant> combatants(int n) {
        List<SessionParticipant> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(combatant((long) (i + 1), (short) (20 - i), i));
        }
        return list;
    }

    /** Wire an ACTIVE session with a turn pointer and an ordered combatant list. */
    private void arm(CombatSession session, Long activeParticipantId, List<SessionParticipant> ordered) {
        session.setCurrentTurnParticipantId(activeParticipantId);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(participantRepository.findBySessionIdOrderByOrderIndexAsc(1L)).thenReturn(ordered);
    }

    /**
     * The canonical visibility fixture: my PC (init 20) → enemy (init 15) →
     * another player's PC (init 10), already in turn order. enemies_hidden is
     * TRUE by default on the session.
     */
    private List<SessionParticipant> hiddenEnemySetup() {
        SessionParticipant mine = pcCombatant(1L, 7L, (short) 20, 0);
        mine.setOwnerUserId(playerId);
        SessionParticipant enemy = combatant(2L, (short) 15, 1);
        enemy.setNpcArmorClass((short) 15);
        SessionParticipant theirs = pcCombatant(3L, 8L, (short) 10, 2);
        theirs.setOwnerUserId(strangerId);
        return new ArrayList<>(List.of(mine, enemy, theirs));
    }

    /** Canonical PCs for {@link #hiddenEnemySetup()}'s PC combatants. */
    private void stubPcs() {
        when(pcRepository.findAllById(any()))
                .thenReturn(List.of(pcWithDex(7L, "Mine", 10), pcWithDex(8L, "Theirs", 10)));
    }

    private static PC pcWithDex(Long id, String name, int dex) {
        PC pc = new PC();
        pc.setId(id);
        pc.setName(name);
        pc.setAbilityDex((short) dex);
        return pc;
    }

    private static PC pc(Long id, String name, int xp) {
        PC pc = new PC();
        pc.setId(id);
        pc.setName(name);
        pc.setXp(xp);
        return pc;
    }
}
