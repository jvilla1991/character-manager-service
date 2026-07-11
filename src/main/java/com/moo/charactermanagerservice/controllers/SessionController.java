package com.moo.charactermanagerservice.controllers;

import com.moo.charactermanagerservice.dto.AddEnemyRequest;
import com.moo.charactermanagerservice.dto.AdvanceRequest;
import com.moo.charactermanagerservice.dto.CastResult;
import com.moo.charactermanagerservice.dto.CastSpellRequest;
import com.moo.charactermanagerservice.dto.LongRestRequest;
import com.moo.charactermanagerservice.dto.ConsumeResult;
import com.moo.charactermanagerservice.dto.ConsumeSurvivalRequest;
import com.moo.charactermanagerservice.dto.DamageRequest;
import com.moo.charactermanagerservice.dto.JoinSessionRequest;
import com.moo.charactermanagerservice.dto.LoadEncounterRequest;
import com.moo.charactermanagerservice.dto.LogRollRequest;
import com.moo.charactermanagerservice.dto.SessionStateView;
import com.moo.charactermanagerservice.dto.SetInitiativeRequest;
import com.moo.charactermanagerservice.dto.SetSoundRequest;
import com.moo.charactermanagerservice.dto.SetLocationRequest;
import com.moo.charactermanagerservice.dto.SetTimeRequest;
import com.moo.charactermanagerservice.dto.SetVisibilityRequest;
import com.moo.charactermanagerservice.dto.User;
import com.moo.charactermanagerservice.dto.XpAwardRequest;
import com.moo.charactermanagerservice.dto.XpAwardResult;
import com.moo.charactermanagerservice.services.SessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Live "Session Mode" endpoints. The session backend lives in the same service
 * as PCs so HP/condition edits write through to the canonical character with no
 * cross-service sync. Real-time delivery is short-poll against
 * {@code GET /session/{id}/state} (App Runner is HTTP-only).
 *
 * <p>Auth follows the existing pattern: the principal is the {@link User}
 * resolved by {@code AuthorizationFilter}; the service asserts ownership.
 */
@RestController
@RequestMapping("/api/v1")
public class SessionController {

    @Autowired
    private SessionService sessionService;

    /** DM opens a lobby for one of their campaigns (409 if one is already live). */
    @PostMapping("/campaign/{campaignId}/session")
    public ResponseEntity<SessionStateView> createSession(@PathVariable Long campaignId,
                                                          Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(sessionService.createSession(campaignId, user.getUuid()));
    }

    /**
     * The campaign's current live session (for discovery before joining) — 204 if
     * none. Visible to the DM and any campaign member.
     */
    @GetMapping("/campaign/{campaignId}/session")
    public ResponseEntity<SessionStateView> getActiveSession(@PathVariable Long campaignId,
                                                            Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        SessionStateView state = sessionService.getActiveSessionForCampaign(campaignId, user.getUuid());
        return state == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(state);
    }

    /**
     * Poll snapshot — visible to the DM and to any player who owns a participant.
     * Pass {@code sinceVersion} (the version of the caller's last snapshot) to get
     * 204 instead of the full payload when nothing has changed.
     */
    @GetMapping("/session/{id}/state")
    public ResponseEntity<SessionStateView> getState(@PathVariable Long id,
                                                     @RequestParam(required = false) Long sinceVersion,
                                                     Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        SessionStateView state = sessionService.getState(id, user.getUuid(), sinceVersion);
        return state == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(state);
    }

    /** A player seats one of their own PCs (must be a member of the campaign). */
    @PostMapping("/session/{id}/join")
    public ResponseEntity<SessionStateView> join(@PathVariable Long id,
                                                 @RequestBody JoinSessionRequest request,
                                                 Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(sessionService.joinSession(id, request.pcId(), user.getUuid()));
    }

    /** Remove a combatant — the DM may remove anyone; a player only their own. */
    @DeleteMapping("/session/{id}/participants/{participantId}")
    public ResponseEntity<SessionStateView> removeParticipant(@PathVariable Long id,
                                                             @PathVariable Long participantId,
                                                             Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(sessionService.removeParticipant(id, participantId, user.getUuid()));
    }

    /**
     * Enter a combatant's initiative; the server re-sorts the turn order. DM:
     * anyone, anytime. Player: own combatant only, and never to revise it once
     * the encounter is active.
     */
    @PutMapping("/session/{id}/participants/{participantId}/initiative")
    public ResponseEntity<SessionStateView> setInitiative(@PathVariable Long id,
                                                         @PathVariable Long participantId,
                                                         @RequestBody SetInitiativeRequest request,
                                                         Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(
                sessionService.setInitiative(id, participantId, request.value(), user.getUuid()));
    }

    /** DM damages (positive) or heals (negative) a combatant; PC HP writes through. */
    @PostMapping("/session/{id}/participants/{participantId}/damage")
    public ResponseEntity<SessionStateView> applyDamage(@PathVariable Long id,
                                                       @PathVariable Long participantId,
                                                       @RequestBody DamageRequest request,
                                                       Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(
                sessionService.applyDamage(id, participantId, request.amount(), user.getUuid()));
    }

    /** DM awards XP to a single combatant (PC only); writes through to the character. */
    @PostMapping("/session/{id}/participants/{participantId}/xp")
    public ResponseEntity<XpAwardResult> awardXp(@PathVariable Long id,
                                                 @PathVariable Long participantId,
                                                 @RequestBody XpAwardRequest request,
                                                 Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(
                sessionService.awardXp(id, participantId, request.amount(), user.getUuid()));
    }

    /** DM awards the same XP amount to every seated PC in the session. */
    @PostMapping("/session/{id}/xp")
    public ResponseEntity<XpAwardResult> awardXpToAll(@PathVariable Long id,
                                                      @RequestBody XpAwardRequest request,
                                                      Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(
                sessionService.awardXpToAll(id, request.amount(), user.getUuid()));
    }

    /** DM starts the encounter (LOBBY → ACTIVE); turn points at the top of the order. */
    @PostMapping("/session/{id}/start")
    public ResponseEntity<SessionStateView> startEncounter(@PathVariable Long id,
                                                           Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(sessionService.startEncounter(id, user.getUuid()));
    }

    /**
     * DM ends the encounter (ACTIVE → LOBBY): turn tracking stops, initiative
     * clears for the next encounter; HP/XP changes already persisted stand.
     * The session itself stays open — see {@code /end} for closing the table.
     */
    @PostMapping("/session/{id}/encounter/end")
    public ResponseEntity<SessionStateView> endEncounter(@PathVariable Long id,
                                                         Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(sessionService.endEncounter(id, user.getUuid()));
    }

    /**
     * Advance the turn (wraps past the end and increments the round). DM Next,
     * or a player ending their own turn — the server checks the active combatant
     * is theirs. Carries the caller's expected active-participant ID so a stale
     * advance is rejected with 409 instead of double-advancing.
     */
    @PostMapping("/session/{id}/advance")
    public ResponseEntity<SessionStateView> advanceTurn(@PathVariable Long id,
                                                        @RequestBody AdvanceRequest request,
                                                        Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(
                sessionService.advanceTurn(id, request.expectedActiveParticipantId(), user.getUuid()));
    }

    /** DM adds an enemy (with a DM-calculated DEX modifier), lobby or mid-encounter. */
    @PostMapping("/session/{id}/enemies")
    public ResponseEntity<SessionStateView> addEnemy(@PathVariable Long id,
                                                     @RequestBody AddEnemyRequest request,
                                                     Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED).body(sessionService.addEnemy(
                id, request.name(), request.dexModifier(), request.hpMax(), user.getUuid()));
    }

    /** DM loads a curated encounter's creatures into the session as enemy combatants. */
    @PostMapping("/session/{id}/encounter/load")
    public ResponseEntity<SessionStateView> loadEncounter(@PathVariable Long id,
                                                          @RequestBody LoadEncounterRequest request,
                                                          Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED).body(
                sessionService.loadEncounter(id, request.encounterId(), user.getUuid()));
    }

    /**
     * DM sets enemy visibility: hidden entirely, visible, or visible with
     * health hidden ({@code enemyHpHidden}; null leaves that flag unchanged
     * for old clients).
     */
    @PutMapping("/session/{id}/visibility")
    public ResponseEntity<SessionStateView> setVisibility(@PathVariable Long id,
                                                          @RequestBody SetVisibilityRequest request,
                                                          Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(sessionService.setVisibility(
                id, request.enemiesHidden(), request.enemyHpHidden(), user.getUuid()));
    }

    /** DM sets (or clears) the encounter-level turn-cue sound. */
    @PutMapping("/session/{id}/sound")
    public ResponseEntity<SessionStateView> setTurnSound(@PathVariable Long id,
                                                         @RequestBody SetSoundRequest request,
                                                         Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(
                sessionService.setTurnSound(id, request.turnSound(), user.getUuid()));
    }

    /**
     * DM advances the campaign clock one segment (dawn → noon → dusk → night →
     * next day). In survival-conditions campaigns, entering dawn/noon/dusk bumps
     * every member PC's conditions server-side.
     */
    @PostMapping("/session/{id}/time/advance")
    public ResponseEntity<SessionStateView> advanceTime(@PathVariable Long id,
                                                        Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(sessionService.advanceTime(id, user.getUuid()));
    }

    /** DM sets the campaign clock directly (no condition bumps). */
    @PutMapping("/session/{id}/time")
    public ResponseEntity<SessionStateView> setTime(@PathVariable Long id,
                                                    @RequestBody SetTimeRequest request,
                                                    Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(sessionService.setTime(id, request, user.getUuid()));
    }

    /** DM sets the party's current location (name + type), broadcast to every sheet. */
    @PutMapping("/session/{id}/location")
    public ResponseEntity<SessionStateView> setLocation(@PathVariable Long id,
                                                        @RequestBody SetLocationRequest request,
                                                        Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(sessionService.setLocation(id, request, user.getUuid()));
    }

    /**
     * DM long rest for the seated party: recovers all spell slots and (in a
     * survival campaign) sheds fatigue — 3 for an undisturbed rest, 1 otherwise.
     */
    @PostMapping("/session/{id}/long-rest")
    public ResponseEntity<SessionStateView> longRest(@PathVariable Long id,
                                                     @RequestBody LongRestRequest request,
                                                     Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(sessionService.longRest(
                id, Boolean.TRUE.equals(request.undisturbed()), user.getUuid()));
    }

    /**
     * A player improves their own seated PC's survival condition (eat / drink /
     * sleep). Returns the new stages plus the possibly-decremented inventory.
     */
    @PostMapping("/session/{id}/survival/consume")
    public ResponseEntity<ConsumeResult> consumeSurvival(@PathVariable Long id,
                                                         @RequestBody ConsumeSurvivalRequest request,
                                                         Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(sessionService.consumeSurvival(
                id, request.pcId(), request.action(), user.getUuid()));
    }

    /**
     * A player casts one of their own seated PC's spells: spends a slot (with
     * upcasting) and consumes a flagged material component. Returns the updated
     * slots and inventory, plus a warning when a costly component was missing
     * but the campaign let the cast through.
     */
    @PostMapping("/session/{id}/spell/cast")
    public ResponseEntity<CastResult> castSpell(@PathVariable Long id,
                                                @RequestBody CastSpellRequest request,
                                                Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(sessionService.castSpell(
                id, request.pcId(), request.spellName(), request.atLevel(), user.getUuid()));
    }

    /**
     * Log a roll made during a live session (from the in-sheet dice modal or
     * the Session Mode Roll button). Caller must be the DM or own the named
     * participant. The server does not re-roll — it validates and stores the
     * client's already-computed results, then bumps the session version so the
     * Roll Log panel picks it up on the next poll.
     */
    @PostMapping("/session/{id}/participants/{participantId}/rolls")
    public ResponseEntity<SessionStateView> logRoll(@PathVariable Long id,
                                                    @PathVariable Long participantId,
                                                    @RequestBody LogRollRequest request,
                                                    Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED).body(
                sessionService.logRoll(id, participantId, request, user.getUuid()));
    }

    /** DM ends the session. */
    @PostMapping("/session/{id}/end")
    public ResponseEntity<SessionStateView> endSession(@PathVariable Long id,
                                                      Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(sessionService.endSession(id, user.getUuid()));
    }
}
