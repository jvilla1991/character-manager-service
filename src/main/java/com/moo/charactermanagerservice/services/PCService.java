package com.moo.charactermanagerservice.services;

import com.moo.charactermanagerservice.exceptions.PCNotFoundException;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.models.User;
import com.moo.charactermanagerservice.repositories.PCRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PCService {

    private final PCRepository pcRepository;

    @Autowired
    public PCService(PCRepository pcRepository) {
        this.pcRepository = pcRepository;
    }

    public PC addPC(PC pc) {
        pc.setPlayerName(pc.getPlayerName());
        pc.setLevel((short)1);
        return pcRepository.saveAndFlush(pc);
    }

    public Optional<PC> findPCById(Long id) {
        return Optional.ofNullable(pcRepository.findById(id)
                .orElseThrow(() -> new PCNotFoundException("PC by id " + id + " was not found")));
    }

//    public List<PC> findAllPCs() {
//        return pcRepository.findAll();
//    }

    public List<PC> findAllPCsForUser(User user) {
        return pcRepository.findByUser(user);
    }

    public PC updatePC(PC pc) {
        return pcRepository.save(pc);
    }

    public void deletePC(Long id) {
        pcRepository.deleteById(id);
    }
}

