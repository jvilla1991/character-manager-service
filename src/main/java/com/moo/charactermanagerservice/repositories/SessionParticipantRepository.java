package com.moo.charactermanagerservice.repositories;

import com.moo.charactermanagerservice.models.SessionParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SessionParticipantRepository extends JpaRepository<SessionParticipant, Long> {

    /** A session's combatants in initiative (turn) order. */
    List<SessionParticipant> findBySessionIdOrderByOrderIndexAsc(Long sessionId);
}
