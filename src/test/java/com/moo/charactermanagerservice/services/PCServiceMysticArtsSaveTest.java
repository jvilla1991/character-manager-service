package com.moo.charactermanagerservice.services;

import com.moo.charactermanagerservice.models.Campaign;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.repositories.CampaignRepository;
import com.moo.charactermanagerservice.repositories.CombatSessionRepository;
import com.moo.charactermanagerservice.repositories.PCRepository;
import com.moo.charactermanagerservice.repositories.PcNoteRepository;
import com.moo.charactermanagerservice.repositories.SessionParticipantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * A whole-sheet save and the Warrior of the Mystic Arts: the spell slots are worked out from the
 * subclass being saved, so a DM who sets the subclass by hand gives the Monk its slots (and one
 * who takes it away takes them), while no other character's slots move.
 */
@ExtendWith(MockitoExtension.class)
class PCServiceMysticArtsSaveTest {

    private static final String MYSTIC_ARTS = "Warrior of the Mystic Arts";
    private static final String FRESH = "{\"1\":{\"max\":4,\"used\":0},\"2\":{\"max\":2,\"used\":0}}";
    private static final String ONE_OF_EACH_SPENT = "{\"1\":{\"max\":4,\"used\":1},\"2\":{\"max\":2,\"used\":1}}";

    @Mock
    private PCRepository pcRepository;
    @Mock
    private LevelUpService levelUpService;
    @Mock
    private CampaignRepository campaignRepository;
    @Mock
    private PcNoteRepository pcNoteRepository;
    @Mock
    private PcActivityLogService activityLogService;
    @Mock
    private CombatSessionRepository sessionRepository;
    @Mock
    private SessionParticipantRepository participantRepository;
    @Mock
    private CombatLogService combatLogService;

    @InjectMocks
    private PCService pcService;

    private UUID ownerId;
    private UUID dmId;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        dmId = UUID.randomUUID();
    }

    private PC character(String clazz, String subclass, int level, Long campaignId) {
        PC pc = new PC();
        pc.setId(1L);
        pc.setName("Shen");
        pc.setClazz(clazz);
        pc.setSubclass(subclass);
        pc.setLevel((short) level);
        pc.setUserId(ownerId);
        pc.setCampaignId(campaignId);
        return pc;
    }

    private PC stored(String clazz, String subclass, int level, Long campaignId, String slots) {
        PC pc = character(clazz, subclass, level, campaignId);
        pc.setSpellSlots(slots);
        lenient().when(pcRepository.findById(1L)).thenReturn(Optional.of(pc));
        lenient().when(pcRepository.save(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));
        if (campaignId != null) {
            Campaign campaign = new Campaign();
            campaign.setId(campaignId);
            campaign.setDmUserId(dmId);
            lenient().when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));
        }
        return pc;
    }

    private static PC body(String clazz, String subclass, int level) {
        PC incoming = new PC();
        incoming.setId(1L);
        incoming.setName("Shen");
        incoming.setClazz(clazz);
        incoming.setSubclass(subclass);
        incoming.setLevel((short) level);
        return incoming;
    }

    @Test
    void aDmWhoSetsTheSubclassByHand_givesTheMonkItsSlots() {
        stored("Monk", "Warrior of the Open Hand", 8, 7L, null);

        PC saved = pcService.updatePCAsDm(body("Monk", MYSTIC_ARTS, 8), null, dmId);

        assertThat(saved.getSpellSlots()).isEqualTo(FRESH);
    }

    @Test
    void aDmWhoTakesTheSubclassAway_takesTheSlotsWithIt() {
        stored("Monk", MYSTIC_ARTS, 8, 7L, ONE_OF_EACH_SPENT);

        PC saved = pcService.updatePCAsDm(body("Monk", "Warrior of Shadow", 8), null, dmId);

        assertThat(saved.getSpellSlots()).isEqualTo("{}");
    }

    @Test
    void anOwnersSave_keepsTheMonksSlots_andWhatIsSpent() {
        stored("Monk", MYSTIC_ARTS, 8, 7L, ONE_OF_EACH_SPENT);

        // A campaign player's body cannot hand slots back, and a body without slots loses none.
        PC body = body("Monk", MYSTIC_ARTS, 8);
        body.setSpellSlots(FRESH);

        assertThat(pcService.updatePC(body, ownerId).getSpellSlots()).isEqualTo(ONE_OF_EACH_SPENT);
    }

    @Test
    void theSlotsFollowTheLevelBeingSaved() {
        stored("Monk", MYSTIC_ARTS, 8, null, ONE_OF_EACH_SPENT);

        assertThat(pcService.updatePC(body("Monk", MYSTIC_ARTS, 13), ownerId).getSpellSlots()).isEqualTo(
                "{\"1\":{\"max\":4,\"used\":1},\"2\":{\"max\":3,\"used\":1},\"3\":{\"max\":2,\"used\":0}}");
        stored("Monk", MYSTIC_ARTS, 8, null, ONE_OF_EACH_SPENT);
        assertThat(pcService.updatePC(body("Monk", MYSTIC_ARTS, 2), ownerId).getSpellSlots()).isEqualTo("{}");
    }

    @Test
    void savingAnyOtherCharacter_leavesItsSlotsExactlyAsTheyWere() {
        // No slot table: nothing appears, and a stored "no slots" is not rewritten.
        stored("Monk", "Warrior of the Open Hand", 8, null, null);
        assertThat(pcService.updatePC(body("Monk", "Warrior of the Open Hand", 8), ownerId).getSpellSlots()).isNull();
        stored("Fighter", "Eldritch Knight", 8, null, null);
        assertThat(pcService.updatePC(body("Fighter", "Eldritch Knight", 8), ownerId).getSpellSlots()).isNull();
        stored("Rogue", "Arcane Trickster", 8, 7L, null);
        assertThat(pcService.updatePCAsDm(body("Rogue", "Arcane Trickster", 8), null, dmId).getSpellSlots()).isNull();
        // The class tables are untouched by the subclass.
        String paladin5 = "{\"1\":{\"max\":4,\"used\":2},\"2\":{\"max\":2,\"used\":0}}";
        stored("Paladin", "Oath of Devotion", 5, null, paladin5);
        assertThat(pcService.updatePC(body("Paladin", "Oath of Devotion", 5), ownerId).getSpellSlots())
                .isEqualTo(paladin5);
        String wizard5 = "{\"1\":{\"max\":4,\"used\":0},\"2\":{\"max\":3,\"used\":1},\"3\":{\"max\":2,\"used\":2}}";
        stored("Wizard", "Evoker", 5, null, wizard5);
        assertThat(pcService.updatePC(body("Wizard", "Evoker", 5), ownerId).getSpellSlots()).isEqualTo(wizard5);
    }

    @Test
    void aNewCharacter_startsWithoutTheSubclassesSlots() {
        when(pcRepository.saveAndFlush(any(PC.class))).thenAnswer(inv -> inv.getArgument(0));
        // Creation is always level 1, where the subclass has nothing yet.
        PC created = pcService.addPC(body("Monk", MYSTIC_ARTS, 8));

        assertThat(created.getLevel()).isEqualTo((short) 1);
        assertThat(created.getSpellSlots()).isNull();
    }
}
