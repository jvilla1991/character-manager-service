package com.moo.charactermanagerservice.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;

import java.io.Serializable;

/**
 * One share in a party loot split: either {@code qty} of a pool line
 * ({@code lootItemId} set) or {@code coinCp} of the coin pile ({@code
 * lootItemId} null) given to a seated character. Whatever no allocation
 * covers is still "in the pile". Rows cascade away with the pool, the line or
 * the participant (V48).
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@Entity
@Table(name = "session_loot_allocation")
public class SessionLootAllocation implements Serializable {

    public SessionLootAllocation() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long sessionLootId;
    private Long participantId;
    private Long lootItemId; // null = coins
    private int qty;
    private long coinCp;

    public void setId(Long id) { this.id = id; }
    public void setSessionLootId(Long sessionLootId) { this.sessionLootId = sessionLootId; }
    public void setParticipantId(Long participantId) { this.participantId = participantId; }
    public void setLootItemId(Long lootItemId) { this.lootItemId = lootItemId; }
    public void setQty(int qty) { this.qty = qty; }
    public void setCoinCp(long coinCp) { this.coinCp = coinCp; }
}
