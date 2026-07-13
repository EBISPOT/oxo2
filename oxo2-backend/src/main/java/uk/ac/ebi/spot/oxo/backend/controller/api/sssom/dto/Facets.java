package uk.ac.ebi.spot.oxo.backend.controller.api.sssom.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Facets block of the SSSOM-API envelope (ADR-0032), computed over the whole filtered result set (not
 * just the returned page), mirroring the reference API. {@code mapping_justification} and {@code
 * predicate_id} are value → count maps; {@code confidence} is the min/max range of the declared
 * confidences.
 *
 * <p>Unlike the reference — which streams every matching row through the application to count them —
 * these come straight from Solr's facet/stats components on the same query that fetches the page, so
 * one round trip settles both. Under same-SPO collapse (ADR-0023) the counts are over the collapse
 * representatives, consistent with {@code total_items} being the group count.
 */
public record Facets(
        @JsonProperty("mapping_justification") Map<String, Long> mappingJustification,
        @JsonProperty("predicate_id") Map<String, Long> predicateId,
        @JsonProperty("confidence") ConfidenceRange confidence) {
}
