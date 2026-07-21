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
     * ENSEMBL sets disagree three ways — the EBI RDF platform stem, the Ensembl-site https stem, and
     * its http variant — all keyed to one {@code ENSEMBL:} CURIE. The override pins the Ensembl-site
     * https form so one gene never splits into two Nemo nodes.
     */
    @Test
    void ensemblOverrideCanonicalisesEveryAliasStem() {
        assertEquals("https://www.ensembl.org/id/", PrefixIriOverrides.canonicalStem("ENSEMBL"));
        assertEquals("https://www.ensembl.org/id/ENSG00000186104",
                PrefixIriOverrides.canonicalizeIri("http://rdf.ebi.ac.uk/resource/ensembl/ENSG00000186104"));
        assertEquals("https://www.ensembl.org/id/ENSG00000186104",
                PrefixIriOverrides.canonicalizeIri("http://www.ensembl.org/id/ENSG00000186104"));
        // A set declaring the EBI RDF stem must not win over the override.
        CurieMap ebiRdf = new CurieMap("ENSEMBL:http://rdf.ebi.ac.uk/resource/ensembl/");
        assertEquals("https://www.ensembl.org/id/ENSG00000186104",
                new EntityReference("ENSEMBL:ENSG00000186104").toUri(ebiRdf).orElseThrow().getDataAsString());
    }

    /**
     * NCBIGENE splits between NCBI's own resolver and UniProt's PURL for the same gene id; the
     * canonical is the NCBI form (dominant and registry-standard).
     */
    @Test
    void ncbigeneOverrideFoldsTheUniprotGeneidAlias() {
        assertEquals("https://www.ncbi.nlm.nih.gov/gene/", PrefixIriOverrides.canonicalStem("NCBIGENE"));
        assertEquals("https://www.ncbi.nlm.nih.gov/gene/920",
                PrefixIriOverrides.canonicalizeIri("http://purl.uniprot.org/geneid/920"));
    }

    /**
     * CHEBI's dominant emitted form ({@code obo/chebi/}) is the non-canonical namespace spelling; the
     * override pins the proper OBO term PURL ({@code obo/CHEBI_}) regardless, including for the
     * EBI search-resolver alias whose stem ends in {@code CHEBI:}.
     */
    @Test
    void chebiOverridePinsTheOboTermPurlOverTheDominantNamespaceForm() {
        assertEquals("http://purl.obolibrary.org/obo/CHEBI_", PrefixIriOverrides.canonicalStem("CHEBI"));
        assertEquals("http://purl.obolibrary.org/obo/CHEBI_15377",
                PrefixIriOverrides.canonicalizeIri("http://purl.obolibrary.org/obo/chebi/15377"));
        assertEquals("http://purl.obolibrary.org/obo/CHEBI_15377",
                PrefixIriOverrides.canonicalizeIri("http://www.ebi.ac.uk/chebi/searchId.do?chebiId=CHEBI:15377"));
        // A set declaring the namespace form must lose to the override for CURIE inputs too.
        CurieMap namespaceForm = new CurieMap("CHEBI:http://purl.obolibrary.org/obo/chebi/");
        assertEquals("http://purl.obolibrary.org/obo/CHEBI_15377",
                new EntityReference("CHEBI:15377").toUri(namespaceForm).orElseThrow().getDataAsString());
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
