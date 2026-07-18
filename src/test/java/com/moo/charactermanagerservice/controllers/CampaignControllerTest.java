package com.moo.charactermanagerservice.controllers;

import com.moo.charactermanagerservice.dto.CampaignMemberView;
import com.moo.charactermanagerservice.dto.CreateNoteRequest;
import com.moo.charactermanagerservice.dto.JoinCampaignRequest;
import com.moo.charactermanagerservice.dto.SessionNoteView;
import com.moo.charactermanagerservice.dto.User;
import com.moo.charactermanagerservice.models.Campaign;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.services.CampaignService;
import com.moo.charactermanagerservice.services.SessionNoteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CampaignControllerTest {

    @InjectMocks
    private CampaignController campaignController;

    @Mock
    private CampaignService campaignService;

    @Mock
    private SessionNoteService sessionNoteService;

    private UUID dmId;
    private User dm;
    private Campaign campaign;
    private Authentication auth;

    @BeforeEach
    void setUp() {
        dmId = UUID.randomUUID();

        dm = new User();
        dm.setUuid(dmId);
        dm.setUserName("dmuser");
        dm.setFirstName("Quill");

        campaign = new Campaign();
        campaign.setId(1L);
        campaign.setName("The Veiled Compass");
        campaign.setDmUserId(dmId);

        auth = new UsernamePasswordAuthenticationToken(dm, null, dm.getAuthorities());
    }

    // --- GET /mine ---

    @Test
    void getMyCampaigns_returns200_withList() {
        when(campaignService.findCampaignsForDm(dmId)).thenReturn(List.of(campaign));

        ResponseEntity<List<Campaign>> response = campaignController.getMyCampaigns(auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(campaign);
    }

    // --- GET /{id} ---

    @Test
    void getCampaign_returns200_whenOwner() {
        when(campaignService.findByIdForDm(1L, dmId)).thenReturn(campaign);

        ResponseEntity<Campaign> response = campaignController.getCampaign(1L, auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(campaign);
    }

    @Test
    void getCampaign_propagates403_whenNotOwner() {
        when(campaignService.findByIdForDm(1L, dmId))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied"));

        assertThatThrownBy(() -> campaignController.getCampaign(1L, auth))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(403));
    }

    // --- POST /add ---

    @Test
    void createCampaign_returns201_setsDmOwnership() {
        Campaign incoming = new Campaign();
        incoming.setName("New Table");

        Campaign saved = new Campaign();
        saved.setId(2L);
        saved.setName("New Table");
        saved.setDmUserId(dmId);

        when(campaignService.createCampaign(any(Campaign.class))).thenReturn(saved);

        ResponseEntity<Campaign> response = campaignController.createCampaign(auth, incoming);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isSameAs(saved);
        // Controller stamps dmUserId from the principal before persisting
        verify(campaignService).createCampaign(argThat(c -> dmId.equals(c.getDmUserId())));
    }

    // --- PUT /{id} ---

    @Test
    void updateCampaign_returns200_andStampsId() {
        Campaign incoming = new Campaign();
        incoming.setName("Renamed");

        when(campaignService.updateCampaign(any(Campaign.class), eq(dmId))).thenReturn(campaign);

        ResponseEntity<Campaign> response = campaignController.updateCampaign(1L, auth, incoming);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(campaignService).updateCampaign(argThat(c -> Long.valueOf(1L).equals(c.getId())), eq(dmId));
    }

    @Test
    void updateCampaign_propagates403_whenNotOwner() {
        when(campaignService.updateCampaign(any(Campaign.class), eq(dmId)))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied"));

        assertThatThrownBy(() -> campaignController.updateCampaign(1L, auth, campaign))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(403));
    }

    // --- PUT /{id}/week-days ---

    @Test
    void setWeekDays_returns200_andDelegatesToTheService() {
        List<String> days = List.of("Sul", "Mol", "Zol");
        when(campaignService.setWeekDays(1L, days, dmId)).thenReturn(campaign);

        ResponseEntity<Campaign> response = campaignController.setWeekDays(1L, auth,
                new com.moo.charactermanagerservice.dto.SetWeekDaysRequest(days));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(campaign);
        verify(campaignService).setWeekDays(1L, days, dmId);
    }

    @Test
    void setWeekDays_propagates403_whenNotOwner() {
        when(campaignService.setWeekDays(eq(1L), anyList(), eq(dmId)))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied"));

        assertThatThrownBy(() -> campaignController.setWeekDays(1L, auth,
                new com.moo.charactermanagerservice.dto.SetWeekDaysRequest(List.of("Sul", "Mol"))))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(403));
    }

    // --- DELETE /{id} ---

    @Test
    void deleteCampaign_returns204_whenOwner() {
        doNothing().when(campaignService).deleteCampaign(1L, dmId);

        ResponseEntity<Void> response = campaignController.deleteCampaign(1L, auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(campaignService).deleteCampaign(1L, dmId);
    }

    @Test
    void deleteCampaign_propagates403_whenNotOwner() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied"))
                .when(campaignService).deleteCampaign(1L, dmId);

        assertThatThrownBy(() -> campaignController.deleteCampaign(1L, auth))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(403));
    }

    // --- GET /{id}/members ---

    @Test
    void getMembers_returns200_withProjections() {
        PC pc = new PC();
        pc.setId(7L);
        pc.setName("Lyra");
        CampaignMemberView view = CampaignMemberView.from(pc);
        when(campaignService.getMembers(1L, dmId)).thenReturn(List.of(view));

        ResponseEntity<List<CampaignMemberView>> response = campaignController.getMembers(1L, auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(view);
    }

    @Test
    void getMembers_propagates403_forUnrelatedUser() {
        when(campaignService.getMembers(1L, dmId))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied"));

        assertThatThrownBy(() -> campaignController.getMembers(1L, auth))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(403));
    }

    // --- GET /invite/{code}/preview ---

    @Test
    void previewInvite_returns200_withPreview() {
        var preview = new com.moo.charactermanagerservice.dto.CampaignPreviewView(
                "The Veiled Compass", java.util.Map.of("slotInventory", true));
        when(campaignService.previewByCode("ABC234")).thenReturn(preview);

        var response = campaignController.previewInvite("ABC234");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(preview);
    }

    // --- GET /{id}/summary ---

    @Test
    void getSummary_returns200_forMemberVisibleHeader() {
        var summary = new com.moo.charactermanagerservice.dto.CampaignSummaryView(
                1L, "The Veiled Compass", java.util.Map.of("slotInventory", true),
                java.util.Map.of("name", "Neverwinter", "type", "Settlement"));
        when(campaignService.getSummary(1L, dmId)).thenReturn(summary);

        var response = campaignController.getSummary(1L, auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(summary);
    }

    // --- POST /join ---

    @Test
    void joinCampaign_returns200_withBoundPc() {
        PC bound = new PC();
        bound.setId(7L);
        bound.setCampaignId(1L);
        when(campaignService.joinByCode("ABC234", 7L, true, dmId)).thenReturn(bound);

        ResponseEntity<PC> response = campaignController.joinCampaign(auth,
                new JoinCampaignRequest("ABC234", 7L, true));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(bound);
    }

    @Test
    void joinCampaign_propagates404_whenCodeUnknown() {
        when(campaignService.joinByCode("ZZZZZZ", 7L, null, dmId))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "No campaign"));

        assertThatThrownBy(() -> campaignController.joinCampaign(auth, new JoinCampaignRequest("ZZZZZZ", 7L, null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(404));
    }

    // --- POST /{id}/notes ---

    @Test
    void addNote_returns201_withCreatedNote() {
        SessionNoteView view = new SessionNoteView(5L, 1L, null, "A new clue surfaces", java.time.Instant.now(), null);
        when(sessionNoteService.addNote(1L, "A new clue surfaces", null, dmId)).thenReturn(view);

        ResponseEntity<SessionNoteView> response =
                campaignController.addNote(1L, auth, new CreateNoteRequest("A new clue surfaces", null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isSameAs(view);
    }

    @Test
    void addNote_passesSessionId_whenTakenInSession() {
        SessionNoteView view = new SessionNoteView(6L, 1L, 9L, "Mid-combat ruling", java.time.Instant.now(), null);
        when(sessionNoteService.addNote(1L, "Mid-combat ruling", 9L, dmId)).thenReturn(view);

        ResponseEntity<SessionNoteView> response =
                campaignController.addNote(1L, auth, new CreateNoteRequest("Mid-combat ruling", 9L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(sessionNoteService).addNote(1L, "Mid-combat ruling", 9L, dmId);
    }

    // --- GET /{id}/notes ---

    @Test
    void getNotes_returns200_withList() {
        SessionNoteView view = new SessionNoteView(5L, 1L, null, "Recap", java.time.Instant.now(), null);
        when(sessionNoteService.listNotes(1L, dmId)).thenReturn(List.of(view));

        ResponseEntity<List<SessionNoteView>> response = campaignController.getNotes(1L, auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(view);
    }

    @Test
    void getNotes_propagates403_whenNotOwner() {
        when(sessionNoteService.listNotes(1L, dmId))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied"));

        assertThatThrownBy(() -> campaignController.getNotes(1L, auth))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(403));
    }

    // --- PUT /{id}/notes/{noteId} ---

    @Test
    void updateNote_returns200_withUpdatedNote() {
        SessionNoteView view = new SessionNoteView(5L, 1L, null, "Edited recap",
                java.time.Instant.now(), java.time.Instant.now());
        when(sessionNoteService.updateNote(1L, 5L, "Edited recap", dmId)).thenReturn(view);

        ResponseEntity<SessionNoteView> response = campaignController.updateNote(
                1L, 5L, new com.moo.charactermanagerservice.dto.UpdateNoteRequest("Edited recap"), auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(view);
    }

    @Test
    void updateNote_propagates403_whenNotOwner() {
        when(sessionNoteService.updateNote(1L, 5L, "x", dmId))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied"));

        assertThatThrownBy(() -> campaignController.updateNote(
                1L, 5L, new com.moo.charactermanagerservice.dto.UpdateNoteRequest("x"), auth))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(403));
    }

    // --- DELETE /{id}/notes/{noteId} ---

    @Test
    void deleteNote_returns204_whenOwner() {
        doNothing().when(sessionNoteService).deleteNote(1L, 5L, dmId);

        ResponseEntity<Void> response = campaignController.deleteNote(1L, 5L, auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(sessionNoteService).deleteNote(1L, 5L, dmId);
    }
}
