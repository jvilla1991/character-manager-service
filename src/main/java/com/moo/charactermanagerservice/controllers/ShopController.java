package com.moo.charactermanagerservice.controllers;

import com.moo.charactermanagerservice.dto.OpenShopRequest;
import com.moo.charactermanagerservice.dto.PurchaseRequest;
import com.moo.charactermanagerservice.dto.PurchaseResult;
import com.moo.charactermanagerservice.dto.SellRequest;
import com.moo.charactermanagerservice.dto.SellResult;
import com.moo.charactermanagerservice.dto.SetShopAttendeesRequest;
import com.moo.charactermanagerservice.dto.ShopView;
import com.moo.charactermanagerservice.dto.User;
import com.moo.charactermanagerservice.services.ShopService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Shopping endpoints, layered on a live session. Auth follows the session
 * pattern: the principal is the {@link User} resolved by {@code
 * AuthorizationFilter}; the service asserts DM ownership or attendee membership.
 * Targeted players discover an open shop via the session poll (the
 * {@code shopForMe} flag on the session state) and then fetch the catalog here.
 */
@RestController
@RequestMapping("/api/v1")
public class ShopController {

    @Autowired
    private ShopService shopService;

    /**
     * DM activates a shop (replaces any already open) and targets characters.
     * A {@code curatedShopId} activates a pre-built curated shop; otherwise a
     * standard catalog shop for the given {@code category}.
     */
    @PostMapping("/session/{id}/shop")
    public ResponseEntity<ShopView> openShop(@PathVariable Long id,
                                             @RequestBody OpenShopRequest request,
                                             Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        ShopView view = request.curatedShopId() != null
                ? shopService.openCuratedShop(
                        id, request.curatedShopId(), request.settlement(), request.pcIds(), user.getUuid())
                : shopService.openShop(
                        id, request.category(), request.settlement(), request.pcIds(), user.getUuid());
        return ResponseEntity.ok(view);
    }

    /** DM re-targets the characters at the active shop. */
    @PutMapping("/session/{id}/shop/attendees")
    public ResponseEntity<ShopView> setAttendees(@PathVariable Long id,
                                                 @RequestBody SetShopAttendeesRequest request,
                                                 Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(shopService.setAttendees(id, request.pcIds(), user.getUuid()));
    }

    /** DM closes the shop; attendance is cleared. */
    @DeleteMapping("/session/{id}/shop")
    public ResponseEntity<Void> closeShop(@PathVariable Long id, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        shopService.closeShop(id, user.getUuid());
        return ResponseEntity.noContent().build();
    }

    /** Browse the active shop — DM or targeted attendee only; 204 if none for caller. */
    @GetMapping("/session/{id}/shop")
    public ResponseEntity<ShopView> getShop(@PathVariable Long id, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        ShopView view = shopService.getActiveShopForUser(id, user.getUuid());
        return view == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(view);
    }

    /** A player buys an item for one of their characters at the shop. */
    @PostMapping("/session/{id}/shop/purchase")
    public ResponseEntity<PurchaseResult> purchase(@PathVariable Long id,
                                                   @RequestBody PurchaseRequest request,
                                                   Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(shopService.purchase(
                id, request.pcId(), request.itemKey(), request.qty(), user.getUuid()));
    }

    /** A player sells an inventory item back to the shop for one of their characters. */
    @PostMapping("/session/{id}/shop/sell")
    public ResponseEntity<SellResult> sell(@PathVariable Long id,
                                           @RequestBody SellRequest request,
                                           Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(shopService.sell(
                id, request.pcId(), request.index(), user.getUuid()));
    }
}
