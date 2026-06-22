package com.moo.charactermanagerservice.controllers;

import com.moo.charactermanagerservice.dto.JoinSessionRequest;
import com.moo.charactermanagerservice.dto.SessionStateView;
import com.moo.charactermanagerservice.dto.User;
import com.moo.charactermanagerservice.services.SessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Live "Session Mode" endpoints. The session backend lives in the same service
 * as PCs so HP/condition edits write through to the canonical character with no
 * cross-service sync. Real-time delivery is short-poll against
 * {@code GET /session/{id}/state} (App Runner is HTTP-only).
 *
 * <p>Auth follows the existing pattern: the principal is the {@link User}
 * resolved by {@code AuthorizationFilter}; the service asserts ownership.
 */
@RestController
@RequestMapping("/api/v1")
public class SessionController {

    @Autowired
    private SessionService sessionService;

    /** DM opens a lobby for one of their campaigns (409 if one is already live). */
    @PostMapping("/campaign/{campaignId}/session")
    public ResponseEntity<SessionStateView> createSession(@PathVariable Long campaignId,
                                                          Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(sessionService.createSession(campaignId, user.getUuid()));
    }

    /** Poll snapshot — visible to the DM and to any player who owns a participant. */
    @GetMapping("/session/{id}/state")
    public ResponseEntity<SessionStateView> getState(@PathVariable Long id,
                                                     Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(sessionService.getState(id, user.getUuid()));
    }

    /** A player seats one of their own PCs (must be a member of the campaign). */
    @PostMapping("/session/{id}/join")
    public ResponseEntity<SessionStateView> join(@PathVariable Long id,
                                                 @RequestBody JoinSessionRequest request,
                                                 Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(sessionService.joinSession(id, request.pcId(), user.getUuid()));
    }

    /** Remove a combatant — the DM may remove anyone; a player only their own. */
    @DeleteMapping("/session/{id}/participants/{participantId}")
    public ResponseEntity<SessionStateView> removeParticipant(@PathVariable Long id,
                                                             @PathVariable Long participantId,
                                                             Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(sessionService.removeParticipant(id, participantId, user.getUuid()));
    }

    /** DM ends the session. */
    @PostMapping("/session/{id}/end")
    public ResponseEntity<SessionStateView> endSession(@PathVariable Long id,
                                                      Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(sessionService.endSession(id, user.getUuid()));
    }
}
