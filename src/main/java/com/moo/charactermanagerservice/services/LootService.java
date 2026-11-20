package com.moo.charactermanagerservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moo.charactermanagerservice.dto.AddLootItemRequest;
import com.moo.charactermanagerservice.dto.ImportLootRequest;
import com.moo.charactermanagerservice.dto.LootItemView;
import com.moo.charactermanagerservice.dto.LootView;
import com.moo.charactermanagerservice.models.CombatSession;
import com.moo.charactermanagerservice.models.CuratedLoot;
import com.moo.charactermanagerservice.models.CuratedLootItem;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.models.PcActivityType;
import com.moo.charactermanagerservice.models.SessionLoot;
import com.moo.charactermanagerservice.models.SessionLootAllocation;
import com.moo.charactermanagerservice.models.SessionLootBallot;
import com.moo.charactermanagerservice.models.SessionLootItem;
import com.moo.charactermanagerservice.models.SessionLootResult;
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
import com.moo.charactermanagerservice.repositories.SessionLootRepository;
import com.moo.charactermanagerservice.repositories.SessionLootResultRepository;
import com.moo.charactermanagerservice.repositories.SessionParticipantRepository;
import com.moo.charactermanagerservice.repositories.SrdItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Post-combat loot: the transient pool a DM opens in a live session, seeded by
 * copying a curated loot list's prepped lines (or built from scratch), edited as
 * an invisible draft, then dropped for the party to split.
 *
 * <p><b>Party split.</b> Dropping moves the pool to SPLITTING: any seated player
 * may move items and coins between the pile and any seated character. Once the
 * pile is empty a player locks it for a vote (VOTING): every seated character
 * gets a ballot — ✓ or ✗, and a ✗ also answers "PvP?". Resolution:
 * <ul>
 *   <li>any ✗ + PvP cancels the loot at once: the pool goes back to the DM as
 *       an undropped draft (for re-dropping to the winners);</li>
 *   <li>otherwise, once every ballot is settled, a majority of ✓ (more than
 *       half the ballots) distributes the split into each character's sheet and
 *       deletes the pool;</li>
 *   <li>without a majority the split re-opens (SPLITTING) with every share
 *       kept where it was.</li>
 * </ul>
 * The DM holds no ballot but may force the split through or cancel it. Every
 * outcome is recorded as the session's {@link SessionLootResult} so clients
 * can announce it after the pool is gone.
 *
 * <p><b>Locking.</b> Every split mutation locks the pool row first; the
 * distribution then locks pc rows in ascending id order — a fixed order, so
 * concurrent writers can't deadlock. Every mutation bumps the session version
 * so all pollers re-fetch. Unclaimed loot is deleted when the session ends
 * (see {@code SessionService.endSession}).
 */
@Service
public class LootService {

    static final String SPLITTING = "SPLITTING";
    static final String VOTING = "VOTING";

    private final CombatSessionRepository sessionRepository;
    private final SessionLootRepository lootRepository;
    private final SessionLootItemRepository lootItemRepository;
    private final SessionLootAllocationRepository allocationRepository;
    private final SessionLootBallotRepository ballotRepository;
    private final SessionLootResultRepository resultRepository;
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
                       SessionLootAllocationRepository allocationRepository,
                       SessionLootBallotRepository ballotRepository,
                       SessionLootResultRepository resultRepository,
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
        this.allocationRepository = allocationRepository;
        this.ballotRepository = ballotRepository;
        this.resultRepository = resultRepository;
        this.participantRepository = participantRepository;
        this.curatedLootRepository = curatedLootRepository;
        this.curatedLootItemRepository = curatedLootItemRepository;
        this.srdItemRepository = srdItemRepository;
        this.pcRepository = pcRepository;
        this.activityLogService = activityLogService;
        this.json = new PcJsonColumns(objectMapper);
    }

    // --- DM pool management ----------------------------------------------------

    /**
     * DM opens a loot pool as an invisible draft, replacing any existing pool
     * (one per session). A {@code lootId} seeds it by COPYING that curated loot
     * list's lines and coin pile — the curated prep is never mutated, so a list
     * can be dropped any number of times.
     */
    @Transactional
    public LootView openLoot(Long sessionId, Long lootId, String name, UUID dmUserId) {
        return openLoot(sessionId, lootId, name, null, dmUserId);
    }

    /**
     * {@link #openLoot(Long, Long, String, UUID)}, optionally seeded from pasted
     * JSON instead of a curated list: {@code contents} carries the lines and
     * the coin pile in the curated-loot import shape. All-or-nothing — the
     * paste is validated in full (unknown catalog keys listed) before the pool
     * it replaces is touched, so a bad paste costs the DM nothing. A pool is
     * seeded from a list or from a paste, never both.
     */
    @Transactional
    public LootView openLoot(Long sessionId, Long lootId, String name, ImportLootRequest contents,
                             UUID dmUserId) {
        CombatSession session = activeSessionForDm(sessionId, dmUserId);
        if (lootId != null && contents != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Seed the loot from a loot list or from pasted contents, not both");
        }
        List<ImportLootRequest.Item> pasted = List.of();
        if (contents != null) {
            List<String> keys = LootLines.validateImport(contents);
            Set<String> known = keys.isEmpty() ? Set.of()
                    : srdItemRepository.findByItemKeyIn(keys).stream()
                            .map(SrdItem::getItemKey).collect(Collectors.toSet());
            List<String> unknown = keys.stream().filter(k -> !known.contains(k)).toList();
            if (!unknown.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unknown catalog keys: " + String.join(", ", unknown));
            }
            pasted = contents.items() == null ? List.of() : contents.items();
        }

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
        if (contents != null && contents.coinGp() != null) {
            long coinCp = Math.round(contents.coinGp() * 100);
            pool.setCoinCpTotal(coinCp);
            pool.setCoinCpRemaining(coinCp);
        }
        SessionLoot saved = lootRepository.save(pool);

        if (lootId != null) {
            List<SessionLootItem> copies = curatedLootItemRepository
                    .findByLootIdOrderByIdAsc(lootId).stream()
                    .map(line -> copyLine(saved.getId(), line))
                    .toList();
            lootItemRepository.saveAll(copies);
        }
        if (!pasted.isEmpty()) {
            lootItemRepository.saveAll(pasted.stream()
                    .map(line -> pastedLine(saved.getId(), line))
                    .toList());
        }

        bump(session);
        return buildLootView(saved, dmUserId);
    }

    /** DM drops the draft — the party can now see it and start splitting. */
    @Transactional
    public LootView dropLoot(Long sessionId, UUID dmUserId) {
        CombatSession session = activeSessionForDm(sessionId, dmUserId);
        SessionLoot pool = requireLoot(sessionId);
        if (!pool.isDropped()) {
            pool.setDropped(true);
            pool.setSplitPhase(SPLITTING);
            lootRepository.save(pool);
        }

        bump(session);
        return buildLootView(pool, dmUserId);
    }

    /** DM discards the pool (shares and ballots cascade away with it). */
    @Transactional
    public void closeLoot(Long sessionId, UUID dmUserId) {
        CombatSession session = findSession(sessionId);
        assertDm(session, dmUserId);

        lootRepository.findBySessionId(sessionId).ifPresent(this::deletePool);
        bump(session);
    }

    /**
     * DM adds a line to the pool (draft or dropped — live edits are allowed). A
     * change to a pool the party is voting on re-opens the split: nobody votes
     * on a pool that changed under them.
     */
    @Transactional
    public LootView addItem(Long sessionId, AddLootItemRequest request, UUID dmUserId) {
        CombatSession session = activeSessionForDm(sessionId, dmUserId);
        SessionLoot pool = requireLoot(sessionId);
        LootLines.validate(request.catalogItemKey(), request.customName(), request.qty());
        String key = LootLines.normalizeKey(request.catalogItemKey());
        LootLines.validateAttributes(key != null, request.category(), request.valueGp(),
                request.weight(), request.bulk(), request.damage(), request.armorClass());
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
                request.weight(), request.bulk(), request.damage(), request.armorClass());
        lootItemRepository.save(item);
        reopenSplitIfVoting(pool);

        bump(session);
        return buildLootView(pool, dmUserId);
    }

    /**
     * DM edits a line. Lowering the quantity below what the party has already
     * shared out returns that line's shares to the pile.
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
        if (qty != null && qty != item.getQty()) {
            int allocated = allocationRepository.findBySessionLootIdOrderByIdAsc(pool.getId()).stream()
                    .filter(a -> lootItemId.equals(a.getLootItemId()))
                    .mapToInt(SessionLootAllocation::getQty)
                    .sum();
            if (qty < allocated) {
                allocationRepository.deleteBySessionLootIdAndLootItemId(pool.getId(), lootItemId);
            }
            item.setQty(qty);
            item.setQtyRemaining(qty);
            reopenSplitIfVoting(pool);
        }
        if (item.getCatalogItemKey() == null) { // custom line: name/notes are editable
            if (customName != null && !customName.isBlank()) item.setCustomName(customName.trim());
            item.setCustomNotes(customNotes == null || customNotes.isBlank() ? null : customNotes.trim());
        }
        lootItemRepository.save(item);

        bump(session);
        return buildLootView(pool, dmUserId);
    }

    /** DM removes a line from the pool; its shares go with it. */
    @Transactional
    public LootView removeItem(Long sessionId, Long lootItemId, UUID dmUserId) {
        CombatSession session = activeSessionForDm(sessionId, dmUserId);
        SessionLoot pool = requireLoot(sessionId);
        SessionLootItem item = requirePoolItem(pool, lootItemId);
        allocationRepository.deleteBySessionLootIdAndLootItemId(pool.getId(), lootItemId);
        lootItemRepository.delete(item);
        reopenSplitIfVoting(pool);

        bump(session);
        return buildLootView(pool, dmUserId);
    }

    /**
     * DM sets the coin pile, in gold. Shrinking it below what the party has
     * already shared out returns every coin share to the pile.
     */
    @Transactional
    public LootView setCoins(Long sessionId, Double coinGp, UUID dmUserId) {
        CombatSession session = activeSessionForDm(sessionId, dmUserId);
        SessionLoot pool = requireLoot(sessionId);
        if (coinGp == null || coinGp < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "coinGp must be zero or more");
        }
        long newTotal = Math.round(coinGp * 100);
        if (newTotal != pool.getCoinCpTotal()) {
            long shared = allocationRepository.findBySessionLootIdOrderByIdAsc(pool.getId()).stream()
                    .filter(a -> a.getLootItemId() == null)
                    .mapToLong(SessionLootAllocation::getCoinCp)
                    .sum();
            if (newTotal < shared) {
                allocationRepository.deleteBySessionLootIdAndLootItemIdIsNull(pool.getId());
            }
            pool.setCoinCpTotal(newTotal);
            pool.setCoinCpRemaining(newTotal);
            reopenSplitIfVoting(pool);
            lootRepository.save(pool);
        }

        bump(session);
        return buildLootView(pool, dmUserId);
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

        return buildLootView(pool, userId);
    }

    // --- party split ----------------------------------------------------------

    /**
     * A seated player moves {@code qty} of a line between the pile and a
     * character, or between two characters. Anyone at the table may move
     * anything — the split is a shared negotiation and the vote is the check.
     */
    @Transactional
    public LootView assignItem(Long sessionId, Long lootItemId, Long fromParticipantId,
                               Long toParticipantId, Integer qty, UUID userId) {
        if (qty == null || qty < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "qty must be at least 1");
        }
        if (Objects.equals(fromParticipantId, toParticipantId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nothing to move");
        }
        CombatSession session = liveSession(sessionId);
        SessionLoot pool = lockSplittingPool(sessionId, userId);
        Map<Long, SessionParticipant> seated = seatedCharacters(sessionId);
        requireRecipient(seated, fromParticipantId);
        requireRecipient(seated, toParticipantId);
        SessionLootItem line = requirePoolItem(pool, lootItemId);

        List<SessionLootAllocation> lineShares = allocationRepository
                .findBySessionLootIdOrderByIdAsc(pool.getId()).stream()
                .filter(a -> lootItemId.equals(a.getLootItemId()))
                .toList();
        Map<Long, SessionLootAllocation> byHolder = lineShares.stream()
                .collect(Collectors.toMap(SessionLootAllocation::getParticipantId, Function.identity()));

        int available;
        if (fromParticipantId == null) {
            available = line.getQty() - lineShares.stream().mapToInt(SessionLootAllocation::getQty).sum();
        } else {
            SessionLootAllocation from = byHolder.get(fromParticipantId);
            available = from == null ? 0 : from.getQty();
        }
        if (available < qty) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    available == 0 ? "That item already moved" : "Only " + available + " to move");
        }

        if (fromParticipantId != null) {
            SessionLootAllocation from = byHolder.get(fromParticipantId);
            if (from.getQty() == qty) {
                allocationRepository.delete(from);
            } else {
                from.setQty(from.getQty() - qty);
                allocationRepository.save(from);
            }
        }
        if (toParticipantId != null) {
            SessionLootAllocation to = byHolder.get(toParticipantId);
            if (to == null) {
                to = new SessionLootAllocation();
                to.setSessionLootId(pool.getId());
                to.setParticipantId(toParticipantId);
                to.setLootItemId(lootItemId);
            }
            to.setQty(to.getQty() + qty);
            allocationRepository.save(to);
        }

        bump(session);
        return buildLootView(pool, userId);
    }

    /** A seated player sets one character's share of the coin pile (0 clears it). */
    @Transactional
    public LootView setCoinShare(Long sessionId, Long participantId, Long coinCp, UUID userId) {
        if (participantId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "participantId is required");
        }
        if (coinCp == null || coinCp < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "coinCp must be zero or more");
        }
        CombatSession session = liveSession(sessionId);
        SessionLoot pool = lockSplittingPool(sessionId, userId);
        requireRecipient(seatedCharacters(sessionId), participantId);

        List<SessionLootAllocation> coinShares = coinSharesOf(pool);
        long othersCp = coinShares.stream()
                .filter(a -> !participantId.equals(a.getParticipantId()))
                .mapToLong(SessionLootAllocation::getCoinCp)
                .sum();
        if (othersCp + coinCp > pool.getCoinCpTotal()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only " + CoinPurse.format(pool.getCoinCpTotal() - othersCp) + " is left to share");
        }
        Optional<SessionLootAllocation> existing = coinShares.stream()
                .filter(a -> participantId.equals(a.getParticipantId()))
                .findFirst();
        if (coinCp == 0) {
            existing.ifPresent(allocationRepository::delete);
        } else {
            SessionLootAllocation share = existing.orElseGet(() -> {
                SessionLootAllocation a = new SessionLootAllocation();
                a.setSessionLootId(pool.getId());
                a.setParticipantId(participantId);
                return a;
            });
            share.setCoinCp(coinCp);
            allocationRepository.save(share);
        }

        bump(session);
        return buildLootView(pool, userId);
    }

    /**
     * Split the whole coin pile evenly across every seated character,
     * replacing any shares. Leftover copper goes one piece each to the first
     * characters in seat order.
     */
    @Transactional
    public LootView splitCoinsEvenly(Long sessionId, UUID userId) {
        CombatSession session = liveSession(sessionId);
        SessionLoot pool = lockSplittingPool(sessionId, userId);
        List<SessionParticipant> seated = new ArrayList<>(seatedCharacters(sessionId).values());

        allocationRepository.deleteBySessionLootIdAndLootItemIdIsNull(pool.getId());
        long total = pool.getCoinCpTotal();
        if (total > 0 && !seated.isEmpty()) {
            long each = total / seated.size();
            long leftover = total % seated.size();
            List<SessionLootAllocation> shares = new ArrayList<>();
            for (int i = 0; i < seated.size(); i++) {
                long cp = each + (i < leftover ? 1 : 0);
                if (cp == 0) continue;
                SessionLootAllocation share = new SessionLootAllocation();
                share.setSessionLootId(pool.getId());
                share.setParticipantId(seated.get(i).getId());
                share.setCoinCp(cp);
                shares.add(share);
            }
            allocationRepository.saveAll(shares);
        }

        bump(session);
        return buildLootView(pool, userId);
    }

    /**
     * A seated player locks the split for a vote. Everything must have an owner
     * first — an empty pile — so the party votes on the whole split. Every
     * seated character gets an unanswered ballot.
     */
    @Transactional
    public LootView proposeSplit(Long sessionId, UUID userId) {
        CombatSession session = liveSession(sessionId);
        SessionLoot pool = lockSplittingPool(sessionId, userId);

        List<SessionLootItem> lines = lootItemRepository.findBySessionLootIdOrderByIdAsc(pool.getId());
        if (lines.isEmpty() && pool.getCoinCpTotal() == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "There is nothing to split");
        }
        if (!fullyAllocated(pool, lines)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Everything must have an owner before the vote");
        }

        ballotRepository.deleteBySessionLootId(pool.getId());
        List<SessionLootBallot> ballots = seatedCharacters(sessionId).values().stream()
                .map(p -> {
                    SessionLootBallot b = new SessionLootBallot();
                    b.setSessionLootId(pool.getId());
                    b.setParticipantId(p.getId());
                    b.setDisplayName(p.getDisplayName());
                    return b;
                })
                .toList();
        ballotRepository.saveAll(ballots);
        pool.setSplitPhase(VOTING);
        lootRepository.save(pool);

        bump(session);
        return buildLootView(pool, userId);
    }

    /**
     * A player answers their ballot (re-casting overwrites until the vote
     * resolves). Resolution is eager: a ✗ + PvP cancels at once; otherwise the
     * last settled ballot decides between distributing and re-opening the
     * split. Returns the caller's view of the pool afterwards — null (→ 204)
     * when it was distributed or went back to a hidden draft.
     */
    @Transactional
    public LootView castVote(Long sessionId, Boolean vote, Boolean pvp, UUID userId) {
        CombatSession session = liveSession(sessionId);
        SessionLoot pool = lockPool(requireDroppedLoot(sessionId));
        if (!VOTING.equals(pool.getSplitPhase())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The party isn't voting on a split");
        }
        Set<Long> mine = seatedCharacters(sessionId).values().stream()
                .filter(p -> userId.equals(p.getOwnerUserId()))
                .map(SessionParticipant::getId)
                .collect(Collectors.toSet());
        List<SessionLootBallot> ballots = ballotRepository.findBySessionLootIdOrderByIdAsc(pool.getId());
        List<SessionLootBallot> myBallots = ballots.stream()
                .filter(b -> mine.contains(b.getParticipantId()))
                .toList();
        if (myBallots.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not hold a ballot in this vote");
        }
        for (SessionLootBallot b : myBallots) {
            b.setVote(vote);
            b.setPvp(Boolean.FALSE.equals(vote) ? pvp : null);
        }
        ballotRepository.saveAll(myBallots);

        boolean stillDropped = resolveIfSettled(session, pool, ballots, userId);

        bump(session);
        return stillDropped ? buildLootView(pool, userId) : null;
    }

    /**
     * DM override. APPROVE distributes the current split right away (the pile
     * must be empty); CANCEL takes the loot back as an undropped draft, the
     * same place a PvP cancel leaves it.
     */
    @Transactional
    public LootView overrideSplit(Long sessionId, String action, UUID dmUserId) {
        CombatSession session = activeSessionForDm(sessionId, dmUserId);
        SessionLoot pool = lockPool(requireDroppedLoot(sessionId));
        List<SessionLootBallot> ballots = ballotRepository.findBySessionLootIdOrderByIdAsc(pool.getId());
        int agree = (int) ballots.stream().filter(b -> Boolean.TRUE.equals(b.getVote())).count();
        int object = (int) ballots.stream().filter(b -> Boolean.FALSE.equals(b.getVote())).count();

        if ("APPROVE".equals(action)) {
            if (!fullyAllocated(pool, lootItemRepository.findBySessionLootIdOrderByIdAsc(pool.getId()))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Everything must have an owner before the split can go through");
            }
            distribute(pool, dmUserId);
            recordResult(session, pool, "DM_APPROVED", List.of(), agree, object);
            bump(session);
            return null; // distributed — the pool is gone
        }
        if (!"CANCEL".equals(action)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "action must be APPROVE or CANCEL");
        }
        backToDraft(pool);
        recordResult(session, pool, "DM_CANCELLED", List.of(), agree, object);

        bump(session);
        return buildLootView(pool, dmUserId); // the DM's draft again
    }

    // --- resolution -------------------------------------------------------------

    /**
     * Resolve the vote if it is decided. Returns whether the pool is still
     * dropped afterwards (false once it was distributed or cancelled to a draft).
     */
    private boolean resolveIfSettled(CombatSession session, SessionLoot pool,
                                     List<SessionLootBallot> ballots, UUID actorId) {
        int agree = (int) ballots.stream().filter(b -> Boolean.TRUE.equals(b.getVote())).count();
        int object = (int) ballots.stream().filter(b -> Boolean.FALSE.equals(b.getVote())).count();

        List<String> pvpCallers = ballots.stream()
                .filter(SessionLootBallot::isPvp)
                .map(SessionLootBallot::getDisplayName)
                .toList();
        if (!pvpCallers.isEmpty()) {
            backToDraft(pool);
            recordResult(session, pool, "PVP", pvpCallers, agree, object);
            return false;
        }
        if (!ballots.stream().allMatch(SessionLootBallot::isSettled)) return true;

        if (agree * 2 > ballots.size()) {
            distribute(pool, actorId);
            recordResult(session, pool, "APPROVED", List.of(), agree, object);
            return false;
        }
        ballotRepository.deleteBySessionLootId(pool.getId());
        pool.setSplitPhase(SPLITTING);
        lootRepository.save(pool);
        recordResult(session, pool, "NO_MAJORITY", List.of(), agree, object);
        return true;
    }

    /**
     * Write every share into its character's sheet, then delete the pool. PC
     * rows are locked in ascending id order (the pool row is already locked).
     * Each character gets one activity-log line listing what they received.
     */
    private void distribute(SessionLoot pool, UUID actorId) {
        List<SessionLootAllocation> shares = allocationRepository.findBySessionLootIdOrderByIdAsc(pool.getId());
        Map<Long, SessionLootItem> lines = lootItemRepository.findBySessionLootIdOrderByIdAsc(pool.getId())
                .stream().collect(Collectors.toMap(SessionLootItem::getId, Function.identity()));
        List<String> keys = lines.values().stream()
                .map(SessionLootItem::getCatalogItemKey).filter(Objects::nonNull).distinct().toList();
        Map<String, SrdItem> catalog = keys.isEmpty() ? Map.of() :
                srdItemRepository.findByItemKeyIn(keys).stream()
                        .collect(Collectors.toMap(SrdItem::getItemKey, Function.identity()));
        Map<Long, SessionParticipant> seated = participantRepository
                .findBySessionIdOrderByOrderIndexAsc(pool.getSessionId()).stream()
                .collect(Collectors.toMap(SessionParticipant::getId, Function.identity()));

        Map<Long, List<SessionLootAllocation>> byPc = new java.util.TreeMap<>();
        for (SessionLootAllocation share : shares) {
            SessionParticipant p = seated.get(share.getParticipantId());
            if (p == null || p.getPcId() == null) continue; // left the table — their share is forfeit
            byPc.computeIfAbsent(p.getPcId(), k -> new ArrayList<>()).add(share);
        }

        for (Map.Entry<Long, List<SessionLootAllocation>> entry : byPc.entrySet()) {
            Long pcId = entry.getKey();
            PC pc = pcRepository.findByIdForUpdate(pcId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "PC not found with id " + pcId));
            List<Map<String, Object>> inventory = json.parse(pc.getInventory());
            Map<String, Integer> coins = null;
            List<String> received = new ArrayList<>();
            for (SessionLootAllocation share : entry.getValue()) {
                if (share.getLootItemId() == null) {
                    coins = CoinPurse.add(coins != null ? coins : json.parseObject(pc.getCoins()), share.getCoinCp());
                    received.add(CoinPurse.format(share.getCoinCp()));
                    continue;
                }
                SessionLootItem line = lines.get(share.getLootItemId());
                if (line == null) continue;
                String name;
                if (line.getCatalogItemKey() != null) {
                    SrdItem item = catalog.get(line.getCatalogItemKey());
                    if (item == null) continue; // vanished from the catalog — skip defensively
                    // No unitCostCp — nothing was paid; a catalog match still makes it sellable.
                    InventoryEntries.addCatalogItem(inventory, item, share.getQty(), null, json);
                    name = item.getName();
                } else {
                    inventory.add(InventoryEntries.newCustomEntry(line.getCustomName(), line.getCategory(),
                            share.getQty(), line.getUnitCostCp(), line.getWeight(), line.getBulk(),
                            line.getDamage(), line.getArmorClass(), line.getCustomNotes()));
                    name = line.getCustomName();
                }
                received.add(share.getQty() > 1 ? name + " ×" + share.getQty() : name);
            }
            pc.setInventory(json.write(inventory));
            if (coins != null) pc.setCoins(json.writeObject(coins));
            pcRepository.save(pc);
            activityLogService.log(pcId, PcActivityType.LOOT,
                    "Received from the party split: " + String.join(", ", received), actorId);
        }

        deletePool(pool);
    }

    /** PvP or DM cancel: hide the pool again as a DM draft, with every share and ballot gone. */
    private void backToDraft(SessionLoot pool) {
        allocationRepository.deleteBySessionLootId(pool.getId());
        ballotRepository.deleteBySessionLootId(pool.getId());
        pool.setDropped(false);
        pool.setSplitPhase(null);
        lootRepository.save(pool);
    }

    private void recordResult(CombatSession session, SessionLoot pool, String outcome,
                              List<String> pvpCallers, int agree, int object) {
        resultRepository.findBySessionId(session.getId()).ifPresent(old -> {
            resultRepository.delete(old);
            resultRepository.flush(); // free the one-per-session unique index before re-inserting
        });
        SessionLootResult result = new SessionLootResult();
        result.setSessionId(session.getId());
        result.setLootName(pool.getName());
        result.setOutcome(outcome);
        result.setPvpCallers(pvpCallers.isEmpty() ? null : String.join(",", pvpCallers));
        result.setAgreeCount(agree);
        result.setObjectCount(object);
        resultRepository.save(result);
    }

    /** A DM edit to a pool under vote sends it back to splitting — the old ballots no longer apply. */
    private void reopenSplitIfVoting(SessionLoot pool) {
        if (VOTING.equals(pool.getSplitPhase())) {
            ballotRepository.deleteBySessionLootId(pool.getId());
            pool.setSplitPhase(SPLITTING);
            lootRepository.save(pool);
        }
    }

    // --- view ---------------------------------------------------------------------

    private LootView buildLootView(SessionLoot pool, UUID callerId) {
        List<SessionLootItem> lines = lootItemRepository.findBySessionLootIdOrderByIdAsc(pool.getId());
        List<String> keys = lines.stream()
                .map(SessionLootItem::getCatalogItemKey)
                .filter(Objects::nonNull)
                .toList();
        Map<String, SrdItem> catalog = keys.isEmpty() ? Map.of() :
                srdItemRepository.findByItemKeyIn(keys).stream()
                        .collect(Collectors.toMap(SrdItem::getItemKey, Function.identity()));

        List<SessionLootAllocation> shares = pool.isDropped()
                ? allocationRepository.findBySessionLootIdOrderByIdAsc(pool.getId())
                : List.of();
        Map<Long, Integer> allocatedByLine = shares.stream()
                .filter(a -> a.getLootItemId() != null)
                .collect(Collectors.groupingBy(SessionLootAllocation::getLootItemId,
                        Collectors.summingInt(SessionLootAllocation::getQty)));
        long coinsShared = shares.stream()
                .filter(a -> a.getLootItemId() == null)
                .mapToLong(SessionLootAllocation::getCoinCp)
                .sum();

        List<LootItemView> items = lines.stream()
                .map(line -> {
                    int inPile = Math.max(0, line.getQty() - allocatedByLine.getOrDefault(line.getId(), 0));
                    if (line.getCatalogItemKey() == null) {
                        return new LootItemView(line.getId(), null, line.getCustomName(), true,
                                line.getCustomNotes(), line.getCategory(), line.getUnitCostCp(),
                                line.getWeight(), line.getDamage(), line.getArmorClass(),
                                line.getQty(), inPile, line.getUnitCostCp(), line.getBulk());
                    }
                    SrdItem c = catalog.get(line.getCatalogItemKey());
                    if (c == null) return null; // catalog item vanished — skip defensively
                    return new LootItemView(line.getId(), c.getItemKey(), c.getName(), false,
                            null, null, null, null, null, null,
                            line.getQty(), inPile, c.getCostCp(), null);
                })
                .filter(Objects::nonNull)
                .toList();

        List<LootView.Recipient> recipients = List.of();
        List<LootView.Ballot> ballots = List.of();
        if (pool.isDropped()) {
            recipients = seatedCharacters(pool.getSessionId()).values().stream()
                    .map(p -> new LootView.Recipient(p.getId(), p.getPcId(), p.getDisplayName(),
                            callerId.equals(p.getOwnerUserId())))
                    .toList();
            Set<Long> mine = recipients.stream()
                    .filter(LootView.Recipient::mine)
                    .map(LootView.Recipient::participantId)
                    .collect(Collectors.toSet());
            if (VOTING.equals(pool.getSplitPhase())) {
                ballots = ballotRepository.findBySessionLootIdOrderByIdAsc(pool.getId()).stream()
                        .map(b -> new LootView.Ballot(b.getParticipantId(), b.getDisplayName(),
                                b.getVote(), b.getPvp(), mine.contains(b.getParticipantId())))
                        .toList();
            }
        }
        int voters = ballots.isEmpty() ? recipients.size() : ballots.size();

        return new LootView(pool.getId(), pool.getSessionId(), pool.getName(), pool.isDropped(),
                pool.getCoinCpTotal(), Math.max(0, pool.getCoinCpTotal() - coinsShared), items,
                pool.isDropped() ? (pool.getSplitPhase() == null ? SPLITTING : pool.getSplitPhase()) : "DRAFT",
                recipients,
                shares.stream()
                        .filter(a -> a.getLootItemId() != null)
                        .map(a -> new LootView.Allocation(a.getParticipantId(), a.getLootItemId(), a.getQty()))
                        .toList(),
                shares.stream()
                        .filter(a -> a.getLootItemId() == null)
                        .map(a -> new LootView.CoinShare(a.getParticipantId(), a.getCoinCp()))
                        .toList(),
                ballots,
                voters / 2 + 1);
    }

    // --- internals -----------------------------------------------------------

    /** One validated pasted line as a pool line: a catalog reference, or a custom item with its stats. */
    private SessionLootItem pastedLine(Long poolId, ImportLootRequest.Item line) {
        boolean hasKey = line.key() != null && !line.key().isBlank();
        SessionLootItem item = new SessionLootItem();
        item.setSessionLootId(poolId);
        item.setCatalogItemKey(hasKey ? line.key().trim() : null);
        item.setCustomName(hasKey ? null : line.name().trim());
        item.setCustomNotes(hasKey || line.notes() == null || line.notes().isBlank()
                ? null : line.notes().trim());
        int amount = line.qty() == null ? 1 : line.qty();
        item.setQty(amount);
        item.setQtyRemaining(amount);
        LootLines.applyAttributes(item, line.category(), line.valueGp(),
                line.weight(), line.bulk(), line.damage(), line.armorClass());
        return item;
    }

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

    private boolean fullyAllocated(SessionLoot pool, List<SessionLootItem> lines) {
        List<SessionLootAllocation> shares = allocationRepository.findBySessionLootIdOrderByIdAsc(pool.getId());
        Map<Long, Integer> allocatedByLine = shares.stream()
                .filter(a -> a.getLootItemId() != null)
                .collect(Collectors.groupingBy(SessionLootAllocation::getLootItemId,
                        Collectors.summingInt(SessionLootAllocation::getQty)));
        long coinsShared = shares.stream()
                .filter(a -> a.getLootItemId() == null)
                .mapToLong(SessionLootAllocation::getCoinCp)
                .sum();
        return coinsShared == pool.getCoinCpTotal()
                && lines.stream().allMatch(l -> allocatedByLine.getOrDefault(l.getId(), 0) == l.getQty());
    }

    private List<SessionLootAllocation> coinSharesOf(SessionLoot pool) {
        return allocationRepository.findBySessionLootIdOrderByIdAsc(pool.getId()).stream()
                .filter(a -> a.getLootItemId() == null)
                .toList();
    }

    /** Seated characters (PC participants) in seat order, keyed by participant id. */
    private Map<Long, SessionParticipant> seatedCharacters(Long sessionId) {
        return participantRepository.findBySessionIdOrderByOrderIndexAsc(sessionId).stream()
                .filter(p -> p.getPcId() != null)
                .sorted(Comparator.comparing(p -> p.getOrderIndex() == null ? 0 : p.getOrderIndex()))
                .collect(Collectors.toMap(SessionParticipant::getId, Function.identity(),
                        (a, b) -> a, LinkedHashMap::new));
    }

    /** {@code participantId} null means the pile; otherwise it must be a seated character. */
    private void requireRecipient(Map<Long, SessionParticipant> seated, Long participantId) {
        if (participantId != null && !seated.containsKey(participantId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Loot can only go to a character seated in this session");
        }
    }

    /**
     * The dropped pool, locked, for a split edit by a seated player. 409 while
     * the party is voting — the split is locked until the vote resolves.
     */
    private SessionLoot lockSplittingPool(Long sessionId, UUID userId) {
        SessionLoot pool = lockPool(requireDroppedLoot(sessionId));
        if (!isSeatedPlayer(sessionId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only seated players can split the loot");
        }
        if (VOTING.equals(pool.getSplitPhase())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The split is locked while the party votes");
        }
        return pool;
    }

    private SessionLoot lockPool(SessionLoot pool) {
        return lootRepository.findByIdForUpdate(pool.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "The loot is gone"));
    }

    private void deletePool(SessionLoot pool) {
        allocationRepository.deleteBySessionLootId(pool.getId());
        ballotRepository.deleteBySessionLootId(pool.getId());
        lootItemRepository.deleteBySessionLootId(pool.getId());
        lootRepository.delete(pool);
    }

    private void clearExistingLoot(Long sessionId) {
        lootRepository.findBySessionId(sessionId).ifPresent(existing -> {
            deletePool(existing);
            lootRepository.flush(); // free the one-per-session unique index before re-inserting
        });
    }

    private CombatSession liveSession(Long sessionId) {
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

    private boolean isSeatedPlayer(Long sessionId, UUID userId) {
        return participantRepository.findBySessionIdOrderByOrderIndexAsc(sessionId).stream()
                .anyMatch(p -> p.getPcId() != null && userId.equals(p.getOwnerUserId()));
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
