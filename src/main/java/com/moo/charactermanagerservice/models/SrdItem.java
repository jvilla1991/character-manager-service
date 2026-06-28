package com.moo.charactermanagerservice.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * One entry in the read-only SRD reference catalog (seeded by Flyway, never
 * mutated at runtime). {@code category} discriminates the slice
 * (WEAPON / ARMOR / MATERIAL_COMPONENT); slice-specific attributes live in the
 * {@code details} JSON-TEXT column, matching the house JSON-as-TEXT style.
 * Prices are stored in copper ({@code costCp}) so the coin math stays
 * integer-only (1 gp = 100 cp).
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@Entity
@Table(name = "srd_item")
public class SrdItem implements Serializable {

    public SrdItem() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String itemKey;
    private String name;
    private String category;
    private Long costCp;
    private BigDecimal weight;
    private String source;

    @Column(columnDefinition = "TEXT")
    private String details;

    public void setId(Long id) { this.id = id; }
    public void setItemKey(String itemKey) { this.itemKey = itemKey; }
    public void setName(String name) { this.name = name; }
    public void setCategory(String category) { this.category = category; }
    public void setCostCp(Long costCp) { this.costCp = costCp; }
    public void setWeight(BigDecimal weight) { this.weight = weight; }
    public void setSource(String source) { this.source = source; }
    public void setDetails(String details) { this.details = details; }
}
