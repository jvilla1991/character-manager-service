package com.moo.charactermanagerservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moo.charactermanagerservice.dto.CuratedShopView;
import com.moo.charactermanagerservice.dto.ImportShopRequest;
import com.moo.charactermanagerservice.dto.ShopSummaryView;
import com.moo.charactermanagerservice.models.Campaign;
import com.moo.charactermanagerservice.models.Shop;
import com.moo.charactermanagerservice.models.ShopItem;
import com.moo.charactermanagerservice.models.SrdItem;
import com.moo.charactermanagerservice.repositories.ShopItemRepository;
import com.moo.charactermanagerservice.repositories.ShopRepository;
import com.moo.charactermanagerservice.repositories.SrdItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CuratedShopServiceTest {

    @Mock private ShopRepository shopRepository;
    @Mock private ShopItemRepository shopItemRepository;
    @Mock private SrdItemRepository srdItemRepository;
    @Mock private CampaignService campaignService;

    private CuratedShopService service;

    private UUID dmId;
    private UUID strangerId;
    private Shop shop;

    @BeforeEach
    void setUp() {
        service = new CuratedShopService(shopRepository, shopItemRepository, srdItemRepository,
                campaignService, new ObjectMapper());
        dmId = UUID.randomUUID();
        strangerId = UUID.randomUUID();

        shop = new Shop();
        shop.setId(5L);
        shop.setCampaignId(1L);
        shop.setDmUserId(dmId);
        shop.setName("Smithy");
    }

    // --- createShop ---

    @Test
    void createShop_assertsCampaignDm_andPersists() {
        when(campaignService.findByIdForDm(1L, dmId)).thenReturn(new Campaign());
        when(shopRepository.save(any(Shop.class))).thenAnswer(inv -> {
            Shop s = inv.getArgument(0);
            s.setId(5L);
            return s;
        });
        when(shopItemRepository.findByShopId(5L)).thenReturn(List.of());

        CuratedShopView view = service.createShop(1L, "Smithy", "Phandalin", dmId);

        assertThat(view.name()).isEqualTo("Smithy");
        assertThat(view.items()).isEmpty();
        verify(shopRepository).save(any(Shop.class));
    }

    @Test
    void createShop_throws400_whenNameBlank() {
        when(campaignService.findByIdForDm(1L, dmId)).thenReturn(new Campaign());
        assertThatThrownBy(() -> service.createShop(1L, "  ", null, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
    }

    @Test
    void createShop_propagates403_whenNotCampaignDm() {
        when(campaignService.findByIdForDm(1L, strangerId))
                .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "Access denied"));
        assertThatThrownBy(() -> service.createShop(1L, "Smithy", null, strangerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
    }

    // --- importShop (pasted JSON) ---

    @Test
    void importShop_createsShopAndLines_withGoldOverridesConvertedToCopper() {
        when(campaignService.findByIdForDm(1L, dmId)).thenReturn(new Campaign());
        when(shopRepository.save(any(Shop.class))).thenAnswer(inv -> {
            Shop s = inv.getArgument(0);
            s.setId(9L);
            return s;
        });
        when(srdItemRepository.findByItemKeyIn(List.of("longsword", "rations")))
                .thenReturn(List.of(srd("longsword", "Longsword", 1500), srd("rations", "Rations", 50)));

        service.importShop(1L, new ImportShopRequest("The Gilded Flask", "Phandalin", List.of(
                new ImportShopRequest.Item("longsword", 12.0),
                new ImportShopRequest.Item("rations", null))), dmId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ShopItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(shopItemRepository).saveAll(captor.capture());
        List<ShopItem> lines = captor.getValue();
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0).getCatalogItemKey()).isEqualTo("longsword");
        assertThat(lines.get(0).getPriceCp()).isEqualTo(1200L); // 12 gp override
        assertThat(lines.get(1).getPriceCp()).isNull();          // inherit catalog price
    }

    @Test
    void importShop_throws400_listingEveryUnknownKey_andWritesNothing() {
        when(campaignService.findByIdForDm(1L, dmId)).thenReturn(new Campaign());
        when(srdItemRepository.findByItemKeyIn(List.of("longsword", "vorpal-blade", "hand-grenade")))
                .thenReturn(List.of(srd("longsword", "Longsword", 1500)));

        assertThatThrownBy(() -> service.importShop(1L, new ImportShopRequest("Bad Shop", null, List.of(
                new ImportShopRequest.Item("longsword", null),
                new ImportShopRequest.Item("vorpal-blade", null),
                new ImportShopRequest.Item("hand-grenade", null))), dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    assertThat(status(e)).isEqualTo(400);
                    assertThat(e.getMessage()).contains("vorpal-blade").contains("hand-grenade");
                });
        verify(shopRepository, never()).save(any());
        verify(shopItemRepository, never()).saveAll(any());
    }

    @Test
    void importShop_throws400_onBlankNameOrDuplicateKeys() {
        when(campaignService.findByIdForDm(1L, dmId)).thenReturn(new Campaign());

        assertThatThrownBy(() -> service.importShop(1L,
                new ImportShopRequest(" ", null, List.of()), dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));

        assertThatThrownBy(() -> service.importShop(1L, new ImportShopRequest("Dupes", null, List.of(
                new ImportShopRequest.Item("longsword", null),
                new ImportShopRequest.Item("longsword", 5.0))), dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        verify(shopRepository, never()).save(any());
    }

    @Test
    void importCategory_acceptsGear() {
        when(shopRepository.findById(5L)).thenReturn(Optional.of(shop));
        when(shopItemRepository.findByShopId(5L)).thenReturn(List.of());
        when(srdItemRepository.findByCategoryOrderByNameAsc("GEAR")).thenReturn(List.of());

        service.importCategory(5L, "GEAR", dmId); // used to 400 — the picker offers Gear

        verify(srdItemRepository).findByCategoryOrderByNameAsc("GEAR");
    }

    // --- listShops ---

    @Test
    void listShops_returnsSummariesWithItemCount() {
        when(campaignService.findByIdForDm(1L, dmId)).thenReturn(new Campaign());
        when(shopRepository.findByCampaignIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(shop));
        when(shopItemRepository.findByShopId(5L)).thenReturn(List.of(shopItem(1L, "longsword", null)));

        List<ShopSummaryView> views = service.listShops(1L, dmId);

        assertThat(views).hasSize(1);
        assertThat(views.get(0).itemCount()).isEqualTo(1);
    }

    // --- getShop / ownership ---

    @Test
    void getShop_resolvesEffectivePrice_overrideElseCatalog() {
        when(shopRepository.findById(5L)).thenReturn(Optional.of(shop));
        when(shopItemRepository.findByShopId(5L)).thenReturn(List.of(
                shopItem(1L, "longsword", null),     // inherit catalog (1500)
                shopItem(2L, "dagger", 50L)));        // override to 50
        when(srdItemRepository.findByItemKeyIn(any())).thenReturn(List.of(
                srd("longsword", "Longsword", 1500), srd("dagger", "Dagger", 200)));

        CuratedShopView view = service.getShop(5L, dmId);

        assertThat(view.items()).hasSize(2);
        assertThat(view.items()).anySatisfy(i -> {
            if (i.catalogItemKey().equals("longsword")) {
                assertThat(i.priceOverrideCp()).isNull();
                assertThat(i.effectiveCostCp()).isEqualTo(1500);
            }
        });
        assertThat(view.items()).anySatisfy(i -> {
            if (i.catalogItemKey().equals("dagger")) {
                assertThat(i.priceOverrideCp()).isEqualTo(50);
                assertThat(i.effectiveCostCp()).isEqualTo(50);
            }
        });
    }

    @Test
    void getShop_throws403_whenNotOwner() {
        when(shopRepository.findById(5L)).thenReturn(Optional.of(shop));
        assertThatThrownBy(() -> service.getShop(5L, strangerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
    }

    @Test
    void getShop_throws404_whenMissing() {
        when(shopRepository.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getShop(9L, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(404));
    }

    // --- addItem ---

    @Test
    void addItem_validatesCatalog_dedupes_andSaves() {
        when(shopRepository.findById(5L)).thenReturn(Optional.of(shop));
        when(srdItemRepository.findByItemKey("longsword")).thenReturn(Optional.of(srd("longsword", "Longsword", 1500)));
        when(shopItemRepository.findByShopIdAndCatalogItemKey(5L, "longsword")).thenReturn(Optional.empty());
        when(shopItemRepository.findByShopId(5L)).thenReturn(List.of(shopItem(1L, "longsword", null)));
        when(srdItemRepository.findByItemKeyIn(any())).thenReturn(List.of(srd("longsword", "Longsword", 1500)));

        service.addItem(5L, "longsword", null, dmId);

        verify(shopItemRepository).save(any(ShopItem.class));
    }

    @Test
    void addItem_throws404_whenCatalogItemUnknown() {
        when(shopRepository.findById(5L)).thenReturn(Optional.of(shop));
        when(srdItemRepository.findByItemKey("frostbrand")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.addItem(5L, "frostbrand", null, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(404));
        verify(shopItemRepository, never()).save(any());
    }

    @Test
    void addItem_throws409_whenAlreadyInShop() {
        when(shopRepository.findById(5L)).thenReturn(Optional.of(shop));
        when(srdItemRepository.findByItemKey("longsword")).thenReturn(Optional.of(srd("longsword", "Longsword", 1500)));
        when(shopItemRepository.findByShopIdAndCatalogItemKey(5L, "longsword"))
                .thenReturn(Optional.of(shopItem(1L, "longsword", null)));
        assertThatThrownBy(() -> service.addItem(5L, "longsword", null, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
        verify(shopItemRepository, never()).save(any());
    }

    @Test
    void addItem_throws400_whenPriceNegative() {
        when(shopRepository.findById(5L)).thenReturn(Optional.of(shop));
        when(srdItemRepository.findByItemKey("longsword")).thenReturn(Optional.of(srd("longsword", "Longsword", 1500)));
        when(shopItemRepository.findByShopIdAndCatalogItemKey(5L, "longsword")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.addItem(5L, "longsword", -10L, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
    }

    // --- importCategory ---

    @Test
    @SuppressWarnings("unchecked")
    void importCategory_addsMissingCatalogItems_skippingExisting() {
        when(shopRepository.findById(5L)).thenReturn(Optional.of(shop));
        when(shopItemRepository.findByShopId(5L)).thenReturn(List.of(shopItem(1L, "longsword", null)));
        when(srdItemRepository.findByCategoryOrderByNameAsc("WEAPON")).thenReturn(List.of(
                srd("longsword", "Longsword", 1500), srd("dagger", "Dagger", 200)));
        when(srdItemRepository.findByItemKeyIn(any())).thenReturn(List.of(srd("longsword", "Longsword", 1500)));

        service.importCategory(5L, "weapon", dmId);

        ArgumentCaptor<List<ShopItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(shopItemRepository).saveAll(captor.capture());
        List<ShopItem> added = captor.getValue();
        assertThat(added).hasSize(1); // longsword already present, only dagger added
        assertThat(added.get(0).getCatalogItemKey()).isEqualTo("dagger");
        assertThat(added.get(0).getPriceCp()).isNull(); // inherits catalog price
    }

    @Test
    void importCategory_throws400_forUnsupportedCategory() {
        when(shopRepository.findById(5L)).thenReturn(Optional.of(shop));
        assertThatThrownBy(() -> service.importCategory(5L, "potions", dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
    }

    // --- removeItem ---

    @Test
    void removeItem_throws404_whenItemNotInThisShop() {
        when(shopRepository.findById(5L)).thenReturn(Optional.of(shop));
        ShopItem other = shopItem(7L, "dagger", null);
        other.setShopId(99L); // belongs to a different shop
        when(shopItemRepository.findById(7L)).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.removeItem(5L, 7L, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(404));
    }

    // --- helpers ---

    private static int status(Throwable e) {
        return ((ResponseStatusException) e).getStatusCode().value();
    }

    private static ShopItem shopItem(Long id, String key, Long priceCp) {
        ShopItem i = new ShopItem();
        i.setId(id);
        i.setShopId(5L);
        i.setCatalogItemKey(key);
        i.setPriceCp(priceCp);
        return i;
    }

    private static SrdItem srd(String key, String name, long costCp) {
        SrdItem s = new SrdItem();
        s.setItemKey(key);
        s.setName(name);
        s.setCategory("WEAPON");
        s.setCostCp(costCp);
        s.setDetails("{\"damage\":\"1d8 slashing\"}");
        return s;
    }
}
