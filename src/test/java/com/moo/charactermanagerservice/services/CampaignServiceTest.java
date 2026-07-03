package com.moo.charactermanagerservice.services;

import com.moo.charactermanagerservice.dto.CampaignMemberView;
import com.moo.charactermanagerservice.models.Campaign;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.repositories.CampaignRepository;
import com.moo.charactermanagerservice.repositories.PCRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    @Mock
    private SlotInventoryConversionService conversionService;

    private CampaignService campaignService;

    private UUID dmId;
    private UUID strangerId;
    private UUID playerId;
    private Campaign campaign;

    @BeforeEach
    void setUp() {
        campaignService = new CampaignService(campaignRepository, pcRepository, pcService,
                conversionService, new com.fasterxml.jackson.databind.ObjectMapper());

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
    void updateCampaign_preservesVariantRules_whenBodyOmitsOrAltersThem() {
        campaign.setVariantRules("{\"slotInventory\":true}");
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));

        Campaign incoming = new Campaign();
        incoming.setId(1L);
        incoming.setName("Renamed");
        incoming.setVariantRules("{\"slotInventory\":false}"); // tampered body

        Campaign result = campaignService.updateCampaign(incoming, dmId);

        assertThat(result.getVariantRules()).isEqualTo("{\"slotInventory\":true}");
    }

    @Test
    void updateCampaign_preservesGameTime_whenBodyOmitsOrAltersIt() {
        campaign.setGameTime("{\"year\":1,\"month\":2,\"day\":3,\"timeOfDay\":\"dusk\"}");
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));

        Campaign incoming = new Campaign();
        incoming.setId(1L);
        incoming.setName("Renamed");
        incoming.setGameTime("{\"year\":999}"); // tampered body — only the session endpoints move time

        Campaign result = campaignService.updateCampaign(incoming, dmId);

        assertThat(result.getGameTime()).isEqualTo("{\"year\":1,\"month\":2,\"day\":3,\"timeOfDay\":\"dusk\"}");
    }

    @Test
    void isVariantEnabled_readsAnyKeyFromTheRulesJson() {
        campaign.setVariantRules("{\"survivalConditions\":true,\"slotInventory\":false}");
        assertThat(campaignService.isVariantEnabled(campaign, "survivalConditions")).isTrue();
        assertThat(campaignService.isVariantEnabled(campaign, "slotInventory")).isFalse();

        campaign.setVariantRules(null);
        assertThat(campaignService.isVariantEnabled(campaign, "survivalConditions")).isFalse();
    }

    @Test
    void createCampaign_passesVariantRulesThrough() {
        when(campaignRepository.findByInviteCode(anyString())).thenReturn(Optional.empty());
        when(campaignRepository.saveAndFlush(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));

        Campaign fresh = new Campaign();
        fresh.setName("Slots & Bulk");
        fresh.setVariantRules("{\"slotInventory\":true}");

        Campaign result = campaignService.createCampaign(fresh);

        assertThat(result.getVariantRules()).isEqualTo("{\"slotInventory\":true}");
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
        when(pcRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(pc)).thenAnswer(inv -> inv.getArgument(0));

        PC result = campaignService.joinByCode("ABC234", 7L, null, playerId);

        assertThat(result.getCampaignId()).isEqualTo(1L);
        verify(pcRepository).findByIdForUpdate(7L); // locked read
        verify(pcRepository).save(pc);
        verify(conversionService, never()).convert(any()); // no variant rules
    }

    @Test
    void joinByCode_throws404_whenCodeUnknown() {
        when(campaignRepository.findByInviteCode("ZZZZZZ")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> campaignService.joinByCode("ZZZZZZ", 7L, null, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(404));
        verify(pcRepository, never()).save(any());
    }

    @Test
    void joinByCode_propagates403_whenPcNotOwned() {
        when(campaignRepository.findByInviteCode("ABC234")).thenReturn(Optional.of(campaign));
        when(pcService.findPCByIdForUser(7L, strangerId))
                .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "Access denied"));

        assertThatThrownBy(() -> campaignService.joinByCode("ABC234", 7L, null, strangerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(403));
    }

    @Test
    void joinByCode_throws409_forSlotCampaign_withoutAcknowledgment() {
        campaign.setVariantRules("{\"slotInventory\":true}");
        PC pc = pcOwnedBy(playerId, 7L);
        when(campaignRepository.findByInviteCode("ABC234")).thenReturn(Optional.of(campaign));
        when(pcService.findPCByIdForUser(7L, playerId)).thenReturn(pc);

        for (Boolean ack : new Boolean[]{null, Boolean.FALSE}) {
            assertThatThrownBy(() -> campaignService.joinByCode("ABC234", 7L, ack, playerId))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(409));
        }
        verify(conversionService, never()).convert(any());
        verify(pcRepository, never()).save(any());
    }

    @Test
    void joinByCode_convertsAndBinds_forSlotCampaign_withAcknowledgment() {
        campaign.setVariantRules("{\"slotInventory\":true}");
        PC pc = pcOwnedBy(playerId, 7L);
        when(campaignRepository.findByInviteCode("ABC234")).thenReturn(Optional.of(campaign));
        when(pcService.findPCByIdForUser(7L, playerId)).thenReturn(pc);
        when(pcRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(pc)).thenAnswer(inv -> inv.getArgument(0));

        PC result = campaignService.joinByCode("ABC234", 7L, true, playerId);

        assertThat(result.getCampaignId()).isEqualTo(1L);
        verify(conversionService).convert(pc);
    }

    @Test
    void joinByCode_ignoresAcknowledgment_forNonVariantCampaign() {
        PC pc = pcOwnedBy(playerId, 7L);
        when(campaignRepository.findByInviteCode("ABC234")).thenReturn(Optional.of(campaign));
        when(pcService.findPCByIdForUser(7L, playerId)).thenReturn(pc);
        when(pcRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(pc)).thenAnswer(inv -> inv.getArgument(0));

        assertThat(campaignService.joinByCode("ABC234", 7L, true, playerId).getCampaignId()).isEqualTo(1L);
        verify(conversionService, never()).convert(any());
    }

    // --- previewByCode (consent gate) ---

    @Test
    void previewByCode_returnsNameAndParsedVariantRules() {
        campaign.setVariantRules("{\"slotInventory\":true}");
        when(campaignRepository.findByInviteCode("ABC234")).thenReturn(Optional.of(campaign));

        var preview = campaignService.previewByCode("ABC234");

        assertThat(preview.name()).isEqualTo("The Veiled Compass");
        assertThat(preview.variantRules()).containsEntry("slotInventory", true);
    }

    @Test
    void previewByCode_returnsEmptyRules_whenColumnNull() {
        when(campaignRepository.findByInviteCode("ABC234")).thenReturn(Optional.of(campaign));
        assertThat(campaignService.previewByCode("ABC234").variantRules()).isEmpty();
    }

    @Test
    void previewByCode_throws404_whenCodeUnknown() {
        when(campaignRepository.findByInviteCode("ZZZZZZ")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> campaignService.previewByCode("ZZZZZZ"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(404));
    }

    // --- getSummary (member-visible header) ---

    @Test
    void getSummary_allowed_forDm_andForMemberOwner() {
        campaign.setVariantRules("{\"slotInventory\":true}");
        PC member = pcOwnedBy(playerId, 7L);
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));
        when(pcRepository.findByCampaignId(1L)).thenReturn(List.of(member));

        assertThat(campaignService.getSummary(1L, dmId).variantRules()).containsEntry("slotInventory", true);
        assertThat(campaignService.getSummary(1L, playerId).name()).isEqualTo("The Veiled Compass");
    }

    @Test
    void getSummary_throws403_forUnrelatedUser() {
        PC member = pcOwnedBy(playerId, 7L);
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));
        when(pcRepository.findByCampaignId(1L)).thenReturn(List.of(member));

        assertThatThrownBy(() -> campaignService.getSummary(1L, strangerId))
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
