package com.moo.charactermanagerservice.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * One line in a {@link Shop}. Exactly one of two kinds (a table CHECK holds it):
 *
 * <ul>
 *   <li>a catalog item — {@code catalogItemKey} → {@code srd_item.item_key},
 *       with an optional price override ({@code priceCp} null = the catalog
 *       price);</li>
 *   <li>a custom item the DM wrote out — {@code customName} plus its own stats
 *       (category, weight, bulk, the weapon damage or armor class, and a
 *       description in {@code customNotes}). Its {@code priceCp} IS the price:
 *       there is no catalog entry to fall back on.</li>
 * </ul>
 *
 * There is no quantity column — curated shops are unlimited until the deferred
 * finite-stock work lands.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@Entity
@Table(name = "shop_item")
public class ShopItem implements Serializable {

    public ShopItem() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long shopId;
    private String catalogItemKey; // null for custom items
    private Long priceCp;          // catalog line: null = inherit catalog price; custom line: the price

    // Custom-item fields (all null on a catalog line — the catalog is authoritative).
    private String customName;
    private String customNotes;
    private String category;       // weapon | armor | material-component | gear | transport | treasure
    private BigDecimal weight;     // pounds
    private BigDecimal bulk;       // explicit bulk; null = derive from weight
    private String damage;         // weapons, e.g. "1d8 slashing"
    private String armorClass;     // armor, e.g. "14 + Dex modifier (max 2)"

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public void setId(Long id) { this.id = id; }
    public void setShopId(Long shopId) { this.shopId = shopId; }
    public void setCatalogItemKey(String catalogItemKey) { this.catalogItemKey = catalogItemKey; }
    public void setPriceCp(Long priceCp) { this.priceCp = priceCp; }
    public void setCustomName(String customName) { this.customName = customName; }
    public void setCustomNotes(String customNotes) { this.customNotes = customNotes; }
    public void setCategory(String category) { this.category = category; }
    public void setWeight(BigDecimal weight) { this.weight = weight; }
    public void setBulk(BigDecimal bulk) { this.bulk = bulk; }
    public void setDamage(String damage) { this.damage = damage; }
    public void setArmorClass(String armorClass) { this.armorClass = armorClass; }

    /** True for a line the DM wrote out; false for a catalog reference. */
    public boolean isCustom() { return catalogItemKey == null; }
}
