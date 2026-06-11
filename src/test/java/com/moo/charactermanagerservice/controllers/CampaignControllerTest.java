package com.moo.charactermanagerservice.controllers;

import com.moo.charactermanagerservice.dto.User;
import com.moo.charactermanagerservice.models.Campaign;
import com.moo.charactermanagerservice.services.CampaignService;
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
}
