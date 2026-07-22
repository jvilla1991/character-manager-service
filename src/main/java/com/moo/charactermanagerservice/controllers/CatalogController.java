package com.moo.charactermanagerservice.controllers;

import com.moo.charactermanagerservice.dto.CatalogItemView;
import com.moo.charactermanagerservice.services.ShopService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only SRD catalog browse, independent of any open shop. Added for the
 * DM-grant picker (granting equipment from a member's character sheet), but
 * intentionally unrestricted beyond authentication: the catalog is public
 * reference data players already see through shops.
 */
@RestController
@RequestMapping("/api/v1")
public class CatalogController {

    @Autowired
    private ShopService shopService;

    /** All catalog items in a category (WEAPON / ARMOR / MATERIAL_COMPONENT / GEAR / TRANSPORT); 400 otherwise. */
    @GetMapping("/catalog")
    public ResponseEntity<List<CatalogItemView>> catalog(@RequestParam String category) {
        return ResponseEntity.ok(shopService.catalog(category));
    }
}
