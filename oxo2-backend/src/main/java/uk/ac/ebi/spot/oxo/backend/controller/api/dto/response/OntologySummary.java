package uk.ac.ebi.spot.oxo.backend.controller.api.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * An ontology (CURIE prefix) present in the mappings index, with how many mappings use it as subject
 * and as object. Backs the cross-ontology mapping source/target selectors (ADR-0024).
 */
@Schema(description = "An ontology (CURIE prefix) in the mappings index, with subject/object mapping counts.")
public record OntologySummary(
        @Schema(description = "CURIE prefix identifying the ontology.", example = "DOID")
        @JsonProperty("prefix") String prefix,
        @Schema(description = "Number of mappings whose subject is in this ontology.")
        @JsonProperty("asSubject") long asSubject,
        @Schema(description = "Number of mappings whose object is in this ontology.")
        @JsonProperty("asObject") long asObject
) {}
