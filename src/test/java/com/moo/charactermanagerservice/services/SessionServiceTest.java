package com.moo.charactermanagerservice.services;

import com.moo.charactermanagerservice.dto.SessionStateView;
import com.moo.charactermanagerservice.dto.XpAwardResult;
import com.moo.charactermanagerservice.models.CombatSession;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.models.SessionParticipant;
import com.moo.charactermanagerservice.models.SessionStatus;
import com.moo.charactermanagerservice.repositories.CombatSessionRepository;
import com.moo.charactermanagerservice.repositories.PCRepository;
import com.moo.charactermanagerservice.repositories.SessionParticipantRepository;
import com.moo.charactermanagerservice.repositories.SessionShopAttendeeRepository;
import com.moo.charactermanagerservice.repositories.SessionShopRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
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
    @Mock private PCRepository pcRepository;
    @Mock private PCService pcService;
    @Mock private CampaignService campaignService;

    private SessionService sessionService;

    private UUID dmId;
    private UUID strangerId;
    private CombatSession session;

    @BeforeEach
    void setUp() {
        sessionService = new SessionService(sessionRepository, participantRepository, shopRepository,
                shopAttendeeRepository, pcRepository, pcService, campaignService);

        dmId = UUID.randomUUID();
        strangerId = UUID.randomUUID();

        session = new CombatSession();
        session.setId(1L);
        session.setCampaignId(1L);
        session.setDmUserId(dmId);
        session.setStatus(SessionStatus.ACTIVE);
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
    void advance_throws403_whenNotDm() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

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

    // --- helpers ---

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
