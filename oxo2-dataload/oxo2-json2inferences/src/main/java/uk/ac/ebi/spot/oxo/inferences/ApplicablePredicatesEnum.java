package uk.ac.ebi.spot.oxo.inferences;

public enum ApplicablePredicatesEnum {
    // Predicates whose mappings JSON2NQuads emits into the N-Quad inference corpus. Predicates not
    // listed here are still indexed as asserted mappings (json2solr) but do not enter inference.
    // oboInOwl#hasDbXref / obo#hasDbXref are intentionally excluded: sssom.rls has no rule that
    // chains them (so they were inert in the corpus), and their objects are frequently bare
    // database codes (IMDRF, GARD, …) with no IRI scheme, which Nemo's RDF reader silently drops.
    OWL_EQUIVALENT_CLASS("http://www.w3.org/2002/07/owl#equivalentClass"),
    OWL_EQUIVALENT_PROPERTY("http://www.w3.org/2002/07/owl#equivalentProperty"),
    OWL_SAME_AS("http://www.w3.org/2002/07/owl#sameAs"),
    RDF_TYPE("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
    RDFS_SUB_CLASS_OF("http://www.w3.org/2000/01/rdf-schema#subClassOf"),
    RDFS_SUB_PROPERTY_OF("http://www.w3.org/2000/01/rdf-schema#subPropertyOf"),
    RDFS_SEE_ALSO("http://www.w3.org/2000/01/rdf-schema#seeAlso"),
    SEMAPV_CROSS_SPECIES_BROAD_MATCH("https://w3id.org/semapv/vocab/crossSpeciesBroadMatch"),
    SEMAPV_CROSS_SPECIES_EXACT_MATCH("https://w3id.org/semapv/vocab/crossSpeciesExactMatch"),
    SEMAPV_CROSS_SPECIES_NARROW_MATCH("https://w3id.org/semapv/vocab/crossSpeciesNarrowMatch"),
    SKOS_BROAD_MATCH("http://www.w3.org/2004/02/skos/core#broadMatch"),
    SKOS_CLOSE_MATCH("http://www.w3.org/2004/02/skos/core#closeMatch"),
    SKOS_EXACT_MATCH("http://www.w3.org/2004/02/skos/core#exactMatch"),
    SKOS_NARROW_MATCH("http://www.w3.org/2004/02/skos/core#narrowMatch"),
    SKOS_RELATED_MATCH("http://www.w3.org/2004/02/skos/core#relatedMatch");


    private final String iri;

    ApplicablePredicatesEnum(String iri) {
        this.iri = iri;
    }

    public String getIri() {
        return iri;
    }
}
