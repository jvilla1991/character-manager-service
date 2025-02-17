package com.moo.charactermanagerservice.controllers;

import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.models.User;
import com.moo.charactermanagerservice.services.PCService;
import com.moo.charactermanagerservice.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// TODO: Need to use Spring Security File
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1/pc")
public class PCController {

    @Autowired
    private PCService pcService;

    @Autowired
    private UserService userService;

    @GetMapping("/all")
    public ResponseEntity<?> getAllPCsForUser(@RequestHeader("Authorization") String authorizationHeader) {
        User user = userService.getUserDetails(authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "User is not authorized or missing"));
        }
        List<PC> pcs = pcService.findAllPCsForUser(user);
        return new ResponseEntity<>(pcs, HttpStatus.OK);
    }

    @CrossOrigin
    @GetMapping
    @RequestMapping("/find/{id}")
    public ResponseEntity<?> getPC(@RequestHeader("Authorization") String authorizationHeader,
                                    @PathVariable Long id) {
        User user = userService.getUserDetails(authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "User is not authorized or missing"));
        }
        PC pc = pcService.findPCById(id)
                .orElseThrow(() -> new RuntimeException("PC not found with ID: " + id));
        return new ResponseEntity<>(pc, HttpStatus.OK);
    }

    @PostMapping("/add")
    public ResponseEntity<?> createPC(@RequestHeader("Authorization") String authorizationHeader,
                                       @RequestBody PC pc) {
        User user = userService.getUserDetails(authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "User is not authorized or missing"));
        }
        pc.setUser(user); // Marrying the Payload to Model/Object
        PC newPC = pcService.addPC(pc);
        return new ResponseEntity<>(newPC, HttpStatus.CREATED);
    }

    @PutMapping("/update")
    public ResponseEntity<?> updatePC(@RequestHeader("Authorization") String authorizationHeader,
                                       @RequestBody PC pc) {
        User user = userService.getUserDetails(authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "User is not authorized or missing"));
        }
        PC updatePC = pcService.updatePC(pc);
        return new ResponseEntity<>(updatePC, HttpStatus.CREATED);
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deletePC(@RequestHeader("Authorization") String authorizationHeader,
                                       @PathVariable Long id) {
        User user = userService.getUserDetails(authorizationHeader);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "User is not authorized or missing"));
        }
        pcService.deletePC(id);
        return new ResponseEntity<>(HttpStatus.OK);
    }

}
