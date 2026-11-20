package com.moo.charactermanagerservice.services;

import com.moo.charactermanagerservice.models.ShopItem;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared shape for a CUSTOM curated-shop line, used by the DM editor
 * ({@link CuratedShopService}) and the live shop ({@link ShopService}) so the
 * two cannot describe the same line differently. The field rules themselves
 * are {@link LootLines#validateAttributes}: a custom shop item is authored
 * exactly like a custom loot item, plus a price.
 */
final class ShopLines {

    private ShopLines() {}

    /** Stamp a validated custom item onto a line (trimming, blanks → null). */
    static void applyCustom(ShopItem line, String name, String notes, String category, long priceCp,
                            Double weight, Double bulk, String damage, String armorClass) {
        line.setCatalogItemKey(null);
        line.setCustomName(name.trim());
        line.setCustomNotes(present(notes) ? notes.trim() : null);
        line.setCategory(present(category) ? category.trim() : null);
        line.setPriceCp(priceCp);
        line.setWeight(weight == null ? null : BigDecimal.valueOf(weight));
        line.setBulk(bulk == null ? null : BigDecimal.valueOf(bulk));
        line.setDamage(present(damage) ? damage.trim() : null);
        line.setArmorClass(present(armorClass) ? armorClass.trim() : null);
    }

    /**
     * A custom line's category as the catalog-style code the shop payloads use
     * ('weapon' → WEAPON, 'material-component' → MATERIAL_COMPONENT); no
     * category means gear, as everywhere else.
     */
    static String categoryCode(ShopItem line) {
        String label = present(line.getCategory()) ? line.getCategory() : "gear";
        return label.trim().toUpperCase().replace('-', '_');
    }

    /** A custom line's stats in the shape of a catalog entry's details map. */
    static Map<String, Object> details(ShopItem line) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (present(line.getDamage())) details.put("damage", line.getDamage());
        if (present(line.getArmorClass())) details.put("armorClass", line.getArmorClass());
        if (present(line.getCustomNotes())) details.put("notes", line.getCustomNotes());
        return details;
    }

    /** What a buyer pays for a custom line (a legacy null price reads as free). */
    static long priceCp(ShopItem line) {
        return line.getPriceCp() == null ? 0L : line.getPriceCp();
    }

    private static boolean present(String s) {
        return s != null && !s.isBlank();
    }
}
