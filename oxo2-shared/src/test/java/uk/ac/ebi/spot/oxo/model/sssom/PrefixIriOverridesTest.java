package uk.ac.ebi.spot.oxo.model.sssom;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PrefixIriOverridesTest {

    @Test
    void seededMeshOverrideResolvesToTheCanonicalStem() {
        assertEquals("http://id.nlm.nih.gov/mesh/", PrefixIriOverrides.canonicalStem("MESH"));
    }

    @Test
    void nonOverriddenPrefixHasNoCanonicalStem() {
        assertNull(PrefixIriOverrides.canonicalStem("DOID"));
    }

    @Test
    void fullIriUsingAnAliasStemIsRewrittenToCanonical() {
        assertEquals("http://id.nlm.nih.gov/mesh/D020176",
                PrefixIriOverrides.canonicalizeIri("http://identifiers.org/mesh/D020176"));
    }

    @Test
    void fullIriAlreadyCanonicalIsUnchanged() {
        assertEquals("http://id.nlm.nih.gov/mesh/D020176",
                PrefixIriOverrides.canonicalizeIri("http://id.nlm.nih.gov/mesh/D020176"));
    }

    @Test
    void unrelatedFullIriIsUnchanged() {
        assertEquals("http://purl.obolibrary.org/obo/DOID_9275",
                PrefixIriOverrides.canonicalizeIri("http://purl.obolibrary.org/obo/DOID_9275"));
    }

    @Test
    void curieForAnOverriddenPrefixExpandsToTheCanonicalStemRegardlessOfCurieMap() {
        // A curie_map that (wrongly) declares the identifiers.org stem must NOT win over the override.
        CurieMap misdeclared = new CurieMap("MESH:http://identifiers.org/mesh/");
        assertEquals("http://id.nlm.nih.gov/mesh/D020176",
                new EntityReference("MESH:D020176").toUri(misdeclared).orElseThrow().getDataAsString());
    }

    @Test
    void fullIriAliasIsCanonicalisedThroughEntityReference() {
        assertEquals("http://id.nlm.nih.gov/mesh/D020176",
                new EntityReference("http://identifiers.org/mesh/D020176")
                        .toUri(new CurieMap("")).orElseThrow().getDataAsString());
    }

    @Test
    void additionalSeededPrefixesResolveToTheirIdentityStem() {
        assertEquals("http://snomed.info/id/", PrefixIriOverrides.canonicalStem("SCTID"));
        assertEquals("http://purl.bioontology.org/ontology/ICD10CM/", PrefixIriOverrides.canonicalStem("ICD10CM"));
        assertEquals("http://linkedlifedata.com/resource/umls/id/", PrefixIriOverrides.canonicalStem("UMLS"));
        assertEquals("http://purl.obolibrary.org/obo/FMA_", PrefixIriOverrides.canonicalStem("FMA"));
    }

    @Test
    void meshbAliasIsAlsoCanonicalised() {
        assertEquals("http://id.nlm.nih.gov/mesh/D020176",
                PrefixIriOverrides.canonicalizeIri("https://meshb.nlm.nih.gov/record/ui?ui=D020176"));
    }

    /**
     * OMIM 'MIM:' and OMIMPS 'MIM:PS' overlap as string prefixes; longest-alias-wins must keep them
     * disjoint so a phenotypic-series IRI is never mis-canonicalised as a plain OMIM entry.
     */
    @Test
    void overlappingOmimStemsStayDisjointViaLongestMatch() {
        assertEquals("https://omim.org/phenotypicSeries/PS276700",
                PrefixIriOverrides.canonicalizeIri("https://omim.org/MIM:PS276700"));
        assertEquals("https://omim.org/entry/276700",
                PrefixIriOverrides.canonicalizeIri("https://omim.org/MIM:276700"));
    }
}
