package com.moo.charactermanagerservice.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.moo.charactermanagerservice.validation.ValidDndClass;
import com.moo.charactermanagerservice.validation.ValidationGroups.OnCreate;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.io.Serializable;
import java.util.UUID;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@Entity
@Table(name = "pc", schema = "character_manage")
public class PC implements Serializable {

    public PC() {}

    public PC(Builder builder) {
        this.id = builder.id;
        this.clazz = builder.clazz;
        this.level = builder.level;
        this.name = builder.name;
        this.playerName = builder.playerName;
        this.userId = builder.userId;
        this.spells = builder.spells;
    }

    // --- Core identity ---
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(groups = OnCreate.class, message = "Character name must not be blank")
    private String name;

    @NotBlank(groups = OnCreate.class, message = "Class must not be blank")
    @ValidDndClass(groups = OnCreate.class)
    private String clazz;

    private Short level;
    private String playerName;
    private UUID userId;

    // --- Character identity ---
    private String species;
    private String background;
    private String subclass;
    private String alignment;
    private String party;
    private String portraitTint;
    private String portraitInitials;

    // --- Ability scores (decomposed from frontend stats object) ---
    @NotNull(groups = OnCreate.class, message = "Strength score is required")
    @Min(groups = OnCreate.class, value = 3, message = "Ability scores must be at least 3")
    @Max(groups = OnCreate.class, value = 18, message = "Ability scores cannot exceed 18")
    private Short abilityStr;

    @NotNull(groups = OnCreate.class, message = "Dexterity score is required")
    @Min(groups = OnCreate.class, value = 3, message = "Ability scores must be at least 3")
    @Max(groups = OnCreate.class, value = 18, message = "Ability scores cannot exceed 18")
    private Short abilityDex;

    @NotNull(groups = OnCreate.class, message = "Constitution score is required")
    @Min(groups = OnCreate.class, value = 3, message = "Ability scores must be at least 3")
    @Max(groups = OnCreate.class, value = 18, message = "Ability scores cannot exceed 18")
    private Short abilityCon;

    @NotNull(groups = OnCreate.class, message = "Intelligence score is required")
    @Min(groups = OnCreate.class, value = 3, message = "Ability scores must be at least 3")
    @Max(groups = OnCreate.class, value = 18, message = "Ability scores cannot exceed 18")
    private Short abilityInt;

    @NotNull(groups = OnCreate.class, message = "Wisdom score is required")
    @Min(groups = OnCreate.class, value = 3, message = "Ability scores must be at least 3")
    @Max(groups = OnCreate.class, value = 18, message = "Ability scores cannot exceed 18")
    private Short abilityWis;

    @NotNull(groups = OnCreate.class, message = "Charisma score is required")
    @Min(groups = OnCreate.class, value = 3, message = "Ability scores must be at least 3")
    @Max(groups = OnCreate.class, value = 18, message = "Ability scores cannot exceed 18")
    private Short abilityCha;

    // --- Combat stats (decomposed from frontend hp object) ---
    private Short hpMax;
    private Short hpCurrent;
    private Short hpTemp;
    private Short ac;
    private Short initiative;
    private Short speed;
    private Short profBonus;

    // --- JSON-serialized arrays / objects stored as TEXT ---
    @Column(columnDefinition = "TEXT")
    private String spells;

    @Column(columnDefinition = "TEXT")
    private String spellSlots;

    @Column(columnDefinition = "TEXT")
    private String saves;

    @Column(columnDefinition = "TEXT")
    private String skills;

    @Column(columnDefinition = "TEXT")
    private String conditions;

    @Column(columnDefinition = "TEXT")
    private String coins;

    @Column(columnDefinition = "TEXT")
    private String weapons;

    @Column(columnDefinition = "TEXT")
    private String gear;

    @Column(columnDefinition = "TEXT")
    private String features;

    @Column(columnDefinition = "TEXT")
    private String traits;

    @Column(columnDefinition = "TEXT")
    private String languages;

    @Column(columnDefinition = "TEXT")
    private String toolProfs;

    // --- Origin feat (single string — name of the feat) ---
    private String feat;

    // --- Narrative ---
    @Column(columnDefinition = "TEXT")
    private String bio;

    @Column(columnDefinition = "TEXT")
    private String notes;

    // --- Setters (all fields; needed by Jackson + controller) ---

    public void setId(Long id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setClazz(String clazz) { this.clazz = clazz; }
    public void setLevel(Short level) { this.level = level; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public void setSpecies(String species) { this.species = species; }
    public void setBackground(String background) { this.background = background; }
    public void setSubclass(String subclass) { this.subclass = subclass; }
    public void setAlignment(String alignment) { this.alignment = alignment; }
    public void setParty(String party) { this.party = party; }
    public void setPortraitTint(String portraitTint) { this.portraitTint = portraitTint; }
    public void setPortraitInitials(String portraitInitials) { this.portraitInitials = portraitInitials; }

    public void setAbilityStr(Short abilityStr) { this.abilityStr = abilityStr; }
    public void setAbilityDex(Short abilityDex) { this.abilityDex = abilityDex; }
    public void setAbilityCon(Short abilityCon) { this.abilityCon = abilityCon; }
    public void setAbilityInt(Short abilityInt) { this.abilityInt = abilityInt; }
    public void setAbilityWis(Short abilityWis) { this.abilityWis = abilityWis; }
    public void setAbilityCha(Short abilityCha) { this.abilityCha = abilityCha; }

    public void setHpMax(Short hpMax) { this.hpMax = hpMax; }
    public void setHpCurrent(Short hpCurrent) { this.hpCurrent = hpCurrent; }
    public void setHpTemp(Short hpTemp) { this.hpTemp = hpTemp; }
    public void setAc(Short ac) { this.ac = ac; }
    public void setInitiative(Short initiative) { this.initiative = initiative; }
    public void setSpeed(Short speed) { this.speed = speed; }
    public void setProfBonus(Short profBonus) { this.profBonus = profBonus; }

    public void setSpells(String spells) { this.spells = spells; }
    public void setSpellSlots(String spellSlots) { this.spellSlots = spellSlots; }
    public void setSaves(String saves) { this.saves = saves; }
    public void setSkills(String skills) { this.skills = skills; }
    public void setConditions(String conditions) { this.conditions = conditions; }
    public void setCoins(String coins) { this.coins = coins; }
    public void setWeapons(String weapons) { this.weapons = weapons; }
    public void setGear(String gear) { this.gear = gear; }
    public void setFeatures(String features) { this.features = features; }
    public void setTraits(String traits) { this.traits = traits; }
    public void setLanguages(String languages) { this.languages = languages; }
    public void setToolProfs(String toolProfs) { this.toolProfs = toolProfs; }
    public void setFeat(String feat) { this.feat = feat; }
    public void setBio(String bio) { this.bio = bio; }
    public void setNotes(String notes) { this.notes = notes; }

    // --- Builder (kept for backward compat; not used by current REST layer) ---
    public static class Builder {
        private Long id;
        private String clazz;
        private Short level;
        private String name;
        private String playerName;
        private UUID userId;
        private String spells;

        public static Builder newInstance() { return new Builder(); }
        private Builder() {}

        public Builder setId(Long id) { this.id = id; return this; }
        public Builder setClazz(String clazz) { this.clazz = clazz; return this; }
        public Builder setLevel(Short level) { this.level = level; return this; }
        public Builder setName(String name) { this.name = name; return this; }
        public Builder setPlayerName(String playerName) { this.playerName = playerName; return this; }
        public Builder setUserId(UUID userId) { this.userId = userId; return this; }
        public Builder setSpells(String spells) { this.spells = spells; return this; }
        public PC build() { return new PC(this); }
    }
}
