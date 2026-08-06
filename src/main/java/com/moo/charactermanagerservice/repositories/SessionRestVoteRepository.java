package com.moo.charactermanagerservice.repositories;

import com.moo.charactermanagerservice.models.RestVoteStatus;
import com.moo.charactermanagerservice.models.SessionRestVote;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SessionRestVoteRepository extends JpaRepository<SessionRestVote, Long> {

    /** Cheap ACTIVE probe for the poll path — hits the partial unique index. */
    Optional<SessionRestVote> findBySessionIdAndStatus(Long sessionId, RestVoteStatus status);

    /** Latest vote regardless of status — terminal votes ride the snapshot so
     *  clients can show the outcome until the next vote replaces them. */
    Optional<SessionRestVote> findTopBySessionIdOrderByIdDesc(Long sessionId);

    /**
     * Load a vote under a pessimistic write lock ({@code SELECT ... FOR UPDATE})
     * so concurrent ballot casts, the DM override, and the lazy expiry check in
     * the poll path serialize — exactly one caller resolves the vote. Lock
     * order where a session save follows: vote row first, then session (fixed,
     * mirrors the loot-then-pc convention). Must be called inside a transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from SessionRestVote v where v.id = :id")
    Optional<SessionRestVote> findByIdForUpdate(@Param("id") Long id);

    @Modifying
    void deleteBySessionId(Long sessionId);
}
