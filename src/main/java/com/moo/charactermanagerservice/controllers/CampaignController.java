package com.moo.charactermanagerservice.controllers;

import com.moo.charactermanagerservice.dto.CampaignMemberView;
import com.moo.charactermanagerservice.dto.CampaignPreviewView;
import com.moo.charactermanagerservice.dto.CampaignSummaryView;
import com.moo.charactermanagerservice.dto.CreateNoteRequest;
import com.moo.charactermanagerservice.dto.JoinCampaignRequest;
import com.moo.charactermanagerservice.dto.SessionNoteView;
import com.moo.charactermanagerservice.dto.User;
import com.moo.charactermanagerservice.models.Campaign;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.services.CampaignService;
import com.moo.charactermanagerservice.services.SessionNoteService;
import com.moo.charactermanagerservice.validation.ValidationGroups.OnCreate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/campaign")
public class CampaignController {

    @Autowired
    private CampaignService campaignService;

    @Autowired
    private SessionNoteService sessionNoteService;

    /** All campaigns owned by the current DM. */
    @GetMapping("/mine")
    public ResponseEntity<List<Campaign>> getMyCampaigns(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(campaignService.findCampaignsForDm(user.getUuid()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Campaign> getCampaign(@PathVariable Long id, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(campaignService.findByIdForDm(id, user.getUuid()));
    }

    /** Invite preview — name + variant rules for a valid code, shown before joining. */
    @GetMapping("/invite/{code}/preview")
    public ResponseEntity<CampaignPreviewView> previewInvite(@PathVariable String code) {
        return ResponseEntity.ok(campaignService.previewByCode(code));
    }

    /** Campaign header — visible to the DM and to any player who owns a member. */
    @GetMapping("/{id}/summary")
    public ResponseEntity<CampaignSummaryView> getSummary(@PathVariable Long id,
                                                          Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(campaignService.getSummary(id, user.getUuid()));
    }

    /** Member projections — visible to the DM and to any player who owns a member. */
    @GetMapping("/{id}/members")
    public ResponseEntity<List<CampaignMemberView>> getMembers(@PathVariable Long id,
                                                               Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(campaignService.getMembers(id, user.getUuid()));
    }

    /** A player binds their own character to a campaign via its invite code. */
    @PostMapping("/join")
    public ResponseEntity<PC> joinCampaign(Authentication authentication,
                                           @RequestBody JoinCampaignRequest request) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(campaignService.joinByCode(request.code(), request.pcId(), user.getUuid()));
    }

    @PostMapping("/add")
    public ResponseEntity<Campaign> createCampaign(Authentication authentication,
                                                   @Validated(OnCreate.class) @RequestBody Campaign campaign) {
        User user = (User) authentication.getPrincipal();
        campaign.setDmUserId(user.getUuid());
        return ResponseEntity.status(HttpStatus.CREATED).body(campaignService.createCampaign(campaign));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Campaign> updateCampaign(@PathVariable Long id, Authentication authentication,
                                                   @RequestBody Campaign campaign) {
        User user = (User) authentication.getPrincipal();
        campaign.setId(id);
        return ResponseEntity.ok(campaignService.updateCampaign(campaign, user.getUuid()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCampaign(@PathVariable Long id, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        campaignService.deleteCampaign(id, user.getUuid());
        return ResponseEntity.noContent().build();
    }

    // --- DM session notes (campaign-scoped, DM-only) -------------------------

    /** Append a session note. The DM may add one from the campaign menu or in-session. */
    @PostMapping("/{id}/notes")
    public ResponseEntity<SessionNoteView> addNote(@PathVariable Long id, Authentication authentication,
                                                   @RequestBody CreateNoteRequest request) {
        User user = (User) authentication.getPrincipal();
        SessionNoteView note = sessionNoteService.addNote(id, request.body(), request.sessionId(), user.getUuid());
        return ResponseEntity.status(HttpStatus.CREATED).body(note);
    }

    /** All notes for a campaign, newest first (DM only). */
    @GetMapping("/{id}/notes")
    public ResponseEntity<List<SessionNoteView>> getNotes(@PathVariable Long id, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(sessionNoteService.listNotes(id, user.getUuid()));
    }

    @DeleteMapping("/{id}/notes/{noteId}")
    public ResponseEntity<Void> deleteNote(@PathVariable Long id, @PathVariable Long noteId,
                                           Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        sessionNoteService.deleteNote(id, noteId, user.getUuid());
        return ResponseEntity.noContent().build();
    }
}
