package com.moo.charactermanagerservice.controllers;

import com.moo.charactermanagerservice.dto.User;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.services.PCService;
import com.moo.charactermanagerservice.validation.ValidationGroups.OnCreate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
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
        return ResponseEntity.ok(pcService.findAllPCsForUser(user.getUuid()));
    }

    @GetMapping("/find/{id}")
    public ResponseEntity<PC> getPC(@PathVariable Long id, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(pcService.findPCByIdForUser(id, user.getUuid()));
    }

    @PostMapping("/add")
    public ResponseEntity<PC> createPC(Authentication authentication,
                                       @Validated(OnCreate.class) @RequestBody PC pc) {
        User user = (User) authentication.getPrincipal();
        pc.setUserId(user.getUuid());
        pc.setPlayerName(user.getFirstName());
        return ResponseEntity.status(HttpStatus.CREATED).body(pcService.addPC(pc));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PC> updatePC(@PathVariable Long id, Authentication authentication,
                                       @RequestBody PC pc) {
        User user = (User) authentication.getPrincipal();
        pc.setId(id);
        return ResponseEntity.ok(pcService.updatePC(pc, user.getUuid()));
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Void> deletePC(@PathVariable Long id, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        pcService.deletePC(id, user.getUuid());
        return ResponseEntity.noContent().build();
    }
}
