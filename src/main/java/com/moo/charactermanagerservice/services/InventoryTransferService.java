package com.moo.charactermanagerservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.repositories.PCRepository;
import com.moo.charactermanagerservice.repositories.PcFollowerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service
public class InventoryTransferService {
    public record Recipient(String kind, Long id, String name, String playerName) {}
    // Compare the entire inventory snapshot, not only the selected line: identical
    // adjacent stacks must not let a retried request give a second stack away.
    public record Request(String kind, Long recipientId, int itemIndex, List<Map<String, Object>> inventory) {}
    public record Result(List<Map<String, Object>> inventory, Short ac) {}

    private final PCRepository pcs;
    private final PcFollowerRepository followers;
    private final ObjectMapper mapper;
    private final PcJsonColumns json;

    public InventoryTransferService(PCRepository pcs, PcFollowerRepository followers, ObjectMapper mapper) {
        this.pcs = pcs;
        this.followers = followers;
        this.mapper = mapper;
        this.json = new PcJsonColumns(mapper);
    }

    @Transactional(readOnly = true)
    public List<Recipient> recipients(Long pcId, UUID userId) {
        PC pc = pcs.findById(pcId).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Character not found"));
        requireOwner(pc, userId);
        List<Recipient> result = new ArrayList<>();
        if (pc.getCampaignId() != null) {
            pcs.findByCampaignId(pc.getCampaignId()).stream().filter(p -> !pcId.equals(p.getId()))
                    .forEach(p -> result.add(new Recipient("player", p.getId(), p.getName(), p.getPlayerName())));
        }
        followers.findByPcIdOrderByCreatedAtAscIdAsc(pcId)
                .forEach(f -> result.add(new Recipient("follower", f.getId(), f.getName(), null)));
        return result;
    }

    @Transactional
    public Result give(Long pcId, Request request, UUID userId) {
        if (request == null || request.recipientId() == null || request.inventory() == null
                || !("player".equals(request.kind()) || "follower".equals(request.kind()))) {
            throw error(HttpStatus.BAD_REQUEST, "Choose a recipient");
        }
        boolean player = "player".equals(request.kind());
        if (player && pcId.equals(request.recipientId())) throw error(HttpStatus.BAD_REQUEST, "Choose another character");
        // Always lock PC rows in ID order to avoid deadlocks on reciprocal gifts.
        Long first = player ? Math.min(pcId, request.recipientId()) : pcId;
        Long second = player ? Math.max(pcId, request.recipientId()) : pcId;
        PC a = locked(first);
        PC b = first.equals(second) ? a : locked(second);
        PC source = pcId.equals(first) ? a : b;
        PC target = pcId.equals(first) ? b : a;
        requireOwner(source, userId);
        List<Map<String, Object>> inventory = json.parse(source.getInventory());
        if (!mapper.valueToTree(inventory).equals((left, right) ->
                left.isNumber() && right.isNumber() ? left.decimalValue().compareTo(right.decimalValue())
                        : left.equals(right) ? 0 : 1, mapper.valueToTree(request.inventory()))
                || request.itemIndex() < 0 || request.itemIndex() >= inventory.size()) {
            throw error(HttpStatus.CONFLICT, "Inventory changed. Close this dialog and refresh before trying again.");
        }
        Map<String, Object> original = inventory.get(request.itemIndex());
        if ("dropped".equals(original.get("status"))) throw error(HttpStatus.CONFLICT, "Dropped items cannot be given");
        Map<String, Object> gift = new LinkedHashMap<>(original);
        gift.put("equipped", false);
        if (player) {
            if (source.getCampaignId() == null || !source.getCampaignId().equals(target.getCampaignId())) {
                throw error(HttpStatus.FORBIDDEN, "Choose a character in your campaign");
            }
            List<Map<String, Object>> destination = json.parse(target.getInventory());
            destination.add(gift);
            target.setInventory(json.write(destination));
            pcs.save(target);
        } else {
            var follower = followers.findByIdForUpdate(request.recipientId())
                    .filter(f -> pcId.equals(f.getPcId()))
                    .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Follower not found"));
            Map<String, Object> block = json.parseObject(follower.getStatBlock());
            List<Map<String, Object>> destination = json.parse(mapper.valueToTree(block.get("inventory")).toString());
            destination.add(gift);
            block.put("inventory", destination);
            follower.setStatBlock(json.writeObject(block));
            followers.save(follower);
        }
        inventory.remove(request.itemIndex());
        source.setInventory(json.write(inventory));
        if (isEquippedArmor(original)) source.setAc(armorClass(source, inventory));
        pcs.save(source);
        return new Result(inventory, source.getAc());
    }

    private PC locked(Long id) {
        return pcs.findByIdForUpdate(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Character not found"));
    }

    private void requireOwner(PC pc, UUID userId) {
        if (!userId.equals(pc.getUserId())) throw error(HttpStatus.FORBIDDEN, "Only the owner can give items");
    }

    private static boolean isEquippedArmor(Map<String, Object> item) {
        return "armor".equals(item.get("category")) && Boolean.TRUE.equals(item.get("equipped"))
                && !"dropped".equals(item.get("status")) && item.get("armorClass") instanceof String formula && !formula.isBlank();
    }

    /** Matches the sheet's armor-math rules, including its hand-editable unarmored fallback. */
    private static short armorClass(PC pc, List<Map<String, Object>> inventory) {
        int dex = Math.floorDiv((pc.getAbilityDex() == null ? 10 : pc.getAbilityDex()) - 10, 2);
        int bestBase = -1, armor = 10 + dex, shields = 0;
        for (var item : inventory) {
            if (!isEquippedArmor(item)) continue;
            String formula = ((String) item.get("armorClass")).trim().toLowerCase(Locale.ROOT);
            var match = java.util.regex.Pattern.compile("^[+]?([0-9]+)").matcher(formula);
            if (!match.find()) continue;
            int base = Integer.parseInt(match.group(1));
            if (formula.startsWith("+")) shields += base;
            else if (base > bestBase) {
                bestBase = base;
                armor = base + (formula.matches(".*max\\s*2.*") ? Math.min(2, dex) : formula.contains("dex") ? dex : 0);
            }
        }
        return (short) (armor + shields);
    }

    private static ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}
