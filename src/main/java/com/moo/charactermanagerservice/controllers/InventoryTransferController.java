package com.moo.charactermanagerservice.controllers;

import com.moo.charactermanagerservice.dto.User;
import com.moo.charactermanagerservice.services.InventoryTransferService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/pc/{pcId}/inventory")
public class InventoryTransferController {
    private final InventoryTransferService transfers;
    public InventoryTransferController(InventoryTransferService transfers) { this.transfers = transfers; }

    @GetMapping("/recipients")
    public List<InventoryTransferService.Recipient> recipients(@PathVariable Long pcId, Authentication auth) {
        return transfers.recipients(pcId, ((User) auth.getPrincipal()).getUuid());
    }

    @PostMapping("/give")
    public InventoryTransferService.Result give(@PathVariable Long pcId,
            @RequestBody InventoryTransferService.Request request, Authentication auth) {
        return transfers.give(pcId, request, ((User) auth.getPrincipal()).getUuid());
    }
}
