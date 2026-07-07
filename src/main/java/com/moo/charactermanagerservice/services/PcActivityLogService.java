package com.moo.charactermanagerservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.models.PcActivityLog;
import com.moo.charactermanagerservice.models.PcActivityType;
import com.moo.charactermanagerservice.repositories.PcActivityLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Per-character activity log: a read-only, pruned-to-10 audit trail written
 * at each notable backend mutation (level-up, shop purchase/sale, DM XP
 * award, long rest, DM edit). Log writes join the caller's existing
 * transaction (default {@code REQUIRED}, so no {@code @Transactional} is
 * needed on the plain insert methods here) — a failed log write rolls back
 * the whole mutation, and there is deliberately no try/catch around the
 * repository calls and no {@code REQUIRES_NEW}: a log entry must never
 * outlive the change it describes. Message-*rendering* code is kept
 * null-safe instead, so a bad/missing field never throws.
 */
@Service
public class PcActivityLogService {

    /** Individual entries are logged one-per-change up to this many; beyond it, one summary row. */
    private static final int DM_DIFF_ENTRY_CAP = 3;

    private static final List<String> ABILITIES = List.of("STR", "DEX", "CON", "INT", "WIS", "CHA");

    private final PcActivityLogRepository activityLogRepository;
    private final PcJsonColumns json;

    @Autowired
    public PcActivityLogService(PcActivityLogRepository activityLogRepository, ObjectMapper objectMapper) {
        this.activityLogRepository = activityLogRepository;
        this.json = new PcJsonColumns(objectMapper);
    }

    /** Record one event and prune the character's log back to the latest 10. */
    public void log(Long pcId, PcActivityType type, String description, UUID actorUserId) {
        PcActivityLog entry = new PcActivityLog();
        entry.setPcId(pcId);
        entry.setActionType(type);
        entry.setDescription(description);
        entry.setActorUserId(actorUserId);
        activityLogRepository.save(entry);
        activityLogRepository.pruneToLatest(pcId);
    }

    /** Record several events of the same type from one action, pruning once at the end. */
    public void logAll(Long pcId, PcActivityType type, List<String> descriptions, UUID actorUserId) {
        if (descriptions == null || descriptions.isEmpty()) return;
        for (String description : descriptions) {
            PcActivityLog entry = new PcActivityLog();
            entry.setPcId(pcId);
            entry.setActionType(type);
            entry.setDescription(description);
            entry.setActorUserId(actorUserId);
            activityLogRepository.save(entry);
        }
        activityLogRepository.pruneToLatest(pcId);
    }

    /** The character's latest 10 log entries, newest first. */
    public List<PcActivityLog> latestFor(Long pcId) {
        return activityLogRepository.findTop10ByPcIdOrderByCreatedAtDescIdDesc(pcId);
    }

    /**
     * Diff a DM's edit of a character (before vs. after) and log the result.
     * Silently no-ops when nothing changed. See {@link #buildDmDiff} for the
     * comparison rules; caller MUST compute {@code before}/{@code after}
     * snapshots before {@code pcRepository.save} clobbers {@code before} (JPA
     * merges the incoming entity onto the managed one at save time).
     */
    public void logDmEdit(PC before, PC after, UUID dmUserId) {
        List<String> changes = buildDmDiff(before, after);
        if (changes.isEmpty()) return;

        List<String> descriptions;
        if (changes.size() <= DM_DIFF_ENTRY_CAP) {
            descriptions = changes;
        } else {
            int more = changes.size() - DM_DIFF_ENTRY_CAP;
            String summary = "DM edited the sheet: "
                    + String.join(", ", changes.subList(0, DM_DIFF_ENTRY_CAP))
                    + ", and " + more + " more";
            descriptions = List.of(summary);
        }
        logAll(after.getId(), PcActivityType.DM_EDIT, descriptions, dmUserId);
    }

    // --- The as-dm diff -----------------------------------------------------

    /**
     * Build the list of human-readable change descriptions between a
     * character's before/after snapshots. Scalars use {@code Objects.equals}
     * (null renders as "—"); coins compare total copper value only (a
     * denomination reshuffle with the same total is not a change); inventory/
     * features/spells (JSON columns) diff by name. Malformed JSON parses as
     * empty rather than throwing — a bad blob just looks like "everything was
     * removed/added", never a 500.
     */
    private List<String> buildDmDiff(PC before, PC after) {
        List<String> changes = new ArrayList<>();

        diffScalar(changes, "level", before.getLevel(), after.getLevel());
        diffScalar(changes, "XP", before.getXp(), after.getXp());
        diffScalar(changes, "max HP", before.getHpMax(), after.getHpMax());
        diffScalar(changes, "current HP", before.getHpCurrent(), after.getHpCurrent());
        diffScalar(changes, "AC", before.getAc(), after.getAc());
        diffScalar(changes, "STR", before.getAbilityStr(), after.getAbilityStr());
        diffScalar(changes, "DEX", before.getAbilityDex(), after.getAbilityDex());
        diffScalar(changes, "CON", before.getAbilityCon(), after.getAbilityCon());
        diffScalar(changes, "INT", before.getAbilityInt(), after.getAbilityInt());
        diffScalar(changes, "WIS", before.getAbilityWis(), after.getAbilityWis());
        diffScalar(changes, "CHA", before.getAbilityCha(), after.getAbilityCha());

        diffCoins(changes, before.getCoins(), after.getCoins());
        diffInventory(changes, before.getInventory(), after.getInventory());
        diffNamedSet(changes, "feature", before.getFeatures(), after.getFeatures());
        diffNamedSet(changes, "spell", before.getSpells(), after.getSpells());

        return changes;
    }

    private void diffScalar(List<String> changes, String label, Object before, Object after) {
        if (Objects.equals(before, after)) return;
        changes.add("DM changed " + label + " " + render(before) + " → " + render(after));
    }

    private String render(Object value) {
        return value == null ? "—" : String.valueOf(value);
    }

    private void diffCoins(List<String> changes, String beforeJson, String afterJson) {
        long beforeCp = CoinPurse.toCopper(json.parseObject(beforeJson));
        long afterCp = CoinPurse.toCopper(json.parseObject(afterJson));
        if (beforeCp == afterCp) return;
        changes.add("DM changed coins " + CoinPurse.format(beforeCp) + " → " + CoinPurse.format(afterCp));
    }

    /**
     * Inventory diff by item name (fallback to catalogKey when name is
     * missing): a net quantity increase logs an "added" entry (with an
     * {@code ×qty} suffix when the increase is more than one), a decrease
     * logs a "removed" entry. Items present in only one snapshot count as the
     * full add/remove of their quantity.
     */
    private void diffInventory(List<String> changes, String beforeJson, String afterJson) {
        Map<String, Integer> before = qtyByName(json.parse(beforeJson));
        Map<String, Integer> after = qtyByName(json.parse(afterJson));

        Set<String> names = new LinkedHashSet<>();
        names.addAll(before.keySet());
        names.addAll(after.keySet());

        for (String name : names) {
            int beforeQty = before.getOrDefault(name, 0);
            int afterQty = after.getOrDefault(name, 0);
            int delta = afterQty - beforeQty;
            if (delta > 0) {
                changes.add("DM added " + name + (delta > 1 ? " ×" + delta : ""));
            } else if (delta < 0) {
                changes.add("DM removed " + name + (delta < -1 ? " ×" + (-delta) : ""));
            }
        }
    }

    private Map<String, Integer> qtyByName(List<Map<String, Object>> items) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Map<String, Object> item : items) {
            String name = itemLabel(item);
            if (name == null) continue;
            int qty = item.get("qty") instanceof Number n ? n.intValue() : 1;
            out.merge(name, qty, Integer::sum);
        }
        return out;
    }

    private String itemLabel(Map<String, Object> item) {
        if (item.get("name") instanceof String s && !s.isBlank()) return s;
        if (item.get("catalogKey") instanceof String s && !s.isBlank()) return s;
        return null;
    }

    /** Set-of-names diff for the features/spells JSON columns (added/removed, no quantity). */
    private void diffNamedSet(List<String> changes, String label, String beforeJson, String afterJson) {
        Set<String> before = namesOf(json.parse(beforeJson));
        Set<String> after = namesOf(json.parse(afterJson));

        for (String name : after) {
            if (!before.contains(name)) changes.add("DM added " + label + " " + name);
        }
        for (String name : before) {
            if (!after.contains(name)) changes.add("DM removed " + label + " " + name);
        }
    }

    private Set<String> namesOf(List<Map<String, Object>> items) {
        Set<String> names = new LinkedHashSet<>();
        for (Map<String, Object> item : items) {
            String name = itemLabel(item);
            if (name != null) names.add(name);
        }
        return names;
    }
}
