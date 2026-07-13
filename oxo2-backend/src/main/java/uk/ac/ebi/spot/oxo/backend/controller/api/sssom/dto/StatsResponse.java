package uk.ac.ebi.spot.oxo.backend.controller.api.sssom.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Corpus counts served by {@code GET /api/sssom/stats} (ADR-0032), matching the reference API's
 * {@code stats}. {@code nb_entity} is an estimate; see the endpoint documentation.
 */
public record StatsResponse(
        @JsonProperty("nb_mapping") long nbMapping,
        @JsonProperty("nb_mapping_set") long nbMappingSet,
        @JsonProperty("nb_mapping_provider") long nbMappingProvider,
        @JsonProperty("nb_entity") long nbEntity) {
}
