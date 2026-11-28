package com.moo.charactermanagerservice.models;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;
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
@Table(name = "pc")
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
    // Experience points. Accumulates only (DM awards in Session Mode); level-up
    // stays the explicit player-driven flow. Defaults to 0 so inserts never send
    // null against the NOT NULL column (see V17).
    private Integer xp = 0;
    // DM-granted level-up permission (V34): lets the player level once without
    // meeting the XP threshold; cleared when the level-up is applied. Server-
    // owned — generic update bodies never change it (preserveServerOwnedColumns).
    private Boolean pendingLevelGrant = false;
    private String playerName;
    private UUID userId;

    // Campaign this character is bound to (nullable). Set via PUT /api/v1/pc/{id}
    // by the PC's owner; cleared to null to leave a campaign.
    private Long campaignId;

    // --- Character identity ---
    private String species;
    // Species origin (V56), set by the create wizard for a World of Warcraft species and null
    // for everyone else: the content source the species comes from ("World of Warcraft"), the
    // lineage chosen for it ("Night Elf", "Taunka") and its size ("Small" / "Medium"). Free
    // text like species — the client's species catalog owns the allowed values. An update body
    // that omits them keeps the stored ones while the species is unchanged
    // (PCService.preserveServerOwnedColumns).
    @Column(columnDefinition = "TEXT")
    private String speciesSource;
    @Column(columnDefinition = "TEXT")
    private String lineage;
    @Column(columnDefinition = "TEXT")
    private String size;
    private String background;
    private String subclass;
    // Hero-class specialization (V46, formerly the Death Knight "rune" from V45): e.g.
    // "Blood"/"Frost"/"Unholy" for the Oath of the Ebon Blade, "Devourer"/"Havoc"/
    // "Vengeance" for the Demon Hunter. Only allowed non-null on a hero subclass, and only
    // one of that subclass's values — validated in HeroClassRules. The "rune" alias accepts
    // the old JSON name from SPA builds deployed before V46 (input only; output is
    // "specialization"); drop it once every client sends "specialization".
    @JsonAlias("rune")
    private String specialization;
    // WoW Hunter pet family (V51): the family of a Beast Master hunter's Primal Companion, e.g.
    // "Wolf" or "Bird of Prey". Only allowed non-null on a WoW Hunter (ranger + Beast Mastery/
    // Marksmanship/Survival) whose subclass is Beast Master, and only one of
    // ClassProgression.PET_FAMILIES — validated in HeroClassRules.
    private String petFamily;
    private String alignment;
    private String portraitTint;
    private String portraitInitials;
    // Uploaded portrait's cache key (V62): null = no portrait. Server-owned — only the
    // portrait endpoints (PcPortraitService) change it; generic update bodies never do
    // (PCService.preserveServerOwnedColumns). The image itself lives in pc_portrait.
    private Long portraitVersion;

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

    // 2024 PHB Exhaustion level, 0-6 (V37): each level is -2 to all d20 Tests
    // and -5 ft Speed; level 6 kills the character. NULL = never tracked (0).
    // Preserved when an update body carries null (like survival), so a payload
    // built without the field can't wipe a real level.
    private Short exhaustion;

    // Spent hit dice (V38, max = level). Mutated only by the session short-rest
    // spend and long-rest restore endpoints — fully server-owned, so generic
    // update bodies never change it (preserveServerOwnedColumns).
    private Short hitDiceUsed = 0;

    // Heroic Inspiration meter (V38): the DM awards pips one at a time; the
    // fifth pip resets the meter and grants Heroic Inspiration. Both fully
    // server-owned (only the inspiration endpoints mutate them).
    private Short inspirationPips = 0;
    private Boolean heroicInspiration = false;

    // --- JSON-serialized arrays / objects stored as TEXT ---
    @Column(columnDefinition = "TEXT")
    private String spells;

    // Spell slots by spell level: {"1":{"max":4,"used":1},"2":{"max":3,"used":0}}. The max is
    // server-owned (V58): class table + the DM's slot1..slot9 lines in statAdjustments, rewritten
    // on every save — a max in a request body is ignored. Only "used" is state (SpellSlotState).
    @Column(columnDefinition = "TEXT")
    private String spellSlots;

    @Column(columnDefinition = "TEXT")
    private String saves;

    @Column(columnDefinition = "TEXT")
    private String skills;

    @Column(columnDefinition = "TEXT")
    private String conditions;

    // Conditions the character is immune to (V63), same JSON-array shape as
    // conditions: ["poisoned"]. NULL = none. A body carrying null keeps the
    // stored list, and an immunity whose condition is active is dropped
    // (see PCService, ConditionRules).
    @Column(columnDefinition = "TEXT")
    private String conditionImmunities;

    @Column(columnDefinition = "TEXT")
    private String coins;

    @Column(columnDefinition = "TEXT")
    private String weapons;

    @Column(columnDefinition = "TEXT")
    private String gear;

    // Structured inventory (purchased items land here). Mirrors the spells column:
    // a JSON array of objects, (de)serialized by the service/frontend, not Hibernate.
    @Column(columnDefinition = "TEXT")
    private String inventory;

    // Darker Dungeons survival conditions: {"hunger":0,"thirst":0,"fatigue":0},
    // stages 0-6; NULL = never tracked (all zeros). Preserved when an update body
    // omits it, so a stale client can't wipe in-session changes.
    @Column(columnDefinition = "TEXT")
    private String survival;

    @Column(columnDefinition = "TEXT")
    private String features;

    @Column(columnDefinition = "TEXT")
    private String traits;

    @Column(columnDefinition = "TEXT")
    private String languages;

    @Column(columnDefinition = "TEXT")
    private String toolProfs;

    // DM-added lines on calculated numbers (V57), keyed by the number they adjust:
    // {"ac":[{"value":1,"label":"Taunka"}]}. NULL = none. The number's own column
    // (e.g. ac) holds the finished total, these lines included — the SPA works it
    // out. In a campaign only the DM's update changes the lines; a body carrying
    // null keeps the stored value (see PCService, StatAdjustmentRules). The keys
    // slot1..slot9 are the exception to "the SPA works it out": the server adds
    // those lines to the spell-slot maximum itself (SpellSlotState).
    @Column(columnDefinition = "TEXT")
    private String statAdjustments;

    // --- Origin feat (single string — name of the feat) ---
    private String feat;

    // --- Narrative ---
    @Column(columnDefinition = "TEXT")
    private String bio;

    @Column(columnDefinition = "TEXT")
    private String notes;

    // Death Knight Runeforging (V53): the NAME of the melee weapon the rune is
    // inscribed on — weapons on the sheet have no id, they are told apart by
    // name. Set as a long rest ends, or from the sheet. A value that no longer
    // names a melee weapon the character has counts as unset (LongRestRules).
    private String runeWeapon;

    // Limited-use class features (V55): how much of each is USED, as JSON keyed
    // by resource — {"secondWind":1,"layOnHands":15}; NULL = nothing used. The
    // maximum is computed (progression/ClassResources), never stored. Written
    // by the sheet, and by the session's rests.
    @Column(columnDefinition = "TEXT")
    private String classResources;

    // Choices a subclass lets the player make (V65), as JSON:
    // {"skills":["Insight"],"arcaneShots":["Banishing Shot","Seeking Shot"],
    //  "vestigeType":"Celestial","vestigeDomain":"Life"}; NULL = none. Server-owned: only a
    // level-up and PUT /pc/{id}/subclass-choices write it (progression/SubclassChoices holds the
    // rules). A response carries it as a JSON object, not as text, and a value in a request
    // body is never read: whole-sheet saves keep the stored choices (PCService).
    @Column(columnDefinition = "TEXT")
    @JsonRawValue
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String subclassChoices;

    // --- Setters (all fields; needed by Jackson + controller) ---

    public void setId(Long id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setClazz(String clazz) { this.clazz = clazz; }
    public void setLevel(Short level) { this.level = level; }
    public void setXp(Integer xp) { this.xp = xp; }
    public void setPendingLevelGrant(Boolean pendingLevelGrant) { this.pendingLevelGrant = pendingLevelGrant; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public void setCampaignId(Long campaignId) { this.campaignId = campaignId; }

    public void setSpecies(String species) { this.species = species; }
    public void setSpeciesSource(String speciesSource) { this.speciesSource = speciesSource; }
    public void setLineage(String lineage) { this.lineage = lineage; }
    public void setSize(String size) { this.size = size; }
    public void setBackground(String background) { this.background = background; }
    public void setSubclass(String subclass) { this.subclass = subclass; }
    public void setSpecialization(String specialization) { this.specialization = specialization; }
    public void setPetFamily(String petFamily) { this.petFamily = petFamily; }
    public void setAlignment(String alignment) { this.alignment = alignment; }
    public void setPortraitTint(String portraitTint) { this.portraitTint = portraitTint; }
    public void setPortraitInitials(String portraitInitials) { this.portraitInitials = portraitInitials; }
    public void setPortraitVersion(Long portraitVersion) { this.portraitVersion = portraitVersion; }

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
    public void setExhaustion(Short exhaustion) { this.exhaustion = exhaustion; }
    public void setHitDiceUsed(Short hitDiceUsed) { this.hitDiceUsed = hitDiceUsed; }
    public void setInspirationPips(Short inspirationPips) { this.inspirationPips = inspirationPips; }
    public void setHeroicInspiration(Boolean heroicInspiration) { this.heroicInspiration = heroicInspiration; }

    public void setSpells(String spells) { this.spells = spells; }
    public void setSpellSlots(String spellSlots) { this.spellSlots = spellSlots; }
    public void setSaves(String saves) { this.saves = saves; }
    public void setSkills(String skills) { this.skills = skills; }
    public void setConditions(String conditions) { this.conditions = conditions; }
    public void setConditionImmunities(String conditionImmunities) { this.conditionImmunities = conditionImmunities; }
    public void setCoins(String coins) { this.coins = coins; }
    public void setWeapons(String weapons) { this.weapons = weapons; }
    public void setGear(String gear) { this.gear = gear; }
    public void setInventory(String inventory) { this.inventory = inventory; }
    public void setSurvival(String survival) { this.survival = survival; }
    public void setFeatures(String features) { this.features = features; }
    public void setTraits(String traits) { this.traits = traits; }
    public void setLanguages(String languages) { this.languages = languages; }
    public void setToolProfs(String toolProfs) { this.toolProfs = toolProfs; }
    public void setStatAdjustments(String statAdjustments) { this.statAdjustments = statAdjustments; }
    public void setFeat(String feat) { this.feat = feat; }
    public void setBio(String bio) { this.bio = bio; }
    public void setNotes(String notes) { this.notes = notes; }
    public void setRuneWeapon(String runeWeapon) { this.runeWeapon = runeWeapon; }
    public void setClassResources(String classResources) { this.classResources = classResources; }
    public void setSubclassChoices(String subclassChoices) { this.subclassChoices = subclassChoices; }

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
