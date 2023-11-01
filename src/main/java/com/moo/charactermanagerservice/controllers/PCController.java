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

@RestController
@RequestMapping("/api/v1/pc")
public class PCController {

    @Autowired
    private PCService pcService;

    @Autowired
    private UserService userService;

    @GetMapping("/all")
    public ResponseEntity<List<PC>> getAllPCsForUser(@RequestHeader("Authorization") String authorizationHeader) {
        User user = userService.getUserDetails(authorizationHeader);
        List<PC> pcs = pcService.findAllPCsForUser(user.getUuid());
        return new ResponseEntity<>(pcs, HttpStatus.OK);
    }

    @CrossOrigin
    @GetMapping
    @RequestMapping("/find/{id}")
    public ResponseEntity<PC> getPC(@RequestHeader("Authorization") String authorizationHeader,
                                    @PathVariable Long id) {
        User user = userService.getUserDetails(authorizationHeader);
        PC pc = pcService.findPCById(id).get();
        return new ResponseEntity<>(pc, HttpStatus.OK);
    }

    @PostMapping("/add")
    public ResponseEntity<PC> createPC(@RequestHeader("Authorization") String authorizationHeader,
                                       @RequestBody PC pc) {
        User user = userService.getUserDetails(authorizationHeader);
        pc.setUser(user);
        PC newPC = pcService.addPC(pc);
        return new ResponseEntity<>(newPC, HttpStatus.CREATED);
    }

    @PutMapping("/update")
    public ResponseEntity<PC> updatePC(@RequestHeader("Authorization") String authorizationHeader,
                                       @RequestBody PC pc) {
        User user = userService.getUserDetails(authorizationHeader);
        PC updatePC = pcService.updatePC(pc);
        return new ResponseEntity<>(updatePC, HttpStatus.CREATED);
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<PC> deletePC(@RequestHeader("Authorization") String authorizationHeader,
                                       @PathVariable Long id) {
        User user = userService.getUserDetails(authorizationHeader);
        pcService.deletePC(id);
        return new ResponseEntity<>(HttpStatus.OK);
    }

}
