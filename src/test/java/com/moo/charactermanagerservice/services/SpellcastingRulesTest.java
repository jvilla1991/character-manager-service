package com.moo.charactermanagerservice.services;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/** Casting math over the PC JSON columns: find spell, slot check/spend, component resolution. */
class SpellcastingRulesTest {

    private Map<String, Object> spell(String name, int lvl, List<String> components, String material) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("name", name);
        s.put("lvl", lvl);
        if (components != null) s.put("components", components);
        if (material != null) s.put("material", material);
        return s;
    }

    private Map<String, Object> componentLine(String spellName, int qty, boolean consumed) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("category", "material-component");
        line.put("spell", spellName);
        line.put("qty", qty);
        line.put("consumedOnCast", consumed);
        return line;
    }

    private Map<String, Object> slots(int level, int max, int used) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("max", max);
        entry.put("used", used);
        Map<String, Object> slots = new LinkedHashMap<>();
        slots.put(String.valueOf(level), entry);
        return slots;
    }

    @Test
    void findSpell_matchesByExactName_andReadsLevel() {
        List<Map<String, Object>> spells = List.of(
                spell("Fire Bolt", 0, null, null),
                spell("Revivify", 3, List.of("v", "m"), "diamonds worth 300+ GP"));
        assertThat(SpellcastingRules.findSpell(spells, "Revivify")).isNotNull();
        assertThat(SpellcastingRules.findSpell(spells, "Wish")).isNull();
        assertThat(SpellcastingRules.levelOf(spell("Fire Bolt", 0, null, null))).isZero();
        assertThat(SpellcastingRules.levelOf(SpellcastingRules.findSpell(spells, "Revivify"))).isEqualTo(3);
    }

    @Test
    void hasSlotAvailable_and_spendSlot_readStringKeys() {
        Map<String, Object> slots = slots(3, 2, 1);
        assertThat(SpellcastingRules.hasSlotAvailable(slots, 3)).isTrue();
        assertThat(SpellcastingRules.hasSlotAvailable(slots, 2)).isFalse(); // no level-2 entry

        SpellcastingRules.spendSlot(slots, 3);
        assertThat(((Map<?, ?>) slots.get("3")).get("used")).isEqualTo(2);
        assertThat(SpellcastingRules.hasSlotAvailable(slots, 3)).isFalse(); // now exhausted
        SpellcastingRules.spendSlot(slots, 3); // no-op, no overdraft
        assertThat(((Map<?, ?>) slots.get("3")).get("used")).isEqualTo(2);
    }

    @Test
    void findComponentLine_matchesLiveLinesBySpellName() {
        List<Map<String, Object>> inv = new ArrayList<>();
        Map<String, Object> dropped = componentLine("Revivify", 3, true);
        dropped.put("status", "dropped");
        inv.add(dropped);
        inv.add(componentLine("Revivify", 0, true));   // out of stock
        Map<String, Object> live = componentLine("Revivify", 2, true);
        inv.add(live);

        assertThat(SpellcastingRules.findComponentLine(inv, "Revivify")).isSameAs(live);
        assertThat(SpellcastingRules.findComponentLine(inv, "Identify")).isNull();
    }

    @Test
    void consumeComponentLine_decrements_andRemovesAtZero() {
        List<Map<String, Object>> inv = new ArrayList<>();
        inv.add(componentLine("Revivify", 2, true));
        SpellcastingRules.consumeComponentLine(inv, "Revivify");
        assertThat(inv).hasSize(1);
        assertThat(inv.get(0).get("qty")).isEqualTo(1);

        SpellcastingRules.consumeComponentLine(inv, "Revivify");
        assertThat(inv).isEmpty();

        SpellcastingRules.consumeComponentLine(inv, "Revivify"); // no line — no error
        assertThat(inv).isEmpty();
    }

    @Test
    void needsCostlyComponent_requiresM_andAGpValue() {
        assertThat(SpellcastingRules.needsCostlyComponent(
                spell("Revivify", 3, List.of("v", "m"), "diamonds worth 300+ GP"))).isTrue();
        assertThat(SpellcastingRules.needsCostlyComponent(
                spell("Find Familiar", 1, List.of("v", "s", "m"), "charcoal, incense, and herbs"))).isFalse();
        assertThat(SpellcastingRules.needsCostlyComponent(
                spell("Bless", 1, List.of("v", "s"), "a pearl worth 100+ GP"))).isFalse(); // no 'm'
        assertThat(SpellcastingRules.needsCostlyComponent(
                spell("Mage Hand", 0, null, null))).isFalse(); // un-enriched
    }
}
