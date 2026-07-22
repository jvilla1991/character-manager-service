package com.moo.charactermanagerservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moo.charactermanagerservice.dto.CuratedShopItemView;
import com.moo.charactermanagerservice.dto.CuratedShopView;
import com.moo.charactermanagerservice.dto.ImportShopRequest;
import com.moo.charactermanagerservice.dto.ShopSummaryView;
import com.moo.charactermanagerservice.models.Shop;
import com.moo.charactermanagerservice.models.ShopItem;
import com.moo.charactermanagerservice.models.SrdItem;
import com.moo.charactermanagerservice.repositories.ShopItemRepository;
import com.moo.charactermanagerservice.repositories.ShopRepository;
import com.moo.charactermanagerservice.repositories.SrdItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * DM-curated shops (Phase 2): persistent, reusable shop definitions a DM builds
 * from the SRD catalog with optional price overrides, later activated into a
 * live session. Ownership mirrors {@link SessionService}: a shop is owned by the
 * campaign's DM and DM-only actions assert it (403 otherwise).
 *
 * <p>Phase 2 MVP is catalog-only with price overrides; quantity is unlimited
 * (no stock column), so there is no decrement here — finite stock is deferred.
 */
@Service
public class CuratedShopService {

    /** Categories the import-template helper accepts (the seeded catalog slices). */
    private static final Set<String> IMPORTABLE_CATEGORIES =
            Set.of("WEAPON", "ARMOR", "MATERIAL_COMPONENT", "GEAR", "TRANSPORT");

    private final ShopRepository shopRepository;
    private final ShopItemRepository shopItemRepository;
    private final SrdItemRepository srdItemRepository;
    private final CampaignService campaignService;
    private final PcJsonColumns json;

    @Autowired
    public CuratedShopService(ShopRepository shopRepository,
                              ShopItemRepository shopItemRepository,
                              SrdItemRepository srdItemRepository,
                              CampaignService campaignService,
                              ObjectMapper objectMapper) {
        this.shopRepository = shopRepository;
        this.shopItemRepository = shopItemRepository;
        this.srdItemRepository = srdItemRepository;
        this.campaignService = campaignService;
        this.json = new PcJsonColumns(objectMapper);
    }

    /** Create an empty curated shop in a campaign. Campaign-DM only. */
    @Transactional
    public CuratedShopView createShop(Long campaignId, String name, String settlement, UUID dmUserId) {
        // Asserts the campaign exists (404) and the caller is its DM (403).
        campaignService.findByIdForDm(campaignId, dmUserId);
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Shop name is required");
        }
        Shop shop = new Shop();
        shop.setCampaignId(campaignId);
        shop.setDmUserId(dmUserId);
        shop.setName(name.trim());
        shop.setSettlement(settlement == null ? null : settlement.trim());
        return buildView(shopRepository.save(shop));
    }

    /** A campaign's curated shops (summaries). Campaign-DM only. */
    @Transactional(readOnly = true)
    public List<ShopSummaryView> listShops(Long campaignId, UUID dmUserId) {
        campaignService.findByIdForDm(campaignId, dmUserId);
        return shopRepository.findByCampaignIdOrderByCreatedAtDesc(campaignId).stream()
                .map(s -> new ShopSummaryView(s.getId(), s.getCampaignId(), s.getName(),
                        s.getSettlement(), shopItemRepository.findByShopId(s.getId()).size()))
                .toList();
    }

    /** A curated shop with its resolved lines. Owner DM only. */
    @Transactional(readOnly = true)
    public CuratedShopView getShop(Long shopId, UUID dmUserId) {
        return buildView(requireOwnedShop(shopId, dmUserId));
    }

    /** Rename / relabel a curated shop. */
    @Transactional
    public CuratedShopView updateShop(Long shopId, String name, String settlement, UUID dmUserId) {
        Shop shop = requireOwnedShop(shopId, dmUserId);
        if (name != null && !name.isBlank()) shop.setName(name.trim());
        shop.setSettlement(settlement == null ? null : settlement.trim());
        return buildView(shopRepository.save(shop));
    }

    /** Delete a curated shop (its lines cascade). */
    @Transactional
    public void deleteShop(Long shopId, UUID dmUserId) {
        Shop shop = requireOwnedShop(shopId, dmUserId);
        shopRepository.delete(shop);
    }

    /** Add a catalog item to the shop, with an optional price override. */
    @Transactional
    public CuratedShopView addItem(Long shopId, String catalogItemKey, Long priceCp, UUID dmUserId) {
        Shop shop = requireOwnedShop(shopId, dmUserId);
        if (srdItemRepository.findByItemKey(catalogItemKey).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found: " + catalogItemKey);
        }
        if (shopItemRepository.findByShopIdAndCatalogItemKey(shopId, catalogItemKey).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Item is already in this shop");
        }
        validatePrice(priceCp);

        ShopItem item = new ShopItem();
        item.setShopId(shopId);
        item.setCatalogItemKey(catalogItemKey);
        item.setPriceCp(priceCp);
        shopItemRepository.save(item);

        touch(shop);
        return buildView(shop);
    }

    /**
     * Bulk-add every catalog item of a category to the shop — the "start from a
     * standard template" helper. Items already present are skipped; added lines
     * inherit the catalog price (no override), ready for the DM to tweak.
     */
    @Transactional
    public CuratedShopView importCategory(Long shopId, String category, UUID dmUserId) {
        Shop shop = requireOwnedShop(shopId, dmUserId);
        String normalized = category == null ? "" : category.trim().toUpperCase();
        if (!IMPORTABLE_CATEGORIES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported category: " + category);
        }
        Set<String> existing = shopItemRepository.findByShopId(shopId).stream()
                .map(ShopItem::getCatalogItemKey).collect(Collectors.toSet());
        List<ShopItem> toAdd = srdItemRepository.findByCategoryOrderByNameAsc(normalized).stream()
                .filter(c -> !existing.contains(c.getItemKey()))
                .map(c -> {
                    ShopItem item = new ShopItem();
                    item.setShopId(shopId);
                    item.setCatalogItemKey(c.getItemKey());
                    item.setPriceCp(null); // inherit catalog price
                    return item;
                })
                .toList();
        shopItemRepository.saveAll(toAdd);
        touch(shop);
        return buildView(shop);
    }

    /**
     * Create a whole curated shop from pasted JSON — name, settlement, and
     * catalog lines with optional gold-price overrides. All-or-nothing: any
     * unknown catalog key fails the import with a 400 listing every bad key,
     * so a typo never leaves a half-built shop behind. Campaign-DM only.
     */
    @Transactional
    public CuratedShopView importShop(Long campaignId, ImportShopRequest request, UUID dmUserId) {
        campaignService.findByIdForDm(campaignId, dmUserId);
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Shop name is required");
        }
        List<ImportShopRequest.Item> lines = request.items() == null ? List.of() : request.items();

        // Validate every key (and collect duplicates away) before writing anything.
        List<String> keys = lines.stream()
                .map(ImportShopRequest.Item::key)
                .filter(k -> k != null && !k.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        if (keys.size() != lines.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Every item needs a unique, non-blank catalog key");
        }
        Set<String> known = srdItemRepository.findByItemKeyIn(keys).stream()
                .map(SrdItem::getItemKey).collect(Collectors.toSet());
        List<String> unknown = keys.stream().filter(k -> !known.contains(k)).toList();
        if (!unknown.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown catalog keys: " + String.join(", ", unknown));
        }

        Shop shop = new Shop();
        shop.setCampaignId(campaignId);
        shop.setDmUserId(dmUserId);
        shop.setName(request.name().trim());
        shop.setSettlement(request.settlement() == null || request.settlement().isBlank()
                ? null : request.settlement().trim());
        Shop saved = shopRepository.save(shop);

        List<ShopItem> toAdd = lines.stream().map(line -> {
            Long priceCp = line.priceGp() == null ? null : Math.round(line.priceGp() * 100);
            validatePrice(priceCp);
            ShopItem item = new ShopItem();
            item.setShopId(saved.getId());
            item.setCatalogItemKey(line.key().trim());
            item.setPriceCp(priceCp);
            return item;
        }).toList();
        shopItemRepository.saveAll(toAdd);

        return buildView(saved);
    }

    /** Change a line's price override (null = back to catalog price). */
    @Transactional
    public CuratedShopView updateItem(Long shopId, Long itemId, Long priceCp, UUID dmUserId) {
        Shop shop = requireOwnedShop(shopId, dmUserId);
        validatePrice(priceCp);
        ShopItem item = requireItem(shopId, itemId);
        item.setPriceCp(priceCp);
        shopItemRepository.save(item);

        touch(shop);
        return buildView(shop);
    }

    /** Remove a line from the shop. */
    @Transactional
    public CuratedShopView removeItem(Long shopId, Long itemId, UUID dmUserId) {
        Shop shop = requireOwnedShop(shopId, dmUserId);
        ShopItem item = requireItem(shopId, itemId);
        shopItemRepository.delete(item);

        touch(shop);
        return buildView(shop);
    }

    // --- internals -----------------------------------------------------------

    private CuratedShopView buildView(Shop shop) {
        List<ShopItem> lines = shopItemRepository.findByShopId(shop.getId());
        Map<String, SrdItem> catalog = lines.isEmpty() ? Map.of() :
                srdItemRepository.findByItemKeyIn(
                                lines.stream().map(ShopItem::getCatalogItemKey).toList()).stream()
                        .collect(Collectors.toMap(SrdItem::getItemKey, Function.identity()));

        List<CuratedShopItemView> items = lines.stream()
                .map(line -> toItemView(line, catalog.get(line.getCatalogItemKey())))
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(CuratedShopItemView::name))
                .toList();

        return new CuratedShopView(shop.getId(), shop.getCampaignId(), shop.getName(),
                shop.getSettlement(), items);
    }

    private CuratedShopItemView toItemView(ShopItem line, SrdItem catalog) {
        if (catalog == null) return null; // catalog item vanished — skip defensively
        long effective = line.getPriceCp() != null ? line.getPriceCp() : catalog.getCostCp();
        return new CuratedShopItemView(
                line.getId(), catalog.getItemKey(), catalog.getName(), catalog.getCategory(),
                line.getPriceCp(), effective, catalog.getWeight(),
                json.parseObject(catalog.getDetails()));
    }

    private Shop requireOwnedShop(Long shopId, UUID dmUserId) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Shop not found with id " + shopId));
        if (!dmUserId.equals(shop.getDmUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        return shop;
    }

    private ShopItem requireItem(Long shopId, Long itemId) {
        ShopItem item = shopItemRepository.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Shop item not found with id " + itemId));
        if (!shopId.equals(item.getShopId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop item not found in this shop");
        }
        return item;
    }

    private void validatePrice(Long priceCp) {
        if (priceCp != null && priceCp < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Price cannot be negative");
        }
    }

    /** Bump the shop's updated_at so edits to its lines are reflected. */
    private void touch(Shop shop) {
        shopRepository.save(shop);
    }
}
