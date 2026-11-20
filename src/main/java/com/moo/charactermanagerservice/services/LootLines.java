package com.moo.charactermanagerservice.services;

import com.moo.charactermanagerservice.dto.ImportLootRequest;
import com.moo.charactermanagerservice.models.LootLineAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * Shared rules for a loot LINE — the catalog-key-or-custom-name reference shape
 * used by both curated loot lists ({@link CuratedLootService}) and the live
 * session pool ({@link LootService}). One place instead of two identical
 * private copies, so the two flows can't drift: the items a loot line produces
 * are denormalized by {@link InventoryEntries}, the same helper the shop
 * purchase and DM-grant paths use.
 */
final class LootLines {

    /** The lowercase inventory category labels a custom line may carry. */
    static final Set<String> CATEGORIES =
            Set.of("weapon", "armor", "material-component", "gear", "transport", "treasure");

    private LootLines() {}

    /** 400 unless exactly one of catalogItemKey/customName is set and qty (if given) is ≥ 1. */
    static void validate(String catalogItemKey, String customName, Integer qty) {
        boolean hasKey = catalogItemKey != null && !catalogItemKey.isBlank();
        boolean hasName = customName != null && !customName.isBlank();
        if (hasKey == hasName) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Provide either a catalogItemKey or a customName (not both)");
        }
        if (qty != null && qty < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "qty must be at least 1");
        }
    }

    /**
     * 400 unless the custom-item attributes are coherent: they are rejected
     * outright on catalog lines (the catalog is authoritative there), the
     * category must be a known inventory label, value/weight/bulk must be ≥ 0, and
     * the category-specific stats belong to their category (damage → weapon,
     * armorClass → armor) — mirroring what the item composer can author.
     */
    static void validateAttributes(boolean catalogLine, String category, Double valueGp,
                                   Double weight, Double bulk, String damage, String armorClass) {
        boolean anyAttribute = present(category) || valueGp != null || weight != null
                || bulk != null || present(damage) || present(armorClass);
        if (catalogLine) {
            if (anyAttribute) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "category/valueGp/weight/bulk/damage/armorClass apply to custom items only "
                                + "— catalog items take their stats from the catalog");
            }
            return;
        }
        if (present(category) && !CATEGORIES.contains(category.trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "category must be one of weapon, armor, material-component, gear, transport, treasure");
        }
        if (valueGp != null && valueGp < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "valueGp must be zero or more");
        }
        if (weight != null && weight < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "weight must be zero or more");
        }
        if (bulk != null && bulk < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bulk must be zero or more");
        }
        String effectiveCategory = present(category) ? category.trim() : "gear";
        if (present(damage) && !"weapon".equals(effectiveCategory)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "damage applies to weapon items only");
        }
        if (present(armorClass) && !"armor".equals(effectiveCategory)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "armorClass applies to armor items only");
        }
    }

    /**
     * Stamp validated attributes onto a custom line (converting gold → copper
     * and normalizing blanks to null). Call after {@link #validateAttributes};
     * catalog lines simply keep all-null attributes.
     */
    static void applyAttributes(LootLineAttributes line, String category, Double valueGp,
                                Double weight, Double bulk, String damage, String armorClass) {
        line.setCategory(present(category) ? category.trim() : null);
        line.setUnitCostCp(valueGp == null ? null : Math.round(valueGp * 100));
        line.setWeight(weight == null ? null : BigDecimal.valueOf(weight));
        line.setBulk(bulk == null ? null : BigDecimal.valueOf(bulk));
        line.setDamage(present(damage) ? damage.trim() : null);
        line.setArmorClass(present(armorClass) ? armorClass.trim() : null);
    }

    /** Copy one line's attributes onto another (curated line → session pool copy). */
    static void copyAttributes(LootLineAttributes from, LootLineAttributes to) {
        to.setCategory(from.getCategory());
        to.setUnitCostCp(from.getUnitCostCp());
        to.setWeight(from.getWeight());
        to.setBulk(from.getBulk());
        to.setDamage(from.getDamage());
        to.setArmorClass(from.getArmorClass());
    }

    /**
     * 400 unless a pasted loot payload is sound: a non-negative coin pile, and
     * every line a catalog "key" or a custom "name" (exactly one) with a qty of
     * at least 1 and coherent attributes. Returns the distinct catalog keys the
     * lines name, for the caller to look up — whether a key exists is the one
     * thing this class cannot check. Shared by the curated loot list import and
     * the session pool opened from JSON, so the two accept the same paste.
     */
    static List<String> validateImport(ImportLootRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Import payload is required");
        }
        if (request.coinGp() != null && request.coinGp() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "coinGp must be zero or more");
        }
        List<ImportLootRequest.Item> lines = request.items() == null ? List.of() : request.items();
        for (ImportLootRequest.Item line : lines) {
            boolean hasKey = line != null && line.key() != null && !line.key().isBlank();
            boolean hasName = line != null && line.name() != null && !line.name().isBlank();
            if (hasKey == hasName) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Each item needs either a \"key\" or a \"name\" (not both)");
            }
            if (line.qty() != null && line.qty() < 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "qty must be at least 1");
            }
            validateAttributes(hasKey, line.category(), line.valueGp(),
                    line.weight(), line.bulk(), line.damage(), line.armorClass());
        }
        return lines.stream()
                .map(ImportLootRequest.Item::key)
                .filter(k -> k != null && !k.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    /** The trimmed catalog key, or null for a custom line (blank counts as absent). */
    static String normalizeKey(String catalogItemKey) {
        return catalogItemKey == null || catalogItemKey.isBlank() ? null : catalogItemKey.trim();
    }

    private static boolean present(String s) {
        return s != null && !s.isBlank();
    }
}
