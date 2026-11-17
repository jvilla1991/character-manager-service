package com.moo.charactermanagerservice.controllers;

import com.moo.charactermanagerservice.dto.ProfessionGrant;
import com.moo.charactermanagerservice.dto.User;
import com.moo.charactermanagerservice.services.PcProfessionService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/pc/{pcId}/professions")
public class PcProfessionController {
    private final PcProfessionService service;
    public PcProfessionController(PcProfessionService service) { this.service = service; }
    @GetMapping
    public List<ProfessionGrant> list(@PathVariable Long pcId, Authentication auth) {
        return service.list(pcId, ((User) auth.getPrincipal()).getUuid());
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProfessionGrant grant(@PathVariable Long pcId, @RequestBody ProfessionGrant request, Authentication auth) {
        return service.grant(pcId, request, ((User) auth.getPrincipal()).getUuid());
    }
    @DeleteMapping("/{profession}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable Long pcId, @PathVariable String profession, Authentication auth) {
        service.revoke(pcId, profession, ((User) auth.getPrincipal()).getUuid());
    }
}
