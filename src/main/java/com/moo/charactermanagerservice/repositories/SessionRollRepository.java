package com.moo.charactermanagerservice.repositories;

import com.moo.charactermanagerservice.models.SessionRoll;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SessionRollRepository extends JpaRepository<SessionRoll, Long> {

    /** Newest-first, capped — used to build the poll payload (DM sees this
     *  directly; a player's view is filtered further in SessionService). */
    List<SessionRoll> findTop50BySessionIdOrderByCreatedAtDescIdDesc(Long sessionId);

    @Modifying
    void deleteBySessionId(Long sessionId);
}
