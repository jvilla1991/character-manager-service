package com.moo.charactermanagerservice.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.models.SrdItem;
import com.moo.charactermanagerservice.repositories.SrdItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlotInventoryConversionServiceTest {

    @Mock
    private SrdItemRepository srdItemRepository;

    private final ObjectMapper mapper = new ObjectMapper();
    private SlotInventoryConversionService service;

    @BeforeEach
    void setUp() {
        service = new SlotInventoryConversionService(srdItemRepository, mapper);
        when(srdItemRepository.findAll()).thenReturn(List.of(
                catalogItem("longsword", "Longsword", "WEAPON", 1500L, "3", "3.0",
                        "{\"damage\":\"1d8 slashing\",\"properties\":[\"versatile (1d10)\"]}"),
                catalogItem("studded-leather", "Studded Leather", "ARMOR", 4500L, "13", "3.0",
                        "{\"armorCategory\":\"light\",\"armorClass\":\"12 + DEX\"}")));
    }

    private PC pc(String weapons, String gear, String inventory) {
        PC pc = new PC();
        pc.setId(7L);
        pc.setWeapons(weapons);
        pc.setGear(gear);
        pc.setInventory(inventory);
        return pc;
    }

    private List<Map<String, Object>> inventoryOf(PC pc) throws Exception {
        return mapper.readValue(pc.getInventory(), new TypeReference<>() {});
    }

    @Test
    void catalogMatchedWeapon_getsKeyCostBulk_andKeepsLegacyDamage() throws Exception {
        PC pc = pc("[{\"name\":\"Longsword\",\"dmg\":\"1d8+3 slashing\"}]", "[]", null);

        service.convert(pc);

        List<Map<String, Object>> inv = inventoryOf(pc);
        assertThat(inv).hasSize(1);
        assertThat(inv.get(0))
                .containsEntry("catalogKey", "longsword")
                .containsEntry("category", "weapon")
                .containsEntry("qty", 1)
                .containsEntry("unitCostCp", 1500)
                .containsEntry("damage", "1d8+3 slashing") // player's roll string wins
                .containsEntry("bulk", 3.0);
        assertThat(pc.getWeapons()).isEqualTo("[]");
    }

    @Test
    void unmatchedMagicWeapon_becomesAdHocLine_withMagicNote() throws Exception {
        PC pc = pc("[{\"name\":\"Rapier of the Last Lullaby\",\"magic\":true,\"dmg\":\"1d8+5\",\"notes\":\"hums\"}]",
                "[]", null);

        service.convert(pc);

        Map<String, Object> line = inventoryOf(pc).get(0);
        assertThat(line).doesNotContainKey("catalogKey");
        assertThat(line)
                .containsEntry("name", "Rapier of the Last Lullaby")
                .containsEntry("category", "weapon")
                .containsEntry("damage", "1d8+5")
                .containsEntry("notes", "magic · hums")
                .containsEntry("bulk", 1); // no weight → Small
    }

    @Test
    void gearMatchingArmorCatalog_becomesArmorLine_keepingEquipped() throws Exception {
        PC pc = pc("[]", "[{\"name\":\"studded leather\",\"equipped\":true}]", null);

        service.convert(pc);

        Map<String, Object> line = inventoryOf(pc).get(0);
        assertThat(line)
                .containsEntry("catalogKey", "studded-leather")
                .containsEntry("name", "Studded Leather")
                .containsEntry("category", "armor")
                .containsEntry("armorClass", "12 + DEX")
                .containsEntry("equipped", true)
                .containsEntry("bulk", 3.0);
        assertThat(pc.getGear()).isEqualTo("[]");
    }

    @Test
    void unmatchedGear_staysGear_atBulkOne() throws Exception {
        // Deliberately not fuzzy-matched onto catalog "Studded Leather".
        PC pc = pc("[]", "[{\"name\":\"Studded Leather Armor of Shadows\",\"magic\":true}]", null);

        service.convert(pc);

        Map<String, Object> line = inventoryOf(pc).get(0);
        assertThat(line)
                .doesNotContainKey("catalogKey");
        assertThat(line)
                .containsEntry("category", "gear")
                .containsEntry("notes", "magic")
                .containsEntry("bulk", 1);
    }

    @Test
    void convertedWeapon_stacksOntoExistingInventoryLine() throws Exception {
        PC pc = pc("[{\"name\":\"Longsword\",\"dmg\":\"1d8\"}]", "[]",
                "[{\"catalogKey\":\"longsword\",\"name\":\"Longsword\",\"category\":\"weapon\",\"qty\":1,\"bulk\":3.0}]");

        service.convert(pc);

        List<Map<String, Object>> inv = inventoryOf(pc);
        assertThat(inv).hasSize(1);
        assertThat(inv.get(0)).containsEntry("qty", 2);
    }

    @Test
    void existingInventory_getsBulkStamped_byCatalogThenWeightThenDefault() throws Exception {
        PC pc = pc("[]", "[]",
                "[{\"catalogKey\":\"longsword\",\"name\":\"Longsword\",\"qty\":1}," +
                " {\"name\":\"Iron Rations\",\"qty\":1,\"weight\":8}," +
                " {\"name\":\"Lucky Coin\",\"qty\":1}]");

        service.convert(pc);

        List<Map<String, Object>> inv = inventoryOf(pc);
        assertThat(inv.get(0)).containsEntry("bulk", 3.0); // catalog rating
        assertThat(inv.get(1)).containsEntry("bulk", 3);   // ≤10 lb band
        assertThat(inv.get(2)).containsEntry("bulk", 1);   // default
    }

    @Test
    void neverOverwritesExistingBulk_andSecondRunIsNoOp() throws Exception {
        PC pc = pc("[{\"name\":\"Longsword\",\"dmg\":\"1d8\"}]", "[]",
                "[{\"name\":\"Keepsake\",\"qty\":1,\"bulk\":0.2}]");

        service.convert(pc);
        String afterFirst = pc.getInventory();
        service.convert(pc);

        assertThat(pc.getInventory()).isEqualTo(afterFirst);
        assertThat(inventoryOf(pc).get(0)).containsEntry("bulk", 0.2);
    }

    @Test
    void malformedLegacyJson_isToleratedAsEmpty() throws Exception {
        PC pc = pc("not-json{", "also-bad", null);

        service.convert(pc);

        assertThat(inventoryOf(pc)).isEmpty();
        assertThat(pc.getWeapons()).isEqualTo("[]");
        assertThat(pc.getGear()).isEqualTo("[]");
    }

    private static SrdItem catalogItem(String key, String name, String category, Long costCp,
                                       String weight, String bulk, String details) {
        SrdItem item = new SrdItem();
        item.setItemKey(key);
        item.setName(name);
        item.setCategory(category);
        item.setCostCp(costCp);
        item.setWeight(new BigDecimal(weight));
        item.setBulk(new BigDecimal(bulk));
        item.setDetails(details);
        return item;
    }
}
