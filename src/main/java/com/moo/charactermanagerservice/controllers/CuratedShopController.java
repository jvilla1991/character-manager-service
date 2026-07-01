package com.moo.charactermanagerservice.controllers;

import com.moo.charactermanagerservice.dto.AddShopItemRequest;
import com.moo.charactermanagerservice.dto.CreateShopRequest;
import com.moo.charactermanagerservice.dto.CuratedShopView;
import com.moo.charactermanagerservice.dto.ShopSummaryView;
import com.moo.charactermanagerservice.dto.UpdateShopItemRequest;
import com.moo.charactermanagerservice.dto.UpdateShopRequest;
import com.moo.charactermanagerservice.dto.User;
import com.moo.charactermanagerservice.services.CuratedShopService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * DM-curated shop management (Phase 2). A DM builds reusable shops from the SRD
 * catalog out of session; activation into a live session is handled by the
 * session shop endpoints. Auth follows the existing pattern: principal is the
 * {@link User}, and the service asserts campaign-DM ownership.
 */
@RestController
@RequestMapping("/api/v1")
public class CuratedShopController {

    @Autowired
    private CuratedShopService curatedShopService;

    @PostMapping("/campaign/{campaignId}/shops")
    public ResponseEntity<CuratedShopView> create(@PathVariable Long campaignId,
                                                  @RequestBody CreateShopRequest request,
                                                  Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED).body(
                curatedShopService.createShop(campaignId, request.name(), request.settlement(), user.getUuid()));
    }

    @GetMapping("/campaign/{campaignId}/shops")
    public ResponseEntity<List<ShopSummaryView>> list(@PathVariable Long campaignId,
                                                      Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(curatedShopService.listShops(campaignId, user.getUuid()));
    }

    @GetMapping("/shops/{shopId}")
    public ResponseEntity<CuratedShopView> get(@PathVariable Long shopId, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(curatedShopService.getShop(shopId, user.getUuid()));
    }

    @PutMapping("/shops/{shopId}")
    public ResponseEntity<CuratedShopView> update(@PathVariable Long shopId,
                                                  @RequestBody UpdateShopRequest request,
                                                  Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(
                curatedShopService.updateShop(shopId, request.name(), request.settlement(), user.getUuid()));
    }

    @DeleteMapping("/shops/{shopId}")
    public ResponseEntity<Void> delete(@PathVariable Long shopId, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        curatedShopService.deleteShop(shopId, user.getUuid());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/shops/{shopId}/items")
    public ResponseEntity<CuratedShopView> addItem(@PathVariable Long shopId,
                                                   @RequestBody AddShopItemRequest request,
                                                   Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(
                curatedShopService.addItem(shopId, request.catalogItemKey(), request.priceCp(), user.getUuid()));
    }

    /** Bulk-add a standard catalog category to the shop (template starting point). */
    @PostMapping("/shops/{shopId}/import")
    public ResponseEntity<CuratedShopView> importCategory(@PathVariable Long shopId,
                                                          @RequestParam String category,
                                                          Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(curatedShopService.importCategory(shopId, category, user.getUuid()));
    }

    @PutMapping("/shops/{shopId}/items/{itemId}")
    public ResponseEntity<CuratedShopView> updateItem(@PathVariable Long shopId,
                                                      @PathVariable Long itemId,
                                                      @RequestBody UpdateShopItemRequest request,
                                                      Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(
                curatedShopService.updateItem(shopId, itemId, request.priceCp(), user.getUuid()));
    }

    @DeleteMapping("/shops/{shopId}/items/{itemId}")
    public ResponseEntity<CuratedShopView> removeItem(@PathVariable Long shopId,
                                                      @PathVariable Long itemId,
                                                      Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(curatedShopService.removeItem(shopId, itemId, user.getUuid()));
    }
}
