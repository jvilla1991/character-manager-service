package com.moo.charactermanagerservice.repositories;

import com.moo.charactermanagerservice.models.SrdItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SrdItemRepository extends JpaRepository<SrdItem, Long> {

    /** A catalog slice (e.g. all WEAPONs), alphabetized for display. */
    List<SrdItem> findByCategoryOrderByNameAsc(String category);

    /** Resolve a single catalog entry by its stable slug (used at purchase). */
    Optional<SrdItem> findByItemKey(String itemKey);
}
