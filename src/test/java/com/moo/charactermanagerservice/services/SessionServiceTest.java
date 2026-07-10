package com.moo.charactermanagerservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moo.charactermanagerservice.dto.LogRollRequest;
import com.moo.charactermanagerservice.dto.SessionRollView;
import com.moo.charactermanagerservice.dto.SessionStateView;
import com.moo.charactermanagerservice.dto.XpAwardResult;
import com.moo.charactermanagerservice.models.Campaign;
import com.moo.charactermanagerservice.models.CombatSession;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.models.PcActivityType;
import com.moo.charactermanagerservice.models.SessionLoot;
import com.moo.charactermanagerservice.models.SessionParticipant;
import com.moo.charactermanagerservice.models.SessionRoll;
import com.moo.charactermanagerservice.models.SessionStatus;
import com.moo.charactermanagerservice.models.Encounter;
import com.moo.charactermanagerservice.models.EncounterCreature;
import com.moo.charactermanagerservice.repositories.CampaignRepository;
import com.moo.charactermanagerservice.repositories.CombatSessionRepository;
import com.moo.charactermanagerservice.repositories.EncounterCreatureRepository;
import com.moo.charactermanagerservice.repositories.EncounterRepository;
import com.moo.charactermanagerservice.repositories.PCRepository;
import com.moo.charactermanagerservice.repositories.SessionLootItemRepository;
import com.moo.charactermanagerservice.repositories.SessionLootRepository;
import com.moo.charactermanagerservice.repositories.SessionParticipantRepository;
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
                activityLogService, sessionRollRepository, new ObjectMapper());

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
    void order_breaksInitiativeTies_byDexModifier_thenInsertionOrder() {
        session.setStatus(SessionStatus.LOBBY);
        // All initiative 15. DEX 9 is a -1 modifier (floorDiv, not truncation), so
        // it must sort below DEX 10's +0. The two NPCs (no modifier) fall back to
        // insertion order (id asc).
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

        assertThat(avgPc.getOrderIndex()).isEqualTo((short) 0);   // +0 beats -1
        assertThat(slowPc.getOrderIndex()).isEqualTo((short) 1);
        assertThat(npcEarly.getOrderIndex()).isEqualTo((short) 2); // null mod → after PCs, id asc
        assertThat(npcLate.getOrderIndex()).isEqualTo((short) 3);
        assertThat(session.getCurrentTurnParticipantId()).isEqualTo(2L);
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

        sessionService.addEnemy(1L, "Goblin Boss", (short) 2, (short) 21, dmId);

        SessionParticipant enemy = list.stream()
                .filter(p -> Long.valueOf(99L).equals(p.getId())).findFirst().orElseThrow();
        assertThat(enemy.getPcId()).isNull();
        assertThat(enemy.getDexModifier()).isEqualTo((short) 2);
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
    void addEnemy_throws400_withoutNameOrDexModifier() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.addEnemy(1L, "  ", (short) 2, null, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        assertThatThrownBy(() -> sessionService.addEnemy(1L, "Goblin", null, null, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
    }

    // --- loadEncounter ---

    @Test
    void loadEncounter_appendsCreatures_expandsQuantity_numbersRows_noInitiative() {
        List<SessionParticipant> list = new ArrayList<>();
        list.add(combatant(1L, (short) 15, 0)); // an existing combatant, left untouched
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(encounterRepository.findById(2L)).thenReturn(Optional.of(encounter(2L, 1L, dmId)));
        when(encounterCreatureRepository.findByEncounterIdOrderByIdAsc(2L)).thenReturn(List.of(
                creature("Goblin", (short) 2, (short) 7, 4),
                creature("Goblin Boss", (short) 1, (short) 21, 1)));
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
        assertThat(boss.getDexModifier()).isEqualTo((short) 1);
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
    void enemyDexModifier_breaksInitiativeTie_againstPcModifier() {
        session.setStatus(SessionStatus.LOBBY);
        // Same initiative: enemy +3 must beat PC dex 12 (+1) — modifiers compare
        // against modifiers, never raw scores.
        SessionParticipant pc = pcCombatant(1L, 7L, (short) 15, 0);
        SessionParticipant enemy = combatant(2L, (short) 15, 1);
        enemy.setDexModifier((short) 3);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(participantRepository.findBySessionIdOrderByOrderIndexAsc(1L))
                .thenReturn(new ArrayList<>(List.of(pc, enemy)));
        when(pcRepository.findAllById(any())).thenReturn(List.of(pcWithDex(7L, "Rogue", 12)));

        sessionService.startEncounter(1L, dmId);

        assertThat(enemy.getOrderIndex()).isEqualTo((short) 0);
        assertThat(pc.getOrderIndex()).isEqualTo((short) 1);
    }

    // --- visibility + sound settings ---

    @Test
    void setVisibility_togglesFlag_andBumpsVersion() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(participantRepository.findBySessionIdOrderByOrderIndexAsc(1L))
                .thenReturn(new ArrayList<>());

        SessionStateView state = sessionService.setVisibility(1L, false, dmId);

        assertThat(session.getEnemiesHidden()).isFalse();
        assertThat(state.enemiesHidden()).isFalse();
        assertThat(session.getVersion()).isEqualTo(1L);
    }

    @Test
    void setVisibility_throws403_whenNotDm() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.setVisibility(1L, false, playerId))
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
    void advanceTime_intoNight_seedsSuppliesThenAutoEats_holdingHungerThirst() {
        campaign.setVariantRules("{\"survivalConditions\":true}");
        campaign.setGameTime("{\"year\":\"1\",\"month\":\"1\",\"day\":\"1\",\"timeOfDay\":\"noon\"}");
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        PC member = pc(7L, "Gorath", 0);
        member.setSurvival("{\"hunger\":2,\"thirst\":2,\"fatigue\":2}"); // unseeded → gets supplies
        when(pcRepository.findByCampaignId(1L)).thenReturn(List.of(member));
        when(pcRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(member));

        sessionService.advanceTime(1L, dmId);

        assertThat(campaign.getGameTime()).contains("\"timeOfDay\":\"night\"");
        // fed from the just-seeded supplies → hunger/thirst held, only fatigue climbs
        assertThat(member.getSurvival())
                .contains("\"hunger\":2").contains("\"thirst\":2").contains("\"fatigue\":3")
                .contains("\"seeded\":true");
        // Starting kit seeded (1 box, 1 skin, 5 rations, 5 water); 1 ration and
        // 1 water eaten/drunk this night → 4 charges left in each container.
        assertThat(member.getInventory())
                .contains("\"catalogKey\":\"rations\"").contains("\"catalogKey\":\"waterskin\"")
                .contains("\"catalogKey\":\"ration-box\"").contains("\"catalogKey\":\"water\"")
                .contains("\"qty\":4");
    }

    @Test
    void advanceTime_whenSuppliesRunOut_raisesHungerAndThirst() {
        campaign.setVariantRules("{\"survivalConditions\":true}");
        campaign.setGameTime("{\"year\":\"1\",\"month\":\"1\",\"day\":\"1\",\"timeOfDay\":\"noon\"}");
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        PC member = pc(7L, "Gorath", 0);
        // already seeded, but the boxes are empty — nothing left to eat
        member.setSurvival("{\"hunger\":2,\"thirst\":2,\"fatigue\":2,\"seeded\":true}");
        member.setInventory("[]");
        when(pcRepository.findByCampaignId(1L)).thenReturn(List.of(member));
        when(pcRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(member));

        sessionService.advanceTime(1L, dmId); // → night

        assertThat(member.getSurvival())
                .contains("\"hunger\":3").contains("\"thirst\":3").contains("\"fatigue\":3");
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

    // --- longRest ---

    private void seatForLongRest(PC pc) {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(participantRepository.findBySessionIdOrderByOrderIndexAsc(1L))
                .thenReturn(List.of(participant(50L, 7L)));
        when(pcRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(pc));
        when(pcRepository.findAllById(any())).thenReturn(List.of(pc)); // buildState's batch load
    }

    @Test
    void longRest_restoresSlots_andShedsFatigue_undisturbed() {
        campaign.setVariantRules("{\"survivalConditions\":true}");
        PC pc = pc(7L, "Gorath", 0);
        pc.setSpellSlots("{\"1\":{\"max\":4,\"used\":3},\"2\":{\"max\":2,\"used\":2}}");
        pc.setSurvival("{\"hunger\":2,\"thirst\":2,\"fatigue\":5}");
        seatForLongRest(pc);

        sessionService.longRest(1L, true, dmId); // undisturbed → -3 fatigue

        assertThat(pc.getSpellSlots()).contains("\"used\":0").doesNotContain("\"used\":3");
        assertThat(pc.getSurvival()).contains("\"fatigue\":2"); // 5 - 3
        verify(sessionRepository).save(session); // version bumped
        verify(activityLogService).log(7L, PcActivityType.LONG_REST, "Completed a long rest", dmId);
    }

    @Test
    void longRest_disturbed_shedsOnlyOneFatigue() {
        campaign.setVariantRules("{\"survivalConditions\":true}");
        PC pc = pc(7L, "Gorath", 0);
        pc.setSurvival("{\"hunger\":2,\"thirst\":2,\"fatigue\":5}");
        seatForLongRest(pc);

        sessionService.longRest(1L, false, dmId);

        assertThat(pc.getSurvival()).contains("\"fatigue\":4"); // 5 - 1
    }

    @Test
    void longRest_nonSurvivalCampaign_restoresSlotsOnly() {
        PC pc = pc(7L, "Gorath", 0); // campaign has no variant rules
        pc.setSpellSlots("{\"1\":{\"max\":4,\"used\":4}}");
        seatForLongRest(pc);

        sessionService.longRest(1L, true, dmId);

        assertThat(pc.getSpellSlots()).contains("\"used\":0");
        assertThat(pc.getSurvival()).isNull(); // no fatigue to shed
    }

    @Test
    void longRest_throws403_forNonDm() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.longRest(1L, true, strangerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
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

    private static EncounterCreature creature(String name, Short dexModifier, Short hpMax, int quantity) {
        EncounterCreature c = new EncounterCreature();
        c.setName(name);
        c.setDexModifier(dexModifier);
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
        enemy.setDexModifier((short) 2);
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
