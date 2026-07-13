package uk.ac.ebi.spot.oxo.backend.controller.api.sssom.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The min/max span of the {@code confidence} slot across a result set (ADR-0032). Solr's stats
 * component ignores documents without a confidence, so the range reflects only mappings that declare
 * one; both bounds are {@code null} when no matching mapping does.
 */
public record ConfidenceRange(
        @JsonProperty("min") Double min,
        @JsonProperty("max") Double max) {
}
