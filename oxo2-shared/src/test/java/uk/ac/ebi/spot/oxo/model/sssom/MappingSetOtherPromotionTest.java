package uk.ac.ebi.spot.oxo.model.sssom;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OLS SSSOM extracts carry an ontology's CURIE prefix and display name inside the mapping-set
 * {@code other} extension block. The dataload promotes them to discrete top-level {@code prefix} /
 * {@code ontology} fields (ADR-0038) so the frontend can list ontologies with their own columns.
 * These assert that promotion on the serialized mapping-set doc.
 */
class MappingSetOtherPromotionTest {

    // Mirror the writer in TSV2JSON. Jackson 3 unwraps Optional fields natively, so unlike the
    // Jackson 2 mapper this replaced there is no Jdk8Module to register.
    // The record's class-level @JsonInclude(NON_EMPTY) omits absent ones regardless of mapper defaults.
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    void promotesPrefixAndOntologyFromOther() throws Exception {
        Map<String, String> other = new LinkedHashMap<>();
        other.put("local_id", "addicto.ols");
        other.put(MappingConstants.PREFIX, "ADDICTO");
        other.put(MappingConstants.ONTOLOGY, "Addiction Ontology (ADDICTO)");
        other.put(MappingConstants.ONTOLOGY_IRI, "http://addictovocab.org/addicto.owl");
        MappingSet mappingSet = MappingSet.builder()
                .mappingSetId("https://w3id.org/commons/ols/mappings/addicto.ols.sssom.tsv")
                .other(other)
                .build();

        JsonNode doc = objectMapper.readTree(objectMapper.writeValueAsString(mappingSet));

        assertEquals("ADDICTO", doc.path(MappingConstants.PREFIX).asString());
        assertEquals("Addiction Ontology (ADDICTO)", doc.path(MappingConstants.ONTOLOGY).asString());
        assertEquals("http://addictovocab.org/addicto.owl",
                doc.path(MappingConstants.ONTOLOGY_IRI).asString());
        // The raw `other` block is kept alongside the promoted fields (it still carries local_id).
        assertTrue(doc.has(MappingConstants.OTHER));
    }

    /**
     * The ontology IRI is the ontology's own identity; the namespace its terms expand against is a
     * different string that this promotion neither carries nor can derive (ADR-0047).
     */
    @Test
    void promotesOntologyIriIndependentlyOfPrefixAndName() throws Exception {
        Map<String, String> other = new LinkedHashMap<>();
        other.put(MappingConstants.ONTOLOGY_IRI, "http://purl.obolibrary.org/obo/sepio.owl");
        MappingSet mappingSet = MappingSet.builder()
                .mappingSetId("https://w3id.org/commons/ols/mappings/sepio.ols.sssom.tsv")
                .other(other)
                .build();

        JsonNode doc = objectMapper.readTree(objectMapper.writeValueAsString(mappingSet));

        assertEquals("http://purl.obolibrary.org/obo/sepio.owl",
                doc.path(MappingConstants.ONTOLOGY_IRI).asString());
        assertFalse(doc.has(MappingConstants.PREFIX));
        assertFalse(doc.has(MappingConstants.ONTOLOGY));
    }

    @Test
    void omitsPrefixAndOntologyWhenNoOtherBlock() throws Exception {
        MappingSet mappingSet = MappingSet.builder()
                .mappingSetId("https://example.org/curated.sssom.tsv")
                .build();

        JsonNode doc = objectMapper.readTree(objectMapper.writeValueAsString(mappingSet));

        assertFalse(doc.has(MappingConstants.PREFIX));
        assertFalse(doc.has(MappingConstants.ONTOLOGY));
        assertFalse(doc.has(MappingConstants.ONTOLOGY_IRI));
    }
}
