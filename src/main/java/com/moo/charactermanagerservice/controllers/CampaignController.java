package com.moo.charactermanagerservice.controllers;

import com.moo.charactermanagerservice.dto.CampaignMemberView;
import com.moo.charactermanagerservice.dto.JoinCampaignRequest;
import com.moo.charactermanagerservice.dto.User;
import com.moo.charactermanagerservice.models.Campaign;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.services.CampaignService;
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
}
