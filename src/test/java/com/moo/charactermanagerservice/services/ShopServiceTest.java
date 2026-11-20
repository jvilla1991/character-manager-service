package com.moo.charactermanagerservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moo.charactermanagerservice.dto.PurchaseResult;
import com.moo.charactermanagerservice.dto.SellResult;
import com.moo.charactermanagerservice.dto.ShopItemView;
import com.moo.charactermanagerservice.dto.ShopView;
import com.moo.charactermanagerservice.models.CombatSession;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.models.PcActivityType;
import com.moo.charactermanagerservice.models.SessionParticipant;
import com.moo.charactermanagerservice.models.SessionShop;
import com.moo.charactermanagerservice.models.SessionShopAttendee;
import com.moo.charactermanagerservice.models.SessionStatus;
import com.moo.charactermanagerservice.models.SrdItem;
import com.moo.charactermanagerservice.repositories.CombatSessionRepository;
import com.moo.charactermanagerservice.repositories.PCRepository;
import com.moo.charactermanagerservice.repositories.SessionParticipantRepository;
import com.moo.charactermanagerservice.repositories.SessionShopAttendeeRepository;
import com.moo.charactermanagerservice.models.Shop;
import com.moo.charactermanagerservice.models.ShopItem;
import com.moo.charactermanagerservice.repositories.SessionShopRepository;
import com.moo.charactermanagerservice.repositories.ShopItemRepository;
import com.moo.charactermanagerservice.repositories.ShopRepository;
import com.moo.charactermanagerservice.repositories.SrdItemRepository;
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

@ExtendWith(MockitoExtension.class)
class ShopServiceTest {

    @Mock private CombatSessionRepository sessionRepository;
    @Mock private SessionShopRepository shopRepository;
    @Mock private SessionShopAttendeeRepository attendeeRepository;
    @Mock private SessionParticipantRepository participantRepository;
    @Mock private SrdItemRepository srdItemRepository;
    @Mock private ShopRepository curatedShopRepository;
    @Mock private ShopItemRepository curatedItemRepository;
    @Mock private PCRepository pcRepository;
    @Mock private PcActivityLogService activityLogService;

    private ShopService shopService;

    private UUID dmId;
    private UUID playerId;
    private UUID strangerId;
    private CombatSession session;
    private SessionShop shop;

    @BeforeEach
    void setUp() {
        shopService = new ShopService(sessionRepository, shopRepository, attendeeRepository,
                participantRepository, srdItemRepository, curatedShopRepository, curatedItemRepository,
                pcRepository, activityLogService, new ObjectMapper());

        dmId = UUID.randomUUID();
        playerId = UUID.randomUUID();
        strangerId = UUID.randomUUID();

        session = new CombatSession();
        session.setId(1L);
        session.setCampaignId(1L);
        session.setDmUserId(dmId);
        session.setStatus(SessionStatus.ACTIVE);

        shop = new SessionShop();
        shop.setId(10L);
        shop.setSessionId(1L);
        shop.setCategory("WEAPON");
        shop.setSettlement("Phandalin");
    }

    // --- openShop ---

    @Test
    void openShop_createsShop_andSeatsRosterCharacters() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(shopRepository.findBySessionId(1L)).thenReturn(Optional.empty());
        when(shopRepository.save(any(SessionShop.class))).thenAnswer(inv -> {
            SessionShop s = inv.getArgument(0);
            s.setId(10L);
            return s;
        });
        when(participantRepository.findBySessionIdAndPcId(1L, 7L))
                .thenReturn(Optional.of(participant(7L, playerId)));
        when(attendeeRepository.findBySessionShopId(10L))
                .thenReturn(List.of(attendee(7L, playerId)));
        when(srdItemRepository.findByCategoryOrderByNameAsc("WEAPON"))
                .thenReturn(List.of(longsword()));

        ShopView view = shopService.openShop(1L, "weapon", "Phandalin", List.of(7L), dmId);

        assertThat(view.category()).isEqualTo("WEAPON");
        assertThat(view.attendeePcIds()).containsExactly(7L);
        assertThat(view.items()).hasSize(1);
        assertThat(view.items().get(0).itemKey()).isEqualTo("longsword");
        verify(attendeeRepository).saveAll(any());
        verify(sessionRepository).save(session); // version bumped
    }

    @Test
    void openShop_throws403_whenNotDm() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        assertThatThrownBy(() -> shopService.openShop(1L, "weapon", "x", List.of(), strangerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
    }

    @Test
    void openShop_throws400_forUnsupportedCategory() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        assertThatThrownBy(() -> shopService.openShop(1L, "potions", "x", List.of(), dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
    }

    @Test
    void openShop_throws400_whenTargetingCharacterNotInSession() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(shopRepository.findBySessionId(1L)).thenReturn(Optional.empty());
        when(shopRepository.save(any(SessionShop.class))).thenAnswer(inv -> inv.getArgument(0));
        when(participantRepository.findBySessionIdAndPcId(1L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shopService.openShop(1L, "weapon", "x", List.of(99L), dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
    }

    // --- getActiveShopForUser (visibility) ---

    @Test
    void getActiveShop_visibleToDm() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(shopRepository.findBySessionId(1L)).thenReturn(Optional.of(shop));
        when(attendeeRepository.findBySessionShopId(10L)).thenReturn(List.of(attendee(7L, playerId)));
        when(srdItemRepository.findByCategoryOrderByNameAsc("WEAPON")).thenReturn(List.of(longsword()));

        assertThat(shopService.getActiveShopForUser(1L, dmId)).isNotNull();
    }

    @Test
    void getActiveShop_visibleToAttendee() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(shopRepository.findBySessionId(1L)).thenReturn(Optional.of(shop));
        when(attendeeRepository.findBySessionShopId(10L)).thenReturn(List.of(attendee(7L, playerId)));
        when(srdItemRepository.findByCategoryOrderByNameAsc("WEAPON")).thenReturn(List.of(longsword()));

        assertThat(shopService.getActiveShopForUser(1L, playerId)).isNotNull();
    }

    @Test
    void getActiveShop_nullForNonAttendee() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(shopRepository.findBySessionId(1L)).thenReturn(Optional.of(shop));
        when(attendeeRepository.findBySessionShopId(10L)).thenReturn(List.of(attendee(7L, playerId)));

        assertThat(shopService.getActiveShopForUser(1L, strangerId)).isNull();
    }

    @Test
    void getActiveShop_nullWhenNoShop() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(shopRepository.findBySessionId(1L)).thenReturn(Optional.empty());
        assertThat(shopService.getActiveShopForUser(1L, dmId)).isNull();
    }

    // --- purchase ---

    @Test
    void purchase_deductsCoins_appendsInventory_andReportsCost() {
        stubActiveShopWithAttendee();
        when(srdItemRepository.findByItemKey("longsword")).thenReturn(Optional.of(longsword()));
        PC pc = pcOwnedBy(playerId, "{\"gp\":20}", null);
        when(pcRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        PurchaseResult result = shopService.purchase(1L, 7L, "longsword", 1, playerId);

        assertThat(result.totalCostCp()).isEqualTo(1500L);
        // 20 gp - 15 gp = 5 gp
        assertThat(result.coins()).containsOnlyKeys("cp", "sp", "gp").containsEntry("gp", 5);
        assertThat(result.inventory()).hasSize(1);
        assertThat(result.inventory().get(0))
                .containsEntry("catalogKey", "longsword")
                .containsEntry("qty", 1)
                .containsEntry("category", "weapon")
                .containsEntry("damage", "1d8 slashing");
        verify(pcRepository).findByIdForUpdate(7L); // locked read
        verify(pcRepository).save(pc);
        verify(activityLogService).log(7L, PcActivityType.PURCHASE, "Bought Longsword for 15 gp", playerId);
    }

    @Test
    void purchase_logsQtySuffix_whenBuyingMoreThanOne() {
        stubActiveShopWithAttendee();
        when(srdItemRepository.findByItemKey("longsword")).thenReturn(Optional.of(longsword()));
        PC pc = pcOwnedBy(playerId, "{\"gp\":100}", null);
        when(pcRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        shopService.purchase(1L, 7L, "longsword", 2, playerId);

        verify(activityLogService).log(7L, PcActivityType.PURCHASE, "Bought Longsword ×2 for 30 gp", playerId);
    }

    @Test
    void purchase_stampsCatalogBulk_onNewInventoryLine() {
        stubActiveShopWithAttendee();
        SrdItem sword = longsword();
        sword.setBulk(new java.math.BigDecimal("3.0"));
        when(srdItemRepository.findByItemKey("longsword")).thenReturn(Optional.of(sword));
        PC pc = pcOwnedBy(playerId, "{\"gp\":20}", null);
        when(pcRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        PurchaseResult result = shopService.purchase(1L, 7L, "longsword", 1, playerId);

        assertThat(result.inventory().get(0)).containsEntry("bulk", new java.math.BigDecimal("3.0"));
    }

    @Test
    void purchase_derivesBulkFromWeight_whenCatalogHasNone() {
        stubActiveShopWithAttendee();
        SrdItem sword = longsword();
        sword.setWeight(new java.math.BigDecimal("3")); // ≤5 lb band → 2 bulk
        when(srdItemRepository.findByItemKey("longsword")).thenReturn(Optional.of(sword));
        PC pc = pcOwnedBy(playerId, "{\"gp\":20}", null);
        when(pcRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        PurchaseResult result = shopService.purchase(1L, 7L, "longsword", 1, playerId);

        assertThat(result.inventory().get(0)).containsEntry("bulk", new java.math.BigDecimal("2"));
    }

    @Test
    void purchase_stacksQuantity_onRepeatBuy() {
        stubActiveShopWithAttendee();
        when(srdItemRepository.findByItemKey("longsword")).thenReturn(Optional.of(longsword()));
        PC pc = pcOwnedBy(playerId, "{\"gp\":100}",
                "[{\"catalogKey\":\"longsword\",\"name\":\"Longsword\",\"qty\":1}]");
        when(pcRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        PurchaseResult result = shopService.purchase(1L, 7L, "longsword", 2, playerId);

        assertThat(result.inventory()).hasSize(1);
        assertThat(result.inventory().get(0)).containsEntry("qty", 3);
    }

    @Test
    void purchase_throws409_whenInsufficientFunds() {
        stubActiveShopWithAttendee();
        when(srdItemRepository.findByItemKey("longsword")).thenReturn(Optional.of(longsword()));
        PC pc = pcOwnedBy(playerId, "{\"gp\":10}", null);
        when(pcRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(pc));

        assertThatThrownBy(() -> shopService.purchase(1L, 7L, "longsword", 1, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
        verify(pcRepository, never()).save(any());
    }

    @Test
    void purchase_throws403_whenCharacterNotAtShop() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(shopRepository.findBySessionId(1L)).thenReturn(Optional.of(shop));
        when(attendeeRepository.findBySessionShopIdAndPcId(10L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shopService.purchase(1L, 7L, "longsword", 1, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
    }

    @Test
    void purchase_throws400_whenItemNotSoldAtThisShop() {
        stubActiveShopWithAttendee();
        SrdItem armor = new SrdItem();
        armor.setItemKey("shield");
        armor.setCategory("ARMOR");
        armor.setCostCp(1000L);
        when(srdItemRepository.findByItemKey("shield")).thenReturn(Optional.of(armor));

        assertThatThrownBy(() -> shopService.purchase(1L, 7L, "shield", 1, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
    }

    @Test
    void purchase_throws404_whenItemUnknown() {
        stubActiveShopWithAttendee();
        when(srdItemRepository.findByItemKey("frostbrand")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shopService.purchase(1L, 7L, "frostbrand", 1, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(404));
    }

    @Test
    void purchase_throws403_whenCallerDoesNotOwnCharacter() {
        stubActiveShopWithAttendee();
        when(srdItemRepository.findByItemKey("longsword")).thenReturn(Optional.of(longsword()));
        PC pc = pcOwnedBy(strangerId, "{\"gp\":50}", null); // owned by someone else
        when(pcRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(pc));

        assertThatThrownBy(() -> shopService.purchase(1L, 7L, "longsword", 1, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
    }

    @Test
    void purchase_throws400_forNonPositiveQuantity() {
        assertThatThrownBy(() -> shopService.purchase(1L, 7L, "longsword", 0, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
    }

    // --- purchase: ration container cap ---

    @Test
    void purchase_rations_allowedBeyondRationBoxCapacity_overflowRidesLoose() {
        shop.setCategory("GEAR");
        stubActiveShopWithAttendee();
        when(srdItemRepository.findByItemKey("rations")).thenReturn(Optional.of(rations()));
        PC pc = pcOwnedBy(playerId, "{\"gp\":10}",
                "[{\"catalogKey\":\"ration-box\",\"qty\":1},{\"catalogKey\":\"rations\",\"qty\":4}]");
        when(pcRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        // 1 box = capacity 5; carrying 4, buying 2 overflows by 1 — the sale
        // still goes through, and the loose ration costs inventory slots on the
        // frontend (suppliesSlots) instead of being refused here.
        PurchaseResult result = shopService.purchase(1L, 7L, "rations", 2, playerId);

        assertThat(result.totalCostCp()).isEqualTo(20L); // 2 × 1 sp
        assertThat(result.inventory())
                .anySatisfy(l -> assertThat(l).containsEntry("catalogKey", "rations").containsEntry("qty", 6));
    }

    @Test
    void purchase_rations_fillsUpToCapacity_countingTheLegacyImpliedBox() {
        shop.setCategory("GEAR");
        stubActiveShopWithAttendee();
        when(srdItemRepository.findByItemKey("rations")).thenReturn(Optional.of(rations()));
        // Legacy pc: rations but no ration-box line — normalization implies one box.
        PC pc = pcOwnedBy(playerId, "{\"gp\":10}", "[{\"catalogKey\":\"rations\",\"qty\":3}]");
        when(pcRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        PurchaseResult result = shopService.purchase(1L, 7L, "rations", 2, playerId);

        assertThat(result.totalCostCp()).isEqualTo(20L); // 2 × 1 sp
        assertThat(result.inventory())
                .anySatisfy(l -> assertThat(l).containsEntry("catalogKey", "rations").containsEntry("qty", 5))
                .anySatisfy(l -> assertThat(l).containsEntry("catalogKey", "ration-box").containsEntry("qty", 1));
    }

    @Test
    void purchase_rationBox_alwaysAllowed_evenWhenRationsAreFull() {
        shop.setCategory("GEAR");
        stubActiveShopWithAttendee();
        when(srdItemRepository.findByItemKey("ration-box")).thenReturn(Optional.of(rationBox()));
        PC pc = pcOwnedBy(playerId, "{\"gp\":10}",
                "[{\"catalogKey\":\"ration-box\",\"qty\":1},{\"catalogKey\":\"rations\",\"qty\":5}]");
        when(pcRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        PurchaseResult result = shopService.purchase(1L, 7L, "ration-box", 1, playerId);

        // The container purchase raises capacity 5 → 10.
        assertThat(result.inventory())
                .anySatisfy(l -> assertThat(l).containsEntry("catalogKey", "ration-box").containsEntry("qty", 2));
    }

    // --- sell ---

    @Test
    void sell_creditsHalfCatalogPrice_removesLine_andReportsGain() {
        stubActiveShopWithAttendee();
        when(srdItemRepository.findByItemKey("longsword")).thenReturn(Optional.of(longsword()));
        PC pc = pcOwnedBy(playerId, "{\"gp\":5}",
                "[{\"catalogKey\":\"longsword\",\"name\":\"Longsword\",\"category\":\"weapon\",\"qty\":1}]");
        when(pcRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        SellResult result = shopService.sell(1L, 7L, 0, null, playerId);

        assertThat(result.totalGainCp()).isEqualTo(750L); // half of 1500 catalog price
        // 500cp (5gp) + 750cp = 1250cp -> re-minted as 1pp 2gp 5sp
        assertThat(result.coins()).containsOnlyKeys("cp", "sp", "gp").containsEntry("gp", 12).containsEntry("sp", 5);
        assertThat(result.inventory()).isEmpty();
        verify(pcRepository).findByIdForUpdate(7L);
        verify(pcRepository).save(pc);
        verify(activityLogService).log(7L, PcActivityType.SALE, "Sold Longsword for 7 gp 5 sp", playerId);
    }

    @Test
    void sell_fallsBackToUnitCostCp_whenNoCatalogKey() {
        stubActiveShopWithAttendee();
        PC pc = pcOwnedBy(playerId, "{\"gp\":0}",
                "[{\"name\":\"Heirloom Ring\",\"category\":\"weapon\",\"qty\":1,\"unitCostCp\":1000}]");
        when(pcRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        SellResult result = shopService.sell(1L, 7L, 0, null, playerId);

        assertThat(result.totalGainCp()).isEqualTo(500L); // half of the 1000cp paid
        verify(srdItemRepository, never()).findByItemKey(any());
    }

    @Test
    void sell_multipliesByStackQuantity() {
        stubActiveShopWithAttendee();
        when(srdItemRepository.findByItemKey("longsword")).thenReturn(Optional.of(longsword()));
        PC pc = pcOwnedBy(playerId, "{\"gp\":0}",
                "[{\"catalogKey\":\"longsword\",\"category\":\"weapon\",\"qty\":3}]");
        when(pcRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        SellResult result = shopService.sell(1L, 7L, 0, null, playerId);

        assertThat(result.totalGainCp()).isEqualTo(2250L); // half of 1500 * 3
    }

    @Test
    void sell_partialQuantity_shrinksTheStack_andPaysForOnlyThoseUnits() {
        stubActiveShopWithAttendee();
        when(srdItemRepository.findByItemKey("longsword")).thenReturn(Optional.of(longsword()));
        PC pc = pcOwnedBy(playerId, "{\"gp\":0}",
                "[{\"catalogKey\":\"longsword\",\"name\":\"Longsword\",\"category\":\"weapon\",\"qty\":3}]");
        when(pcRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        SellResult result = shopService.sell(1L, 7L, 0, 2, playerId);

        assertThat(result.totalGainCp()).isEqualTo(1500L); // half of 1500 * 2
        assertThat(result.inventory()).singleElement()
                .satisfies(l -> assertThat(l).containsEntry("qty", 1));
        verify(activityLogService).log(7L, PcActivityType.SALE, "Sold Longsword ×2 for 15 gp", playerId);
    }

    @Test
    void sell_throws400_whenQuantityIsOutsideTheStack() {
        stubActiveShopWithAttendee();
        when(srdItemRepository.findByItemKey("longsword")).thenReturn(Optional.of(longsword()));
        PC pc = pcOwnedBy(playerId, "{\"gp\":0}",
                "[{\"catalogKey\":\"longsword\",\"category\":\"weapon\",\"qty\":3}]");
        when(pcRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(pc));

        for (int bad : new int[] {0, -1, 4}) {
            assertThatThrownBy(() -> shopService.sell(1L, 7L, 0, bad, playerId))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("Quantity must be between");
        }
        verify(pcRepository, never()).save(any());
    }

    @Test
    void sell_throws400_whenItemIsDropped() {
        stubActiveShopWithAttendee();
        PC pc = pcOwnedBy(playerId, "{\"gp\":0}",
                "[{\"catalogKey\":\"longsword\",\"category\":\"weapon\",\"qty\":1,\"status\":\"dropped\"}]");
        when(pcRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(pc));

        assertThatThrownBy(() -> shopService.sell(1L, 7L, 0, null, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        verify(pcRepository, never()).save(any());
    }

    @Test
    void sell_buysAnyCategory_atAStandardShop() {
        stubActiveShopWithAttendee(); // shop category is WEAPON; the item is armor
        PC pc = pcOwnedBy(playerId, "{\"gp\":0}",
                "[{\"catalogKey\":\"shield\",\"category\":\"armor\",\"qty\":1,\"unitCostCp\":1000}]");
        when(pcRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        SellResult result = shopService.sell(1L, 7L, 0, null, playerId);

        assertThat(result.totalGainCp()).isEqualTo(500L);
        assertThat(result.inventory()).isEmpty();
    }

    @Test
    void sell_pricesStartingEquipment_byItsCatalogName() {
        stubActiveShopWithAttendee();
        // A starting-kit line: a name and nothing else — no key, no price paid.
        PC pc = pcOwnedBy(playerId, "{\"gp\":0}",
                "[{\"name\":\"longsword\",\"category\":\"weapon\",\"qty\":1}]");
        when(pcRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(pc));
        when(srdItemRepository.findFirstByNameIgnoreCase("longsword")).thenReturn(Optional.of(longsword()));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        SellResult result = shopService.sell(1L, 7L, 0, null, playerId);

        assertThat(result.totalGainCp()).isEqualTo(750L); // half the 1500cp catalog price
        assertThat(result.inventory()).isEmpty();
    }

    @Test
    void sell_allowsAnyCategory_atCuratedShop() {
        SessionShop curatedSession = new SessionShop();
        curatedSession.setId(10L);
        curatedSession.setSessionId(1L);
        curatedSession.setShopId(50L); // curated — no category
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(shopRepository.findBySessionId(1L)).thenReturn(Optional.of(curatedSession));
        when(attendeeRepository.findBySessionShopIdAndPcId(10L, 7L))
                .thenReturn(Optional.of(attendee(7L, playerId)));
        PC pc = pcOwnedBy(playerId, "{\"gp\":0}",
                "[{\"catalogKey\":\"shield\",\"category\":\"armor\",\"qty\":1,\"unitCostCp\":1000}]");
        when(pcRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        SellResult result = shopService.sell(1L, 7L, 0, null, playerId);

        assertThat(result.totalGainCp()).isEqualTo(500L);
    }

    @Test
    void sell_takesAnUnpricedItemForNothing() {
        stubActiveShopWithAttendee();
        PC pc = pcOwnedBy(playerId, "{\"gp\":3}",
                "[{\"name\":\"Mystery Note\",\"category\":\"gear\",\"qty\":1}]");
        when(pcRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(pc));
        when(srdItemRepository.findFirstByNameIgnoreCase("Mystery Note")).thenReturn(Optional.empty());
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        SellResult result = shopService.sell(1L, 7L, 0, null, playerId);

        assertThat(result.totalGainCp()).isZero();
        assertThat(result.coins()).containsEntry("gp", 3);
        assertThat(result.inventory()).isEmpty();
        verify(activityLogService).log(7L, PcActivityType.SALE, "Sold Mystery Note for nothing", playerId);
    }

    @Test
    void sell_throws400_whenIndexOutOfBounds() {
        stubActiveShopWithAttendee();
        PC pc = pcOwnedBy(playerId, "{\"gp\":0}", "[]");
        when(pcRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(pc));

        assertThatThrownBy(() -> shopService.sell(1L, 7L, 0, null, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
    }

    @Test
    void sell_throws403_whenCharacterNotAtShop() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(shopRepository.findBySessionId(1L)).thenReturn(Optional.of(shop));
        when(attendeeRepository.findBySessionShopIdAndPcId(10L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shopService.sell(1L, 7L, 0, null, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
    }

    @Test
    void sell_throws403_whenCallerDoesNotOwnCharacter() {
        stubActiveShopWithAttendee();
        PC pc = pcOwnedBy(strangerId, "{\"gp\":0}",
                "[{\"catalogKey\":\"longsword\",\"category\":\"weapon\",\"qty\":1}]");
        when(pcRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(pc));

        assertThatThrownBy(() -> shopService.sell(1L, 7L, 0, null, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
    }

    // --- curated shop activation + browse ---

    @Test
    void openCuratedShop_activatesCuratedShop_andResolvesItems() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(curatedShopRepository.findById(50L)).thenReturn(Optional.of(curatedShop()));
        when(shopRepository.findBySessionId(1L)).thenReturn(Optional.empty());
        when(shopRepository.save(any(SessionShop.class))).thenAnswer(inv -> {
            SessionShop s = inv.getArgument(0);
            s.setId(10L);
            return s;
        });
        when(attendeeRepository.findBySessionShopId(10L)).thenReturn(List.of());
        // openCuratedShop validates the shop; buildShopView re-reads it for name + items.
        when(curatedItemRepository.findByShopId(50L)).thenReturn(List.of(curatedLine(1L, "longsword", 1200L)));
        when(srdItemRepository.findByItemKeyIn(any())).thenReturn(List.of(longsword()));

        ShopView view = shopService.openCuratedShop(1L, 50L, null, List.of(), dmId);

        assertThat(view.curatedShopId()).isEqualTo(50L);
        assertThat(view.shopName()).isEqualTo("The Smithy");
        assertThat(view.category()).isNull();
        assertThat(view.items()).hasSize(1);
        assertThat(view.items().get(0).costCp()).isEqualTo(1200L); // price override applied
    }

    @Test
    void openCuratedShop_throws403_whenNotShopOwner() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        Shop other = curatedShop();
        other.setDmUserId(strangerId);
        when(curatedShopRepository.findById(50L)).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> shopService.openCuratedShop(1L, 50L, null, List.of(), dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
    }

    @Test
    void openCuratedShop_throws400_whenDifferentCampaign() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        Shop other = curatedShop();
        other.setCampaignId(999L);
        when(curatedShopRepository.findById(50L)).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> shopService.openCuratedShop(1L, 50L, null, List.of(), dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
    }

    @Test
    void purchase_fromCuratedShop_usesOverridePrice() {
        SessionShop curatedSession = new SessionShop();
        curatedSession.setId(10L);
        curatedSession.setSessionId(1L);
        curatedSession.setShopId(50L); // curated
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(shopRepository.findBySessionId(1L)).thenReturn(Optional.of(curatedSession));
        when(attendeeRepository.findBySessionShopIdAndPcId(10L, 7L))
                .thenReturn(Optional.of(attendee(7L, playerId)));
        when(srdItemRepository.findByItemKey("longsword")).thenReturn(Optional.of(longsword()));
        when(curatedItemRepository.findByShopIdAndCatalogItemKey(50L, "longsword"))
                .thenReturn(Optional.of(curatedLine(1L, "longsword", 1200L)));
        PC pc = pcOwnedBy(playerId, "{\"gp\":20}", null);
        when(pcRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        PurchaseResult result = shopService.purchase(1L, 7L, "longsword", 1, playerId);

        assertThat(result.totalCostCp()).isEqualTo(1200L); // override, not 1500 catalog
        assertThat(result.coins()).containsEntry("gp", 8);  // 20 gp - 12 gp
        assertThat(result.inventory().get(0)).containsEntry("unitCostCp", 1200L);
    }

    @Test
    void purchase_fromCuratedShop_throws400_whenItemNotaLine() {
        SessionShop curatedSession = new SessionShop();
        curatedSession.setId(10L);
        curatedSession.setSessionId(1L);
        curatedSession.setShopId(50L);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(shopRepository.findBySessionId(1L)).thenReturn(Optional.of(curatedSession));
        when(attendeeRepository.findBySessionShopIdAndPcId(10L, 7L))
                .thenReturn(Optional.of(attendee(7L, playerId)));
        when(srdItemRepository.findByItemKey("dagger")).thenReturn(Optional.of(longsword()));
        when(curatedItemRepository.findByShopIdAndCatalogItemKey(50L, "dagger")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shopService.purchase(1L, 7L, "dagger", 1, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
    }

    // --- custom (non-catalog) lines in a curated shop ---

    private static ShopItem customLine(Long id, String name, Long priceCp) {
        ShopItem i = new ShopItem();
        i.setId(id);
        i.setShopId(50L);
        i.setCustomName(name);
        i.setPriceCp(priceCp);
        return i;
    }

    private static ShopItem flametongue() {
        ShopItem i = customLine(2L, "Flametongue", 500000L);
        i.setCategory("weapon");
        i.setDamage("1d8 slashing + 2d6 fire");
        i.setWeight(new java.math.BigDecimal("3"));
        i.setBulk(new java.math.BigDecimal("4"));
        i.setCustomNotes("Requires attunement. Ignites on command.");
        return i;
    }

    private void stubCuratedShopWithAttendee() {
        SessionShop curatedSession = new SessionShop();
        curatedSession.setId(10L);
        curatedSession.setSessionId(1L);
        curatedSession.setShopId(50L);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(shopRepository.findBySessionId(1L)).thenReturn(Optional.of(curatedSession));
        when(attendeeRepository.findBySessionShopIdAndPcId(10L, 7L))
                .thenReturn(Optional.of(attendee(7L, playerId)));
    }

    @Test
    void curatedShopView_listsCustomLines_withTheirLineIdAndStats() {
        SessionShop curatedSession = new SessionShop();
        curatedSession.setId(10L);
        curatedSession.setSessionId(1L);
        curatedSession.setShopId(50L);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(shopRepository.findBySessionId(1L)).thenReturn(Optional.of(curatedSession));
        when(attendeeRepository.findBySessionShopId(10L)).thenReturn(List.of());
        when(curatedShopRepository.findById(50L)).thenReturn(Optional.of(curatedShop()));
        when(curatedItemRepository.findByShopId(50L))
                .thenReturn(List.of(curatedLine(1L, "longsword", 1200L), flametongue()));
        when(srdItemRepository.findByItemKeyIn(List.of("longsword"))).thenReturn(List.of(longsword()));

        ShopView view = shopService.getActiveShopForUser(1L, dmId);

        assertThat(view.items()).extracting(ShopItemView::name).containsExactly("Flametongue", "Longsword");
        ShopItemView custom = view.items().get(0);
        assertThat(custom.custom()).isTrue();
        assertThat(custom.itemKey()).isNull();
        assertThat(custom.shopItemId()).isEqualTo(2L);
        assertThat(custom.category()).isEqualTo("WEAPON");
        assertThat(custom.costCp()).isEqualTo(500000L);
        assertThat(custom.details())
                .containsEntry("damage", "1d8 slashing + 2d6 fire")
                .containsEntry("notes", "Requires attunement. Ignites on command.");
        ShopItemView catalog = view.items().get(1);
        assertThat(catalog.custom()).isFalse();
        assertThat(catalog.shopItemId()).isEqualTo(1L);
        assertThat(catalog.costCp()).isEqualTo(1200L);
    }

    @Test
    void purchase_customLine_landsWithTheDmsStats_andThePricePaid() {
        stubCuratedShopWithAttendee();
        when(curatedItemRepository.findById(2L)).thenReturn(Optional.of(flametongue()));
        PC pc = pcOwnedBy(playerId, "{\"gp\":6000}", null);
        when(pcRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        PurchaseResult result = shopService.purchase(1L, 7L, null, 2L, 1, playerId);

        assertThat(result.totalCostCp()).isEqualTo(500000L);
        assertThat(result.coins()).containsEntry("gp", 1000);
        assertThat(result.inventory()).hasSize(1);
        assertThat(result.inventory().get(0))
                .doesNotContainKey("catalogKey")
                .containsEntry("name", "Flametongue")
                .containsEntry("category", "weapon")
                .containsEntry("qty", 1)
                .containsEntry("unitCostCp", 500000L)
                .containsEntry("damage", "1d8 slashing + 2d6 fire")
                .containsEntry("notes", "Requires attunement. Ignites on command.");
        // the DM's explicit bulk wins over the weight band (3 lb would be 2)
        assertThat((java.math.BigDecimal) result.inventory().get(0).get("bulk")).isEqualByComparingTo("4");
        verify(activityLogService).log(7L, PcActivityType.PURCHASE, "Bought Flametongue for 5000 gp", playerId);
        verifyNoInteractions(srdItemRepository);
    }

    @Test
    void purchase_customLine_derivesBulkFromWeight_whenTheLineHasNone() {
        stubCuratedShopWithAttendee();
        ShopItem draught = customLine(3L, "Healing Draught", 5000L);
        draught.setWeight(new java.math.BigDecimal("0.5"));
        when(curatedItemRepository.findById(3L)).thenReturn(Optional.of(draught));
        PC pc = pcOwnedBy(playerId, "{\"gp\":200}", null);
        when(pcRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        PurchaseResult result = shopService.purchase(1L, 7L, null, 3L, 2, playerId);

        assertThat(result.totalCostCp()).isEqualTo(10000L);
        assertThat(result.inventory().get(0)).containsEntry("qty", 2).containsEntry("category", "gear");
        assertThat((java.math.BigDecimal) result.inventory().get(0).get("bulk")).isEqualByComparingTo("1");
    }

    @Test
    void purchase_customLine_stacksOntoAnIdenticalLineAlreadyHeld() {
        stubCuratedShopWithAttendee();
        ShopItem draught = customLine(3L, "Healing Draught", 5000L);
        draught.setWeight(new java.math.BigDecimal("0.5"));
        when(curatedItemRepository.findById(3L)).thenReturn(Optional.of(draught));
        // One bought earlier (identical), one that differs in price, one equipped look-alike.
        PC pc = pcOwnedBy(playerId, "{\"gp\":200}",
                "[{\"name\":\"Healing Draught\",\"category\":\"gear\",\"qty\":1,\"unitCostCp\":4000,\"weight\":0.5,\"bulk\":1},"
                        + "{\"name\":\"Healing Draught\",\"category\":\"gear\",\"qty\":1,\"unitCostCp\":5000,\"weight\":0.5,\"bulk\":1,\"equipped\":true},"
                        + "{\"name\":\"Healing Draught\",\"category\":\"gear\",\"qty\":1,\"unitCostCp\":5000,\"weight\":0.50,\"bulk\":1}]");
        when(pcRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        PurchaseResult result = shopService.purchase(1L, 7L, null, 3L, 2, playerId);

        assertThat(result.inventory()).hasSize(3);
        assertThat(result.inventory().get(0)).containsEntry("qty", 1); // cheaper one: a different item
        assertThat(result.inventory().get(1)).containsEntry("qty", 1); // equipped: left alone
        assertThat(result.inventory().get(2)).containsEntry("qty", 3); // identical: stacked
    }

    @Test
    void purchase_catalogLine_canBeNamedByItsShopLine() {
        stubCuratedShopWithAttendee();
        when(curatedItemRepository.findById(1L)).thenReturn(Optional.of(curatedLine(1L, "longsword", 1200L)));
        when(srdItemRepository.findByItemKey("longsword")).thenReturn(Optional.of(longsword()));
        PC pc = pcOwnedBy(playerId, "{\"gp\":20}", null);
        when(pcRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        PurchaseResult result = shopService.purchase(1L, 7L, null, 1L, 1, playerId);

        assertThat(result.totalCostCp()).isEqualTo(1200L);
        assertThat(result.inventory().get(0)).containsEntry("catalogKey", "longsword");
    }

    @Test
    void purchase_throws400_whenTheLineBelongsToAnotherShop() {
        stubCuratedShopWithAttendee();
        ShopItem elsewhere = customLine(9L, "Flametongue", 100L);
        elsewhere.setShopId(77L);
        when(curatedItemRepository.findById(9L)).thenReturn(Optional.of(elsewhere));

        assertThatThrownBy(() -> shopService.purchase(1L, 7L, null, 9L, 1, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        verify(pcRepository, never()).save(any());
    }

    @Test
    void purchase_throws400_forAShopLineAtAStandardShop_orWithNothingNamed() {
        stubActiveShopWithAttendee();

        assertThatThrownBy(() -> shopService.purchase(1L, 7L, null, 2L, 1, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        assertThatThrownBy(() -> shopService.purchase(1L, 7L, " ", null, 1, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        verify(pcRepository, never()).save(any());
    }

    // --- catalog (DM-grant picker) ---

    @Test
    void catalog_returnsItems_withDetailsParsed_andCatalogBulk() {
        SrdItem sword = longsword();
        sword.setWeight(new java.math.BigDecimal("3"));
        sword.setBulk(new java.math.BigDecimal("2")); // explicit rating wins over weight band
        when(srdItemRepository.findByCategoryOrderByNameAsc("WEAPON")).thenReturn(List.of(sword));

        var items = shopService.catalog("WEAPON");

        assertThat(items).hasSize(1);
        var view = items.get(0);
        assertThat(view.itemKey()).isEqualTo("longsword");
        assertThat(view.costCp()).isEqualTo(1500L);
        assertThat(view.bulk()).isEqualByComparingTo("2");
        assertThat(view.details()).containsEntry("damage", "1d8 slashing");
    }

    @Test
    void catalog_derivesBulkFromWeightBand_whenNoCatalogRating() {
        SrdItem sword = longsword();
        sword.setWeight(new java.math.BigDecimal("3")); // ≤5 lb band → 2
        sword.setBulk(null);
        when(srdItemRepository.findByCategoryOrderByNameAsc("WEAPON")).thenReturn(List.of(sword));

        assertThat(shopService.catalog("WEAPON").get(0).bulk()).isEqualByComparingTo("2");
    }

    @Test
    void catalog_normalizesCategoryInput() {
        when(srdItemRepository.findByCategoryOrderByNameAsc("GEAR")).thenReturn(List.of());

        assertThat(shopService.catalog(" gear ")).isEmpty();
        verify(srdItemRepository).findByCategoryOrderByNameAsc("GEAR");
    }

    @Test
    void catalog_throws400_forUnsupportedCategory() {
        assertThatThrownBy(() -> shopService.catalog("POTION"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
    }

    // --- helpers ---

    private Shop curatedShop() {
        Shop s = new Shop();
        s.setId(50L);
        s.setCampaignId(1L);
        s.setDmUserId(dmId);
        s.setName("The Smithy");
        s.setSettlement("Phandalin");
        return s;
    }

    private static ShopItem curatedLine(Long id, String key, Long priceCp) {
        ShopItem i = new ShopItem();
        i.setId(id);
        i.setShopId(50L);
        i.setCatalogItemKey(key);
        i.setPriceCp(priceCp);
        return i;
    }

    private void stubActiveShopWithAttendee() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(shopRepository.findBySessionId(1L)).thenReturn(Optional.of(shop));
        when(attendeeRepository.findBySessionShopIdAndPcId(10L, 7L))
                .thenReturn(Optional.of(attendee(7L, playerId)));
    }

    private static int status(Throwable e) {
        return ((ResponseStatusException) e).getStatusCode().value();
    }

    private static SrdItem rations() {
        SrdItem item = new SrdItem();
        item.setId(2L);
        item.setItemKey("rations");
        item.setName("Rations (1 day)");
        item.setCategory("GEAR");
        item.setCostCp(10L); // V30 reprice: 1 sp per serving
        item.setDetails("{}");
        return item;
    }

    private static SrdItem rationBox() {
        SrdItem item = new SrdItem();
        item.setId(3L);
        item.setItemKey("ration-box");
        item.setName("Ration box");
        item.setCategory("GEAR");
        item.setCostCp(50L);
        item.setDetails("{}");
        return item;
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

    private static SessionShopAttendee attendee(Long pcId, UUID owner) {
        SessionShopAttendee a = new SessionShopAttendee();
        a.setId(pcId);
        a.setPcId(pcId);
        a.setOwnerUserId(owner);
        return a;
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
