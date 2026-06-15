package com.moo.charactermanagerservice.services;

import com.moo.charactermanagerservice.dto.CampaignMemberView;
import com.moo.charactermanagerservice.models.Campaign;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.repositories.CampaignRepository;
import com.moo.charactermanagerservice.repositories.PCRepository;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CampaignServiceTest {

    @Mock
    private CampaignRepository campaignRepository;
    @Mock
    private PCRepository pcRepository;
    @Mock
    private PCService pcService;

    @InjectMocks
    private CampaignService campaignService;

    private UUID dmId;
    private UUID strangerId;
    private UUID playerId;
    private Campaign campaign;

    @BeforeEach
    void setUp() {
        dmId = UUID.randomUUID();
        strangerId = UUID.randomUUID();
        playerId = UUID.randomUUID();

        campaign = new Campaign();
        campaign.setId(1L);
        campaign.setName("The Veiled Compass");
        campaign.setDmUserId(dmId);
        campaign.setInviteCode("ABC234");
    }

    // --- createCampaign ---

    @Test
    void createCampaign_generatesInviteCode_persistsAndReturns() {
        when(campaignRepository.findByInviteCode(anyString())).thenReturn(Optional.empty());
        when(campaignRepository.saveAndFlush(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));

        Campaign fresh = new Campaign();
        fresh.setName("New Table");

        Campaign result = campaignService.createCampaign(fresh);

        assertThat(result.getInviteCode()).isNotBlank();
        assertThat(result.getInviteCode()).hasSize(6);
        verify(campaignRepository).saveAndFlush(fresh);
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

    @Test
    void findById_throws404_whenNotFound() {
        when(campaignRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> campaignService.findById(99L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(404));
    }

    // --- updateCampaign / deleteCampaign (ownership) ---

    @Test
    void updateCampaign_preservesDmOwnership_whenOwner() {
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));

        Campaign incoming = new Campaign();
        incoming.setId(1L);
        incoming.setName("Renamed");

        Campaign result = campaignService.updateCampaign(incoming, dmId);

        assertThat(result.getDmUserId()).isEqualTo(dmId);
    }

    @Test
    void deleteCampaign_throws403_whenNotOwner() {
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));
        assertThatThrownBy(() -> campaignService.deleteCampaign(1L, strangerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(403));
        verify(campaignRepository, never()).deleteById(any());
    }

    // --- joinByCode ---

    @Test
    void joinByCode_bindsOwnedPc_whenCodeValid() {
        PC pc = pcOwnedBy(playerId, 7L);
        when(campaignRepository.findByInviteCode("ABC234")).thenReturn(Optional.of(campaign));
        when(pcService.findPCByIdForUser(7L, playerId)).thenReturn(pc);
        when(pcService.updatePC(pc, playerId)).thenAnswer(inv -> inv.getArgument(0));

        PC result = campaignService.joinByCode("ABC234", 7L, playerId);

        assertThat(result.getCampaignId()).isEqualTo(1L);
        verify(pcService).updatePC(pc, playerId);
    }

    @Test
    void joinByCode_throws404_whenCodeUnknown() {
        when(campaignRepository.findByInviteCode("ZZZZZZ")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> campaignService.joinByCode("ZZZZZZ", 7L, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(404));
        verify(pcService, never()).updatePC(any(), any());
    }

    @Test
    void joinByCode_propagates403_whenPcNotOwned() {
        when(campaignRepository.findByInviteCode("ABC234")).thenReturn(Optional.of(campaign));
        when(pcService.findPCByIdForUser(7L, strangerId))
                .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "Access denied"));

        assertThatThrownBy(() -> campaignService.joinByCode("ABC234", 7L, strangerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(403));
    }

    // --- getMembers (cross-user authz) ---

    @Test
    void getMembers_allowed_forDm() {
        PC member = pcOwnedBy(playerId, 7L);
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));
        when(pcRepository.findByCampaignId(1L)).thenReturn(List.of(member));

        List<CampaignMemberView> views = campaignService.getMembers(1L, dmId);

        assertThat(views).hasSize(1);
        assertThat(views.get(0).id()).isEqualTo(7L);
    }

    @Test
    void getMembers_allowed_forMemberOwner_evenIfNotDm() {
        PC member = pcOwnedBy(playerId, 7L);
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));
        when(pcRepository.findByCampaignId(1L)).thenReturn(List.of(member));

        // playerId owns a member PC but is not the DM — still allowed
        List<CampaignMemberView> views = campaignService.getMembers(1L, playerId);

        assertThat(views).hasSize(1);
    }

    @Test
    void getMembers_throws403_forUnrelatedUser() {
        PC member = pcOwnedBy(playerId, 7L);
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));
        when(pcRepository.findByCampaignId(1L)).thenReturn(List.of(member));

        assertThatThrownBy(() -> campaignService.getMembers(1L, strangerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(403));
    }

    private PC pcOwnedBy(UUID owner, Long id) {
        PC pc = new PC();
        pc.setId(id);
        pc.setName("Member " + id);
        pc.setUserId(owner);
        return pc;
    }
}
