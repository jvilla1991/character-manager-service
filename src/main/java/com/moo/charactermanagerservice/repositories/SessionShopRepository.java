package com.moo.charactermanagerservice.repositories;

import com.moo.charactermanagerservice.models.SessionShop;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SessionShopRepository extends JpaRepository<SessionShop, Long> {

    /** The session's active shop, if any. The unique index (V10) guarantees ≤1. */
    Optional<SessionShop> findBySessionId(Long sessionId);
}
