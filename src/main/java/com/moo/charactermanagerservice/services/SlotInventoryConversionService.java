package com.moo.charactermanagerservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.models.SrdItem;
import com.moo.charactermanagerservice.repositories.SrdItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One-time inventory conversion for the Darker Dungeons slot-based inventory
 * campaign variant, run when a character joins an opted-in campaign:
 * consolidates the legacy {@code weapons}/{@code gear} JSON columns into the
 * structured {@code inventory} column and stamps a {@code bulk} rating on
 * every line. Idempotent — a rejoin parses empty legacy arrays and never
 * overwrites an existing bulk value.
 */
@Service
public class SlotInventoryConversionService {

    private final SrdItemRepository srdItemRepository;
    private final PcJsonColumns json;

    @Autowired
    public SlotInventoryConversionService(SrdItemRepository srdItemRepository, ObjectMapper objectMapper) {
        this.srdItemRepository = srdItemRepository;
        this.json = new PcJsonColumns(objectMapper);
    }

    /** Convert the PC's carried items to the slot system. Mutates {@code pc}; the caller saves. */
    public void convert(PC pc) {
        List<SrdItem> catalog = srdItemRepository.findAll();
        // Exact-name matching only (case-insensitive) — never fuzzy-match a
        // player-flavored name onto a catalog row.
        Map<String, SrdItem> byName = new HashMap<>();
        Map<String, SrdItem> byKey = new HashMap<>();
        for (SrdItem item : catalog) {
            byName.put(item.getName().toLowerCase(Locale.ROOT), item);
            byKey.put(item.getItemKey(), item);
        }

        List<Map<String, Object>> inventory = json.parse(pc.getInventory());

        for (Map<String, Object> weapon : json.parse(pc.getWeapons())) {
            addLine(inventory, convertWeapon(weapon, byName));
        }
        for (Map<String, Object> gear : json.parse(pc.getGear())) {
            addLine(inventory, convertGear(gear, byName));
        }
        for (Map<String, Object> line : inventory) {
            stampBulk(line, byKey);
        }

        pc.setInventory(json.write(inventory));
        pc.setWeapons("[]");
        pc.setGear("[]");
    }

    /** Legacy weapon {@code {name, magic?, dmg, notes?}} → structured inventory line. */
    private Map<String, Object> convertWeapon(Map<String, Object> weapon, Map<String, SrdItem> byName) {
        String name = str(weapon.get("name"));
        SrdItem match = matchInCategory(byName, name, "WEAPON");

        Map<String, Object> line = new LinkedHashMap<>();
        if (match != null) {
            line.put("catalogKey", match.getItemKey());
            line.put("name", match.getName());
        } else {
            line.put("name", name);
        }
        line.put("category", "weapon");
        line.put("qty", 1);
        if (match != null) {
            line.put("unitCostCp", match.getCostCp());
            if (match.getWeight() != null) line.put("weight", match.getWeight());
        }
        // Keep the player's damage string — it embeds their roll modifiers.
        String dmg = str(weapon.get("dmg"));
        if (!dmg.isBlank()) line.put("damage", dmg);
        putNotes(line, weapon);
        if (match != null) {
            Map<String, Object> details = json.parseObject(match.getDetails());
            details.remove("damage"); // legacy dmg wins
            details.forEach(line::putIfAbsent);
        }
        return line;
    }

    /** Legacy gear {@code {name, magic?, equipped?, notes?}} → structured inventory line. */
    private Map<String, Object> convertGear(Map<String, Object> gear, Map<String, SrdItem> byName) {
        String name = str(gear.get("name"));
        SrdItem match = byName.get(name.toLowerCase(Locale.ROOT));

        Map<String, Object> line = new LinkedHashMap<>();
        if (match != null) {
            line.put("catalogKey", match.getItemKey());
            line.put("name", match.getName());
            line.put("category", categoryLabel(match.getCategory()));
            line.put("qty", 1);
            line.put("unitCostCp", match.getCostCp());
            if (match.getWeight() != null) line.put("weight", match.getWeight());
        } else {
            line.put("name", name);
            line.put("category", "gear");
            line.put("qty", 1);
        }
        if (Boolean.TRUE.equals(gear.get("equipped"))) line.put("equipped", true);
        putNotes(line, gear);
        if (match != null) {
            json.parseObject(match.getDetails()).forEach(line::putIfAbsent);
        }
        return line;
    }

    /** Append a line, stacking quantity onto an existing line with the same catalog key. */
    private void addLine(List<Map<String, Object>> inventory, Map<String, Object> line) {
        Object key = line.get("catalogKey");
        if (key != null) {
            for (Map<String, Object> existing : inventory) {
                if (key.equals(existing.get("catalogKey"))) {
                    int qty = existing.get("qty") instanceof Number n ? n.intValue() : 0;
                    existing.put("qty", qty + 1);
                    return;
                }
            }
        }
        inventory.add(line);
    }

    /** Stamp bulk if absent: catalog rating → weight band → 1. Never overwrites. */
    private void stampBulk(Map<String, Object> line, Map<String, SrdItem> byKey) {
        if (line.get("bulk") instanceof Number) return;
        SrdItem item = line.get("catalogKey") instanceof String key ? byKey.get(key) : null;
        BigDecimal catalogBulk = item == null ? null : item.getBulk();
        BigDecimal weight = line.get("weight") instanceof Number n
                ? new BigDecimal(n.toString())
                : (item == null ? null : item.getWeight());
        line.put("bulk", BulkRules.bulkFor(catalogBulk, weight));
    }

    private SrdItem matchInCategory(Map<String, SrdItem> byName, String name, String category) {
        SrdItem match = byName.get(name.toLowerCase(Locale.ROOT));
        return match != null && category.equals(match.getCategory()) ? match : null;
    }

    /** PcItem has no magic flag — preserve it as a note prefix. */
    private void putNotes(Map<String, Object> line, Map<String, Object> legacy) {
        String notes = str(legacy.get("notes"));
        if (Boolean.TRUE.equals(legacy.get("magic"))) {
            notes = notes.isBlank() ? "magic" : "magic · " + notes;
        }
        if (!notes.isBlank()) line.put("notes", notes);
    }

    private String str(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private String categoryLabel(String category) {
        return switch (category) {
            case "WEAPON" -> "weapon";
            case "ARMOR" -> "armor";
            case "MATERIAL_COMPONENT" -> "material-component";
            default -> category.toLowerCase(Locale.ROOT);
        };
    }
}
