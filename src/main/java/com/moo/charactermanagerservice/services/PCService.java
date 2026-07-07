package com.moo.charactermanagerservice.services;

import com.moo.charactermanagerservice.dto.HpMode;
import com.moo.charactermanagerservice.dto.LevelUpPreview;
import com.moo.charactermanagerservice.dto.LevelUpRequest;
import com.moo.charactermanagerservice.exceptions.PCNotFoundException;
import com.moo.charactermanagerservice.models.Campaign;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.models.PcActivityLog;
import com.moo.charactermanagerservice.models.PcActivityType;
import com.moo.charactermanagerservice.models.PcNote;
import com.moo.charactermanagerservice.repositories.CampaignRepository;
import com.moo.charactermanagerservice.repositories.PCRepository;
import com.moo.charactermanagerservice.repositories.PcNoteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PCService {

    private final PCRepository pcRepository;
    private final LevelUpService levelUpService;
    private final CampaignRepository campaignRepository;
    private final PcNoteRepository pcNoteRepository;
    private final PcActivityLogService activityLogService;

    @Autowired
    public PCService(PCRepository pcRepository, LevelUpService levelUpService,
                     CampaignRepository campaignRepository, PcNoteRepository pcNoteRepository,
                     PcActivityLogService activityLogService) {
        this.pcRepository = pcRepository;
        this.levelUpService = levelUpService;
        this.campaignRepository = campaignRepository;
        this.pcNoteRepository = pcNoteRepository;
        this.activityLogService = activityLogService;
    }

    public PC addPC(PC pc) {
        pc.setLevel((short) 1);
        return pcRepository.saveAndFlush(pc);
    }

    public PC findPCById(Long id) {
        return pcRepository.findById(id)
                .orElseThrow(() -> new PCNotFoundException("PC not found with id " + id));
    }

    public PC findPCByIdForUser(Long id, UUID userId) {
        PC pc = findPCById(id);
        assertOwnership(pc, userId);
        return pc;
    }

    public List<PC> findAllPCsForUser(UUID userId) {
        return pcRepository.findByUserId(userId);
    }

    public PC updatePC(PC pc, UUID userId) {
        PC existing = findPCById(pc.getId());
        assertOwnership(existing, userId);
        preserveServerOwnedColumns(pc, existing);
        return pcRepository.save(pc);
    }

    /**
     * Survival stages are adjusted server-side during live sessions (time
     * advancement, consuming rations); a full-entity update body that omits them
     * must not wipe those changes. Clients adjust survival by sending it — they
     * can never clear it to NULL, which nothing legitimately does.
     */
    private void preserveServerOwnedColumns(PC incoming, PC existing) {
        if (incoming.getSurvival() == null) {
            incoming.setSurvival(existing.getSurvival());
        }
    }

    /**
     * Load a campaign member's full PC for the Dungeon Master who runs its
     * campaign. Authorized by campaign-DM ownership rather than PC ownership, so
     * the DM can see the complete sheet (not the privacy-limited member
     * projection). 403 if the PC is in no campaign or the caller does not run it.
     */
    public PC findPCByIdForDm(Long id, UUID dmUserId) {
        PC pc = findPCById(id);
        assertCampaignDm(pc, dmUserId);
        return pc;
    }

    /**
     * DM-authorized update of a campaign member's PC. Mirrors
     * {@link com.moo.charactermanagerservice.services.SessionService#applyDamage}'s
     * write-through to the canonical pc row: the DM is authorized at the campaign
     * level, not by PC ownership. The character's owner ({@code userId}) and its
     * campaign binding are immutable on a DM edit — the incoming body cannot
     * reassign them (mirrors {@link CampaignService#updateCampaign}).
     *
     * <p>⚠️ {@code pcRepository.save(incoming)} merges the incoming entity onto
     * the managed {@code existing} instance — after save, {@code existing}
     * holds the NEW values too (same JPA session, same row). A before/after
     * diff for the activity log MUST be computed off a defensive snapshot
     * taken before save() runs; diffing {@code existing} afterward would
     * always see "no change". {@code @Transactional} makes the load-diff-save-log
     * sequence atomic.
     */
    @Transactional
    public PC updatePCAsDm(PC incoming, UUID dmUserId) {
        PC existing = findPCById(incoming.getId());
        assertCampaignDm(existing, dmUserId);
        PC before = snapshot(existing); // taken BEFORE save() clobbers `existing`
        incoming.setUserId(existing.getUserId());
        incoming.setCampaignId(existing.getCampaignId());
        preserveServerOwnedColumns(incoming, existing);
        PC saved = pcRepository.save(incoming);
        activityLogService.logDmEdit(before, saved, dmUserId);
        return saved;
    }

    /**
     * A detached, independent copy of the fields {@link PcActivityLogService}'s
     * diff reads — taken before a save() call that would otherwise merge new
     * values onto the same managed instance and erase the "before" state.
     */
    private PC snapshot(PC pc) {
        PC copy = new PC();
        copy.setId(pc.getId());
        copy.setLevel(pc.getLevel());
        copy.setXp(pc.getXp());
        copy.setHpMax(pc.getHpMax());
        copy.setHpCurrent(pc.getHpCurrent());
        copy.setAc(pc.getAc());
        copy.setAbilityStr(pc.getAbilityStr());
        copy.setAbilityDex(pc.getAbilityDex());
        copy.setAbilityCon(pc.getAbilityCon());
        copy.setAbilityInt(pc.getAbilityInt());
        copy.setAbilityWis(pc.getAbilityWis());
        copy.setAbilityCha(pc.getAbilityCha());
        copy.setCoins(pc.getCoins());
        copy.setInventory(pc.getInventory());
        copy.setFeatures(pc.getFeatures());
        copy.setSpells(pc.getSpells());
        return copy;
    }

    /**
     * Compute, without persisting, the gains of advancing this PC one level. Enforces ownership.
     * Drives the SPA's pre-confirmation preview so the client never computes D&D rules itself.
     */
    public LevelUpPreview previewLevelUp(Long id, UUID userId) {
        PC pc = findPCByIdForUser(id, userId);
        return levelUpService.preview(pc);
    }

    /**
     * Advance this PC one level and persist. Ownership is enforced and the rules are applied
     * server-side ({@link LevelUpService}); the client supplies only choices (e.g. a subclass
     * selection), never computed stats. {@code @Transactional} keeps the load-modify-save atomic
     * so a level can't be applied twice from a single request.
     *
     * @param request the player's level-up choices (subclass, ASI), or {@code null} when none
     */
    @Transactional
    public PC levelUpPC(Long id, UUID userId, LevelUpRequest request) {
        PC pc = findPCByIdForUser(id, userId);
        String subclass = request == null ? null : request.subclass();
        Map<String, Integer> abilityIncreases = request == null ? null : request.abilityIncreases();
        String feat = request == null ? null : request.feat();
        List<Map<String, Object>> newSpells = request == null ? null : request.newSpells();
        HpMode hpMode = request == null || request.hpMode() == null ? HpMode.AVERAGE : request.hpMode();
        levelUpService.applyLevelUp(pc, subclass, abilityIncreases, feat, newSpells, hpMode);
        PC saved = pcRepository.save(pc);
        activityLogService.log(saved.getId(), PcActivityType.LEVEL_UP,
                "Leveled up to " + saved.getLevel(), userId);
        return saved;
    }

    public void deletePC(Long id, UUID userId) {
        PC pc = findPCById(id);
        assertOwnership(pc, userId);
        pcRepository.deleteById(id);
    }

    // --- Per-character session notes ---------------------------------------

    /**
     * The owning player appends a note to their character ("what my character
     * remembers"). Campaign and (optional) session ids are snapshotted so the
     * note stays meaningful history even if the PC later leaves the campaign.
     */
    public PcNote addNote(Long pcId, String body, Long sessionId, UUID userId) {
        PC pc = findPCById(pcId);
        assertOwnership(pc, userId);
        if (body == null || body.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Note body must not be blank");
        }
        PcNote note = new PcNote();
        note.setPcId(pcId);
        note.setCampaignId(pc.getCampaignId());
        note.setSessionId(sessionId);
        note.setAuthorUserId(userId);
        note.setBody(body.trim());
        return pcNoteRepository.save(note);
    }

    /**
     * A character's notes, newest first. Readable by the owning player and by
     * the DM of the campaign the PC currently belongs to (the cross-link view).
     */
    public List<PcNote> notesFor(Long pcId, UUID userId) {
        PC pc = findPCById(pcId);
        if (!userId.equals(pc.getUserId())) {
            assertCampaignDm(pc, userId); // not the owner → must be the campaign's DM
        }
        return pcNoteRepository.findByPcIdOrderByCreatedAtDesc(pcId);
    }

    // --- Per-character activity log -----------------------------------------

    /**
     * A character's latest 10 activity log entries, newest first. Same access
     * rule as {@link #notesFor}: readable by the owning player and by the DM
     * of the campaign the PC currently belongs to.
     */
    public List<PcActivityLog> activityLogFor(Long pcId, UUID userId) {
        PC pc = findPCById(pcId);
        if (!userId.equals(pc.getUserId())) {
            assertCampaignDm(pc, userId); // not the owner → must be the campaign's DM
        }
        return activityLogService.latestFor(pcId);
    }

    private void assertOwnership(PC pc, UUID userId) {
        if (!userId.equals(pc.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
    }

    /**
     * Assert the caller is the DM of the campaign this PC belongs to. A PC bound
     * to no campaign has no DM, so access is denied. (One PC has one campaign; one
     * campaign has one DM — so this single check fully authorizes the edit.)
     */
    private void assertCampaignDm(PC pc, UUID dmUserId) {
        Long campaignId = pc.getCampaignId();
        if (campaignId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Character is not part of a campaign");
        }
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied"));
        if (!dmUserId.equals(campaign.getDmUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
    }
}
