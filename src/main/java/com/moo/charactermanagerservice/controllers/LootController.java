package com.moo.charactermanagerservice.controllers;

import com.moo.charactermanagerservice.dto.AddLootItemRequest;
import com.moo.charactermanagerservice.dto.ClaimCoinsRequest;
import com.moo.charactermanagerservice.dto.ClaimItemRequest;
import com.moo.charactermanagerservice.dto.ClaimResult;
import com.moo.charactermanagerservice.dto.LootView;
import com.moo.charactermanagerservice.dto.OpenLootRequest;
import com.moo.charactermanagerservice.dto.SetLootCoinsRequest;
import com.moo.charactermanagerservice.dto.UpdateLootItemRequest;
import com.moo.charactermanagerservice.dto.User;
import com.moo.charactermanagerservice.services.LootService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Post-combat loot endpoints, layered on a live session. Auth follows the shop
 * pattern: the principal is the {@link User}; the service asserts DM ownership
 * (pool management) or seated-participant ownership (claims). Players discover
 * a dropped pool via the session poll ({@code lootStatus} on the session state)
 * and fetch it here.
 */
@RestController
@RequestMapping("/api/v1")
public class LootController {

    @Autowired
    private LootService lootService;

    /**
     * DM opens a loot pool as an invisible draft (replacing any existing pool).
     * A {@code lootId} seeds it from that curated loot list's prepped lines.
     */
    @PostMapping("/session/{id}/loot")
    public ResponseEntity<LootView> openLoot(@PathVariable Long id,
                                             @RequestBody OpenLootRequest request,
                                             Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(lootService.openLoot(
                id, request.lootId(), request.name(), user.getUuid()));
    }

    /** DM drops the loot — players can now see and claim. */
    @PostMapping("/session/{id}/loot/drop")
    public ResponseEntity<LootView> dropLoot(@PathVariable Long id, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(lootService.dropLoot(id, user.getUuid()));
    }

    /** DM discards the pool; unclaimed loot is gone. */
    @DeleteMapping("/session/{id}/loot")
    public ResponseEntity<Void> closeLoot(@PathVariable Long id, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        lootService.closeLoot(id, user.getUuid());
        return ResponseEntity.noContent().build();
    }

    /** DM adds a line to the pool (before or after dropping). */
    @PostMapping("/session/{id}/loot/items")
    public ResponseEntity<LootView> addItem(@PathVariable Long id,
                                            @RequestBody AddLootItemRequest request,
                                            Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(lootService.addItem(id, request, user.getUuid()));
    }

    /** DM edits a line (qty shifts the remaining count by the same delta). */
    @PutMapping("/session/{id}/loot/items/{itemId}")
    public ResponseEntity<LootView> updateItem(@PathVariable Long id,
                                               @PathVariable Long itemId,
                                               @RequestBody UpdateLootItemRequest request,
                                               Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(lootService.updateItem(id, itemId,
                request.qty(), request.customName(), request.customNotes(), user.getUuid()));
    }

    /** DM removes a line from the pool. */
    @DeleteMapping("/session/{id}/loot/items/{itemId}")
    public ResponseEntity<LootView> removeItem(@PathVariable Long id,
                                               @PathVariable Long itemId,
                                               Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(lootService.removeItem(id, itemId, user.getUuid()));
    }

    /** DM sets the coin pile, in gold. */
    @PutMapping("/session/{id}/loot/coins")
    public ResponseEntity<LootView> setCoins(@PathVariable Long id,
                                             @RequestBody SetLootCoinsRequest request,
                                             Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(lootService.setCoins(id, request.coinGp(), user.getUuid()));
    }

    /** The pool as seen by the caller — DM always, players once dropped; 204 if none for caller. */
    @GetMapping("/session/{id}/loot")
    public ResponseEntity<LootView> getLoot(@PathVariable Long id, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        LootView view = lootService.getLootForUser(id, user.getUuid());
        return view == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(view);
    }

    /** A player claims an item line (first-come-first-served) for their character. */
    @PostMapping("/session/{id}/loot/claim-item")
    public ResponseEntity<ClaimResult> claimItem(@PathVariable Long id,
                                                 @RequestBody ClaimItemRequest request,
                                                 Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(lootService.claimItem(
                id, request.pcId(), request.lootItemId(), request.qty(), user.getUuid()));
    }

    /** A player takes coins from the pile for their character. */
    @PostMapping("/session/{id}/loot/claim-coins")
    public ResponseEntity<ClaimResult> claimCoins(@PathVariable Long id,
                                                  @RequestBody ClaimCoinsRequest request,
                                                  Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(lootService.claimCoins(
                id, request.pcId(), request.coins(), user.getUuid()));
    }
}
