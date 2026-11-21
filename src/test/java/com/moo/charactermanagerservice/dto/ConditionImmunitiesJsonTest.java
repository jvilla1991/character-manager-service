package com.moo.charactermanagerservice.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moo.charactermanagerservice.models.PC;
import com.moo.charactermanagerservice.models.SessionParticipant;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Condition immunities (V63) travel as {@code conditionImmunities} on the PC body, a JSON
 * string like {@code conditions}, and ride the live-session snapshot beside the conditions.
 */
class ConditionImmunitiesJsonTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void pcBody_roundTripsConditionImmunities() throws Exception {
        String immunities = "[\"poisoned\",\"charmed\"]";
        PC pc = mapper.readValue(mapper.writeValueAsString(Map.of("conditionImmunities", immunities)), PC.class);
        assertThat(pc.getConditionImmunities()).isEqualTo(immunities);

        String json = mapper.writeValueAsString(pc);
        assertThat(mapper.readValue(json, PC.class).getConditionImmunities()).isEqualTo(immunities);
    }

    @Test
    void pcBody_withoutConditionImmunities_deserializesToNull() throws Exception {
        assertThat(mapper.readValue("{\"clazz\":\"fighter\"}", PC.class).getConditionImmunities()).isNull();
    }

    @Test
    void snapshotRow_carriesAPcsImmunities_andNoneForAnEnemy() {
        PC pc = new PC();
        pc.setId(1L);
        pc.setConditions("[\"prone\"]");
        pc.setConditionImmunities("[\"charmed\"]");
        SessionParticipant seat = new SessionParticipant();
        seat.setPcId(1L);

        ParticipantView row = ParticipantView.from(seat, pc, true, false);
        assertThat(row.conditions()).isEqualTo("[\"prone\"]");
        assertThat(row.conditionImmunities()).isEqualTo("[\"charmed\"]");
        assertThat(row.withoutHp().conditionImmunities()).isEqualTo("[\"charmed\"]");

        SessionParticipant enemy = new SessionParticipant();
        enemy.setDisplayName("Goblin");
        assertThat(ParticipantView.from(enemy, null, false, false).conditionImmunities()).isNull();
    }
}
