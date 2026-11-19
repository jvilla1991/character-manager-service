package com.moo.charactermanagerservice.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moo.charactermanagerservice.models.PC;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The DM's adjustment lines (V57) travel as {@code statAdjustments} on the PC body: a JSON
 * string, like the other TEXT columns the SPA (de)serializes itself.
 */
class StatAdjustmentsJsonTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void pcBody_roundTripsStatAdjustments() throws Exception {
        String lines = "{\"ac\":[{\"value\":1,\"label\":\"Taunka\"}]}";
        PC pc = mapper.readValue(mapper.writeValueAsString(Map.of("statAdjustments", lines)), PC.class);
        assertThat(pc.getStatAdjustments()).isEqualTo(lines);

        String json = mapper.writeValueAsString(pc);
        assertThat(mapper.readValue(json, PC.class).getStatAdjustments()).isEqualTo(lines);
    }

    @Test
    void pcBody_withoutStatAdjustments_deserializesToNull_andWritesNull() throws Exception {
        PC pc = mapper.readValue("{\"clazz\":\"fighter\"}", PC.class);
        assertThat(pc.getStatAdjustments()).isNull();
        assertThat(mapper.writeValueAsString(pc)).contains("\"statAdjustments\":null");
    }
}
