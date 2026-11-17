package com.moo.charactermanagerservice.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moo.charactermanagerservice.models.PC;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V46 renamed the Death Knight {@code rune} to the hero-class {@code specialization}. For one
 * release the SPA build that still sends {@code "rune"} must keep working, so the old name is an
 * input-only alias on the PC body and on the level-up request; output uses only the new name.
 */
class SpecializationJsonAliasTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void pcBody_withLegacyRuneField_deserializesIntoSpecialization() throws Exception {
        PC pc = mapper.readValue(
                "{\"clazz\":\"paladin\",\"subclass\":\"Oath of the Ebon Blade\",\"rune\":\"Frost\"}", PC.class);
        assertThat(pc.getSpecialization()).isEqualTo("Frost");
    }

    @Test
    void pcBody_withSpecializationField_deserializes() throws Exception {
        PC pc = mapper.readValue(
                "{\"clazz\":\"ranger\",\"subclass\":\"Demon Hunter\",\"specialization\":\"Havoc\"}", PC.class);
        assertThat(pc.getSpecialization()).isEqualTo("Havoc");
    }

    @Test
    void pcJson_writesOnlySpecialization() throws Exception {
        PC pc = new PC();
        pc.setSpecialization("Vengeance");
        String json = mapper.writeValueAsString(pc);
        assertThat(json).contains("\"specialization\":\"Vengeance\"").doesNotContain("\"rune\"");
    }

    @Test
    void levelUpRequest_withLegacyRuneField_deserializesIntoSpecialization() throws Exception {
        LevelUpRequest req = mapper.readValue("{\"rune\":\"Unholy\"}", LevelUpRequest.class);
        assertThat(req.specialization()).isEqualTo("Unholy");
        assertThat(req.fightingStyle()).isNull();
    }

    @Test
    void levelUpRequest_withNewFields_deserializes() throws Exception {
        LevelUpRequest req = mapper.readValue(
                "{\"specialization\":\"Havoc\",\"fightingStyle\":\"Void Warrior\","
                        + "\"newSpells\":[{\"name\":\"Eldritch Blast\",\"lvl\":0}]}",
                LevelUpRequest.class);
        assertThat(req.specialization()).isEqualTo("Havoc");
        assertThat(req.fightingStyle()).isEqualTo("Void Warrior");
        assertThat(req.newSpells()).hasSize(1);
    }

    @Test
    void campaignMemberView_writesSpecialization() throws Exception {
        PC pc = new PC();
        pc.setSpecialization("Blood");
        String json = mapper.writeValueAsString(CampaignMemberView.from(pc));
        assertThat(json).contains("\"specialization\":\"Blood\"").doesNotContain("\"rune\"");
    }
}
