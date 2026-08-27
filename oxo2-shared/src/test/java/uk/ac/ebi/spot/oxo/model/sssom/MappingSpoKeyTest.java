package uk.ac.ebi.spot.oxo.model.sssom;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Same-SPO grouping key semantics (ADR-0013): spo_key collapses a triple across sets / justifications
 * / inference types, keeps a relation distinct from its negation, and is a derived, output-only field.
 * A literal subject is identified by its label rather than by an absent subject_id (ADR-0042).
 */
class MappingSpoKeyTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private static Mapping.Builder triple(String subjectId, String predicateId, String objectId) {
        return new Mapping.Builder()
                .mappingSetId("https://example.org/set")
                .subjectId(subjectId)
                .predicateId(predicateId)
                .objectId(objectId);
    }

    /** A literal mapping: free text awaiting a CURIE, so subject_label but no subject_id. */
    private static Mapping.Builder literalTriple(String subjectLabel, String predicateId, String objectId) {
        return new Mapping.Builder()
                .mappingSetId("https://example.org/set")
                .subjectLabel(subjectLabel)
                .subjectType("rdfs literal")
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
    void literalSubjectsWithDifferentTextDoNotCollide() {
        // The defect ADR-0042 fixes: with the subject slot left empty, every literal mapping sharing a
        // predicate and object hashed identically — 7,539 distinct GWAS traits collapsed into one row.
        Mapping oneTrait = literalTriple("protein measurement levels", "skos:closeMatch", "EFO:0004747").build();
        Mapping anotherTrait = literalTriple("blood metabolite levels", "skos:closeMatch", "EFO:0004747").build();
        assertNotEquals(oneTrait.spoKey(), anotherTrait.spoKey(),
                "two unrelated free-text subjects mapped to the same object must not collapse into one row");
    }

    @Test
    void theSameLiteralSubjectStillCollapsesAcrossSets() {
        // The collapse ADR-0013 exists for: "lung" closeMatch UBERON:0002048 is asserted in five of the
        // ebi-text-mappings sets, and those five are one triple, not five.
        Mapping inSetA = literalTriple("lung", "skos:closeMatch", "UBERON:0002048")
                .mappingSetId("https://example.org/setA").build();
        Mapping inSetB = literalTriple("lung", "skos:closeMatch", "UBERON:0002048")
                .mappingSetId("https://example.org/setB").build();
        assertEquals(inSetA.spoKey(), inSetB.spoKey(),
                "the same literal subject mapped the same way must share a spo_key across mapping sets");
    }

    @Test
    void aLiteralSubjectNeverCollidesWithTheCurieItSpells() {
        // The literal marker earns its keep here: text reading "A:1" is not the term A:1.
        Mapping literal = literalTriple("A:1", "skos:exactMatch", "B:2").build();
        Mapping identified = triple("A:1", "skos:exactMatch", "B:2").build();
        assertNotEquals(literal.spoKey(), identified.spoKey(),
                "a free-text subject that happens to read like a CURIE must not join that CURIE's group");
    }

    @Test
    void anIdentifiedSubjectIgnoresLabelDrift() {
        // The fallback applies only where there is no id. Labels still vary per set for the same term,
        // and that must not split the group — the reason ADR-0013 keyed on ids in the first place.
        Mapping oneLabel = triple("A:1", "skos:exactMatch", "B:2").subjectLabel("Entity A").build();
        Mapping otherLabel = triple("A:1", "skos:exactMatch", "B:2").subjectLabel("entity a (obsolete)").build();
        assertEquals(oneLabel.spoKey(), otherLabel.spoKey(),
                "an identified subject must key on its id, so per-set label drift cannot split the group");
    }

    @Test
    void aSubjectWithNeitherIdNorLabelStillKeys() {
        Mapping mapping = new Mapping.Builder()
                .mappingSetId("https://example.org/set")
                .predicateId("skos:exactMatch")
                .objectId("B:2")
                .build();
        assertFalse(mapping.spoKey().isBlank(), "an unidentifiable subject must still produce a key");
    }

    @Test
    void prefixCaseDriftDoesNotSplitATriple() {
        // Not hypothetical: a single OLS export (testcases/worktree/mondo.ols.sssom.tsv) writes
        // doid:0050043 on its skos:exactMatch row and DOID:0050043 on its oboInOwl:hasDbXref row.
        // EntityReference upper-cases the prefix on parse, so both spellings index as the SAME
        // subject_id — keying on the raw string handed them two spo_keys and two result rows.
        Mapping lowerCasePrefixes = triple("doid:0050043", "skos:exactMatch", "mondo:0000616").build();
        Mapping upperCasePrefixes = triple("DOID:0050043", "SKOS:exactMatch", "MONDO:0000616").build();
        assertEquals(lowerCasePrefixes.spoKey(), upperCasePrefixes.spoKey(),
                "one triple written with differently-cased CURIE prefixes must be one group");
    }

    @Test
    void spoKeyMatchesTheIndexedIdsNotTheSourceSpelling() {
        // The invariant behind the case-drift fix, stated directly: whatever spelling arrives, the
        // key is the key of the ids Solr actually stores. An asserted mapping and the OxO2-inferred
        // mapping that duplicates it reach the model by different routes — the inferred side mints
        // its predicate CURIE from the prefix map, lowercase — and must still collapse together.
        Mapping asserted = triple("ex:b", "owl:equivalentClass", "ex:a").build();
        Mapping asIndexed = triple(
                asserted.subjectId().orElseThrow().getDataRepresentation().orElseThrow(),
                asserted.predicateId().orElseThrow().getDataRepresentation().orElseThrow(),
                asserted.objectId().orElseThrow().getDataRepresentation().orElseThrow()).build();
        assertEquals(asIndexed.spoKey(), asserted.spoKey(),
                "spo_key must hash the normalised ids that land in Solr, not the raw source strings");
    }

    @Test
    void caseFoldingStopsAtThePrefix() {
        // Only the prefix normalises. The local id is opaque and case-significant — OMIM:PS100070
        // is not OMIM:ps100070 — so it must keep splitting the group.
        Mapping oneLocalId = triple("OMIM:PS100070", "skos:exactMatch", "B:2").build();
        Mapping otherLocalId = triple("OMIM:ps100070", "skos:exactMatch", "B:2").build();
        assertNotEquals(oneLocalId.spoKey(), otherLocalId.spoKey(),
                "the local part of a CURIE identifies the term and must stay case-sensitive");
    }

    @Test
    void literalSubjectTextStaysCaseSensitive() {
        // The literal branch must NOT normalise (ADR-0042): a label is free text, and text identity
        // is case-sensitive on purpose. Only the CURIE branches fold their prefix.
        Mapping titleCase = literalTriple("Lung", "skos:closeMatch", "UBERON:0002048").build();
        Mapping lowerCase = literalTriple("lung", "skos:closeMatch", "UBERON:0002048").build();
        assertNotEquals(titleCase.spoKey(), lowerCase.spoKey(),
                "a literal subject is identified by its exact text, so case must still separate it");
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
