package com.moo.charactermanagerservice.services;

import com.moo.charactermanagerservice.dto.HpMode;
import com.moo.charactermanagerservice.dto.LevelUpPreview;
import com.moo.charactermanagerservice.dto.LevelUpRequest;
import com.moo.charactermanagerservice.exceptions.PCNotFoundException;
import com.moo.charactermanagerservice.models.Campaign;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.models.PcActivityLog;
import com.moo.charactermanagerservice.models.PcActivityType;
import com.moo.charactermanagerservice.models.PcNote;
import com.moo.charactermanagerservice.repositories.CampaignRepository;
import com.moo.charactermanagerservice.repositories.PcNoteRepository;
import com.moo.charactermanagerservice.repositories.PCRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PCServiceTest {

    @Mock
    private PCRepository pcRepository;

    @Mock
    private LevelUpService levelUpService;

    @Mock
    private CampaignRepository campaignRepository;

    @Mock
    private PcNoteRepository pcNoteRepository;

    @Mock
    private PcActivityLogService activityLogService;

    @InjectMocks
    private PCService pcService;

    private UUID ownerId;
    private UUID strangerId;
    private UUID dmId;
    private PC pc;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        strangerId = UUID.randomUUID();
        dmId = UUID.randomUUID();

        pc = new PC();
        pc.setId(1L);
        pc.setName("Aelindra");
        pc.setClazz("Wizard");
        pc.setUserId(ownerId);
    }

    /** A campaign owned by {@link #dmId}, used by the DM-authorization tests. */
    private Campaign campaignOwnedByDm() {
        Campaign campaign = new Campaign();
        campaign.setId(7L);
        campaign.setDmUserId(dmId);
        return campaign;
    }

    // --- addPC ---

    @Test
    void addPC_forcesLevelToOne() {
        pc.setLevel((short) 5);
        when(pcRepository.saveAndFlush(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        PC saved = pcService.addPC(pc);

        assertThat(saved.getLevel()).isEqualTo((short) 1);
    }

    @Test
    void addPC_persistsAndReturns() {
        when(pcRepository.saveAndFlush(pc)).thenReturn(pc);

        PC result = pcService.addPC(pc);

        assertThat(result).isSameAs(pc);
        verify(pcRepository).saveAndFlush(pc);
    }

    // --- findPCById ---

    @Test
    void findPCById_returnsPC_whenFound() {
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));

        PC result = pcService.findPCById(1L);

        assertThat(result).isSameAs(pc);
    }

    @Test
    void findPCById_throws_whenNotFound() {
        when(pcRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pcService.findPCById(99L))
                .isInstanceOf(PCNotFoundException.class);
    }

    // --- findPCByIdForUser ---

    @Test
    void findPCByIdForUser_returnsPC_whenOwner() {
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));

        PC result = pcService.findPCByIdForUser(1L, ownerId);

        assertThat(result).isSameAs(pc);
    }

    @Test
    void findPCByIdForUser_throws403_whenNotOwner() {
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));

        assertThatThrownBy(() -> pcService.findPCByIdForUser(1L, strangerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(403));
    }

    // --- updatePC ---

    @Test
    void updatePC_savesAndReturns_whenOwner() {
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(pc)).thenReturn(pc);

        PC result = pcService.updatePC(pc, ownerId);

        assertThat(result).isSameAs(pc);
        verify(pcRepository).save(pc);
    }

    @Test
    void updatePC_throws403_whenNotOwner() {
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));

        assertThatThrownBy(() -> pcService.updatePC(pc, strangerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(403));

        verify(pcRepository, never()).save(any());
    }

    // --- survival preservation (an update omitting it must not wipe it) ---

    @Test
    void updatePC_preservesSurvival_whenTheBodyOmitsIt() {
        pc.setSurvival("{\"hunger\":3,\"thirst\":1,\"fatigue\":2}");
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        PC incoming = new PC();
        incoming.setId(1L);
        incoming.setName("Renamed"); // survival null — a stale client's payload

        PC result = pcService.updatePC(incoming, ownerId);

        assertThat(result.getSurvival()).isEqualTo("{\"hunger\":3,\"thirst\":1,\"fatigue\":2}");
    }

    @Test
    void updatePC_acceptsAnExplicitSurvival() {
        pc.setSurvival("{\"hunger\":3,\"thirst\":1,\"fatigue\":2}");
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        PC incoming = new PC();
        incoming.setId(1L);
        incoming.setSurvival("{\"hunger\":2,\"thirst\":1,\"fatigue\":2}"); // player ate a ration

        PC result = pcService.updatePC(incoming, ownerId);

        assertThat(result.getSurvival()).isEqualTo("{\"hunger\":2,\"thirst\":1,\"fatigue\":2}");
    }

    @Test
    void updatePCAsDm_preservesSurvival_whenTheBodyOmitsIt() {
        PC existing = new PC();
        existing.setId(1L);
        existing.setUserId(ownerId);
        existing.setCampaignId(7L);
        existing.setSurvival("{\"hunger\":5,\"thirst\":0,\"fatigue\":0}");
        when(pcRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(campaignRepository.findById(7L)).thenReturn(Optional.of(campaignOwnedByDm()));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        PC incoming = new PC();
        incoming.setId(1L);

        PC result = pcService.updatePCAsDm(incoming, dmId);

        assertThat(result.getSurvival()).isEqualTo("{\"hunger\":5,\"thirst\":0,\"fatigue\":0}");
    }

    // --- per-character notes ---

    @Test
    void addNote_savesWithCampaignSnapshotAndAuthor() {
        pc.setCampaignId(7L);
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(pcNoteRepository.save(any(PcNote.class))).thenAnswer(inv -> inv.getArgument(0));

        PcNote note = pcService.addNote(1L, "  We spared the goblin chief.  ", 42L, ownerId);

        assertThat(note.getPcId()).isEqualTo(1L);
        assertThat(note.getCampaignId()).isEqualTo(7L);
        assertThat(note.getSessionId()).isEqualTo(42L);
        assertThat(note.getAuthorUserId()).isEqualTo(ownerId);
        assertThat(note.getBody()).isEqualTo("We spared the goblin chief.");
    }

    @Test
    void addNote_throws403_forNonOwner_and400_forBlankBody() {
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));

        assertThatThrownBy(() -> pcService.addNote(1L, "note", null, strangerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(403));
        assertThatThrownBy(() -> pcService.addNote(1L, "   ", null, ownerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(400));
        verify(pcNoteRepository, never()).save(any());
    }

    @Test
    void notesFor_readableByOwnerAndCampaignDm_deniedToStrangers() {
        pc.setCampaignId(7L);
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(campaignRepository.findById(7L)).thenReturn(Optional.of(campaignOwnedByDm()));
        when(pcNoteRepository.findByPcIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

        assertThat(pcService.notesFor(1L, ownerId)).isEmpty();  // owner reads
        assertThat(pcService.notesFor(1L, dmId)).isEmpty();     // the campaign's DM reads

        assertThatThrownBy(() -> pcService.notesFor(1L, strangerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(403));
    }

    // --- per-character activity log ---

    @Test
    void activityLogFor_readableByOwnerAndCampaignDm_deniedToStrangers() {
        pc.setCampaignId(7L);
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(campaignRepository.findById(7L)).thenReturn(Optional.of(campaignOwnedByDm()));
        when(activityLogService.latestFor(1L)).thenReturn(List.of());

        assertThat(pcService.activityLogFor(1L, ownerId)).isEmpty();  // owner reads
        assertThat(pcService.activityLogFor(1L, dmId)).isEmpty();     // the campaign's DM reads

        assertThatThrownBy(() -> pcService.activityLogFor(1L, strangerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(403));
    }

    @Test
    void activityLogFor_returnsTheLatestEntries() {
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        PcActivityLog entry = new PcActivityLog();
        entry.setPcId(1L);
        entry.setActionType(PcActivityType.LEVEL_UP);
        entry.setDescription("Leveled up to 2");
        when(activityLogService.latestFor(1L)).thenReturn(List.of(entry));

        assertThat(pcService.activityLogFor(1L, ownerId)).containsExactly(entry);
    }

    // --- findPCByIdForDm ---

    @Test
    void findPCByIdForDm_returnsPC_whenCallerRunsTheCampaign() {
        pc.setCampaignId(7L);
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(campaignRepository.findById(7L)).thenReturn(Optional.of(campaignOwnedByDm()));

        PC result = pcService.findPCByIdForDm(1L, dmId);

        assertThat(result).isSameAs(pc);
    }

    @Test
    void findPCByIdForDm_throws403_whenCallerIsNotTheDm() {
        pc.setCampaignId(7L);
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(campaignRepository.findById(7L)).thenReturn(Optional.of(campaignOwnedByDm()));

        assertThatThrownBy(() -> pcService.findPCByIdForDm(1L, strangerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(403));
    }

    @Test
    void findPCByIdForDm_throws403_whenPcInNoCampaign() {
        pc.setCampaignId(null);
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));

        assertThatThrownBy(() -> pcService.findPCByIdForDm(1L, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(403));

        verify(campaignRepository, never()).findById(any());
    }

    // --- updatePCAsDm ---

    @Test
    void updatePCAsDm_savesAndReturns_whenCallerRunsTheCampaign() {
        PC existing = new PC();
        existing.setId(1L);
        existing.setUserId(ownerId);
        existing.setCampaignId(7L);
        when(pcRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(campaignRepository.findById(7L)).thenReturn(Optional.of(campaignOwnedByDm()));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        // Incoming body tries to reassign owner and campaign — both must be ignored.
        PC incoming = new PC();
        incoming.setId(1L);
        incoming.setUserId(strangerId);
        incoming.setCampaignId(999L);
        incoming.setHpCurrent((short) 12);

        PC result = pcService.updatePCAsDm(incoming, dmId);

        assertThat(result.getHpCurrent()).isEqualTo((short) 12);
        assertThat(result.getUserId()).isEqualTo(ownerId);     // owner preserved
        assertThat(result.getCampaignId()).isEqualTo(7L);      // campaign preserved
        verify(pcRepository).save(incoming);
    }

    @Test
    void updatePCAsDm_throws403_whenCallerIsNotTheDm() {
        PC existing = new PC();
        existing.setId(1L);
        existing.setUserId(ownerId);
        existing.setCampaignId(7L);
        when(pcRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(campaignRepository.findById(7L)).thenReturn(Optional.of(campaignOwnedByDm()));

        PC incoming = new PC();
        incoming.setId(1L);

        assertThatThrownBy(() -> pcService.updatePCAsDm(incoming, strangerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(403));

        verify(pcRepository, never()).save(any());
    }

    /**
     * The critical ordering regression test: {@code pcRepository.save(incoming)}
     * merges onto the same managed {@code existing} instance JPA loaded, so by
     * the time {@code logDmEdit} would read {@code existing} after save() it
     * would see the NEW values on both sides and report zero changes. PCService
     * must snapshot the "before" state before save() runs — this asserts the
     * diff sees the ORIGINAL pre-save AC, not the post-save one.
     */
    @Test
    void updatePCAsDm_diffsThePreSaveSnapshot_notThePostSaveClobberedInstance() {
        PC existing = new PC();
        existing.setId(1L);
        existing.setUserId(ownerId);
        existing.setCampaignId(7L);
        existing.setAc((short) 15);
        when(pcRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(campaignRepository.findById(7L)).thenReturn(Optional.of(campaignOwnedByDm()));
        // Mirrors real JPA merge behavior: save(incoming) mutates `existing` in place.
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> {
            PC incomingArg = inv.getArgument(0);
            existing.setAc(incomingArg.getAc());
            return incomingArg;
        });

        PC incoming = new PC();
        incoming.setId(1L);
        incoming.setAc((short) 18);

        pcService.updatePCAsDm(incoming, dmId);

        ArgumentCaptor<PC> beforeCaptor = ArgumentCaptor.forClass(PC.class);
        ArgumentCaptor<PC> afterCaptor = ArgumentCaptor.forClass(PC.class);
        verify(activityLogService).logDmEdit(beforeCaptor.capture(), afterCaptor.capture(), eq(dmId));
        assertThat(beforeCaptor.getValue().getAc()).isEqualTo((short) 15); // pre-save AC, not clobbered
        assertThat(afterCaptor.getValue().getAc()).isEqualTo((short) 18);
    }

    // --- levelUpPC ---

    @Test
    void levelUpPC_appliesRulesAndSaves_whenOwner() {
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(pc)).thenReturn(pc);

        PC result = pcService.levelUpPC(1L, ownerId, null);

        assertThat(result).isSameAs(pc);
        verify(levelUpService).applyLevelUp(pc, null, null, null, null, HpMode.AVERAGE);
        verify(pcRepository).save(pc);
    }

    @Test
    void levelUpPC_logsTheNewLevel() {
        pc.setLevel((short) 4);
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(pc)).thenReturn(pc);

        pcService.levelUpPC(1L, ownerId, null);

        verify(activityLogService).log(1L, PcActivityType.LEVEL_UP, "Leveled up to 4", ownerId);
    }

    @Test
    void levelUpPC_passesChoicesToRulesEngine() {
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(pc)).thenReturn(pc);

        List<Map<String, Object>> spells = List.of(Map.of("lvl", 0, "name", "Light"));
        pcService.levelUpPC(1L, ownerId, new LevelUpRequest("Life Domain", Map.of("STR", 2), "Sentinel", spells));

        verify(levelUpService).applyLevelUp(pc, "Life Domain", Map.of("STR", 2), "Sentinel", spells, HpMode.AVERAGE);
    }

    @Test
    void levelUpPC_forwardsRollHpMode() {
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(pc)).thenReturn(pc);

        pcService.levelUpPC(1L, ownerId, new LevelUpRequest(null, null, null, null, HpMode.ROLL));

        verify(levelUpService).applyLevelUp(pc, null, null, null, null, HpMode.ROLL);
    }

    @Test
    void levelUpPC_nullHpMode_defaultsToAverage() {
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(pc)).thenReturn(pc);

        // hpMode explicitly null in the request -> service coalesces to AVERAGE.
        pcService.levelUpPC(1L, ownerId, new LevelUpRequest(null, null, null, null, null));

        verify(levelUpService).applyLevelUp(pc, null, null, null, null, HpMode.AVERAGE);
    }

    @Test
    void levelUpPC_throws403_whenNotOwner() {
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));

        assertThatThrownBy(() -> pcService.levelUpPC(1L, strangerId, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(403));

        verify(levelUpService, never()).applyLevelUp(any(), any(), any(), any(), any());
        verify(pcRepository, never()).save(any());
    }

    // --- previewLevelUp ---

    @Test
    void previewLevelUp_returnsPreview_whenOwner() {
        LevelUpPreview preview = new LevelUpPreview(4, 5, 8, 2, 7, 39, 2, 3, Map.of(), Map.of(), false, List.of(), false, List.of(), List.of(), 0, 0, 0, 0);
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(levelUpService.preview(pc)).thenReturn(preview);

        LevelUpPreview result = pcService.previewLevelUp(1L, ownerId);

        assertThat(result).isSameAs(preview);
    }

    @Test
    void previewLevelUp_throws403_whenNotOwner() {
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));

        assertThatThrownBy(() -> pcService.previewLevelUp(1L, strangerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(403));

        verify(levelUpService, never()).preview(any());
    }

    // --- deletePC ---

    @Test
    void deletePC_deletesById_whenOwner() {
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));

        pcService.deletePC(1L, ownerId);

        verify(pcRepository).deleteById(1L);
    }

    @Test
    void deletePC_throws403_whenNotOwner() {
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));

        assertThatThrownBy(() -> pcService.deletePC(1L, strangerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(403));

        verify(pcRepository, never()).deleteById(any());
    }

    // --- findAllPCsForUser ---

    @Test
    void findAllPCsForUser_returnsFilteredList() {
        when(pcRepository.findByUserId(ownerId)).thenReturn(List.of(pc));

        List<PC> result = pcService.findAllPCsForUser(ownerId);

        assertThat(result).containsExactly(pc);
    }
}
