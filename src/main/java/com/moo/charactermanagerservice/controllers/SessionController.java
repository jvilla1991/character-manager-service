package com.moo.charactermanagerservice.controllers;

import com.moo.charactermanagerservice.dto.AddEnemyRequest;
import com.moo.charactermanagerservice.dto.AdvanceRequest;
import com.moo.charactermanagerservice.dto.CastResult;
import com.moo.charactermanagerservice.dto.CastSpellRequest;
import com.moo.charactermanagerservice.dto.CombatLogPageView;
import com.moo.charactermanagerservice.dto.LongRestChoices;
import com.moo.charactermanagerservice.dto.LongRestOptionsRequest;
import com.moo.charactermanagerservice.dto.LongRestReadyRequest;
import com.moo.charactermanagerservice.dto.LongRestRequest;
import com.moo.charactermanagerservice.dto.RestVoteRequest;
import com.moo.charactermanagerservice.dto.ConsumeResult;
import com.moo.charactermanagerservice.dto.ConsumeSurvivalRequest;
import com.moo.charactermanagerservice.dto.DamageRequest;
import com.moo.charactermanagerservice.dto.HitDieSpendResult;
import com.moo.charactermanagerservice.dto.JoinSessionRequest;
import com.moo.charactermanagerservice.dto.LoadEncounterRequest;
import com.moo.charactermanagerservice.dto.LogRollRequest;
import com.moo.charactermanagerservice.dto.RestVoteCastRequest;
import com.moo.charactermanagerservice.dto.SessionStateView;
import com.moo.charactermanagerservice.dto.SetInitiativeRequest;
import com.moo.charactermanagerservice.dto.SetInspirationRequest;
import com.moo.charactermanagerservice.dto.SetSoundRequest;
import com.moo.charactermanagerservice.dto.SetLocationRequest;
import com.moo.charactermanagerservice.dto.SetTimeRequest;
import com.moo.charactermanagerservice.dto.SetVisibilityRequest;
import com.moo.charactermanagerservice.dto.SpendHitDieRequest;
import com.moo.charactermanagerservice.dto.User;
import com.moo.charactermanagerservice.dto.XpAwardRequest;
import com.moo.charactermanagerservice.dto.XpAwardResult;
import com.moo.charactermanagerservice.models.RestType;
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

    /**
     * DM damages (positive) or heals (negative) a follower of a seated PC; the
     * new HP writes through to the follower's saved stat block.
     */
    @PostMapping("/session/{id}/followers/{followerId}/damage")
    public ResponseEntity<SessionStateView> applyFollowerDamage(@PathVariable Long id,
                                                               @PathVariable Long followerId,
                                                               @RequestBody DamageRequest request,
                                                               Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(
                sessionService.applyFollowerDamage(id, followerId, request.amount(), user.getUuid()));
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

    /** DM adds an enemy (with an optional armor class), lobby or mid-encounter. */
    @PostMapping("/session/{id}/enemies")
    public ResponseEntity<SessionStateView> addEnemy(@PathVariable Long id,
                                                     @RequestBody AddEnemyRequest request,
                                                     Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED).body(sessionService.addEnemy(
                id, request.name(), request.armorClass(), request.hpMax(), user.getUuid()));
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
     * DM opens a long rest phase for the seated party (no vote). Nothing is
     * recovered yet: players mark Ready, and recovery lands on
     * {@code /long-rest/finish} or when the last able player readies up. The
     * optional body seeds the undisturbed option (default true).
     */
    @PostMapping("/session/{id}/long-rest")
    public ResponseEntity<SessionStateView> longRest(@PathVariable Long id,
                                                     @RequestBody(required = false) LongRestRequest request,
                                                     Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(sessionService.openLongRest(
                id, request == null ? null : request.undisturbed(), user.getUuid()));
    }

    /**
     * A player marks their own seated PC ready (or not ready) for the open
     * long rest. A Ready may carry the hero's choices (prepared spells, rune
     * weapon), which are checked here (400) and applied when the rest
     * finishes. The last able player's Ready finishes the rest.
     */
    @PostMapping("/session/{id}/long-rest/ready")
    public ResponseEntity<SessionStateView> longRestReady(@PathVariable Long id,
                                                          @RequestBody LongRestReadyRequest request,
                                                          Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(sessionService.setLongRestReady(
                id, request.pcId(), request.ready(),
                new LongRestChoices(request.preparedSpells(), request.runeWeapon()), user.getUuid()));
    }

    /** DM sets the open long rest's options (advance the clock, undisturbed). */
    @PutMapping("/session/{id}/long-rest/options")
    public ResponseEntity<SessionStateView> longRestOptions(@PathVariable Long id,
                                                            @RequestBody LongRestOptionsRequest request,
                                                            Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(sessionService.setLongRestOptions(
                id, request.advanceClock(), request.undisturbed(), user.getUuid()));
    }

    /** DM finishes the open long rest now: recovery is applied to every seated hero. */
    @PostMapping("/session/{id}/long-rest/finish")
    public ResponseEntity<SessionStateView> finishLongRest(@PathVariable Long id,
                                                           Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(sessionService.finishLongRest(id, user.getUuid()));
    }

    /** DM interrupts the open long rest: the phase closes and nobody recovers. */
    @PostMapping("/session/{id}/long-rest/interrupt")
    public ResponseEntity<SessionStateView> interruptLongRest(@PathVariable Long id,
                                                              Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(sessionService.interruptLongRest(id, user.getUuid()));
    }

    /**
     * DM announces (or calls off) a short rest — toggles the session's
     * short-rest window, during which players may spend hit dice.
     */
    @PostMapping("/session/{id}/short-rest")
    public ResponseEntity<SessionStateView> shortRest(@PathVariable Long id,
                                                      Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(sessionService.toggleShortRest(id, user.getUuid()));
    }

    /**
     * A seated player calls a rest: opens a 60-second unanimous vote among
     * every seated PC (the caller's ballot is pre-set to yes). The optional
     * body picks the rest ({@code restType} SHORT or LONG, default SHORT). A
     * passed SHORT vote opens the same short-rest window as the DM's toggle;
     * a passed LONG vote opens the long rest phase.
     */
    @PostMapping("/session/{id}/rest-vote")
    public ResponseEntity<SessionStateView> initiateRestVote(@PathVariable Long id,
                                                             @RequestBody(required = false) RestVoteRequest request,
                                                             Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(sessionService.initiateRestVote(
                id, RestType.parse(request == null ? null : request.restType()), user.getUuid()));
    }

    /** A player answers the active rest vote (true = yes, false = no). */
    @PostMapping("/session/{id}/rest-vote/cast")
    public ResponseEntity<SessionStateView> castRestVote(@PathVariable Long id,
                                                         @RequestBody RestVoteCastRequest request,
                                                         Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(sessionService.castRestVote(id, request.vote(), user.getUuid()));
    }

    /** DM override on the active vote: true ends it passed now, false ends it failed. */
    @PostMapping("/session/{id}/rest-vote/override")
    public ResponseEntity<SessionStateView> overrideRestVote(@PathVariable Long id,
                                                             @RequestBody RestVoteCastRequest request,
                                                             Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(sessionService.overrideRestVote(id, request.vote(), user.getUuid()));
    }

    /**
     * A player spends one of their own seated PC's hit dice while the DM's
     * short-rest window is open. The server rolls 1d[hitDie] + CON mod and
     * returns the roll, the healing applied, and the new totals.
     */
    @PostMapping("/session/{id}/hit-dice/spend")
    public ResponseEntity<HitDieSpendResult> spendHitDie(@PathVariable Long id,
                                                         @RequestBody SpendHitDieRequest request,
                                                         Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(sessionService.spendHitDie(id, request.pcId(), user.getUuid()));
    }

    /**
     * DM sets a seated PC's inspiration meter by clicking a pip in the tracker —
     * raising or lowering it. Filling the meter converts into Heroic Inspiration
     * (meter empties, badge lights up on every viewer).
     */
    @PutMapping("/session/{id}/participants/{participantId}/inspiration")
    public ResponseEntity<SessionStateView> setInspiration(@PathVariable Long id,
                                                           @PathVariable Long participantId,
                                                           @RequestBody SetInspirationRequest request,
                                                           Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(
                sessionService.setInspiration(id, participantId, request.pips(), user.getUuid()));
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
     * but the campaign let the cast through. With {@code "ritual": true} a
     * ritual spell is cast without spending a slot (409 if it is not one).
     */
    @PostMapping("/session/{id}/spell/cast")
    public ResponseEntity<CastResult> castSpell(@PathVariable Long id,
                                                @RequestBody CastSpellRequest request,
                                                Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(sessionService.castSpell(
                id, request.pcId(), request.spellName(), request.atLevel(),
                Boolean.TRUE.equals(request.ritual()), user.getUuid()));
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

    /**
     * One page of the session's combat log — the health and condition changes
     * made this session, newest first. DM only (403 otherwise). {@code page} is
     * zero-based and defaults to 0; {@code size} defaults to 10 and is capped
     * at 50. The log is emptied when the session ends.
     */
    @GetMapping("/session/{id}/combat-log")
    public ResponseEntity<CombatLogPageView> combatLog(@PathVariable Long id,
                                                       @RequestParam(required = false) Integer page,
                                                       @RequestParam(required = false) Integer size,
                                                       Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(sessionService.combatLog(id, page, size, user.getUuid()));
    }

    /** DM ends the session. */
    @PostMapping("/session/{id}/end")
    public ResponseEntity<SessionStateView> endSession(@PathVariable Long id,
                                                      Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(sessionService.endSession(id, user.getUuid()));
    }
}
