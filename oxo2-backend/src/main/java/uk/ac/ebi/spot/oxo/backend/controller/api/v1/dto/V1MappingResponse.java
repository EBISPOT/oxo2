package uk.ac.ebi.spot.oxo.backend.controller.api.v1.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * One mapping for an input term, in the OxO v1 shape (ADR-0024). {@code distance} is a coarse
 * direct/indirect sentinel (1 = asserted, 2 = inferred), not a true hop count — OxO2 cannot reproduce
 * v1's query-time depth over a precomputed closure.
 */
public record V1MappingResponse(
        @JsonProperty("curie") String curie,
        @JsonProperty("label") String label,
        @JsonProperty("sourcePrefixes") List<String> sourcePrefixes,
        @JsonProperty("targetPrefix") String targetPrefix,
        @JsonProperty("distance") int distance) {
}
