package com.moo.charactermanagerservice.services;

import com.moo.charactermanagerservice.dto.FactionView;
import com.moo.charactermanagerservice.dto.RenownAwardRequest;
import com.moo.charactermanagerservice.dto.RenownAwardResult;
import com.moo.charactermanagerservice.models.CombatSession;
import com.moo.charactermanagerservice.models.SessionParticipant;
import com.moo.charactermanagerservice.models.SessionStatus;
import com.moo.charactermanagerservice.repositories.CombatSessionRepository;
import com.moo.charactermanagerservice.repositories.SessionParticipantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Renown from inside a live session: the DM's reward bar picks a campaign
 * faction and moves every ticked seated PC's score with it by the same amount.
 *
 * <p>Kept apart from {@link SessionService} because nothing here touches the
 * session snapshot: renown is not on it, so a reward does not bump the session
 * version. This class only answers "is this the session's DM, and who is
 * seated?"; the scores themselves stay in {@link RenownService}.
 */
@Service
public class SessionRenownService {
    private final CombatSessionRepository sessionRepository;
    private final SessionParticipantRepository participantRepository;
    private final RenownService renownService;

    public SessionRenownService(CombatSessionRepository sessionRepository,
                                SessionParticipantRepository participantRepository,
                                RenownService renownService) {
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.renownService = renownService;
    }

    /** The factions of the session's campaign, for the DM's picker. DM only. */
    @Transactional(readOnly = true)
    public List<FactionView> factions(Long sessionId, UUID dmUserId) {
        return renownService.factionsOf(sessionForDm(sessionId, dmUserId).getCampaignId());
    }

    /**
     * Move the ticked seated PCs' renown with one faction. DM only, and refused
     * once the session has ended (same rule as an XP award). Who counts as a
     * recipient follows {@link SessionService#rewardRecipients}: PCs only, every
     * seated PC when no ids are sent, and a stale tick is a 400.
     */
    @Transactional
    public RenownAwardResult award(Long sessionId, RenownAwardRequest request, UUID dmUserId) {
        CombatSession session = sessionForDm(sessionId, dmUserId);
        if (session.getStatus() == SessionStatus.ENDED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session has ended");
        }
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A renown reward needs a body");
        }
        List<Long> pcIds = SessionService.rewardRecipients(
                        participantRepository.findBySessionIdOrderByOrderIndexAsc(sessionId),
                        request.participantIds())
                .stream().map(SessionParticipant::getPcId).toList();
        return renownService.awardToCharacters(
                session.getCampaignId(), request.factionId(), request.amount(), pcIds, dmUserId);
    }

    private CombatSession sessionForDm(Long sessionId, UUID dmUserId) {
        CombatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Session not found with id " + sessionId));
        if (!dmUserId.equals(session.getDmUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        return session;
    }
}
