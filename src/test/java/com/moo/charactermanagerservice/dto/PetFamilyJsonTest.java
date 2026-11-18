package com.moo.charactermanagerservice.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moo.charactermanagerservice.models.PC;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The WoW Hunter's pet family (V51) travels as {@code petFamily} on the PC body and member view. */
class PetFamilyJsonTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void pcBody_roundTripsPetFamily() throws Exception {
        PC pc = mapper.readValue("{\"clazz\":\"ranger\",\"subclass\":\"Beast Master\","
                + "\"specialization\":\"Beast Mastery\",\"petFamily\":\"Bird of Prey\"}", PC.class);
        assertThat(pc.getPetFamily()).isEqualTo("Bird of Prey");

        String json = mapper.writeValueAsString(pc);
        assertThat(json).contains("\"petFamily\":\"Bird of Prey\"");
        assertThat(mapper.readValue(json, PC.class).getPetFamily()).isEqualTo("Bird of Prey");
    }

    @Test
    void pcBody_withoutPetFamily_deserializesToNull_andWritesNull() throws Exception {
        PC pc = mapper.readValue("{\"clazz\":\"ranger\"}", PC.class);
        assertThat(pc.getPetFamily()).isNull();
        assertThat(mapper.writeValueAsString(pc)).contains("\"petFamily\":null");
    }

    @Test
    void campaignMemberView_writesPetFamily() throws Exception {
        PC pc = new PC();
        pc.setPetFamily("Wolf");
        assertThat(mapper.writeValueAsString(CampaignMemberView.from(pc))).contains("\"petFamily\":\"Wolf\"");
    }
}
