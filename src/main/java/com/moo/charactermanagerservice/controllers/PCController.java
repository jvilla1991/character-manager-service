package com.moo.charactermanagerservice.controllers;

import com.moo.charactermanagerservice.dto.AddPcNoteRequest;
import com.moo.charactermanagerservice.dto.LevelGrantRequest;
import com.moo.charactermanagerservice.dto.LevelUpPreview;
import com.moo.charactermanagerservice.dto.LevelUpRequest;
import com.moo.charactermanagerservice.dto.RestoreSpellSlotsRequest;
import com.moo.charactermanagerservice.dto.SetInspirationRequest;
import com.moo.charactermanagerservice.dto.SetPetRequest;
import com.moo.charactermanagerservice.dto.SpellSlotResult;
import com.moo.charactermanagerservice.dto.SpendSpellSlotRequest;
import com.moo.charactermanagerservice.dto.UpdatePcAsDmRequest;
import com.moo.charactermanagerservice.dto.User;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.models.PcActivityLog;
import com.moo.charactermanagerservice.models.PcNote;
import com.moo.charactermanagerservice.services.PCService;
import com.moo.charactermanagerservice.validation.ValidationGroups.OnCreate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/pc")
public class PCController {

    @Autowired
    private PCService pcService;

    @GetMapping("/all")
    public ResponseEntity<List<PC>> getAllPCsForUser(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(pcService.findAllPCsForUser(user.getUuid()));
    }

    @GetMapping("/find/{id}")
    public ResponseEntity<PC> getPC(@PathVariable Long id, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(pcService.findPCByIdForUser(id, user.getUuid()));
    }

    @PostMapping("/add")
    public ResponseEntity<PC> createPC(Authentication authentication,
                                       @Validated(OnCreate.class) @RequestBody PC pc) {
        User user = (User) authentication.getPrincipal();
        pc.setUserId(user.getUuid());
        pc.setPlayerName(user.getFirstName());
        return ResponseEntity.status(HttpStatus.CREATED).body(pcService.addPC(pc));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PC> updatePC(@PathVariable Long id, Authentication authentication,
                                       @RequestBody PC pc) {
        User user = (User) authentication.getPrincipal();
        pc.setId(id);
        return ResponseEntity.ok(pcService.updatePC(pc, user.getUuid()));
    }

    /**
     * Full sheet of a campaign member, for the DM who runs its campaign.
     * Authorized by campaign-DM ownership (not PC ownership), so the DM can edit
     * the complete character rather than the privacy-limited member projection.
     */
    @GetMapping("/{id}/as-dm")
    public ResponseEntity<PC> getPCAsDm(@PathVariable Long id, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(pcService.findPCByIdForDm(id, user.getUuid()));
    }

    /**
     * DM-authorized update of a campaign member's PC (campaign-DM ownership).
     * The body wraps the PC alongside an optional DM-authored log description
     * that replaces the automatic before/after diff — see
     * {@link UpdatePcAsDmRequest}.
     */
    @PutMapping("/{id}/as-dm")
    public ResponseEntity<PC> updatePCAsDm(@PathVariable Long id, Authentication authentication,
                                           @RequestBody UpdatePcAsDmRequest request) {
        if (request.pc() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "pc is required");
        }
        User user = (User) authentication.getPrincipal();
        PC pc = request.pc();
        pc.setId(id);
        return ResponseEntity.ok(pcService.updatePCAsDm(pc, request.description(), user.getUuid()));
    }

    /**
     * DM grants (or revokes) a pending level-up for a campaign member —
     * campaign-DM authorized like the as-dm endpoints. The player may then
     * level once without meeting the XP threshold; applying the level-up
     * consumes the grant.
     */
    @PutMapping("/{id}/level-grant")
    public ResponseEntity<PC> setLevelGrant(@PathVariable Long id, Authentication authentication,
                                            @RequestBody LevelGrantRequest request) {
        if (request == null || request.granted() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "granted is required");
        }
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(pcService.setLevelGrant(id, request.granted(), user.getUuid()));
    }

    @GetMapping("/{id}/level-up/preview")
    public ResponseEntity<LevelUpPreview> previewLevelUp(@PathVariable Long id, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(pcService.previewLevelUp(id, user.getUuid()));
    }

    @PostMapping("/{id}/level-up")
    public ResponseEntity<PC> levelUp(@PathVariable Long id, Authentication authentication,
                                      @RequestBody(required = false) LevelUpRequest request) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(pcService.levelUpPC(id, user.getUuid(), request));
    }

    /**
     * Level-up preview of a campaign member, for the DM who runs its campaign —
     * campaign-DM authorized like the as-dm endpoints.
     */
    @GetMapping("/{id}/level-up/preview/as-dm")
    public ResponseEntity<LevelUpPreview> previewLevelUpAsDm(@PathVariable Long id,
                                                             Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(pcService.previewLevelUpAsDm(id, user.getUuid()));
    }

    /**
     * The DM levels up a campaign member directly — campaign-DM authorized like
     * the as-dm endpoints, and not gated on XP or a pending grant (the DM's
     * action is the authorization).
     */
    @PostMapping("/{id}/level-up/as-dm")
    public ResponseEntity<PC> levelUpAsDm(@PathVariable Long id, Authentication authentication,
                                          @RequestBody(required = false) LevelUpRequest request) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(pcService.levelUpPCAsDm(id, user.getUuid(), request));
    }

    /**
     * DM sets a campaign member's inspiration meter from the member sheet
     * (out-of-session) — campaign-DM authorized like the as-dm endpoints. The
     * sheet's pips are clickable, so this both raises and lowers the meter;
     * asking for a full meter grants Heroic Inspiration and empties it.
     */
    @PutMapping("/{id}/inspiration")
    public ResponseEntity<PC> setInspirationPips(@PathVariable Long id,
                                                 @RequestBody SetInspirationRequest request,
                                                 Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(pcService.setInspirationPips(id, request.pips(), user.getUuid()));
    }

    /**
     * Set or clear a World of Warcraft Hunter's pet family — the owner or the campaign's DM.
     * {@code {"family": "Wolf"}} sets it; {@code {"family": null}} (or a blank name) clears it.
     * The generic {@code PUT /{id}} ignores {@code petFamily}, so this is the only way to change it.
     */
    @PutMapping("/{id}/pet")
    public ResponseEntity<PC> setPetFamily(@PathVariable Long id,
                                           @RequestBody SetPetRequest request,
                                           Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(pcService.setPetFamily(id, request == null ? null : request.family(),
                user.getUuid()));
    }

    /**
     * Set choices a subclass lets the player make, outside a level-up. The body is a partial
     * choices object; each key present replaces the stored one:
     * {@code {"skills":["Insight"]}}, {@code {"arcaneShots":["Banishing Shot","Seeking Shot"]}},
     * {@code {"vestigeType":"Celestial","vestigeDomain":"Life"}}. Returns the saved character.
     * The generic {@code PUT /{id}} ignores {@code subclassChoices}, so besides a level-up this
     * is the only way to change them.
     *
     * <p>The campaign's DM, or the owner of a character in no campaign, may set any key. The
     * owner of a character in a campaign may always set {@code vestigeType}, and any other key
     * only while it is missing or short of what the level asks for (403 once it is complete).
     * 400 for an empty body, an unknown key, a key the character does not have, or a value the
     * rules refuse.
     */
    @PutMapping("/{id}/subclass-choices")
    public ResponseEntity<PC> setSubclassChoices(@PathVariable Long id,
                                                 @RequestBody(required = false) Map<String, Object> choices,
                                                 Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(pcService.setSubclassChoices(id, choices, user.getUuid()));
    }

    /**
     * Spend Heroic Inspiration — the owning player (or the campaign's DM)
     * clears the badge after the reroll is used. 409 when there is none.
     */
    @PostMapping("/{id}/inspiration/use")
    public ResponseEntity<PC> useHeroicInspiration(@PathVariable Long id,
                                                   Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(pcService.useHeroicInspiration(id, user.getUuid()));
    }

    /**
     * Spend one spell slot by hand — the campaign's DM, or the owner of a
     * character in no campaign (a campaign player gets 403: in a campaign a
     * slot is spent by casting in a live session). Body {@code {"level": 3}}.
     * 400 for a level that is not 1-9, 409 when no slot of that level is free.
     * Returns the character's slots afterwards.
     */
    @PostMapping("/{id}/spell-slots/spend")
    public ResponseEntity<SpellSlotResult> spendSpellSlot(@PathVariable Long id,
                                                          @RequestBody(required = false) SpendSpellSlotRequest request,
                                                          Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(pcService.spendSpellSlot(id, request == null ? null : request.level(),
                user.getUuid()));
    }

    /**
     * Give spell slots back outside a rest — the campaign's DM, or the owner of
     * a character in no campaign (a campaign player gets 403). Body
     * {@code {"level": 3}} restores one slot of that level (409 when none is
     * spent); {@code {"all": true}} restores every slot and never fails on a
     * fully rested character.
     */
    @PostMapping("/{id}/spell-slots/restore")
    public ResponseEntity<SpellSlotResult> restoreSpellSlots(@PathVariable Long id,
                                                             @RequestBody(required = false) RestoreSpellSlotsRequest request,
                                                             Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(pcService.restoreSpellSlots(id,
                request == null ? null : request.level(),
                request != null && Boolean.TRUE.equals(request.all()),
                user.getUuid()));
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Void> deletePC(@PathVariable Long id, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        pcService.deletePC(id, user.getUuid());
        return ResponseEntity.noContent().build();
    }

    /** The owning player appends a session note to their character. */
    @PostMapping("/{id}/notes")
    public ResponseEntity<PcNote> addNote(@PathVariable Long id, Authentication authentication,
                                          @RequestBody AddPcNoteRequest request) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED).body(
                pcService.addNote(id, request.body(), request.sessionId(), user.getUuid()));
    }

    /** A character's notes, newest first — owner or the campaign's DM. */
    @GetMapping("/{id}/notes")
    public ResponseEntity<List<PcNote>> getNotes(@PathVariable Long id, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(pcService.notesFor(id, user.getUuid()));
    }

    /** A character's latest 10 activity log entries, newest first — owner or the campaign's DM. */
    @GetMapping("/{id}/log")
    public ResponseEntity<List<PcActivityLog>> getActivityLog(@PathVariable Long id, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(pcService.activityLogFor(id, user.getUuid()));
    }
}
