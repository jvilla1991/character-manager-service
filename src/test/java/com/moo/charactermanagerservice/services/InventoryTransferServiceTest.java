package com.moo.charactermanagerservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.models.PcFollower;
import com.moo.charactermanagerservice.repositories.PCRepository;
import com.moo.charactermanagerservice.repositories.PcFollowerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class InventoryTransferServiceTest {
    final PCRepository pcs = mock(PCRepository.class);
    final PcFollowerRepository followers = mock(PcFollowerRepository.class);
    final ObjectMapper mapper = new ObjectMapper();
    final PcJsonColumns json = new PcJsonColumns(mapper);
    final InventoryTransferService service = new InventoryTransferService(pcs, followers, mapper);
    final UUID owner = UUID.randomUUID();
    PC source, target;
    List<Map<String, Object>> snapshot;

    @BeforeEach void setup() {
        source = pc(1L, owner, 10L);
        target = pc(2L, UUID.randomUUID(), 10L);
        source.setAc((short) 18);
        source.setAbilityDex((short) 14);
        snapshot = List.of(Map.of("name", "Rations", "qty", 3, "category", "gear"),
                Map.of("name", "Plate", "qty", 2, "category", "armor", "equipped", true, "armorClass", "18", "weight", 65.0));
        source.setInventory(json.write(snapshot));
        target.setInventory("[{\"name\":\"Torch\",\"qty\":1}]");
        when(pcs.findByIdForUpdate(1L)).thenReturn(Optional.of(source));
        when(pcs.findByIdForUpdate(2L)).thenReturn(Optional.of(target));
    }
    PC pc(Long id, UUID user, Long campaign) {
        PC pc = new PC(); pc.setId(id); pc.setUserId(user); pc.setCampaignId(campaign); pc.setName("PC " + id); return pc;
    }
    InventoryTransferService.Request request(String kind, Long id) {
        return new InventoryTransferService.Request(kind, id, 1, snapshot);
    }
    @Test void transfersWholeStackPreservesSuppliesAndRecomputesArmor() {
        var result = service.give(1L, request("player", 2L), owner);
        assertThat(result.inventory()).hasSize(1);
        assertThat(result.inventory().get(0).get("name")).isEqualTo("Rations");
        assertThat(result.ac()).isEqualTo((short) 12);
        var destination = json.parse(target.getInventory());
        assertThat(destination).hasSize(2);
        assertThat(destination.get(1)).containsEntry("qty", 2).containsEntry("equipped", false).containsEntry("armorClass", "18");
        verify(pcs).save(source); verify(pcs).save(target);
        assertThatThrownBy(() -> service.give(1L, request("player", 2L), owner)).isInstanceOf(ResponseStatusException.class);
        assertThat(json.parse(target.getInventory())).hasSize(2);
    }
    /** Give the source a chain shirt, a shield and the rations; the item at `itemIndex` is what gets given. */
    private InventoryTransferService.Request wearingShirtAndShield(int itemIndex) {
        snapshot = List.of(Map.of("name", "Rations", "qty", 3, "category", "gear"),
                Map.of("name", "Chain Shirt", "qty", 1, "category", "armor", "equipped", true,
                        "armorClass", "13 + Dex modifier (max 2)"),
                Map.of("name", "Shield", "qty", 1, "category", "armor", "equipped", true, "armorClass", "+2"));
        source.setInventory(json.write(snapshot));
        return new InventoryTransferService.Request("player", 2L, itemIndex, snapshot);
    }
    @Test void givingArmorAwayKeepsTheDmAdjustmentLinesInTheSavedAc() {
        var request = wearingShirtAndShield(2);
        source.setAc((short) 18);                                  // 13 + Dex 2 + shield 2 + Taunka 1
        source.setStatAdjustments("{\"ac\":[{\"value\":1,\"label\":\"Taunka\"}]}");
        assertThat(service.give(1L, request, owner).ac()).isEqualTo((short) 16); // only the shield's 2 goes
    }
    @Test void givingArmorAwayKeepsAnOlderHandTypedDifference() {
        var request = wearingShirtAndShield(1);
        source.setAc((short) 20);                                  // 17 from the gear and 3 nobody labelled
        assertThat(service.give(1L, request, owner).ac()).isEqualTo((short) 17); // 10 + Dex 2 + shield 2 + 3
    }
    @Test void givingAwayAShieldWhoseLineTheDmRemovedCostsNoAcAgain() {
        var request = wearingShirtAndShield(2);
        source.setAc((short) 15);                                  // 13 + Dex 2, the shield already out
        source.setStatAdjustments("{\"acRemoved\":[{\"value\":1,\"label\":\"Shield\"}]}");
        assertThat(service.give(1L, request, owner).ac()).isEqualTo((short) 15);
    }
    @Test void givingArmorAwayLeavesARemovedDexterityLineOut() {
        var request = wearingShirtAndShield(1);
        source.setAc((short) 15);                                  // 13 + shield 2, Dexterity out
        source.setStatAdjustments("{\"acRemoved\":[{\"value\":1,\"label\":\"Dexterity\"}]}");
        assertThat(service.give(1L, request, owner).ac()).isEqualTo((short) 12); // unarmored 10 + shield 2
    }
    @Test void givingArmorAwayBuildsTheTotalFromTheLinesWhenNoAcWasSaved() {
        var request = wearingShirtAndShield(2);
        source.setAc(null);
        source.setStatAdjustments("{\"ac\":[{\"value\":1,\"label\":\"Taunka\"}]}");
        assertThat(service.give(1L, request, owner).ac()).isEqualTo((short) 16); // 13 + Dex 2 + Taunka 1
    }
    @Test void givingSomethingThatIsNotWornArmorLeavesTheSavedAcAlone() {
        var request = wearingShirtAndShield(0);                    // the rations
        source.setAc((short) 25);                                  // hand-set
        assertThat(service.give(1L, request, owner).ac()).isEqualTo((short) 25);
    }
    @Test void acceptsEquivalentJsonNumbers() {
        var copy = new LinkedHashMap<>(snapshot.get(1)); copy.put("weight", 65);
        snapshot = List.of(snapshot.get(0), copy);
        service.give(1L, request("player", 2L), owner);
        assertThat(json.parse(source.getInventory())).hasSize(1);
    }
    @Test void transfersToOwnFollowerPreservingStatBlock() {
        var follower = new PcFollower(); follower.setId(3L); follower.setPcId(1L);
        follower.setStatBlock("{\"hp\":12,\"inventory\":[{\"name\":\"Spear\"}]}");
        when(followers.findByIdForUpdate(3L)).thenReturn(Optional.of(follower));
        service.give(1L, request("follower", 3L), owner);
        var block = json.parseObject(follower.getStatBlock());
        assertThat(block).containsEntry("hp", 12);
        assertThat((List<?>) block.get("inventory")).hasSize(2);
        verify(followers).save(follower);
    }
    @Test void rejectsOtherOwnersWithoutWrites() {
        assertThatThrownBy(() -> service.give(1L, request("player", 2L), UUID.randomUUID())).isInstanceOf(ResponseStatusException.class);
        verify(pcs, never()).save(any());
    }
    @Test void rejectsOtherCampaignsWithoutWrites() {
        target.setCampaignId(20L);
        assertThatThrownBy(() -> service.give(1L, request("player", 2L), owner)).isInstanceOf(ResponseStatusException.class);
        verify(pcs, never()).save(any());
    }
    @Test void rejectsOtherPlayersFollowersWithoutWrites() {
        var follower = new PcFollower(); follower.setPcId(2L);
        when(followers.findByIdForUpdate(3L)).thenReturn(Optional.of(follower));
        assertThatThrownBy(() -> service.give(1L, request("follower", 3L), owner)).isInstanceOf(ResponseStatusException.class);
        verify(pcs, never()).save(any()); verify(followers, never()).save(any());
    }
    @Test void rejectsStaleAndDroppedItems() {
        source.setInventory("[]");
        assertThatThrownBy(() -> service.give(1L, request("player", 2L), owner)).isInstanceOf(ResponseStatusException.class);
        var dropped = new LinkedHashMap<>(snapshot.get(1)); dropped.put("status", "dropped");
        snapshot = List.of(snapshot.get(0), dropped); source.setInventory(json.write(snapshot));
        assertThatThrownBy(() -> service.give(1L, request("player", 2L), owner)).isInstanceOf(ResponseStatusException.class);
        verify(pcs, never()).save(any());
    }
    @Test void listsOnlyCampaignPeersAndOwnFollowers() {
        when(pcs.findById(1L)).thenReturn(Optional.of(source));
        when(pcs.findByCampaignId(10L)).thenReturn(List.of(source, target));
        var follower = new PcFollower(); follower.setId(3L); follower.setName("Companion");
        when(followers.findByPcIdOrderByCreatedAtAscIdAsc(1L)).thenReturn(List.of(follower));
        assertThat(service.recipients(1L, owner)).extracting(InventoryTransferService.Recipient::kind)
                .containsExactly("player", "follower");
        assertThatThrownBy(() -> service.recipients(1L, UUID.randomUUID())).isInstanceOf(ResponseStatusException.class);
    }
}
