package com.moo.charactermanagerservice.services;

import com.moo.charactermanagerservice.dto.ParticipantView;
import com.moo.charactermanagerservice.dto.SessionStateView;
import com.moo.charactermanagerservice.models.CombatSession;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.models.SessionParticipant;
import com.moo.charactermanagerservice.models.SessionStatus;
import com.moo.charactermanagerservice.repositories.CombatSessionRepository;
import com.moo.charactermanagerservice.repositories.SessionParticipantRepository;
import com.moo.charactermanagerservice.repositories.PCRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    private final CombatSessionRepository sessionRepository;
    private final SessionParticipantRepository participantRepository;
    private final PCRepository pcRepository;
    private final PCService pcService;
    private final CampaignService campaignService;

    @Autowired
    public SessionService(CombatSessionRepository sessionRepository,
                          SessionParticipantRepository participantRepository,
                          PCRepository pcRepository,
                          PCService pcService,
                          CampaignService campaignService) {
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.pcRepository = pcRepository;
        this.pcService = pcService;
        this.campaignService = campaignService;
    }

    /**
     * Open a LOBBY session for a campaign. Only the campaign's DM may do this
     * (enforced by {@link CampaignService#findByIdForDm}). Fails with 409 if a
     * non-ended session already exists for the campaign.
     */
    public SessionStateView createSession(Long campaignId, UUID dmUserId) {
        // Asserts the campaign exists (404) and the caller is its DM (403).
        campaignService.findByIdForDm(campaignId, dmUserId);

        sessionRepository.findByCampaignIdAndStatusNot(campaignId, SessionStatus.ENDED)
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "A session is already live for this campaign");
                });

        CombatSession session = new CombatSession();
        session.setCampaignId(campaignId);
        session.setDmUserId(dmUserId);
        session.setStatus(SessionStatus.LOBBY);
        // round, currentTurnIndex, and version use their entity defaults (1, 0, 0).
        CombatSession saved = sessionRepository.saveAndFlush(session);
        return buildState(saved, dmUserId);
    }

    /**
     * The poll snapshot. Readable by the DM or by any player who owns a
     * participating PC; everyone else is denied (mirrors campaign member access).
     */
    public SessionStateView getState(Long sessionId, UUID userId) {
        CombatSession session = findSession(sessionId);
        List<SessionParticipant> participants =
                participantRepository.findBySessionIdOrderByOrderIndexAsc(sessionId);

        boolean isDm = userId.equals(session.getDmUserId());
        boolean isMemberOwner = participants.stream()
                .anyMatch(p -> userId.equals(p.getOwnerUserId()));
        if (!isDm && !isMemberOwner) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        return buildState(session, participants, userId);
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
     * their own PC. The participant must belong to the given session.
     */
    public SessionStateView removeParticipant(Long sessionId, Long participantId, UUID userId) {
        CombatSession session = findSession(sessionId);
        SessionParticipant participant = requireParticipant(sessionId, participantId);

        boolean isDm = userId.equals(session.getDmUserId());
        boolean isOwner = userId.equals(participant.getOwnerUserId());
        if (!isDm && !isOwner) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        participantRepository.delete(participant);
        session.bumpVersion();
        sessionRepository.save(session);
        return buildState(session, userId);
    }

    /**
     * The DM enters (or re-enters) a combatant's initiative. Recomputes the
     * whole turn order server-side: initiative descending, ties broken by the
     * PC's Dexterity score (descending), then by id for stability. NPCs and
     * not-yet-entered combatants sort last. DM only.
     */
    public SessionStateView setInitiative(Long sessionId, Long participantId, Short value, UUID dmUserId) {
        CombatSession session = findSession(sessionId);
        assertDmOwnership(session, dmUserId);
        if (session.getStatus() == SessionStatus.ENDED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session has ended");
        }

        SessionParticipant target = requireParticipant(sessionId, participantId);

        target.setInitiative(value);
        target.setInitRolled(Boolean.TRUE);
        participantRepository.save(target);

        recomputeOrder(sessionId);

        session.bumpVersion();
        sessionRepository.save(session);
        return buildState(session, dmUserId);
    }

    /**
     * Re-sort a session's combatants and persist their order_index. Order:
     * initiative desc (unset last), then Dexterity desc (NPC/unknown last),
     * then id asc for a stable result.
     */
    private void recomputeOrder(Long sessionId) {
        List<SessionParticipant> participants =
                participantRepository.findBySessionIdOrderByOrderIndexAsc(sessionId);
        if (participants.isEmpty()) return;

        Map<Long, PC> pcsById = loadPcs(participants);

        Comparator<SessionParticipant> byInitiative = Comparator.comparing(
                SessionParticipant::getInitiative, Comparator.nullsLast(Comparator.reverseOrder()));
        Comparator<SessionParticipant> byDex = Comparator.comparing(
                p -> dexOf(p, pcsById), Comparator.nullsLast(Comparator.reverseOrder()));
        Comparator<SessionParticipant> byId = Comparator.comparing(SessionParticipant::getId);

        participants.sort(byInitiative.thenComparing(byDex).thenComparing(byId));

        for (short i = 0; i < participants.size(); i++) {
            participants.get(i).setOrderIndex(i);
        }
        participantRepository.saveAll(participants);
    }

    /** A PC participant's Dexterity score (the initiative tie-breaker); null for NPCs. */
    private Short dexOf(SessionParticipant p, Map<Long, PC> pcsById) {
        if (p.getPcId() == null) return null;
        PC pc = pcsById.get(p.getPcId());
        return pc == null ? null : pc.getAbilityDex();
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
            int[] hp = applyHpDelta(pc.getHpCurrent(), pc.getHpTemp(), pc.getHpMax(), amount);
            pc.setHpCurrent((short) hp[0]);
            pc.setHpTemp((short) hp[1]);
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

    /** End the session. DM only. HP/conditions are already persisted on the PCs. */
    public SessionStateView endSession(Long sessionId, UUID dmUserId) {
        CombatSession session = findSession(sessionId);
        assertDmOwnership(session, dmUserId);

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
     */
    private SessionStateView buildState(CombatSession session,
                                        List<SessionParticipant> participants,
                                        UUID requesterId) {
        Map<Long, PC> pcsById = loadPcs(participants);

        boolean isDm = requesterId.equals(session.getDmUserId());
        boolean active = session.getStatus() == SessionStatus.ACTIVE;
        Short turnIndex = session.getCurrentTurnIndex();

        List<ParticipantView> views = participants.stream().map(p -> {
            PC pc = p.getPcId() == null ? null : pcsById.get(p.getPcId());
            boolean ownedByMe = requesterId.equals(p.getOwnerUserId());
            boolean currentTurn = active
                    && p.getOrderIndex() != null
                    && p.getOrderIndex().equals(turnIndex);
            return ParticipantView.from(p, pc, ownedByMe, currentTurn);
        }).toList();

        return new SessionStateView(
                session.getId(),
                session.getCampaignId(),
                session.getStatus().name(),
                session.getRound(),
                session.getCurrentTurnIndex(),
                session.getVersion(),
                isDm,
                views
        );
    }
}
