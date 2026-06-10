package com.moo.charactermanagerservice.services;

import com.moo.charactermanagerservice.models.Campaign;
import com.moo.charactermanagerservice.repositories.CampaignRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Campaign CRUD scoped to the owning Dungeon Master. Mirrors PCService's
 * ownership model: every read/write of a single campaign asserts the caller is
 * the DM who owns it. (Cross-user member access arrives in Phase 3.)
 */
@Service
public class CampaignService {

    private final CampaignRepository campaignRepository;

    @Autowired
    public CampaignService(CampaignRepository campaignRepository) {
        this.campaignRepository = campaignRepository;
    }

    public Campaign createCampaign(Campaign campaign) {
        return campaignRepository.saveAndFlush(campaign);
    }

    public List<Campaign> findCampaignsForDm(UUID dmUserId) {
        return campaignRepository.findByDmUserId(dmUserId);
    }

    public Campaign findById(Long id) {
        return campaignRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Campaign not found with id " + id));
    }

    public Campaign findByIdForDm(Long id, UUID dmUserId) {
        Campaign campaign = findById(id);
        assertDmOwnership(campaign, dmUserId);
        return campaign;
    }

    public Campaign updateCampaign(Campaign campaign, UUID dmUserId) {
        Campaign existing = findById(campaign.getId());
        assertDmOwnership(existing, dmUserId);
        // dm ownership is immutable — never let the body reassign the DM.
        campaign.setDmUserId(existing.getDmUserId());
        return campaignRepository.save(campaign);
    }

    public void deleteCampaign(Long id, UUID dmUserId) {
        Campaign campaign = findById(id);
        assertDmOwnership(campaign, dmUserId);
        campaignRepository.deleteById(id);
    }

    private void assertDmOwnership(Campaign campaign, UUID dmUserId) {
        if (!dmUserId.equals(campaign.getDmUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
    }
}
