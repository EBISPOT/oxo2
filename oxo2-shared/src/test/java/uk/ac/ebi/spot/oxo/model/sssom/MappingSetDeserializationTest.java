package uk.ac.ebi.spot.oxo.model.sssom;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MappingSetDeserializationTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

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
