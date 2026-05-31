package com.moo.charactermanagerservice.services;

import com.moo.charactermanagerservice.exceptions.PCNotFoundException;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.repositories.PCRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class PCService {

    private final PCRepository pcRepository;

    @Autowired
    public PCService(PCRepository pcRepository) {
        this.pcRepository = pcRepository;
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
