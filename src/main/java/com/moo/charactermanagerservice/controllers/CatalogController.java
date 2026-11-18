package com.moo.charactermanagerservice.controllers;

import com.moo.charactermanagerservice.dto.CatalogItemView;
import com.moo.charactermanagerservice.dto.FeatureProgressionEntry;
import com.moo.charactermanagerservice.progression.ClassFeatures;
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

    /**
     * The features a class gains from level 2 to level 20, in level order — the same catalog
     * level-up grants from, so the sheet's "yet to unlock" list can never disagree with it.
     * {@code subclass} adds that subclass's features (omit it for a character who has not chosen
     * one yet); {@code specialization} applies a World of Warcraft variant's feature swaps. An
     * unknown class yields an empty list rather than an error: this is reference data.
     */
    @GetMapping("/catalog/class-features")
    public ResponseEntity<List<FeatureProgressionEntry>> classFeatures(
            @RequestParam String clazz,
            @RequestParam(required = false) String subclass,
            @RequestParam(required = false) String specialization) {
        return ResponseEntity.ok(ClassFeatures.progression(clazz, blankToNull(subclass), blankToNull(specialization)));
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
