package com.moo.charactermanagerservice.controllers;

import com.moo.charactermanagerservice.dto.AddLootItemRequest;
import com.moo.charactermanagerservice.dto.CreateLootRequest;
import com.moo.charactermanagerservice.dto.CuratedLootSummaryView;
import com.moo.charactermanagerservice.dto.CuratedLootView;
import com.moo.charactermanagerservice.dto.ImportLootRequest;
import com.moo.charactermanagerservice.dto.SetLootCoinsRequest;
import com.moo.charactermanagerservice.dto.UpdateLootItemRequest;
import com.moo.charactermanagerservice.dto.UpdateLootRequest;
import com.moo.charactermanagerservice.dto.User;
import com.moo.charactermanagerservice.services.CuratedLootService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * DM-curated loot management. A DM preps reusable loot lists (item lines plus a
 * coin pile) on the campaign dashboard; dropping one into a live session's claim
 * pool is handled by the session loot endpoints ({@link LootController}). Auth
 * follows the existing pattern: the principal is the {@link User}, and the
 * service asserts campaign-DM ownership. Mirrors {@link CuratedShopController} /
 * {@link CuratedEncounterController}.
 */
@RestController
@RequestMapping("/api/v1")
public class CuratedLootController {

    @Autowired
    private CuratedLootService curatedLootService;

    @PostMapping("/campaign/{campaignId}/loot")
    public ResponseEntity<CuratedLootView> create(@PathVariable Long campaignId,
                                                  @RequestBody CreateLootRequest request,
                                                  Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED).body(
                curatedLootService.createLoot(campaignId, request.name(), request.notes(), user.getUuid()));
    }

    @GetMapping("/campaign/{campaignId}/loot")
    public ResponseEntity<List<CuratedLootSummaryView>> list(@PathVariable Long campaignId,
                                                             Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(curatedLootService.listLoot(campaignId, user.getUuid()));
    }

    @GetMapping("/curated-loot/{lootId}")
    public ResponseEntity<CuratedLootView> get(@PathVariable Long lootId, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(curatedLootService.getLoot(lootId, user.getUuid()));
    }

    @PutMapping("/curated-loot/{lootId}")
    public ResponseEntity<CuratedLootView> update(@PathVariable Long lootId,
                                                  @RequestBody UpdateLootRequest request,
                                                  Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(
                curatedLootService.updateLoot(lootId, request.name(), request.notes(), user.getUuid()));
    }

    @DeleteMapping("/curated-loot/{lootId}")
    public ResponseEntity<Void> delete(@PathVariable Long lootId, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        curatedLootService.deleteLoot(lootId, user.getUuid());
        return ResponseEntity.noContent().build();
    }

    /** Add a prepped line — a catalog item, or a custom item with optional attributes. */
    @PostMapping("/curated-loot/{lootId}/items")
    public ResponseEntity<CuratedLootView> addItem(@PathVariable Long lootId,
                                                   @RequestBody AddLootItemRequest request,
                                                   Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(curatedLootService.addItem(lootId, request, user.getUuid()));
    }

    @PutMapping("/curated-loot/{lootId}/items/{itemId}")
    public ResponseEntity<CuratedLootView> updateItem(@PathVariable Long lootId,
                                                      @PathVariable Long itemId,
                                                      @RequestBody UpdateLootItemRequest request,
                                                      Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(curatedLootService.updateItem(lootId, itemId,
                request.qty(), request.customName(), request.customNotes(), user.getUuid()));
    }

    @DeleteMapping("/curated-loot/{lootId}/items/{itemId}")
    public ResponseEntity<CuratedLootView> removeItem(@PathVariable Long lootId,
                                                      @PathVariable Long itemId,
                                                      Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(curatedLootService.removeItem(lootId, itemId, user.getUuid()));
    }

    /** Set the list's prepped coin pile, in gold (fractions allowed). */
    @PutMapping("/curated-loot/{lootId}/coins")
    public ResponseEntity<CuratedLootView> setCoins(@PathVariable Long lootId,
                                                    @RequestBody SetLootCoinsRequest request,
                                                    Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(curatedLootService.setCoins(lootId, request.coinGp(), user.getUuid()));
    }

    /** Bulk-add lines from pasted JSON (appends; coinGp adds to the pile). */
    @PostMapping("/curated-loot/{lootId}/import")
    public ResponseEntity<CuratedLootView> importLoot(@PathVariable Long lootId,
                                                      @RequestBody ImportLootRequest request,
                                                      Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(curatedLootService.importLoot(lootId, request, user.getUuid()));
    }
}
