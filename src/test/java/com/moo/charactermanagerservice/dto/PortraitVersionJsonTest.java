package com.moo.charactermanagerservice.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.models.SessionParticipant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The portrait cache key (V62) travels as {@code portraitVersion} on the PC and on the views built from it. */
class PortraitVersionJsonTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void pc_writesPortraitVersion_andNullWhenThereIsNoPortrait() throws Exception {
        PC pc = new PC();
        assertThat(mapper.writeValueAsString(pc)).contains("\"portraitVersion\":null");
        pc.setPortraitVersion(1732147200000L);
        assertThat(mapper.writeValueAsString(pc)).contains("\"portraitVersion\":1732147200000");
    }

    @Test
    void campaignMemberView_andParticipantView_writePortraitVersion() throws Exception {
        PC pc = new PC();
        pc.setId(1L);
        pc.setPortraitVersion(7L);
        assertThat(mapper.writeValueAsString(CampaignMemberView.from(pc))).contains("\"portraitVersion\":7");

        SessionParticipant participant = new SessionParticipant();
        participant.setPcId(1L);
        assertThat(mapper.writeValueAsString(ParticipantView.from(participant, pc, false, false)))
                .contains("\"portraitVersion\":7");
    }

    @Test
    void uploadResponse_isPortraitVersionOnly() throws Exception {
        assertThat(mapper.writeValueAsString(new PortraitVersionView(12L))).isEqualTo("{\"portraitVersion\":12}");
    }
}
