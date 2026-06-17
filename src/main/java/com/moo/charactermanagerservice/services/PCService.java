package com.moo.charactermanagerservice.services;

import com.moo.charactermanagerservice.dto.LevelUpPreview;
import com.moo.charactermanagerservice.exceptions.PCNotFoundException;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.repositories.PCRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class PCService {

    private final PCRepository pcRepository;
    private final LevelUpService levelUpService;

    @Autowired
    public PCService(PCRepository pcRepository, LevelUpService levelUpService) {
        this.pcRepository = pcRepository;
        this.levelUpService = levelUpService;
    }

    public PC addPC(PC pc) {
        pc.setLevel((short) 1);
        return pcRepository.saveAndFlush(pc);
    }

    public PC findPCById(Long id) {
        return pcRepository.findById(id)
                .orElseThrow(() -> new PCNotFoundException("PC not found with id " + id));
    }

    public PC findPCByIdForUser(Long id, UUID userId) {
        PC pc = findPCById(id);
        assertOwnership(pc, userId);
        return pc;
    }

    public List<PC> findAllPCsForUser(UUID userId) {
        return pcRepository.findByUserId(userId);
    }

    public PC updatePC(PC pc, UUID userId) {
        PC existing = findPCById(pc.getId());
        assertOwnership(existing, userId);
        return pcRepository.save(pc);
    }

    /**
     * Compute, without persisting, the gains of advancing this PC one level. Enforces ownership.
     * Drives the SPA's pre-confirmation preview so the client never computes D&D rules itself.
     */
    public LevelUpPreview previewLevelUp(Long id, UUID userId) {
        PC pc = findPCByIdForUser(id, userId);
        return levelUpService.preview(pc);
    }

    /**
     * Advance this PC one level and persist. Ownership is enforced and the rules are applied
     * server-side ({@link LevelUpService}); the client supplies only choices (e.g. a subclass
     * selection), never computed stats. {@code @Transactional} keeps the load-modify-save atomic
     * so a level can't be applied twice from a single request.
     *
     * @param chosenSubclass the player's subclass selection, or {@code null} when none applies
     */
    @Transactional
    public PC levelUpPC(Long id, UUID userId, String chosenSubclass) {
        PC pc = findPCByIdForUser(id, userId);
        levelUpService.applyLevelUp(pc, chosenSubclass);
        return pcRepository.save(pc);
    }

    public void deletePC(Long id, UUID userId) {
        PC pc = findPCById(id);
        assertOwnership(pc, userId);
        pcRepository.deleteById(id);
    }

    private void assertOwnership(PC pc, UUID userId) {
        if (!userId.equals(pc.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
    }
}
