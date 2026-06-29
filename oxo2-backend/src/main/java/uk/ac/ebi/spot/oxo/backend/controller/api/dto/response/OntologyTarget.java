package uk.ac.ebi.spot.oxo.backend.controller.api.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A target ontology reachable from a chosen source ontology, with the number of mappings into it.
 * Returned by {@code GET /api/v2/ontologies?forSubject=...} to drive the count-laden target selector
 * (ADR-0024).
 */
@Schema(description = "A target ontology reachable from a source ontology, with the mapping count into it.")
public record OntologyTarget(
        @Schema(description = "CURIE prefix of the target ontology.", example = "EFO")
        @JsonProperty("prefix") String prefix,
        @Schema(description = "Number of mappings from the source ontology into this target.")
        @JsonProperty("count") long count
) {}
