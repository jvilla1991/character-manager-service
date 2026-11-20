package com.moo.charactermanagerservice.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * One claimable line in a {@link SessionLoot} pool. Same catalog-or-custom shape
 * (and custom-item {@link LootLineAttributes}) as {@link CuratedLootItem};
 * {@code qtyRemaining} is the only claim state (first-come-first-served — who
 * took what is recorded in the pc activity log). Claims decrement it under a
 * pessimistic row lock so two players can't take the last one.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@Entity
@Table(name = "session_loot_item")
public class SessionLootItem implements Serializable, LootLineAttributes {

    public SessionLootItem() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long sessionLootId;
    private String catalogItemKey; // null for custom items
    private String customName;     // null for catalog items
    private String customNotes;
    private int qty = 1;
    private int qtyRemaining = 1;

    // Custom-item attributes copied from the curated line (null for catalog
    // lines and for legacy rows created before V35).
    private String category;
    private Long unitCostCp;
    private BigDecimal weight;
    private BigDecimal bulk;
    private String damage;
    private String armorClass;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public void setId(Long id) { this.id = id; }
    public void setSessionLootId(Long sessionLootId) { this.sessionLootId = sessionLootId; }
    public void setCatalogItemKey(String catalogItemKey) { this.catalogItemKey = catalogItemKey; }
    public void setCustomName(String customName) { this.customName = customName; }
    public void setCustomNotes(String customNotes) { this.customNotes = customNotes; }
    public void setQty(int qty) { this.qty = qty; }
    public void setQtyRemaining(int qtyRemaining) { this.qtyRemaining = qtyRemaining; }
    @Override public void setCategory(String category) { this.category = category; }
    @Override public void setUnitCostCp(Long unitCostCp) { this.unitCostCp = unitCostCp; }
    @Override public void setWeight(BigDecimal weight) { this.weight = weight; }
    @Override public void setBulk(BigDecimal bulk) { this.bulk = bulk; }
    @Override public void setDamage(String damage) { this.damage = damage; }
    @Override public void setArmorClass(String armorClass) { this.armorClass = armorClass; }
}
