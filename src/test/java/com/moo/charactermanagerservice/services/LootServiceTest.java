package com.moo.charactermanagerservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moo.charactermanagerservice.dto.AddLootItemRequest;
import com.moo.charactermanagerservice.dto.LootView;
import com.moo.charactermanagerservice.models.CombatSession;
import com.moo.charactermanagerservice.models.CuratedLoot;
import com.moo.charactermanagerservice.models.CuratedLootItem;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.models.PcActivityType;
import com.moo.charactermanagerservice.models.SessionLoot;
import com.moo.charactermanagerservice.models.SessionLootAllocation;
import com.moo.charactermanagerservice.models.SessionLootBallot;
import com.moo.charactermanagerservice.models.SessionLootResult;
import com.moo.charactermanagerservice.models.SessionLootItem;
import com.moo.charactermanagerservice.models.SessionParticipant;
import com.moo.charactermanagerservice.models.SessionStatus;
import com.moo.charactermanagerservice.models.SrdItem;
import com.moo.charactermanagerservice.repositories.CombatSessionRepository;
import com.moo.charactermanagerservice.repositories.CuratedLootItemRepository;
import com.moo.charactermanagerservice.repositories.CuratedLootRepository;
import com.moo.charactermanagerservice.repositories.PCRepository;
import com.moo.charactermanagerservice.repositories.SessionLootAllocationRepository;
import com.moo.charactermanagerservice.repositories.SessionLootBallotRepository;
import com.moo.charactermanagerservice.repositories.SessionLootItemRepository;
import com.moo.charactermanagerservice.repositories.SessionLootResultRepository;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
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
 * per-caller visibility, and the party split — moving shares, locking it for a
 * vote, and every way a vote resolves (PvP cancel, majority distribution into
 * the pc rows, no majority, DM override). Pure Mockito — mirrors
 * {@link ShopServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
class LootServiceTest {

    @Mock private CombatSessionRepository sessionRepository;
    @Mock private SessionLootRepository lootRepository;
    @Mock private SessionLootItemRepository lootItemRepository;
    @Mock private SessionLootAllocationRepository allocationRepository;
    @Mock private SessionLootBallotRepository ballotRepository;
    @Mock private SessionLootResultRepository resultRepository;
    @Mock private SessionParticipantRepository participantRepository;
    @Mock private CuratedLootRepository curatedLootRepository;
    @Mock private CuratedLootItemRepository curatedLootItemRepository;
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
                allocationRepository, ballotRepository, resultRepository, participantRepository, curatedLootRepository, curatedLootItemRepository,
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
        pool.setSplitPhase("SPLITTING");
        pool.setCoinCpTotal(12550L);
        pool.setCoinCpRemaining(12550L);
    }

    // --- openLoot ---

    @Test
    void openLoot_seedsFromCuratedList_copyingLinesCoinsAndAttributes() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(lootRepository.findBySessionId(1L)).thenReturn(Optional.empty());
        when(lootRepository.save(any(SessionLoot.class))).thenAnswer(inv -> {
            SessionLoot p = inv.getArgument(0);
            p.setId(20L);
            return p;
        });
        CuratedLoot curated = curatedList(dmId, 1L);
        when(curatedLootRepository.findById(5L)).thenReturn(Optional.of(curated));
        CuratedLootItem rich = curatedLine(null, "Flametongue", "Ignites on command.", 1);
        rich.setCategory("weapon");
        rich.setUnitCostCp(500000L);
        rich.setWeight(new BigDecimal("3"));
        rich.setDamage("1d8 slashing + 2d6 fire");
        when(curatedLootItemRepository.findByLootIdOrderByIdAsc(5L)).thenReturn(List.of(
                curatedLine("longsword", null, null, 2), rich));
        when(lootItemRepository.findBySessionLootIdOrderByIdAsc(20L)).thenReturn(List.of());

        LootView view = lootService.openLoot(1L, 5L, null, dmId);

        assertThat(view.name()).isEqualTo("Goblin Ambush loot"); // defaults to the list's name
        assertThat(view.dropped()).isFalse();                    // opens as an invisible draft
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
        assertThat(copies.get(1).getCustomName()).isEqualTo("Flametongue");
        assertThat(copies.get(1).getCustomNotes()).isEqualTo("Ignites on command.");
        // The custom line's attributes ride the copy so claims can stamp them.
        assertThat(copies.get(1).getCategory()).isEqualTo("weapon");
        assertThat(copies.get(1).getUnitCostCp()).isEqualTo(500000L);
        assertThat(copies.get(1).getWeight()).isEqualByComparingTo("3");
        assertThat(copies.get(1).getDamage()).isEqualTo("1d8 slashing + 2d6 fire");
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
        verify(allocationRepository).deleteBySessionLootId(19L);
        verify(lootRepository).delete(existing);
    }

    @Test
    void openLoot_throws403_whenListNotOwnedByDm() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(lootRepository.findBySessionId(1L)).thenReturn(Optional.empty());
        when(curatedLootRepository.findById(5L)).thenReturn(Optional.of(curatedList(strangerId, 1L)));

        assertThatThrownBy(() -> lootService.openLoot(1L, 5L, null, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
    }

    @Test
    void openLoot_throws403_whenListBelongsToOtherCampaign() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(lootRepository.findBySessionId(1L)).thenReturn(Optional.empty());
        when(curatedLootRepository.findById(5L)).thenReturn(Optional.of(curatedList(dmId, 99L)));

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
        assertThat(view.phase()).isEqualTo("SPLITTING"); // the party starts dividing it
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

        assertThatThrownBy(() -> lootService.addItem(1L, addRequest("longsword", "Cloak"), dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        assertThatThrownBy(() -> lootService.addItem(1L, addRequest(null, "  "), dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        verify(lootItemRepository, never()).save(any());
    }

    @Test
    void addItem_throws404_whenCatalogKeyUnknown() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(lootRepository.findBySessionId(1L)).thenReturn(Optional.of(pool));
        when(srdItemRepository.findByItemKey("frostbrand")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> lootService.addItem(1L, addRequest("frostbrand", null), dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(404));
    }

    @Test
    void addItem_customLine_stampsAttributes() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(lootRepository.findBySessionId(1L)).thenReturn(Optional.of(pool));
        when(lootItemRepository.findBySessionLootIdOrderByIdAsc(20L)).thenReturn(List.of());

        lootService.addItem(1L, new AddLootItemRequest(null, "Flametongue", "Ignites.", 1,
                "weapon", 50.0, 3.0, "1d8 slashing + 2d6 fire", null), dmId);

        ArgumentCaptor<SessionLootItem> captor = ArgumentCaptor.forClass(SessionLootItem.class);
        verify(lootItemRepository).save(captor.capture());
        SessionLootItem saved = captor.getValue();
        assertThat(saved.getCategory()).isEqualTo("weapon");
        assertThat(saved.getUnitCostCp()).isEqualTo(5000L);
        assertThat(saved.getWeight()).isEqualByComparingTo("3.0");
        assertThat(saved.getDamage()).isEqualTo("1d8 slashing + 2d6 fire");
    }

    @Test
    void addItem_throws400_whenCatalogLineCarriesAttributes() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(lootRepository.findBySessionId(1L)).thenReturn(Optional.of(pool));

        assertThatThrownBy(() -> lootService.addItem(1L, new AddLootItemRequest(
                "longsword", null, null, 1, "weapon", null, null, null, null), dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        verify(lootItemRepository, never()).save(any());
    }

    @Test
    void updateItem_qtyBelowWhatWasShared_returnsThatLinesSharesToThePile() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(lootRepository.findBySessionId(1L)).thenReturn(Optional.of(pool));
        SessionLootItem line = poolLine(30L, "longsword", null, 3);
        when(lootItemRepository.findById(30L)).thenReturn(Optional.of(line));
        when(allocationRepository.findBySessionLootIdOrderByIdAsc(20L))
                .thenReturn(List.of(itemShare(101L, 30L, 2)));
        when(lootItemRepository.findBySessionLootIdOrderByIdAsc(20L)).thenReturn(List.of());

        lootService.updateItem(1L, 30L, 1, null, null, dmId);

        assertThat(line.getQty()).isEqualTo(1);
        verify(allocationRepository).deleteBySessionLootIdAndLootItemId(20L, 30L);
    }

    @Test
    void setCoins_belowWhatWasShared_returnsEveryCoinShareToThePile() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(lootRepository.findBySessionId(1L)).thenReturn(Optional.of(pool));
        when(allocationRepository.findBySessionLootIdOrderByIdAsc(20L))
                .thenReturn(List.of(coinShare(101L, 6000L), coinShare(102L, 6000L)));
        when(lootItemRepository.findBySessionLootIdOrderByIdAsc(20L)).thenReturn(List.of());

        lootService.setCoins(1L, 100.0, dmId); // 12 000 cp shared > 10 000 cp

        assertThat(pool.getCoinCpTotal()).isEqualTo(10000L);
        verify(allocationRepository).deleteBySessionLootIdAndLootItemIdIsNull(20L);
    }

    @Test
    void addItem_whileVoting_reopensTheSplit_andDropsTheBallots() {
        pool.setSplitPhase("VOTING");
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(lootRepository.findBySessionId(1L)).thenReturn(Optional.of(pool));
        when(lootItemRepository.findBySessionLootIdOrderByIdAsc(20L)).thenReturn(List.of());

        lootService.addItem(1L, addRequest(null, "Silver Idol"), dmId);

        assertThat(pool.getSplitPhase()).isEqualTo("SPLITTING");
        verify(ballotRepository).deleteBySessionLootId(20L);
    }

    // --- split: moving shares ---

    @Test
    void assignItem_pileToCharacter_createsAShare_andBumps() {
        stubSplit(List.of(), poolLine(30L, "longsword", null, 2));

        lootService.assignItem(1L, 30L, null, 102L, 1, playerId);

        ArgumentCaptor<SessionLootAllocation> captor = ArgumentCaptor.forClass(SessionLootAllocation.class);
        verify(allocationRepository).save(captor.capture());
        assertThat(captor.getValue().getParticipantId()).isEqualTo(102L); // any seat — not just your own
        assertThat(captor.getValue().getLootItemId()).isEqualTo(30L);
        assertThat(captor.getValue().getQty()).isEqualTo(1);
        verify(sessionRepository).save(session);
    }

    @Test
    void assignItem_characterToCharacter_movesTheShare() {
        SessionLootAllocation held = itemShare(101L, 30L, 2);
        stubSplit(List.of(held), poolLine(30L, "longsword", null, 2));

        lootService.assignItem(1L, 30L, 101L, 103L, 1, playerId);

        assertThat(held.getQty()).isEqualTo(1);
        ArgumentCaptor<SessionLootAllocation> captor = ArgumentCaptor.forClass(SessionLootAllocation.class);
        verify(allocationRepository, times(2)).save(captor.capture());
        SessionLootAllocation created = captor.getAllValues().get(1);
        assertThat(created).isNotSameAs(held);
        assertThat(created.getParticipantId()).isEqualTo(103L);
        assertThat(created.getQty()).isEqualTo(1);
    }

    @Test
    void assignItem_backToPile_deletesAnEmptiedShare() {
        SessionLootAllocation held = itemShare(101L, 30L, 1);
        stubSplit(List.of(held), poolLine(30L, "longsword", null, 1));

        lootService.assignItem(1L, 30L, 101L, null, 1, playerId);

        verify(allocationRepository).delete(held);
    }

    @Test
    void assignItem_throws409_whenThePileIsShort() {
        stubSplit(List.of(itemShare(101L, 30L, 2)), poolLine(30L, "longsword", null, 2));

        assertThatThrownBy(() -> lootService.assignItem(1L, 30L, null, 102L, 1, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
        verify(allocationRepository, never()).save(any());
    }

    @Test
    void assignItem_throws409_whileThePartyIsVoting() {
        pool.setSplitPhase("VOTING");
        stubSplit(List.of(), poolLine(30L, "longsword", null, 2));

        assertThatThrownBy(() -> lootService.assignItem(1L, 30L, null, 102L, 1, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
    }

    @Test
    void assignItem_throws403_forSomeoneWithoutASeat() {
        stubSplit(List.of(), poolLine(30L, "longsword", null, 2));

        assertThatThrownBy(() -> lootService.assignItem(1L, 30L, null, 102L, 1, strangerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
    }

    @Test
    void assignItem_throws400_whenTheTargetIsNotSeated() {
        stubSplit(List.of(), poolLine(30L, "longsword", null, 2));

        assertThatThrownBy(() -> lootService.assignItem(1L, 30L, null, 999L, 1, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
    }

    @Test
    void setCoinShare_throws409_whenItWouldExceedThePile() {
        stubSplit(List.of(coinShare(102L, 10000L)));

        assertThatThrownBy(() -> lootService.setCoinShare(1L, 101L, 3000L, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
    }

    @Test
    void splitCoinsEvenly_replacesShares_leftoverCopperToTheFirstSeats() {
        pool.setCoinCpTotal(1001L);
        stubSplit(List.of());

        lootService.splitCoinsEvenly(1L, playerId);

        verify(allocationRepository).deleteBySessionLootIdAndLootItemIdIsNull(20L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SessionLootAllocation>> captor = ArgumentCaptor.forClass(List.class);
        verify(allocationRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(SessionLootAllocation::getCoinCp)
                .containsExactly(334L, 334L, 333L);
    }

    // --- split: locking it for a vote ---

    @Test
    void proposeSplit_throws409_whileAnythingIsStillInThePile() {
        stubSplit(List.of(itemShare(101L, 30L, 1), coinShare(101L, 12550L)),
                poolLine(30L, "longsword", null, 2));

        assertThatThrownBy(() -> lootService.proposeSplit(1L, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
        verify(ballotRepository, never()).saveAll(anyList());
    }

    @Test
    void proposeSplit_locksIt_andGivesEverySeatedCharacterABallot() {
        stubSplit(List.of(itemShare(101L, 30L, 2), coinShare(102L, 12550L)),
                poolLine(30L, "longsword", null, 2));

        lootService.proposeSplit(1L, playerId);

        assertThat(pool.getSplitPhase()).isEqualTo("VOTING");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SessionLootBallot>> captor = ArgumentCaptor.forClass(List.class);
        verify(ballotRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(SessionLootBallot::getDisplayName)
                .containsExactly("Thessaly", "Brakka", "Quill");
        assertThat(captor.getValue()).allMatch(b -> b.getVote() == null);
    }

    // --- split: the vote ---

    @Test
    void castVote_anXWithPvp_cancelsTheLootBackToADraft_atOnce() {
        pool.setSplitPhase("VOTING");
        stubVote(ballots(null, null, null), List.of());

        LootView view = lootService.castVote(1L, false, true, playerId); // Thessaly draws steel

        assertThat(view).isNull(); // the pool is hidden from players again
        assertThat(pool.isDropped()).isFalse();
        assertThat(pool.getSplitPhase()).isNull();
        verify(allocationRepository).deleteBySessionLootId(20L);
        verify(pcRepository, never()).save(any());
        SessionLootResult result = savedResult();
        assertThat(result.getOutcome()).isEqualTo("PVP");
        assertThat(result.getPvpCallers()).isEqualTo("Thessaly");
    }

    @Test
    void castVote_waits_whileAnyBallotIsUnsettled() {
        pool.setSplitPhase("VOTING");
        stubVote(ballots(null, true, true), List.of());

        LootView view = lootService.castVote(1L, false, null, playerId); // ✗, still deciding on PvP

        assertThat(view).isNotNull();
        assertThat(view.phase()).isEqualTo("VOTING");
        verify(resultRepository, never()).save(any());
    }

    @Test
    void castVote_majorityAgree_writesEveryShareIntoTheSheets_andDeletesThePool() {
        pool.setSplitPhase("VOTING");
        pool.setCoinCpTotal(6000L);
        SessionLootItem sword = poolLine(30L, "longsword", null, 1);
        SessionLootItem potions = poolLine(31L, null, "Potion of Healing", 2);
        potions.setCategory("consumable");
        stubVote(ballots(null, false, true), List.of(
                itemShare(101L, 30L, 1), itemShare(102L, 31L, 2),
                coinShare(101L, 3000L), coinShare(102L, 3000L)), sword, potions);
        when(srdItemRepository.findByItemKeyIn(anyList())).thenReturn(List.of(longsword()));
        // Brakka objected but declined PvP; Quill agreed — Thessaly's ✓ makes 2 of 3.
        ballotsByName.get("Brakka").setPvp(false);
        PC thessaly = pc(7L, "{\"gp\":1}");
        PC brakka = pc(8L, null);
        when(pcRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(thessaly));
        when(pcRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(brakka));

        LootView view = lootService.castVote(1L, true, null, playerId);

        assertThat(view).isNull(); // distributed — the pool is gone
        verify(lootRepository).delete(pool);
        assertThat(thessaly.getInventory()).contains("longsword");
        assertThat(thessaly.getCoins()).contains("\"gp\":31").contains("\"pp\":0"); // 1 gp + 30 gp, all gold
        assertThat(brakka.getInventory()).contains("Potion of Healing").contains("\"qty\":2");
        verify(activityLogService).log(7L, PcActivityType.LOOT,
                "Received from the party split: Longsword, 30 gp", playerId);
        verify(activityLogService).log(8L, PcActivityType.LOOT,
                "Received from the party split: Potion of Healing ×2, 30 gp", playerId);
        SessionLootResult result = savedResult();
        assertThat(result.getOutcome()).isEqualTo("APPROVED");
        assertThat(result.getAgreeCount()).isEqualTo(2);
        assertThat(result.getObjectCount()).isEqualTo(1);
    }

    @Test
    void castVote_noMajority_reopensTheSplit_keepingEveryShare() {
        pool.setSplitPhase("VOTING");
        stubVote(ballots(null, false, false), List.of());
        ballotsByName.get("Brakka").setPvp(false);
        ballotsByName.get("Quill").setPvp(false);

        LootView view = lootService.castVote(1L, true, null, playerId); // 1 ✓ of 3

        assertThat(view).isNotNull();
        assertThat(pool.getSplitPhase()).isEqualTo("SPLITTING");
        assertThat(pool.isDropped()).isTrue();
        verify(ballotRepository).deleteBySessionLootId(20L);
        verify(allocationRepository, never()).deleteBySessionLootId(any());
        assertThat(savedResult().getOutcome()).isEqualTo("NO_MAJORITY");
    }

    @Test
    void castVote_throws403_forACallerWithoutABallot() {
        pool.setSplitPhase("VOTING");
        stubVote(ballots(null, null, null), List.of());

        assertThatThrownBy(() -> lootService.castVote(1L, true, null, strangerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
    }

    @Test
    void castVote_throws409_whenNoVoteIsOpen() {
        stubVote(ballots(null, null, null), List.of());

        assertThatThrownBy(() -> lootService.castVote(1L, true, null, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
    }

    // --- DM override ---

    @Test
    void overrideSplit_cancel_takesTheLootBackAsADraft() {
        pool.setSplitPhase("VOTING");
        stubVote(ballots(true, null, null), List.of());

        lootService.overrideSplit(1L, "CANCEL", dmId);

        assertThat(pool.isDropped()).isFalse();
        assertThat(savedResult().getOutcome()).isEqualTo("DM_CANCELLED");
    }

    @Test
    void overrideSplit_approve_throws409_whileThePileIsNotEmpty() {
        stubVote(ballots(null, null, null), List.of(), poolLine(30L, "longsword", null, 1));

        assertThatThrownBy(() -> lootService.overrideSplit(1L, "APPROVE", dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
    }

    @Test
    void overrideSplit_throws403_forAPlayer() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> lootService.overrideSplit(1L, "APPROVE", playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
    }

    // --- helpers ---

    private final Map<String, SessionLootBallot> ballotsByName = new HashMap<>();

    /** Three seated characters: Thessaly (the caller, pc 7), Brakka (pc 8), Quill (pc 9). */
    private List<SessionParticipant> table() {
        return List.of(seat(101L, 7L, playerId, "Thessaly"),
                seat(102L, 8L, UUID.randomUUID(), "Brakka"),
                seat(103L, 9L, UUID.randomUUID(), "Quill"));
    }

    /** A dropped pool mid-split, with these shares and lines. Lenient: not every test reads all of it. */
    private void stubSplit(List<SessionLootAllocation> shares, SessionLootItem... lines) {
        lenient().when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        lenient().when(lootRepository.findBySessionId(1L)).thenReturn(Optional.of(pool));
        lenient().when(lootRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(pool));
        List<SessionParticipant> seats = table();
        lenient().when(participantRepository.findBySessionIdOrderByOrderIndexAsc(1L)).thenReturn(seats);
        lenient().when(allocationRepository.findBySessionLootIdOrderByIdAsc(20L)).thenReturn(shares);
        lenient().when(lootItemRepository.findBySessionLootIdOrderByIdAsc(20L)).thenReturn(List.of(lines));
        for (SessionLootItem line : lines) {
            lenient().when(lootItemRepository.findById(line.getId())).thenReturn(Optional.of(line));
        }
    }

    private void stubVote(List<SessionLootBallot> ballots, List<SessionLootAllocation> shares,
                          SessionLootItem... lines) {
        stubSplit(shares, lines);
        lenient().when(ballotRepository.findBySessionLootIdOrderByIdAsc(20L)).thenReturn(ballots);
        lenient().when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    /** Ballots for Thessaly, Brakka and Quill, in that order. */
    private List<SessionLootBallot> ballots(Boolean thessaly, Boolean brakka, Boolean quill) {
        List<SessionLootBallot> list = new ArrayList<>();
        String[] names = {"Thessaly", "Brakka", "Quill"};
        Boolean[] votes = {thessaly, brakka, quill};
        for (int i = 0; i < 3; i++) {
            SessionLootBallot b = new SessionLootBallot();
            b.setSessionLootId(20L);
            b.setParticipantId(101L + i);
            b.setDisplayName(names[i]);
            b.setVote(votes[i]);
            list.add(b);
            ballotsByName.put(names[i], b);
        }
        return list;
    }

    private SessionLootResult savedResult() {
        ArgumentCaptor<SessionLootResult> captor = ArgumentCaptor.forClass(SessionLootResult.class);
        verify(resultRepository).save(captor.capture());
        return captor.getValue();
    }

    private static SessionLootAllocation itemShare(Long participantId, Long lootItemId, int qty) {
        SessionLootAllocation a = new SessionLootAllocation();
        a.setSessionLootId(20L);
        a.setParticipantId(participantId);
        a.setLootItemId(lootItemId);
        a.setQty(qty);
        return a;
    }

    private static SessionLootAllocation coinShare(Long participantId, long coinCp) {
        SessionLootAllocation a = new SessionLootAllocation();
        a.setSessionLootId(20L);
        a.setParticipantId(participantId);
        a.setCoinCp(coinCp);
        return a;
    }

    private static SessionParticipant seat(Long id, Long pcId, UUID owner, String name) {
        SessionParticipant p = new SessionParticipant();
        p.setId(id);
        p.setPcId(pcId);
        p.setOwnerUserId(owner);
        p.setDisplayName(name);
        return p;
    }

    private static PC pc(Long id, String coinsJson) {
        PC pc = new PC();
        pc.setId(id);
        pc.setCoins(coinsJson);
        return pc;
    }


    private static int status(Throwable e) {
        return ((ResponseStatusException) e).getStatusCode().value();
    }

    private static CuratedLoot curatedList(UUID owner, Long campaignId) {
        CuratedLoot l = new CuratedLoot();
        l.setId(5L);
        l.setCampaignId(campaignId);
        l.setDmUserId(owner);
        l.setName("Goblin Ambush loot");
        l.setCoinCp(500L);
        return l;
    }

    private static CuratedLootItem curatedLine(String key, String customName, String notes, int qty) {
        CuratedLootItem line = new CuratedLootItem();
        line.setLootId(5L);
        line.setCatalogItemKey(key);
        line.setCustomName(customName);
        line.setCustomNotes(notes);
        line.setQty(qty);
        return line;
    }

    private static AddLootItemRequest addRequest(String key, String customName) {
        return new AddLootItemRequest(key, customName, null, 1, null, null, null, null, null);
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
}
