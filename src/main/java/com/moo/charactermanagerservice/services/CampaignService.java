package com.moo.charactermanagerservice.services;

import com.moo.charactermanagerservice.dto.CampaignMemberView;
import com.moo.charactermanagerservice.models.Campaign;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.repositories.CampaignRepository;
import com.moo.charactermanagerservice.repositories.PCRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;

/**
 * Campaign CRUD scoped to the owning Dungeon Master. Mirrors PCService's
 * ownership model: every read/write of a single campaign asserts the caller is
 * the DM who owns it. (Cross-user member access arrives in Phase 3.)
 */
@Service
public class CampaignService {

    // Invite-code alphabet: no 0/O/1/I to avoid transcription errors.
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CampaignRepository campaignRepository;
    private final PCRepository pcRepository;
    private final PCService pcService;

    @Autowired
    public CampaignService(CampaignRepository campaignRepository,
                           PCRepository pcRepository,
                           PCService pcService) {
        this.campaignRepository = campaignRepository;
        this.pcRepository = pcRepository;
        this.pcService = pcService;
    }

    public Campaign createCampaign(Campaign campaign) {
        campaign.setInviteCode(generateUniqueInviteCode());
        return campaignRepository.saveAndFlush(campaign);
    }

    /**
     * Bind the caller's own character to a campaign via its invite code. The PC
     * owner consents by entering the code; this is the only path by which a PC
     * the DM does not own becomes a campaign member.
     */
    public PC joinByCode(String inviteCode, Long pcId, UUID userId) {
        Campaign campaign = campaignRepository.findByInviteCode(inviteCode)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No campaign found for that invite code"));
        // findPCByIdForUser asserts the caller owns this PC (403 otherwise).
        PC pc = pcService.findPCByIdForUser(pcId, userId);
        pc.setCampaignId(campaign.getId());
        return pcService.updatePC(pc, userId);
    }

    /**
     * Member projections for a campaign. Visible to the owning DM and to any
     * player who owns a member PC; everyone else is denied. Never includes
     * private narrative or campaign secrets.
     */
    public List<CampaignMemberView> getMembers(Long campaignId, UUID userId) {
        Campaign campaign = findById(campaignId);
        List<PC> members = pcRepository.findByCampaignId(campaignId);

        boolean isDm = userId.equals(campaign.getDmUserId());
        boolean isMemberOwner = members.stream().anyMatch(p -> userId.equals(p.getUserId()));
        if (!isDm && !isMemberOwner) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        return members.stream().map(CampaignMemberView::from).toList();
    }

    private String generateUniqueInviteCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String code = randomCode();
            if (campaignRepository.findByInviteCode(code).isEmpty()) {
                return code;
            }
        }
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "Could not allocate a unique invite code");
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        }
        return sb.toString();
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
