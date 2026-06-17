package com.moo.charactermanagerservice.services;

import com.moo.charactermanagerservice.dto.LevelUpPreview;
import com.moo.charactermanagerservice.dto.LevelUpRequest;
import com.moo.charactermanagerservice.exceptions.PCNotFoundException;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.repositories.PCRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PCServiceTest {

    @Mock
    private PCRepository pcRepository;

    @Mock
    private LevelUpService levelUpService;

    @InjectMocks
    private PCService pcService;

    private UUID ownerId;
    private UUID strangerId;
    private PC pc;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        strangerId = UUID.randomUUID();

        pc = new PC();
        pc.setId(1L);
        pc.setName("Aelindra");
        pc.setClazz("Wizard");
        pc.setUserId(ownerId);
    }

    // --- addPC ---

    @Test
    void addPC_forcesLevelToOne() {
        pc.setLevel((short) 5);
        when(pcRepository.saveAndFlush(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        PC saved = pcService.addPC(pc);

        assertThat(saved.getLevel()).isEqualTo((short) 1);
    }

    @Test
    void addPC_persistsAndReturns() {
        when(pcRepository.saveAndFlush(pc)).thenReturn(pc);

        PC result = pcService.addPC(pc);

        assertThat(result).isSameAs(pc);
        verify(pcRepository).saveAndFlush(pc);
    }

    // --- findPCById ---

    @Test
    void findPCById_returnsPC_whenFound() {
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));

        PC result = pcService.findPCById(1L);

        assertThat(result).isSameAs(pc);
    }

    @Test
    void findPCById_throws_whenNotFound() {
        when(pcRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pcService.findPCById(99L))
                .isInstanceOf(PCNotFoundException.class);
    }

    // --- findPCByIdForUser ---

    @Test
    void findPCByIdForUser_returnsPC_whenOwner() {
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));

        PC result = pcService.findPCByIdForUser(1L, ownerId);

        assertThat(result).isSameAs(pc);
    }

    @Test
    void findPCByIdForUser_throws403_whenNotOwner() {
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));

        assertThatThrownBy(() -> pcService.findPCByIdForUser(1L, strangerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(403));
    }

    // --- updatePC ---

    @Test
    void updatePC_savesAndReturns_whenOwner() {
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(pc)).thenReturn(pc);

        PC result = pcService.updatePC(pc, ownerId);

        assertThat(result).isSameAs(pc);
        verify(pcRepository).save(pc);
    }

    @Test
    void updatePC_throws403_whenNotOwner() {
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));

        assertThatThrownBy(() -> pcService.updatePC(pc, strangerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(403));

        verify(pcRepository, never()).save(any());
    }

    // --- levelUpPC ---

    @Test
    void levelUpPC_appliesRulesAndSaves_whenOwner() {
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(pc)).thenReturn(pc);

        PC result = pcService.levelUpPC(1L, ownerId, null);

        assertThat(result).isSameAs(pc);
        verify(levelUpService).applyLevelUp(pc, null, null, null);
        verify(pcRepository).save(pc);
    }

    @Test
    void levelUpPC_passesChoicesToRulesEngine() {
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(pc)).thenReturn(pc);

        pcService.levelUpPC(1L, ownerId, new LevelUpRequest("Life Domain", Map.of("STR", 2), "Sentinel"));

        verify(levelUpService).applyLevelUp(pc, "Life Domain", Map.of("STR", 2), "Sentinel");
    }

    @Test
    void levelUpPC_throws403_whenNotOwner() {
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));

        assertThatThrownBy(() -> pcService.levelUpPC(1L, strangerId, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(403));

        verify(levelUpService, never()).applyLevelUp(any(), any(), any(), any());
        verify(pcRepository, never()).save(any());
    }

    // --- previewLevelUp ---

    @Test
    void previewLevelUp_returnsPreview_whenOwner() {
        LevelUpPreview preview = new LevelUpPreview(4, 5, 8, 2, 7, 39, 2, 3, Map.of(), Map.of(), false, List.of(), false, List.of());
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        when(levelUpService.preview(pc)).thenReturn(preview);

        LevelUpPreview result = pcService.previewLevelUp(1L, ownerId);

        assertThat(result).isSameAs(preview);
    }

    @Test
    void previewLevelUp_throws403_whenNotOwner() {
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));

        assertThatThrownBy(() -> pcService.previewLevelUp(1L, strangerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(403));

        verify(levelUpService, never()).preview(any());
    }

    // --- deletePC ---

    @Test
    void deletePC_deletesById_whenOwner() {
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));

        pcService.deletePC(1L, ownerId);

        verify(pcRepository).deleteById(1L);
    }

    @Test
    void deletePC_throws403_whenNotOwner() {
        when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));

        assertThatThrownBy(() -> pcService.deletePC(1L, strangerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(403));

        verify(pcRepository, never()).deleteById(any());
    }

    // --- findAllPCsForUser ---

    @Test
    void findAllPCsForUser_returnsFilteredList() {
        when(pcRepository.findByUserId(ownerId)).thenReturn(List.of(pc));

        List<PC> result = pcService.findAllPCsForUser(ownerId);

        assertThat(result).containsExactly(pc);
    }
}
