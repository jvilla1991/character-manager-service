package com.moo.charactermanagerservice.controllers;

import com.moo.charactermanagerservice.dto.AddCreatureRequest;
import com.moo.charactermanagerservice.dto.CreateEncounterRequest;
import com.moo.charactermanagerservice.dto.EncounterSummaryView;
import com.moo.charactermanagerservice.dto.EncounterView;
import com.moo.charactermanagerservice.dto.UpdateCreatureRequest;
import com.moo.charactermanagerservice.dto.UpdateEncounterRequest;
import com.moo.charactermanagerservice.dto.User;
import com.moo.charactermanagerservice.services.CuratedEncounterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * DM-curated encounter management. A DM builds reusable encounters (free-hand lists
 * of enemy creatures) out of session; loading one into a live session is handled by
 * the session encounter-load endpoint. Auth follows the existing pattern: the
 * principal is the {@link User}, and the service asserts campaign-DM ownership.
 * Loot endpoints live on {@link CuratedLootController} — prepped spoils are
 * standalone curated loot lists, no longer attached to encounters.
 */
@RestController
@RequestMapping("/api/v1")
public class CuratedEncounterController {

    @Autowired
    private CuratedEncounterService curatedEncounterService;

    @PostMapping("/campaign/{campaignId}/encounters")
    public ResponseEntity<EncounterView> create(@PathVariable Long campaignId,
                                                @RequestBody CreateEncounterRequest request,
                                                Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED).body(
                curatedEncounterService.createEncounter(campaignId, request.name(), request.notes(), user.getUuid()));
    }

    @GetMapping("/campaign/{campaignId}/encounters")
    public ResponseEntity<List<EncounterSummaryView>> list(@PathVariable Long campaignId,
                                                           Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(curatedEncounterService.listEncounters(campaignId, user.getUuid()));
    }

    @GetMapping("/encounters/{encounterId}")
    public ResponseEntity<EncounterView> get(@PathVariable Long encounterId, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(curatedEncounterService.getEncounter(encounterId, user.getUuid()));
    }

    @PutMapping("/encounters/{encounterId}")
    public ResponseEntity<EncounterView> update(@PathVariable Long encounterId,
                                                @RequestBody UpdateEncounterRequest request,
                                                Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(
                curatedEncounterService.updateEncounter(encounterId, request.name(), request.notes(), user.getUuid()));
    }

    @DeleteMapping("/encounters/{encounterId}")
    public ResponseEntity<Void> delete(@PathVariable Long encounterId, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        curatedEncounterService.deleteEncounter(encounterId, user.getUuid());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/encounters/{encounterId}/creatures")
    public ResponseEntity<EncounterView> addCreature(@PathVariable Long encounterId,
                                                     @RequestBody AddCreatureRequest request,
                                                     Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(curatedEncounterService.addCreature(encounterId, request.name(),
                request.armorClass(), request.hpMax(), request.quantity(), user.getUuid()));
    }

    @PutMapping("/encounters/{encounterId}/creatures/{creatureId}")
    public ResponseEntity<EncounterView> updateCreature(@PathVariable Long encounterId,
                                                        @PathVariable Long creatureId,
                                                        @RequestBody UpdateCreatureRequest request,
                                                        Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(curatedEncounterService.updateCreature(encounterId, creatureId,
                request.name(), request.armorClass(), request.hpMax(), request.quantity(), user.getUuid()));
    }

    @DeleteMapping("/encounters/{encounterId}/creatures/{creatureId}")
    public ResponseEntity<EncounterView> removeCreature(@PathVariable Long encounterId,
                                                        @PathVariable Long creatureId,
                                                        Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(curatedEncounterService.removeCreature(encounterId, creatureId, user.getUuid()));
    }
}
