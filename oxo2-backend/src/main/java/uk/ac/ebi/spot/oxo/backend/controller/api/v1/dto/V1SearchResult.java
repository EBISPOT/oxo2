package uk.ac.ebi.spot.oxo.backend.controller.api.v1.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * One input term and its mappings, in the OxO v1 shape (ADR-0024). An input that maps to nothing comes
 * back with null {@code curie}/{@code label} and an empty {@code mappingResponseList}, as in v1.
 */
public record V1SearchResult(
        @JsonProperty("queryId") String queryId,
        @JsonProperty("querySource") String querySource,
        @JsonProperty("curie") String curie,
        @JsonProperty("label") String label,
        @JsonProperty("mappingResponseList") List<V1MappingResponse> mappingResponseList) {
}
