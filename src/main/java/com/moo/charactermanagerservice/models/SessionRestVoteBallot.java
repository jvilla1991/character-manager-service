package com.moo.charactermanagerservice.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;

import java.io.Serializable;

/**
 * One seated PC participant's ballot in a {@link SessionRestVote}. Created for
 * every PC participant when the vote opens; {@code vote} stays null (the
 * yellow "?") until the player answers. The DB row cascades away if the
 * participant leaves, shrinking the unanimity set.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@Entity
@Table(name = "session_rest_vote_ballot")
public class SessionRestVoteBallot implements Serializable {

    public SessionRestVoteBallot() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long voteId;
    private Long participantId;
    private String displayName;
    private Boolean vote; // null = not yet voted

    public void setId(Long id) { this.id = id; }
    public void setVoteId(Long voteId) { this.voteId = voteId; }
    public void setParticipantId(Long participantId) { this.participantId = participantId; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setVote(Boolean vote) { this.vote = vote; }
}
