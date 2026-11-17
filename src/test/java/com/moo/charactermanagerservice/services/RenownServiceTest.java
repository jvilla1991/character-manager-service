package com.moo.charactermanagerservice.services;

import com.moo.charactermanagerservice.dto.*;
import com.moo.charactermanagerservice.models.*;
import com.moo.charactermanagerservice.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class RenownServiceTest {
    @Mock PCService pcService;
    @Mock PCRepository pcRepository;
    @Mock CampaignFactionRepository factions;
    @Mock PcRenownRepository scores;
    @Mock PcActivityLogService log;
    RenownService service;
    PC pc;
    UUID owner = UUID.randomUUID(), dm = UUID.randomUUID(), stranger = UUID.randomUUID();

    @BeforeEach
    void setup() {
        service = new RenownService(pcService, pcRepository, factions, scores, log);
        pc = new PC(); pc.setId(1L); pc.setUserId(owner); pc.setCampaignId(7L);
        lenient().when(pcService.findPCById(1L)).thenReturn(pc);
        lenient().when(pcService.findPCByIdForDm(1L, dm)).thenReturn(pc);
        lenient().when(pcService.findPCByIdForDm(1L, owner))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));
        lenient().when(pcService.findPCByIdForDm(1L, stranger))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));
        lenient().when(pcRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(pc));
    }

    CampaignFaction faction(long id, Long parentId) {
        CampaignFaction f = new CampaignFaction(); f.setId(id); f.setCampaignId(7L);
        f.setParentId(parentId); f.setName("Guild " + id); return f;
    }

    @Test void ownerSeesCampaignFactionsAtZeroAndOwnScores() {
        when(factions.findByCampaignIdOrderByIdAsc(7L)).thenReturn(List.of(faction(10, null), faction(11, 10L)));
        PcRenown value = new PcRenown(); value.setFactionId(11L); value.setScore(10);
        when(scores.findByPcId(1L)).thenReturn(List.of(value));
        assertThat(service.list(1L, owner)).containsExactly(
                new RenownView(10L, null, "Guild 10", 0), new RenownView(11L, 10L, "Guild 11", 10));
        verify(pcService, never()).findPCByIdForDm(any(), any());
    }

    @Test void noCampaignHasNoFactions() {
        pc.setCampaignId(null);
        assertThat(service.list(1L, owner)).isEmpty();
        verifyNoInteractions(factions, scores);
    }

    @Test void strangerCannotRead() {
        assertThatThrownBy(() -> service.list(1L, stranger)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(factions, scores);
    }

    @Test void dmCanRead() {
        assertThat(service.list(1L, dm)).isEmpty();
        verify(pcService).findPCByIdForDm(1L, dm);
    }

    @Test void playerCannotCreateOrAdjust() {
        assertThatThrownBy(() -> service.create(1L, new FactionRequest("Guild", null), owner))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.adjust(1L, 10L, new RenownAdjustment(1), owner))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(factions, scores, log);
    }

    @Test void createSharedFactionStartsAtZero() {
        when(factions.save(any())).thenAnswer(i -> { CampaignFaction f = i.getArgument(0); f.setId(10L); return f; });
        assertThat(service.create(1L, new FactionRequest("  Guild  ", null), dm))
                .isEqualTo(new RenownView(10L, null, "Guild", 0));
        verifyNoInteractions(scores);
    }

    @Test void createSubfaction() {
        when(factions.findById(10L)).thenReturn(Optional.of(faction(10, null)));
        when(factions.save(any())).thenAnswer(i -> i.getArgument(0));
        assertThat(service.create(1L, new FactionRequest("Branch", 10L), dm).parentId()).isEqualTo(10L);
    }

    @Test void rejectsThirdLevelAndForeignCampaignParent() {
        CampaignFaction parent = faction(11, 10L);
        when(factions.findById(11L)).thenReturn(Optional.of(parent));
        assertThatThrownBy(() -> service.create(1L, new FactionRequest("Branch", 11L), dm))
                .isInstanceOf(ResponseStatusException.class);
        parent.setParentId(null); parent.setCampaignId(8L);
        assertThatThrownBy(() -> service.create(1L, new FactionRequest("Branch", 11L), dm))
                .isInstanceOf(ResponseStatusException.class);
        verify(factions, never()).save(any());
    }

    @Test void rejectsInvalidNames() {
        for (String name : Arrays.asList(null, "", "   ", "a".repeat(101))) {
            assertThatThrownBy(() -> service.create(1L, new FactionRequest(name, null), dm))
                    .isInstanceOf(ResponseStatusException.class);
        }
        verifyNoInteractions(factions);
    }

    @ParameterizedTest
    @CsvSource({"0,1,1", "0,-1,-1", "99,1,100", "100,1,100", "-99,-1,-100", "-100,-1,-100", "-100,1,-99", "100,-1,99"})
    void adjustsIndependentScoreAndClamps(int initial, int delta, int expected) {
        when(factions.findById(10L)).thenReturn(Optional.of(faction(10, null)));
        PcRenown score = new PcRenown(); score.setPcId(1L); score.setFactionId(10L); score.setScore(initial);
        when(scores.findByPcIdAndFactionId(1L, 10L)).thenReturn(Optional.of(score));
        assertThat(service.adjust(1L, 10L, new RenownAdjustment(delta), dm).score()).isEqualTo(expected);
        var order = inOrder(pcRepository, scores);
        order.verify(pcRepository).findByIdForUpdate(1L);
        order.verify(scores).findByPcIdAndFactionId(1L, 10L);
        if (expected == initial) verifyNoInteractions(log);
        else verify(scores).save(score);
    }

    @Test void firstAdjustmentCreatesOnlyThisCharactersScore() {
        when(factions.findById(10L)).thenReturn(Optional.of(faction(10, null)));
        assertThat(service.adjust(1L, 10L, new RenownAdjustment(1), dm).score()).isEqualTo(1);
        verify(scores).save(argThat(s -> s.getPcId() == 1L && s.getFactionId() == 10L && s.getScore() == 1));
    }

    @Test void rejectsForeignFactionAdjustment() {
        CampaignFaction other = faction(10, null); other.setCampaignId(8L);
        when(factions.findById(10L)).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.adjust(1L, 10L, new RenownAdjustment(1), dm))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(scores, log);
    }

    @Test void rejectsInvalidDeltas() {
        for (Integer delta : Arrays.asList(null, 0, 2, -2, Integer.MAX_VALUE)) {
            assertThatThrownBy(() -> service.adjust(1L, 10L, new RenownAdjustment(delta), dm))
                    .isInstanceOf(ResponseStatusException.class);
        }
        verifyNoInteractions(factions, scores, log);
    }
}
