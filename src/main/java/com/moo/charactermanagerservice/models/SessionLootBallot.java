package com.moo.charactermanagerservice.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;

import java.io.Serializable;

/**
 * One seated character's ballot on a locked loot split. {@code vote}: null =
 * not yet voted, true = ✓ agree, false = ✗ object. {@code pvp} only matters on
 * a ✗: null = still deciding, true = PvP (cancels the loot for everyone),
 * false = object without fighting. A ballot is "settled" once it is a ✓ or a
 * ✗ with a PvP answer.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@Entity
@Table(name = "session_loot_ballot")
public class SessionLootBallot implements Serializable {

    public SessionLootBallot() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long sessionLootId;
    private Long participantId;
    private String displayName;
    private Boolean vote;
    private Boolean pvp;

    public void setId(Long id) { this.id = id; }
    public void setSessionLootId(Long sessionLootId) { this.sessionLootId = sessionLootId; }
    public void setParticipantId(Long participantId) { this.participantId = participantId; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setVote(Boolean vote) { this.vote = vote; }
    public void setPvp(Boolean pvp) { this.pvp = pvp; }

    /** ✓, or ✗ with a PvP answer — this ballot needs nothing more from its player. */
    public boolean isSettled() {
        return Boolean.TRUE.equals(vote) || (Boolean.FALSE.equals(vote) && pvp != null);
    }

    public boolean isPvp() {
        return Boolean.FALSE.equals(vote) && Boolean.TRUE.equals(pvp);
    }
}
