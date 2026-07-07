package com.moo.charactermanagerservice.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.moo.charactermanagerservice.validation.ValidationGroups.OnCreate;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * A campaign a Dungeon Master runs. Owned by {@code dmUserId}; members are PCs
 * bound via {@code PC.campaignId}. DM-only fields ({@code secrets}) are returned
 * only to the owning DM (enforced in the service/controller, not the entity).
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@Entity
@Table(name = "campaign")
public class Campaign implements Serializable {

    public Campaign() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private UUID dmUserId;

    @NotBlank(groups = OnCreate.class, message = "Campaign name must not be blank")
    private String name;

    private String party;
    private String setting;
    private Short session;

    @Column(name = "next_session")
    private String nextSession;

    private String arc;
    private String tint;

    @Column(name = "invite_code")
    private String inviteCode;

    @Column(columnDefinition = "TEXT")
    private String chronicle;

    @Column(columnDefinition = "TEXT")
    private String secrets;

    @Column(columnDefinition = "TEXT")
    private String threads;   // JSON array of open plot threads

    @Column(name = "variant_rules", columnDefinition = "TEXT")
    private String variantRules;   // JSON object of variant-rule opt-ins, set at creation only

    // In-world clock: {"year":1492,"month":3,"day":12,"timeOfDay":"dawn"}; NULL = never set.
    // Written at creation and by the session time endpoints; pinned on campaign updates.
    @Column(name = "game_time", columnDefinition = "TEXT")
    private String gameTime;

    // Party's current location: {"name":"Neverwinter","type":"Settlement"}; NULL
    // = never set. The DM changes it from Session Mode; broadcast to every sheet.
    @Column(columnDefinition = "TEXT")
    private String location;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // --- Setters (Jackson + service/controller) ---

    public void setId(Long id) { this.id = id; }
    public void setDmUserId(UUID dmUserId) { this.dmUserId = dmUserId; }
    public void setName(String name) { this.name = name; }
    public void setParty(String party) { this.party = party; }
    public void setSetting(String setting) { this.setting = setting; }
    public void setSession(Short session) { this.session = session; }
    public void setNextSession(String nextSession) { this.nextSession = nextSession; }
    public void setArc(String arc) { this.arc = arc; }
    public void setTint(String tint) { this.tint = tint; }
    public void setInviteCode(String inviteCode) { this.inviteCode = inviteCode; }
    public void setChronicle(String chronicle) { this.chronicle = chronicle; }
    public void setSecrets(String secrets) { this.secrets = secrets; }
    public void setThreads(String threads) { this.threads = threads; }
    public void setVariantRules(String variantRules) { this.variantRules = variantRules; }
    public void setGameTime(String gameTime) { this.gameTime = gameTime; }
    public void setLocation(String location) { this.location = location; }
}
