package com.moo.charactermanagerservice.controllers;

import com.moo.charactermanagerservice.dto.AddLootItemRequest;
import com.moo.charactermanagerservice.dto.AssignLootRequest;
import com.moo.charactermanagerservice.dto.CastLootVoteRequest;
import com.moo.charactermanagerservice.dto.LootOverrideRequest;
import com.moo.charactermanagerservice.dto.LootView;
import com.moo.charactermanagerservice.dto.OpenLootRequest;
import com.moo.charactermanagerservice.dto.SetCoinShareRequest;
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
 * (pool management, override) or a seated character (the party split and
 * vote). Players discover a dropped pool via the session poll ({@code
 * lootStatus} on the session state) and fetch it here.
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

    /** DM drops the loot — the party can now see it and split it. */
    @PostMapping("/session/{id}/loot/drop")
    public ResponseEntity<LootView> dropLoot(@PathVariable Long id, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(lootService.dropLoot(id, user.getUuid()));
    }

    /** DM discards the pool; anything not yet distributed is gone. */
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

    // --- party split ---------------------------------------------------------

    /**
     * Move items during the split: pile → character, character → pile, or
     * character → character ({@code null} participant = the pile).
     */
    @PostMapping("/session/{id}/loot/split/assign")
    public ResponseEntity<LootView> assign(@PathVariable Long id,
                                           @RequestBody AssignLootRequest request,
                                           Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(lootService.assignItem(id, request.lootItemId(),
                request.fromParticipantId(), request.toParticipantId(), request.qty(), user.getUuid()));
    }

    /** Set one character's share of the coin pile, in copper. */
    @PutMapping("/session/{id}/loot/split/coins")
    public ResponseEntity<LootView> setCoinShare(@PathVariable Long id,
                                                 @RequestBody SetCoinShareRequest request,
                                                 Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(lootService.setCoinShare(
                id, request.participantId(), request.coinCp(), user.getUuid()));
    }

    /** Split the whole coin pile evenly across the seated characters. */
    @PostMapping("/session/{id}/loot/split/coins/even")
    public ResponseEntity<LootView> splitCoinsEvenly(@PathVariable Long id, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(lootService.splitCoinsEvenly(id, user.getUuid()));
    }

    /** Lock the split (pile must be empty) and open the party vote. */
    @PostMapping("/session/{id}/loot/split/propose")
    public ResponseEntity<LootView> propose(@PathVariable Long id, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(lootService.proposeSplit(id, user.getUuid()));
    }

    /**
     * Cast (or re-cast) a ballot: ✓, or ✗ plus a PvP answer. 204 when the vote
     * resolved and the pool is gone for the caller (distributed or cancelled).
     */
    @PostMapping("/session/{id}/loot/split/vote")
    public ResponseEntity<LootView> vote(@PathVariable Long id,
                                         @RequestBody CastLootVoteRequest request,
                                         Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        LootView view = lootService.castVote(id, request.vote(), request.pvp(), user.getUuid());
        return view == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(view);
    }

    /** DM override: APPROVE distributes the split now, CANCEL takes the loot back as a draft. */
    @PostMapping("/session/{id}/loot/split/override")
    public ResponseEntity<LootView> override(@PathVariable Long id,
                                             @RequestBody LootOverrideRequest request,
                                             Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        LootView view = lootService.overrideSplit(id, request.action(), user.getUuid());
        return view == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(view);
    }
}
