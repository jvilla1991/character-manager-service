package com.moo.charactermanagerservice.models;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moo.charactermanagerservice.dto.UpdatePcAsDmRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * How {@code subclassChoices} travels in the character's JSON: out as an object the client can
 * read as it is, and never in. The column is server-owned, so a request body's value is not even
 * read into the entity, whatever shape a client sends it in.
 */
class PcSubclassChoicesJsonTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void aResponse_carriesTheChoicesAsAJsonObject_notAsText() throws Exception {
        PC pc = new PC();
        pc.setName("Aelindra");
        pc.setClassResources("{\"secondWind\":1}");
        pc.setSubclassChoices("{\"skills\":[],\"arcaneShots\":[\"Banishing Shot\",\"Seeking Shot\"]}");

        JsonNode sent = JSON.readTree(JSON.writeValueAsString(pc));

        assertThat(sent.get("subclassChoices").isObject()).isTrue();
        assertThat(sent.get("subclassChoices").get("skills").isArray()).isTrue();
        assertThat(sent.get("subclassChoices").get("skills")).isEmpty();
        assertThat(sent.get("subclassChoices").get("arcaneShots").get(1).asText()).isEqualTo("Seeking Shot");
        // The older JSON columns still travel as text.
        assertThat(sent.get("classResources").isTextual()).isTrue();
    }

    @Test
    void aResponse_carriesNull_whenNothingIsChosen() throws Exception {
        JsonNode sent = JSON.readTree(JSON.writeValueAsString(new PC()));

        assertThat(sent.has("subclassChoices")).isTrue();
        assertThat(sent.get("subclassChoices").isNull()).isTrue();
    }

    @Test
    void aRequestBody_neverSetsTheChoices_whateverShapeItSendsThemIn() throws Exception {
        String asObject = "{\"name\":\"Aelindra\",\"subclassChoices\":{\"vestigeType\":\"Fiend\"}}";
        String asText = "{\"name\":\"Aelindra\",\"subclassChoices\":\"{\\\"vestigeType\\\":\\\"Fiend\\\"}\"}";
        String asNull = "{\"name\":\"Aelindra\",\"subclassChoices\":null}";

        for (String body : new String[] {asObject, asText, asNull}) {
            PC read = JSON.readValue(body, PC.class);
            assertThat(read.getName()).as(body).isEqualTo("Aelindra");
            assertThat(read.getSubclassChoices()).as(body).isNull();
        }

        // The DM's save wraps the character; the same holds there.
        UpdatePcAsDmRequest wrapped = JSON.readValue("{\"pc\":" + asObject + ",\"description\":\"x\"}",
                UpdatePcAsDmRequest.class);
        assertThat(wrapped.pc().getName()).isEqualTo("Aelindra");
        assertThat(wrapped.pc().getSubclassChoices()).isNull();
    }

    @Test
    void whatTheServerSends_canBeSentStraightBack() throws Exception {
        PC pc = new PC();
        pc.setName("Aelindra");
        pc.setSubclassChoices("{\"vestigeType\":\"Fiend\",\"vestigeDomain\":\"War\"}");

        PC echoed = JSON.readValue(JSON.writeValueAsString(pc), PC.class);

        assertThat(echoed.getName()).isEqualTo("Aelindra");
        assertThat(echoed.getSubclassChoices()).isNull();
    }
}
