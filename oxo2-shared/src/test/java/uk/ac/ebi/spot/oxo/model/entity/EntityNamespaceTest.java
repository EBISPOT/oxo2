package uk.ac.ebi.spot.oxo.model.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The namespace stored on an entity is derived from the CURIE and the IRI the dataload actually
 * minted for it, by stripping the local part (ADR-0047). Deriving it this way rather than looking the
 * prefix up in a registry is what makes it answerable for prefixes no registry knows, and what stops
 * it contradicting the IRIs the API serves.
 */
class EntityNamespaceTest {

    @Test
    void stripsTheLocalPartFromAnObofoundryIri() {
        assertEquals("http://purl.obolibrary.org/obo/MONDO_",
                EntityConstants.namespaceOf("MONDO:0005148",
                        "http://purl.obolibrary.org/obo/MONDO_0005148"));
    }

    /** A prefix the Bioregistry snapshot does not carry still resolves, because nothing is looked up. */
    @Test
    void derivesAPrefixNoRegistryKnows() {
        assertEquals("http://aber-owl.net/ontology/",
                EntityConstants.namespaceOf("ABEROWL:GO", "http://aber-owl.net/ontology/GO"));
    }

    /**
     * The stem OxO2 minted for HGNC is the ADR-0029 override, not the {@code genenames.org} form the
     * source set's own curie_map declares. Deriving from the indexed IRI reports the former, which is
     * the one the API serves.
     */
    @Test
    void reportsTheOverriddenStemRatherThanTheDeclaredOne() {
        assertEquals("http://identifiers.org/hgnc/",
                EntityConstants.namespaceOf("HGNC:5056", "http://identifiers.org/hgnc/5056"));
    }

    /**
     * A namespace is not always a path stem — a resolver-style prefix ends mid-query-string, and can
     * itself contain a colon. Real shape, from the {@code CHEBI_2} entities in the test corpus.
     */
    @Test
    void derivesAResolverStyleNamespaceContainingAColon() {
        assertEquals("http://www.ebi.ac.uk/chebi/searchId.do?chebiId=CHEBI:",
                EntityConstants.namespaceOf("CHEBI_2:6495",
                        "http://www.ebi.ac.uk/chebi/searchId.do?chebiId=CHEBI:6495"));
    }

    /** Only the FIRST colon separates prefix from local part, so a local part may contain one. */
    @Test
    void splitsOnTheFirstColonOnly() {
        assertEquals("http://snomed.info/id/",
                EntityConstants.namespaceOf("SCTID:x:y", "http://snomed.info/id/x:y"));
    }

    @Test
    void yieldsNothingWhenTheIriDoesNotEndWithTheLocalPart() {
        assertNull(EntityConstants.namespaceOf("MONDO:0005148", "http://example.org/something-else"));
    }

    @Test
    void yieldsNothingForABareIriThatNeverResolvedToACurie() {
        assertNull(EntityConstants.namespaceOf("http://example.org/thing", null));
        assertNull(EntityConstants.namespaceOf("no-colon-here", "http://example.org/thing"));
    }

    @Test
    void yieldsNothingForAnEmptyLocalPart() {
        assertNull(EntityConstants.namespaceOf("MONDO:", "http://purl.obolibrary.org/obo/MONDO_"));
    }

    @Test
    void yieldsNothingForNullInput() {
        assertNull(EntityConstants.namespaceOf(null, "http://purl.obolibrary.org/obo/MONDO_1"));
        assertNull(EntityConstants.namespaceOf("MONDO:1", null));
    }
}
