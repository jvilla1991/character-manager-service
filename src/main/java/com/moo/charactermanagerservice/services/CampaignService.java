package com.moo.charactermanagerservice.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moo.charactermanagerservice.dto.CampaignMemberView;
import com.moo.charactermanagerservice.dto.CampaignPreviewView;
import com.moo.charactermanagerservice.dto.CampaignSummaryView;
import com.moo.charactermanagerservice.models.Campaign;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.repositories.CampaignRepository;
import com.moo.charactermanagerservice.repositories.PCRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    private final SlotInventoryConversionService conversionService;
    private final PcJsonColumns json;
    private final ObjectMapper objectMapper;

    @Autowired
    public CampaignService(CampaignRepository campaignRepository,
                           PCRepository pcRepository,
                           PCService pcService,
                           SlotInventoryConversionService conversionService,
                           ObjectMapper objectMapper) {
        this.campaignRepository = campaignRepository;
        this.pcRepository = pcRepository;
        this.pcService = pcService;
        this.conversionService = conversionService;
        this.json = new PcJsonColumns(objectMapper);
        this.objectMapper = objectMapper;
    }

    public Campaign createCampaign(Campaign campaign) {
        // A creation body may define the week up front — validate and normalize
        // it exactly like the week-days endpoint (a bad list is a 400, never a
        // silently broken column).
        if (campaign.getWeekDays() != null && !campaign.getWeekDays().isBlank()) {
            List<String> days = parseWeekDays(campaign);
            if (days == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "weekDays must be a JSON array of weekday names");
            }
            campaign.setWeekDays(weekDaysJson(days));
        }
        campaign.setInviteCode(generateUniqueInviteCode());
        return campaignRepository.saveAndFlush(campaign);
    }

    /**
     * The campaign's defined week as an ordered list of weekday names, or null
     * when the DM never defined one (or the column is malformed) — callers then
     * fall back to the free-text weekday behavior.
     */
    public List<String> parseWeekDays(Campaign campaign) {
        String stored = campaign.getWeekDays();
        if (stored == null || stored.isBlank()) return null;
        try {
            List<String> days = objectMapper.readValue(stored, new TypeReference<List<String>>() {});
            return days == null || days.isEmpty() ? null : days;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * DM defines (or clears) the campaign's week: the ordered weekday names the
     * clock walks on each night → morning rollover. Null or empty clears the
     * definition, returning the campaign to free-text weekdays.
     */
    public Campaign setWeekDays(Long id, List<String> weekDays, UUID dmUserId) {
        Campaign campaign = findById(id);
        assertDmOwnership(campaign, dmUserId);
        campaign.setWeekDays(weekDaysJson(weekDays));
        return campaignRepository.save(campaign);
    }

    /** Validate, trim, and serialize a week definition; null/empty clears it. */
    private String weekDaysJson(List<String> weekDays) {
        if (weekDays == null || weekDays.isEmpty()) return null;
        if (!GameClock.isValidWeekDays(weekDays)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Week requires " + GameClock.MIN_WEEK_DAYS + "-" + GameClock.MAX_WEEK_DAYS
                            + " unique weekday names of at most "
                            + GameClock.MAX_LABEL_LENGTH + " characters each");
        }
        try {
            return objectMapper.writeValueAsString(weekDays.stream().map(String::trim).toList());
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to serialize week days");
        }
    }

    /**
     * Bind the caller's own character to a campaign via its invite code. The PC
     * owner consents by entering the code; this is the only path by which a PC
     * the DM does not own becomes a campaign member.
     *
     * Slot-inventory campaigns additionally require the caller to acknowledge
     * the one-time inventory conversion (the client shows a consent gate);
     * joining without it is a 409 so the gate cannot be bypassed via the API.
     */
    @Transactional
    public PC joinByCode(String inviteCode, Long pcId, Boolean acknowledgeVariantRules, UUID userId) {
        Campaign campaign = campaignRepository.findByInviteCode(inviteCode)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No campaign found for that invite code"));
        // findPCByIdForUser asserts the caller owns this PC (403 otherwise).
        pcService.findPCByIdForUser(pcId, userId);

        boolean slot = slotInventoryEnabled(campaign);
        if (slot && !Boolean.TRUE.equals(acknowledgeVariantRules)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This campaign uses slot-based inventory; joining requires acknowledging the conversion");
        }

        // Lock the row for the read-modify-write of the JSON columns (same
        // discipline as ShopService.purchase — serializes vs concurrent buys).
        PC pc = pcRepository.findByIdForUpdate(pcId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Character not found"));
        pc.setCampaignId(campaign.getId());
        if (slot) {
            conversionService.convert(pc);
        }
        if (isVariantEnabled(campaign, "survivalConditions")) {
            seedSurvivalSupplies(pc);
        }
        return pcRepository.save(pc);
    }

    /**
     * Give a character joining a survival campaign its starting kit (1 ration
     * box, 1 waterskin, 5 rations, 5 water) unless already seeded — the
     * {@code seeded} flag on the survival state prevents a re-grant after the
     * supplies are consumed. Legacy supply lines are container-normalized first.
     */
    private void seedSurvivalSupplies(PC pc) {
        java.util.Map<String, Object> survival = json.parseObject(pc.getSurvival());
        if (Boolean.TRUE.equals(survival.get("seeded"))) return;
        java.util.List<java.util.Map<String, Object>> inventory = json.parse(pc.getInventory());
        SurvivalSupplies.normalize(inventory);
        SurvivalSupplies.seed(inventory);
        survival.put("seeded", true);
        pc.setInventory(json.write(inventory));
        pc.setSurvival(json.writeObject(survival));
    }

    /** True when this campaign has opted into slot-based inventory. */
    private boolean slotInventoryEnabled(Campaign campaign) {
        return isVariantEnabled(campaign, "slotInventory");
    }

    /** True when this campaign has opted into the named variant rule. */
    public boolean isVariantEnabled(Campaign campaign, String key) {
        return Boolean.TRUE.equals(json.parseObject(campaign.getVariantRules()).get(key));
    }

    /**
     * Campaign facts a holder of a valid invite code may see BEFORE joining —
     * powers the consent gate for variant-rule campaigns. Leaks only the name
     * and the variant flags; possession of the code is the invitation.
     */
    public CampaignPreviewView previewByCode(String inviteCode) {
        Campaign campaign = campaignRepository.findByInviteCode(inviteCode)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No campaign found for that invite code"));
        return new CampaignPreviewView(campaign.getName(), json.parseObject(campaign.getVariantRules()));
    }

    /**
     * Campaign header for the DM and for owners of member PCs — lets a player's
     * character sheet learn the campaign's variant rules without the DM-only
     * full payload.
     */
    public CampaignSummaryView getSummary(Long campaignId, UUID userId) {
        Campaign campaign = findById(campaignId);
        assertDmOrMemberOwner(campaign, pcRepository.findByCampaignId(campaignId), userId);
        return new CampaignSummaryView(
                campaign.getId(), campaign.getName(), json.parseObject(campaign.getVariantRules()),
                json.parseObject(campaign.getLocation()));
    }

    /**
     * Member projections for a campaign. Visible to the owning DM and to any
     * player who owns a member PC; everyone else is denied. Never includes
     * private narrative or campaign secrets.
     */
    public List<CampaignMemberView> getMembers(Long campaignId, UUID userId) {
        Campaign campaign = findById(campaignId);
        List<PC> members = pcRepository.findByCampaignId(campaignId);
        assertDmOrMemberOwner(campaign, members, userId);
        return members.stream().map(CampaignMemberView::from).toList();
    }

    private void assertDmOrMemberOwner(Campaign campaign, List<PC> members, UUID userId) {
        boolean isDm = userId.equals(campaign.getDmUserId());
        boolean isMemberOwner = members.stream().anyMatch(p -> userId.equals(p.getUserId()));
        if (!isDm && !isMemberOwner) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
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
        // variant rules are chosen at creation and immutable — a body that omits
        // them (every existing client) must not null the column.
        campaign.setVariantRules(existing.getVariantRules());
        // the game clock is written only by the session time endpoints — a
        // campaign edit must not reset or null it.
        campaign.setGameTime(existing.getGameTime());
        // the week definition has its own endpoint — a body that omits it
        // (every existing client) must not null the column.
        campaign.setWeekDays(existing.getWeekDays());
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
