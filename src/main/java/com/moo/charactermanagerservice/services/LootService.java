package com.moo.charactermanagerservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moo.charactermanagerservice.dto.AddLootItemRequest;
import com.moo.charactermanagerservice.dto.ClaimResult;
import com.moo.charactermanagerservice.dto.LootItemView;
import com.moo.charactermanagerservice.dto.LootView;
import com.moo.charactermanagerservice.models.CombatSession;
import com.moo.charactermanagerservice.models.CuratedLoot;
import com.moo.charactermanagerservice.models.CuratedLootItem;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.models.PcActivityType;
import com.moo.charactermanagerservice.models.SessionLoot;
import com.moo.charactermanagerservice.models.SessionLootItem;
import com.moo.charactermanagerservice.models.SessionParticipant;
import com.moo.charactermanagerservice.models.SessionStatus;
import com.moo.charactermanagerservice.models.SrdItem;
import com.moo.charactermanagerservice.repositories.CombatSessionRepository;
import com.moo.charactermanagerservice.repositories.CuratedLootItemRepository;
import com.moo.charactermanagerservice.repositories.CuratedLootRepository;
import com.moo.charactermanagerservice.repositories.PCRepository;
import com.moo.charactermanagerservice.repositories.SessionLootItemRepository;
import com.moo.charactermanagerservice.repositories.SessionLootRepository;
import com.moo.charactermanagerservice.repositories.SessionParticipantRepository;
import com.moo.charactermanagerservice.repositories.SrdItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Post-combat loot: the transient pool a DM opens in a live session, seeded by
 * copying a curated loot list's prepped lines (or built from scratch), edited as
 * an invisible draft, then dropped for players to claim first-come-first-served.
 * Mirrors {@link ShopService}'s shape — DM-owned session state plus a
 * write-through to the canonical pc row — with one addition: claims mutate
 * shared contested stock ({@code qtyRemaining} / {@code coinCpRemaining}), so
 * the loot row is locked before the pc row (fixed order prevents deadlock) and
 * every claim bumps the session version so all pollers see the new remainder.
 * Unclaimed loot is deleted when the session ends (see
 * {@code SessionService.endSession}) — snooze you lose.
 */
@Service
public class LootService {

    private final CombatSessionRepository sessionRepository;
    private final SessionLootRepository lootRepository;
    private final SessionLootItemRepository lootItemRepository;
    private final SessionParticipantRepository participantRepository;
    private final CuratedLootRepository curatedLootRepository;
    private final CuratedLootItemRepository curatedLootItemRepository;
    private final SrdItemRepository srdItemRepository;
    private final PCRepository pcRepository;
    private final PcActivityLogService activityLogService;
    private final PcJsonColumns json;

    @Autowired
    public LootService(CombatSessionRepository sessionRepository,
                       SessionLootRepository lootRepository,
                       SessionLootItemRepository lootItemRepository,
                       SessionParticipantRepository participantRepository,
                       CuratedLootRepository curatedLootRepository,
                       CuratedLootItemRepository curatedLootItemRepository,
                       SrdItemRepository srdItemRepository,
                       PCRepository pcRepository,
                       PcActivityLogService activityLogService,
                       ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.lootRepository = lootRepository;
        this.lootItemRepository = lootItemRepository;
        this.participantRepository = participantRepository;
        this.curatedLootRepository = curatedLootRepository;
        this.curatedLootItemRepository = curatedLootItemRepository;
        this.srdItemRepository = srdItemRepository;
        this.pcRepository = pcRepository;
        this.activityLogService = activityLogService;
        this.json = new PcJsonColumns(objectMapper);
    }

    /**
     * DM opens a loot pool as an invisible draft, replacing any existing pool
     * (one per session). A {@code lootId} seeds it by COPYING that curated loot
     * list's lines and coin pile — the curated prep is never mutated, so a list
     * can be dropped any number of times.
     */
    @Transactional
    public LootView openLoot(Long sessionId, Long lootId, String name, UUID dmUserId) {
        CombatSession session = activeSessionForDm(sessionId, dmUserId);

        clearExistingLoot(sessionId);

        SessionLoot pool = new SessionLoot();
        pool.setSessionId(sessionId);
        pool.setName(name == null || name.isBlank() ? null : name.trim());

        if (lootId != null) {
            CuratedLoot curated = curatedLootRepository.findById(lootId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Loot not found with id " + lootId));
            // The list must belong to this DM and to this session's campaign
            // (same checks as SessionService.loadEncounter).
            if (!dmUserId.equals(curated.getDmUserId())
                    || !Objects.equals(curated.getCampaignId(), session.getCampaignId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
            }
            if (pool.getName() == null) pool.setName(curated.getName());
            pool.setCoinCpTotal(curated.getCoinCp());
            pool.setCoinCpRemaining(curated.getCoinCp());
        }
        SessionLoot saved = lootRepository.save(pool);

        if (lootId != null) {
            List<SessionLootItem> copies = curatedLootItemRepository
                    .findByLootIdOrderByIdAsc(lootId).stream()
                    .map(line -> copyLine(saved.getId(), line))
                    .toList();
            lootItemRepository.saveAll(copies);
        }

        bump(session);
        return buildLootView(saved);
    }

    /** DM publishes the draft — players can now see and claim. */
    @Transactional
    public LootView dropLoot(Long sessionId, UUID dmUserId) {
        CombatSession session = activeSessionForDm(sessionId, dmUserId);
        SessionLoot pool = requireLoot(sessionId);
        pool.setDropped(true);
        lootRepository.save(pool);

        bump(session);
        return buildLootView(pool);
    }

    /** DM discards the pool; whatever is unclaimed is gone. */
    @Transactional
    public void closeLoot(Long sessionId, UUID dmUserId) {
        CombatSession session = findSession(sessionId);
        assertDm(session, dmUserId);

        lootRepository.findBySessionId(sessionId).ifPresent(pool -> {
            lootItemRepository.deleteBySessionLootId(pool.getId());
            lootRepository.delete(pool);
        });
        bump(session);
    }

    /** DM adds a line to the pool (draft or already dropped — live edits are allowed). */
    @Transactional
    public LootView addItem(Long sessionId, AddLootItemRequest request, UUID dmUserId) {
        CombatSession session = activeSessionForDm(sessionId, dmUserId);
        SessionLoot pool = requireLoot(sessionId);
        LootLines.validate(request.catalogItemKey(), request.customName(), request.qty());
        String key = LootLines.normalizeKey(request.catalogItemKey());
        LootLines.validateAttributes(key != null, request.category(), request.valueGp(),
                request.weight(), request.damage(), request.armorClass());
        if (key != null && srdItemRepository.findByItemKey(key).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found: " + key);
        }

        SessionLootItem item = new SessionLootItem();
        item.setSessionLootId(pool.getId());
        item.setCatalogItemKey(key);
        item.setCustomName(key != null || request.customName() == null
                ? null : request.customName().trim());
        item.setCustomNotes(request.customNotes() == null || request.customNotes().isBlank()
                ? null : request.customNotes().trim());
        int amount = request.qty() == null ? 1 : request.qty();
        item.setQty(amount);
        item.setQtyRemaining(amount);
        LootLines.applyAttributes(item, request.category(), request.valueGp(),
                request.weight(), request.damage(), request.armorClass());
        lootItemRepository.save(item);

        bump(session);
        return buildLootView(pool);
    }

    /**
     * DM edits a line. A quantity change shifts {@code qtyRemaining} by the same
     * delta (clamped at 0), so raising a partially-claimed stack adds new
     * claimable copies and lowering it takes from what's left.
     */
    @Transactional
    public LootView updateItem(Long sessionId, Long lootItemId, Integer qty,
                               String customName, String customNotes, UUID dmUserId) {
        CombatSession session = activeSessionForDm(sessionId, dmUserId);
        SessionLoot pool = requireLoot(sessionId);
        SessionLootItem item = requirePoolItem(pool, lootItemId);
        if (qty != null && qty < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "qty must be at least 1");
        }
        if (qty != null) {
            int delta = qty - item.getQty();
            item.setQty(qty);
            item.setQtyRemaining(Math.max(0, item.getQtyRemaining() + delta));
        }
        if (item.getCatalogItemKey() == null) { // custom line: name/notes are editable
            if (customName != null && !customName.isBlank()) item.setCustomName(customName.trim());
            item.setCustomNotes(customNotes == null || customNotes.isBlank() ? null : customNotes.trim());
        }
        lootItemRepository.save(item);

        bump(session);
        return buildLootView(pool);
    }

    /** DM removes a line from the pool. */
    @Transactional
    public LootView removeItem(Long sessionId, Long lootItemId, UUID dmUserId) {
        CombatSession session = activeSessionForDm(sessionId, dmUserId);
        SessionLoot pool = requireLoot(sessionId);
        lootItemRepository.delete(requirePoolItem(pool, lootItemId));

        bump(session);
        return buildLootView(pool);
    }

    /**
     * DM sets the coin pile, in gold. The remaining amount shifts by the same
     * delta as the total (clamped into [0, total]), preserving what has already
     * been claimed.
     */
    @Transactional
    public LootView setCoins(Long sessionId, Double coinGp, UUID dmUserId) {
        CombatSession session = activeSessionForDm(sessionId, dmUserId);
        SessionLoot pool = requireLoot(sessionId);
        if (coinGp == null || coinGp < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "coinGp must be zero or more");
        }
        long newTotal = Math.round(coinGp * 100);
        long delta = newTotal - pool.getCoinCpTotal();
        pool.setCoinCpTotal(newTotal);
        pool.setCoinCpRemaining(Math.min(newTotal, Math.max(0, pool.getCoinCpRemaining() + delta)));
        lootRepository.save(pool);

        bump(session);
        return buildLootView(pool);
    }

    /**
     * The pool as seen by the caller: the DM always sees it (draft or dropped);
     * a player sees it only once dropped and only if they own a seated
     * character. Returns null (→ 204) otherwise, so a draft's existence never
     * leaks (mirrors {@link ShopService#getActiveShopForUser}).
     */
    @Transactional(readOnly = true)
    public LootView getLootForUser(Long sessionId, UUID userId) {
        CombatSession session = findSession(sessionId);
        SessionLoot pool = lootRepository.findBySessionId(sessionId).orElse(null);
        if (pool == null) return null;

        boolean isDm = userId.equals(session.getDmUserId());
        if (!isDm && !(pool.isDropped() && isSeatedPlayer(sessionId, userId))) return null;

        return buildLootView(pool);
    }

    /**
     * A player claims {@code qty} of a line into their own seated character's
     * inventory. Atomic and first-come-first-served: the loot line is locked and
     * decremented (409 when someone got there first), then the pc row is locked
     * and the item appended — never a capacity check (overflow is allowed; the
     * sheet warns). Lock order (loot row, then pc row) is fixed across both
     * claim paths to prevent deadlock.
     */
    @Transactional
    public ClaimResult claimItem(Long sessionId, Long pcId, Long lootItemId, Integer qty, UUID userId) {
        if (qty == null || qty < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "qty must be at least 1");
        }
        CombatSession session = claimableSession(sessionId);
        SessionLoot pool = requireDroppedLoot(sessionId);
        requireOwnSeatedPc(sessionId, pcId, userId);

        SessionLootItem line = lootItemRepository.findByIdForUpdate(lootItemId)
                .filter(i -> pool.getId().equals(i.getSessionLootId()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Loot item not found with id " + lootItemId));
        if (line.getQtyRemaining() < qty) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    line.getQtyRemaining() == 0 ? "Already claimed" : "Only " + line.getQtyRemaining() + " left");
        }
        line.setQtyRemaining(line.getQtyRemaining() - qty);
        lootItemRepository.save(line);

        PC pc = lockOwnPc(pcId, userId);
        List<Map<String, Object>> inventory = json.parse(pc.getInventory());
        String itemName;
        if (line.getCatalogItemKey() != null) {
            SrdItem item = srdItemRepository.findByItemKey(line.getCatalogItemKey())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.CONFLICT, "Item no longer exists in the catalog"));
            // No unitCostCp — nothing was paid; a catalog match still makes it sellable.
            InventoryEntries.addCatalogItem(inventory, item, qty, null, json);
            itemName = item.getName();
        } else {
            // The pool line carries the authored attributes (category, value,
            // weight, damage, armor class) — stamped into the inventory entry so
            // a claimed custom item is as richly stat'd as a DM-granted one.
            inventory.add(InventoryEntries.newCustomEntry(line.getCustomName(), line.getCategory(),
                    qty, line.getUnitCostCp(), line.getWeight(), line.getDamage(),
                    line.getArmorClass(), line.getCustomNotes()));
            itemName = line.getCustomName();
        }
        pc.setInventory(json.write(inventory));
        pcRepository.save(pc);

        String qtySuffix = qty > 1 ? " ×" + qty : "";
        activityLogService.log(pcId, PcActivityType.LOOT, "Looted " + itemName + qtySuffix, userId);

        bump(session);
        return new ClaimResult(coinsOf(pc), inventory, buildLootView(pool));
    }

    /**
     * A player takes coins from the pile — any amount up to what remains, no
     * auto-split. Same atomicity and lock order as {@link #claimItem}.
     */
    @Transactional
    public ClaimResult claimCoins(Long sessionId, Long pcId, Map<String, Integer> coins, UUID userId) {
        long amountCp = CoinPurse.toCopper(coins);
        if (amountCp < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be at least 1 cp");
        }
        CombatSession session = claimableSession(sessionId);
        SessionLoot preLock = requireDroppedLoot(sessionId);
        requireOwnSeatedPc(sessionId, pcId, userId);

        SessionLoot pool = lootRepository.findByIdForUpdate(preLock.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "The loot is gone"));
        if (pool.getCoinCpRemaining() < amountCp) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only " + CoinPurse.format(pool.getCoinCpRemaining()) + " left in the pile");
        }
        pool.setCoinCpRemaining(pool.getCoinCpRemaining() - amountCp);
        lootRepository.save(pool);

        PC pc = lockOwnPc(pcId, userId);
        Map<String, Integer> newCoins = CoinPurse.add(json.parseObject(pc.getCoins()), amountCp);
        pc.setCoins(json.writeObject(newCoins));
        pcRepository.save(pc);

        activityLogService.log(pcId, PcActivityType.LOOT,
                "Looted " + CoinPurse.format(amountCp), userId);

        bump(session);
        return new ClaimResult(newCoins, json.parse(pc.getInventory()), buildLootView(pool));
    }

    // --- internals -----------------------------------------------------------

    private SessionLootItem copyLine(Long poolId, CuratedLootItem line) {
        SessionLootItem copy = new SessionLootItem();
        copy.setSessionLootId(poolId);
        copy.setCatalogItemKey(line.getCatalogItemKey());
        copy.setCustomName(line.getCustomName());
        copy.setCustomNotes(line.getCustomNotes());
        copy.setQty(line.getQty());
        copy.setQtyRemaining(line.getQty());
        LootLines.copyAttributes(line, copy);
        return copy;
    }

    private LootView buildLootView(SessionLoot pool) {
        List<SessionLootItem> lines = lootItemRepository.findBySessionLootIdOrderByIdAsc(pool.getId());
        List<String> keys = lines.stream()
                .map(SessionLootItem::getCatalogItemKey)
                .filter(Objects::nonNull)
                .toList();
        Map<String, SrdItem> catalog = keys.isEmpty() ? Map.of() :
                srdItemRepository.findByItemKeyIn(keys).stream()
                        .collect(Collectors.toMap(SrdItem::getItemKey, Function.identity()));

        List<LootItemView> items = lines.stream()
                .map(line -> {
                    if (line.getCatalogItemKey() == null) {
                        return new LootItemView(line.getId(), null, line.getCustomName(), true,
                                line.getCustomNotes(), line.getCategory(), line.getUnitCostCp(),
                                line.getWeight(), line.getDamage(), line.getArmorClass(),
                                line.getQty(), line.getQtyRemaining());
                    }
                    SrdItem c = catalog.get(line.getCatalogItemKey());
                    if (c == null) return null; // catalog item vanished — skip defensively
                    return new LootItemView(line.getId(), c.getItemKey(), c.getName(), false,
                            null, null, null, null, null, null,
                            line.getQty(), line.getQtyRemaining());
                })
                .filter(Objects::nonNull)
                .toList();

        return new LootView(pool.getId(), pool.getSessionId(), pool.getName(), pool.isDropped(),
                pool.getCoinCpTotal(), pool.getCoinCpRemaining(), items);
    }

    private void clearExistingLoot(Long sessionId) {
        lootRepository.findBySessionId(sessionId).ifPresent(existing -> {
            lootItemRepository.deleteBySessionLootId(existing.getId());
            lootRepository.delete(existing);
            lootRepository.flush(); // free the one-per-session unique index before re-inserting
        });
    }

    private CombatSession claimableSession(Long sessionId) {
        CombatSession session = findSession(sessionId);
        if (session.getStatus() == SessionStatus.ENDED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session has ended");
        }
        return session;
    }

    private SessionLoot requireDroppedLoot(Long sessionId) {
        SessionLoot pool = requireLoot(sessionId);
        if (!pool.isDropped()) {
            // A draft is invisible to players; 404 keeps its existence hidden.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No loot in this session");
        }
        return pool;
    }

    /** The claimer must own a seated character with this pcId in the session. */
    private void requireOwnSeatedPc(Long sessionId, Long pcId, UUID userId) {
        SessionParticipant participant = participantRepository.findBySessionIdAndPcId(sessionId, pcId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Character is not in this session"));
        if (!userId.equals(participant.getOwnerUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
    }

    private boolean isSeatedPlayer(Long sessionId, UUID userId) {
        return participantRepository.findBySessionIdOrderByOrderIndexAsc(sessionId).stream()
                .anyMatch(p -> p.getPcId() != null && userId.equals(p.getOwnerUserId()));
    }

    private PC lockOwnPc(Long pcId, UUID userId) {
        PC pc = pcRepository.findByIdForUpdate(pcId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "PC not found with id " + pcId));
        if (!userId.equals(pc.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        return pc;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<String, Integer> coinsOf(PC pc) {
        // The purse column already holds integer denominations; reuse it as-is
        // for the (unchanged-coins) item-claim result.
        return (Map) json.parseObject(pc.getCoins());
    }

    private SessionLoot requireLoot(Long sessionId) {
        return lootRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No loot in this session"));
    }

    private SessionLootItem requirePoolItem(SessionLoot pool, Long lootItemId) {
        SessionLootItem item = lootItemRepository.findById(lootItemId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Loot item not found with id " + lootItemId));
        if (!pool.getId().equals(item.getSessionLootId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Loot item not found in this pool");
        }
        return item;
    }

    private CombatSession activeSessionForDm(Long sessionId, UUID dmUserId) {
        CombatSession session = findSession(sessionId);
        assertDm(session, dmUserId);
        if (session.getStatus() == SessionStatus.ENDED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session has ended");
        }
        return session;
    }

    private CombatSession findSession(Long sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Session not found with id " + sessionId));
    }

    private void assertDm(CombatSession session, UUID dmUserId) {
        if (!dmUserId.equals(session.getDmUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
    }

    private void bump(CombatSession session) {
        session.bumpVersion();
        sessionRepository.save(session);
    }

}
