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
import com.moo.charactermanagerservice.models.SessionStatus;
import com.moo.charactermanagerservice.progression.ClassProgression;
import com.moo.charactermanagerservice.progression.XpThresholds;
import com.moo.charactermanagerservice.repositories.CampaignRepository;
import com.moo.charactermanagerservice.repositories.CombatSessionRepository;
import com.moo.charactermanagerservice.repositories.PCRepository;
import com.moo.charactermanagerservice.repositories.PcNoteRepository;
import com.moo.charactermanagerservice.repositories.SessionParticipantRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class PCService {

    private final PCRepository pcRepository;
    private final LevelUpService levelUpService;
    private final CampaignRepository campaignRepository;
    private final PcNoteRepository pcNoteRepository;
    private final PcActivityLogService activityLogService;
    // The session REPOSITORIES, not SessionService: SessionService already
    // depends on this class, so injecting it here would be a circular bean
    // dependency. See bumpLiveSessionVersion.
    private final CombatSessionRepository sessionRepository;
    private final SessionParticipantRepository participantRepository;

    @Autowired
    public PCService(PCRepository pcRepository, LevelUpService levelUpService,
                     CampaignRepository campaignRepository, PcNoteRepository pcNoteRepository,
                     PcActivityLogService activityLogService,
                     CombatSessionRepository sessionRepository,
                     SessionParticipantRepository participantRepository) {
        this.pcRepository = pcRepository;
        this.levelUpService = levelUpService;
        this.campaignRepository = campaignRepository;
        this.pcNoteRepository = pcNoteRepository;
        this.activityLogService = activityLogService;
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
    }

    public PC addPC(PC pc) {
        // Hero subclasses (e.g. the Death Knight's Oath of the Ebon Blade, the Demon Hunter) are
        // chosen here, at creation, and only for their base class; a specialization needs a hero
        // subclass and must be one of that subclass's values.
        HeroClassRules.validate(pc);
        pc.setLevel((short) 1);
        pc.setRuneWeapon(LongRestRules.normalizeRuneWeapon(pc, null));
        pc.setClassResources(ClassResourceState.normalize(pc, null));
        pc.setCoins(CoinPurse.normalizeJson(pc.getCoins()));
        pc.setStatAdjustments(StatAdjustmentRules.normalizeJson(pc.getStatAdjustments()));
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
        // In a campaign the exhaustion level is the DM's to set (updatePCAsDm):
        // an owner's update keeps the stored level whatever the body says. A
        // character in no campaign has no DM, so its owner still manages it.
        if (existing.getCampaignId() != null) {
            pc.setExhaustion(existing.getExhaustion());
            // Same idea for prepared spells: in a campaign they change during a
            // long rest (or by the DM), so an owner's update keeps the stored
            // flags. Spells that are new in the body keep the flag they bring.
            LongRestRules.keepStoredPreparedFlags(pc, existing);
            // Same for the adjustment lines on calculated numbers ("+1 | Taunka"
            // under AC): in a campaign they are the DM's, so the owner's body
            // never changes them.
            pc.setStatAdjustments(existing.getStatAdjustments());
        } else {
            pc.setStatAdjustments(StatAdjustmentRules.normalizeJson(pc.getStatAdjustments()));
        }
        HeroClassRules.validate(pc);
        HeroClassRules.assertOwnerMayChangeSpecialization(existing, pc);
        pc.setCoins(CoinPurse.normalizeJson(pc.getCoins()));
        return pcRepository.save(pc);
    }

    /**
     * Survival stages are adjusted server-side during live sessions (time
     * advancement, consuming rations); a full-entity update body that omits them
     * must not wipe those changes. Clients adjust survival by sending it — they
     * can never clear it to NULL, which nothing legitimately does.
     *
     * <p>The exhaustion level (2024 PHB, V37) follows the same rule: clients set
     * it by sending a value (0 included); a body carrying NULL — e.g. one built
     * from a projection that lacks the field — preserves the stored level.
     *
     * <p>The adjustment lines on calculated numbers (V57, {@code statAdjustments}) follow the
     * same rule: a body carrying NULL keeps the stored lines; they are cleared by sending an
     * empty object. Who may change them at all is decided by the caller ({@link #updatePC}
     * keeps the stored lines for a character in a campaign).
     *
     * <p>The pending level grant is fully server-owned: only the DM's grant
     * endpoint sets it and only an applied level-up clears it, so a generic
     * update body (even one echoing a stale value) never changes it.
     *
     * <p>Hit dice and the inspiration meter (V38) are fully server-owned too:
     * only the session short-rest/long-rest endpoints move {@code hitDiceUsed},
     * and only the inspiration endpoints move {@code inspirationPips} /
     * {@code heroicInspiration} — a generic update body always keeps the
     * stored values, like the pending level grant.
     *
     * <p>The hero-class specialization (V46, formerly the V45 rune) follows the
     * survival/exhaustion rule while the character is still a hero subclass: a body carrying a
     * NULL specialization (e.g. one built without the field) keeps the stored one. It is changed
     * by sending a value (or via level-up, where the subclass allows it). The same applies to a
     * World of Warcraft class specialization (e.g. a WoW druid's Balance/Feral/...) while the
     * character is still of that class and has no hero subclass — but only a stored value that is
     * one of that class's specializations is kept (so e.g. a Death Knight's "Frost" is dropped,
     * not carried, when the DM turns it into an ordinary paladin).
     *
     * <p>The WoW Hunter's pet family (V51) is server-owned on this path: only
     * {@link #setPetFamily} changes it, so a body's {@code petFamily} — null, missing or a value —
     * is ignored. That is what lets "no pet" be saved: on the dedicated endpoint null always means
     * "clear", while here null could also mean "the body just didn't carry it". The stored family
     * is kept while the character can still keep a pet (a Beast Mastery or Survival hunter with the
     * Beast Master subclass) and dropped otherwise, so leaving Beast Master (or switching to
     * Marksmanship) never 400s on a stale family.
     *
     * <p>The Death Knight's rune weapon (V53) is kept when the body omits it and replaced when
     * the body names a melee weapon on its own sheet; a value that no longer fits is stored as
     * unset instead of failing the save — see {@link LongRestRules#normalizeRuneWeapon}.
     *
     * <p>The species origin (V56: {@code speciesSource}, {@code lineage}, {@code size}) is chosen
     * once, in the create wizard. A body carrying NULL for any of them keeps the stored value
     * while the species itself is unchanged — so a client built before V56, or a body made from a
     * projection without the fields, can't wipe a character's lineage. Changing the species takes
     * the body as sent, NULLs included: a Night Elf turned into a Dwarf does not stay a Night Elf.
     */
    private void preserveServerOwnedColumns(PC incoming, PC existing) {
        if (Objects.equals(incoming.getSpecies(), existing.getSpecies())) {
            if (incoming.getSpeciesSource() == null) incoming.setSpeciesSource(existing.getSpeciesSource());
            if (incoming.getLineage() == null) incoming.setLineage(existing.getLineage());
            if (incoming.getSize() == null) incoming.setSize(existing.getSize());
        }
        boolean carriesSpecialization = ClassProgression.isHeroSubclass(incoming.getSubclass())
                || ClassProgression.isClassSpecialization(incoming.getClazz(), existing.getSpecialization());
        if (incoming.getSpecialization() == null && carriesSpecialization) {
            incoming.setSpecialization(existing.getSpecialization());
        }
        // Runs after the specialization carry-over, so a body omitting both keeps both.
        incoming.setPetFamily(existing.getPetFamily());
        dropPetFamilyIfIneligible(incoming);
        if (incoming.getSurvival() == null) {
            incoming.setSurvival(existing.getSurvival());
        }
        if (incoming.getExhaustion() == null) {
            incoming.setExhaustion(existing.getExhaustion());
        }
        if (incoming.getStatAdjustments() == null) {
            incoming.setStatAdjustments(existing.getStatAdjustments());
        }
        incoming.setPendingLevelGrant(existing.getPendingLevelGrant());
        incoming.setHitDiceUsed(existing.getHitDiceUsed());
        incoming.setInspirationPips(existing.getInspirationPips());
        incoming.setHeroicInspiration(existing.getHeroicInspiration());
        incoming.setRuneWeapon(LongRestRules.normalizeRuneWeapon(incoming, existing));
        // Class resource uses are bookkeeping the player keeps, like spell slots:
        // the body's amounts are taken, cleaned against the rules table (unknown
        // resources dropped, each clamped to 0..max). A body without the field
        // keeps the stored amounts.
        incoming.setClassResources(ClassResourceState.normalize(incoming, existing));
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
     *
     * @param description optional DM-authored log entry that replaces the
     *                     automatic before/after diff; blank/null falls back
     *                     to the diff (see {@link PcActivityLogService#logDmEdit(PC, PC, UUID, String)})
     */
    @Transactional
    public PC updatePCAsDm(PC incoming, String description, UUID dmUserId) {
        PC existing = findPCById(incoming.getId());
        assertCampaignDm(existing, dmUserId);
        PC before = snapshot(existing); // taken BEFORE save() clobbers `existing`
        incoming.setUserId(existing.getUserId());
        incoming.setCampaignId(existing.getCampaignId());
        preserveServerOwnedColumns(incoming, existing);
        HeroClassRules.validate(incoming);
        incoming.setCoins(CoinPurse.normalizeJson(incoming.getCoins()));
        incoming.setStatAdjustments(StatAdjustmentRules.normalizeJson(incoming.getStatAdjustments()));
        PC saved = pcRepository.save(incoming);
        activityLogService.logDmEdit(before, saved, dmUserId, description);
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
        copy.setStatAdjustments(pc.getStatAdjustments());
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
     * Same preview, but for the DM of the campaign this PC belongs to — authorized by
     * campaign-DM ownership like {@link #findPCByIdForDm}, so a DM can drive the level-up
     * flow on a member's sheet.
     */
    public LevelUpPreview previewLevelUpAsDm(Long id, UUID dmUserId) {
        PC pc = findPCByIdForDm(id, dmUserId);
        return levelUpService.preview(pc);
    }

    /**
     * Advance this PC one level and persist. Ownership is enforced and the rules are applied
     * server-side ({@link LevelUpService}); the client supplies only choices (e.g. a subclass
     * selection), never computed stats. {@code @Transactional} keeps the load-modify-save atomic
     * so a level can't be applied twice from a single request.
     *
     * <p>Gated: a level-up is allowed only when the character's XP has crossed
     * the next 2024 PHB threshold ({@link XpThresholds}) OR the campaign's DM
     * granted a pending level-up. Applying the level-up consumes the grant.
     *
     * @param request the player's level-up choices (subclass, ASI), or {@code null} when none
     */
    @Transactional
    public PC levelUpPC(Long id, UUID userId, LevelUpRequest request) {
        PC pc = findPCByIdForUser(id, userId);
        boolean granted = Boolean.TRUE.equals(pc.getPendingLevelGrant());
        boolean earned = XpThresholds.isReadyToLevel(
                pc.getLevel() == null ? 1 : pc.getLevel(), pc.getXp() == null ? 0 : pc.getXp());
        if (!granted && !earned) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Not enough XP to level up — reach the next threshold or ask your DM to grant a level");
        }
        return applyLevelUpAndSave(pc, request, userId, "Leveled up to ");
    }

    /**
     * The DM levels up a campaign member directly — authorized by campaign-DM
     * ownership like {@link #updatePCAsDm}. There is no XP/grant gate: the DM
     * performing the level-up is itself the authorization (the same authority
     * that could grant one and have the player spend it). Any pending grant is
     * consumed so the player can't level a second time off the same award.
     */
    @Transactional
    public PC levelUpPCAsDm(Long id, UUID dmUserId, LevelUpRequest request) {
        PC pc = findPCById(id);
        assertCampaignDm(pc, dmUserId);
        return applyLevelUpAndSave(pc, request, dmUserId, "DM leveled up to ");
    }

    /**
     * Shared tail of the owner and DM level-up paths: unpack the request, apply
     * the rules engine, consume any pending grant, persist, and log the new
     * level (the description prefix distinguishes who performed it).
     *
     * <p>Also wakes the PC's live session ({@link #bumpLiveSessionVersion}): a
     * level-up changes the seated combatant's level and max HP and clears
     * {@code myLevelGrant}, and nothing else on this path touches the session.
     */
    private PC applyLevelUpAndSave(PC pc, LevelUpRequest request, UUID actorUserId,
                                   String logDescriptionPrefix) {
        String subclass = request == null ? null : request.subclass();
        Map<String, Integer> abilityIncreases = request == null ? null : request.abilityIncreases();
        String feat = request == null ? null : request.feat();
        List<Map<String, Object>> newSpells = request == null ? null : request.newSpells();
        HpMode hpMode = request == null || request.hpMode() == null ? HpMode.AVERAGE : request.hpMode();
        String specialization = request == null ? null : request.specialization();
        String fightingStyle = request == null ? null : request.fightingStyle();
        levelUpService.applyLevelUp(pc, subclass, abilityIncreases, feat, newSpells, hpMode,
                specialization, fightingStyle);
        dropPetFamilyIfIneligible(pc); // e.g. a WoW Hunter re-specialized into Marksmanship
        pc.setPendingLevelGrant(false); // the grant (if any) is consumed by this level-up
        PC saved = pcRepository.save(pc);
        activityLogService.log(saved.getId(), PcActivityType.LEVEL_UP,
                logDescriptionPrefix + saved.getLevel(), actorUserId);
        bumpLiveSessionVersion(saved);
        return saved;
    }

    /**
     * DM grants (or revokes) a pending level-up for a campaign member —
     * authorized by campaign-DM ownership like {@link #updatePCAsDm}. The flag
     * lets the player level once without meeting the XP threshold and is
     * cleared when the level-up is applied.
     *
     * <p>An actual change (grant or revoke) also wakes the PC's live session
     * ({@link #bumpLiveSessionVersion}) so the player's next poll carries the
     * new {@code myLevelGrant} — that is what makes the "level up now" prompt
     * appear mid-session. Repeating the current value is a no-op and bumps
     * nothing.
     */
    @Transactional
    public PC setLevelGrant(Long id, boolean granted, UUID dmUserId) {
        PC pc = findPCById(id);
        assertCampaignDm(pc, dmUserId);
        boolean was = Boolean.TRUE.equals(pc.getPendingLevelGrant());
        pc.setPendingLevelGrant(granted);
        PC saved = pcRepository.save(pc);
        if (granted != was) {
            activityLogService.log(saved.getId(), PcActivityType.DM_EDIT,
                    granted ? "DM granted a level-up" : "DM revoked the granted level-up", dmUserId);
            bumpLiveSessionVersion(saved);
        }
        return saved;
    }

    /**
     * Bump the version of the live session this PC is seated in, if any, so
     * the 2s poll's {@code sinceVersion} short-circuit stops answering 204 and
     * every viewer gets a fresh snapshot. For sheet changes made OUTSIDE the
     * session endpoints (those bump the session they were handed) that a live
     * table still needs to see at once.
     *
     * <p>"The live session" is singular on purpose: a PC belongs to one
     * campaign, and a campaign has at most one non-ended session (the V8
     * partial unique index behind
     * {@link CombatSessionRepository#findByCampaignIdAndStatusNot}). Ended
     * sessions are never touched, and neither is a live session the PC has
     * not joined — its snapshot does not include this character.
     *
     * <p>Call it from inside the caller's transaction so the bump commits (or
     * rolls back) together with the PC change it announces.
     */
    private void bumpLiveSessionVersion(PC pc) {
        if (pc.getCampaignId() == null) {
            return;
        }
        sessionRepository.findByCampaignIdAndStatusNot(pc.getCampaignId(), SessionStatus.ENDED)
                .filter(session -> participantRepository
                        .findBySessionIdAndPcId(session.getId(), pc.getId()).isPresent())
                .ifPresent(session -> {
                    session.bumpVersion();
                    sessionRepository.save(session);
                });
    }

    // --- WoW Hunter pet family (V51) ------------------------------------------

    /**
     * Set or clear a WoW Hunter's pet family — the only way it changes after creation (the generic
     * update paths keep the stored value, see {@link #preserveServerOwnedColumns}). The owner may
     * do it, and so may the DM of the PC's campaign. {@code null} or a blank name clears the pet;
     * anything else must be one of {@link ClassProgression#PET_FAMILIES} on a character that may
     * keep a pet (400 otherwise, via {@link HeroClassRules#validatePetFamily}).
     */
    @Transactional
    public PC setPetFamily(Long id, String family, UUID userId) {
        PC pc = findPCById(id);
        if (!userId.equals(pc.getUserId())) {
            assertCampaignDm(pc, userId); // not the owner → must be the campaign's DM
        }
        String next = family == null || family.isBlank() ? null : family.trim();
        HeroClassRules.validatePetFamily(pc.getClazz(), pc.getSubclass(), pc.getSpecialization(), next);
        pc.setPetFamily(next);
        return pcRepository.save(pc);
    }

    /** Clear a stored pet family the character can no longer keep (class/subclass/spec changed). */
    private static void dropPetFamilyIfIneligible(PC pc) {
        if (pc.getPetFamily() != null && !ClassProgression.petFamilyAllowed(
                pc.getClazz(), pc.getSubclass(), pc.getSpecialization())) {
            pc.setPetFamily(null);
        }
    }

    // --- Heroic Inspiration meter (V38) --------------------------------------

    /** Pips needed to fill the meter and convert into Heroic Inspiration. */
    public static final int INSPIRATION_METER_SIZE = 5;

    /**
     * DM sets a campaign member's inspiration meter from the member sheet
     * (out-of-session) — campaign-DM authorized like {@link #updatePCAsDm}.
     * Filling the meter grants Heroic Inspiration instead of storing 5 pips.
     */
    @Transactional
    public PC setInspirationPips(Long id, Integer pips, UUID dmUserId) {
        PC pc = findPCById(id);
        assertCampaignDm(pc, dmUserId);
        applyInspirationValue(pc, pips, dmUserId);
        return pcRepository.save(pc);
    }

    /**
     * Shared meter logic (used here and by the in-session set): store an
     * explicit pip count, so clicking a pip on the sheet can raise OR lower the
     * meter in one action. A request for the full meter
     * ({@value #INSPIRATION_METER_SIZE}) empties it and grants Heroic
     * Inspiration — the meter never rests full. Out-of-range values are clamped
     * rather than rejected, so a stale click can never 400. Mutates {@code pc}
     * and writes the activity log; the caller persists.
     */
    void applyInspirationValue(PC pc, Integer requested, UUID actorUserId) {
        int value = requested == null ? 0 : requested;
        value = Math.max(0, Math.min(INSPIRATION_METER_SIZE, value));

        int current = pc.getInspirationPips() == null ? 0 : pc.getInspirationPips();
        if (value >= INSPIRATION_METER_SIZE) {
            pc.setInspirationPips((short) 0);
            pc.setHeroicInspiration(true);
            activityLogService.log(pc.getId(), PcActivityType.INSPIRATION,
                    "Gained Heroic Inspiration (meter filled)", actorUserId);
            return;
        }

        pc.setInspirationPips((short) value);
        // A click that lands on the current value (no-op) writes no log line.
        if (value != current) {
            activityLogService.log(pc.getId(), PcActivityType.INSPIRATION,
                    "DM set inspiration to " + value + "/" + INSPIRATION_METER_SIZE,
                    actorUserId);
        }
    }

    /**
     * Spend Heroic Inspiration — the owning player (or the campaign's DM on
     * their behalf) clears the badge after using the reroll. 409 when there is
     * none to spend, so a stale double-click can't silently "use" nothing.
     */
    @Transactional
    public PC useHeroicInspiration(Long id, UUID userId) {
        PC pc = findPCById(id);
        if (!userId.equals(pc.getUserId())) {
            assertCampaignDm(pc, userId); // not the owner → must be the campaign's DM
        }
        if (!Boolean.TRUE.equals(pc.getHeroicInspiration())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No Heroic Inspiration to use");
        }
        pc.setHeroicInspiration(false);
        PC saved = pcRepository.save(pc);
        activityLogService.log(saved.getId(), PcActivityType.INSPIRATION,
                "Used Heroic Inspiration", userId);
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
