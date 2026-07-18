package com.moo.charactermanagerservice.services;

import com.moo.charactermanagerservice.dto.AddLootItemRequest;
import com.moo.charactermanagerservice.dto.CuratedLootItemView;
import com.moo.charactermanagerservice.dto.CuratedLootSummaryView;
import com.moo.charactermanagerservice.dto.CuratedLootView;
import com.moo.charactermanagerservice.dto.ImportLootRequest;
import com.moo.charactermanagerservice.models.CuratedLoot;
import com.moo.charactermanagerservice.models.CuratedLootItem;
import com.moo.charactermanagerservice.models.SrdItem;
import com.moo.charactermanagerservice.repositories.CuratedLootItemRepository;
import com.moo.charactermanagerservice.repositories.CuratedLootRepository;
import com.moo.charactermanagerservice.repositories.SrdItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * DM-curated loot lists: persistent, reusable spoils (item lines plus a coin
 * pile) a DM preps on the campaign dashboard, later dropped into a live
 * session's claim pool by {@link LootService#openLoot} (copy semantics — the
 * curated list is never mutated by a session). Ownership mirrors
 * {@link CuratedShopService} / {@link CuratedEncounterService}: a list is owned
 * by the campaign's DM and DM-only actions assert it (403 otherwise). Custom
 * lines may carry the same attributes a DM grant stamps (category / value /
 * weight / damage / armor class — see {@link LootLines#validateAttributes}), so
 * claimed custom items are as richly stat'd as granted ones.
 */
@Service
public class CuratedLootService {

    private final CuratedLootRepository lootRepository;
    private final CuratedLootItemRepository lootItemRepository;
    private final SrdItemRepository srdItemRepository;
    private final CampaignService campaignService;

    @Autowired
    public CuratedLootService(CuratedLootRepository lootRepository,
                              CuratedLootItemRepository lootItemRepository,
                              SrdItemRepository srdItemRepository,
                              CampaignService campaignService) {
        this.lootRepository = lootRepository;
        this.lootItemRepository = lootItemRepository;
        this.srdItemRepository = srdItemRepository;
        this.campaignService = campaignService;
    }

    /** Create an empty curated loot list in a campaign. Campaign-DM only. */
    @Transactional
    public CuratedLootView createLoot(Long campaignId, String name, String notes, UUID dmUserId) {
        // Asserts the campaign exists (404) and the caller is its DM (403).
        campaignService.findByIdForDm(campaignId, dmUserId);
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Loot name is required");
        }
        CuratedLoot loot = new CuratedLoot();
        loot.setCampaignId(campaignId);
        loot.setDmUserId(dmUserId);
        loot.setName(name.trim());
        loot.setNotes(notes == null || notes.isBlank() ? null : notes.trim());
        return buildView(lootRepository.save(loot));
    }

    /** A campaign's curated loot lists (summaries). Campaign-DM only. */
    @Transactional(readOnly = true)
    public List<CuratedLootSummaryView> listLoot(Long campaignId, UUID dmUserId) {
        campaignService.findByIdForDm(campaignId, dmUserId);
        return lootRepository.findByCampaignIdOrderByCreatedAtDesc(campaignId).stream()
                .map(l -> new CuratedLootSummaryView(l.getId(), l.getCampaignId(), l.getName(),
                        l.getNotes(), l.getCoinCp(), lootItemRepository.countByLootId(l.getId())))
                .toList();
    }

    /** A curated loot list with its resolved lines. Owner DM only. */
    @Transactional(readOnly = true)
    public CuratedLootView getLoot(Long lootId, UUID dmUserId) {
        return buildView(requireOwnedLoot(lootId, dmUserId));
    }

    /** Rename / re-note a curated loot list. */
    @Transactional
    public CuratedLootView updateLoot(Long lootId, String name, String notes, UUID dmUserId) {
        CuratedLoot loot = requireOwnedLoot(lootId, dmUserId);
        if (name != null && !name.isBlank()) loot.setName(name.trim());
        loot.setNotes(notes == null || notes.isBlank() ? null : notes.trim());
        return buildView(lootRepository.save(loot));
    }

    /** Delete a curated loot list (its lines cascade). */
    @Transactional
    public void deleteLoot(Long lootId, UUID dmUserId) {
        lootRepository.delete(requireOwnedLoot(lootId, dmUserId));
    }

    /** Add a prepped line — a catalog item, or a custom item with optional attributes. */
    @Transactional
    public CuratedLootView addItem(Long lootId, AddLootItemRequest request, UUID dmUserId) {
        CuratedLoot loot = requireOwnedLoot(lootId, dmUserId);
        LootLines.validate(request.catalogItemKey(), request.customName(), request.qty());
        String key = LootLines.normalizeKey(request.catalogItemKey());
        LootLines.validateAttributes(key != null, request.category(), request.valueGp(),
                request.weight(), request.damage(), request.armorClass());
        if (key != null && srdItemRepository.findByItemKey(key).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found: " + key);
        }

        CuratedLootItem item = new CuratedLootItem();
        item.setLootId(lootId);
        item.setCatalogItemKey(key);
        item.setCustomName(key != null || request.customName() == null
                ? null : request.customName().trim());
        item.setCustomNotes(request.customNotes() == null || request.customNotes().isBlank()
                ? null : request.customNotes().trim());
        item.setQty(request.qty() == null ? 1 : request.qty());
        LootLines.applyAttributes(item, request.category(), request.valueGp(),
                request.weight(), request.damage(), request.armorClass());
        lootItemRepository.save(item);

        touch(loot);
        return buildView(loot);
    }

    /** Update a line's quantity (and, for custom lines, its name/notes). */
    @Transactional
    public CuratedLootView updateItem(Long lootId, Long lootItemId, Integer qty,
                                      String customName, String customNotes, UUID dmUserId) {
        CuratedLoot loot = requireOwnedLoot(lootId, dmUserId);
        CuratedLootItem item = requireItem(lootId, lootItemId);
        if (qty != null && qty < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "qty must be at least 1");
        }
        if (qty != null) item.setQty(qty);
        if (item.getCatalogItemKey() == null) { // custom line: name/notes are editable
            if (customName != null && !customName.isBlank()) item.setCustomName(customName.trim());
            item.setCustomNotes(customNotes == null || customNotes.isBlank() ? null : customNotes.trim());
        }
        lootItemRepository.save(item);

        touch(loot);
        return buildView(loot);
    }

    /** Remove a line from the list. */
    @Transactional
    public CuratedLootView removeItem(Long lootId, Long lootItemId, UUID dmUserId) {
        CuratedLoot loot = requireOwnedLoot(lootId, dmUserId);
        lootItemRepository.delete(requireItem(lootId, lootItemId));

        touch(loot);
        return buildView(loot);
    }

    /** Set the list's prepped coin pile, in gold (fractions allowed). */
    @Transactional
    public CuratedLootView setCoins(Long lootId, Double coinGp, UUID dmUserId) {
        CuratedLoot loot = requireOwnedLoot(lootId, dmUserId);
        if (coinGp == null || coinGp < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "coinGp must be zero or more");
        }
        loot.setCoinCp(Math.round(coinGp * 100));
        return buildView(lootRepository.save(loot));
    }

    /**
     * Bulk-add lines from pasted JSON, appending to the list's existing loot
     * ({@code coinGp} adds to the pile). All-or-nothing: any invalid line or
     * unknown catalog key fails the import with a 400 (unknown keys listed),
     * mirroring {@link CuratedShopService#importShop}. Duplicate lines are
     * allowed — two identical loot drops are legal, unlike shop stock. Legacy
     * payloads (key/name/notes/qty only) import unchanged.
     */
    @Transactional
    public CuratedLootView importLoot(Long lootId, ImportLootRequest request, UUID dmUserId) {
        CuratedLoot loot = requireOwnedLoot(lootId, dmUserId);
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Import payload is required");
        }
        if (request.coinGp() != null && request.coinGp() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "coinGp must be zero or more");
        }
        List<ImportLootRequest.Item> lines = request.items() == null ? List.of() : request.items();

        // Validate every line before writing anything.
        for (ImportLootRequest.Item line : lines) {
            boolean hasKey = line.key() != null && !line.key().isBlank();
            boolean hasName = line.name() != null && !line.name().isBlank();
            if (hasKey == hasName) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Each item needs either a \"key\" or a \"name\" (not both)");
            }
            if (line.qty() != null && line.qty() < 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "qty must be at least 1");
            }
            LootLines.validateAttributes(hasKey, line.category(), line.valueGp(),
                    line.weight(), line.damage(), line.armorClass());
        }
        List<String> keys = lines.stream()
                .map(ImportLootRequest.Item::key)
                .filter(k -> k != null && !k.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        Set<String> known = srdItemRepository.findByItemKeyIn(keys).stream()
                .map(SrdItem::getItemKey).collect(Collectors.toSet());
        List<String> unknown = keys.stream().filter(k -> !known.contains(k)).toList();
        if (!unknown.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown catalog keys: " + String.join(", ", unknown));
        }

        List<CuratedLootItem> toAdd = lines.stream().map(line -> {
            CuratedLootItem item = new CuratedLootItem();
            item.setLootId(lootId);
            boolean hasKey = line.key() != null && !line.key().isBlank();
            item.setCatalogItemKey(hasKey ? line.key().trim() : null);
            item.setCustomName(hasKey ? null : line.name().trim());
            item.setCustomNotes(hasKey || line.notes() == null || line.notes().isBlank()
                    ? null : line.notes().trim());
            item.setQty(line.qty() == null ? 1 : line.qty());
            LootLines.applyAttributes(item, line.category(), line.valueGp(),
                    line.weight(), line.damage(), line.armorClass());
            return item;
        }).toList();
        lootItemRepository.saveAll(toAdd);

        if (request.coinGp() != null) {
            loot.setCoinCp(loot.getCoinCp() + Math.round(request.coinGp() * 100));
        }
        touch(loot);
        return buildView(loot);
    }

    // --- internals -----------------------------------------------------------

    private CuratedLootView buildView(CuratedLoot loot) {
        List<CuratedLootItem> lines = lootItemRepository.findByLootIdOrderByIdAsc(loot.getId());
        List<String> keys = lines.stream()
                .map(CuratedLootItem::getCatalogItemKey)
                .filter(Objects::nonNull)
                .toList();
        Map<String, SrdItem> catalog = keys.isEmpty() ? Map.of() :
                srdItemRepository.findByItemKeyIn(keys).stream()
                        .collect(Collectors.toMap(SrdItem::getItemKey, Function.identity()));

        List<CuratedLootItemView> items = lines.stream()
                .map(line -> {
                    if (line.getCatalogItemKey() == null) {
                        return new CuratedLootItemView(line.getId(), null, line.getCustomName(),
                                true, line.getCustomNotes(), line.getCategory(), line.getUnitCostCp(),
                                line.getWeight(), line.getDamage(), line.getArmorClass(), line.getQty());
                    }
                    SrdItem c = catalog.get(line.getCatalogItemKey());
                    if (c == null) return null; // catalog item vanished — skip defensively
                    return new CuratedLootItemView(line.getId(), c.getItemKey(), c.getName(),
                            false, null, null, null, null, null, null, line.getQty());
                })
                .filter(Objects::nonNull)
                .toList();

        return new CuratedLootView(loot.getId(), loot.getCampaignId(), loot.getName(),
                loot.getNotes(), loot.getCoinCp(), items);
    }

    private CuratedLoot requireOwnedLoot(Long lootId, UUID dmUserId) {
        CuratedLoot loot = lootRepository.findById(lootId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Loot not found with id " + lootId));
        if (!dmUserId.equals(loot.getDmUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        return loot;
    }

    private CuratedLootItem requireItem(Long lootId, Long lootItemId) {
        CuratedLootItem item = lootItemRepository.findById(lootItemId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Loot item not found with id " + lootItemId));
        if (!lootId.equals(item.getLootId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Loot item not found in this list");
        }
        return item;
    }

    /** Bump the list's updated_at so edits to its lines are reflected. */
    private void touch(CuratedLoot loot) {
        lootRepository.save(loot);
    }
}
