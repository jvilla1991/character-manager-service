package com.moo.charactermanagerservice.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;

import java.io.Serializable;
import java.time.Instant;

/**
 * One creature line in an {@link Encounter}: free-hand enemy data (no SRD catalog
 * reference). {@code dexModifier} is the DM-calculated initiative tie-breaker,
 * {@code hpMax} is optional (null = untracked HP), and {@code quantity} expands
 * into that many numbered enemy combatants when the encounter is loaded into a
 * session (e.g. Goblin 1..Goblin 4).
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@Entity
@Table(name = "encounter_creature")
public class EncounterCreature implements Serializable {

    public EncounterCreature() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long encounterId;
    private String name;
    private Short dexModifier;
    private Short hpMax; // null = untracked HP
    private int quantity = 1;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public void setId(Long id) { this.id = id; }
    public void setEncounterId(Long encounterId) { this.encounterId = encounterId; }
    public void setName(String name) { this.name = name; }
    public void setDexModifier(Short dexModifier) { this.dexModifier = dexModifier; }
    public void setHpMax(Short hpMax) { this.hpMax = hpMax; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}
