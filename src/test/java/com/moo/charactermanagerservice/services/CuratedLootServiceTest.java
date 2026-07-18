package com.moo.charactermanagerservice.services;

import com.moo.charactermanagerservice.dto.AddLootItemRequest;
import com.moo.charactermanagerservice.dto.CuratedLootSummaryView;
import com.moo.charactermanagerservice.dto.CuratedLootView;
import com.moo.charactermanagerservice.dto.ImportLootRequest;
import com.moo.charactermanagerservice.models.Campaign;
import com.moo.charactermanagerservice.models.CuratedLoot;
import com.moo.charactermanagerservice.models.CuratedLootItem;
import com.moo.charactermanagerservice.models.SrdItem;
import com.moo.charactermanagerservice.repositories.CuratedLootItemRepository;
import com.moo.charactermanagerservice.repositories.CuratedLootRepository;
import com.moo.charactermanagerservice.repositories.SrdItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for standalone curated loot lists: CRUD + ownership, line editing,
 * the JSON import (including the custom-item attribute validation shared with
 * the item composer), and the view resolution. Pure Mockito — mirrors
 * {@link CuratedShopServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
class CuratedLootServiceTest {

    @Mock private CuratedLootRepository lootRepository;
    @Mock private CuratedLootItemRepository lootItemRepository;
    @Mock private SrdItemRepository srdItemRepository;
    @Mock private CampaignService campaignService;

    private CuratedLootService service;

    private UUID dmId;
    private UUID strangerId;
    private CuratedLoot loot;

    @BeforeEach
    void setUp() {
        service = new CuratedLootService(lootRepository, lootItemRepository,
                srdItemRepository, campaignService);
        dmId = UUID.randomUUID();
        strangerId = UUID.randomUUID();

        loot = new CuratedLoot();
        loot.setId(5L);
        loot.setCampaignId(1L);
        loot.setDmUserId(dmId);
        loot.setName("Goblin Ambush loot");
    }

    // --- createLoot / listLoot / ownership ---

    @Test
    void createLoot_assertsCampaignDm_andPersists() {
        when(campaignService.findByIdForDm(1L, dmId)).thenReturn(new Campaign());
        when(lootRepository.save(any(CuratedLoot.class))).thenAnswer(inv -> {
            CuratedLoot l = inv.getArgument(0);
            l.setId(5L);
            return l;
        });
        when(lootItemRepository.findByLootIdOrderByIdAsc(5L)).thenReturn(List.of());

        CuratedLootView view = service.createLoot(1L, "  Bandit spoils ", null, dmId);

        assertThat(view.name()).isEqualTo("Bandit spoils");
        assertThat(view.coinCp()).isZero();
        assertThat(view.items()).isEmpty();
    }

    @Test
    void createLoot_throws400_whenNameBlank() {
        when(campaignService.findByIdForDm(1L, dmId)).thenReturn(new Campaign());
        assertThatThrownBy(() -> service.createLoot(1L, "  ", null, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
    }

    @Test
    void listLoot_returnsSummariesWithItemCountAndCoins() {
        loot.setCoinCp(12550L);
        when(campaignService.findByIdForDm(1L, dmId)).thenReturn(new Campaign());
        when(lootRepository.findByCampaignIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(loot));
        when(lootItemRepository.countByLootId(5L)).thenReturn(3L);

        List<CuratedLootSummaryView> views = service.listLoot(1L, dmId);

        assertThat(views).hasSize(1);
        assertThat(views.get(0).itemCount()).isEqualTo(3L);
        assertThat(views.get(0).coinCp()).isEqualTo(12550L);
    }

    @Test
    void getLoot_throws403_whenNotOwner() {
        when(lootRepository.findById(5L)).thenReturn(Optional.of(loot));
        assertThatThrownBy(() -> service.getLoot(5L, strangerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
    }

    @Test
    void getLoot_resolvesCatalogAndCustomLines_withAttributes() {
        loot.setCoinCp(12550L);
        when(lootRepository.findById(5L)).thenReturn(Optional.of(loot));
        CuratedLootItem custom = customLine(2L, "Flametongue", "Ignites on command.", 1);
        custom.setCategory("weapon");
        custom.setUnitCostCp(500000L);
        custom.setWeight(new BigDecimal("3"));
        custom.setDamage("1d8 slashing + 2d6 fire");
        when(lootItemRepository.findByLootIdOrderByIdAsc(5L)).thenReturn(List.of(
                catalogLine(1L, "longsword", 2), custom));
        when(srdItemRepository.findByItemKeyIn(List.of("longsword"))).thenReturn(List.of(longsword()));

        CuratedLootView view = service.getLoot(5L, dmId);

        assertThat(view.coinCp()).isEqualTo(12550L);
        assertThat(view.items()).hasSize(2);
        assertThat(view.items().get(0).name()).isEqualTo("Longsword"); // resolved from catalog
        assertThat(view.items().get(0).custom()).isFalse();
        assertThat(view.items().get(0).category()).isNull(); // catalog stats stay in the catalog
        assertThat(view.items().get(1).name()).isEqualTo("Flametongue");
        assertThat(view.items().get(1).custom()).isTrue();
        assertThat(view.items().get(1).category()).isEqualTo("weapon");
        assertThat(view.items().get(1).unitCostCp()).isEqualTo(500000L);
        assertThat(view.items().get(1).weight()).isEqualByComparingTo("3");
        assertThat(view.items().get(1).damage()).isEqualTo("1d8 slashing + 2d6 fire");
    }

    // --- addItem ---

    @Test
    void addItem_catalogLine_persists_withDefaultedQty() {
        when(lootRepository.findById(5L)).thenReturn(Optional.of(loot));
        when(srdItemRepository.findByItemKey("longsword")).thenReturn(Optional.of(longsword()));

        service.addItem(5L, request("longsword", null, null, null, null, null, null, null, null), dmId);

        ArgumentCaptor<CuratedLootItem> captor = ArgumentCaptor.forClass(CuratedLootItem.class);
        verify(lootItemRepository).save(captor.capture());
        CuratedLootItem saved = captor.getValue();
        assertThat(saved.getCatalogItemKey()).isEqualTo("longsword");
        assertThat(saved.getCustomName()).isNull();
        assertThat(saved.getQty()).isEqualTo(1); // null qty → 1
    }

    @Test
    void addItem_customLine_stampsAttributes_convertingGoldToCopper() {
        when(lootRepository.findById(5L)).thenReturn(Optional.of(loot));

        service.addItem(5L, request(null, "Flametongue", "Ignites on command.", 1,
                "weapon", 50.0, 3.0, "1d8 slashing + 2d6 fire", null), dmId);

        ArgumentCaptor<CuratedLootItem> captor = ArgumentCaptor.forClass(CuratedLootItem.class);
        verify(lootItemRepository).save(captor.capture());
        CuratedLootItem saved = captor.getValue();
        assertThat(saved.getCustomName()).isEqualTo("Flametongue");
        assertThat(saved.getCustomNotes()).isEqualTo("Ignites on command.");
        assertThat(saved.getCategory()).isEqualTo("weapon");
        assertThat(saved.getUnitCostCp()).isEqualTo(5000L); // 50 gp → 5000 cp
        assertThat(saved.getWeight()).isEqualByComparingTo("3.0");
        assertThat(saved.getDamage()).isEqualTo("1d8 slashing + 2d6 fire");
        assertThat(saved.getArmorClass()).isNull();
        verify(srdItemRepository, never()).findByItemKey(any());
    }

    @Test
    void addItem_throws400_whenCatalogLineCarriesAttributes() {
        when(lootRepository.findById(5L)).thenReturn(Optional.of(loot));
        assertThatThrownBy(() -> service.addItem(5L,
                request("longsword", null, null, 1, "weapon", null, null, null, null), dmId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("custom items only")
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        verify(lootItemRepository, never()).save(any());
    }

    @Test
    void addItem_throws400_onBadAttributeValues() {
        when(lootRepository.findById(5L)).thenReturn(Optional.of(loot));
        // Unknown category.
        assertThatThrownBy(() -> service.addItem(5L,
                request(null, "Thing", null, 1, "artifact", null, null, null, null), dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        // Negative value.
        assertThatThrownBy(() -> service.addItem(5L,
                request(null, "Thing", null, 1, "gear", -1.0, null, null, null), dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        // Negative weight.
        assertThatThrownBy(() -> service.addItem(5L,
                request(null, "Thing", null, 1, "gear", null, -0.5, null, null), dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        // Damage on a non-weapon.
        assertThatThrownBy(() -> service.addItem(5L,
                request(null, "Thing", null, 1, "gear", null, null, "1d6", null), dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        // armorClass on a non-armor (no category defaults to gear).
        assertThatThrownBy(() -> service.addItem(5L,
                request(null, "Thing", null, 1, null, null, null, null, "14"), dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        verify(lootItemRepository, never()).save(any());
    }

    @Test
    void addItem_throws400_whenBothOrNeitherOfKeyAndName() {
        when(lootRepository.findById(5L)).thenReturn(Optional.of(loot));
        assertThatThrownBy(() -> service.addItem(5L,
                request("longsword", "Cloak", null, 1, null, null, null, null, null), dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        assertThatThrownBy(() -> service.addItem(5L,
                request("  ", null, null, 1, null, null, null, null, null), dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        verify(lootItemRepository, never()).save(any());
    }

    @Test
    void addItem_throws404_whenCatalogKeyUnknown() {
        when(lootRepository.findById(5L)).thenReturn(Optional.of(loot));
        when(srdItemRepository.findByItemKey("frostbrand")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.addItem(5L,
                request("frostbrand", null, null, 1, null, null, null, null, null), dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(404));
    }

    // --- setCoins ---

    @Test
    void setCoins_storesRoundedCopper() {
        when(lootRepository.findById(5L)).thenReturn(Optional.of(loot));
        when(lootRepository.save(any(CuratedLoot.class))).thenAnswer(inv -> inv.getArgument(0));
        when(lootItemRepository.findByLootIdOrderByIdAsc(5L)).thenReturn(List.of());

        service.setCoins(5L, 125.5, dmId);

        assertThat(loot.getCoinCp()).isEqualTo(12550L);
    }

    @Test
    void setCoins_throws400_whenNegativeOrNull() {
        when(lootRepository.findById(5L)).thenReturn(Optional.of(loot));
        assertThatThrownBy(() -> service.setCoins(5L, -1.0, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        assertThatThrownBy(() -> service.setCoins(5L, null, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
    }

    // --- importLoot ---

    @Test
    void importLoot_appendsLines_andAddsCoinsToPile() {
        loot.setCoinCp(100L);
        when(lootRepository.findById(5L)).thenReturn(Optional.of(loot));
        when(srdItemRepository.findByItemKeyIn(List.of("longsword"))).thenReturn(List.of(longsword()));

        ImportLootRequest request = new ImportLootRequest(10.0, List.of(
                importItem("longsword", null, null, null, null, null, null, null, null),
                importItem(null, "Cloak of Elvenkind", "Advantage on Stealth.", 2,
                        null, null, null, null, null)));
        service.importLoot(5L, request, dmId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CuratedLootItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(lootItemRepository).saveAll(captor.capture());
        List<CuratedLootItem> saved = captor.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getCatalogItemKey()).isEqualTo("longsword");
        assertThat(saved.get(0).getQty()).isEqualTo(1); // null qty → 1
        assertThat(saved.get(1).getCustomName()).isEqualTo("Cloak of Elvenkind");
        assertThat(saved.get(1).getQty()).isEqualTo(2);
        assertThat(saved.get(1).getCategory()).isNull(); // legacy shape imports untouched
        assertThat(loot.getCoinCp()).isEqualTo(1100L); // 100 + 10 gp
    }

    @Test
    void importLoot_persistsCustomAttributes() {
        when(lootRepository.findById(5L)).thenReturn(Optional.of(loot));

        ImportLootRequest request = new ImportLootRequest(null, List.of(
                importItem(null, "Mithral Plate", null, 1, "armor", 800.0, 40.0, null, "18")));
        service.importLoot(5L, request, dmId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CuratedLootItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(lootItemRepository).saveAll(captor.capture());
        CuratedLootItem saved = captor.getValue().get(0);
        assertThat(saved.getCategory()).isEqualTo("armor");
        assertThat(saved.getUnitCostCp()).isEqualTo(80000L);
        assertThat(saved.getWeight()).isEqualByComparingTo("40.0");
        assertThat(saved.getArmorClass()).isEqualTo("18");
        assertThat(saved.getDamage()).isNull();
    }

    @Test
    void importLoot_throws400_whenKeyLineCarriesAttributes_savesNothing() {
        when(lootRepository.findById(5L)).thenReturn(Optional.of(loot));

        ImportLootRequest request = new ImportLootRequest(null, List.of(
                importItem("longsword", null, null, 1, null, 10.0, null, null, null)));
        assertThatThrownBy(() -> service.importLoot(5L, request, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("custom items only")
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        verify(lootItemRepository, never()).saveAll(any());
    }

    @Test
    void importLoot_throws400_listingUnknownKeys_savesNothing() {
        when(lootRepository.findById(5L)).thenReturn(Optional.of(loot));
        when(srdItemRepository.findByItemKeyIn(List.of("frostbrand"))).thenReturn(List.of());

        ImportLootRequest request = new ImportLootRequest(null, List.of(
                importItem("frostbrand", null, null, 1, null, null, null, null, null)));
        assertThatThrownBy(() -> service.importLoot(5L, request, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unknown catalog keys: frostbrand")
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        verify(lootItemRepository, never()).saveAll(any());
    }

    @Test
    void importLoot_throws400_whenLineHasBothKeyAndName() {
        when(lootRepository.findById(5L)).thenReturn(Optional.of(loot));

        ImportLootRequest request = new ImportLootRequest(null, List.of(
                importItem("longsword", "Also a name", null, 1, null, null, null, null, null)));
        assertThatThrownBy(() -> service.importLoot(5L, request, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        verify(lootItemRepository, never()).saveAll(any());
    }

    // --- helpers ---

    private static AddLootItemRequest request(String key, String name, String notes, Integer qty,
                                              String category, Double valueGp, Double weight,
                                              String damage, String armorClass) {
        return new AddLootItemRequest(key, name, notes, qty, category, valueGp, weight, damage, armorClass);
    }

    private static ImportLootRequest.Item importItem(String key, String name, String notes, Integer qty,
                                                     String category, Double valueGp, Double weight,
                                                     String damage, String armorClass) {
        return new ImportLootRequest.Item(key, name, notes, qty, category, valueGp, weight, damage, armorClass);
    }

    private static SrdItem longsword() {
        SrdItem item = new SrdItem();
        item.setId(1L);
        item.setItemKey("longsword");
        item.setName("Longsword");
        item.setCategory("WEAPON");
        item.setCostCp(1500L);
        item.setDetails("{}");
        return item;
    }

    private static CuratedLootItem catalogLine(Long id, String key, int qty) {
        CuratedLootItem line = new CuratedLootItem();
        line.setId(id);
        line.setLootId(5L);
        line.setCatalogItemKey(key);
        line.setQty(qty);
        return line;
    }

    private static CuratedLootItem customLine(Long id, String name, String notes, int qty) {
        CuratedLootItem line = new CuratedLootItem();
        line.setId(id);
        line.setLootId(5L);
        line.setCustomName(name);
        line.setCustomNotes(notes);
        line.setQty(qty);
        return line;
    }

    private static int status(Throwable e) {
        return ((ResponseStatusException) e).getStatusCode().value();
    }
}
