package com.moo.charactermanagerservice.controllers;

import com.moo.charactermanagerservice.dto.*;
import com.moo.charactermanagerservice.services.RenownService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/pc/{pcId}/renown")
public class RenownController {
    private final RenownService service;

    public RenownController(RenownService service) { this.service = service; }

    @GetMapping
    public List<RenownView> list(@PathVariable Long pcId, Authentication authentication) {
        return service.list(pcId, ((User) authentication.getPrincipal()).getUuid());
    }

    @PostMapping
    public ResponseEntity<RenownView> create(@PathVariable Long pcId, @RequestBody FactionRequest request,
                                            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(pcId, request, ((User) authentication.getPrincipal()).getUuid()));
    }

    /** DM saves a whole batch of score changes at once — one activity-log entry. */
    @PatchMapping
    public List<RenownView> saveAll(@PathVariable Long pcId, @RequestBody List<RenownChange> changes,
                                    Authentication authentication) {
        return service.saveAll(pcId, changes, ((User) authentication.getPrincipal()).getUuid());
    }

    @PatchMapping("/{factionId}")
    public RenownView adjust(@PathVariable Long pcId, @PathVariable Long factionId,
                             @RequestBody RenownAdjustment request, Authentication authentication) {
        return service.adjust(pcId, factionId, request, ((User) authentication.getPrincipal()).getUuid());
    }
}
