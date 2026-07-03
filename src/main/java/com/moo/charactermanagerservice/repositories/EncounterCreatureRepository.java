package com.moo.charactermanagerservice.repositories;

import com.moo.charactermanagerservice.models.EncounterCreature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EncounterCreatureRepository extends JpaRepository<EncounterCreature, Long> {

    /** An encounter's creature lines, oldest first (insertion order). */
    List<EncounterCreature> findByEncounterIdOrderByIdAsc(Long encounterId);

    /** Count an encounter's creature lines (for the summary view). */
    int countByEncounterId(Long encounterId);
}
