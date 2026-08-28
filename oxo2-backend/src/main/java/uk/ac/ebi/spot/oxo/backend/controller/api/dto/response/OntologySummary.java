package uk.ac.ebi.spot.oxo.backend.controller.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * An ontology (CURIE prefix) present in the mappings index, with how many mappings use it as subject
 * and as object. Backs the cross-ontology mapping source/target selectors (ADR-0024).
 *
 * <p>{@code namespace} and {@code uri} are two different things and neither is derivable from the
 * other: for MONDO they are {@code http://purl.obolibrary.org/obo/MONDO_} and
 * {@code http://purl.obolibrary.org/obo/mondo.owl} (ADR-0047).
 *
 * <p>Both are omitted rather than nulled when unknown. That makes {@code uri}'s presence meaningful:
 * this listing is every CURIE prefix in the corpus, most of which are not ontologies at all
 * ({@code ATC_CODE}, {@code AISM_2}, {@code AEO_RETIRED}), and a {@code uri} appears only where a real
 * ontology backs the prefix.
 */
@Schema(description = "An ontology (CURIE prefix) in the mappings index, with subject/object mapping counts.")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OntologySummary(
        @Schema(description = "CURIE prefix identifying the ontology.", example = "DOID")
        @JsonProperty("prefix") String prefix,
        @Schema(description = "IRI stem this prefix's CURIEs expand against, as actually minted by the "
                + "dataload. Absent when it cannot be derived.",
                example = "http://purl.obolibrary.org/obo/DOID_")
        @JsonProperty("namespace") String namespace,
        @Schema(description = "IRI of the ontology itself. Present only for prefixes backed by an "
                + "ontology-derived mapping set; absent for prefixes that name no ontology.",
                example = "http://purl.obolibrary.org/obo/doid.owl")
        @JsonProperty("uri") String uri,
        @Schema(description = "Number of mappings whose subject is in this ontology.")
        @JsonProperty("asSubject") long asSubject,
        @Schema(description = "Number of mappings whose object is in this ontology.")
        @JsonProperty("asObject") long asObject
) {}
