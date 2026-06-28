package com.moo.charactermanagerservice.repositories;

import com.moo.charactermanagerservice.models.SessionShopAttendee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SessionShopAttendeeRepository extends JpaRepository<SessionShopAttendee, Long> {

    /** All characters currently at a shop. */
    List<SessionShopAttendee> findBySessionShopId(Long sessionShopId);

    /** A specific character's attendance row, if present (visibility/purchase check). */
    Optional<SessionShopAttendee> findBySessionShopIdAndPcId(Long sessionShopId, Long pcId);

    /** Clear the roster when re-targeting or closing the shop. */
    void deleteBySessionShopId(Long sessionShopId);
}
