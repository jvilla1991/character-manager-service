package com.moo.charactermanagerservice.controllers;

import com.moo.charactermanagerservice.dto.AddCreatureRequest;
import com.moo.charactermanagerservice.dto.AddLootItemRequest;
import com.moo.charactermanagerservice.dto.CreateEncounterRequest;
import com.moo.charactermanagerservice.dto.EncounterSummaryView;
import com.moo.charactermanagerservice.dto.EncounterView;
import com.moo.charactermanagerservice.dto.ImportLootRequest;
import com.moo.charactermanagerservice.dto.SetLootCoinsRequest;
import com.moo.charactermanagerservice.dto.UpdateCreatureRequest;
import com.moo.charactermanagerservice.dto.UpdateEncounterRequest;
import com.moo.charactermanagerservice.dto.UpdateLootItemRequest;
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
                request.dexModifier(), request.hpMax(), request.quantity(), user.getUuid()));
    }

    @PutMapping("/encounters/{encounterId}/creatures/{creatureId}")
    public ResponseEntity<EncounterView> updateCreature(@PathVariable Long encounterId,
                                                        @PathVariable Long creatureId,
                                                        @RequestBody UpdateCreatureRequest request,
                                                        Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(curatedEncounterService.updateCreature(encounterId, creatureId,
                request.name(), request.dexModifier(), request.hpMax(), request.quantity(), user.getUuid()));
    }

    @DeleteMapping("/encounters/{encounterId}/creatures/{creatureId}")
    public ResponseEntity<EncounterView> removeCreature(@PathVariable Long encounterId,
                                                        @PathVariable Long creatureId,
                                                        Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(curatedEncounterService.removeCreature(encounterId, creatureId, user.getUuid()));
    }

    @PostMapping("/encounters/{encounterId}/loot")
    public ResponseEntity<EncounterView> addLootItem(@PathVariable Long encounterId,
                                                     @RequestBody AddLootItemRequest request,
                                                     Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(curatedEncounterService.addLootItem(encounterId, request.catalogItemKey(),
                request.customName(), request.customNotes(), request.qty(), user.getUuid()));
    }

    @PutMapping("/encounters/{encounterId}/loot/{lootItemId}")
    public ResponseEntity<EncounterView> updateLootItem(@PathVariable Long encounterId,
                                                        @PathVariable Long lootItemId,
                                                        @RequestBody UpdateLootItemRequest request,
                                                        Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(curatedEncounterService.updateLootItem(encounterId, lootItemId,
                request.qty(), request.customName(), request.customNotes(), user.getUuid()));
    }

    @DeleteMapping("/encounters/{encounterId}/loot/{lootItemId}")
    public ResponseEntity<EncounterView> removeLootItem(@PathVariable Long encounterId,
                                                        @PathVariable Long lootItemId,
                                                        Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(curatedEncounterService.removeLootItem(encounterId, lootItemId, user.getUuid()));
    }

    @PutMapping("/encounters/{encounterId}/loot-coins")
    public ResponseEntity<EncounterView> setLootCoins(@PathVariable Long encounterId,
                                                      @RequestBody SetLootCoinsRequest request,
                                                      Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(curatedEncounterService.setLootCoins(encounterId, request.coinGp(), user.getUuid()));
    }

    /** Bulk-add loot lines from pasted JSON (appends; coinGp adds to the pile). */
    @PostMapping("/encounters/{encounterId}/loot/import")
    public ResponseEntity<EncounterView> importLoot(@PathVariable Long encounterId,
                                                    @RequestBody ImportLootRequest request,
                                                    Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(curatedEncounterService.importLoot(encounterId, request, user.getUuid()));
    }
}
