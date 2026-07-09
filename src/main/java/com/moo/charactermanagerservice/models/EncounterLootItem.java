package com.moo.charactermanagerservice.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;

import java.io.Serializable;
import java.time.Instant;

/**
 * One loot line attached to a curated {@link Encounter} — the DM's prepped
 * spoils, edited on the campaign dashboard. Either a catalog reference
 * ({@code catalogItemKey}) or a free-hand custom item ({@code customName} +
 * {@code customNotes}), never both (CHECK in V31). Copied — never moved — into
 * a {@link SessionLoot} pool when the DM opens loot from this encounter, so an
 * encounter's prep survives being dropped any number of times.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@Entity
@Table(name = "encounter_loot_item")
public class EncounterLootItem implements Serializable {

    public EncounterLootItem() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long encounterId;
    private String catalogItemKey; // null for custom items
    private String customName;     // null for catalog items
    private String customNotes;
    private int qty = 1;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public void setId(Long id) { this.id = id; }
    public void setEncounterId(Long encounterId) { this.encounterId = encounterId; }
    public void setCatalogItemKey(String catalogItemKey) { this.catalogItemKey = catalogItemKey; }
    public void setCustomName(String customName) { this.customName = customName; }
    public void setCustomNotes(String customNotes) { this.customNotes = customNotes; }
    public void setQty(int qty) { this.qty = qty; }
}
