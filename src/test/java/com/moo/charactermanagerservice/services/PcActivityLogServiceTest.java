package com.moo.charactermanagerservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.models.PcActivityLog;
import com.moo.charactermanagerservice.models.PcActivityType;
import com.moo.charactermanagerservice.repositories.PcActivityLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PcActivityLogService}: the plain save/prune paths and
 * the {@code logDmEdit} before/after diff. Pure Mockito, no DB — mirrors
 * {@code ShopServiceTest}'s style.
 */
@ExtendWith(MockitoExtension.class)
class PcActivityLogServiceTest {

    @Mock
    private PcActivityLogRepository activityLogRepository;

    private PcActivityLogService activityLogService;

    private UUID actorId;

    @BeforeEach
    void setUp() {
        activityLogService = new PcActivityLogService(activityLogRepository, new ObjectMapper());
        actorId = UUID.randomUUID();
    }

    // --- log / logAll --------------------------------------------------------

    @Test
    void log_savesTheEntry_andPrunes() {
        when(activityLogRepository.save(any(PcActivityLog.class))).thenAnswer(inv -> inv.getArgument(0));

        activityLogService.log(1L, PcActivityType.LEVEL_UP, "Leveled up to 5", actorId);

        ArgumentCaptor<PcActivityLog> captor = ArgumentCaptor.forClass(PcActivityLog.class);
        verify(activityLogRepository).save(captor.capture());
        PcActivityLog saved = captor.getValue();
        assertThat(saved.getPcId()).isEqualTo(1L);
        assertThat(saved.getActionType()).isEqualTo(PcActivityType.LEVEL_UP);
        assertThat(saved.getDescription()).isEqualTo("Leveled up to 5");
        assertThat(saved.getActorUserId()).isEqualTo(actorId);
        verify(activityLogRepository).pruneToLatest(1L);
    }

    @Test
    void logAll_savesEachEntry_andPrunesOnce() {
        activityLogService.logAll(1L, PcActivityType.DM_EDIT,
                List.of("DM changed AC 15 → 16", "DM added Longsword"), actorId);

        verify(activityLogRepository, times(2)).save(any(PcActivityLog.class));
        verify(activityLogRepository, times(1)).pruneToLatest(1L);
    }

    @Test
    void logAll_noOps_onEmptyList() {
        activityLogService.logAll(1L, PcActivityType.DM_EDIT, List.of(), actorId);

        verify(activityLogRepository, never()).save(any());
        verify(activityLogRepository, never()).pruneToLatest(any());
    }

    @Test
    void latestFor_delegatesToTheRepository() {
        PcActivityLog entry = new PcActivityLog();
        when(activityLogRepository.findTop10ByPcIdOrderByCreatedAtDescIdDesc(1L)).thenReturn(List.of(entry));

        assertThat(activityLogService.latestFor(1L)).containsExactly(entry);
    }

    // --- logDmEdit: scalar diffs ---------------------------------------------

    @Test
    void logDmEdit_logsOneEntry_perScalarChange() {
        PC before = new PC();
        before.setId(1L);
        before.setAc((short) 15);
        PC after = new PC();
        after.setId(1L);
        after.setAc((short) 16);

        activityLogService.logDmEdit(before, after, actorId);

        verify(activityLogRepository).save(argThat(e -> "DM changed AC 15 → 16".equals(e.getDescription())));
        verify(activityLogRepository).pruneToLatest(1L);
    }

    @Test
    void logDmEdit_rendersNullAsAnEmDash() {
        PC before = new PC();
        before.setId(1L);
        before.setLevel((short) 3);
        PC after = new PC();
        after.setId(1L);
        after.setLevel(null);

        activityLogService.logDmEdit(before, after, actorId);

        verify(activityLogRepository).save(argThat(e -> "DM changed level 3 → —".equals(e.getDescription())));
    }

    @Test
    void logDmEdit_noOps_whenNothingChanged() {
        PC before = new PC();
        before.setId(1L);
        before.setAc((short) 15);
        PC after = new PC();
        after.setId(1L);
        after.setAc((short) 15);

        activityLogService.logDmEdit(before, after, actorId);

        verify(activityLogRepository, never()).save(any());
        verify(activityLogRepository, never()).pruneToLatest(any());
    }

    // --- logDmEdit: coins ------------------------------------------------------

    @Test
    void logDmEdit_logsCoins_whenTotalValueDiffers() {
        PC before = new PC();
        before.setId(1L);
        before.setCoins("{\"gp\":3}");
        PC after = new PC();
        after.setId(1L);
        after.setCoins("{\"gp\":6}");

        activityLogService.logDmEdit(before, after, actorId);

        verify(activityLogRepository).save(argThat(e ->
                "DM changed coins 3 gp → 6 gp".equals(e.getDescription())));
    }

    @Test
    void logDmEdit_coins_noOps_onADenominationReshuffleWithTheSameTotal() {
        PC before = new PC();
        before.setId(1L);
        before.setCoins("{\"gp\":1}"); // 100 cp
        PC after = new PC();
        after.setId(1L);
        after.setCoins("{\"sp\":10}"); // also 100 cp

        activityLogService.logDmEdit(before, after, actorId);

        verify(activityLogRepository, never()).save(any());
    }

    // --- logDmEdit: inventory ---------------------------------------------------

    @Test
    void logDmEdit_inventory_logsAddedAndRemovedItems() {
        PC before = new PC();
        before.setId(1L);
        before.setInventory("[{\"name\":\"Shortbow\",\"qty\":1}]");
        PC after = new PC();
        after.setId(1L);
        after.setInventory("[{\"name\":\"Longsword\",\"qty\":1}]");

        activityLogService.logDmEdit(before, after, actorId);

        verify(activityLogRepository).save(argThat(e -> "DM added Longsword".equals(e.getDescription())));
        verify(activityLogRepository).save(argThat(e -> "DM removed Shortbow".equals(e.getDescription())));
    }

    @Test
    void logDmEdit_inventory_logsQuantityIncreaseWithMultiplier() {
        PC before = new PC();
        before.setId(1L);
        before.setInventory("[{\"name\":\"Rations\",\"qty\":1}]");
        PC after = new PC();
        after.setId(1L);
        after.setInventory("[{\"name\":\"Rations\",\"qty\":4}]");

        activityLogService.logDmEdit(before, after, actorId);

        verify(activityLogRepository).save(argThat(e -> "DM added Rations ×3".equals(e.getDescription())));
    }

    // --- logDmEdit: features / spells -------------------------------------------

    @Test
    void logDmEdit_logsAddedFeatureAndSpell() {
        PC before = new PC();
        before.setId(1L);
        PC after = new PC();
        after.setId(1L);
        after.setFeatures("[{\"name\":\"Darkvision\"}]");
        after.setSpells("[{\"name\":\"Fireball\"}]");

        activityLogService.logDmEdit(before, after, actorId);

        verify(activityLogRepository).save(argThat(e -> "DM added feature Darkvision".equals(e.getDescription())));
        verify(activityLogRepository).save(argThat(e -> "DM added spell Fireball".equals(e.getDescription())));
    }

    // --- logDmEdit: malformed JSON -----------------------------------------------

    @Test
    void logDmEdit_malformedJson_parsesAsEmpty_neverThrows() {
        PC before = new PC();
        before.setId(1L);
        before.setInventory("not json");
        before.setCoins("also not json");
        PC after = new PC();
        after.setId(1L);
        after.setInventory("[{\"name\":\"Longsword\",\"qty\":1}]");
        after.setCoins("{\"gp\":5}");

        activityLogService.logDmEdit(before, after, actorId);

        // Malformed before-JSON parses as empty, so the after side looks like a
        // pure addition rather than a diff or a thrown exception.
        verify(activityLogRepository).save(argThat(e -> "DM added Longsword".equals(e.getDescription())));
        verify(activityLogRepository).save(argThat(e -> "DM changed coins 0 cp → 5 gp".equals(e.getDescription())));
    }

    // --- logDmEdit: identical PCs -------------------------------------------

    @Test
    void logDmEdit_identicalPCs_writesNothing() {
        PC before = new PC();
        before.setId(1L);
        before.setLevel((short) 5);
        before.setCoins("{\"gp\":10}");
        before.setInventory("[{\"name\":\"Longsword\",\"qty\":1}]");
        PC after = new PC();
        after.setId(1L);
        after.setLevel((short) 5);
        after.setCoins("{\"gp\":10}");
        after.setInventory("[{\"name\":\"Longsword\",\"qty\":1}]");

        activityLogService.logDmEdit(before, after, actorId);

        verify(activityLogRepository, never()).save(any());
        verify(activityLogRepository, never()).pruneToLatest(any());
    }

    // --- logDmEdit: the >3 changes cap -------------------------------------------

    @Test
    void logDmEdit_summarizes_whenMoreThanThreeChanges() {
        PC before = new PC();
        before.setId(1L);
        before.setAc((short) 15);
        before.setHpMax((short) 20);
        before.setHpCurrent((short) 20);
        before.setLevel((short) 4);
        before.setXp(100);

        PC after = new PC();
        after.setId(1L);
        after.setAc((short) 16);
        after.setHpMax((short) 25);
        after.setHpCurrent((short) 25);
        after.setLevel((short) 5);
        after.setXp(200);

        activityLogService.logDmEdit(before, after, actorId);

        // 5 scalar changes > cap of 3 -> a single summary row.
        ArgumentCaptor<PcActivityLog> captor = ArgumentCaptor.forClass(PcActivityLog.class);
        verify(activityLogRepository, times(1)).save(captor.capture());
        String description = captor.getValue().getDescription();
        assertThat(description).startsWith("DM edited the sheet:");
        assertThat(description).contains("and 2 more");
        verify(activityLogRepository).pruneToLatest(1L);
    }

    // --- logDmEdit(4-arg): DM-authored description -------------------------------

    @Test
    void logDmEdit_withDescription_logsOneVerbatimEntry_noDiff() {
        PC before = new PC();
        before.setId(1L);
        before.setAc((short) 15);
        PC after = new PC();
        after.setId(1L);
        after.setAc((short) 16);

        activityLogService.logDmEdit(before, after, actorId, "DM changed AC 15 -> 16 for cover");

        ArgumentCaptor<PcActivityLog> captor = ArgumentCaptor.forClass(PcActivityLog.class);
        verify(activityLogRepository, times(1)).save(captor.capture());
        PcActivityLog saved = captor.getValue();
        assertThat(saved.getDescription()).isEqualTo("DM changed AC 15 -> 16 for cover");
        assertThat(saved.getActionType()).isEqualTo(PcActivityType.DM_EDIT);
        verify(activityLogRepository).pruneToLatest(1L);
    }

    @Test
    void logDmEdit_withDescription_trimsWhitespace() {
        PC before = new PC();
        before.setId(1L);
        PC after = new PC();
        after.setId(1L);

        activityLogService.logDmEdit(before, after, actorId, "   Custom note   ");

        verify(activityLogRepository).save(argThat(e -> "Custom note".equals(e.getDescription())));
    }

    @Test
    void logDmEdit_withDescription_capsAt500Characters() {
        PC before = new PC();
        before.setId(1L);
        PC after = new PC();
        after.setId(1L);
        String longDescription = "x".repeat(600);

        activityLogService.logDmEdit(before, after, actorId, longDescription);

        ArgumentCaptor<PcActivityLog> captor = ArgumentCaptor.forClass(PcActivityLog.class);
        verify(activityLogRepository).save(captor.capture());
        assertThat(captor.getValue().getDescription()).hasSize(500);
    }

    @Test
    void logDmEdit_nullDescription_fallsBackToAutoDiff() {
        PC before = new PC();
        before.setId(1L);
        before.setAc((short) 15);
        PC after = new PC();
        after.setId(1L);
        after.setAc((short) 16);

        activityLogService.logDmEdit(before, after, actorId, null);

        verify(activityLogRepository).save(argThat(e -> "DM changed AC 15 → 16".equals(e.getDescription())));
    }

    @Test
    void logDmEdit_blankDescription_fallsBackToAutoDiff() {
        PC before = new PC();
        before.setId(1L);
        before.setAc((short) 15);
        PC after = new PC();
        after.setId(1L);
        after.setAc((short) 16);

        activityLogService.logDmEdit(before, after, actorId, "   ");

        verify(activityLogRepository).save(argThat(e -> "DM changed AC 15 → 16".equals(e.getDescription())));
    }

    @Test
    void logDmEdit_withDescription_logsEvenWhenNoFieldsChanged() {
        PC before = new PC();
        before.setId(1L);
        before.setAc((short) 15);
        PC after = new PC();
        after.setId(1L);
        after.setAc((short) 15);

        activityLogService.logDmEdit(before, after, actorId, "DM note with no stat change");

        verify(activityLogRepository).save(argThat(e -> "DM note with no stat change".equals(e.getDescription())));
        verify(activityLogRepository).pruneToLatest(1L);
    }
}
