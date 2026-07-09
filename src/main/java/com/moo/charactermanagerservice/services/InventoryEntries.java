package com.moo.charactermanagerservice.services;

import com.moo.charactermanagerservice.models.SrdItem;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builders for the PC inventory's JSON item lines, shared by every "an item
 * lands in an inventory" path — shop purchases and loot claims. Catalog items
 * are denormalized into a self-contained snapshot (like PcSpell) so the owned
 * line survives catalog changes; custom (free-hand) loot has no catalog key or
 * price and is stamped {@code category: 'gear'}, matching the sheet's own
 * custom-item flow. All methods are static; state lives in the caller.
 */
final class InventoryEntries {

    private InventoryEntries() {}

    /**
     * Append a catalog item, stacking quantity onto an existing line with the
     * same catalog key. {@code unitCostCp} is the price actually paid — null
     * for loot (nothing was paid, and an unpriced line correctly can't be
     * sold back without a catalog match).
     */
    static void addCatalogItem(List<Map<String, Object>> inventory, SrdItem item,
                               int qty, Long unitCostCp, PcJsonColumns json) {
        for (Map<String, Object> entry : inventory) {
            if (item.getItemKey().equals(entry.get("catalogKey"))) {
                int existing = entry.get("qty") instanceof Number n ? n.intValue() : 0;
                entry.put("qty", existing + qty);
                return;
            }
        }
        inventory.add(newCatalogEntry(item, qty, unitCostCp, json));
    }

    /** Build a denormalized inventory line from a catalog item — a self-contained snapshot. */
    static Map<String, Object> newCatalogEntry(SrdItem item, int qty, Long unitCostCp, PcJsonColumns json) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("catalogKey", item.getItemKey());
        entry.put("name", item.getName());
        entry.put("category", categoryLabel(item.getCategory()));
        entry.put("qty", qty);
        if (unitCostCp != null) entry.put("unitCostCp", unitCostCp);
        if (item.getWeight() != null) entry.put("weight", item.getWeight());
        // Darker Dungeons bulk — stamped on every acquisition so slot-variant
        // campaigns can total it; inert everywhere else.
        entry.put("bulk", BulkRules.bulkFor(item.getBulk(), item.getWeight()));
        // Flatten the catalog details (damage, properties, …) into the owned item.
        json.parseObject(item.getDetails()).forEach(entry::putIfAbsent);
        return entry;
    }

    /**
     * Build a free-hand item line (custom loot: magic items, trophies). Never
     * stacked — each custom line is its own entry, since two "Cloak of
     * Elvenkind"s are not interchangeable the way two longswords are.
     */
    static Map<String, Object> newCustomEntry(String name, int qty, String notes) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("name", name);
        entry.put("category", "gear");
        entry.put("qty", qty);
        if (notes != null && !notes.isBlank()) entry.put("notes", notes.trim());
        return entry;
    }

    /** Map a catalog category (WEAPON) to the lower-case inventory label ('weapon'). */
    static String categoryLabel(String category) {
        return switch (category) {
            case "WEAPON" -> "weapon";
            case "ARMOR" -> "armor";
            case "MATERIAL_COMPONENT" -> "material-component";
            default -> category.toLowerCase();
        };
    }
}
