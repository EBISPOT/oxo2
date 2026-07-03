package uk.ac.ebi.spot.oxo.backend.controller.api.v1.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One mapping in the OxO v1 shape, for the {@code /api/mappings} compatibility endpoint (ADR-0025).
 * {@code mappingId} is always null — v1's numeric Neo4j id cannot be reproduced and mapping identity
 * is deferred to the SSSOM API. {@code sourceType} and {@code predicate} have no OxO2 equivalent and
 * are left null; {@code sourcePrefix} is the subject ontology prefix (matching v1), while
 * {@code datasource} identifies the SSSOM mapping set the mapping came from — v1 left it null on this
 * endpoint, so populating it cannot break wire-compat (see {@link V1Datasource#ofMappingSet}).
 * {@code scope} is derived from the SSSOM predicate (see {@link V1Scope}). Per-item HAL {@code _links}
 * are omitted: OxO2 exposes no per-mapping resource URL (no {@code /api/mappings/&#123;id&#125;}), so a
 * self link would be dead.
 */
public record V1Mapping(
        @JsonProperty("mappingId") Long mappingId,
        @JsonProperty("fromTerm") V1Term fromTerm,
        @JsonProperty("toTerm") V1Term toTerm,
        @JsonProperty("sourcePrefix") String sourcePrefix,
        @JsonProperty("sourceType") String sourceType,
        @JsonProperty("predicate") String predicate,
        @JsonProperty("datasource") V1Datasource datasource,
        @JsonProperty("scope") String scope,
        @JsonProperty("date") String date) {
}
