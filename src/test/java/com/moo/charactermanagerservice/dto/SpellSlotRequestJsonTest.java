package com.moo.charactermanagerservice.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The request and response bodies of the spell-slot endpoints and the ritual flag on a cast. */
class SpellSlotRequestJsonTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void castRequest_withoutRitual_isAnOrdinaryCast() throws Exception {
        // What every client deployed before the flag existed sends.
        CastSpellRequest request = mapper.readValue(
                "{\"pcId\":7,\"spellName\":\"Cure Wounds\",\"atLevel\":1}", CastSpellRequest.class);

        assertThat(request.pcId()).isEqualTo(7L);
        assertThat(request.spellName()).isEqualTo("Cure Wounds");
        assertThat(request.atLevel()).isEqualTo(1);
        assertThat(request.ritual()).isNull();
    }

    @Test
    void castRequest_carriesRitual() throws Exception {
        assertThat(mapper.readValue("{\"pcId\":7,\"spellName\":\"Detect Magic\",\"ritual\":true}",
                CastSpellRequest.class).ritual()).isTrue();
        assertThat(mapper.readValue("{\"pcId\":7,\"spellName\":\"Detect Magic\",\"ritual\":false}",
                CastSpellRequest.class).ritual()).isFalse();
        assertThat(mapper.readValue("{\"pcId\":7,\"spellName\":\"Detect Magic\",\"ritual\":null}",
                CastSpellRequest.class).ritual()).isNull();
    }

    @Test
    void spendRequest_carriesTheLevel() throws Exception {
        assertThat(mapper.readValue("{\"level\":3}", SpendSpellSlotRequest.class).level()).isEqualTo(3);
        assertThat(mapper.readValue("{}", SpendSpellSlotRequest.class).level()).isNull();
    }

    @Test
    void restoreRequest_carriesALevel_orAll() throws Exception {
        RestoreSpellSlotsRequest one = mapper.readValue("{\"level\":3}", RestoreSpellSlotsRequest.class);
        assertThat(one.level()).isEqualTo(3);
        assertThat(one.all()).isNull();

        RestoreSpellSlotsRequest all = mapper.readValue("{\"all\":true}", RestoreSpellSlotsRequest.class);
        assertThat(all.level()).isNull();
        assertThat(all.all()).isTrue();
    }

    @Test
    void result_writesTheSlotsKeyedBySpellLevel() throws Exception {
        String json = mapper.writeValueAsString(
                new SpellSlotResult(7L, Map.of("1", Map.of("max", 4, "used", 1))));

        assertThat(json).contains("\"pcId\":7").contains("\"spellSlots\":{\"1\":{");
    }
}
