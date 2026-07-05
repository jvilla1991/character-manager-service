package com.moo.charactermanagerservice.controllers;

import com.moo.charactermanagerservice.dto.CatalogItemView;
import com.moo.charactermanagerservice.services.ShopService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for CatalogController — exercises controller logic directly without
 * the Spring MVC / security filter stack, like {@link PCControllerTest}.
 */
@ExtendWith(MockitoExtension.class)
class CatalogControllerTest {

    @InjectMocks
    private CatalogController catalogController;

    @Mock
    private ShopService shopService;

    @Test
    void catalog_returns200_withItems() {
        CatalogItemView longsword = new CatalogItemView(
                "longsword", "Longsword", "WEAPON", 1500L,
                new BigDecimal("3"), new BigDecimal("2"),
                Map.of("damage", "1d8 slashing"));
        when(shopService.catalog("WEAPON")).thenReturn(List.of(longsword));

        ResponseEntity<List<CatalogItemView>> response = catalogController.catalog("WEAPON");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(longsword);
    }

    @Test
    void catalog_propagates400_forUnsupportedCategory() {
        when(shopService.catalog("POTION")).thenThrow(
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported shop category: POTION"));

        assertThatThrownBy(() -> catalogController.catalog("POTION"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(400));
    }
}
