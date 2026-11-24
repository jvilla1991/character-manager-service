package com.moo.charactermanagerservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.progression.ClassResources;
import com.moo.charactermanagerservice.progression.ClassResources.Resource;
import com.moo.charactermanagerservice.progression.ClassResources.ShortRest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The stored half of a character's class resources: {@code pc.class_resources},
 * a JSON object of how much of each resource is USED, keyed by
 * {@link Resource#key()} — e.g. {@code {"secondWind":1,"actionSurge":1}}. The
 * maximum is never stored; {@link ClassResources} computes it.
 *
 * <p>Every write goes through {@link #normalize}: a resource the character
 * does not have is dropped, an amount is clamped to 0..max (so a maximum that
 * shrank pulls the used amount down with it), and a resource with nothing used
 * is not stored at all. A character with nothing used stores NULL.
 */
final class ClassResourceState {

    private ClassResourceState() {}

    private static final PcJsonColumns JSON = new PcJsonColumns(new ObjectMapper());

    /** The resources this character has, from the rules table: its class's, then its feats'. */
    static List<Resource> resourcesOf(PC pc) {
        return ClassResources.forCharacter(pc.getClazz(), pc.getSubclass(),
                pc.getLevel() == null ? null : pc.getLevel().intValue(),
                pc.getAbilityCha() == null ? null : pc.getAbilityCha().intValue(),
                pc.getAbilityWis() == null ? null : pc.getAbilityWis().intValue(),
                pc.getAbilityInt() == null ? null : pc.getAbilityInt().intValue(),
                CharacterFeats.namesOn(pc));
    }

    /** How much of a resource is used, clamped to 0..max. */
    static int used(Map<String, Object> stored, Resource resource) {
        int used = stored.get(resource.key()) instanceof Number n ? n.intValue() : 0;
        return Math.max(0, Math.min(used, resource.max()));
    }

    /** The stored map for this character, cleaned against the rules table. */
    private static Map<String, Object> clean(PC pc, String stored) {
        Map<String, Object> raw = JSON.parseObject(stored);
        Map<String, Object> out = new LinkedHashMap<>();
        for (Resource r : resourcesOf(pc)) {
            int used = used(raw, r);
            if (used > 0) {
                out.put(r.key(), used);
            }
        }
        return out;
    }

    private static String write(Map<String, Object> used) {
        return used.isEmpty() ? null : JSON.writeObject(used);
    }

    /**
     * The value to store when a whole-character save arrives (create, owner
     * update, DM update). The body's own map wins, cleaned against the body's
     * class, level and ability scores. A body without the field keeps the
     * stored map (so a payload built without it cannot hand every use back),
     * cleaned the same way — which is also what pulls the amounts down when the
     * save lowered a maximum.
     */
    static String normalize(PC incoming, PC existing) {
        String source = incoming.getClassResources() != null
                ? incoming.getClassResources()
                : existing == null ? null : existing.getClassResources();
        return write(clean(incoming, source));
    }

    /**
     * A Long Rest: everything comes back. Returns what was restored, for the
     * activity log ("Second Wind 1 → 3"), empty when nothing was spent.
     * Mutates the character.
     */
    static List<String> restoreAll(PC pc) {
        Map<String, Object> stored = JSON.parseObject(pc.getClassResources());
        List<String> restored = new ArrayList<>();
        for (Resource r : resourcesOf(pc)) {
            int used = used(stored, r);
            if (used > 0) {
                restored.add(r.name() + " " + (r.max() - used) + " → " + r.max());
            }
        }
        pc.setClassResources(null);
        return restored;
    }

    /**
     * A Short Rest: each resource regains what the rules table says — one use,
     * everything, or nothing. Returns what came back, for the activity log,
     * empty when nothing did. Mutates the character.
     */
    static List<String> shortRest(PC pc) {
        Map<String, Object> next = clean(pc, pc.getClassResources());
        List<String> regained = new ArrayList<>();
        for (Resource r : resourcesOf(pc)) {
            int used = used(next, r);
            if (used == 0 || r.shortRest() == ShortRest.NONE) {
                continue;
            }
            int after = r.shortRest() == ShortRest.ALL ? 0 : used - 1;
            regained.add(r.name() + " " + (r.max() - used) + " → " + (r.max() - after));
            if (after > 0) {
                next.put(r.key(), after);
            } else {
                next.remove(r.key());
            }
        }
        pc.setClassResources(write(next));
        return regained;
    }
}
