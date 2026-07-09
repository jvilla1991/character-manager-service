package com.moo.charactermanagerservice.services;

import com.moo.charactermanagerservice.dto.EncounterSummaryView;
import com.moo.charactermanagerservice.dto.EncounterView;
import com.moo.charactermanagerservice.dto.ImportLootRequest;
import com.moo.charactermanagerservice.models.Campaign;
import com.moo.charactermanagerservice.models.Encounter;
import com.moo.charactermanagerservice.models.EncounterCreature;
import com.moo.charactermanagerservice.models.EncounterLootItem;
import com.moo.charactermanagerservice.models.SrdItem;
import com.moo.charactermanagerservice.repositories.EncounterCreatureRepository;
import com.moo.charactermanagerservice.repositories.EncounterLootItemRepository;
import com.moo.charactermanagerservice.repositories.EncounterRepository;
import com.moo.charactermanagerservice.repositories.SrdItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CuratedEncounterServiceTest {

    @Mock private EncounterRepository encounterRepository;
    @Mock private EncounterCreatureRepository creatureRepository;
    @Mock private EncounterLootItemRepository lootItemRepository;
    @Mock private SrdItemRepository srdItemRepository;
    @Mock private CampaignService campaignService;

    private CuratedEncounterService service;

    private UUID dmId;
    private UUID strangerId;
    private Encounter encounter;

    @BeforeEach
    void setUp() {
        service = new CuratedEncounterService(encounterRepository, creatureRepository,
                lootItemRepository, srdItemRepository, campaignService);
        dmId = UUID.randomUUID();
        strangerId = UUID.randomUUID();

        encounter = new Encounter();
        encounter.setId(5L);
        encounter.setCampaignId(1L);
        encounter.setDmUserId(dmId);
        encounter.setName("Goblin Ambush");
    }

    // --- createEncounter ---

    @Test
    void createEncounter_assertsCampaignDm_andPersists() {
        when(campaignService.findByIdForDm(1L, dmId)).thenReturn(new Campaign());
        when(encounterRepository.save(any(Encounter.class))).thenAnswer(inv -> {
            Encounter e = inv.getArgument(0);
            e.setId(5L);
            return e;
        });
        when(creatureRepository.findByEncounterIdOrderByIdAsc(5L)).thenReturn(List.of());

        EncounterView view = service.createEncounter(1L, "Goblin Ambush", "They wait in the trees.", dmId);

        assertThat(view.name()).isEqualTo("Goblin Ambush");
        assertThat(view.creatures()).isEmpty();
        verify(encounterRepository).save(any(Encounter.class));
    }

    @Test
    void createEncounter_throws400_whenNameBlank() {
        when(campaignService.findByIdForDm(1L, dmId)).thenReturn(new Campaign());
        assertThatThrownBy(() -> service.createEncounter(1L, "  ", null, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
    }

    @Test
    void createEncounter_propagates403_whenNotCampaignDm() {
        when(campaignService.findByIdForDm(1L, strangerId))
                .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "Access denied"));
        assertThatThrownBy(() -> service.createEncounter(1L, "Goblin Ambush", null, strangerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
    }

    // --- listEncounters ---

    @Test
    void listEncounters_returnsSummariesWithCreatureCount() {
        when(campaignService.findByIdForDm(1L, dmId)).thenReturn(new Campaign());
        when(encounterRepository.findByCampaignIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(encounter));
        when(creatureRepository.countByEncounterId(5L)).thenReturn(3);

        List<EncounterSummaryView> views = service.listEncounters(1L, dmId);

        assertThat(views).hasSize(1);
        assertThat(views.get(0).creatureCount()).isEqualTo(3);
        assertThat(views.get(0).name()).isEqualTo("Goblin Ambush");
    }

    // --- getEncounter / ownership ---

    @Test
    void getEncounter_returnsCreatureLines() {
        when(encounterRepository.findById(5L)).thenReturn(Optional.of(encounter));
        when(creatureRepository.findByEncounterIdOrderByIdAsc(5L)).thenReturn(List.of(
                creature(1L, "Goblin", (short) 2, (short) 7, 4),
                creature(2L, "Goblin Boss", (short) 1, (short) 21, 1)));

        EncounterView view = service.getEncounter(5L, dmId);

        assertThat(view.creatures()).hasSize(2);
        assertThat(view.creatures().get(0).name()).isEqualTo("Goblin");
        assertThat(view.creatures().get(0).quantity()).isEqualTo(4);
        assertThat(view.creatures().get(1).hpMax()).isEqualTo((short) 21);
    }

    @Test
    void getEncounter_throws403_whenNotOwner() {
        when(encounterRepository.findById(5L)).thenReturn(Optional.of(encounter));
        assertThatThrownBy(() -> service.getEncounter(5L, strangerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
    }

    @Test
    void getEncounter_throws404_whenMissing() {
        when(encounterRepository.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getEncounter(9L, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(404));
    }

    // --- addCreature ---

    @Test
    void addCreature_persists_withDefaultedQuantity() {
        when(encounterRepository.findById(5L)).thenReturn(Optional.of(encounter));
        when(creatureRepository.findByEncounterIdOrderByIdAsc(5L)).thenReturn(List.of());

        service.addCreature(5L, "Goblin", (short) 2, (short) 7, null, dmId);

        ArgumentCaptor<EncounterCreature> captor = ArgumentCaptor.forClass(EncounterCreature.class);
        verify(creatureRepository).save(captor.capture());
        EncounterCreature saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("Goblin");
        assertThat(saved.getDexModifier()).isEqualTo((short) 2);
        assertThat(saved.getQuantity()).isEqualTo(1); // null quantity → 1
    }

    @Test
    void addCreature_throws400_whenNameBlankOrDexModifierNull() {
        when(encounterRepository.findById(5L)).thenReturn(Optional.of(encounter));
        assertThatThrownBy(() -> service.addCreature(5L, "  ", (short) 2, null, 1, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        assertThatThrownBy(() -> service.addCreature(5L, "Goblin", null, null, 1, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        verify(creatureRepository, never()).save(any());
    }

    @Test
    void addCreature_throws400_whenQuantityBelowOne() {
        when(encounterRepository.findById(5L)).thenReturn(Optional.of(encounter));
        assertThatThrownBy(() -> service.addCreature(5L, "Goblin", (short) 2, (short) 7, 0, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        verify(creatureRepository, never()).save(any());
    }

    // --- removeCreature ---

    @Test
    void removeCreature_throws404_whenCreatureNotInThisEncounter() {
        when(encounterRepository.findById(5L)).thenReturn(Optional.of(encounter));
        EncounterCreature other = creature(7L, "Dragon", (short) 0, (short) 200, 1);
        other.setEncounterId(99L); // belongs to a different encounter
        when(creatureRepository.findById(7L)).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.removeCreature(5L, 7L, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(404));
    }

    // --- loot lines ---

    @Test
    void addLootItem_catalogLine_persists_withDefaultedQty() {
        when(encounterRepository.findById(5L)).thenReturn(Optional.of(encounter));
        when(srdItemRepository.findByItemKey("longsword")).thenReturn(Optional.of(longsword()));

        service.addLootItem(5L, "longsword", null, null, null, dmId);

        ArgumentCaptor<EncounterLootItem> captor = ArgumentCaptor.forClass(EncounterLootItem.class);
        verify(lootItemRepository).save(captor.capture());
        EncounterLootItem saved = captor.getValue();
        assertThat(saved.getCatalogItemKey()).isEqualTo("longsword");
        assertThat(saved.getCustomName()).isNull();
        assertThat(saved.getQty()).isEqualTo(1); // null qty → 1
    }

    @Test
    void addLootItem_customLine_keepsNotes() {
        when(encounterRepository.findById(5L)).thenReturn(Optional.of(encounter));

        service.addLootItem(5L, null, "Cloak of Elvenkind", "Advantage on Stealth.", 1, dmId);

        ArgumentCaptor<EncounterLootItem> captor = ArgumentCaptor.forClass(EncounterLootItem.class);
        verify(lootItemRepository).save(captor.capture());
        assertThat(captor.getValue().getCustomName()).isEqualTo("Cloak of Elvenkind");
        assertThat(captor.getValue().getCustomNotes()).isEqualTo("Advantage on Stealth.");
        verify(srdItemRepository, never()).findByItemKey(any());
    }

    @Test
    void addLootItem_throws400_whenBothOrNeitherOfKeyAndName() {
        when(encounterRepository.findById(5L)).thenReturn(Optional.of(encounter));
        assertThatThrownBy(() -> service.addLootItem(5L, "longsword", "Cloak", null, 1, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        assertThatThrownBy(() -> service.addLootItem(5L, "  ", null, null, 1, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        verify(lootItemRepository, never()).save(any());
    }

    @Test
    void addLootItem_throws404_whenCatalogKeyUnknown() {
        when(encounterRepository.findById(5L)).thenReturn(Optional.of(encounter));
        when(srdItemRepository.findByItemKey("frostbrand")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.addLootItem(5L, "frostbrand", null, null, 1, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(404));
    }

    @Test
    void getEncounter_resolvesLootLines_catalogAndCustom() {
        encounter.setLootCoinCp(12550L);
        when(encounterRepository.findById(5L)).thenReturn(Optional.of(encounter));
        when(lootItemRepository.findByEncounterIdOrderByIdAsc(5L)).thenReturn(List.of(
                lootLine(1L, "longsword", null, null, 2),
                lootLine(2L, null, "Cloak of Elvenkind", "Advantage on Stealth.", 1)));
        when(srdItemRepository.findByItemKeyIn(List.of("longsword"))).thenReturn(List.of(longsword()));

        EncounterView view = service.getEncounter(5L, dmId);

        assertThat(view.lootCoinCp()).isEqualTo(12550L);
        assertThat(view.lootItems()).hasSize(2);
        assertThat(view.lootItems().get(0).name()).isEqualTo("Longsword"); // resolved from catalog
        assertThat(view.lootItems().get(0).custom()).isFalse();
        assertThat(view.lootItems().get(1).name()).isEqualTo("Cloak of Elvenkind");
        assertThat(view.lootItems().get(1).custom()).isTrue();
        assertThat(view.lootItems().get(1).customNotes()).isEqualTo("Advantage on Stealth.");
    }

    // --- setLootCoins ---

    @Test
    void setLootCoins_storesRoundedCopper() {
        when(encounterRepository.findById(5L)).thenReturn(Optional.of(encounter));
        when(encounterRepository.save(any(Encounter.class))).thenAnswer(inv -> inv.getArgument(0));

        service.setLootCoins(5L, 125.5, dmId);

        assertThat(encounter.getLootCoinCp()).isEqualTo(12550L);
    }

    @Test
    void setLootCoins_throws400_whenNegativeOrNull() {
        when(encounterRepository.findById(5L)).thenReturn(Optional.of(encounter));
        assertThatThrownBy(() -> service.setLootCoins(5L, -1.0, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        assertThatThrownBy(() -> service.setLootCoins(5L, null, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
    }

    // --- importLoot ---

    @Test
    void importLoot_appendsLines_andAddsCoinsToPile() {
        encounter.setLootCoinCp(100L);
        when(encounterRepository.findById(5L)).thenReturn(Optional.of(encounter));
        when(srdItemRepository.findByItemKeyIn(List.of("longsword"))).thenReturn(List.of(longsword()));

        ImportLootRequest request = new ImportLootRequest(10.0, List.of(
                new ImportLootRequest.Item("longsword", null, null, null),
                new ImportLootRequest.Item(null, "Cloak of Elvenkind", "Advantage on Stealth.", 2)));
        service.importLoot(5L, request, dmId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EncounterLootItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(lootItemRepository).saveAll(captor.capture());
        List<EncounterLootItem> saved = captor.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getCatalogItemKey()).isEqualTo("longsword");
        assertThat(saved.get(0).getQty()).isEqualTo(1); // null qty → 1
        assertThat(saved.get(1).getCustomName()).isEqualTo("Cloak of Elvenkind");
        assertThat(saved.get(1).getQty()).isEqualTo(2);
        assertThat(encounter.getLootCoinCp()).isEqualTo(1100L); // 100 + 10 gp
    }

    @Test
    void importLoot_throws400_listingUnknownKeys_savesNothing() {
        when(encounterRepository.findById(5L)).thenReturn(Optional.of(encounter));
        when(srdItemRepository.findByItemKeyIn(List.of("frostbrand"))).thenReturn(List.of());

        ImportLootRequest request = new ImportLootRequest(null, List.of(
                new ImportLootRequest.Item("frostbrand", null, null, 1)));
        assertThatThrownBy(() -> service.importLoot(5L, request, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unknown catalog keys: frostbrand")
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        verify(lootItemRepository, never()).saveAll(any());
    }

    @Test
    void importLoot_throws400_whenLineHasBothKeyAndName() {
        when(encounterRepository.findById(5L)).thenReturn(Optional.of(encounter));

        ImportLootRequest request = new ImportLootRequest(null, List.of(
                new ImportLootRequest.Item("longsword", "Also a name", null, 1)));
        assertThatThrownBy(() -> service.importLoot(5L, request, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        verify(lootItemRepository, never()).saveAll(any());
    }

    // --- helpers ---

    private static SrdItem longsword() {
        SrdItem item = new SrdItem();
        item.setId(1L);
        item.setItemKey("longsword");
        item.setName("Longsword");
        item.setCategory("WEAPON");
        item.setCostCp(1500L);
        item.setDetails("{}");
        return item;
    }

    private static EncounterLootItem lootLine(Long id, String key, String customName, String notes, int qty) {
        EncounterLootItem line = new EncounterLootItem();
        line.setId(id);
        line.setEncounterId(5L);
        line.setCatalogItemKey(key);
        line.setCustomName(customName);
        line.setCustomNotes(notes);
        line.setQty(qty);
        return line;
    }

    private static int status(Throwable e) {
        return ((ResponseStatusException) e).getStatusCode().value();
    }

    private static EncounterCreature creature(Long id, String name, Short dexModifier, Short hpMax, int quantity) {
        EncounterCreature c = new EncounterCreature();
        c.setId(id);
        c.setEncounterId(5L);
        c.setName(name);
        c.setDexModifier(dexModifier);
        c.setHpMax(hpMax);
        c.setQuantity(quantity);
        return c;
    }
}
