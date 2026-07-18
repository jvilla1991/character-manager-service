package com.moo.charactermanagerservice.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * One line in a {@link CuratedLoot} list. Either a catalog reference
 * ({@code catalogItemKey}) or a free-hand custom item ({@code customName} +
 * optional notes and {@link LootLineAttributes}), never both (CHECK in V35).
 * Copied — never moved — into a {@link SessionLoot} pool when the DM drops
 * this list in a session, so the curated prep survives being dropped any
 * number of times.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@Entity
@Table(name = "curated_loot_item")
public class CuratedLootItem implements Serializable, LootLineAttributes {

    public CuratedLootItem() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long lootId;
    private String catalogItemKey; // null for custom items
    private String customName;     // null for catalog items
    private String customNotes;
    private int qty = 1;

    // Custom-item attributes (null for catalog lines — the catalog is authoritative).
    private String category;
    private Long unitCostCp;
    private BigDecimal weight;
    private String damage;
    private String armorClass;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public void setId(Long id) { this.id = id; }
    public void setLootId(Long lootId) { this.lootId = lootId; }
    public void setCatalogItemKey(String catalogItemKey) { this.catalogItemKey = catalogItemKey; }
    public void setCustomName(String customName) { this.customName = customName; }
    public void setCustomNotes(String customNotes) { this.customNotes = customNotes; }
    public void setQty(int qty) { this.qty = qty; }
    @Override public void setCategory(String category) { this.category = category; }
    @Override public void setUnitCostCp(Long unitCostCp) { this.unitCostCp = unitCostCp; }
    @Override public void setWeight(BigDecimal weight) { this.weight = weight; }
    @Override public void setDamage(String damage) { this.damage = damage; }
    @Override public void setArmorClass(String armorClass) { this.armorClass = armorClass; }
}
