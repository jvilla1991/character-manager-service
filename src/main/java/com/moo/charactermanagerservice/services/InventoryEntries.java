package com.moo.charactermanagerservice.services;

import com.moo.charactermanagerservice.models.SrdItem;

import java.math.BigDecimal;
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
     * Elvenkind"s are not interchangeable the way two longswords are. The
     * attribute fields mirror what the DM grant form stamps client-side —
     * category (default 'gear' when absent, matching legacy rows), value,
     * weight, and the category stat (weapon damage / armor class) — so a
     * claimed custom item is exactly as stat'd as a granted one. Bulk is
     * stamped like every other acquisition: the DM's explicit {@code bulk}
     * when the line carries one, else the weight-band fallback (no weight
     * means 1, "a small object").
     */
    static Map<String, Object> newCustomEntry(String name, String category, int qty, Long unitCostCp,
                                              BigDecimal weight, BigDecimal bulk, String damage,
                                              String armorClass, String notes) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("name", name);
        entry.put("category", category == null || category.isBlank() ? "gear" : category);
        entry.put("qty", qty);
        if (unitCostCp != null) entry.put("unitCostCp", unitCostCp);
        if (weight != null) entry.put("weight", weight);
        entry.put("bulk", BulkRules.bulkFor(bulk, weight));
        if (damage != null && !damage.isBlank()) entry.put("damage", damage.trim());
        if (armorClass != null && !armorClass.isBlank()) entry.put("armorClass", armorClass.trim());
        if (notes != null && !notes.isBlank()) entry.put("notes", notes.trim());
        return entry;
    }

    /** The fields that make two custom lines "the same item" for stacking a purchase. */
    private static final List<String> CUSTOM_IDENTITY =
            List.of("name", "category", "unitCostCp", "weight", "bulk", "damage", "armorClass", "notes");

    /**
     * Add a custom item bought at a shop, stacking onto a line the character
     * already holds when it is the same item in every respect (name, stats,
     * price paid, description). A shop sells any number of one line, so three
     * potions bought one at a time should read "×3", not three lines. Loot
     * keeps {@link #newCustomEntry} un-stacked: each looted trophy is its own.
     * A catalog line, an equipped or dropped line, never absorbs the purchase.
     */
    static void addCustomItem(List<Map<String, Object>> inventory, Map<String, Object> bought) {
        int qty = bought.get("qty") instanceof Number n ? n.intValue() : 1;
        for (Map<String, Object> entry : inventory) {
            if (entry.get("catalogKey") != null) continue;
            if (Boolean.TRUE.equals(entry.get("equipped")) || "dropped".equals(entry.get("status"))) continue;
            if (CUSTOM_IDENTITY.stream().allMatch(f -> sameValue(entry.get(f), bought.get(f)))) {
                int existing = entry.get("qty") instanceof Number n ? n.intValue() : 0;
                entry.put("qty", existing + qty);
                return;
            }
        }
        inventory.add(bought);
    }

    /** Equal as stored: numbers by value (3, 3.0 and "3.00" parsed are one weight), the rest by equals. */
    private static boolean sameValue(Object a, Object b) {
        if (a instanceof Number x && b instanceof Number y) {
            return new BigDecimal(x.toString()).compareTo(new BigDecimal(y.toString())) == 0;
        }
        return java.util.Objects.equals(a, b);
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
