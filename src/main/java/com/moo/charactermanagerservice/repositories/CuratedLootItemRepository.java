package com.moo.charactermanagerservice.repositories;

import com.moo.charactermanagerservice.models.CuratedLootItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CuratedLootItemRepository extends JpaRepository<CuratedLootItem, Long> {

    List<CuratedLootItem> findByLootIdOrderByIdAsc(Long lootId);

    long countByLootId(Long lootId);
}
