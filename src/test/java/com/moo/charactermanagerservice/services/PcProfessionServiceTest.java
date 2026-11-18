package com.moo.charactermanagerservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moo.charactermanagerservice.dto.ProfessionGrant;
import com.moo.charactermanagerservice.models.*;
import com.moo.charactermanagerservice.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PcProfessionServiceTest {
    private final PCService pcs = mock(PCService.class);
    private final PCRepository pcRepository = mock(PCRepository.class);
    private final PcProfessionRepository grants = mock(PcProfessionRepository.class);
    private final PcActivityLogService log = mock(PcActivityLogService.class);
    private final UUID owner = UUID.randomUUID(), dm = UUID.randomUUID(), stranger = UUID.randomUUID();
    private final PC pc = new PC();
    private final PcProfessionService service = new PcProfessionService(pcs, pcRepository, grants, log, new ObjectMapper());
    @BeforeEach void setup() {
        pc.setId(1L); pc.setUserId(owner);
        when(pcs.findPCById(1L)).thenReturn(pc);
        when(pcs.findPCByIdForDm(1L, dm)).thenReturn(pc);
        when(pcs.findPCByIdForDm(1L, owner)).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));
        when(pcs.findPCByIdForDm(1L, stranger)).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));
        when(pcRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(pc));
    }
    @Test void ownerAndDmCanReadButStrangerCannot() {
        assertThat(service.list(1L, owner)).isEmpty();
        assertThat(service.list(1L, dm)).isEmpty();
        assertThatThrownBy(() -> service.list(1L, stranger)).isInstanceOf(ResponseStatusException.class);
    }
    @Test void playersAndUnrelatedDmsCannotMutate() {
        for (UUID caller : List.of(owner, stranger)) {
            assertThatThrownBy(() -> service.grant(1L, new ProfessionGrant("fishing"), caller)).isInstanceOf(ResponseStatusException.class);
            assertThatThrownBy(() -> service.revoke(1L, "fishing", caller)).isInstanceOf(ResponseStatusException.class);
        }
        verifyNoInteractions(grants);
    }
    @Test void fishingNeedsNoProficiencyAndIsLogged() {
        assertThat(service.grant(1L, new ProfessionGrant("fishing"), dm).profession()).isEqualTo("fishing");
        verify(grants).save(argThat(g -> g.getPcId().equals(1L) && g.getProfession().equals("fishing")));
        verify(log).log(eq(1L), eq(PcActivityType.DM_EDIT), contains("fishing"), eq(dm));
    }
    @Test void historyExpertiseAndNormalizedToolNamesQualify() {
        pc.setSkills("{\"History\":\"expert\"}");
        service.grant(1L, new ProfessionGrant("archaeology"), dm);
        pc.setToolProfs("[\" mason’s tools \",\"Herbalism Kit\",\"Leatherworker's Tools\"]");
        for (String key : List.of("mining", "herbalism", "skinning")) service.grant(1L, new ProfessionGrant(key), dm);
        verify(grants, times(4)).save(any());
    }
    @Test void missingOrMalformedProficienciesAndUnknownKeysAreRejected() {
        pc.setToolProfs("not json");
        pc.setSkills("{\"History\":\"none\"}");
        for (String key : List.of("archaeology", "mining", "herbalism", "skinning", "alchemy", "unknown")) {
            assertThatThrownBy(() -> service.grant(1L, new ProfessionGrant(key), dm))
                    .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
        assertThatThrownBy(() -> service.grant(1L, null, dm)).isInstanceOf(ResponseStatusException.class);
        verify(grants, never()).save(any());
    }
    @Test void duplicateGrantsConflictAndRevokeIsScopedToCharacter() {
        PcProfession grant = new PcProfession(); grant.setPcId(1L); grant.setProfession("fishing");
        when(grants.findByPcIdAndProfession(1L, "fishing")).thenReturn(Optional.of(grant));
        assertThatThrownBy(() -> service.grant(1L, new ProfessionGrant("fishing"), dm))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        service.revoke(1L, "fishing", dm);
        verify(grants).delete(grant);
        assertThatThrownBy(() -> service.revoke(1L, "mining", dm))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "alchemy|Alchemist's Supplies", "alchemy|Herbalism Kit",
            "blacksmithing|Mason's Tools", "blacksmithing|Smith's Tools",
            "cooking|Brewer's Supplies", "cooking|Cook's Utensils",
            "enchanting|Carpenter's Tools", "enchanting|Potter's Tools", "enchanting|Woodcarver's Tools",
            "engineering|Tinker's Tools",
            "inscription|Calligrapher's Supplies", "inscription|Cartographer's Tools", "inscription|Painter's Supplies",
            "jewelcrafting|Glassblower's Tools", "jewelcrafting|Jeweler's Tools",
            "leatherworking|Cobbler's Tools", "leatherworking|Leatherworker's Tools",
            "tailoring|Weaver's Tools"
    })
    void eachProductionToolAlternativeQualifiesOnItsOwn(String profession, String tool) throws Exception {
        // Use curly apostrophes, extra space, and lowercase to exercise imported proficiency names.
        String importedTool = " " + tool.toLowerCase(Locale.ROOT).replace('\'', '’') + " ";
        pc.setToolProfs(new ObjectMapper().writeValueAsString(List.of(importedTool)));
        assertThat(service.grant(1L, new ProfessionGrant(profession), dm).profession()).isEqualTo(profession);
        verify(grants).save(argThat(g -> g.getPcId().equals(1L) && g.getProfession().equals(profession)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"alchemy", "blacksmithing", "cooking", "enchanting", "engineering",
            "inscription", "jewelcrafting", "leatherworking", "tailoring"})
    void unrelatedToolsDoNotQualifyForProduction(String profession) {
        pc.setToolProfs("[\"Navigator's Tools\"]");
        assertThatThrownBy(() -> service.grant(1L, new ProfessionGrant(profession), dm))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(grants, never()).save(any());
    }

    @Test void productionProficiencyDoesNotLetPlayersOrOtherDmsGrantOrRevoke() {
        pc.setToolProfs("[\"Herbalism Kit\"]");
        for (UUID caller : List.of(owner, stranger)) {
            assertThatThrownBy(() -> service.grant(1L, new ProfessionGrant("alchemy"), caller))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
            assertThatThrownBy(() -> service.revoke(1L, "alchemy", caller))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        }
        verifyNoInteractions(grants);
    }

    @Test void productionGrantsRetainDuplicateProtectionAndCanBeRevoked() {
        pc.setToolProfs("[\"Herbalism Kit\"]");
        PcProfession grant = new PcProfession(); grant.setPcId(1L); grant.setProfession("alchemy");
        when(grants.findByPcIdAndProfession(1L, "alchemy")).thenReturn(Optional.of(grant));
        assertThatThrownBy(() -> service.grant(1L, new ProfessionGrant("alchemy"), dm))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        service.revoke(1L, "alchemy", dm);
        verify(grants).delete(grant);
        verify(grants, never()).save(any());
    }
}
