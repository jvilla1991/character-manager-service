package com.moo.charactermanagerservice.models;

import java.math.BigDecimal;

/**
 * The optional custom-item attributes a loot line can carry — the same fields a
 * DM grant stamps on an inventory entry, so a claimed custom item is as richly
 * stat'd as a granted one. Implemented by both the curated line
 * ({@link CuratedLootItem}) and its session pool copy ({@link SessionLootItem});
 * all fields are null for catalog lines (the catalog is authoritative there)
 * and for legacy rows created before the columns existed.
 */
public interface LootLineAttributes {

    String getCategory();          // weapon | armor | material-component | gear | transport
    Long getUnitCostCp();          // value in copper (authored in gold)
    BigDecimal getWeight();        // pounds
    String getDamage();            // weapons, e.g. "1d8 slashing"
    String getArmorClass();        // armor, e.g. "14 + Dex modifier (max 2)"

    void setCategory(String category);
    void setUnitCostCp(Long unitCostCp);
    void setWeight(BigDecimal weight);
    void setDamage(String damage);
    void setArmorClass(String armorClass);
}
