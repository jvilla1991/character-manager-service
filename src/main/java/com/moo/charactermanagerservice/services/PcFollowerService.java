package com.moo.charactermanagerservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moo.charactermanagerservice.dto.FollowerRequest;
import com.moo.charactermanagerservice.dto.FollowerView;
import com.moo.charactermanagerservice.models.FollowerKind;
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
import java.util.stream.Collectors;

/**
 * Followers (V43): a character's companions, each with its own stat block
 * that the client renders as a cut-down sheet.
 *
 * <p>Authority depends on the follower's kind (V64, {@link FollowerKind}). A
 * {@code DM_GRANT} is the DM's: only the DM of the PC's campaign grants and
 * dismisses it. A {@code SUMMON} or a {@code FAMILIAR} is the player's own —
 * a spell of theirs made it — so the owning player (in a campaign or not) or
 * that DM creates and dismisses it. A character has one familiar: creating a
 * new one replaces the old. The owning player and the DM may both read the
 * list and update any follower (HP, spell slots, gear change as it adventures).
 * The stat block is an opaque JSON object — validated only for being an object
 * of sane size.
 *
 * <p>A {@code COMPANION} is the server's own: it comes and goes with the
 * feature that gives it and its stat block is worked out from its character
 * (a Vestige Patron's vestige). So nothing here creates one, nobody
 * dismisses one — not the DM either — and an update takes
 * only its name, its current and temporary Hit Points and its conditions
 * ({@link CompanionVitals}); whatever else the request carries is left unread.
 *
 * <p>A follower rides in the live session snapshot under its owner (see
 * {@code FollowerCombatantView}), so every write here also wakes the PC's live
 * session — otherwise the 2s poll would keep answering "nothing changed" and
 * the table would look at a stale follower until something else happened.
 */
@Service
public class PcFollowerService {

    /** A character's follower cap — generous for play, bounded against abuse. */
    static final int MAX_FOLLOWERS_PER_PC = 20;
    static final int MAX_NAME_LENGTH = 100;
    /** Serialized stat-block cap (characters). A full caster sheet is ~10–20k. */
    static final int MAX_STAT_BLOCK_CHARS = 100_000;
    /** What a request to dismiss a companion is answered with. */
    static final String COMPANION_NOT_DISMISSED =
            "A companion cannot be dismissed. It stays for as long as its character has the feature that gives it";

    private final PCService pcService;
    private final PcFollowerRepository followerRepository;
    private final PcActivityLogService activityLogService;
    private final CombatLogService combatLogService;
    private final PcJsonColumns json;

    @Autowired
    public PcFollowerService(PCService pcService, PcFollowerRepository followerRepository,
                             PcActivityLogService activityLogService, CombatLogService combatLogService,
                             ObjectMapper objectMapper) {
        this.pcService = pcService;
        this.followerRepository = followerRepository;
        this.activityLogService = activityLogService;
        this.combatLogService = combatLogService;
        this.json = new PcJsonColumns(objectMapper);
    }

    /** A character's followers, oldest grant first — owner or the campaign's DM. */
    public List<FollowerView> followersFor(Long pcId, UUID userId) {
        assertOwnerOrCampaignDm(pcId, userId);
        return followerRepository.findByPcIdOrderByCreatedAtAscIdAsc(pcId).stream()
                .map(this::toView)
                .toList();
    }

    /**
     * Create a follower. The request's kind decides who may: a {@code DM_GRANT}
     * (also what a missing kind means) is granted by the DM of the PC's campaign
     * alone; a {@code SUMMON} or a {@code FAMILIAR} by the owning player or that
     * DM. Any other kind is a 400.
     *
     * <p>A new familiar replaces the character's current one in the same
     * transaction, and the replaced one does not count toward the cap.
     */
    @Transactional
    public FollowerView grant(Long pcId, FollowerRequest request, UUID userId) {
        FollowerKind kind = requireCreatableKind(request == null ? null : request.kind());
        PC pc = kind.playerManaged()
                ? assertOwnerOrCampaignDm(pcId, userId)
                : pcService.findPCByIdForDm(pcId, userId); // 403 unless the caller runs the PC's campaign
        String name = requireName(request == null ? null : request.name());
        String statBlock = requireStatBlock(request == null ? null : request.statBlock());
        List<PcFollower> replaced = kind == FollowerKind.FAMILIAR
                ? followerRepository.findByPcIdAndKind(pcId, FollowerKind.FAMILIAR)
                : List.of();
        if (followerRepository.countByPcId(pcId) - replaced.size() >= MAX_FOLLOWERS_PER_PC) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A character can have at most " + MAX_FOLLOWERS_PER_PC + " followers");
        }
        if (!replaced.isEmpty()) {
            followerRepository.deleteAll(replaced);
        }

        PcFollower follower = new PcFollower();
        follower.setPcId(pcId);
        follower.setName(name);
        follower.setStatBlock(statBlock);
        follower.setGrantedByUserId(userId);
        follower.setKind(kind);
        PcFollower saved = followerRepository.save(follower);

        logCreated(pc, saved, replaced, userId);
        pcService.bumpLiveSessionVersion(pc);
        return toView(saved);
    }

    /**
     * Update a follower's name and/or stat block — the owning player or the
     * campaign's DM. A null field keeps the stored value, so a body carrying
     * only the stat block (the common sheet-edit case) never renames it. The
     * kind is not part of an update: whatever the body says, it stays.
     *
     * <p>Of a {@code COMPANION}'s stat block only the current Hit Points, the
     * temporary Hit Points and the conditions are taken; the rest of the body's
     * block is ignored, not refused, so the sheet can send what it holds.
     */
    @Transactional
    public FollowerView update(Long pcId, Long followerId, FollowerRequest request, UUID userId) {
        PC pc = assertOwnerOrCampaignDm(pcId, userId);
        PcFollower follower = findFollower(pcId, followerId);
        if (request == null || (request.name() == null && request.statBlock() == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name or statBlock is required");
        }
        if (request.name() != null) {
            follower.setName(requireName(request.name()));
        }
        if (request.statBlock() != null) {
            CombatLogService.Vitals before =
                    combatLogService.vitalsOfStatBlock(json.parseObject(follower.getStatBlock()));
            follower.setStatBlock(kindOf(follower) == FollowerKind.COMPANION
                    ? json.writeObject(CompanionVitals.edited(
                            json.parseObject(follower.getStatBlock()), request.statBlock()))
                    : requireStatBlock(request.statBlock()));
            logSheetVitals(pc, follower, before, userId);
        }
        PcFollower saved = followerRepository.save(follower);
        pcService.bumpLiveSessionVersion(pc);
        return toView(saved);
    }

    /**
     * Dismiss (permanently remove) a follower. The DM of the PC's campaign may
     * dismiss any of them; the owning player only one of their own — a
     * {@code SUMMON} or a {@code FAMILIAR}. A {@code COMPANION} is nobody's to
     * dismiss: 403 for the owner and the DM alike.
     */
    @Transactional
    public void dismiss(Long pcId, Long followerId, UUID userId) {
        PC pc = assertOwnerOrCampaignDm(pcId, userId); // a stranger is refused before any lookup
        PcFollower follower = findFollower(pcId, followerId);
        if (kindOf(follower) == FollowerKind.COMPANION) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, COMPANION_NOT_DISMISSED);
        }
        if (!kindOf(follower).playerManaged()) {
            pcService.findPCByIdForDm(pcId, userId); // the DM's to dismiss: 403 for the owning player
        }
        followerRepository.delete(follower);
        pcService.bumpLiveSessionVersion(pc);
        logDismissed(pc, follower, userId);
    }

    // --- helpers -----------------------------------------------------------

    /**
     * The kind a create asks for: none means a DM grant. {@code COMPANION} and
     * anything unknown are refused — a companion is never created by a request.
     */
    private FollowerKind requireCreatableKind(String raw) {
        if (raw == null || raw.isBlank()) {
            return FollowerKind.DM_GRANT;
        }
        String wanted = raw.trim();
        for (FollowerKind kind : List.of(FollowerKind.DM_GRANT, FollowerKind.SUMMON, FollowerKind.FAMILIAR)) {
            if (kind.name().equals(wanted)) {
                return kind;
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Follower kind must be DM_GRANT, SUMMON or FAMILIAR");
    }

    /** A follower's kind; a row that somehow carries none is a DM grant, as before V64. */
    private static FollowerKind kindOf(PcFollower follower) {
        return follower.getKind() == null ? FollowerKind.DM_GRANT : follower.getKind();
    }

    /** "familiar" or "follower" — what the activity log calls a follower of this kind. */
    private static String noun(FollowerKind kind) {
        return kind == FollowerKind.FAMILIAR ? "familiar" : "follower";
    }

    /**
     * Activity line for a new follower. A grant stays the DM edit it always
     * was. A summon or a familiar says who made it: the player's own is a
     * FOLLOWER line with no "DM" in it, one the DM added on their behalf a DM
     * edit. A replaced familiar is named in the same line.
     */
    private void logCreated(PC pc, PcFollower created, List<PcFollower> replaced, UUID userId) {
        FollowerKind kind = kindOf(created);
        if (kind == FollowerKind.DM_GRANT) {
            activityLogService.log(pc.getId(), PcActivityType.DM_EDIT,
                    "DM granted a follower: " + created.getName(), userId);
            return;
        }
        boolean byOwner = userId.equals(pc.getUserId());
        String line = (byOwner ? "Summoned a " : "DM summoned a ") + noun(kind) + ": " + created.getName();
        if (!replaced.isEmpty()) {
            line += " (replaces " + replaced.stream().map(PcFollower::getName)
                    .collect(Collectors.joining(", ")) + ")";
        }
        activityLogService.log(pc.getId(), byOwner ? PcActivityType.FOLLOWER : PcActivityType.DM_EDIT,
                line, userId);
    }

    /** Activity line for a dismissal: the player's own is a FOLLOWER line, the DM's a DM edit. */
    private void logDismissed(PC pc, PcFollower dismissed, UUID userId) {
        FollowerKind kind = kindOf(dismissed);
        boolean byOwner = kind.playerManaged() && userId.equals(pc.getUserId());
        activityLogService.log(pc.getId(), byOwner ? PcActivityType.FOLLOWER : PcActivityType.DM_EDIT,
                (byOwner ? "Dismissed the " : "DM dismissed the ") + noun(kind) + " " + dismissed.getName(),
                userId);
    }

    /**
     * A follower's sheet save changed its hit points or conditions: if its
     * owner sits in a live session, add the change to that session's combat
     * log. The caller bumps the session version (every follower write does).
     */
    private void logSheetVitals(PC owner, PcFollower follower, CombatLogService.Vitals before, UUID userId) {
        CombatLogService.Vitals after =
                combatLogService.vitalsOfStatBlock(json.parseObject(follower.getStatBlock()));
        if (!combatLogService.differ(before, after)) {
            return;
        }
        boolean byDm = !userId.equals(owner.getUserId());
        pcService.liveSessionFor(owner).ifPresent(session -> combatLogService.logSheetChange(session,
                CombatLogService.followerSubject(follower.getName(), owner.getName()),
                before, after, byDm, userId));
    }

    /** Owner passes outright; anyone else must be the DM of the PC's campaign. */
    private PC assertOwnerOrCampaignDm(Long pcId, UUID userId) {
        PC pc = pcService.findPCById(pcId);
        if (!userId.equals(pc.getUserId())) {
            pcService.findPCByIdForDm(pcId, userId);
        }
        return pc;
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
                json.parseObject(follower.getStatBlock()), follower.getCreatedAt(), kindOf(follower).name());
    }
}
