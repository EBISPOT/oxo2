package uk.ac.ebi.spot.oxo.backend.controller.api.v1.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * OxO v1 {@code Term} shape, reproduced for the {@code /api/mappings} compatibility endpoint
 * (ADR-0025). {@code curie}, {@code label} and {@code uri} (the full IRI, from the {@code subject_iri}
 * / {@code object_iri} index fields) are populated; {@code identifier} — the bare local id, which OxO2
 * does not carry — is left null. {@code datasource} is synthesized from the CURIE prefix (the term's
 * ontology).
 */
public record V1Term(
        @JsonProperty("curie") String curie,
        @JsonProperty("identifier") String identifier,
        @JsonProperty("uri") String uri,
        @JsonProperty("label") String label,
        @JsonProperty("datasource") V1Datasource datasource) {
}
