package uk.ac.ebi.spot.oxo.model.sssom;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Same-SPO grouping key semantics (ADR-0013): spo_key collapses a triple across sets / justifications
 * / inference types, keeps a relation distinct from its negation, and is a derived, output-only field.
 */
class MappingSpoKeyTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new Jdk8Module());

    private static Mapping.Builder triple(String subjectId, String predicateId, String objectId) {
        return new Mapping.Builder()
                .mappingSetId("https://example.org/set")
                .subjectId(subjectId)
                .predicateId(predicateId)
                .objectId(objectId);
    }

    @Test
    void spoKeyIsStableForTheSameTripleRegardlessOfSet() {
        Mapping inSetA = triple("A:1", "skos:exactMatch", "B:2")
                .mappingSetId("https://example.org/setA").build();
        Mapping inSetB = triple("A:1", "skos:exactMatch", "B:2")
                .mappingSetId("https://example.org/setB").build();
        assertEquals(inSetA.spoKey(), inSetB.spoKey(),
                "the same SPO triple must share a spo_key across mapping sets");
        assertNotEquals(inSetA.mappingId(), inSetB.mappingId(),
                "mapping_id still differs — the set is in the id hash, not the spo_key");
    }

    @Test
    void predicateModifierSeparatesPositiveFromNegated() {
        Mapping positive = triple("A:1", "skos:exactMatch", "B:2").build();
        Mapping negated = triple("A:1", "skos:exactMatch", "B:2").predicateModifier("Not").build();
        assertNotEquals(positive.spoKey(), negated.spoKey(),
                "a relation and its negation must not collapse into one group");
    }

    @Test
    void differentObjectsDoNotCollide() {
        Mapping toB = triple("A:1", "skos:exactMatch", "B:2").build();
        Mapping toC = triple("A:1", "skos:exactMatch", "B:3").build();
        assertNotEquals(toB.spoKey(), toC.spoKey());
    }

    @Test
    void spoKeyIsSerialisedAsTheSpoKeyField() throws Exception {
        String json = objectMapper.writeValueAsString(triple("A:1", "skos:exactMatch", "B:2").build());
        assertTrue(json.contains("\"spo_key\""), "serialised mapping must carry spo_key: " + json);
    }

    @Test
    void builderIgnoresDerivedSpoKeyOnInput() {
        // The derived, output-only spo_key must not break deserialisation through the builder.
        Mapping mapping = assertDoesNotThrow(() -> triple("A:1", "skos:exactMatch", "B:2")
                .spoKey("ignored-on-input")
                .build());
        assertFalse(mapping.spoKey().isBlank());
    }
}
