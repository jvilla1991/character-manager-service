package com.moo.charactermanagerservice.repositories;

import com.moo.charactermanagerservice.models.EncounterLootItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EncounterLootItemRepository extends JpaRepository<EncounterLootItem, Long> {

    List<EncounterLootItem> findByEncounterIdOrderByIdAsc(Long encounterId);
}
