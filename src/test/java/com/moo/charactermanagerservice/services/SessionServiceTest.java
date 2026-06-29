package com.moo.charactermanagerservice.services;

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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the DM XP-award flow ({@link SessionService#awardXp} /
 * {@link SessionService#awardXpToAll}). Pure Mockito — mirrors {@code ShopServiceTest}'s
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

    private static PC pc(Long id, String name, int xp) {
        PC pc = new PC();
        pc.setId(id);
        pc.setName(name);
        pc.setXp(xp);
        return pc;
    }
}
