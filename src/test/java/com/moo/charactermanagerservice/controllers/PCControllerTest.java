package com.moo.charactermanagerservice.controllers;

import com.moo.charactermanagerservice.dto.LevelUpPreview;
import com.moo.charactermanagerservice.dto.LevelUpRequest;
import com.moo.charactermanagerservice.dto.User;
import com.moo.charactermanagerservice.exceptions.PCNotFoundException;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.services.PCService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PCController — exercises controller logic directly without the
 * Spring MVC / security filter stack. Security integration is covered separately.
 */
@ExtendWith(MockitoExtension.class)
class PCControllerTest {

    @InjectMocks
    private PCController pcController;

    @Mock
    private PCService pcService;

    private UUID ownerId;
    private User owner;
    private PC pc;
    private Authentication auth;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();

        owner = new User();
        owner.setUuid(ownerId);
        owner.setUserName("testuser");
        owner.setFirstName("Test");

        pc = new PC();
        pc.setId(1L);
        pc.setName("Aelindra");
        pc.setClazz("Wizard");
        pc.setLevel((short) 1);
        pc.setUserId(ownerId);

        auth = new UsernamePasswordAuthenticationToken(owner, null, owner.getAuthorities());
    }

    // --- GET /all ---

    @Test
    void getAllPCsForUser_returns200_withList() {
        when(pcService.findAllPCsForUser(ownerId)).thenReturn(List.of(pc));

        ResponseEntity<List<PC>> response = pcController.getAllPCsForUser(auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(pc);
    }

    @Test
    void getAllPCsForUser_returns200_withEmptyList() {
        when(pcService.findAllPCsForUser(ownerId)).thenReturn(List.of());

        ResponseEntity<List<PC>> response = pcController.getAllPCsForUser(auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    // --- GET /find/{id} ---

    @Test
    void getPC_returns200_whenOwner() {
        when(pcService.findPCByIdForUser(1L, ownerId)).thenReturn(pc);

        ResponseEntity<PC> response = pcController.getPC(1L, auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(pc);
    }

    @Test
    void getPC_propagates403_whenNotOwner() {
        when(pcService.findPCByIdForUser(1L, ownerId))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied"));

        assertThatThrownBy(() -> pcController.getPC(1L, auth))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(403));
    }

    @Test
    void getPC_propagates404_whenNotFound() {
        when(pcService.findPCByIdForUser(99L, ownerId))
                .thenThrow(new PCNotFoundException("PC not found with id 99"));

        assertThatThrownBy(() -> pcController.getPC(99L, auth))
                .isInstanceOf(PCNotFoundException.class);
    }

    // --- POST /add ---

    @Test
    void createPC_returns201_setsOwnershipAndPlayerName() {
        PC incoming = new PC();
        incoming.setName("Milo");
        incoming.setClazz("Rogue");

        PC saved = new PC();
        saved.setId(2L);
        saved.setName("Milo");
        saved.setLevel((short) 1);
        saved.setUserId(ownerId);

        when(pcService.addPC(any(PC.class))).thenReturn(saved);

        ResponseEntity<PC> response = pcController.createPC(auth, incoming);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isSameAs(saved);

        // Verify the controller stamped userId and playerName before calling the service
        verify(pcService).addPC(argThat(p ->
                ownerId.equals(p.getUserId()) && "Test".equals(p.getPlayerName())
        ));
    }

    // --- PUT /{id} ---

    @Test
    void updatePC_returns200_andStampsId() {
        PC incoming = new PC();
        incoming.setName("Aelindra Updated");

        when(pcService.updatePC(any(PC.class), eq(ownerId))).thenReturn(pc);

        ResponseEntity<PC> response = pcController.updatePC(1L, auth, incoming);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Controller must stamp the path id onto the body before delegating
        verify(pcService).updatePC(argThat(p -> Long.valueOf(1L).equals(p.getId())), eq(ownerId));
    }

    @Test
    void updatePC_propagates403_whenNotOwner() {
        when(pcService.updatePC(any(PC.class), eq(ownerId)))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied"));

        assertThatThrownBy(() -> pcController.updatePC(1L, auth, pc))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(403));
    }

    // --- GET /{id}/level-up/preview ---

    @Test
    void previewLevelUp_returns200_withPreview() {
        LevelUpPreview preview = new LevelUpPreview(4, 5, 10, 3, 9, 39, 2, 3, Map.of(), Map.of(), false, List.of(), false, List.of());
        when(pcService.previewLevelUp(1L, ownerId)).thenReturn(preview);

        ResponseEntity<LevelUpPreview> response = pcController.previewLevelUp(1L, auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(preview);
    }

    @Test
    void previewLevelUp_propagates403_whenNotOwner() {
        when(pcService.previewLevelUp(1L, ownerId))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied"));

        assertThatThrownBy(() -> pcController.previewLevelUp(1L, auth))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(403));
    }

    // --- POST /{id}/level-up ---

    @Test
    void levelUp_returns200_whenOwner() {
        when(pcService.levelUpPC(1L, ownerId, null)).thenReturn(pc);

        ResponseEntity<PC> response = pcController.levelUp(1L, auth, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(pc);
    }

    @Test
    void levelUp_passesChoicesFromBody() {
        LevelUpRequest body = new LevelUpRequest("Life Domain", java.util.Map.of("STR", 2), null);
        when(pcService.levelUpPC(1L, ownerId, body)).thenReturn(pc);

        pcController.levelUp(1L, auth, body);

        verify(pcService).levelUpPC(1L, ownerId, body);
    }

    @Test
    void levelUp_propagates403_whenNotOwner() {
        when(pcService.levelUpPC(1L, ownerId, null))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied"));

        assertThatThrownBy(() -> pcController.levelUp(1L, auth, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(403));
    }

    @Test
    void levelUp_propagates409_whenAtMaxLevel() {
        when(pcService.levelUpPC(1L, ownerId, null))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Character is already at the maximum level (20)"));

        assertThatThrownBy(() -> pcController.levelUp(1L, auth, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(409));
    }

    // --- DELETE /delete/{id} ---

    @Test
    void deletePC_returns204_whenOwner() {
        doNothing().when(pcService).deletePC(1L, ownerId);

        ResponseEntity<Void> response = pcController.deletePC(1L, auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(pcService).deletePC(1L, ownerId);
    }

    @Test
    void deletePC_propagates403_whenNotOwner() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied"))
                .when(pcService).deletePC(1L, ownerId);

        assertThatThrownBy(() -> pcController.deletePC(1L, auth))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(403));
    }
}
