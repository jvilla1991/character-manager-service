package com.moo.charactermanagerservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moo.charactermanagerservice.dto.CatalogItemView;
import com.moo.charactermanagerservice.dto.ShopItemView;
import com.moo.charactermanagerservice.dto.ShopView;
import com.moo.charactermanagerservice.dto.PurchaseResult;
import com.moo.charactermanagerservice.dto.SellResult;
import com.moo.charactermanagerservice.models.CombatSession;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.models.PcActivityType;
import com.moo.charactermanagerservice.models.SessionParticipant;
import com.moo.charactermanagerservice.models.SessionShop;
import com.moo.charactermanagerservice.models.SessionShopAttendee;
import com.moo.charactermanagerservice.models.SessionStatus;
import com.moo.charactermanagerservice.models.Shop;
import com.moo.charactermanagerservice.models.ShopItem;
import com.moo.charactermanagerservice.models.SrdItem;
import com.moo.charactermanagerservice.repositories.CombatSessionRepository;
import com.moo.charactermanagerservice.repositories.PCRepository;
import com.moo.charactermanagerservice.repositories.SessionParticipantRepository;
import com.moo.charactermanagerservice.repositories.SessionShopAttendeeRepository;
import com.moo.charactermanagerservice.repositories.SessionShopRepository;
import com.moo.charactermanagerservice.repositories.ShopItemRepository;
import com.moo.charactermanagerservice.repositories.ShopRepository;
import com.moo.charactermanagerservice.repositories.SrdItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Shopping feature, Phase 1. A DM activates one shop per live session and
 * targets characters from the session roster; targeted players browse and buy.
 * Mirrors {@link SessionService}'s ownership model (DM-owned session, 403
 * otherwise) and its canonical-vs-transient rule: shop attendance is transient
 * session state, but a purchase writes through to the canonical {@code pc} row
 * (coins deducted, inventory appended), so it persists after the session ends.
 *
 * <p>Standard shops (Phase 1) derive their stock from {@code srd_item} filtered
 * by category, at catalog price, with unlimited quantity — so there is no shared
 * contested stock and no stock concurrency here. The only race guarded against
 * is a player double-submitting against their own purse, handled by a pessimistic
 * lock on the pc row in {@link #purchase}.
 */
@Service
public class ShopService {

    /** Catalog slices a shop may sell. Phase 1 seeds WEAPON; the rest arrive with their slices. */
    private static final Set<String> SUPPORTED_CATEGORIES =
            Set.of("WEAPON", "ARMOR", "MATERIAL_COMPONENT", "GEAR", "TRANSPORT");

    private final CombatSessionRepository sessionRepository;
    private final SessionShopRepository shopRepository;
    private final SessionShopAttendeeRepository attendeeRepository;
    private final SessionParticipantRepository participantRepository;
    private final SrdItemRepository srdItemRepository;
    private final ShopRepository curatedShopRepository;
    private final ShopItemRepository curatedItemRepository;
    private final PCRepository pcRepository;
    private final PcActivityLogService activityLogService;
    private final PcJsonColumns json;

    @Autowired
    public ShopService(CombatSessionRepository sessionRepository,
                       SessionShopRepository shopRepository,
                       SessionShopAttendeeRepository attendeeRepository,
                       SessionParticipantRepository participantRepository,
                       SrdItemRepository srdItemRepository,
                       ShopRepository curatedShopRepository,
                       ShopItemRepository curatedItemRepository,
                       PCRepository pcRepository,
                       PcActivityLogService activityLogService,
                       ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.shopRepository = shopRepository;
        this.attendeeRepository = attendeeRepository;
        this.participantRepository = participantRepository;
        this.srdItemRepository = srdItemRepository;
        this.curatedShopRepository = curatedShopRepository;
        this.curatedItemRepository = curatedItemRepository;
        this.pcRepository = pcRepository;
        this.activityLogService = activityLogService;
        this.json = new PcJsonColumns(objectMapper);
    }

    /**
     * DM activates a shop in the session, replacing any shop already open (one
     * per session). The chosen characters must be seated in the session.
     */
    @Transactional
    public ShopView openShop(Long sessionId, String category, String settlement,
                             List<Long> pcIds, UUID dmUserId) {
        CombatSession session = activeSessionForDm(sessionId, dmUserId);
        String normalized = normalizeCategory(category);

        clearExistingShop(sessionId);

        SessionShop shop = new SessionShop();
        shop.setSessionId(sessionId);
        shop.setCategory(normalized);
        shop.setSettlement(settlement);
        SessionShop saved = shopRepository.save(shop);

        addAttendees(saved, sessionId, pcIds);

        bump(session);
        return buildShopView(saved);
    }

    /**
     * DM activates a pre-built curated shop in the session (replacing any open
     * shop). The curated shop must belong to this session's campaign and be owned
     * by the DM. Its own settlement label is used unless {@code settlement} overrides.
     */
    @Transactional
    public ShopView openCuratedShop(Long sessionId, Long curatedShopId, String settlement,
                                    List<Long> pcIds, UUID dmUserId) {
        CombatSession session = activeSessionForDm(sessionId, dmUserId);
        Shop curated = curatedShopRepository.findById(curatedShopId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Curated shop not found with id " + curatedShopId));
        if (!dmUserId.equals(curated.getDmUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        if (!session.getCampaignId().equals(curated.getCampaignId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Curated shop belongs to a different campaign");
        }

        clearExistingShop(sessionId);

        SessionShop shop = new SessionShop();
        shop.setSessionId(sessionId);
        shop.setShopId(curatedShopId);          // curated: category stays null
        shop.setSettlement(settlement != null ? settlement : curated.getSettlement());
        SessionShop saved = shopRepository.save(shop);

        addAttendees(saved, sessionId, pcIds);

        bump(session);
        return buildShopView(saved);
    }

    /** DM replaces the set of characters at the active shop. */
    @Transactional
    public ShopView setAttendees(Long sessionId, List<Long> pcIds, UUID dmUserId) {
        CombatSession session = activeSessionForDm(sessionId, dmUserId);
        SessionShop shop = requireShop(sessionId);

        attendeeRepository.deleteBySessionShopId(shop.getId());
        addAttendees(shop, sessionId, pcIds);

        bump(session);
        return buildShopView(shop);
    }

    /** DM closes the shop; attendance is cleared (no long-term association). */
    @Transactional
    public void closeShop(Long sessionId, UUID dmUserId) {
        CombatSession session = findSession(sessionId);
        assertDm(session, dmUserId);

        shopRepository.findBySessionId(sessionId).ifPresent(shop -> {
            attendeeRepository.deleteBySessionShopId(shop.getId());
            shopRepository.delete(shop);
        });
        bump(session);
    }

    /**
     * The active shop as seen by the caller — the DM or a targeted attendee.
     * Returns null (→ 204) when there is no shop or the caller isn't targeted,
     * so non-attendees simply see nothing rather than learning the shop exists.
     */
    @Transactional(readOnly = true)
    public ShopView getActiveShopForUser(Long sessionId, UUID userId) {
        CombatSession session = findSession(sessionId);
        SessionShop shop = shopRepository.findBySessionId(sessionId).orElse(null);
        if (shop == null) return null;

        boolean isDm = userId.equals(session.getDmUserId());
        boolean isAttendee = attendeeRepository.findBySessionShopId(shop.getId()).stream()
                .anyMatch(a -> userId.equals(a.getOwnerUserId()));
        if (!isDm && !isAttendee) return null;

        return buildShopView(shop);
    }

    /**
     * Buy {@code qty} of an item for one of the caller's characters that is at the
     * shop. Atomic: coins deducted (with change-making) and inventory appended on
     * the pc row, under a pessimistic lock so concurrent self-purchases can't lose
     * an update. Standard-shop stock is unlimited in Phase 1, so nothing is
     * decremented here.
     */
    @Transactional
    public PurchaseResult purchase(Long sessionId, Long pcId, String itemKey, Integer qty, UUID userId) {
        if (qty == null || qty < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "qty must be at least 1");
        }
        CombatSession session = findSession(sessionId);
        if (session.getStatus() == SessionStatus.ENDED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session has ended");
        }
        SessionShop shop = requireShop(sessionId);

        // The character must be at this shop (session-exclusive access at the write path).
        attendeeRepository.findBySessionShopIdAndPcId(shop.getId(), pcId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Character is not at this shop"));

        SrdItem item = srdItemRepository.findByItemKey(itemKey)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Item not found: " + itemKey));

        // Resolve the price the buyer pays: catalog price for a standard shop, or
        // the curated line's effective price (override ?? catalog) for a curated one.
        long unitCostCp;
        if (shop.getShopId() == null) {
            if (!item.getCategory().equals(shop.getCategory())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Item is not sold at this shop");
            }
            unitCostCp = item.getCostCp();
        } else {
            ShopItem line = curatedItemRepository
                    .findByShopIdAndCatalogItemKey(shop.getShopId(), itemKey)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Item is not sold at this shop"));
            unitCostCp = line.getPriceCp() != null ? line.getPriceCp() : item.getCostCp();
        }
        long totalCostCp = unitCostCp * qty;

        // Lock the pc row for the read-modify-write of coins + inventory.
        PC pc = pcRepository.findByIdForUpdate(pcId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "PC not found with id " + pcId));
        if (!userId.equals(pc.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        Map<String, Object> coins = json.parseObject(pc.getCoins());
        Map<String, Integer> newCoins = CoinPurse.deduct(coins, totalCostCp);

        List<Map<String, Object>> inventory = json.parse(pc.getInventory());
        // Rations only FIT inside ration boxes (5 servings per box), but buying
        // past capacity is allowed: the overflow rides loose and fills inventory
        // slots (0.2 bulk each — the frontend's suppliesSlots math charges for
        // it) until another box shelters it. Normalize legacy supply lines so
        // the old model's implied free box still counts toward capacity.
        if ("rations".equals(itemKey)) {
            SurvivalSupplies.normalize(inventory);
        }
        InventoryEntries.addCatalogItem(inventory, item, qty, unitCostCp, json);

        pc.setCoins(json.writeObject(newCoins));
        pc.setInventory(json.write(inventory));
        pcRepository.save(pc);

        String qtySuffix = qty > 1 ? " ×" + qty : "";
        activityLogService.log(pcId, PcActivityType.PURCHASE,
                "Bought " + item.getName() + qtySuffix + " for " + CoinPurse.format(totalCostCp), userId);

        return new PurchaseResult(newCoins, inventory, totalCostCp);
    }

    /**
     * Sell the entire stack at inventory position {@code index} back to the
     * active shop for half its value. Atomic: item removed and coins credited on
     * the pc row, under the same pessimistic lock as {@link #purchase}.
     *
     * <p>Sell price prefers the live catalog price (via the item's catalogKey) so
     * a player can't inflate what they claim to have paid; items with no catalog
     * match (ad-hoc/DM-granted items) fall back to the price snapshot already on
     * the line ({@code unitCostCp}). Standard shops only buy back items in their
     * open category (mirrors what they sell); curated shops buy anything, since
     * they have no category of their own. Dropped items and equipped items are
     * otherwise ordinary — dropped items can't be sold (use Discard instead);
     * equipped items sell fine and are simply removed, no explicit unequip step
     * needed.
     */
    @Transactional
    public SellResult sell(Long sessionId, Long pcId, Integer index, UUID userId) {
        CombatSession session = findSession(sessionId);
        if (session.getStatus() == SessionStatus.ENDED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session has ended");
        }
        SessionShop shop = requireShop(sessionId);

        attendeeRepository.findBySessionShopIdAndPcId(shop.getId(), pcId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Character is not at this shop"));

        PC pc = pcRepository.findByIdForUpdate(pcId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "PC not found with id " + pcId));
        if (!userId.equals(pc.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        List<Map<String, Object>> inventory = json.parse(pc.getInventory());
        if (index == null || index < 0 || index >= inventory.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid item index");
        }
        Map<String, Object> entry = inventory.get(index);

        if ("dropped".equals(entry.get("status"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dropped items can't be sold");
        }
        if (shop.getShopId() == null
                && !InventoryEntries.categoryLabel(shop.getCategory()).equals(entry.get("category"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This shop doesn't buy that kind of item");
        }

        Long baseUnitCp = null;
        Object catalogKey = entry.get("catalogKey");
        if (catalogKey instanceof String key) {
            baseUnitCp = srdItemRepository.findByItemKey(key).map(SrdItem::getCostCp).orElse(null);
        }
        if (baseUnitCp == null && entry.get("unitCostCp") instanceof Number n) {
            baseUnitCp = n.longValue();
        }
        if (baseUnitCp == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Item has no resolvable price and can't be sold");
        }

        int qty = entry.get("qty") instanceof Number n ? n.intValue() : 1;
        long totalGainCp = (baseUnitCp * qty) / 2;
        // Capture the item's name before it's removed — the log message needs it
        // after the entry is gone from the inventory list.
        String itemName = entry.get("name") instanceof String s ? s : "item";

        inventory.remove(index.intValue());
        Map<String, Object> coins = json.parseObject(pc.getCoins());
        Map<String, Integer> newCoins = CoinPurse.add(coins, totalGainCp);

        pc.setCoins(json.writeObject(newCoins));
        pc.setInventory(json.write(inventory));
        pcRepository.save(pc);

        activityLogService.log(pcId, PcActivityType.SALE,
                "Sold " + itemName + " for " + CoinPurse.format(totalGainCp), userId);

        return new SellResult(newCoins, inventory, totalGainCp);
    }

    /**
     * Raw catalog browse for the DM-grant picker: every item in a category, at
     * catalog price, with the effective bulk rating — the same value
     * {@link #newInventoryEntry} stamps at purchase, so the frontend can
     * denormalize a granted item without re-deriving weight bands. Read-only
     * reference data; any authenticated user may read it (players already see
     * the same items through shops).
     */
    @Transactional(readOnly = true)
    public List<CatalogItemView> catalog(String category) {
        String normalized = normalizeCategory(category);
        return srdItemRepository.findByCategoryOrderByNameAsc(normalized).stream()
                .map(i -> new CatalogItemView(
                        i.getItemKey(), i.getName(), i.getCategory(), i.getCostCp(),
                        i.getWeight(), BulkRules.bulkFor(i.getBulk(), i.getWeight()),
                        json.parseObject(i.getDetails())))
                .toList();
    }

    // --- internals -----------------------------------------------------------

    private List<ShopItemView> catalogItems(String category) {
        return srdItemRepository.findByCategoryOrderByNameAsc(category).stream()
                .map(i -> new ShopItemView(
                        i.getItemKey(), i.getName(), i.getCategory(), i.getCostCp(),
                        i.getWeight(), json.parseObject(i.getDetails()),
                        null /* unlimited stock in Phase 1 */))
                .toList();
    }

    private ShopView buildShopView(SessionShop shop) {
        List<Long> attendeePcIds = attendeeRepository.findBySessionShopId(shop.getId()).stream()
                .map(SessionShopAttendee::getPcId)
                .toList();

        if (shop.getShopId() != null) {
            // Curated: items + name come from the persistent curated shop.
            Shop curated = curatedShopRepository.findById(shop.getShopId()).orElse(null);
            String shopName = curated == null ? null : curated.getName();
            return new ShopView(
                    shop.getId(), shop.getSessionId(), null, shop.getSettlement(),
                    attendeePcIds, curatedItems(shop.getShopId()), shop.getShopId(), shopName);
        }

        // Standard: derived from the catalog by category, unlimited.
        return new ShopView(
                shop.getId(), shop.getSessionId(), shop.getCategory(), shop.getSettlement(),
                attendeePcIds, catalogItems(shop.getCategory()), null, null);
    }

    /** Resolve a curated shop's lines against the catalog (effective price = override ?? catalog). */
    private List<ShopItemView> curatedItems(Long curatedShopId) {
        List<ShopItem> lines = curatedItemRepository.findByShopId(curatedShopId);
        if (lines.isEmpty()) return List.of();
        Map<String, SrdItem> catalog = srdItemRepository.findByItemKeyIn(
                        lines.stream().map(ShopItem::getCatalogItemKey).toList()).stream()
                .collect(Collectors.toMap(SrdItem::getItemKey, Function.identity()));

        return lines.stream()
                .map(line -> {
                    SrdItem c = catalog.get(line.getCatalogItemKey());
                    if (c == null) return null; // catalog item vanished — skip
                    long cost = line.getPriceCp() != null ? line.getPriceCp() : c.getCostCp();
                    return new ShopItemView(c.getItemKey(), c.getName(), c.getCategory(), cost,
                            c.getWeight(), json.parseObject(c.getDetails()), null);
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(ShopItemView::name))
                .toList();
    }

    /** Seat each chosen PC at the shop, verifying it is in the session roster. */
    private void addAttendees(SessionShop shop, Long sessionId, List<Long> pcIds) {
        if (pcIds == null) return;
        List<Long> distinct = pcIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        List<SessionShopAttendee> rows = new ArrayList<>();
        for (Long pcId : distinct) {
            SessionParticipant participant = participantRepository
                    .findBySessionIdAndPcId(sessionId, pcId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Character " + pcId + " is not in this session"));
            SessionShopAttendee attendee = new SessionShopAttendee();
            attendee.setSessionShopId(shop.getId());
            attendee.setPcId(pcId);
            attendee.setOwnerUserId(participant.getOwnerUserId());
            rows.add(attendee);
        }
        attendeeRepository.saveAll(rows);
    }

    private void clearExistingShop(Long sessionId) {
        shopRepository.findBySessionId(sessionId).ifPresent(existing -> {
            attendeeRepository.deleteBySessionShopId(existing.getId());
            shopRepository.delete(existing);
            shopRepository.flush(); // free the one-per-session unique index before re-inserting
        });
    }

    private CombatSession activeSessionForDm(Long sessionId, UUID dmUserId) {
        CombatSession session = findSession(sessionId);
        assertDm(session, dmUserId);
        if (session.getStatus() == SessionStatus.ENDED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session has ended");
        }
        return session;
    }

    private SessionShop requireShop(Long sessionId) {
        return shopRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No active shop in this session"));
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

    private String normalizeCategory(String category) {
        String normalized = category == null ? "" : category.trim().toUpperCase();
        if (!SUPPORTED_CATEGORIES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported shop category: " + category);
        }
        return normalized;
    }
}
