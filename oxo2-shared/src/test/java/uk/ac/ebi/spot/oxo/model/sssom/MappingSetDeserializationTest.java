package uk.ac.ebi.spot.oxo.model.sssom;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MappingSetDeserializationTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    void roundTripsSetConfidenceAndPublisher() {
        MappingSet source = objectMapper.readValue("""
                {"mapping_set_id":"https://example.org/set", "mapping_set_confidence":0.99,
                 "publisher":"Example publisher"}
                """, MappingSet.class);
        MappingSet restored = objectMapper.readValue(objectMapper.writeValueAsString(source), MappingSet.class);
        assertEquals(0.99, restored.mappingSetConfidence().orElseThrow());
        assertEquals(java.util.Set.of("Example publisher"), restored.publisher());
    }

    @ParameterizedTest
    @ValueSource(strings = {"users@example.org", "https://example.org/mailing-list", "mailto:users@example.org"})
    void roundTripsMailingListAsSupplied(String mailingList) {
        MappingSet source = MappingSet.builder().mappingSetId("https://example.org/set")
                .mailingList(mailingList).build();
        String json = objectMapper.writeValueAsString(source);
        MappingSet restored = objectMapper.readValue(json, MappingSet.class);
        assertEquals(mailingList, restored.mailingList().orElseThrow());
        assertEquals(mailingList, objectMapper.readTree(json).path("mailing_list").asText());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void omitsMissingMailingList(String mailingList) {
        MappingSet source = MappingSet.builder().mappingSetId("https://example.org/set")
                .mailingList(mailingList).build();
        assertFalse(objectMapper.readTree(objectMapper.writeValueAsString(source)).has("mailing_list"));
    }

    /**
     * Regression: {@code mapping_set_source} is multivalued in SSSOM, so a metadata header may
     * supply it as a sequence. The earlier String-only setter threw
     * "Cannot deserialize value of type String from Array value", which dropped whole sets
     * (e.g. the biopragmatics SeMRA {@code mapping_set_source} lists). It must now bind.
     */
    @Test
    void deserializesMappingSetSourceAsSequence() throws Exception {
        String json = "{\"mapping_set_id\":\"https://example.org/set\","
                + "\"mapping_set_source\":[\"https://w3id.org/a.sssom.tsv\","
                + "\"https://w3id.org/b.sssom.tsv\"]}";

        MappingSet mappingSet = objectMapper.readValue(json, MappingSet.class);

        assertEquals(2, mappingSet.mappingSetSource().size());
    }

    @Test
    void deserializesMappingSetSourceAsScalar() throws Exception {
        String json = "{\"mapping_set_id\":\"https://example.org/set\","
                + "\"mapping_set_source\":\"https://w3id.org/a.sssom.tsv\"}";

        MappingSet mappingSet = objectMapper.readValue(json, MappingSet.class);

        assertEquals(1, mappingSet.mappingSetSource().size());
    }
}
