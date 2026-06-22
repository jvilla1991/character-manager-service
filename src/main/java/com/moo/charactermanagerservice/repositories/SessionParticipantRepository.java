package com.moo.charactermanagerservice.repositories;

import com.moo.charactermanagerservice.models.SessionParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SessionParticipantRepository extends JpaRepository<SessionParticipant, Long> {

    /** A session's combatants in initiative (turn) order. */
    List<SessionParticipant> findBySessionIdOrderByOrderIndexAsc(Long sessionId);

    /** A PC's existing seat in a session, if any — used to keep join idempotent. */
    Optional<SessionParticipant> findBySessionIdAndPcId(Long sessionId, Long pcId);
}
