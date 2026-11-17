package com.moo.charactermanagerservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moo.charactermanagerservice.dto.FollowerRequest;
import com.moo.charactermanagerservice.dto.FollowerView;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.models.PcActivityType;
import com.moo.charactermanagerservice.models.PcFollower;
import com.moo.charactermanagerservice.repositories.PcFollowerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Followers (V42): companions the campaign's DM grants to a character, each
 * with its own stat block that the client renders as a cut-down sheet.
 *
 * <p>Authority: only the DM of the PC's campaign grants and dismisses a
 * follower; the owning player and that DM may both read the list and update a
 * follower (HP, spell slots, gear change as it adventures). The stat block is
 * an opaque JSON object — validated only for being an object of sane size.
 */
@Service
public class PcFollowerService {

    /** A character's follower cap — generous for play, bounded against abuse. */
    static final int MAX_FOLLOWERS_PER_PC = 20;
    static final int MAX_NAME_LENGTH = 100;
    /** Serialized stat-block cap (characters). A full caster sheet is ~10–20k. */
    static final int MAX_STAT_BLOCK_CHARS = 100_000;

    private final PCService pcService;
    private final PcFollowerRepository followerRepository;
    private final PcActivityLogService activityLogService;
    private final PcJsonColumns json;

    @Autowired
    public PcFollowerService(PCService pcService, PcFollowerRepository followerRepository,
                             PcActivityLogService activityLogService, ObjectMapper objectMapper) {
        this.pcService = pcService;
        this.followerRepository = followerRepository;
        this.activityLogService = activityLogService;
        this.json = new PcJsonColumns(objectMapper);
    }

    /** A character's followers, oldest grant first — owner or the campaign's DM. */
    public List<FollowerView> followersFor(Long pcId, UUID userId) {
        assertOwnerOrCampaignDm(pcId, userId);
        return followerRepository.findByPcIdOrderByCreatedAtAscIdAsc(pcId).stream()
                .map(this::toView)
                .toList();
    }

    /** The DM of the PC's campaign grants it a follower. */
    @Transactional
    public FollowerView grant(Long pcId, FollowerRequest request, UUID dmUserId) {
        pcService.findPCByIdForDm(pcId, dmUserId); // 403 unless the caller runs the PC's campaign
        String name = requireName(request == null ? null : request.name());
        String statBlock = requireStatBlock(request == null ? null : request.statBlock());
        if (followerRepository.countByPcId(pcId) >= MAX_FOLLOWERS_PER_PC) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A character can have at most " + MAX_FOLLOWERS_PER_PC + " followers");
        }

        PcFollower follower = new PcFollower();
        follower.setPcId(pcId);
        follower.setName(name);
        follower.setStatBlock(statBlock);
        follower.setGrantedByUserId(dmUserId);
        PcFollower saved = followerRepository.save(follower);

        activityLogService.log(pcId, PcActivityType.DM_EDIT, "DM granted a follower: " + name, dmUserId);
        return toView(saved);
    }

    /**
     * Update a follower's name and/or stat block — the owning player or the
     * campaign's DM. A null field keeps the stored value, so a body carrying
     * only the stat block (the common sheet-edit case) never renames it.
     */
    @Transactional
    public FollowerView update(Long pcId, Long followerId, FollowerRequest request, UUID userId) {
        assertOwnerOrCampaignDm(pcId, userId);
        PcFollower follower = findFollower(pcId, followerId);
        if (request == null || (request.name() == null && request.statBlock() == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name or statBlock is required");
        }
        if (request.name() != null) {
            follower.setName(requireName(request.name()));
        }
        if (request.statBlock() != null) {
            follower.setStatBlock(requireStatBlock(request.statBlock()));
        }
        return toView(followerRepository.save(follower));
    }

    /** The DM of the PC's campaign dismisses (permanently removes) a follower. */
    @Transactional
    public void dismiss(Long pcId, Long followerId, UUID dmUserId) {
        pcService.findPCByIdForDm(pcId, dmUserId);
        PcFollower follower = findFollower(pcId, followerId);
        followerRepository.delete(follower);
        activityLogService.log(pcId, PcActivityType.DM_EDIT,
                "DM dismissed the follower " + follower.getName(), dmUserId);
    }

    // --- helpers -----------------------------------------------------------

    /** Owner passes outright; anyone else must be the DM of the PC's campaign. */
    private void assertOwnerOrCampaignDm(Long pcId, UUID userId) {
        PC pc = pcService.findPCById(pcId);
        if (!userId.equals(pc.getUserId())) {
            pcService.findPCByIdForDm(pcId, userId);
        }
    }

    /** The follower, but only if it belongs to this PC — otherwise 404 (no leak across PCs). */
    private PcFollower findFollower(Long pcId, Long followerId) {
        return followerRepository.findById(followerId)
                .filter(f -> pcId.equals(f.getPcId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Follower not found"));
    }

    private String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Follower name must not be blank");
        }
        String trimmed = name.trim();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Follower name must be at most " + MAX_NAME_LENGTH + " characters");
        }
        return trimmed;
    }

    private String requireStatBlock(Map<String, Object> statBlock) {
        if (statBlock == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "statBlock must be a JSON object");
        }
        String serialized = json.writeObject(statBlock);
        if (serialized.length() > MAX_STAT_BLOCK_CHARS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stat block is too large");
        }
        return serialized;
    }

    private FollowerView toView(PcFollower follower) {
        return new FollowerView(follower.getId(), follower.getPcId(), follower.getName(),
                json.parseObject(follower.getStatBlock()), follower.getCreatedAt());
    }
}
