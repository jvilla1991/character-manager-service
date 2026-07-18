package com.moo.charactermanagerservice.services;

import com.moo.charactermanagerservice.dto.EncounterSummaryView;
import com.moo.charactermanagerservice.dto.EncounterView;
import com.moo.charactermanagerservice.models.Campaign;
import com.moo.charactermanagerservice.models.Encounter;
import com.moo.charactermanagerservice.models.EncounterCreature;
import com.moo.charactermanagerservice.repositories.EncounterCreatureRepository;
import com.moo.charactermanagerservice.repositories.EncounterRepository;
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
    @Mock private CampaignService campaignService;

    private CuratedEncounterService service;

    private UUID dmId;
    private UUID strangerId;
    private Encounter encounter;

    @BeforeEach
    void setUp() {
        service = new CuratedEncounterService(encounterRepository, creatureRepository, campaignService);
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
    void getEncounter_returnsCreatureLines_withArmorClass() {
        when(encounterRepository.findById(5L)).thenReturn(Optional.of(encounter));
        when(creatureRepository.findByEncounterIdOrderByIdAsc(5L)).thenReturn(List.of(
                creature(1L, "Goblin", (short) 15, (short) 7, 4),
                creature(2L, "Goblin Boss", (short) 17, (short) 21, 1)));

        EncounterView view = service.getEncounter(5L, dmId);

        assertThat(view.creatures()).hasSize(2);
        assertThat(view.creatures().get(0).name()).isEqualTo("Goblin");
        assertThat(view.creatures().get(0).armorClass()).isEqualTo((short) 15);
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

        service.addCreature(5L, "Goblin", (short) 15, (short) 7, null, dmId);

        ArgumentCaptor<EncounterCreature> captor = ArgumentCaptor.forClass(EncounterCreature.class);
        verify(creatureRepository).save(captor.capture());
        EncounterCreature saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("Goblin");
        assertThat(saved.getArmorClass()).isEqualTo((short) 15);
        assertThat(saved.getQuantity()).isEqualTo(1); // null quantity → 1
    }

    @Test
    void addCreature_allowsUnknownArmorClass() {
        // AC is optional reference info (existing creatures migrated with null).
        when(encounterRepository.findById(5L)).thenReturn(Optional.of(encounter));
        when(creatureRepository.findByEncounterIdOrderByIdAsc(5L)).thenReturn(List.of());

        service.addCreature(5L, "Mysterious Figure", null, null, 1, dmId);

        ArgumentCaptor<EncounterCreature> captor = ArgumentCaptor.forClass(EncounterCreature.class);
        verify(creatureRepository).save(captor.capture());
        assertThat(captor.getValue().getArmorClass()).isNull();
    }

    @Test
    void addCreature_throws400_whenNameBlank() {
        when(encounterRepository.findById(5L)).thenReturn(Optional.of(encounter));
        assertThatThrownBy(() -> service.addCreature(5L, "  ", (short) 15, null, 1, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        verify(creatureRepository, never()).save(any());
    }

    @Test
    void addCreature_throws400_whenQuantityBelowOne() {
        when(encounterRepository.findById(5L)).thenReturn(Optional.of(encounter));
        assertThatThrownBy(() -> service.addCreature(5L, "Goblin", (short) 15, (short) 7, 0, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
        verify(creatureRepository, never()).save(any());
    }

    // --- removeCreature ---

    @Test
    void removeCreature_throws404_whenCreatureNotInThisEncounter() {
        when(encounterRepository.findById(5L)).thenReturn(Optional.of(encounter));
        EncounterCreature other = creature(7L, "Dragon", (short) 19, (short) 200, 1);
        other.setEncounterId(99L); // belongs to a different encounter
        when(creatureRepository.findById(7L)).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.removeCreature(5L, 7L, dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(404));
    }

    // --- helpers ---

    private static int status(Throwable e) {
        return ((ResponseStatusException) e).getStatusCode().value();
    }

    private static EncounterCreature creature(Long id, String name, Short armorClass, Short hpMax, int quantity) {
        EncounterCreature c = new EncounterCreature();
        c.setId(id);
        c.setEncounterId(5L);
        c.setName(name);
        c.setArmorClass(armorClass);
        c.setHpMax(hpMax);
        c.setQuantity(quantity);
        return c;
    }
}
