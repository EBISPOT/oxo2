package uk.ac.ebi.spot.oxo.model.sssom;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link EntityReference#getCuriePrefix()} is the single source of truth for "the ontology a term
 * belongs to" (ADR-0024): it backs both the {@code subject_prefix} / {@code object_prefix} Solr
 * fields and the ontology count that a mapping's {@code distance} is derived from (ADR-0031).
 */
class EntityReferenceCuriePrefixTest {

    @Test
    void curiePrefixIsThePrefixOfACurie() {
        assertEquals(Optional.of("DOID"), new EntityReference("DOID:0001816").getCuriePrefix());
    }

    @Test
    void curiePrefixIsUpperCasedSoOneOntologyIsOneBucket() {
        // EntityReference normalises the prefix to upper case, so differently-cased sources for the
        // same ontology never split into two prefix buckets (and two distance ontologies).
        assertEquals(Optional.of("MONDO"), new EntityReference("mondo:0005148").getCuriePrefix());
        assertEquals(new EntityReference("MONDO:0005148").getCuriePrefix(),
                new EntityReference("mondo:0005148").getCuriePrefix());
    }

    @Test
    void bareIriHasNoCuriePrefix() {
        assertTrue(new EntityReference("http://purl.obolibrary.org/obo/MONDO_0005148")
                .getCuriePrefix().isEmpty());
    }
}
