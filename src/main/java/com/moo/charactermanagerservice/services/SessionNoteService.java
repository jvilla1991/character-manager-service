package com.moo.charactermanagerservice.services;

import com.moo.charactermanagerservice.dto.SessionNoteView;
import com.moo.charactermanagerservice.models.SessionNote;
import com.moo.charactermanagerservice.repositories.SessionNoteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * DM session notes, scoped to the owning Dungeon Master. Every operation first
 * asserts the caller owns the campaign via {@link CampaignService#findByIdForDm}
 * (404 if the campaign is missing, 403 if the caller is not its DM), so notes are
 * never readable or writable by players.
 */
@Service
public class SessionNoteService {

    private final SessionNoteRepository sessionNoteRepository;
    private final CampaignService campaignService;

    @Autowired
    public SessionNoteService(SessionNoteRepository sessionNoteRepository,
                              CampaignService campaignService) {
        this.sessionNoteRepository = sessionNoteRepository;
        this.campaignService = campaignService;
    }

    /** Append a note to a campaign. sessionId is optional (null outside a session). */
    public SessionNoteView addNote(Long campaignId, String body, Long sessionId, UUID dmUserId) {
        campaignService.findByIdForDm(campaignId, dmUserId);   // asserts ownership
        if (body == null || body.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Note body must not be blank");
        }
        SessionNote note = new SessionNote();
        note.setCampaignId(campaignId);
        note.setSessionId(sessionId);
        note.setDmUserId(dmUserId);
        note.setBody(body.strip());
        return SessionNoteView.from(sessionNoteRepository.save(note));
    }

    /** A campaign's notes, newest first. */
    public List<SessionNoteView> listNotes(Long campaignId, UUID dmUserId) {
        campaignService.findByIdForDm(campaignId, dmUserId);   // asserts ownership
        return sessionNoteRepository.findByCampaignIdOrderByCreatedAtDesc(campaignId)
                .stream().map(SessionNoteView::from).toList();
    }

    /** Delete a note. 404 if the note does not exist or is not on the owned campaign. */
    public void deleteNote(Long campaignId, Long noteId, UUID dmUserId) {
        campaignService.findByIdForDm(campaignId, dmUserId);   // asserts ownership
        SessionNote note = sessionNoteRepository.findById(noteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Note not found"));
        if (!campaignId.equals(note.getCampaignId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Note not found on this campaign");
        }
        sessionNoteRepository.delete(note);
    }
}
