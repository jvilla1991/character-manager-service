package com.moo.charactermanagerservice.controllers;

import com.moo.charactermanagerservice.dto.AddPcNoteRequest;
import com.moo.charactermanagerservice.dto.LevelUpPreview;
import com.moo.charactermanagerservice.dto.LevelUpRequest;
import com.moo.charactermanagerservice.dto.User;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.models.PcNote;
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

    /**
     * Full sheet of a campaign member, for the DM who runs its campaign.
     * Authorized by campaign-DM ownership (not PC ownership), so the DM can edit
     * the complete character rather than the privacy-limited member projection.
     */
    @GetMapping("/{id}/as-dm")
    public ResponseEntity<PC> getPCAsDm(@PathVariable Long id, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(pcService.findPCByIdForDm(id, user.getUuid()));
    }

    /** DM-authorized update of a campaign member's PC (campaign-DM ownership). */
    @PutMapping("/{id}/as-dm")
    public ResponseEntity<PC> updatePCAsDm(@PathVariable Long id, Authentication authentication,
                                           @RequestBody PC pc) {
        User user = (User) authentication.getPrincipal();
        pc.setId(id);
        return ResponseEntity.ok(pcService.updatePCAsDm(pc, user.getUuid()));
    }

    @GetMapping("/{id}/level-up/preview")
    public ResponseEntity<LevelUpPreview> previewLevelUp(@PathVariable Long id, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(pcService.previewLevelUp(id, user.getUuid()));
    }

    @PostMapping("/{id}/level-up")
    public ResponseEntity<PC> levelUp(@PathVariable Long id, Authentication authentication,
                                      @RequestBody(required = false) LevelUpRequest request) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(pcService.levelUpPC(id, user.getUuid(), request));
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Void> deletePC(@PathVariable Long id, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        pcService.deletePC(id, user.getUuid());
        return ResponseEntity.noContent().build();
    }

    /** The owning player appends a session note to their character. */
    @PostMapping("/{id}/notes")
    public ResponseEntity<PcNote> addNote(@PathVariable Long id, Authentication authentication,
                                          @RequestBody AddPcNoteRequest request) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED).body(
                pcService.addNote(id, request.body(), request.sessionId(), user.getUuid()));
    }

    /** A character's notes, newest first — owner or the campaign's DM. */
    @GetMapping("/{id}/notes")
    public ResponseEntity<List<PcNote>> getNotes(@PathVariable Long id, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(pcService.notesFor(id, user.getUuid()));
    }
}
