package com.moo.charactermanagerservice.repositories;

import com.moo.charactermanagerservice.models.SessionRestVoteBallot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SessionRestVoteBallotRepository extends JpaRepository<SessionRestVoteBallot, Long> {

    /** Insertion order = seating order at vote start; the modal grid renders this verbatim. */
    List<SessionRestVoteBallot> findByVoteIdOrderByIdAsc(Long voteId);
}
