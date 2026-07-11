package com.moo.charactermanagerservice.services;

import com.moo.charactermanagerservice.dto.EncounterCreatureView;
import com.moo.charactermanagerservice.dto.EncounterLootItemView;
import com.moo.charactermanagerservice.dto.EncounterSummaryView;
import com.moo.charactermanagerservice.dto.EncounterView;
import com.moo.charactermanagerservice.dto.ImportLootRequest;
import com.moo.charactermanagerservice.models.Encounter;
import com.moo.charactermanagerservice.models.EncounterCreature;
import com.moo.charactermanagerservice.models.EncounterLootItem;
import com.moo.charactermanagerservice.models.SrdItem;
import com.moo.charactermanagerservice.repositories.EncounterCreatureRepository;
import com.moo.charactermanagerservice.repositories.EncounterLootItemRepository;
import com.moo.charactermanagerservice.repositories.EncounterRepository;
import com.moo.charactermanagerservice.repositories.SrdItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * DM-curated encounters: persistent, reusable encounter definitions a DM builds
 * as a free-hand list of enemy creatures, later loaded into a live session (where
 * each creature becomes an enemy combatant — see {@code SessionService.loadEncounter}).
 * Ownership mirrors {@link CuratedShopService}: an encounter is owned by the
 * campaign's DM and DM-only actions assert it (403 otherwise).
 */
@Service
public class CuratedEncounterService {

    private final EncounterRepository encounterRepository;
    private final EncounterCreatureRepository creatureRepository;
    private final EncounterLootItemRepository lootItemRepository;
    private final SrdItemRepository srdItemRepository;
    private final CampaignService campaignService;

    @Autowired
    public CuratedEncounterService(EncounterRepository encounterRepository,
                                   EncounterCreatureRepository creatureRepository,
                                   EncounterLootItemRepository lootItemRepository,
                                   SrdItemRepository srdItemRepository,
                                   CampaignService campaignService) {
        this.encounterRepository = encounterRepository;
        this.creatureRepository = creatureRepository;
        this.lootItemRepository = lootItemRepository;
        this.srdItemRepository = srdItemRepository;
        this.campaignService = campaignService;
    }

    /** Create an empty curated encounter in a campaign. Campaign-DM only. */
    @Transactional
    public EncounterView createEncounter(Long campaignId, String name, String notes, UUID dmUserId) {
        // Asserts the campaign exists (404) and the caller is its DM (403).
        campaignService.findByIdForDm(campaignId, dmUserId);
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Encounter name is required");
        }
        Encounter encounter = new Encounter();
        encounter.setCampaignId(campaignId);
        encounter.setDmUserId(dmUserId);
        encounter.setName(name.trim());
        encounter.setNotes(notes == null ? null : notes.trim());
        return buildView(encounterRepository.save(encounter));
    }

    /** A campaign's curated encounters (summaries). Campaign-DM only. */
    @Transactional(readOnly = true)
    public List<EncounterSummaryView> listEncounters(Long campaignId, UUID dmUserId) {
        campaignService.findByIdForDm(campaignId, dmUserId);
        return encounterRepository.findByCampaignIdOrderByCreatedAtDesc(campaignId).stream()
                .map(e -> new EncounterSummaryView(e.getId(), e.getCampaignId(), e.getName(),
                        e.getNotes(), creatureRepository.countByEncounterId(e.getId())))
                .toList();
    }

    /** A curated encounter with its creature lines. Owner DM only. */
    @Transactional(readOnly = true)
    public EncounterView getEncounter(Long encounterId, UUID dmUserId) {
        return buildView(requireOwnedEncounter(encounterId, dmUserId));
    }

    /** Rename / re-note a curated encounter. */
    @Transactional
    public EncounterView updateEncounter(Long encounterId, String name, String notes, UUID dmUserId) {
        Encounter encounter = requireOwnedEncounter(encounterId, dmUserId);
        if (name != null && !name.isBlank()) encounter.setName(name.trim());
        encounter.setNotes(notes == null ? null : notes.trim());
        return buildView(encounterRepository.save(encounter));
    }

    /** Delete a curated encounter (its creatures cascade). */
    @Transactional
    public void deleteEncounter(Long encounterId, UUID dmUserId) {
        Encounter encounter = requireOwnedEncounter(encounterId, dmUserId);
        encounterRepository.delete(encounter);
    }

    /** Add a creature line to the encounter. */
    @Transactional
    public EncounterView addCreature(Long encounterId, String name, Short dexModifier,
                                     Short hpMax, Integer quantity, UUID dmUserId) {
        Encounter encounter = requireOwnedEncounter(encounterId, dmUserId);
        validateCreature(name, dexModifier, quantity);

        EncounterCreature creature = new EncounterCreature();
        creature.setEncounterId(encounterId);
        creature.setName(name.trim());
        creature.setDexModifier(dexModifier);
        creature.setHpMax(hpMax);
        creature.setQuantity(quantity == null ? 1 : quantity);
        creatureRepository.save(creature);

        touch(encounter);
        return buildView(encounter);
    }

    /** Update a creature line's fields. */
    @Transactional
    public EncounterView updateCreature(Long encounterId, Long creatureId, String name,
                                        Short dexModifier, Short hpMax, Integer quantity, UUID dmUserId) {
        Encounter encounter = requireOwnedEncounter(encounterId, dmUserId);
        validateCreature(name, dexModifier, quantity);
        EncounterCreature creature = requireCreature(encounterId, creatureId);
        creature.setName(name.trim());
        creature.setDexModifier(dexModifier);
        creature.setHpMax(hpMax);
        creature.setQuantity(quantity == null ? 1 : quantity);
        creatureRepository.save(creature);

        touch(encounter);
        return buildView(encounter);
    }

    /** Remove a creature line from the encounter. */
    @Transactional
    public EncounterView removeCreature(Long encounterId, Long creatureId, UUID dmUserId) {
        Encounter encounter = requireOwnedEncounter(encounterId, dmUserId);
        EncounterCreature creature = requireCreature(encounterId, creatureId);
        creatureRepository.delete(creature);

        touch(encounter);
        return buildView(encounter);
    }

    // --- loot ------------------------------------------------------------------

    /** Add a prepped loot line — a catalog item or a free-hand custom item. */
    @Transactional
    public EncounterView addLootItem(Long encounterId, String catalogItemKey, String customName,
                                     String customNotes, Integer qty, UUID dmUserId) {
        Encounter encounter = requireOwnedEncounter(encounterId, dmUserId);
        LootLines.validate(catalogItemKey, customName, qty);
        String key = LootLines.normalizeKey(catalogItemKey);
        if (key != null && srdItemRepository.findByItemKey(key).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found: " + key);
        }

        EncounterLootItem item = new EncounterLootItem();
        item.setEncounterId(encounterId);
        item.setCatalogItemKey(key);
        item.setCustomName(key != null || customName == null ? null : customName.trim());
        item.setCustomNotes(customNotes == null || customNotes.isBlank() ? null : customNotes.trim());
        item.setQty(qty == null ? 1 : qty);
        lootItemRepository.save(item);

        touch(encounter);
        return buildView(encounter);
    }

    /** Update a loot line's quantity (and, for custom lines, its name/notes). */
    @Transactional
    public EncounterView updateLootItem(Long encounterId, Long lootItemId, Integer qty,
                                        String customName, String customNotes, UUID dmUserId) {
        Encounter encounter = requireOwnedEncounter(encounterId, dmUserId);
        EncounterLootItem item = requireLootItem(encounterId, lootItemId);
        if (qty != null && qty < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "qty must be at least 1");
        }
        if (qty != null) item.setQty(qty);
        if (item.getCatalogItemKey() == null) { // custom line: name/notes are editable
            if (customName != null && !customName.isBlank()) item.setCustomName(customName.trim());
            item.setCustomNotes(customNotes == null || customNotes.isBlank() ? null : customNotes.trim());
        }
        lootItemRepository.save(item);

        touch(encounter);
        return buildView(encounter);
    }

    /** Remove a loot line from the encounter. */
    @Transactional
    public EncounterView removeLootItem(Long encounterId, Long lootItemId, UUID dmUserId) {
        Encounter encounter = requireOwnedEncounter(encounterId, dmUserId);
        lootItemRepository.delete(requireLootItem(encounterId, lootItemId));

        touch(encounter);
        return buildView(encounter);
    }

    /** Set the encounter's prepped coin pile, in gold (fractions allowed). */
    @Transactional
    public EncounterView setLootCoins(Long encounterId, Double coinGp, UUID dmUserId) {
        Encounter encounter = requireOwnedEncounter(encounterId, dmUserId);
        if (coinGp == null || coinGp < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "coinGp must be zero or more");
        }
        encounter.setLootCoinCp(Math.round(coinGp * 100));
        return buildView(encounterRepository.save(encounter));
    }

    /**
     * Bulk-add loot lines from pasted JSON, appending to the encounter's existing
     * loot ({@code coinGp} adds to the pile). All-or-nothing: any unknown catalog
     * key fails the import with a 400 listing every bad key, mirroring
     * {@link CuratedShopService#importShop}. Duplicate lines are allowed — two
     * identical loot drops are legal, unlike shop stock.
     */
    @Transactional
    public EncounterView importLoot(Long encounterId, ImportLootRequest request, UUID dmUserId) {
        Encounter encounter = requireOwnedEncounter(encounterId, dmUserId);
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

        List<EncounterLootItem> toAdd = lines.stream().map(line -> {
            EncounterLootItem item = new EncounterLootItem();
            item.setEncounterId(encounterId);
            boolean hasKey = line.key() != null && !line.key().isBlank();
            item.setCatalogItemKey(hasKey ? line.key().trim() : null);
            item.setCustomName(hasKey ? null : line.name().trim());
            item.setCustomNotes(hasKey || line.notes() == null || line.notes().isBlank()
                    ? null : line.notes().trim());
            item.setQty(line.qty() == null ? 1 : line.qty());
            return item;
        }).toList();
        lootItemRepository.saveAll(toAdd);

        if (request.coinGp() != null) {
            encounter.setLootCoinCp(encounter.getLootCoinCp() + Math.round(request.coinGp() * 100));
        }
        touch(encounter);
        return buildView(encounter);
    }

    // --- internals -----------------------------------------------------------

    private EncounterView buildView(Encounter encounter) {
        List<EncounterCreatureView> creatures = creatureRepository
                .findByEncounterIdOrderByIdAsc(encounter.getId()).stream()
                .map(c -> new EncounterCreatureView(c.getId(), c.getName(),
                        c.getDexModifier(), c.getHpMax(), c.getQuantity()))
                .toList();
        return new EncounterView(encounter.getId(), encounter.getCampaignId(),
                encounter.getName(), encounter.getNotes(), creatures,
                encounter.getLootCoinCp(), lootViews(encounter.getId()));
    }

    /** Resolve the encounter's loot lines (catalog names looked up in one batch). */
    private List<EncounterLootItemView> lootViews(Long encounterId) {
        List<EncounterLootItem> lines = lootItemRepository.findByEncounterIdOrderByIdAsc(encounterId);
        if (lines.isEmpty()) return List.of();
        List<String> keys = lines.stream()
                .map(EncounterLootItem::getCatalogItemKey)
                .filter(java.util.Objects::nonNull)
                .toList();
        Map<String, SrdItem> catalog = keys.isEmpty() ? Map.of() :
                srdItemRepository.findByItemKeyIn(keys).stream()
                        .collect(Collectors.toMap(SrdItem::getItemKey, Function.identity()));

        return lines.stream()
                .map(line -> {
                    if (line.getCatalogItemKey() == null) {
                        return new EncounterLootItemView(line.getId(), null,
                                line.getCustomName(), true, line.getCustomNotes(), line.getQty());
                    }
                    SrdItem c = catalog.get(line.getCatalogItemKey());
                    if (c == null) return null; // catalog item vanished — skip defensively
                    return new EncounterLootItemView(line.getId(), c.getItemKey(),
                            c.getName(), false, null, line.getQty());
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private EncounterLootItem requireLootItem(Long encounterId, Long lootItemId) {
        EncounterLootItem item = lootItemRepository.findById(lootItemId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Loot item not found with id " + lootItemId));
        if (!encounterId.equals(item.getEncounterId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Loot item not found in this encounter");
        }
        return item;
    }

    private Encounter requireOwnedEncounter(Long encounterId, UUID dmUserId) {
        Encounter encounter = encounterRepository.findById(encounterId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Encounter not found with id " + encounterId));
        if (!dmUserId.equals(encounter.getDmUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        return encounter;
    }

    private EncounterCreature requireCreature(Long encounterId, Long creatureId) {
        EncounterCreature creature = creatureRepository.findById(creatureId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Creature not found with id " + creatureId));
        if (!encounterId.equals(creature.getEncounterId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Creature not found in this encounter");
        }
        return creature;
    }

    private void validateCreature(String name, Short dexModifier, Integer quantity) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Creature name is required");
        }
        if (dexModifier == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dexModifier is required");
        }
        if (quantity != null && quantity < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quantity must be at least 1");
        }
    }

    /** Bump the encounter's updated_at so edits to its creatures are reflected. */
    private void touch(Encounter encounter) {
        encounterRepository.save(encounter);
    }
}
