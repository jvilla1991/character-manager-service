package com.moo.charactermanagerservice.services;

import com.moo.charactermanagerservice.models.Campaign;
import com.moo.charactermanagerservice.repositories.CampaignRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CampaignServiceTest {

    @Mock
    private CampaignRepository campaignRepository;

    @InjectMocks
    private CampaignService campaignService;

    private UUID dmId;
    private UUID strangerId;
    private Campaign campaign;

    @BeforeEach
    void setUp() {
        dmId = UUID.randomUUID();
        strangerId = UUID.randomUUID();

        campaign = new Campaign();
        campaign.setId(1L);
        campaign.setName("The Veiled Compass");
        campaign.setDmUserId(dmId);
    }

    // --- createCampaign ---

    @Test
    void createCampaign_persistsAndReturns() {
        when(campaignRepository.saveAndFlush(campaign)).thenReturn(campaign);

        Campaign result = campaignService.createCampaign(campaign);

        assertThat(result).isSameAs(campaign);
        verify(campaignRepository).saveAndFlush(campaign);
    }

    // --- findById ---

    @Test
    void findById_returns_whenFound() {
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));

        assertThat(campaignService.findById(1L)).isSameAs(campaign);
    }

    @Test
    void findById_throws404_whenNotFound() {
        when(campaignRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> campaignService.findById(99L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(404));
    }

    // --- findByIdForDm ---

    @Test
    void findByIdForDm_returns_whenOwner() {
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));

        assertThat(campaignService.findByIdForDm(1L, dmId)).isSameAs(campaign);
    }

    @Test
    void findByIdForDm_throws403_whenNotOwner() {
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));

        assertThatThrownBy(() -> campaignService.findByIdForDm(1L, strangerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(403));
    }

    // --- updateCampaign ---

    @Test
    void updateCampaign_savesAndReturns_whenOwner() {
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));

        Campaign incoming = new Campaign();
        incoming.setId(1L);
        incoming.setName("Renamed");

        Campaign result = campaignService.updateCampaign(incoming, dmId);

        // DM ownership is preserved even though the incoming body omitted it
        assertThat(result.getDmUserId()).isEqualTo(dmId);
        verify(campaignRepository).save(incoming);
    }

    @Test
    void updateCampaign_throws403_whenNotOwner() {
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));

        Campaign incoming = new Campaign();
        incoming.setId(1L);

        assertThatThrownBy(() -> campaignService.updateCampaign(incoming, strangerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(403));

        verify(campaignRepository, never()).save(any());
    }

    // --- deleteCampaign ---

    @Test
    void deleteCampaign_deletesById_whenOwner() {
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));

        campaignService.deleteCampaign(1L, dmId);

        verify(campaignRepository).deleteById(1L);
    }

    @Test
    void deleteCampaign_throws403_whenNotOwner() {
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));

        assertThatThrownBy(() -> campaignService.deleteCampaign(1L, strangerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(403));

        verify(campaignRepository, never()).deleteById(any());
    }

    // --- findCampaignsForDm ---

    @Test
    void findCampaignsForDm_returnsList() {
        when(campaignRepository.findByDmUserId(dmId)).thenReturn(List.of(campaign));

        assertThat(campaignService.findCampaignsForDm(dmId)).containsExactly(campaign);
    }
}
