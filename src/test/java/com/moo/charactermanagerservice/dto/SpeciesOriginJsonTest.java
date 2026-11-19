package com.moo.charactermanagerservice.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moo.charactermanagerservice.models.PC;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The species origin (V56) travels as {@code speciesSource}, {@code lineage} and {@code size}
 * on the PC body and on the DM's member view.
 */
class SpeciesOriginJsonTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void pcBody_roundTripsTheSpeciesOrigin() throws Exception {
        PC pc = mapper.readValue("{\"clazz\":\"fighter\",\"species\":\"Tauren\","
                + "\"speciesSource\":\"World of Warcraft\",\"lineage\":\"Taunka\",\"size\":\"Medium\"}", PC.class);
        assertThat(pc.getSpeciesSource()).isEqualTo("World of Warcraft");
        assertThat(pc.getLineage()).isEqualTo("Taunka");
        assertThat(pc.getSize()).isEqualTo("Medium");

        String json = mapper.writeValueAsString(pc);
        assertThat(json).contains("\"speciesSource\":\"World of Warcraft\"")
                .contains("\"lineage\":\"Taunka\"")
                .contains("\"size\":\"Medium\"");
        PC again = mapper.readValue(json, PC.class);
        assertThat(again.getLineage()).isEqualTo("Taunka");
        assertThat(again.getSize()).isEqualTo("Medium");
    }

    @Test
    void pcBody_withoutASpeciesOrigin_deserializesToNull_andWritesNull() throws Exception {
        PC pc = mapper.readValue("{\"clazz\":\"fighter\",\"species\":\"Halfling\"}", PC.class);
        assertThat(pc.getSpeciesSource()).isNull();
        assertThat(pc.getLineage()).isNull();
        assertThat(pc.getSize()).isNull();
        assertThat(mapper.writeValueAsString(pc))
                .contains("\"speciesSource\":null")
                .contains("\"lineage\":null")
                .contains("\"size\":null");
    }

    @Test
    void campaignMemberView_writesTheSpeciesOrigin() throws Exception {
        PC pc = new PC();
        pc.setSpecies("Goblin");
        pc.setSpeciesSource("World of Warcraft");
        pc.setSize("Small");
        String json = mapper.writeValueAsString(CampaignMemberView.from(pc));
        assertThat(json).contains("\"species\":\"Goblin\"")
                .contains("\"speciesSource\":\"World of Warcraft\"")
                .contains("\"lineage\":null")
                .contains("\"size\":\"Small\"");
    }
}
