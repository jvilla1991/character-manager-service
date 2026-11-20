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

    @Test void savesABatchWithOneLogEntry() {
        when(factions.findById(10L)).thenReturn(Optional.of(faction(10, null)));
        when(factions.findById(11L)).thenReturn(Optional.of(faction(11, 10L)));
        when(factions.findById(12L)).thenReturn(Optional.of(faction(12, null)));
        PcRenown maxed = new PcRenown(); maxed.setPcId(1L); maxed.setFactionId(12L); maxed.setScore(100);
        PcRenown eleven = new PcRenown(); eleven.setPcId(1L); eleven.setFactionId(11L); eleven.setScore(-3);
        when(scores.findByPcIdAndFactionId(1L, 10L)).thenReturn(Optional.empty());
        when(scores.findByPcIdAndFactionId(1L, 11L)).thenReturn(Optional.of(eleven));
        when(scores.findByPcIdAndFactionId(1L, 12L)).thenReturn(Optional.of(maxed));

        service.saveAll(1L, List.of(new RenownChange(10L, 5), new RenownChange(11L, -2),
                new RenownChange(12L, 4)), dm);

        verify(scores).save(argThat(s -> s.getFactionId() == 10L && s.getScore() == 5));
        verify(scores).save(argThat(s -> s.getFactionId() == 11L && s.getScore() == -5));
        verify(scores, times(2)).save(any()); // the already-maxed score did not move
        verify(log).log(1L, PcActivityType.DM_EDIT,
                "Renown changed: Guild 10 +5 (now 5); Guild 11 −2 (now -5)", dm);
        verifyNoMoreInteractions(log);
    }

    @Test void listsACampaignsFactionsWithoutScores() {
        when(factions.findByCampaignIdOrderByIdAsc(7L)).thenReturn(List.of(faction(10, null), faction(11, 10L)));
        assertThat(service.factionsOf(7L)).containsExactly(
                new FactionView(10L, null, "Guild 10"), new FactionView(11L, 10L, "Guild 11"));
        verifyNoInteractions(scores);
    }

    @Test void rewardsSeveralCharactersWithOneLogEntryEach() {
        PC second = new PC(); second.setId(2L); second.setName("Mira"); second.setCampaignId(7L);
        pc.setName("Gorath");
        when(pcRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(second));
        when(factions.findById(10L)).thenReturn(Optional.of(faction(10, null)));
        PcRenown nearMax = new PcRenown(); nearMax.setPcId(2L); nearMax.setFactionId(10L); nearMax.setScore(99);
        when(scores.findByPcIdAndFactionId(1L, 10L)).thenReturn(Optional.empty());
        when(scores.findByPcIdAndFactionId(2L, 10L)).thenReturn(Optional.of(nearMax));

        RenownAwardResult result = service.awardToCharacters(7L, 10L, 3, List.of(2L, 1L), dm);

        // locked and answered in id order; the second character is clamped at 100
        assertThat(result.awarded()).containsExactly(
                new RenownAwardResult.Entry(1L, "Gorath", 3, 3),
                new RenownAwardResult.Entry(2L, "Mira", 100, 1));
        var order = inOrder(pcRepository);
        order.verify(pcRepository).findByIdForUpdate(1L);
        order.verify(pcRepository).findByIdForUpdate(2L);
        verify(log).log(1L, PcActivityType.DM_EDIT, "Renown changed: Guild 10 +3 (now 3)", dm);
        verify(log).log(2L, PcActivityType.DM_EDIT, "Renown changed: Guild 10 +1 (now 100)", dm);
        verifyNoMoreInteractions(log);
    }

    @Test void rewardThatCannotMoveAScoreIsReportedButNotSavedOrLogged() {
        when(factions.findById(10L)).thenReturn(Optional.of(faction(10, null)));
        PcRenown floor = new PcRenown(); floor.setPcId(1L); floor.setFactionId(10L); floor.setScore(-100);
        when(scores.findByPcIdAndFactionId(1L, 10L)).thenReturn(Optional.of(floor));

        assertThat(service.awardToCharacters(7L, 10L, -5, List.of(1L), dm).awarded())
                .containsExactly(new RenownAwardResult.Entry(1L, null, -100, 0));
        verify(scores, never()).save(any());
        verifyNoInteractions(log);
    }

    @Test void rewardRejectsBadAmountsForeignFactionsAndForeignCharacters() {
        for (Integer amount : Arrays.asList(null, 0, 201, -201)) {
            assertThatThrownBy(() -> service.awardToCharacters(7L, 10L, amount, List.of(1L), dm))
                    .isInstanceOf(ResponseStatusException.class);
        }
        assertThatThrownBy(() -> service.awardToCharacters(7L, null, 1, List.of(1L), dm))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(factions);

        CampaignFaction other = faction(10, null); other.setCampaignId(8L);
        when(factions.findById(10L)).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.awardToCharacters(7L, 10L, 1, List.of(1L), dm))
                .isInstanceOf(ResponseStatusException.class);

        when(factions.findById(10L)).thenReturn(Optional.of(faction(10, null)));
        pc.setCampaignId(8L); // a character from another campaign
        assertThatThrownBy(() -> service.awardToCharacters(7L, 10L, 1, List.of(1L), dm))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(scores, log);
    }

    @Test void batchRejectsPlayersEmptyAndMalformedInput() {
        assertThatThrownBy(() -> service.saveAll(1L, List.of(new RenownChange(10L, 1)), owner))
                .isInstanceOf(ResponseStatusException.class);
        List<List<RenownChange>> bad = Arrays.asList(null, List.of(),
                List.of(new RenownChange(null, 1)), List.of(new RenownChange(10L, null)),
                List.of(new RenownChange(10L, 201)),
                List.of(new RenownChange(10L, 1), new RenownChange(10L, 2)));
        for (List<RenownChange> changes : bad) {
            assertThatThrownBy(() -> service.saveAll(1L, changes, dm))
                    .isInstanceOf(ResponseStatusException.class);
        }
        verifyNoInteractions(scores, log);
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
