package com.moo.charactermanagerservice.services;

import com.moo.charactermanagerservice.dto.HpMode;
import com.moo.charactermanagerservice.dto.LevelUpPreview;
import com.moo.charactermanagerservice.dto.LevelUpRequest;
import com.moo.charactermanagerservice.exceptions.PCNotFoundException;
import com.moo.charactermanagerservice.models.Campaign;
import com.moo.charactermanagerservice.models.CombatSession;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.models.PcActivityLog;
import com.moo.charactermanagerservice.models.PcActivityType;
import com.moo.charactermanagerservice.models.PcNote;
import com.moo.charactermanagerservice.models.SessionParticipant;
import com.moo.charactermanagerservice.models.SessionStatus;
import com.moo.charactermanagerservice.repositories.CampaignRepository;
import com.moo.charactermanagerservice.repositories.CombatSessionRepository;
import com.moo.charactermanagerservice.repositories.PcNoteRepository;
import com.moo.charactermanagerservice.repositories.PCRepository;
import com.moo.charactermanagerservice.repositories.SessionParticipantRepository;
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
import static org.mockito.ArgumentMatchers.isNull;
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

    @Mock
    private CombatSessionRepository sessionRepository;

    @Mock
    private SessionParticipantRepository participantRepository;

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

    @Test
    void updatePC_convertsLegacyCoinsFromAnOlderClient() {
        // A tab opened before the three-coin release still sends ep/pp keys.
        pc.setCoins("{\"cp\":1,\"sp\":2,\"ep\":2,\"gp\":3,\"pp\":4}");
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(pc)).thenReturn(pc);

        pcService.updatePC(pc, ownerId);

        assertThat(pc.getCoins()).isEqualTo("{\"cp\":1,\"sp\":12,\"gp\":43}");
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

        PC result = pcService.updatePCAsDm(incoming, null, dmId);

        assertThat(result.getSurvival()).isEqualTo("{\"hunger\":5,\"thirst\":0,\"fatigue\":0}");
    }

    // --- exhaustion preservation (an update carrying null must not wipe it) ---

    @Test
    void updatePC_preservesExhaustion_whenTheBodyOmitsIt() {
        pc.setExhaustion((short) 3);
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        PC incoming = new PC();
        incoming.setId(1L);
        incoming.setName("Renamed"); // exhaustion null — a payload built without the field

        PC result = pcService.updatePC(incoming, ownerId);

        assertThat(result.getExhaustion()).isEqualTo((short) 3);
    }

    @Test
    void updatePC_acceptsAnExplicitExhaustion_includingZero() {
        pc.setExhaustion((short) 3);
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        PC incoming = new PC();
        incoming.setId(1L);
        incoming.setExhaustion((short) 0); // a long rest walked it back down

        PC result = pcService.updatePC(incoming, ownerId);

        assertThat(result.getExhaustion()).isEqualTo((short) 0);
    }

    @Test
    void updatePCAsDm_preservesExhaustion_whenTheBodyOmitsIt() {
        PC existing = new PC();
        existing.setId(1L);
        existing.setUserId(ownerId);
        existing.setCampaignId(7L);
        existing.setExhaustion((short) 5);
        when(pcRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(campaignRepository.findById(7L)).thenReturn(Optional.of(campaignOwnedByDm()));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        PC incoming = new PC();
        incoming.setId(1L);

        PC result = pcService.updatePCAsDm(incoming, null, dmId);

        assertThat(result.getExhaustion()).isEqualTo((short) 5);
    }

    // --- V38 server-owned columns (hit dice + inspiration meter) ---

    @Test
    void updatePC_neverChangesHitDiceOrInspiration_evenWhenTheBodyCarriesValues() {
        pc.setHitDiceUsed((short) 3);
        pc.setInspirationPips((short) 2);
        pc.setHeroicInspiration(true);
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        PC incoming = new PC();
        incoming.setId(1L);
        incoming.setHitDiceUsed((short) 0);      // a stale/tampered echo
        incoming.setInspirationPips((short) 4);
        incoming.setHeroicInspiration(false);

        PC result = pcService.updatePC(incoming, ownerId);

        assertThat(result.getHitDiceUsed()).isEqualTo((short) 3);
        assertThat(result.getInspirationPips()).isEqualTo((short) 2);
        assertThat(result.getHeroicInspiration()).isTrue();
    }

    // --- inspiration meter (set pips / use heroic) ---

    @Test
    void setInspirationPips_raisesMeter_andLogs() {
        pc.setCampaignId(7L);
        pc.setInspirationPips((short) 1);
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(campaignRepository.findById(7L)).thenReturn(Optional.of(campaignOwnedByDm()));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        PC result = pcService.setInspirationPips(1L, 2, dmId);

        assertThat(result.getInspirationPips()).isEqualTo((short) 2);
        assertThat(result.getHeroicInspiration()).isNotEqualTo(Boolean.TRUE);
        verify(activityLogService).log(1L, PcActivityType.INSPIRATION,
                "DM set inspiration to 2/5", dmId);
    }

    /** Clicking a filled pip lowers the meter — the DM can take pips back. */
    @Test
    void setInspirationPips_lowersMeter() {
        pc.setCampaignId(7L);
        pc.setInspirationPips((short) 4);
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(campaignRepository.findById(7L)).thenReturn(Optional.of(campaignOwnedByDm()));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        PC result = pcService.setInspirationPips(1L, 1, dmId);

        assertThat(result.getInspirationPips()).isEqualTo((short) 1);
        assertThat(result.getHeroicInspiration()).isNotEqualTo(Boolean.TRUE);
        verify(activityLogService).log(1L, PcActivityType.INSPIRATION,
                "DM set inspiration to 1/5", dmId);
    }

    /** Zero is a legal target — clicking the only lit pip clears the meter. */
    @Test
    void setInspirationPips_clearsMeterToZero() {
        pc.setCampaignId(7L);
        pc.setInspirationPips((short) 1);
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(campaignRepository.findById(7L)).thenReturn(Optional.of(campaignOwnedByDm()));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        PC result = pcService.setInspirationPips(1L, 0, dmId);

        assertThat(result.getInspirationPips()).isEqualTo((short) 0);
        verify(activityLogService).log(1L, PcActivityType.INSPIRATION,
                "DM set inspiration to 0/5", dmId);
    }

    @Test
    void setInspirationPips_fullMeter_grantsHeroic_andResetsMeter() {
        pc.setCampaignId(7L);
        pc.setInspirationPips((short) 2);
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(campaignRepository.findById(7L)).thenReturn(Optional.of(campaignOwnedByDm()));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        PC result = pcService.setInspirationPips(1L, 5, dmId);

        assertThat(result.getInspirationPips()).isEqualTo((short) 0);
        assertThat(result.getHeroicInspiration()).isTrue();
        verify(activityLogService).log(1L, PcActivityType.INSPIRATION,
                "Gained Heroic Inspiration (meter filled)", dmId);
    }

    /** A stale click must never 400 — out-of-range values clamp into the meter. */
    @Test
    void setInspirationPips_clampsOutOfRangeValues() {
        pc.setCampaignId(7L);
        pc.setInspirationPips((short) 3);
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(campaignRepository.findById(7L)).thenReturn(Optional.of(campaignOwnedByDm()));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(pcService.setInspirationPips(1L, -4, dmId).getInspirationPips())
                .isEqualTo((short) 0);
        assertThat(pcService.setInspirationPips(1L, 99, dmId).getHeroicInspiration()).isTrue();
    }

    /** Re-clicking the pip already at the meter's value changes nothing and logs nothing. */
    @Test
    void setInspirationPips_noOp_writesNoLogLine() {
        pc.setCampaignId(7L);
        pc.setInspirationPips((short) 3);
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(campaignRepository.findById(7L)).thenReturn(Optional.of(campaignOwnedByDm()));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        PC result = pcService.setInspirationPips(1L, 3, dmId);

        assertThat(result.getInspirationPips()).isEqualTo((short) 3);
        verify(activityLogService, never())
                .log(eq(1L), eq(PcActivityType.INSPIRATION), any(), any());
    }

    @Test
    void setInspirationPips_throws403_whenNotTheCampaignDm() {
        pc.setCampaignId(7L);
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(campaignRepository.findById(7L)).thenReturn(Optional.of(campaignOwnedByDm()));

        assertThatThrownBy(() -> pcService.setInspirationPips(1L, 3, strangerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(403));
        verify(pcRepository, never()).save(any());
    }

    @Test
    void useHeroicInspiration_clearsTheBadge_forTheOwner() {
        pc.setHeroicInspiration(true);
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        PC result = pcService.useHeroicInspiration(1L, ownerId);

        assertThat(result.getHeroicInspiration()).isFalse();
        verify(activityLogService).log(1L, PcActivityType.INSPIRATION,
                "Used Heroic Inspiration", ownerId);
    }

    @Test
    void useHeroicInspiration_throws409_whenThereIsNoneToUse() {
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc)); // heroic false

        assertThatThrownBy(() -> pcService.useHeroicInspiration(1L, ownerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(409));
        verify(pcRepository, never()).save(any());
    }

    @Test
    void useHeroicInspiration_allowsTheCampaignDm() {
        pc.setCampaignId(7L);
        pc.setHeroicInspiration(true);
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(campaignRepository.findById(7L)).thenReturn(Optional.of(campaignOwnedByDm()));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        PC result = pcService.useHeroicInspiration(1L, dmId);

        assertThat(result.getHeroicInspiration()).isFalse();
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

        PC result = pcService.updatePCAsDm(incoming, null, dmId);

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

        assertThatThrownBy(() -> pcService.updatePCAsDm(incoming, null, strangerId))
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

        pcService.updatePCAsDm(incoming, null, dmId);

        ArgumentCaptor<PC> beforeCaptor = ArgumentCaptor.forClass(PC.class);
        ArgumentCaptor<PC> afterCaptor = ArgumentCaptor.forClass(PC.class);
        verify(activityLogService).logDmEdit(beforeCaptor.capture(), afterCaptor.capture(), eq(dmId), isNull());
        assertThat(beforeCaptor.getValue().getAc()).isEqualTo((short) 15); // pre-save AC, not clobbered
        assertThat(afterCaptor.getValue().getAc()).isEqualTo((short) 18);
    }

    @Test
    void updatePCAsDm_passesTheDescriptionThrough_toTheActivityLog() {
        PC existing = new PC();
        existing.setId(1L);
        existing.setUserId(ownerId);
        existing.setCampaignId(7L);
        when(pcRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(campaignRepository.findById(7L)).thenReturn(Optional.of(campaignOwnedByDm()));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        PC incoming = new PC();
        incoming.setId(1L);
        incoming.setAc((short) 18);

        pcService.updatePCAsDm(incoming, "DM changed AC 15 -> 18 for cover", dmId);

        verify(activityLogService).logDmEdit(any(PC.class), any(PC.class), eq(dmId),
                eq("DM changed AC 15 -> 18 for cover"));
    }

    // --- levelUpPC ---

    @Test
    void levelUpPC_appliesRulesAndSaves_whenOwner() {
        pc.setXp(1_000_000); // past every threshold — the XP gate is exercised separately
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(pc)).thenReturn(pc);

        PC result = pcService.levelUpPC(1L, ownerId, null);

        assertThat(result).isSameAs(pc);
        verify(levelUpService).applyLevelUp(pc, null, null, null, null, HpMode.AVERAGE, null, null);
        verify(pcRepository).save(pc);
    }

    @Test
    void levelUpPC_logsTheNewLevel() {
        pc.setLevel((short) 4);
        pc.setXp(1_000_000);
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(pc)).thenReturn(pc);

        pcService.levelUpPC(1L, ownerId, null);

        verify(activityLogService).log(1L, PcActivityType.LEVEL_UP, "Leveled up to 4", ownerId);
    }

    @Test
    void levelUpPC_passesChoicesToRulesEngine() {
        pc.setXp(1_000_000);
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(pc)).thenReturn(pc);

        List<Map<String, Object>> spells = List.of(Map.of("lvl", 0, "name", "Light"));
        pcService.levelUpPC(1L, ownerId, new LevelUpRequest("Life Domain", Map.of("STR", 2), "Sentinel", spells));

        verify(levelUpService).applyLevelUp(pc, "Life Domain", Map.of("STR", 2), "Sentinel", spells, HpMode.AVERAGE, null, null);
    }

    @Test
    void levelUpPC_forwardsRollHpMode() {
        pc.setXp(1_000_000);
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(pc)).thenReturn(pc);

        pcService.levelUpPC(1L, ownerId, new LevelUpRequest(null, null, null, null, HpMode.ROLL));

        verify(levelUpService).applyLevelUp(pc, null, null, null, null, HpMode.ROLL, null, null);
    }

    @Test
    void levelUpPC_nullHpMode_defaultsToAverage() {
        pc.setXp(1_000_000);
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(pc)).thenReturn(pc);

        // hpMode explicitly null in the request -> service coalesces to AVERAGE.
        pcService.levelUpPC(1L, ownerId, new LevelUpRequest(null, null, null, null, null));

        verify(levelUpService).applyLevelUp(pc, null, null, null, null, HpMode.AVERAGE, null, null);
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

    // --- level-up gate + DM grant ---

    @Test
    void levelUpPC_throws409_withoutXpOrGrant() {
        // level 1, xp 0, no grant — the gate blocks the level-up.
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));

        assertThatThrownBy(() -> pcService.levelUpPC(1L, ownerId, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(409));
        verify(pcRepository, never()).save(any());
    }

    @Test
    void levelUpPC_allowedByXpThreshold() {
        pc.setLevel((short) 1);
        pc.setXp(300); // exactly the L2 threshold
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(pc)).thenReturn(pc);

        pcService.levelUpPC(1L, ownerId, null);

        verify(levelUpService).applyLevelUp(pc, null, null, null, null, HpMode.AVERAGE, null, null);
    }

    @Test
    void levelUpPC_allowedByDmGrant_andConsumesIt() {
        pc.setLevel((short) 3);
        pc.setXp(0);
        pc.setPendingLevelGrant(true);
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(pc)).thenReturn(pc);

        pcService.levelUpPC(1L, ownerId, null);

        verify(levelUpService).applyLevelUp(pc, null, null, null, null, HpMode.AVERAGE, null, null);
        assertThat(pc.getPendingLevelGrant()).isFalse(); // grant consumed
    }

    // --- levelUpPCAsDm (DM levels the character directly) ---

    @Test
    void levelUpPCAsDm_appliesRules_withoutXpOrGrant() {
        // Level 1, xp 0, no grant — the DM path has no XP gate.
        pc.setCampaignId(7L);
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(campaignRepository.findById(7L)).thenReturn(Optional.of(campaignOwnedByDm()));
        when(pcRepository.save(pc)).thenReturn(pc);

        PC result = pcService.levelUpPCAsDm(1L, dmId, null);

        assertThat(result).isSameAs(pc);
        verify(levelUpService).applyLevelUp(pc, null, null, null, null, HpMode.AVERAGE, null, null);
    }

    @Test
    void levelUpPCAsDm_passesChoicesToRulesEngine() {
        pc.setCampaignId(7L);
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(campaignRepository.findById(7L)).thenReturn(Optional.of(campaignOwnedByDm()));
        when(pcRepository.save(pc)).thenReturn(pc);

        List<Map<String, Object>> spells = List.of(Map.of("lvl", 0, "name", "Light"));
        pcService.levelUpPCAsDm(1L, dmId, new LevelUpRequest("Life Domain", Map.of("STR", 2), "Sentinel", spells));

        verify(levelUpService).applyLevelUp(pc, "Life Domain", Map.of("STR", 2), "Sentinel", spells, HpMode.AVERAGE, null, null);
    }

    @Test
    void levelUpPCAsDm_consumesAPendingGrant_andLogsTheDm() {
        pc.setCampaignId(7L);
        pc.setLevel((short) 3);
        pc.setPendingLevelGrant(true);
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(campaignRepository.findById(7L)).thenReturn(Optional.of(campaignOwnedByDm()));
        when(pcRepository.save(pc)).thenReturn(pc);

        pcService.levelUpPCAsDm(1L, dmId, null);

        assertThat(pc.getPendingLevelGrant()).isFalse(); // grant consumed
        verify(activityLogService).log(1L, PcActivityType.LEVEL_UP, "DM leveled up to 3", dmId);
    }

    @Test
    void levelUpPCAsDm_throws403_whenCallerIsNotTheCampaignDm() {
        pc.setCampaignId(7L);
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(campaignRepository.findById(7L)).thenReturn(Optional.of(campaignOwnedByDm()));

        assertThatThrownBy(() -> pcService.levelUpPCAsDm(1L, strangerId, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(403));
        verify(pcRepository, never()).save(any());
    }

    @Test
    void levelUpPCAsDm_throws403_whenPcIsInNoCampaign() {
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc)); // campaignId null

        assertThatThrownBy(() -> pcService.levelUpPCAsDm(1L, dmId, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(403));
        verify(pcRepository, never()).save(any());
    }

    // --- previewLevelUpAsDm ---

    @Test
    void previewLevelUpAsDm_returnsPreview_whenCampaignDm() {
        pc.setCampaignId(7L);
        LevelUpPreview preview = new LevelUpPreview(4, 5, 8, 2, 7, 39, 2, 3, Map.of(), Map.of(), false, List.of(), false, List.of(), List.of(), 0, 0, 0, 0);
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(campaignRepository.findById(7L)).thenReturn(Optional.of(campaignOwnedByDm()));
        when(levelUpService.preview(pc)).thenReturn(preview);

        LevelUpPreview result = pcService.previewLevelUpAsDm(1L, dmId);

        assertThat(result).isSameAs(preview);
    }

    @Test
    void previewLevelUpAsDm_throws403_whenCallerIsNotTheCampaignDm() {
        pc.setCampaignId(7L);
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(campaignRepository.findById(7L)).thenReturn(Optional.of(campaignOwnedByDm()));

        assertThatThrownBy(() -> pcService.previewLevelUpAsDm(1L, strangerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(403));
        verify(levelUpService, never()).preview(any());
    }

    @Test
    void setLevelGrant_byCampaignDm_setsTheFlag_andLogs() {
        pc.setCampaignId(7L);
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(campaignRepository.findById(7L)).thenReturn(Optional.of(campaignOwnedByDm()));
        when(pcRepository.save(pc)).thenReturn(pc);

        PC result = pcService.setLevelGrant(1L, true, dmId);

        assertThat(result.getPendingLevelGrant()).isTrue();
        verify(activityLogService).log(1L, PcActivityType.DM_EDIT, "DM granted a level-up", dmId);
    }

    @Test
    void setLevelGrant_revoke_logsTheRevocation() {
        pc.setCampaignId(7L);
        pc.setPendingLevelGrant(true);
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(campaignRepository.findById(7L)).thenReturn(Optional.of(campaignOwnedByDm()));
        when(pcRepository.save(pc)).thenReturn(pc);

        PC result = pcService.setLevelGrant(1L, false, dmId);

        assertThat(result.getPendingLevelGrant()).isFalse();
        verify(activityLogService).log(1L, PcActivityType.DM_EDIT, "DM revoked the granted level-up", dmId);
    }

    @Test
    void setLevelGrant_throws403_whenCallerIsNotTheCampaignDm() {
        pc.setCampaignId(7L);
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(campaignRepository.findById(7L)).thenReturn(Optional.of(campaignOwnedByDm()));

        assertThatThrownBy(() -> pcService.setLevelGrant(1L, true, strangerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(403));
        verify(pcRepository, never()).save(any());
        verify(sessionRepository, never()).save(any());
    }

    // --- live-session version bump (grant / revoke / level-up reach the poll) ---

    /** A non-ended session (id 40, version 3) for campaign 7. */
    private CombatSession liveSession() {
        CombatSession session = new CombatSession();
        session.setId(40L);
        session.setCampaignId(7L);
        session.setDmUserId(dmId);
        session.setStatus(SessionStatus.ACTIVE);
        session.setVersion(3L);
        return session;
    }

    /** Stub the DM grant path for a campaign-7 PC with the given current flag. */
    private void stubGrantPath(boolean currentlyGranted) {
        pc.setCampaignId(7L);
        pc.setPendingLevelGrant(currentlyGranted);
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(campaignRepository.findById(7L)).thenReturn(Optional.of(campaignOwnedByDm()));
        when(pcRepository.save(pc)).thenReturn(pc);
    }

    /** Seat PC 1 in {@code session}, the campaign's live session. */
    private void seatInLiveSession(CombatSession session) {
        when(sessionRepository.findByCampaignIdAndStatusNot(7L, SessionStatus.ENDED))
                .thenReturn(Optional.of(session));
        when(participantRepository.findBySessionIdAndPcId(40L, 1L))
                .thenReturn(Optional.of(new SessionParticipant()));
    }

    @Test
    void setLevelGrant_grant_bumpsTheSeatedLiveSessionsVersion() {
        stubGrantPath(false);
        CombatSession session = liveSession();
        seatInLiveSession(session);

        pcService.setLevelGrant(1L, true, dmId);

        assertThat(session.getVersion()).isEqualTo(4L);
        verify(sessionRepository).save(session);
    }

    @Test
    void setLevelGrant_revoke_bumpsTheSeatedLiveSessionsVersion() {
        stubGrantPath(true);
        CombatSession session = liveSession();
        seatInLiveSession(session);

        pcService.setLevelGrant(1L, false, dmId);

        assertThat(session.getVersion()).isEqualTo(4L);
        verify(sessionRepository).save(session);
    }

    @Test
    void setLevelGrant_unchangedFlag_doesNotBumpAnySession() {
        stubGrantPath(true);

        pcService.setLevelGrant(1L, true, dmId); // already granted: a no-op

        verify(sessionRepository, never()).findByCampaignIdAndStatusNot(any(), any());
        verify(sessionRepository, never()).save(any());
        verify(activityLogService, never()).log(any(), any(), any(), any());
    }

    @Test
    void setLevelGrant_onlyEndedSessions_touchesNoSession() {
        // The lookup excludes ENDED by status, so a campaign whose only
        // sessions are ended has nothing to bump.
        stubGrantPath(false);
        when(sessionRepository.findByCampaignIdAndStatusNot(7L, SessionStatus.ENDED))
                .thenReturn(Optional.empty());

        pcService.setLevelGrant(1L, true, dmId);

        verify(sessionRepository).findByCampaignIdAndStatusNot(7L, SessionStatus.ENDED);
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void setLevelGrant_liveSessionThePcHasNotJoined_isNotBumped() {
        stubGrantPath(false);
        CombatSession session = liveSession();
        when(sessionRepository.findByCampaignIdAndStatusNot(7L, SessionStatus.ENDED))
                .thenReturn(Optional.of(session));
        when(participantRepository.findBySessionIdAndPcId(40L, 1L)).thenReturn(Optional.empty());

        pcService.setLevelGrant(1L, true, dmId);

        assertThat(session.getVersion()).isEqualTo(3L);
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void levelUpPC_bumpsTheSeatedLiveSessionsVersion() {
        pc.setCampaignId(7L);
        pc.setPendingLevelGrant(true);
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(pc)).thenReturn(pc);
        CombatSession session = liveSession();
        seatInLiveSession(session);

        pcService.levelUpPC(1L, ownerId, null);

        assertThat(pc.getPendingLevelGrant()).isFalse();
        assertThat(session.getVersion()).isEqualTo(4L);
        verify(sessionRepository).save(session);
    }

    @Test
    void levelUpPCAsDm_bumpsTheSeatedLiveSessionsVersion() {
        pc.setCampaignId(7L);
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(campaignRepository.findById(7L)).thenReturn(Optional.of(campaignOwnedByDm()));
        when(pcRepository.save(pc)).thenReturn(pc);
        CombatSession session = liveSession();
        seatInLiveSession(session);

        pcService.levelUpPCAsDm(1L, dmId, null);

        assertThat(session.getVersion()).isEqualTo(4L);
        verify(sessionRepository).save(session);
    }

    @Test
    void levelUpPC_outsideAnyCampaign_touchesNoSession() {
        pc.setPendingLevelGrant(true); // campaignId null
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(pc)).thenReturn(pc);

        pcService.levelUpPC(1L, ownerId, null);

        verifyNoInteractions(sessionRepository, participantRepository);
    }

    @Test
    void updatePCAsDm_neverChangesThePendingLevelGrant() {
        // Server-owned: even a body echoing a stale value can't flip the flag.
        pc.setCampaignId(7L);
        pc.setPendingLevelGrant(true);
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(campaignRepository.findById(7L)).thenReturn(Optional.of(campaignOwnedByDm()));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        PC incoming = new PC();
        incoming.setId(1L);
        incoming.setPendingLevelGrant(false); // stale client echo

        PC saved = pcService.updatePCAsDm(incoming, null, dmId);

        assertThat(saved.getPendingLevelGrant()).isTrue();
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

    // --- Death Knight hero class (paladin + Oath of the Ebon Blade, specialization) ---

    private static int status(Throwable e) {
        return ((ResponseStatusException) e).getStatusCode().value();
    }

    @Test
    void addPC_acceptsEbonBlade_onPaladinAtLevelOne() {
        pc.setClazz("paladin");
        pc.setSubclass("Oath of the Ebon Blade");
        pc.setSpecialization("Frost");
        when(pcRepository.saveAndFlush(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        PC saved = pcService.addPC(pc);

        assertThat(saved.getLevel()).isEqualTo((short) 1);
        assertThat(saved.getSubclass()).isEqualTo("Oath of the Ebon Blade");
        assertThat(saved.getSpecialization()).isEqualTo("Frost");
    }

    @Test
    void addPC_acceptsEbonBlade_withoutSpecialization() {
        pc.setClazz("paladin");
        pc.setSubclass("Oath of the Ebon Blade");
        when(pcRepository.saveAndFlush(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(pcService.addPC(pc).getSpecialization()).isNull();
    }

    @Test
    void addPC_rejectsEbonBlade_onNonPaladin() {
        pc.setClazz("fighter");
        pc.setSubclass("Oath of the Ebon Blade");

        assertThatThrownBy(() -> pcService.addPC(pc))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        verify(pcRepository, never()).saveAndFlush(any());
    }

    @Test
    void addPC_stillAcceptsCreationSubclassesForOtherClasses() {
        pc.setClazz("sorcerer");
        pc.setSubclass("Draconic Sorcery");
        when(pcRepository.saveAndFlush(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(pcService.addPC(pc).getSubclass()).isEqualTo("Draconic Sorcery");
    }

    @Test
    void addPC_rejectsInvalidEbonBladeSpecialization() {
        pc.setClazz("paladin");
        pc.setSubclass("Oath of the Ebon Blade");
        pc.setSpecialization("blood"); // exact values only

        assertThatThrownBy(() -> pcService.addPC(pc))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
    }

    @Test
    void addPC_rejectsSpecialization_onNonHeroSubclass() {
        pc.setClazz("paladin");
        pc.setSubclass("Oath of Devotion");
        pc.setSpecialization("Unholy");

        assertThatThrownBy(() -> pcService.addPC(pc))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
    }

    @Test
    void addPC_rejectsSpecialization_withNoSubclass() {
        pc.setSpecialization("Blood");

        assertThatThrownBy(() -> pcService.addPC(pc))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
    }

    @Test
    void updatePC_changesSpecialization_forDeathKnight() {
        pc.setClazz("paladin");
        pc.setSubclass("Oath of the Ebon Blade");
        pc.setSpecialization("Blood");
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        PC incoming = new PC();
        incoming.setId(1L);
        incoming.setClazz("paladin");
        incoming.setSubclass("Oath of the Ebon Blade");
        incoming.setSpecialization("Unholy");

        assertThat(pcService.updatePC(incoming, ownerId).getSpecialization()).isEqualTo("Unholy");
    }

    @Test
    void updatePC_preservesSpecialization_whenTheBodyOmitsIt() {
        pc.setClazz("paladin");
        pc.setSubclass("Oath of the Ebon Blade");
        pc.setSpecialization("Blood");
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        PC incoming = new PC();
        incoming.setId(1L);
        incoming.setClazz("paladin");
        incoming.setSubclass("Oath of the Ebon Blade"); // specialization null — built without the field

        assertThat(pcService.updatePC(incoming, ownerId).getSpecialization()).isEqualTo("Blood");
    }

    @Test
    void updatePC_rejectsSpecialization_onNonHeroSubclass() {
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));

        PC incoming = new PC();
        incoming.setId(1L);
        incoming.setClazz("paladin");
        incoming.setSubclass("Oath of Glory");
        incoming.setSpecialization("Frost");

        assertThatThrownBy(() -> pcService.updatePC(incoming, ownerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        verify(pcRepository, never()).save(any());
    }

    @Test
    void updatePCAsDm_rejectsEbonBlade_onNonPaladin() {
        pc.setCampaignId(7L);
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(campaignRepository.findById(7L)).thenReturn(Optional.of(campaignOwnedByDm()));

        PC incoming = new PC();
        incoming.setId(1L);
        incoming.setClazz("Wizard");
        incoming.setSubclass("Oath of the Ebon Blade");

        assertThatThrownBy(() -> pcService.updatePCAsDm(incoming, null, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        verify(pcRepository, never()).save(any());
    }

    @Test
    void levelUpPC_forwardsSpecializationToRulesEngine() {
        pc.setXp(1_000_000);
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(pc)).thenReturn(pc);

        pcService.levelUpPC(1L, ownerId, new LevelUpRequest(null, null, null, null, null, "Frost"));

        verify(levelUpService).applyLevelUp(pc, null, null, null, null, HpMode.AVERAGE, "Frost", null);
    }

    // --- Demon Hunter hero class (ranger + Demon Hunter, specialization) ---

    @Test
    void addPC_acceptsDemonHunter_onRangerWithSpecialization() {
        pc.setClazz("ranger");
        pc.setSubclass("Demon Hunter");
        pc.setSpecialization("Havoc");
        when(pcRepository.saveAndFlush(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        PC saved = pcService.addPC(pc);

        assertThat(saved.getSubclass()).isEqualTo("Demon Hunter");
        assertThat(saved.getSpecialization()).isEqualTo("Havoc");
    }

    @Test
    void addPC_rejectsDemonHunter_onNonRanger() {
        for (String clazz : List.of("paladin", "fighter", "warlock")) {
            pc.setClazz(clazz);
            pc.setSubclass("Demon Hunter");

            assertThatThrownBy(() -> pcService.addPC(pc))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        }
        verify(pcRepository, never()).saveAndFlush(any());
    }

    @Test
    void addPC_rejectsSpecialization_notInTheHeroSubclassList() {
        // A Death Knight rune on a Demon Hunter, a wrong-case value, and a DH value on a Death Knight.
        for (String[] c : new String[][] {
                {"ranger", "Demon Hunter", "Blood"},
                {"ranger", "Demon Hunter", "havoc"},
                {"paladin", "Oath of the Ebon Blade", "Vengeance"}}) {
            pc.setClazz(c[0]);
            pc.setSubclass(c[1]);
            pc.setSpecialization(c[2]);

            assertThatThrownBy(() -> pcService.addPC(pc))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        }
        verify(pcRepository, never()).saveAndFlush(any());
    }

    @Test
    void updatePC_ownerCannotChangeDemonHunterSpecialization() {
        pc.setClazz("ranger");
        pc.setSubclass("Demon Hunter");
        pc.setSpecialization("Havoc");
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));

        PC incoming = new PC();
        incoming.setId(1L);
        incoming.setClazz("ranger");
        incoming.setSubclass("Demon Hunter");
        incoming.setSpecialization("Vengeance");

        assertThatThrownBy(() -> pcService.updatePC(incoming, ownerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        verify(pcRepository, never()).save(any());
    }

    @Test
    void updatePC_ownerMayKeepOrFirstSetDemonHunterSpecialization() {
        pc.setClazz("ranger");
        pc.setSubclass("Demon Hunter");
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        PC incoming = new PC();
        incoming.setId(1L);
        incoming.setClazz("ranger");
        incoming.setSubclass("Demon Hunter");
        incoming.setSpecialization("Devourer"); // none stored yet

        assertThat(pcService.updatePC(incoming, ownerId).getSpecialization()).isEqualTo("Devourer");

        pc.setSpecialization("Devourer");
        PC omitted = new PC();
        omitted.setId(1L);
        omitted.setClazz("ranger");
        omitted.setSubclass("Demon Hunter"); // specialization omitted — keeps the stored one

        assertThat(pcService.updatePC(omitted, ownerId).getSpecialization()).isEqualTo("Devourer");
    }

    @Test
    void updatePCAsDm_mayChangeDemonHunterSpecialization() {
        pc.setClazz("ranger");
        pc.setSubclass("Demon Hunter");
        pc.setSpecialization("Havoc");
        pc.setCampaignId(7L);
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(campaignRepository.findById(7L)).thenReturn(Optional.of(campaignOwnedByDm()));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        PC incoming = new PC();
        incoming.setId(1L);
        incoming.setClazz("ranger");
        incoming.setSubclass("Demon Hunter");
        incoming.setSpecialization("Vengeance");

        assertThat(pcService.updatePCAsDm(incoming, null, dmId).getSpecialization()).isEqualTo("Vengeance");
    }

    @Test
    void levelUpPC_forwardsFightingStyleToRulesEngine() {
        pc.setXp(1_000_000);
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(pc)).thenReturn(pc);

        pcService.levelUpPC(1L, ownerId,
                new LevelUpRequest(null, null, null, null, null, null, "Void Warrior"));

        verify(levelUpService).applyLevelUp(pc, null, null, null, null, HpMode.AVERAGE, null, "Void Warrior");
    }

    // --- WoW Druid (druid + class specialization, no subclass lock) ---

    @Test
    void addPC_acceptsEachDruidSpecialization() {
        when(pcRepository.saveAndFlush(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));
        for (String spec : List.of("Balance", "Feral", "Guardian", "Restoration")) {
            PC druid = new PC();
            druid.setClazz("druid");
            druid.setSpecialization(spec);

            PC saved = pcService.addPC(druid);

            assertThat(saved.getSpecialization()).isEqualTo(spec);
            assertThat(saved.getSubclass()).isNull();
        }
    }

    @Test
    void addPC_acceptsBaseGameDruid_withoutSpecialization() {
        pc.setClazz("Druid");
        when(pcRepository.saveAndFlush(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(pcService.addPC(pc).getSpecialization()).isNull();
    }

    @Test
    void addPC_rejectsInvalidDruidSpecialization() {
        for (String bad : List.of("balance", "Havoc", "Blood", "Shadow")) {
            PC druid = new PC();
            druid.setClazz("druid");
            druid.setSpecialization(bad);

            assertThatThrownBy(() -> pcService.addPC(druid))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        }
        verify(pcRepository, never()).saveAndFlush(any());
    }

    @Test
    void addPC_rejectsDruidSpecialization_onClassWithoutWowVariant() {
        pc.setClazz("fighter");
        pc.setSpecialization("Balance");

        assertThatThrownBy(() -> pcService.addPC(pc))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        verify(pcRepository, never()).saveAndFlush(any());
    }

    @Test
    void updatePC_ownerMayChangeDruidSpecialization() {
        pc.setClazz("druid");
        pc.setSubclass("Circle of the Moon");
        pc.setSpecialization("Feral");
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        PC incoming = new PC();
        incoming.setId(1L);
        incoming.setClazz("druid");
        incoming.setSubclass("Circle of the Moon");
        incoming.setSpecialization("Guardian");

        assertThat(pcService.updatePC(incoming, ownerId).getSpecialization()).isEqualTo("Guardian");
    }

    @Test
    void updatePC_preservesDruidSpecialization_whenTheBodyOmitsIt() {
        pc.setClazz("druid");
        pc.setSpecialization("Restoration");
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        PC incoming = new PC();
        incoming.setId(1L);
        incoming.setClazz("druid"); // specialization null — built without the field

        assertThat(pcService.updatePC(incoming, ownerId).getSpecialization()).isEqualTo("Restoration");
    }

    @Test
    void updatePC_rejectsInvalidDruidSpecialization() {
        pc.setClazz("druid");
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));

        PC incoming = new PC();
        incoming.setId(1L);
        incoming.setClazz("druid");
        incoming.setSpecialization("Frost");

        assertThatThrownBy(() -> pcService.updatePC(incoming, ownerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        verify(pcRepository, never()).save(any());
    }

    @Test
    void updatePCAsDm_acceptsDruidSpecialization_andRejectsItOnFighter() {
        pc.setCampaignId(7L);
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(campaignRepository.findById(7L)).thenReturn(Optional.of(campaignOwnedByDm()));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        PC druid = new PC();
        druid.setId(1L);
        druid.setClazz("druid");
        druid.setSpecialization("Balance");
        assertThat(pcService.updatePCAsDm(druid, null, dmId).getSpecialization()).isEqualTo("Balance");

        PC fighter = new PC();
        fighter.setId(1L);
        fighter.setClazz("fighter");
        fighter.setSpecialization("Balance");
        assertThatThrownBy(() -> pcService.updatePCAsDm(fighter, null, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
    }

    // --- WoW Paladin (paladin + Holy/Protection/Retribution) ---

    @Test
    void addPC_acceptsEachPaladinSpecialization() {
        when(pcRepository.saveAndFlush(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));
        for (String spec : List.of("Holy", "Protection", "Retribution")) {
            PC paladin = new PC();
            paladin.setClazz("paladin");
            paladin.setSpecialization(spec);

            PC saved = pcService.addPC(paladin);

            assertThat(saved.getSpecialization()).isEqualTo(spec);
            assertThat(saved.getSubclass()).isNull();
        }
    }

    @Test
    void addPC_deathKnightCannotTakeHoly_andWowPaladinCannotTakeFrost() {
        PC dk = new PC();
        dk.setClazz("paladin");
        dk.setSubclass("Oath of the Ebon Blade");
        dk.setSpecialization("Holy");
        assertThatThrownBy(() -> pcService.addPC(dk))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));

        PC paladin = new PC();
        paladin.setClazz("paladin");
        paladin.setSpecialization("Frost");
        assertThatThrownBy(() -> pcService.addPC(paladin))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));

        PC lower = new PC();
        lower.setClazz("paladin");
        lower.setSpecialization("holy"); // exact values only
        assertThatThrownBy(() -> pcService.addPC(lower))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        verify(pcRepository, never()).saveAndFlush(any());
    }

    @Test
    void updatePC_ownerMayChangePaladinSpecialization() {
        pc.setClazz("paladin");
        pc.setSubclass("Oath of Devotion");
        pc.setSpecialization("Protection");
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        PC incoming = new PC();
        incoming.setId(1L);
        incoming.setClazz("paladin");
        incoming.setSubclass("Oath of Devotion");
        incoming.setSpecialization("Retribution");

        assertThat(pcService.updatePC(incoming, ownerId).getSpecialization()).isEqualTo("Retribution");
    }

    @Test
    void updatePC_preservesPaladinSpecialization_whenTheBodyOmitsIt() {
        pc.setClazz("paladin");
        pc.setSpecialization("Holy");
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        PC incoming = new PC();
        incoming.setId(1L);
        incoming.setClazz("paladin");

        assertThat(pcService.updatePC(incoming, ownerId).getSpecialization()).isEqualTo("Holy");
    }

    @Test
    void updatePCAsDm_turningADeathKnightIntoAPaladin_dropsTheRuneSpecialization() {
        pc.setClazz("paladin");
        pc.setSubclass("Oath of the Ebon Blade");
        pc.setSpecialization("Frost");
        pc.setCampaignId(7L);
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(campaignRepository.findById(7L)).thenReturn(Optional.of(campaignOwnedByDm()));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        PC incoming = new PC();
        incoming.setId(1L);
        incoming.setClazz("paladin");
        incoming.setSubclass("Oath of Devotion"); // specialization omitted

        assertThat(pcService.updatePCAsDm(incoming, null, dmId).getSpecialization()).isNull();
    }

    @Test
    void updatePCAsDm_acceptsPaladinSpecialization() {
        pc.setCampaignId(7L);
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(campaignRepository.findById(7L)).thenReturn(Optional.of(campaignOwnedByDm()));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        PC paladin = new PC();
        paladin.setId(1L);
        paladin.setClazz("paladin");
        paladin.setSpecialization("Protection");
        assertThat(pcService.updatePCAsDm(paladin, null, dmId).getSpecialization()).isEqualTo("Protection");
    }

    // --- WoW Rogue (rogue + Assassination/Outlaw/Subtlety) ---

    @Test
    void addPC_acceptsEachRogueSpecialization() {
        when(pcRepository.saveAndFlush(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));
        for (String spec : List.of("Assassination", "Outlaw", "Subtlety")) {
            PC rogue = new PC();
            rogue.setClazz("rogue");
            rogue.setSpecialization(spec);

            PC saved = pcService.addPC(rogue);

            assertThat(saved.getSpecialization()).isEqualTo(spec);
            assertThat(saved.getSubclass()).isNull();
        }
    }

    @Test
    void addPC_rejectsInvalidRogueSpecialization_andRogueSpecsOnOtherClasses() {
        for (String bad : List.of("outlaw", "Combat", "Holy", "Havoc")) {
            PC rogue = new PC();
            rogue.setClazz("rogue");
            rogue.setSpecialization(bad);
            assertThatThrownBy(() -> pcService.addPC(rogue))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        }
        PC paladin = new PC();
        paladin.setClazz("paladin");
        paladin.setSpecialization("Outlaw");
        assertThatThrownBy(() -> pcService.addPC(paladin))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        verify(pcRepository, never()).saveAndFlush(any());
    }

    @Test
    void updatePC_ownerMayChangeRogueSpecialization() {
        pc.setClazz("rogue");
        pc.setSubclass("Swashbuckler");
        pc.setSpecialization("Outlaw");
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        PC incoming = new PC();
        incoming.setId(1L);
        incoming.setClazz("rogue");
        incoming.setSubclass("Swashbuckler");
        incoming.setSpecialization("Subtlety");
        assertThat(pcService.updatePC(incoming, ownerId).getSpecialization()).isEqualTo("Subtlety");
    }

    @Test
    void updatePC_rejectsInvalidRogueSpecialization() {
        pc.setClazz("rogue");
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));

        PC incoming = new PC();
        incoming.setId(1L);
        incoming.setClazz("rogue");
        incoming.setSpecialization("Frost");
        assertThatThrownBy(() -> pcService.updatePC(incoming, ownerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        verify(pcRepository, never()).save(any());
    }

    @Test
    void updatePCAsDm_acceptsRogueSpecialization() {
        pc.setCampaignId(7L);
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(campaignRepository.findById(7L)).thenReturn(Optional.of(campaignOwnedByDm()));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        PC rogue = new PC();
        rogue.setId(1L);
        rogue.setClazz("rogue");
        rogue.setSpecialization("Assassination");
        assertThat(pcService.updatePCAsDm(rogue, null, dmId).getSpecialization()).isEqualTo("Assassination");
    }

    // --- WoW Warrior (fighter + Arms/Fury/Protection) ---

    @Test
    void addPC_acceptsEachWarriorSpecialization() {
        when(pcRepository.saveAndFlush(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));
        for (String spec : List.of("Arms", "Fury", "Protection")) {
            PC fighter = new PC();
            fighter.setClazz("fighter");
            fighter.setSpecialization(spec);

            PC saved = pcService.addPC(fighter);

            assertThat(saved.getSpecialization()).isEqualTo(spec);
            assertThat(saved.getSubclass()).isNull();
        }
    }

    @Test
    void addPC_rejectsInvalidWarriorSpecialization_andWarriorSpecsOnOtherClasses() {
        for (String bad : List.of("arms", "Holy", "Elemental", "Outlaw", "Retribution")) {
            PC fighter = new PC();
            fighter.setClazz("fighter");
            fighter.setSpecialization(bad);
            assertThatThrownBy(() -> pcService.addPC(fighter))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        }
        for (String clazz : List.of("rogue", "barbarian", "druid", "ranger")) {
            PC other = new PC();
            other.setClazz(clazz);
            other.setSpecialization("Arms");
            assertThatThrownBy(() -> pcService.addPC(other))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        }
        PC paladin = new PC();
        paladin.setClazz("paladin");
        paladin.setSpecialization("Fury");
        assertThatThrownBy(() -> pcService.addPC(paladin))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        verify(pcRepository, never()).saveAndFlush(any());
    }

    @Test
    void updatePC_ownerMayChangeWarriorSpecialization() {
        pc.setClazz("fighter");
        pc.setSubclass("Battle Master");
        pc.setSpecialization("Arms");
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        PC incoming = new PC();
        incoming.setId(1L);
        incoming.setClazz("fighter");
        incoming.setSubclass("Battle Master");
        incoming.setSpecialization("Protection");
        assertThat(pcService.updatePC(incoming, ownerId).getSpecialization()).isEqualTo("Protection");
    }

    @Test
    void updatePC_rejectsInvalidWarriorSpecialization() {
        pc.setClazz("fighter");
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));

        PC incoming = new PC();
        incoming.setId(1L);
        incoming.setClazz("fighter");
        incoming.setSpecialization("Elemental");
        assertThatThrownBy(() -> pcService.updatePC(incoming, ownerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        verify(pcRepository, never()).save(any());
    }

    @Test
    void updatePCAsDm_acceptsWarriorSpecialization() {
        pc.setCampaignId(7L);
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(campaignRepository.findById(7L)).thenReturn(Optional.of(campaignOwnedByDm()));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        PC fighter = new PC();
        fighter.setId(1L);
        fighter.setClazz("fighter");
        fighter.setSpecialization("Fury");
        assertThat(pcService.updatePCAsDm(fighter, null, dmId).getSpecialization()).isEqualTo("Fury");
    }

    // --- WoW Shaman (barbarian + Elemental/Enhancement/Restoration) ---

    @Test
    void addPC_acceptsEachShamanSpecialization() {
        when(pcRepository.saveAndFlush(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));
        for (String spec : List.of("Elemental", "Enhancement", "Restoration")) {
            PC barbarian = new PC();
            barbarian.setClazz("barbarian");
            barbarian.setSpecialization(spec);

            PC saved = pcService.addPC(barbarian);

            assertThat(saved.getSpecialization()).isEqualTo(spec);
            assertThat(saved.getSubclass()).isNull();
        }
    }

    @Test
    void addPC_rejectsInvalidShamanSpecialization_andShamanSpecsOnOtherClasses() {
        for (String bad : List.of("elemental", "Enhance", "Holy", "Outlaw", "Frost")) {
            PC barbarian = new PC();
            barbarian.setClazz("barbarian");
            barbarian.setSpecialization(bad);
            assertThatThrownBy(() -> pcService.addPC(barbarian))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        }
        for (String clazz : List.of("rogue", "paladin", "fighter")) {
            PC other = new PC();
            other.setClazz(clazz);
            other.setSpecialization("Elemental");
            assertThatThrownBy(() -> pcService.addPC(other))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        }
        verify(pcRepository, never()).saveAndFlush(any());
    }

    @Test
    void updatePC_ownerMayChangeShamanSpecialization() {
        pc.setClazz("barbarian");
        pc.setSubclass("Path of the Elements");
        pc.setSpecialization("Elemental");
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        PC incoming = new PC();
        incoming.setId(1L);
        incoming.setClazz("barbarian");
        incoming.setSubclass("Path of the Elements");
        incoming.setSpecialization("Enhancement");
        assertThat(pcService.updatePC(incoming, ownerId).getSpecialization()).isEqualTo("Enhancement");
    }

    @Test
    void updatePC_rejectsInvalidShamanSpecialization() {
        pc.setClazz("barbarian");
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));

        PC incoming = new PC();
        incoming.setId(1L);
        incoming.setClazz("barbarian");
        incoming.setSpecialization("Holy");
        assertThatThrownBy(() -> pcService.updatePC(incoming, ownerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        verify(pcRepository, never()).save(any());
    }

    @Test
    void updatePCAsDm_acceptsShamanSpecialization() {
        pc.setCampaignId(7L);
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(campaignRepository.findById(7L)).thenReturn(Optional.of(campaignOwnedByDm()));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        PC barbarian = new PC();
        barbarian.setId(1L);
        barbarian.setClazz("barbarian");
        barbarian.setSpecialization("Restoration");
        assertThat(pcService.updatePCAsDm(barbarian, null, dmId).getSpecialization()).isEqualTo("Restoration");
    }

    // --- WoW Warlock (warlock + Affliction/Demonology/Destruction) ---

    @Test
    void addPC_acceptsEachWarlockSpecialization() {
        when(pcRepository.saveAndFlush(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));
        for (String spec : List.of("Affliction", "Demonology", "Destruction")) {
            PC warlock = new PC();
            warlock.setClazz("warlock");
            warlock.setSubclass("Fiend Patron");
            warlock.setSpecialization(spec);

            PC saved = pcService.addPC(warlock);

            assertThat(saved.getSpecialization()).isEqualTo(spec);
            assertThat(saved.getSubclass()).isEqualTo("Fiend Patron");
        }
    }

    @Test
    void addPC_rejectsInvalidWarlockSpecialization_andWarlockSpecsOnOtherClasses() {
        for (String bad : List.of("affliction", "Fire", "Holy", "Outlaw", "Elemental")) {
            PC warlock = new PC();
            warlock.setClazz("warlock");
            warlock.setSpecialization(bad);
            assertThatThrownBy(() -> pcService.addPC(warlock))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        }
        for (String clazz : List.of("sorcerer", "wizard", "paladin", "rogue")) {
            PC other = new PC();
            other.setClazz(clazz);
            other.setSpecialization("Destruction");
            assertThatThrownBy(() -> pcService.addPC(other))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        }
        verify(pcRepository, never()).saveAndFlush(any());
    }

    @Test
    void updatePC_ownerMayChangeWarlockSpecialization() {
        pc.setClazz("warlock");
        pc.setSubclass("Great Old One Patron");
        pc.setSpecialization("Affliction");
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        PC incoming = new PC();
        incoming.setId(1L);
        incoming.setClazz("warlock");
        incoming.setSubclass("Great Old One Patron");
        incoming.setSpecialization("Demonology");
        assertThat(pcService.updatePC(incoming, ownerId).getSpecialization()).isEqualTo("Demonology");
    }

    @Test
    void updatePC_rejectsInvalidWarlockSpecialization() {
        pc.setClazz("warlock");
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));

        PC incoming = new PC();
        incoming.setId(1L);
        incoming.setClazz("warlock");
        incoming.setSpecialization("Fire");
        assertThatThrownBy(() -> pcService.updatePC(incoming, ownerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        verify(pcRepository, never()).save(any());
    }

    @Test
    void updatePCAsDm_acceptsWarlockSpecialization() {
        pc.setCampaignId(7L);
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(campaignRepository.findById(7L)).thenReturn(Optional.of(campaignOwnedByDm()));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        PC warlock = new PC();
        warlock.setId(1L);
        warlock.setClazz("warlock");
        warlock.setSpecialization("Destruction");
        assertThat(pcService.updatePCAsDm(warlock, null, dmId).getSpecialization()).isEqualTo("Destruction");
    }

    // --- WoW Priest (cleric + Discipline/Holy/Shadow) ---

    @Test
    void addPC_acceptsEachPriestSpecialization() {
        when(pcRepository.saveAndFlush(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));
        for (String spec : List.of("Discipline", "Holy", "Shadow")) {
            PC cleric = new PC();
            cleric.setClazz("cleric");
            cleric.setSpecialization(spec);

            PC saved = pcService.addPC(cleric);

            assertThat(saved.getSpecialization()).isEqualTo(spec);
            assertThat(saved.getSubclass()).isNull();
        }
    }

    @Test
    void addPC_acceptsBaseGameCleric_withoutSpecialization() {
        pc.setClazz("Cleric");
        when(pcRepository.saveAndFlush(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(pcService.addPC(pc).getSpecialization()).isNull();
    }

    @Test
    void addPC_rejectsInvalidPriestSpecialization_andPriestSpecsOnOtherClasses() {
        for (String bad : List.of("holy", "Protection", "Elemental", "Disc", "Frost", "Restoration")) {
            PC cleric = new PC();
            cleric.setClazz("cleric");
            cleric.setSpecialization(bad);
            assertThatThrownBy(() -> pcService.addPC(cleric))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        }
        for (String clazz : List.of("druid", "rogue", "barbarian", "wizard")) {
            for (String spec : List.of("Discipline", "Shadow")) {
                PC other = new PC();
                other.setClazz(clazz);
                other.setSpecialization(spec);
                assertThatThrownBy(() -> pcService.addPC(other))
                        .isInstanceOf(ResponseStatusException.class)
                        .satisfies(e -> assertThat(status(e)).isEqualTo(400));
            }
        }
        verify(pcRepository, never()).saveAndFlush(any());
    }

    @Test
    void updatePC_ownerMayChangePriestSpecialization() {
        pc.setClazz("cleric");
        pc.setSubclass("Shadow Domain");
        pc.setSpecialization("Shadow");
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        PC incoming = new PC();
        incoming.setId(1L);
        incoming.setClazz("cleric");
        incoming.setSubclass("Shadow Domain");
        incoming.setSpecialization("Discipline");
        assertThat(pcService.updatePC(incoming, ownerId).getSpecialization()).isEqualTo("Discipline");
    }

    @Test
    void updatePC_rejectsInvalidPriestSpecialization() {
        pc.setClazz("cleric");
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));

        PC incoming = new PC();
        incoming.setId(1L);
        incoming.setClazz("cleric");
        incoming.setSpecialization("Protection");
        assertThatThrownBy(() -> pcService.updatePC(incoming, ownerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        verify(pcRepository, never()).save(any());
    }

    @Test
    void updatePCAsDm_acceptsPriestSpecialization() {
        pc.setCampaignId(7L);
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(campaignRepository.findById(7L)).thenReturn(Optional.of(campaignOwnedByDm()));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        PC cleric = new PC();
        cleric.setId(1L);
        cleric.setClazz("cleric");
        cleric.setSpecialization("Holy");
        assertThat(pcService.updatePCAsDm(cleric, null, dmId).getSpecialization()).isEqualTo("Holy");
    }
}
