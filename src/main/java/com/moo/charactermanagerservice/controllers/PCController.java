package com.moo.charactermanagerservice.controllers;

import com.moo.charactermanagerservice.dto.User;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.services.PCService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/pc")
public class PCController {

    @Autowired
    private PCService pcService;

    @GetMapping("/all")
    public ResponseEntity<List<PC>> getAllPCsForUser(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        List<PC> pcs = pcService.findAllPCsForUser(user.getUuid());
        return new ResponseEntity<>(pcs, HttpStatus.OK);
    }

    @GetMapping("/find/{id}")
    public ResponseEntity<PC> getPC(@PathVariable Long id) {
        PC pc = pcService.findPCById(id)
                .orElseThrow(() -> new RuntimeException("PC not found with ID: " + id));
        return new ResponseEntity<>(pc, HttpStatus.OK);
    }

    @PostMapping("/add")
    public ResponseEntity<PC> createPC(Authentication authentication, @RequestBody PC pc) {
        User user = (User) authentication.getPrincipal();
        pc.setUserId(user.getUuid());
        pc.setPlayerName(user.getFirstName());
        PC newPC = pcService.addPC(pc);
        return new ResponseEntity<>(newPC, HttpStatus.CREATED);
    }

    @PutMapping("/update")
    public ResponseEntity<PC> updatePC(@RequestBody PC pc) {
        PC updatedPC = pcService.updatePC(pc);
        return new ResponseEntity<>(updatedPC, HttpStatus.CREATED);
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Void> deletePC(@PathVariable Long id) {
        pcService.deletePC(id);
        return new ResponseEntity<>(HttpStatus.OK);
    }
}
