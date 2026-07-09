package com.moo.charactermanagerservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moo.charactermanagerservice.dto.ClaimResult;
import com.moo.charactermanagerservice.dto.LootView;
import com.moo.charactermanagerservice.models.CombatSession;
import com.moo.charactermanagerservice.models.Encounter;
import com.moo.charactermanagerservice.models.EncounterLootItem;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.models.PcActivityType;
import com.moo.charactermanagerservice.models.SessionLoot;
import com.moo.charactermanagerservice.models.SessionLootItem;
import com.moo.charactermanagerservice.models.SessionParticipant;
import com.moo.charactermanagerservice.models.SessionStatus;
import com.moo.charactermanagerservice.models.SrdItem;
import com.moo.charactermanagerservice.repositories.CombatSessionRepository;
import com.moo.charactermanagerservice.repositories.EncounterLootItemRepository;
import com.moo.charactermanagerservice.repositories.EncounterRepository;
import com.moo.charactermanagerservice.repositories.PCRepository;
import com.moo.charactermanagerservice.repositories.SessionLootItemRepository;
import com.moo.charactermanagerservice.repositories.SessionLootRepository;
import com.moo.charactermanagerservice.repositories.SessionParticipantRepository;
import com.moo.charactermanagerservice.repositories.SrdItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the post-combat loot pool: DM lifecycle (open/seed/drop/edit),
 * per-caller visibility, and the first-come-first-served claim paths that write
 * through to the pc row. Pure Mockito — mirrors {@link ShopServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
class LootServiceTest {

    @Mock private CombatSessionRepository sessionRepository;
    @Mock private SessionLootRepository lootRepository;
    @Mock private SessionLootItemRepository lootItemRepository;
    @Mock private SessionParticipantRepository participantRepository;
    @Mock private EncounterRepository encounterRepository;
    @Mock private EncounterLootItemRepository encounterLootItemRepository;
    @Mock private SrdItemRepository srdItemRepository;
    @Mock private PCRepository pcRepository;
    @Mock private PcActivityLogService activityLogService;

    private LootService lootService;

    private UUID dmId;
    private UUID playerId;
    private UUID strangerId;
    private CombatSession session;
    private SessionLoot pool;

    @BeforeEach
    void setUp() {
        lootService = new LootService(sessionRepository, lootRepository, lootItemRepository,
                participantRepository, encounterRepository, encounterLootItemRepository,
                srdItemRepository, pcRepository, activityLogService, new ObjectMapper());

        dmId = UUID.randomUUID();
        playerId = UUID.randomUUID();
        strangerId = UUID.randomUUID();

        session = new CombatSession();
        session.setId(1L);
        session.setCampaignId(1L);
        session.setDmUserId(dmId);
        session.setStatus(SessionStatus.ACTIVE);

        pool = new SessionLoot();
        pool.setId(20L);
        pool.setSessionId(1L);
        pool.setName("Goblin Ambush");
        pool.setDropped(true);
        pool.setCoinCpTotal(12550L);
        pool.setCoinCpRemaining(12550L);
    }

    // --- openLoot ---

    @Test
    void openLoot_seedsFromEncounter_copyingLinesAndCoins() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(lootRepository.findBySessionId(1L)).thenReturn(Optional.empty());
        when(lootRepository.save(any(SessionLoot.class))).thenAnswer(inv -> {
            SessionLoot p = inv.getArgument(0);
            p.setId(20L);
            return p;
        });
        Encounter encounter = encounter(dmId, 1L);
        when(encounterRepository.findById(5L)).thenReturn(Optional.of(encounter));
        when(encounterLootItemRepository.findByEncounterIdOrderByIdAsc(5L)).thenReturn(List.of(
                encounterLine("longsword", null, null, 2),
                encounterLine(null, "Cloak of Elvenkind", "Advantage on Stealth.", 1)));
        when(lootItemRepository.findBySessionLootIdOrderByIdAsc(20L)).thenReturn(List.of());

        LootView view = lootService.openLoot(1L, 5L, null, dmId);

        assertThat(view.name()).isEqualTo("Goblin Ambush"); // defaults to the encounter's name
        assertThat(view.dropped()).isFalse();               // opens as an invisible draft
        assertThat(view.coinCpTotal()).isEqualTo(500L);
        assertThat(view.coinCpRemaining()).isEqualTo(500L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SessionLootItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(lootItemRepository).saveAll(captor.capture());
        List<SessionLootItem> copies = captor.getValue();
        assertThat(copies).hasSize(2);
        assertThat(copies.get(0).getCatalogItemKey()).isEqualTo("longsword");
        assertThat(copies.get(0).getQty()).isEqualTo(2);
        assertThat(copies.get(0).getQtyRemaining()).isEqualTo(2);
        assertThat(copies.get(1).getCustomName()).isEqualTo("Cloak of Elvenkind");
        assertThat(copies.get(1).getCustomNotes()).isEqualTo("Advantage on Stealth.");
        verify(sessionRepository).save(session); // version bumped
    }

    @Test
    void openLoot_replacesExistingPool() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        SessionLoot existing = new SessionLoot();
        existing.setId(19L);
        when(lootRepository.findBySessionId(1L)).thenReturn(Optional.of(existing));
        when(lootRepository.save(any(SessionLoot.class))).thenAnswer(inv -> {
            SessionLoot p = inv.getArgument(0);
            p.setId(20L);
            return p;
        });
        when(lootItemRepository.findBySessionLootIdOrderByIdAsc(20L)).thenReturn(List.of());

        lootService.openLoot(1L, null, "Bandit spoils", dmId);

        verify(lootItemRepository).deleteBySessionLootId(19L);
        verify(lootRepository).delete(existing);
    }

    @Test
    void openLoot_throws403_whenEncounterNotOwnedByDm() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(lootRepository.findBySessionId(1L)).thenReturn(Optional.empty());
        when(encounterRepository.findById(5L)).thenReturn(Optional.of(encounter(strangerId, 1L)));

        assertThatThrownBy(() -> lootService.openLoot(1L, 5L, null, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
    }

    @Test
    void openLoot_throws403_whenEncounterBelongsToOtherCampaign() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(lootRepository.findBySessionId(1L)).thenReturn(Optional.empty());
        when(encounterRepository.findById(5L)).thenReturn(Optional.of(encounter(dmId, 99L)));

        assertThatThrownBy(() -> lootService.openLoot(1L, 5L, null, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
    }

    @Test
    void openLoot_throws403_whenNotDm() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        assertThatThrownBy(() -> lootService.openLoot(1L, null, null, strangerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
    }

    @Test
    void openLoot_throws409_whenSessionEnded() {
        session.setStatus(SessionStatus.ENDED);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        assertThatThrownBy(() -> lootService.openLoot(1L, null, null, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
    }

    // --- dropLoot / visibility ---

    @Test
    void dropLoot_flipsVisibility_andBumpsVersion() {
        pool.setDropped(false);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(lootRepository.findBySessionId(1L)).thenReturn(Optional.of(pool));
        when(lootItemRepository.findBySessionLootIdOrderByIdAsc(20L)).thenReturn(List.of());

        LootView view = lootService.dropLoot(1L, dmId);

        assertThat(view.dropped()).isTrue();
        verify(lootRepository).save(pool);
        verify(sessionRepository).save(session);
    }

    @Test
    void getLoot_draftVisibleToDm_butNeverToPlayers() {
        pool.setDropped(false);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(lootRepository.findBySessionId(1L)).thenReturn(Optional.of(pool));
        when(lootItemRepository.findBySessionLootIdOrderByIdAsc(20L)).thenReturn(List.of());

        assertThat(lootService.getLootForUser(1L, dmId)).isNotNull();
        assertThat(lootService.getLootForUser(1L, playerId)).isNull();
    }

    @Test
    void getLoot_droppedVisibleToSeatedPlayer_notToStranger() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(lootRepository.findBySessionId(1L)).thenReturn(Optional.of(pool));
        when(participantRepository.findBySessionIdOrderByOrderIndexAsc(1L))
                .thenReturn(List.of(participant(7L, playerId)));
        when(lootItemRepository.findBySessionLootIdOrderByIdAsc(20L)).thenReturn(List.of());

        assertThat(lootService.getLootForUser(1L, playerId)).isNotNull();
        assertThat(lootService.getLootForUser(1L, strangerId)).isNull();
    }

    // --- DM edits ---

    @Test
    void addItem_throws400_whenBothOrNeitherOfKeyAndName() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(lootRepository.findBySessionId(1L)).thenReturn(Optional.of(pool));

        assertThatThrownBy(() -> lootService.addItem(1L, "longsword", "Cloak", null, 1, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        assertThatThrownBy(() -> lootService.addItem(1L, null, "  ", null, 1, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        verify(lootItemRepository, never()).save(any());
    }

    @Test
    void addItem_throws404_whenCatalogKeyUnknown() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(lootRepository.findBySessionId(1L)).thenReturn(Optional.of(pool));
        when(srdItemRepository.findByItemKey("frostbrand")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> lootService.addItem(1L, "frostbrand", null, null, 1, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(404));
    }

    @Test
    void updateItem_qtyChange_shiftsRemainingByDelta_clampedAtZero() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(lootRepository.findBySessionId(1L)).thenReturn(Optional.of(pool));
        SessionLootItem line = poolLine(30L, "longsword", null, 3);
        line.setQtyRemaining(1); // 2 already claimed
        when(lootItemRepository.findById(30L)).thenReturn(Optional.of(line));
        when(lootItemRepository.findBySessionLootIdOrderByIdAsc(20L)).thenReturn(List.of());

        lootService.updateItem(1L, 30L, 5, null, null, dmId); // +2

        assertThat(line.getQty()).isEqualTo(5);
        assertThat(line.getQtyRemaining()).isEqualTo(3);

        lootService.updateItem(1L, 30L, 1, null, null, dmId); // -4, remaining clamps at 0
        assertThat(line.getQtyRemaining()).isEqualTo(0);
    }

    @Test
    void setCoins_shiftsRemainingByDelta() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        pool.setCoinCpTotal(1000L);
        pool.setCoinCpRemaining(400L); // 600 already claimed
        when(lootRepository.findBySessionId(1L)).thenReturn(Optional.of(pool));
        when(lootItemRepository.findBySessionLootIdOrderByIdAsc(20L)).thenReturn(List.of());

        lootService.setCoins(1L, 15.0, dmId); // total 1000 → 1500

        assertThat(pool.getCoinCpTotal()).isEqualTo(1500L);
        assertThat(pool.getCoinCpRemaining()).isEqualTo(900L);
    }

    // --- claimItem ---

    @Test
    void claimItem_decrementsRemaining_appendsSnapshot_andLogs() {
        stubClaimableSessionWithSeatedPc();
        SessionLootItem line = poolLine(30L, "longsword", null, 2);
        when(lootItemRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(line));
        when(srdItemRepository.findByItemKey("longsword")).thenReturn(Optional.of(longsword()));
        PC pc = pcOwnedBy(playerId, "{\"gp\":5}", null);
        when(pcRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));
        when(lootItemRepository.findBySessionLootIdOrderByIdAsc(20L)).thenReturn(List.of(line));
        when(srdItemRepository.findByItemKeyIn(anyList())).thenReturn(List.of(longsword()));

        ClaimResult result = lootService.claimItem(1L, 7L, 30L, 1, playerId);

        assertThat(line.getQtyRemaining()).isEqualTo(1);
        assertThat(result.inventory()).hasSize(1);
        assertThat(result.inventory().get(0))
                .containsEntry("catalogKey", "longsword")
                .containsEntry("category", "weapon")
                .containsEntry("qty", 1)
                .doesNotContainKey("unitCostCp"); // nothing was paid
        assertThat(result.coins()).containsEntry("gp", 5); // purse untouched
        assertThat(result.loot().items().get(0).qtyRemaining()).isEqualTo(1);
        verify(activityLogService).log(7L, PcActivityType.LOOT, "Looted Longsword", playerId);
        verify(sessionRepository).save(session); // claims bump — everyone re-fetches the pool
    }

    @Test
    void claimItem_customLine_appendsGearEntryWithNotes() {
        stubClaimableSessionWithSeatedPc();
        SessionLootItem line = poolLine(31L, null, "Cloak of Elvenkind", 1);
        line.setCustomNotes("Advantage on Stealth.");
        when(lootItemRepository.findByIdForUpdate(31L)).thenReturn(Optional.of(line));
        PC pc = pcOwnedBy(playerId, null, null);
        when(pcRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));
        when(lootItemRepository.findBySessionLootIdOrderByIdAsc(20L)).thenReturn(List.of(line));

        ClaimResult result = lootService.claimItem(1L, 7L, 31L, 1, playerId);

        assertThat(result.inventory().get(0))
                .containsEntry("name", "Cloak of Elvenkind")
                .containsEntry("category", "gear")
                .containsEntry("notes", "Advantage on Stealth.")
                .doesNotContainKey("catalogKey");
        verify(activityLogService).log(7L, PcActivityType.LOOT, "Looted Cloak of Elvenkind", playerId);
    }

    @Test
    void claimItem_throws409_whenNotEnoughLeft() {
        stubClaimableSessionWithSeatedPc();
        SessionLootItem line = poolLine(30L, "longsword", null, 2);
        line.setQtyRemaining(0);
        when(lootItemRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(line));

        assertThatThrownBy(() -> lootService.claimItem(1L, 7L, 30L, 1, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
        verify(pcRepository, never()).save(any());
    }

    @Test
    void claimItem_throws404_whenPoolNotDropped() {
        pool.setDropped(false);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(lootRepository.findBySessionId(1L)).thenReturn(Optional.of(pool));

        assertThatThrownBy(() -> lootService.claimItem(1L, 7L, 30L, 1, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(404));
    }

    @Test
    void claimItem_throws409_whenSessionEnded() {
        session.setStatus(SessionStatus.ENDED);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> lootService.claimItem(1L, 7L, 30L, 1, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
    }

    @Test
    void claimItem_throws403_whenPcNotSeated() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(lootRepository.findBySessionId(1L)).thenReturn(Optional.of(pool));
        when(participantRepository.findBySessionIdAndPcId(1L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> lootService.claimItem(1L, 7L, 30L, 1, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
    }

    @Test
    void claimItem_throws403_whenClaimingAnotherUsersPc() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(lootRepository.findBySessionId(1L)).thenReturn(Optional.of(pool));
        when(participantRepository.findBySessionIdAndPcId(1L, 7L))
                .thenReturn(Optional.of(participant(7L, strangerId)));

        assertThatThrownBy(() -> lootService.claimItem(1L, 7L, 30L, 1, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
    }

    // --- claimCoins ---

    @Test
    void claimCoins_creditsPurse_andDecrementsPile() {
        stubClaimableSessionWithSeatedPc();
        when(lootRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(pool));
        PC pc = pcOwnedBy(playerId, "{\"gp\":1}", null);
        when(pcRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));
        when(lootItemRepository.findBySessionLootIdOrderByIdAsc(20L)).thenReturn(List.of());

        ClaimResult result = lootService.claimCoins(1L, 7L, Map.of("gp", 25), playerId);

        assertThat(pool.getCoinCpRemaining()).isEqualTo(12550L - 2500L);
        // 1 gp + 25 gp = 2600 cp, re-minted greedily → 2 pp 6 gp
        assertThat(result.coins()).containsEntry("pp", 2).containsEntry("gp", 6);
        assertThat(result.loot().coinCpRemaining()).isEqualTo(10050L);
        // format() renders greedily, like purchase logs: 2500 cp → "2 pp 5 gp"
        verify(activityLogService).log(7L, PcActivityType.LOOT, "Looted 2 pp 5 gp", playerId);
        verify(sessionRepository).save(session);
    }

    @Test
    void claimCoins_throws409_whenPileTooSmall() {
        stubClaimableSessionWithSeatedPc();
        pool.setCoinCpRemaining(100L);
        when(lootRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(pool));

        assertThatThrownBy(() -> lootService.claimCoins(1L, 7L, Map.of("gp", 25), playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
        verify(pcRepository, never()).save(any());
    }

    @Test
    void claimCoins_throws400_whenAmountNotPositive() {
        assertThatThrownBy(() -> lootService.claimCoins(1L, 7L, Map.of(), playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
    }

    // --- helpers ---

    private void stubClaimableSessionWithSeatedPc() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(lootRepository.findBySessionId(1L)).thenReturn(Optional.of(pool));
        when(participantRepository.findBySessionIdAndPcId(1L, 7L))
                .thenReturn(Optional.of(participant(7L, playerId)));
    }

    private static int status(Throwable e) {
        return ((ResponseStatusException) e).getStatusCode().value();
    }

    private static Encounter encounter(UUID owner, Long campaignId) {
        Encounter e = new Encounter();
        e.setId(5L);
        e.setCampaignId(campaignId);
        e.setDmUserId(owner);
        e.setName("Goblin Ambush");
        e.setLootCoinCp(500L);
        return e;
    }

    private static EncounterLootItem encounterLine(String key, String customName, String notes, int qty) {
        EncounterLootItem line = new EncounterLootItem();
        line.setEncounterId(5L);
        line.setCatalogItemKey(key);
        line.setCustomName(customName);
        line.setCustomNotes(notes);
        line.setQty(qty);
        return line;
    }

    private static SessionLootItem poolLine(Long id, String key, String customName, int qty) {
        SessionLootItem line = new SessionLootItem();
        line.setId(id);
        line.setSessionLootId(20L);
        line.setCatalogItemKey(key);
        line.setCustomName(customName);
        line.setQty(qty);
        line.setQtyRemaining(qty);
        return line;
    }

    private static SrdItem longsword() {
        SrdItem item = new SrdItem();
        item.setId(1L);
        item.setItemKey("longsword");
        item.setName("Longsword");
        item.setCategory("WEAPON");
        item.setCostCp(1500L);
        item.setDetails("{\"damage\":\"1d8 slashing\",\"properties\":[\"versatile (1d10)\"]}");
        return item;
    }

    private static SessionParticipant participant(Long pcId, UUID owner) {
        SessionParticipant p = new SessionParticipant();
        p.setId(pcId);
        p.setPcId(pcId);
        p.setOwnerUserId(owner);
        return p;
    }

    private static PC pcOwnedBy(UUID owner, String coinsJson, String inventoryJson) {
        PC pc = new PC();
        pc.setId(7L);
        pc.setUserId(owner);
        pc.setCoins(coinsJson);
        pc.setInventory(inventoryJson);
        return pc;
    }
}
