package com.moo.charactermanagerservice.repositories;

import com.moo.charactermanagerservice.models.SessionLongRestResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SessionLongRestResultRepository extends JpaRepository<SessionLongRestResult, Long> {

    /** The session's latest long rest outcome — the unique index (V52) guarantees at most one. */
    Optional<SessionLongRestResult> findBySessionId(Long sessionId);

    @Modifying
    void deleteBySessionId(Long sessionId);
}
