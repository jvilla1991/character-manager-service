package com.moo.charactermanagerservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moo.charactermanagerservice.dto.PurchaseResult;
import com.moo.charactermanagerservice.dto.ShopView;
import com.moo.charactermanagerservice.models.CombatSession;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.models.SessionParticipant;
import com.moo.charactermanagerservice.models.SessionShop;
import com.moo.charactermanagerservice.models.SessionShopAttendee;
import com.moo.charactermanagerservice.models.SessionStatus;
import com.moo.charactermanagerservice.models.SrdItem;
import com.moo.charactermanagerservice.repositories.CombatSessionRepository;
import com.moo.charactermanagerservice.repositories.PCRepository;
import com.moo.charactermanagerservice.repositories.SessionParticipantRepository;
import com.moo.charactermanagerservice.repositories.SessionShopAttendeeRepository;
import com.moo.charactermanagerservice.repositories.SessionShopRepository;
import com.moo.charactermanagerservice.repositories.SrdItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class ShopServiceTest {

    @Mock private CombatSessionRepository sessionRepository;
    @Mock private SessionShopRepository shopRepository;
    @Mock private SessionShopAttendeeRepository attendeeRepository;
    @Mock private SessionParticipantRepository participantRepository;
    @Mock private SrdItemRepository srdItemRepository;
    @Mock private PCRepository pcRepository;

    private ShopService shopService;

    private UUID dmId;
    private UUID playerId;
    private UUID strangerId;
    private CombatSession session;
    private SessionShop shop;

    @BeforeEach
    void setUp() {
        shopService = new ShopService(sessionRepository, shopRepository, attendeeRepository,
                participantRepository, srdItemRepository, pcRepository, new ObjectMapper());

        dmId = UUID.randomUUID();
        playerId = UUID.randomUUID();
        strangerId = UUID.randomUUID();

        session = new CombatSession();
        session.setId(1L);
        session.setDmUserId(dmId);
        session.setStatus(SessionStatus.ACTIVE);

        shop = new SessionShop();
        shop.setId(10L);
        shop.setSessionId(1L);
        shop.setCategory("WEAPON");
        shop.setSettlement("Phandalin");
    }

    // --- openShop ---

    @Test
    void openShop_createsShop_andSeatsRosterCharacters() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(shopRepository.findBySessionId(1L)).thenReturn(Optional.empty());
        when(shopRepository.save(any(SessionShop.class))).thenAnswer(inv -> {
            SessionShop s = inv.getArgument(0);
            s.setId(10L);
            return s;
        });
        when(participantRepository.findBySessionIdAndPcId(1L, 7L))
                .thenReturn(Optional.of(participant(7L, playerId)));
        when(attendeeRepository.findBySessionShopId(10L))
                .thenReturn(List.of(attendee(7L, playerId)));
        when(srdItemRepository.findByCategoryOrderByNameAsc("WEAPON"))
                .thenReturn(List.of(longsword()));

        ShopView view = shopService.openShop(1L, "weapon", "Phandalin", List.of(7L), dmId);

        assertThat(view.category()).isEqualTo("WEAPON");
        assertThat(view.attendeePcIds()).containsExactly(7L);
        assertThat(view.items()).hasSize(1);
        assertThat(view.items().get(0).itemKey()).isEqualTo("longsword");
        verify(attendeeRepository).saveAll(any());
        verify(sessionRepository).save(session); // version bumped
    }

    @Test
    void openShop_throws403_whenNotDm() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        assertThatThrownBy(() -> shopService.openShop(1L, "weapon", "x", List.of(), strangerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
    }

    @Test
    void openShop_throws400_forUnsupportedCategory() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        assertThatThrownBy(() -> shopService.openShop(1L, "potions", "x", List.of(), dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
    }

    @Test
    void openShop_throws400_whenTargetingCharacterNotInSession() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(shopRepository.findBySessionId(1L)).thenReturn(Optional.empty());
        when(shopRepository.save(any(SessionShop.class))).thenAnswer(inv -> inv.getArgument(0));
        when(participantRepository.findBySessionIdAndPcId(1L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shopService.openShop(1L, "weapon", "x", List.of(99L), dmId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
    }

    // --- getActiveShopForUser (visibility) ---

    @Test
    void getActiveShop_visibleToDm() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(shopRepository.findBySessionId(1L)).thenReturn(Optional.of(shop));
        when(attendeeRepository.findBySessionShopId(10L)).thenReturn(List.of(attendee(7L, playerId)));
        when(srdItemRepository.findByCategoryOrderByNameAsc("WEAPON")).thenReturn(List.of(longsword()));

        assertThat(shopService.getActiveShopForUser(1L, dmId)).isNotNull();
    }

    @Test
    void getActiveShop_visibleToAttendee() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(shopRepository.findBySessionId(1L)).thenReturn(Optional.of(shop));
        when(attendeeRepository.findBySessionShopId(10L)).thenReturn(List.of(attendee(7L, playerId)));
        when(srdItemRepository.findByCategoryOrderByNameAsc("WEAPON")).thenReturn(List.of(longsword()));

        assertThat(shopService.getActiveShopForUser(1L, playerId)).isNotNull();
    }

    @Test
    void getActiveShop_nullForNonAttendee() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(shopRepository.findBySessionId(1L)).thenReturn(Optional.of(shop));
        when(attendeeRepository.findBySessionShopId(10L)).thenReturn(List.of(attendee(7L, playerId)));

        assertThat(shopService.getActiveShopForUser(1L, strangerId)).isNull();
    }

    @Test
    void getActiveShop_nullWhenNoShop() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(shopRepository.findBySessionId(1L)).thenReturn(Optional.empty());
        assertThat(shopService.getActiveShopForUser(1L, dmId)).isNull();
    }

    // --- purchase ---

    @Test
    void purchase_deductsCoins_appendsInventory_andReportsCost() {
        stubActiveShopWithAttendee();
        when(srdItemRepository.findByItemKey("longsword")).thenReturn(Optional.of(longsword()));
        PC pc = pcOwnedBy(playerId, "{\"gp\":20}", null);
        when(pcRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        PurchaseResult result = shopService.purchase(1L, 7L, "longsword", 1, playerId);

        assertThat(result.totalCostCp()).isEqualTo(1500L);
        // 20 gp - 15 gp = 5 gp
        assertThat(result.coins()).containsEntry("gp", 5).containsEntry("pp", 0);
        assertThat(result.inventory()).hasSize(1);
        assertThat(result.inventory().get(0))
                .containsEntry("catalogKey", "longsword")
                .containsEntry("qty", 1)
                .containsEntry("category", "weapon")
                .containsEntry("damage", "1d8 slashing");
        verify(pcRepository).findByIdForUpdate(7L); // locked read
        verify(pcRepository).save(pc);
    }

    @Test
    void purchase_stacksQuantity_onRepeatBuy() {
        stubActiveShopWithAttendee();
        when(srdItemRepository.findByItemKey("longsword")).thenReturn(Optional.of(longsword()));
        PC pc = pcOwnedBy(playerId, "{\"gp\":100}",
                "[{\"catalogKey\":\"longsword\",\"name\":\"Longsword\",\"qty\":1}]");
        when(pcRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(pc));
        when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));

        PurchaseResult result = shopService.purchase(1L, 7L, "longsword", 2, playerId);

        assertThat(result.inventory()).hasSize(1);
        assertThat(result.inventory().get(0)).containsEntry("qty", 3);
    }

    @Test
    void purchase_throws409_whenInsufficientFunds() {
        stubActiveShopWithAttendee();
        when(srdItemRepository.findByItemKey("longsword")).thenReturn(Optional.of(longsword()));
        PC pc = pcOwnedBy(playerId, "{\"gp\":10}", null);
        when(pcRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(pc));

        assertThatThrownBy(() -> shopService.purchase(1L, 7L, "longsword", 1, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(409));
        verify(pcRepository, never()).save(any());
    }

    @Test
    void purchase_throws403_whenCharacterNotAtShop() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(shopRepository.findBySessionId(1L)).thenReturn(Optional.of(shop));
        when(attendeeRepository.findBySessionShopIdAndPcId(10L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shopService.purchase(1L, 7L, "longsword", 1, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
    }

    @Test
    void purchase_throws400_whenItemNotSoldAtThisShop() {
        stubActiveShopWithAttendee();
        SrdItem armor = new SrdItem();
        armor.setItemKey("shield");
        armor.setCategory("ARMOR");
        armor.setCostCp(1000L);
        when(srdItemRepository.findByItemKey("shield")).thenReturn(Optional.of(armor));

        assertThatThrownBy(() -> shopService.purchase(1L, 7L, "shield", 1, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
    }

    @Test
    void purchase_throws404_whenItemUnknown() {
        stubActiveShopWithAttendee();
        when(srdItemRepository.findByItemKey("frostbrand")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shopService.purchase(1L, 7L, "frostbrand", 1, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(404));
    }

    @Test
    void purchase_throws403_whenCallerDoesNotOwnCharacter() {
        stubActiveShopWithAttendee();
        when(srdItemRepository.findByItemKey("longsword")).thenReturn(Optional.of(longsword()));
        PC pc = pcOwnedBy(strangerId, "{\"gp\":50}", null); // owned by someone else
        when(pcRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(pc));

        assertThatThrownBy(() -> shopService.purchase(1L, 7L, "longsword", 1, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(403));
    }

    @Test
    void purchase_throws400_forNonPositiveQuantity() {
        assertThatThrownBy(() -> shopService.purchase(1L, 7L, "longsword", 0, playerId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status(e)).isEqualTo(400));
    }

    // --- helpers ---

    private void stubActiveShopWithAttendee() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(shopRepository.findBySessionId(1L)).thenReturn(Optional.of(shop));
        when(attendeeRepository.findBySessionShopIdAndPcId(10L, 7L))
                .thenReturn(Optional.of(attendee(7L, playerId)));
    }

    private static int status(Throwable e) {
        return ((ResponseStatusException) e).getStatusCode().value();
    }

    private static SrdItem longsword() {
        SrdItem item = new SrdItem();
        item.setId(1L);
        item.setItemKey("longsword");
        item.setName("Longsword");
        item.setCategory("WEAPON");
        item.setCostCp(1500L);
        item.setDetails("{\"damage\":\"1d8 slashing\",\"properties\":[\"versatile (1d10)\"]}");
        return item;
    }

    private static SessionParticipant participant(Long pcId, UUID owner) {
        SessionParticipant p = new SessionParticipant();
        p.setId(pcId);
        p.setPcId(pcId);
        p.setOwnerUserId(owner);
        return p;
    }

    private static SessionShopAttendee attendee(Long pcId, UUID owner) {
        SessionShopAttendee a = new SessionShopAttendee();
        a.setId(pcId);
        a.setPcId(pcId);
        a.setOwnerUserId(owner);
        return a;
    }

    private static PC pcOwnedBy(UUID owner, String coinsJson, String inventoryJson) {
        PC pc = new PC();
        pc.setId(7L);
        pc.setUserId(owner);
        pc.setCoins(coinsJson);
        pc.setInventory(inventoryJson);
        return pc;
    }
}
