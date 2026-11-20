package com.moo.charactermanagerservice.services;

import com.moo.charactermanagerservice.dto.FactionView;
import com.moo.charactermanagerservice.dto.RenownAwardRequest;
import com.moo.charactermanagerservice.dto.RenownAwardResult;
import com.moo.charactermanagerservice.models.CombatSession;
import com.moo.charactermanagerservice.models.SessionParticipant;
import com.moo.charactermanagerservice.models.SessionStatus;
import com.moo.charactermanagerservice.repositories.CombatSessionRepository;
import com.moo.charactermanagerservice.repositories.SessionParticipantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The DM's session-wide renown reward: who may send it and who receives it. */
@ExtendWith(MockitoExtension.class)
class SessionRenownServiceTest {
    @Mock CombatSessionRepository sessionRepository;
    @Mock SessionParticipantRepository participantRepository;
    @Mock RenownService renownService;
    SessionRenownService service;
    CombatSession session;
    UUID dm = UUID.randomUUID(), player = UUID.randomUUID();

    @BeforeEach
    void setup() {
        service = new SessionRenownService(sessionRepository, participantRepository, renownService);
        session = new CombatSession();
        session.setId(1L);
        session.setCampaignId(7L);
        session.setDmUserId(dm);
        session.setStatus(SessionStatus.LOBBY);
        lenient().when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        // Two seated PCs (participants 5 and 9) with an enemy row between them.
        lenient().when(participantRepository.findBySessionIdOrderByOrderIndexAsc(1L))
                .thenReturn(List.of(participant(5L, 70L), participant(6L, null), participant(9L, 80L)));
    }

    static SessionParticipant participant(Long id, Long pcId) {
        SessionParticipant p = new SessionParticipant();
        p.setId(id);
        p.setSessionId(1L);
        p.setPcId(pcId);
        return p;
    }

    static int status(Throwable e) {
        return ((ResponseStatusException) e).getStatusCode().value();
    }

    @Test
    void dmListsTheSessionsCampaignFactions() {
        List<FactionView> tree = List.of(new FactionView(10L, null, "Kirin Tor"));
        when(renownService.factionsOf(7L)).thenReturn(tree);
        assertThat(service.factions(1L, dm)).isEqualTo(tree);
    }

    @Test
    void rewardsOnlyTheTickedSeatedCharacters() {
        RenownAwardResult done = new RenownAwardResult(List.of());
        when(renownService.awardToCharacters(7L, 10L, 2, List.of(80L), dm)).thenReturn(done);
        assertThat(service.award(1L, new RenownAwardRequest(10L, 2, List.of(9L)), dm)).isSameAs(done);
    }

    @Test
    void noTickListMeansEverySeatedCharacterAndNeverAnEnemy() {
        service.award(1L, new RenownAwardRequest(10L, -1, null), dm);
        verify(renownService).awardToCharacters(7L, 10L, -1, List.of(70L, 80L), dm);
    }

    @Test
    void refusesAnEmptyStaleOrEnemyTick() {
        for (List<Long> ticked : List.of(List.<Long>of(), List.of(5L, 99L), List.of(6L))) {
            assertThatThrownBy(() -> service.award(1L, new RenownAwardRequest(10L, 2, ticked), dm))
                    .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        }
        verifyNoInteractions(renownService);
    }

    @Test
    void onlyTheSessionsDmMayListOrReward() {
        assertThatThrownBy(() -> service.factions(1L, player))
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
        assertThatThrownBy(() -> service.award(1L, new RenownAwardRequest(10L, 2, null), player))
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
        verifyNoInteractions(renownService, participantRepository);
    }

    @Test
    void refusesARewardOnceTheSessionHasEnded() {
        session.setStatus(SessionStatus.ENDED);
        assertThatThrownBy(() -> service.award(1L, new RenownAwardRequest(10L, 2, null), dm))
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
        verifyNoInteractions(renownService);
    }

    @Test
    void unknownSessionIs404AndMissingBodyIs400() {
        when(sessionRepository.findById(2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.factions(2L, dm))
                .satisfies(e -> assertThat(status(e)).isEqualTo(404));
        assertThatThrownBy(() -> service.award(1L, null, dm))
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
    }
}
