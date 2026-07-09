package com.moo.charactermanagerservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moo.charactermanagerservice.dto.CastResult;
import com.moo.charactermanagerservice.dto.ParticipantView;
import com.moo.charactermanagerservice.dto.SessionStateView;
import com.moo.charactermanagerservice.dto.SetLocationRequest;
import com.moo.charactermanagerservice.dto.SetTimeRequest;
import com.moo.charactermanagerservice.dto.XpAwardResult;
import com.moo.charactermanagerservice.models.Campaign;
import com.moo.charactermanagerservice.models.CombatSession;
import com.moo.charactermanagerservice.models.Encounter;
import com.moo.charactermanagerservice.models.EncounterCreature;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.models.PcActivityType;
import com.moo.charactermanagerservice.models.SessionLoot;
import com.moo.charactermanagerservice.models.SessionParticipant;
import com.moo.charactermanagerservice.models.SessionShop;
import com.moo.charactermanagerservice.models.SessionStatus;
import com.moo.charactermanagerservice.repositories.CampaignRepository;
import com.moo.charactermanagerservice.repositories.CombatSessionRepository;
import com.moo.charactermanagerservice.repositories.EncounterCreatureRepository;
import com.moo.charactermanagerservice.repositories.EncounterRepository;
import com.moo.charactermanagerservice.repositories.SessionLootItemRepository;
import com.moo.charactermanagerservice.repositories.SessionLootRepository;
import com.moo.charactermanagerservice.repositories.SessionParticipantRepository;
import com.moo.charactermanagerservice.repositories.SessionShopAttendeeRepository;
import com.moo.charactermanagerservice.repositories.SessionShopRepository;
import com.moo.charactermanagerservice.repositories.PCRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Live session lifecycle, scoped to the owning Dungeon Master. Mirrors
 * {@link CampaignService}'s ownership model: a session is owned by the DM who
 * created it, and DM-only actions assert that ownership (403 otherwise). Session
 * state is server-authoritative — clients read the {@link SessionStateView}
 * snapshot and never compute turn order themselves.
 *
 * <p>Scope here is the lifecycle (create / read / end). Joining participants,
 * initiative entry, and damage write-through arrive in later features.
 */
@Service
public class SessionService {

    /** A session idle longer than this is auto-ended on next access (no scheduler). */
    private static final Duration SESSION_TTL = Duration.ofHours(4);

    private final CombatSessionRepository sessionRepository;
    private final SessionParticipantRepository participantRepository;
    private final SessionShopRepository shopRepository;
    private final SessionShopAttendeeRepository shopAttendeeRepository;
    private final SessionLootRepository lootRepository;
    private final SessionLootItemRepository lootItemRepository;
    private final PCRepository pcRepository;
    private final PCService pcService;
    private final CampaignService campaignService;
    private final CampaignRepository campaignRepository;
    private final EncounterRepository encounterRepository;
    private final EncounterCreatureRepository encounterCreatureRepository;
    private final PcActivityLogService activityLogService;
    private final PcJsonColumns json;

    @Autowired
    public SessionService(CombatSessionRepository sessionRepository,
                          SessionParticipantRepository participantRepository,
                          SessionShopRepository shopRepository,
                          SessionShopAttendeeRepository shopAttendeeRepository,
                          SessionLootRepository lootRepository,
                          SessionLootItemRepository lootItemRepository,
                          PCRepository pcRepository,
                          PCService pcService,
                          CampaignService campaignService,
                          CampaignRepository campaignRepository,
                          EncounterRepository encounterRepository,
                          EncounterCreatureRepository encounterCreatureRepository,
                          PcActivityLogService activityLogService,
                          ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.shopRepository = shopRepository;
        this.shopAttendeeRepository = shopAttendeeRepository;
        this.lootRepository = lootRepository;
        this.lootItemRepository = lootItemRepository;
        this.pcRepository = pcRepository;
        this.pcService = pcService;
        this.campaignService = campaignService;
        this.campaignRepository = campaignRepository;
        this.encounterRepository = encounterRepository;
        this.encounterCreatureRepository = encounterCreatureRepository;
        this.activityLogService = activityLogService;
        this.json = new PcJsonColumns(objectMapper);
    }

    /**
     * Open a LOBBY session for a campaign. Only the campaign's DM may do this
     * (enforced by {@link CampaignService#findByIdForDm}). Fails with 409 if a
     * non-ended session already exists for the campaign.
     */
    public SessionStateView createSession(Long campaignId, UUID dmUserId) {
        // Asserts the campaign exists (404) and the caller is its DM (403).
        campaignService.findByIdForDm(campaignId, dmUserId);

        activeSession(campaignId).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A session is already live for this campaign");
        });

        CombatSession session = new CombatSession();
        session.setCampaignId(campaignId);
        session.setDmUserId(dmUserId);
        session.setStatus(SessionStatus.LOBBY);
        // round and version use their entity defaults (1, 0); the turn pointer
        // stays null until startEncounter.
        CombatSession saved = sessionRepository.saveAndFlush(session);
        return buildState(saved, dmUserId);
    }

    /**
     * The poll snapshot. Readable by the DM or by any player who owns a
     * participating PC; everyone else is denied (mirrors campaign member access).
     *
     * <p>{@code sinceVersion} is the poll short-circuit: when it matches the
     * session's current version nothing has changed since the caller's last
     * snapshot, so this returns null and the controller replies 204 — the 2s
     * poll then costs a couple of DB reads and an empty response instead of the
     * full payload.
     */
    public SessionStateView getState(Long sessionId, UUID userId, Long sinceVersion) {
        CombatSession session = findSession(sessionId);
        List<SessionParticipant> participants =
                participantRepository.findBySessionIdOrderByOrderIndexAsc(sessionId);

        boolean isDm = userId.equals(session.getDmUserId());
        boolean isMemberOwner = participants.stream()
                .anyMatch(p -> userId.equals(p.getOwnerUserId()));
        if (!isDm && !isMemberOwner) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        if (sinceVersion != null && sinceVersion.equals(session.getVersion())) {
            return null;
        }
        return buildState(session, participants, userId);
    }

    /**
     * The campaign's current live (non-ended) session, or null if none. Visible
     * to the DM and to any campaign member (a PC owner in the campaign) — broader
     * than {@link #getState}, so a player can discover and join a session before
     * they have a participant row.
     */
    public SessionStateView getActiveSessionForCampaign(Long campaignId, UUID userId) {
        Campaign campaign = campaignService.findById(campaignId);
        boolean isDm = userId.equals(campaign.getDmUserId());
        boolean isMember = pcRepository.findByCampaignId(campaignId).stream()
                .anyMatch(pc -> userId.equals(pc.getUserId()));
        if (!isDm && !isMember) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        return activeSession(campaignId)
                .map(session -> buildState(session, userId))
                .orElse(null);
    }

    /**
     * The campaign's live session, auto-ending it first if it has been idle past
     * {@link #SESSION_TTL}. Returns empty when there is none — or it just expired —
     * so callers treat an abandoned session as gone rather than blocking on it.
     * This is lazy expiry: the cleanup happens on access, no background job.
     */
    private Optional<CombatSession> activeSession(Long campaignId) {
        return sessionRepository.findByCampaignIdAndStatusNot(campaignId, SessionStatus.ENDED)
                .filter(session -> {
                    if (isExpired(session)) {
                        session.setStatus(SessionStatus.ENDED);
                        session.bumpVersion();
                        sessionRepository.save(session);
                        return false;
                    }
                    return true;
                });
    }

    /** True once a session has gone untouched (no edits) for longer than the TTL. */
    private boolean isExpired(CombatSession session) {
        Instant lastActivity = session.getUpdatedAt();
        return lastActivity != null && lastActivity.isBefore(Instant.now().minus(SESSION_TTL));
    }

    /**
     * Seat one of the caller's own PCs in the session. The PC must already be a
     * member of the session's campaign (players use the campaign join-by-code
     * flow first). Idempotent: re-joining the same PC returns the current state
     * without creating a duplicate row. New combatants are appended to the end;
     * initiative ordering is applied later when the DM enters initiative.
     */
    public SessionStateView joinSession(Long sessionId, Long pcId, UUID userId) {
        CombatSession session = findSession(sessionId);
        if (session.getStatus() == SessionStatus.ENDED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session has ended");
        }

        // Asserts the caller owns this PC (403) and that it exists (404).
        PC pc = pcService.findPCByIdForUser(pcId, userId);
        if (pc.getCampaignId() == null || !pc.getCampaignId().equals(session.getCampaignId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Character is not a member of this campaign");
        }

        if (participantRepository.findBySessionIdAndPcId(sessionId, pcId).isEmpty()) {
            int nextIndex = participantRepository
                    .findBySessionIdOrderByOrderIndexAsc(sessionId).size();

            SessionParticipant participant = new SessionParticipant();
            participant.setSessionId(sessionId);
            participant.setPcId(pcId);
            participant.setOwnerUserId(userId);
            participant.setDisplayName(pc.getName());
            participant.setOrderIndex((short) nextIndex); // append to end
            participantRepository.save(participant);

            session.bumpVersion();
            sessionRepository.save(session);
        }
        return buildState(session, userId);
    }

    /**
     * Remove a combatant. The DM may remove anyone; a player may remove only
     * their own PC. The participant must belong to the given session. If the
     * combatant being removed holds the turn pointer, the pointer advances off
     * them first (wrapping and incrementing the round if they were last in
     * order) so it never dangles on a deleted row.
     */
    public SessionStateView removeParticipant(Long sessionId, Long participantId, UUID userId) {
        CombatSession session = findSession(sessionId);
        SessionParticipant participant = requireParticipant(sessionId, participantId);

        boolean isDm = userId.equals(session.getDmUserId());
        boolean isOwner = userId.equals(participant.getOwnerUserId());
        if (!isDm && !isOwner) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        if (participantId.equals(session.getCurrentTurnParticipantId())) {
            List<SessionParticipant> ordered =
                    participantRepository.findBySessionIdOrderByOrderIndexAsc(sessionId);
            if (ordered.size() <= 1) {
                session.setCurrentTurnParticipantId(null); // last combatant leaves
            } else {
                movePointerToNext(session, ordered);
            }
        }

        participantRepository.delete(participant);
        session.bumpVersion();
        sessionRepository.save(session);
        return buildState(session, userId);
    }

    /**
     * Start the encounter: LOBBY → ACTIVE. DM only. Recomputes the order (so
     * combatants without initiative sit at the bottom, per the nulls-last sort)
     * and points the turn at the top of it. Requires at least one combatant —
     * an empty encounter has no turn to point at.
     */
    public SessionStateView startEncounter(Long sessionId, UUID dmUserId) {
        CombatSession session = findSession(sessionId);
        assertDmOwnership(session, dmUserId);
        if (session.getStatus() != SessionStatus.LOBBY) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Encounter can only be started from the lobby");
        }

        List<SessionParticipant> ordered = recomputeOrder(sessionId);
        if (ordered.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot start an encounter with no combatants");
        }

        session.setStatus(SessionStatus.ACTIVE);
        session.setCurrentTurnParticipantId(ordered.get(0).getId());
        session.setRound((short) 1); // a fresh encounter always opens on round 1
        session.bumpVersion();
        sessionRepository.save(session);
        return buildState(session, dmUserId);
    }

    /**
     * End the encounter (not the session): ACTIVE → back to LOBBY. What
     * happened stands — HP, conditions, and XP were written through live — but
     * turn tracking stops: the pointer clears, and every combatant's initiative
     * resets so the next encounter at this table rolls fresh (players may enter
     * again because init_rolled is back to false). DM only.
     */
    public SessionStateView endEncounter(Long sessionId, UUID dmUserId) {
        CombatSession session = findSession(sessionId);
        assertDmOwnership(session, dmUserId);
        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No encounter is running");
        }

        List<SessionParticipant> participants =
                participantRepository.findBySessionIdOrderByOrderIndexAsc(sessionId);
        for (SessionParticipant p : participants) {
            p.setInitiative(null);
            p.setInitRolled(Boolean.FALSE);
        }
        participantRepository.saveAll(participants);

        session.setStatus(SessionStatus.LOBBY);
        session.setCurrentTurnParticipantId(null);
        session.setRound((short) 1);
        session.bumpVersion();
        sessionRepository.save(session);
        return buildState(session, dmUserId);
    }

    /**
     * Advance the turn to the next combatant in order, wrapping past the end to
     * the top and incrementing the round. The DM may advance anyone's turn
     * (Next); a player may only end <em>their own</em> — the advance is allowed
     * only when the combatant currently holding the turn is owned by the caller.
     *
     * <p>Optimistic concurrency: the caller sends the participant ID they
     * believe is active. A mismatch means they acted on a stale snapshot (e.g.
     * DM Next and a player End Turn racing) — the request is rejected with 409
     * and the client refetches instead of retrying, so the turn can never
     * double-advance. The stale check runs before the ownership check: by the
     * time a player's End Turn loses a race, it no longer matters whether the
     * turn was theirs.
     */
    public SessionStateView advanceTurn(Long sessionId, Long expectedActiveParticipantId, UUID userId) {
        CombatSession session = findSession(sessionId);
        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Encounter is not active");
        }
        if (expectedActiveParticipantId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "expectedActiveParticipantId is required");
        }
        if (!expectedActiveParticipantId.equals(session.getCurrentTurnParticipantId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Turn has already advanced — refresh and try again");
        }

        List<SessionParticipant> ordered =
                participantRepository.findBySessionIdOrderByOrderIndexAsc(sessionId);

        boolean isDm = userId.equals(session.getDmUserId());
        if (!isDm) {
            boolean ownsActiveCombatant = ordered.stream()
                    .filter(p -> p.getId().equals(session.getCurrentTurnParticipantId()))
                    .anyMatch(p -> userId.equals(p.getOwnerUserId()));
            if (!ownsActiveCombatant) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "You can only end your own turn");
            }
        }

        movePointerToNext(session, ordered);

        session.bumpVersion();
        sessionRepository.save(session);
        return buildState(session, userId);
    }

    /**
     * Move the pointer to the combatant after the current one in the given
     * order, wrapping to the top and incrementing the round when the current
     * combatant is last. A pointer not found in the list (defensive — removal
     * keeps it valid) counts as "before the top", so the next combatant is the
     * first one and the round does not increment.
     */
    private void movePointerToNext(CombatSession session, List<SessionParticipant> ordered) {
        int currentIdx = -1;
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).getId().equals(session.getCurrentTurnParticipantId())) {
                currentIdx = i;
                break;
            }
        }

        int nextIdx = currentIdx + 1;
        if (nextIdx >= ordered.size()) {
            nextIdx = 0;
            session.setRound((short) (session.getRound() + 1));
        }
        session.setCurrentTurnParticipantId(ordered.get(nextIdx).getId());
    }

    /**
     * Enter (or re-enter) a combatant's initiative. Recomputes the whole turn
     * order server-side: initiative descending, ties broken by Dexterity
     * modifier (descending), then by id (insertion order) for stability. NPCs
     * and not-yet-entered combatants sort last.
     *
     * <p>Authorization: the DM may edit any combatant at any time. A player may
     * only touch their own combatant, and only to <em>enter</em> initiative —
     * freely in the lobby, and once the encounter is active only while theirs
     * is still unset (so a late joiner can slot in mid-encounter, but nobody
     * revises a bad roll after the fact).
     *
     * <p>The turn pointer is untouched — it is a participant ID, so a
     * combatant whose new initiative sorts them above the current turn lands in
     * territory already passed this round and is only reached after the wrap,
     * i.e. they act starting next round.
     */
    public SessionStateView setInitiative(Long sessionId, Long participantId, Short value, UUID userId) {
        CombatSession session = findSession(sessionId);
        if (session.getStatus() == SessionStatus.ENDED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session has ended");
        }

        SessionParticipant target = requireParticipant(sessionId, participantId);

        boolean isDm = userId.equals(session.getDmUserId());
        if (!isDm) {
            if (!userId.equals(target.getOwnerUserId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
            }
            boolean locked = session.getStatus() == SessionStatus.ACTIVE
                    && Boolean.TRUE.equals(target.getInitRolled());
            if (locked) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Initiative is locked once the encounter starts — ask the DM to change it");
            }
        }

        target.setInitiative(value);
        target.setInitRolled(Boolean.TRUE);
        participantRepository.save(target);

        recomputeOrder(sessionId);

        session.bumpVersion();
        sessionRepository.save(session);
        return buildState(session, userId);
    }

    /**
     * Re-sort a session's combatants, persist their order_index, and return
     * them in the new order. Order: initiative desc (unset last), then DEX
     * modifier desc (NPC/unknown last), then id asc — id is BIGSERIAL, so that
     * final fallback is insertion order and keeps full ties from reshuffling.
     */
    private List<SessionParticipant> recomputeOrder(Long sessionId) {
        List<SessionParticipant> participants =
                participantRepository.findBySessionIdOrderByOrderIndexAsc(sessionId);
        if (participants.isEmpty()) return participants;

        Map<Long, PC> pcsById = loadPcs(participants);

        Comparator<SessionParticipant> byInitiative = Comparator.comparing(
                SessionParticipant::getInitiative, Comparator.nullsLast(Comparator.reverseOrder()));
        Comparator<SessionParticipant> byDexMod = Comparator.comparing(
                p -> dexModOf(p, pcsById), Comparator.nullsLast(Comparator.reverseOrder()));
        Comparator<SessionParticipant> byId = Comparator.comparing(SessionParticipant::getId);

        participants.sort(byInitiative.thenComparing(byDexMod).thenComparing(byId));

        for (short i = 0; i < participants.size(); i++) {
            participants.get(i).setOrderIndex(i);
        }
        participantRepository.saveAll(participants);
        return participants;
    }

    /**
     * A combatant's Dexterity modifier, the initiative tie-breaker. For an
     * enemy it is the DM-entered {@code dexModifier}; for a PC it is derived
     * from the canonical ability score, so there is no stored copy to drift.
     * Both sides of the comparison are modifiers, never raw scores.
     * {@code Math.floorDiv} keeps odd scores below 10 correct (9 → -1, not 0).
     */
    private Integer dexModOf(SessionParticipant p, Map<Long, PC> pcsById) {
        if (p.getPcId() == null) {
            return p.getDexModifier() == null ? null : p.getDexModifier().intValue();
        }
        PC pc = pcsById.get(p.getPcId());
        if (pc == null || pc.getAbilityDex() == null) return null;
        return Math.floorDiv(pc.getAbilityDex() - 10, 2);
    }

    /** Load the canonical PCs for a set of participants, keyed by id (NPCs excluded). */
    private Map<Long, PC> loadPcs(List<SessionParticipant> participants) {
        List<Long> pcIds = participants.stream()
                .map(SessionParticipant::getPcId)
                .filter(Objects::nonNull)
                .toList();
        return pcRepository.findAllById(pcIds).stream()
                .collect(Collectors.toMap(PC::getId, Function.identity()));
    }

    /**
     * Apply damage (positive amount) or healing (negative amount) to a combatant.
     * DM only. For a PC this writes through to the canonical pc row so the change
     * persists after the session ends — temporary HP absorbs damage first, and
     * healing is capped at max HP. For an NPC the change lands on the participant
     * row. Current HP never drops below zero.
     */
    public SessionStateView applyDamage(Long sessionId, Long participantId, Integer amount, UUID dmUserId) {
        CombatSession session = findSession(sessionId);
        assertDmOwnership(session, dmUserId);
        if (session.getStatus() == SessionStatus.ENDED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session has ended");
        }
        if (amount == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount is required");
        }

        SessionParticipant participant = requireParticipant(sessionId, participantId);

        if (participant.getPcId() != null) {
            // Write through to the canonical character — this is the source of truth.
            PC pc = pcService.findPCById(participant.getPcId());
            int before = pc.getHpCurrent() == null ? 0 : pc.getHpCurrent();
            int[] hp = applyHpDelta(pc.getHpCurrent(), pc.getHpTemp(), pc.getHpMax(), amount);
            pc.setHpCurrent((short) hp[0]);
            pc.setHpTemp((short) hp[1]);
            // Darker Dungeons: dropping to 0 HP is an exhausting shock — +1 fatigue.
            // Only on the transition into 0 so repeated hits at 0 don't stack.
            if (before > 0 && hp[0] == 0 && survivalConditionsEnabled(session)) {
                pc.setSurvival(json.writeObject(
                        SurvivalRules.bump(json.parseObject(pc.getSurvival()), "fatigue", 1)));
            }
            pcRepository.save(pc);
        } else {
            int[] hp = applyHpDelta(participant.getNpcHpCurrent(), participant.getNpcHpTemp(),
                    participant.getNpcHpMax(), amount);
            participant.setNpcHpCurrent((short) hp[0]);
            participant.setNpcHpTemp((short) hp[1]);
            participantRepository.save(participant);
        }

        session.bumpVersion();
        sessionRepository.save(session);
        return buildState(session, dmUserId);
    }

    /**
     * Resolve an HP change. Returns {@code [newCurrent, newTemp]}. Positive
     * {@code amount} is damage (temp HP absorbs first, current floored at 0);
     * negative is healing (current capped at max, temp untouched).
     */
    private int[] applyHpDelta(Short curShort, Short tempShort, Short maxShort, int amount) {
        int cur = curShort == null ? 0 : curShort;
        int temp = tempShort == null ? 0 : tempShort;

        if (amount > 0) {
            int absorbed = Math.min(temp, amount);
            temp -= absorbed;
            cur = Math.max(0, cur - (amount - absorbed));
        } else if (amount < 0) {
            cur += -amount;
            if (maxShort != null) {
                cur = Math.min(cur, maxShort);
            }
        }
        return new int[]{cur, temp};
    }

    /**
     * Award XP to a single combatant. DM only. Writes through to the canonical pc
     * row — the same source-of-truth rule as {@link #applyDamage} — and floors the
     * total at 0 so a negative correction can't drive XP below zero. XP is not on
     * the session snapshot, so the result carries the new total for the DM's
     * confirmation toast. Bumps the session version.
     */
    @Transactional
    public XpAwardResult awardXp(Long sessionId, Long participantId, Integer amount, UUID dmUserId) {
        CombatSession session = findSession(sessionId);
        assertDmOwnership(session, dmUserId);
        if (session.getStatus() == SessionStatus.ENDED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session has ended");
        }
        if (amount == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount is required");
        }

        SessionParticipant participant = requireParticipant(sessionId, participantId);
        if (participant.getPcId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot award XP to a non-PC combatant");
        }

        PC pc = pcService.findPCById(participant.getPcId());
        XpAwardResult.Entry entry = applyXpDelta(pc, amount, dmUserId);

        session.bumpVersion();
        sessionRepository.save(session);
        return new XpAwardResult(List.of(entry));
    }

    /**
     * Award the same XP amount to every seated PC in the session — NPC participants
     * are skipped. DM only. Each PC's total is written through and floored at 0, and
     * the version bumps once. Returns one entry per awarded PC for the DM toast.
     */
    @Transactional
    public XpAwardResult awardXpToAll(Long sessionId, Integer amount, UUID dmUserId) {
        CombatSession session = findSession(sessionId);
        assertDmOwnership(session, dmUserId);
        if (session.getStatus() == SessionStatus.ENDED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session has ended");
        }
        if (amount == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount is required");
        }

        List<SessionParticipant> participants =
                participantRepository.findBySessionIdOrderByOrderIndexAsc(sessionId);
        Map<Long, PC> pcsById = loadPcs(participants);

        List<XpAwardResult.Entry> awarded = new ArrayList<>();
        for (SessionParticipant p : participants) {
            if (p.getPcId() == null) continue;        // NPCs have no XP
            PC pc = pcsById.get(p.getPcId());
            if (pc == null) continue;
            awarded.add(applyXpDelta(pc, amount, dmUserId));
        }

        session.bumpVersion();
        sessionRepository.save(session);
        return new XpAwardResult(awarded);
    }

    /**
     * Apply an XP delta to a PC (floored at 0), persist it, and report the
     * result. Logs the APPLIED delta (after flooring) — not the requested
     * amount — and skips the log entirely when flooring reduced it to zero
     * (nothing actually changed).
     */
    private XpAwardResult.Entry applyXpDelta(PC pc, int amount, UUID dmUserId) {
        int current = pc.getXp() == null ? 0 : pc.getXp();
        int updated = Math.max(0, current + amount);
        int applied = updated - current;
        pc.setXp(updated);
        pcRepository.save(pc);
        if (applied != 0) {
            String verb = applied > 0 ? "Awarded " : "Removed ";
            activityLogService.log(pc.getId(), PcActivityType.XP_AWARD,
                    verb + Math.abs(applied) + " XP", dmUserId);
        }
        return new XpAwardResult.Entry(pc.getId(), pc.getName(), updated, applied);
    }

    /** True when the session's campaign runs the survival-conditions variant. */
    private boolean survivalConditionsEnabled(CombatSession session) {
        Campaign campaign = campaignService.findById(session.getCampaignId());
        return campaignService.isVariantEnabled(campaign, "survivalConditions");
    }

    /**
     * DM advances the campaign clock one segment (dawn → noon → dusk → night →
     * next day's dawn). The clock is campaign state, so it persists between
     * sessions. When the campaign runs the survival-conditions variant, entering
     * dawn/noon/dusk applies the book's condition bumps to EVERY campaign-member
     * PC — the party stays in sync whether or not each player is seated.
     *
     * <p>A campaign whose clock was never set is initialized to day 1 dawn with
     * NO bumps: the first click establishes the clock, it doesn't pass time.
     *
     * <p>With a defined week (campaign {@code weekDays}), the night → morning
     * rollover also walks the weekday to the next defined name — wrapping past
     * the last day increments the week counter (see {@link GameClock}).
     */
    @Transactional
    public SessionStateView advanceTime(Long sessionId, UUID dmUserId) {
        CombatSession session = findSession(sessionId);
        assertDmOwnership(session, dmUserId);
        if (session.getStatus() == SessionStatus.ENDED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session has ended");
        }

        Campaign campaign = campaignService.findById(session.getCampaignId());
        String stored = campaign.getGameTime();
        if (stored == null || stored.isBlank()) {
            campaign.setGameTime(json.writeObject(GameClock.initial()));
        } else {
            Map<String, Object> advanced = GameClock.advanceSegment(
                    json.parseObject(stored), campaignService.parseWeekDays(campaign));
            campaign.setGameTime(json.writeObject(advanced));
            String segment = String.valueOf(advanced.get("timeOfDay"));
            // Survival campaigns: each member auto-eats/drinks their supplies as
            // time passes (hunger/thirst rise only when the ration/waterskin runs
            // out); fatigue always climbs on its segments.
            if (campaignService.isVariantEnabled(campaign, "survivalConditions")) {
                // Lock member rows one at a time in ascending-id order — the same
                // row lock purchase/sell take, so a concurrent buy can't be
                // overwritten and lock acquisition can't deadlock.
                List<Long> memberIds = pcRepository.findByCampaignId(campaign.getId()).stream()
                        .map(PC::getId)
                        .sorted()
                        .toList();
                for (Long pcId : memberIds) {
                    pcRepository.findByIdForUpdate(pcId).ifPresent(pc -> advanceSurvival(pc, segment));
                }
            }
        }
        campaignRepository.save(campaign);

        session.bumpVersion();
        sessionRepository.save(session);
        return buildState(session, dmUserId);
    }

    /**
     * Advance one member's survival for a day-segment: seed their starting
     * supplies once (covers members who predate the feature), auto-consume a
     * ration/water on hunger/thirst steps, and apply the resulting stage
     * changes — persisting the updated survival and inventory together.
     */
    private void advanceSurvival(PC pc, String segment) {
        Map<String, Object> survival = json.parseObject(pc.getSurvival());
        List<Map<String, Object>> inventory = json.parse(pc.getInventory());

        // Container model: migrate legacy supply lines (implied box; waterskin
        // qty was the water charges), then seed the starting kit once.
        SurvivalSupplies.normalize(inventory);
        if (!Boolean.TRUE.equals(survival.get("seeded"))) {
            SurvivalSupplies.seed(inventory);
        }
        boolean supplyStep = SurvivalRules.isSupplyStep(segment);
        boolean ate = supplyStep && SurvivalSupplies.tryConsume(inventory, "rations");
        // Water is drunk from the 'water' charge line — never from the skin itself.
        boolean drank = supplyStep && SurvivalSupplies.tryConsume(inventory, "water");

        Map<String, Object> next = SurvivalRules.applySegment(survival, segment, ate, drank);
        next.put("seeded", true);
        pc.setSurvival(json.writeObject(next));
        pc.setInventory(json.write(inventory));
        pcRepository.save(pc);
    }

    /**
     * DM long rest for the seated party: every seated PC recovers all spell
     * slots, and — in a survival campaign — sheds fatigue (3 for an undisturbed
     * rest, 1 for a disturbed one). Bumps the version so the party's sheets
     * update live.
     */
    @Transactional
    public SessionStateView longRest(Long sessionId, boolean undisturbed, UUID dmUserId) {
        CombatSession session = findSession(sessionId);
        assertDmOwnership(session, dmUserId);
        if (session.getStatus() == SessionStatus.ENDED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session has ended");
        }
        boolean survival = survivalConditionsEnabled(session);
        int fatigueRelief = undisturbed ? 3 : 1;

        List<Long> pcIds = participantRepository.findBySessionIdOrderByOrderIndexAsc(sessionId).stream()
                .map(SessionParticipant::getPcId)
                .filter(Objects::nonNull)
                .sorted()
                .toList();
        for (Long pcId : pcIds) {
            pcRepository.findByIdForUpdate(pcId).ifPresent(pc -> {
                pc.setSpellSlots(json.writeObject(restoreAllSlots(json.parseObject(pc.getSpellSlots()))));
                if (survival) {
                    pc.setSurvival(json.writeObject(
                            SurvivalRules.bump(json.parseObject(pc.getSurvival()), "fatigue", -fatigueRelief)));
                }
                pcRepository.save(pc);
                activityLogService.log(pc.getId(), PcActivityType.LONG_REST,
                        "Completed a long rest", dmUserId);
            });
        }
        session.bumpVersion();
        sessionRepository.save(session);
        return buildState(session, dmUserId);
    }

    /** Reset every spell level's {@code used} count to 0 (a full recovery). */
    @SuppressWarnings("unchecked")
    private Map<String, Object> restoreAllSlots(Map<String, Object> slots) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : slots.entrySet()) {
            if (e.getValue() instanceof Map<?, ?> lvl) {
                Map<String, Object> next = new LinkedHashMap<>((Map<String, Object>) lvl);
                next.put("used", 0);
                out.put(e.getKey(), next);
            } else {
                out.put(e.getKey(), e.getValue());
            }
        }
        return out;
    }

    /**
     * DM sets the campaign clock directly (correcting the date or matching a
     * homebrew calendar — date parts are free text). No condition bumps — only
     * {@link #advanceTime} passes time.
     *
     * <p>The week counter lives here: when the WEEKDAY CHANGES to a value seen
     * before (case-insensitive) since the last completed week, a week has
     * passed — the counter ticks and the history resets to the new weekday.
     * An unchanged weekday (e.g. a date correction) never touches the history,
     * so resubmitting the form can't double-count.
     *
     * <p>With a defined week (campaign {@code weekDays}) the repetition logic
     * is skipped entirely: the weekday must be one of the defined names
     * (canonicalized to the defined casing), the week counter moves only when
     * the request carries an explicit {@code week}, and {@code weekdaysSeen}
     * is frozen — neither read nor written — so the fallback resumes untouched
     * if the definition is ever removed.
     */
    @Transactional
    public SessionStateView setTime(Long sessionId, SetTimeRequest request, UUID dmUserId) {
        CombatSession session = findSession(sessionId);
        assertDmOwnership(session, dmUserId);
        if (session.getStatus() == SessionStatus.ENDED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session has ended");
        }
        if (request == null
                || !GameClock.isValidSegment(request.timeOfDay())
                || !GameClock.isValidLabel(request.year())
                || !GameClock.isValidLabel(request.month())
                || !GameClock.isValidLabel(request.day())
                || !GameClock.isValidLabel(request.weekday())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Time requires timeOfDay morning|noon|night and date labels of at most "
                            + GameClock.MAX_LABEL_LENGTH + " characters");
        }

        Campaign campaign = campaignService.findById(session.getCampaignId());
        Map<String, Object> current = GameClock.normalize(json.parseObject(campaign.getGameTime()));

        @SuppressWarnings("unchecked")
        List<String> weekdaysSeen = (List<String>) current.get("weekdaysSeen");
        int week = (int) current.get("week");
        String currentWeekday = current.get("weekday") instanceof String s ? s : null;
        String newWeekday = request.weekday() == null || request.weekday().isBlank()
                ? null
                : request.weekday().trim();

        List<String> definedWeek = campaignService.parseWeekDays(campaign);
        if (definedWeek != null) {
            // Defined week: the weekday must be a defined name (stored in the
            // defined casing) and the week counter never moves on its own —
            // only an explicit request week (floored at 1) changes it.
            if (newWeekday != null) {
                newWeekday = GameClock.canonicalDay(definedWeek, newWeekday);
                if (newWeekday == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Weekday must be one of the campaign's defined days");
                }
            }
            if (request.week() != null) {
                week = Math.max(1, request.week());
            }
        } else {
            boolean weekdayChanged = newWeekday != null
                    && (currentWeekday == null || !newWeekday.equalsIgnoreCase(currentWeekday));
            if (weekdayChanged) {
                boolean seenBefore = weekdaysSeen.stream().anyMatch(newWeekday::equalsIgnoreCase);
                if (seenBefore) {
                    week++;                                  // a full week has passed
                    weekdaysSeen = new ArrayList<>(List.of(newWeekday));
                } else {
                    weekdaysSeen.add(newWeekday);
                }
            }
        }

        campaign.setGameTime(json.writeObject(GameClock.of(
                blankToNull(request.year()) == null ? (String) current.get("year") : request.year().trim(),
                blankToNull(request.month()) == null ? (String) current.get("month") : request.month().trim(),
                blankToNull(request.day()) == null ? (String) current.get("day") : request.day().trim(),
                request.timeOfDay(),
                newWeekday == null ? currentWeekday : newWeekday,
                weekdaysSeen,
                week)));
        campaignRepository.save(campaign);

        session.bumpVersion();
        sessionRepository.save(session);
        return buildState(session, dmUserId);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    /** The party-location types the DM may pick, mirrored on the client. */
    private static final Set<String> LOCATION_TYPES = Set.of("Settlement", "Wilderness", "Dungeon");
    private static final int LOCATION_NAME_MAX = 60;

    /**
     * DM sets the party's current location. Party-wide (stored on the campaign,
     * like the clock) and version-bumped, so every seated sheet updates via the
     * poll. Name is free text (may be blank); type must be one of the three
     * recognized kinds.
     */
    @Transactional
    public SessionStateView setLocation(Long sessionId, SetLocationRequest request, UUID dmUserId) {
        CombatSession session = findSession(sessionId);
        assertDmOwnership(session, dmUserId);
        if (session.getStatus() == SessionStatus.ENDED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session has ended");
        }
        if (request == null || request.type() == null || !LOCATION_TYPES.contains(request.type())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Location requires a type of Settlement, Wilderness, or Dungeon");
        }
        String name = request.name() == null ? "" : request.name().trim();
        if (name.length() > LOCATION_NAME_MAX) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Location name must be at most " + LOCATION_NAME_MAX + " characters");
        }

        Campaign campaign = campaignService.findById(session.getCampaignId());
        Map<String, Object> location = new LinkedHashMap<>();
        location.put("name", name);
        location.put("type", request.type());
        campaign.setLocation(json.writeObject(location));
        campaignRepository.save(campaign);

        session.bumpVersion();
        sessionRepository.save(session);
        return buildState(session, dmUserId);
    }

    /**
     * A player casts one of their own seated PC's spells: spends a slot at
     * {@code atLevel} (nothing for a cantrip; upcasting allowed) and consumes a
     * flagged material component from inventory. Server-authoritative and
     * version-bumped — mirrors {@link #consumeSurvival} step for step — so the
     * DM sees the slot spend live via the poll. Unlike survival there is no
     * variant gate on casting itself; the {@code strictComponents} variant only
     * decides whether a missing costly component blocks the cast (409) or lets
     * it through with a warning (the table's call).
     */
    @Transactional
    public CastResult castSpell(Long sessionId, Long pcId, String spellName, Integer atLevel, UUID userId) {
        CombatSession session = findSession(sessionId);
        if (session.getStatus() == SessionStatus.ENDED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session has ended");
        }
        if (pcId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "pcId is required");
        }
        if (spellName == null || spellName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "spellName is required");
        }

        SessionParticipant seated = participantRepository.findBySessionIdAndPcId(sessionId, pcId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Character is not seated in this session"));
        if (!userId.equals(seated.getOwnerUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        // Same row lock as purchase/sell/consume — slots and inventory are read-modify-write.
        PC pc = pcRepository.findByIdForUpdate(pcId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Character not found"));

        List<Map<String, Object>> spells = json.parse(pc.getSpells());
        Map<String, Object> spell = SpellcastingRules.findSpell(spells, spellName);
        if (spell == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Character does not know " + spellName);
        }

        int spellLevel = SpellcastingRules.levelOf(spell);
        Map<String, Object> slots = json.parseObject(pc.getSpellSlots());
        // Cantrips (level 0) never spend a slot; a leveled spell needs a free slot
        // at or above its level (upcasting), chosen by the caller.
        if (spellLevel > 0) {
            int level = atLevel == null ? spellLevel : atLevel;
            if (level < spellLevel) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Cannot cast a level " + spellLevel + " spell at level " + level);
            }
            if (!SpellcastingRules.hasSlotAvailable(slots, level)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "No level " + level + " slot available");
            }
            SpellcastingRules.spendSlot(slots, level);
        }

        List<Map<String, Object>> inventory = json.parse(pc.getInventory());
        Map<String, Object> componentLine = SpellcastingRules.findComponentLine(inventory, spellName);
        String warning = null;
        if (componentLine != null) {
            // A consumed-on-cast component is spent; a reusable one only needs to be present.
            if (Boolean.TRUE.equals(componentLine.get("consumedOnCast"))) {
                SpellcastingRules.consumeComponentLine(inventory, spellName);
            }
        } else if (SpellcastingRules.needsCostlyComponent(spell)) {
            // No line, but the spell needs a costly one: strict campaigns block,
            // lenient ones let the table decide and just flag it.
            Campaign campaign = campaignService.findById(session.getCampaignId());
            String material = spell.get("material") instanceof String m ? m : "a costly component";
            if (campaignService.isVariantEnabled(campaign, "strictComponents")) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Missing material component: " + material);
            }
            warning = "Missing material component: " + material;
        }

        pc.setSpellSlots(json.writeObject(slots));
        pc.setInventory(json.write(inventory));
        pcRepository.save(pc);

        session.bumpVersion();
        sessionRepository.save(session);
        return new CastResult(pcId, slots, inventory, warning);
    }

    /**
     * Decrement one unit of the first live line with this catalog key; the line
     * is removed at zero. No matching line is fine — the action still applies.
     */
    private void consumeInventoryLine(List<Map<String, Object>> inventory, String catalogKey) {
        for (Iterator<Map<String, Object>> it = inventory.iterator(); it.hasNext(); ) {
            Map<String, Object> line = it.next();
            if (!catalogKey.equals(line.get("catalogKey"))) continue;
            if ("dropped".equals(line.get("status"))) continue;
            int qty = line.get("qty") instanceof Number n ? n.intValue() : 0;
            if (qty <= 0) continue;
            if (qty == 1) {
                it.remove();
            } else {
                line.put("qty", qty - 1);
            }
            return;
        }
    }

    /** Load a participant and assert it belongs to the given session (404 otherwise). */
    private SessionParticipant requireParticipant(Long sessionId, Long participantId) {
        SessionParticipant participant = participantRepository.findById(participantId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Participant not found with id " + participantId));
        if (!sessionId.equals(participant.getSessionId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Participant not found in this session");
        }
        return participant;
    }

    /**
     * DM adds an enemy combatant, in the lobby or mid-encounter. The DM
     * calculates and enters the DEX modifier (the initiative tie-breaker); the
     * enemy starts with no initiative, so the re-sort parks it at the bottom of
     * the order until the DM enters one — at which point the normal late-entry
     * rule applies (sorted above the pointer means it acts next round).
     */
    public SessionStateView addEnemy(Long sessionId, String name, Short dexModifier, Short hpMax,
                                     UUID dmUserId) {
        CombatSession session = findSession(sessionId);
        assertDmOwnership(session, dmUserId);
        if (session.getStatus() == SessionStatus.ENDED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session has ended");
        }
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
        if (dexModifier == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dexModifier is required");
        }

        SessionParticipant enemy = new SessionParticipant();
        enemy.setSessionId(sessionId);
        enemy.setDisplayName(name.trim());
        enemy.setDexModifier(dexModifier);
        enemy.setNpcHpMax(hpMax);
        enemy.setNpcHpCurrent(hpMax);
        participantRepository.save(enemy);

        recomputeOrder(sessionId);

        session.bumpVersion();
        sessionRepository.save(session);
        return buildState(session, dmUserId);
    }

    /**
     * DM loads a curated encounter into the session: every {@link EncounterCreature}
     * becomes an enemy combatant, exactly as {@link #addEnemy} would create it (no
     * initiative — the DM rolls). Creatures are appended to any existing combatants;
     * a quantity &gt; 1 expands into numbered rows (e.g. Goblin 1..Goblin 4). The
     * whole load is one transaction and bumps the version once.
     */
    @Transactional
    public SessionStateView loadEncounter(Long sessionId, Long encounterId, UUID dmUserId) {
        CombatSession session = findSession(sessionId);
        assertDmOwnership(session, dmUserId);
        if (session.getStatus() == SessionStatus.ENDED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session has ended");
        }

        Encounter encounter = encounterRepository.findById(encounterId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Encounter not found with id " + encounterId));
        // The encounter must belong to this DM and to this session's campaign.
        if (!dmUserId.equals(encounter.getDmUserId())
                || !Objects.equals(encounter.getCampaignId(), session.getCampaignId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        List<EncounterCreature> creatures =
                encounterCreatureRepository.findByEncounterIdOrderByIdAsc(encounterId);
        for (EncounterCreature creature : creatures) {
            int count = Math.max(1, creature.getQuantity());
            for (int n = 1; n <= count; n++) {
                SessionParticipant enemy = new SessionParticipant();
                enemy.setSessionId(sessionId);
                // Suffix a running number only when there is more than one of this creature.
                enemy.setDisplayName(count > 1 ? creature.getName() + " " + n : creature.getName());
                enemy.setDexModifier(creature.getDexModifier());
                enemy.setNpcHpMax(creature.getHpMax());
                enemy.setNpcHpCurrent(creature.getHpMax());
                participantRepository.save(enemy);
            }
        }

        recomputeOrder(sessionId);

        session.bumpVersion();
        sessionRepository.save(session);
        return buildState(session, dmUserId);
    }

    /** DM toggles whether players can see enemy combatants at all. */
    public SessionStateView setVisibility(Long sessionId, Boolean enemiesHidden, UUID dmUserId) {
        CombatSession session = findSession(sessionId);
        assertDmOwnership(session, dmUserId);
        if (session.getStatus() == SessionStatus.ENDED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session has ended");
        }
        if (enemiesHidden == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "enemiesHidden is required");
        }

        session.setEnemiesHidden(enemiesHidden);
        session.bumpVersion();
        sessionRepository.save(session);
        return buildState(session, dmUserId);
    }

    /** DM sets (or clears, with null) the encounter-level turn-cue sound. */
    public SessionStateView setTurnSound(Long sessionId, String turnSound, UUID dmUserId) {
        CombatSession session = findSession(sessionId);
        assertDmOwnership(session, dmUserId);
        if (session.getStatus() == SessionStatus.ENDED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session has ended");
        }

        session.setTurnSound(turnSound);
        session.bumpVersion();
        sessionRepository.save(session);
        return buildState(session, dmUserId);
    }

    /** End the session. DM only. HP/conditions are already persisted on the PCs. */
    @Transactional
    public SessionStateView endSession(Long sessionId, UUID dmUserId) {
        CombatSession session = findSession(sessionId);
        assertDmOwnership(session, dmUserId);

        // Unclaimed loot is discarded — the pool never outlives its session.
        lootRepository.findBySessionId(sessionId).ifPresent(pool -> {
            lootItemRepository.deleteBySessionLootId(pool.getId());
            lootRepository.delete(pool);
        });

        session.setStatus(SessionStatus.ENDED);
        session.bumpVersion();
        CombatSession saved = sessionRepository.save(session);
        return buildState(saved, dmUserId);
    }

    // --- internals ---

    private CombatSession findSession(Long sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Session not found with id " + sessionId));
    }

    private void assertDmOwnership(CombatSession session, UUID dmUserId) {
        if (!dmUserId.equals(session.getDmUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
    }

    private SessionStateView buildState(CombatSession session, UUID requesterId) {
        return buildState(session,
                participantRepository.findBySessionIdOrderByOrderIndexAsc(session.getId()),
                requesterId);
    }

    /**
     * Compose the snapshot. PC HP/conditions are read from the canonical pc rows
     * (loaded in one batch); NPC stats come from the participant rows.
     *
     * <p>This is the single place visibility is resolved. A player's view with
     * enemies hidden: enemy rows are omitted from the list (their data never
     * enters the payload), {@code activeParticipantId} is null while the turn
     * sits on a combatant they cannot see (no glow, no sound cue), and the
     * on-deck target is the next combatant in TRUE turn order that they are
     * allowed to see. The DM always gets the full list and the true next. Turn
     * order itself is never affected — only what each viewer is shown.
     */
    private SessionStateView buildState(CombatSession session,
                                        List<SessionParticipant> participants,
                                        UUID requesterId) {
        Map<Long, PC> pcsById = loadPcs(participants);

        boolean isDm = requesterId.equals(session.getDmUserId());
        boolean active = session.getStatus() == SessionStatus.ACTIVE;
        boolean enemiesHidden = Boolean.TRUE.equals(session.getEnemiesHidden());

        List<SessionParticipant> visible = (isDm || !enemiesHidden)
                ? participants
                : participants.stream().filter(p -> p.getPcId() != null).toList();

        Long pointerId = active ? session.getCurrentTurnParticipantId() : null;
        Long activeId = null;
        Long onDeckId = null;
        if (pointerId != null) {
            boolean pointerVisible = visible.stream().anyMatch(p -> pointerId.equals(p.getId()));
            activeId = pointerVisible ? pointerId : null;
            onDeckId = nextVisibleAfter(pointerId, participants, visible);
            if (pointerId.equals(onDeckId)) {
                onDeckId = null; // one visible combatant — never green and yellow at once
            }
        }

        final Long activeIdForViews = activeId;
        List<ParticipantView> views = visible.stream().map(p -> {
            PC pc = p.getPcId() == null ? null : pcsById.get(p.getPcId());
            boolean ownedByMe = requesterId.equals(p.getOwnerUserId());
            boolean currentTurn = p.getId().equals(activeIdForViews);
            return ParticipantView.from(p, pc, ownedByMe, currentTurn);
        }).toList();

        // Targeted-shop signal: a shop is open, and it's visible to the DM or to a
        // targeted attendee. The full catalog is fetched separately from /shop.
        SessionShop shop = shopRepository.findBySessionId(session.getId()).orElse(null);
        boolean shopOpen = shop != null;
        boolean shopForMe = shopOpen && (isDm || shopAttendeeRepository.findBySessionShopId(shop.getId())
                .stream().anyMatch(a -> requesterId.equals(a.getOwnerUserId())));
        String shopCategory = shopForMe ? shop.getCategory() : null;

        // Loot signal, caller-scoped like the shop's: "DRAFT" only for the DM
        // (players never learn an undropped pool exists), "DROPPED" for the DM
        // and for seated players, null otherwise. Pool contents are fetched
        // separately from /loot, re-keyed on every version change (claims and
        // DM edits mutate the pool under a stable status).
        SessionLoot loot = lootRepository.findBySessionId(session.getId()).orElse(null);
        String lootStatus = null;
        String lootName = null;
        if (loot != null) {
            boolean seated = participants.stream()
                    .anyMatch(p -> p.getPcId() != null && requesterId.equals(p.getOwnerUserId()));
            if (isDm) {
                lootStatus = loot.isDropped() ? "DROPPED" : "DRAFT";
            } else if (loot.isDropped() && seated) {
                lootStatus = "DROPPED";
            }
            lootName = lootStatus == null ? null : loot.getName();
        }

        // Caller-scoped XP: only the requester's own seated PC, never a teammate's
        // (ParticipantView is broadcast to everyone, so XP can't live there).
        Integer myXp = participants.stream()
                .filter(p -> requesterId.equals(p.getOwnerUserId()) && p.getPcId() != null)
                .findFirst()
                .map(p -> pcsById.get(p.getPcId()))
                .map(PC::getXp)
                .orElse(null);

        // The campaign clock and party location ride every snapshot (each null
        // until the DM sets it). The sinceVersion 204 short-circuit returns before
        // buildState, so idle polls never pay for this campaign read.
        Campaign campaign = campaignService.findById(session.getCampaignId());
        String storedTime = campaign.getGameTime();
        Map<String, Object> gameTime = (storedTime == null || storedTime.isBlank())
                ? null
                : json.parseObject(storedTime);
        String storedLocation = campaign.getLocation();
        Map<String, Object> location = (storedLocation == null || storedLocation.isBlank())
                ? null
                : json.parseObject(storedLocation);
        // The defined week (or null for free-text weekdays) rides along so the
        // client can render the weekday dropdown and week field.
        List<String> weekDays = campaignService.parseWeekDays(campaign);

        return new SessionStateView(
                session.getId(),
                session.getCampaignId(),
                session.getStatus().name(),
                session.getRound(),
                activeId,
                onDeckId,
                session.getVersion(),
                isDm,
                enemiesHidden,
                session.getTurnSound(),
                shopOpen,
                shopForMe,
                shopCategory,
                lootStatus,
                lootName,
                myXp,
                gameTime,
                location,
                weekDays,
                views
        );
    }

    /**
     * The next combatant in true cyclic turn order after the pointer that the
     * viewer is allowed to see — hidden enemies are stepped over, never shown as
     * a masked slot. Walks at most one full loop, so with a single visible
     * combatant it returns the pointer itself (the caller nulls that out).
     */
    private Long nextVisibleAfter(Long pointerId, List<SessionParticipant> ordered,
                                  List<SessionParticipant> visible) {
        int idx = -1;
        for (int i = 0; i < ordered.size(); i++) {
            if (pointerId.equals(ordered.get(i).getId())) {
                idx = i;
                break;
            }
        }
        if (idx < 0) return null;

        Set<Long> visibleIds = visible.stream()
                .map(SessionParticipant::getId)
                .collect(Collectors.toSet());
        for (int step = 1; step <= ordered.size(); step++) {
            SessionParticipant candidate = ordered.get((idx + step) % ordered.size());
            if (visibleIds.contains(candidate.getId())) {
                return candidate.getId();
            }
        }
        return null;
    }
}
