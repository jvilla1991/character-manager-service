package com.moo.charactermanagerservice.repositories;

import com.moo.charactermanagerservice.models.SessionCombatLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

@Repository
public interface SessionCombatLogRepository extends JpaRepository<SessionCombatLog, Long> {

    /** One page of a session's log, newest first (ids rise with time). */
    Page<SessionCombatLog> findBySessionIdOrderByIdDesc(Long sessionId, Pageable pageable);

    @Modifying
    void deleteBySessionId(Long sessionId);
}
