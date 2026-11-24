package com.moo.charactermanagerservice.controllers;

import com.moo.charactermanagerservice.dto.FollowerRequest;
import com.moo.charactermanagerservice.dto.FollowerView;
import com.moo.charactermanagerservice.dto.User;
import com.moo.charactermanagerservice.services.PcFollowerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * A character's followers (V43). The campaign's DM grants and dismisses them;
 * the owning player and that DM read the list and update a follower's sheet.
 * A summon or a familiar (V64 {@code kind}) is the player's own: the owning
 * player, or that DM, creates and dismisses it.
 */
@RestController
@RequestMapping("/api/v1/pc/{pcId}/followers")
public class PcFollowerController {

    @Autowired
    private PcFollowerService followerService;

    /** A character's followers, oldest grant first — owner or the campaign's DM. */
    @GetMapping
    public ResponseEntity<List<FollowerView>> getFollowers(@PathVariable Long pcId,
                                                           Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(followerService.followersFor(pcId, user.getUuid()));
    }

    /**
     * Create a follower (name + stat block + optional kind). The campaign's DM
     * grants one (kind DM_GRANT, the default); the owning player or that DM adds
     * a SUMMON or a FAMILIAR.
     */
    @PostMapping
    public ResponseEntity<FollowerView> grantFollower(@PathVariable Long pcId,
                                                      Authentication authentication,
                                                      @RequestBody FollowerRequest request) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(followerService.grant(pcId, request, user.getUuid()));
    }

    /** Update a follower's name and/or stat block — owner or the campaign's DM. */
    @PutMapping("/{followerId}")
    public ResponseEntity<FollowerView> updateFollower(@PathVariable Long pcId,
                                                       @PathVariable Long followerId,
                                                       Authentication authentication,
                                                       @RequestBody FollowerRequest request) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(followerService.update(pcId, followerId, request, user.getUuid()));
    }

    /**
     * Dismiss (permanently remove) a follower: the campaign's DM any of them,
     * the owning player only their own SUMMON or FAMILIAR.
     */
    @DeleteMapping("/{followerId}")
    public ResponseEntity<Void> dismissFollower(@PathVariable Long pcId,
                                                @PathVariable Long followerId,
                                                Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        followerService.dismiss(pcId, followerId, user.getUuid());
        return ResponseEntity.noContent().build();
    }
}
