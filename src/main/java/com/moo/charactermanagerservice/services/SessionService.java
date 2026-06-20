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
    private final CampaignService campaignService;

    @Autowired
    public SessionService(CombatSessionRepository sessionRepository,
                          SessionParticipantRepository participantRepository,
                          PCRepository pcRepository,
                          CampaignService campaignService) {
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.pcRepository = pcRepository;
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
        List<Long> pcIds = participants.stream()
                .map(SessionParticipant::getPcId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, PC> pcsById = pcRepository.findAllById(pcIds).stream()
                .collect(Collectors.toMap(PC::getId, Function.identity()));

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
