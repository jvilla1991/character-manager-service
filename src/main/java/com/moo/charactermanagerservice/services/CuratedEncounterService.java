package com.moo.charactermanagerservice.services;

import com.moo.charactermanagerservice.dto.EncounterCreatureView;
import com.moo.charactermanagerservice.dto.EncounterSummaryView;
import com.moo.charactermanagerservice.dto.EncounterView;
import com.moo.charactermanagerservice.models.Encounter;
import com.moo.charactermanagerservice.models.EncounterCreature;
import com.moo.charactermanagerservice.repositories.EncounterCreatureRepository;
import com.moo.charactermanagerservice.repositories.EncounterRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * DM-curated encounters: persistent, reusable encounter definitions a DM builds
 * as a free-hand list of enemy creatures, later loaded into a live session (where
 * each creature becomes an enemy combatant — see {@code SessionService.loadEncounter}).
 * Ownership mirrors {@link CuratedShopService}: an encounter is owned by the
 * campaign's DM and DM-only actions assert it (403 otherwise).
 */
@Service
public class CuratedEncounterService {

    private final EncounterRepository encounterRepository;
    private final EncounterCreatureRepository creatureRepository;
    private final CampaignService campaignService;

    @Autowired
    public CuratedEncounterService(EncounterRepository encounterRepository,
                                   EncounterCreatureRepository creatureRepository,
                                   CampaignService campaignService) {
        this.encounterRepository = encounterRepository;
        this.creatureRepository = creatureRepository;
        this.campaignService = campaignService;
    }

    /** Create an empty curated encounter in a campaign. Campaign-DM only. */
    @Transactional
    public EncounterView createEncounter(Long campaignId, String name, String notes, UUID dmUserId) {
        // Asserts the campaign exists (404) and the caller is its DM (403).
        campaignService.findByIdForDm(campaignId, dmUserId);
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Encounter name is required");
        }
        Encounter encounter = new Encounter();
        encounter.setCampaignId(campaignId);
        encounter.setDmUserId(dmUserId);
        encounter.setName(name.trim());
        encounter.setNotes(notes == null ? null : notes.trim());
        return buildView(encounterRepository.save(encounter));
    }

    /** A campaign's curated encounters (summaries). Campaign-DM only. */
    @Transactional(readOnly = true)
    public List<EncounterSummaryView> listEncounters(Long campaignId, UUID dmUserId) {
        campaignService.findByIdForDm(campaignId, dmUserId);
        return encounterRepository.findByCampaignIdOrderByCreatedAtDesc(campaignId).stream()
                .map(e -> new EncounterSummaryView(e.getId(), e.getCampaignId(), e.getName(),
                        e.getNotes(), creatureRepository.countByEncounterId(e.getId())))
                .toList();
    }

    /** A curated encounter with its creature lines. Owner DM only. */
    @Transactional(readOnly = true)
    public EncounterView getEncounter(Long encounterId, UUID dmUserId) {
        return buildView(requireOwnedEncounter(encounterId, dmUserId));
    }

    /** Rename / re-note a curated encounter. */
    @Transactional
    public EncounterView updateEncounter(Long encounterId, String name, String notes, UUID dmUserId) {
        Encounter encounter = requireOwnedEncounter(encounterId, dmUserId);
        if (name != null && !name.isBlank()) encounter.setName(name.trim());
        encounter.setNotes(notes == null ? null : notes.trim());
        return buildView(encounterRepository.save(encounter));
    }

    /** Delete a curated encounter (its creatures cascade). */
    @Transactional
    public void deleteEncounter(Long encounterId, UUID dmUserId) {
        Encounter encounter = requireOwnedEncounter(encounterId, dmUserId);
        encounterRepository.delete(encounter);
    }

    /** Add a creature line to the encounter. */
    @Transactional
    public EncounterView addCreature(Long encounterId, String name, Short dexModifier,
                                     Short hpMax, Integer quantity, UUID dmUserId) {
        Encounter encounter = requireOwnedEncounter(encounterId, dmUserId);
        validateCreature(name, dexModifier, quantity);

        EncounterCreature creature = new EncounterCreature();
        creature.setEncounterId(encounterId);
        creature.setName(name.trim());
        creature.setDexModifier(dexModifier);
        creature.setHpMax(hpMax);
        creature.setQuantity(quantity == null ? 1 : quantity);
        creatureRepository.save(creature);

        touch(encounter);
        return buildView(encounter);
    }

    /** Update a creature line's fields. */
    @Transactional
    public EncounterView updateCreature(Long encounterId, Long creatureId, String name,
                                        Short dexModifier, Short hpMax, Integer quantity, UUID dmUserId) {
        Encounter encounter = requireOwnedEncounter(encounterId, dmUserId);
        validateCreature(name, dexModifier, quantity);
        EncounterCreature creature = requireCreature(encounterId, creatureId);
        creature.setName(name.trim());
        creature.setDexModifier(dexModifier);
        creature.setHpMax(hpMax);
        creature.setQuantity(quantity == null ? 1 : quantity);
        creatureRepository.save(creature);

        touch(encounter);
        return buildView(encounter);
    }

    /** Remove a creature line from the encounter. */
    @Transactional
    public EncounterView removeCreature(Long encounterId, Long creatureId, UUID dmUserId) {
        Encounter encounter = requireOwnedEncounter(encounterId, dmUserId);
        EncounterCreature creature = requireCreature(encounterId, creatureId);
        creatureRepository.delete(creature);

        touch(encounter);
        return buildView(encounter);
    }

    // --- internals -----------------------------------------------------------

    private EncounterView buildView(Encounter encounter) {
        List<EncounterCreatureView> creatures = creatureRepository
                .findByEncounterIdOrderByIdAsc(encounter.getId()).stream()
                .map(c -> new EncounterCreatureView(c.getId(), c.getName(),
                        c.getDexModifier(), c.getHpMax(), c.getQuantity()))
                .toList();
        return new EncounterView(encounter.getId(), encounter.getCampaignId(),
                encounter.getName(), encounter.getNotes(), creatures);
    }

    private Encounter requireOwnedEncounter(Long encounterId, UUID dmUserId) {
        Encounter encounter = encounterRepository.findById(encounterId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Encounter not found with id " + encounterId));
        if (!dmUserId.equals(encounter.getDmUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        return encounter;
    }

    private EncounterCreature requireCreature(Long encounterId, Long creatureId) {
        EncounterCreature creature = creatureRepository.findById(creatureId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Creature not found with id " + creatureId));
        if (!encounterId.equals(creature.getEncounterId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Creature not found in this encounter");
        }
        return creature;
    }

    private void validateCreature(String name, Short dexModifier, Integer quantity) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Creature name is required");
        }
        if (dexModifier == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dexModifier is required");
        }
        if (quantity != null && quantity < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quantity must be at least 1");
        }
    }

    /** Bump the encounter's updated_at so edits to its creatures are reflected. */
    private void touch(Encounter encounter) {
        encounterRepository.save(encounter);
    }
}
