package uk.ac.ebi.spot.oxo.backend.controller.api.dto.response;

import java.util.Map;

/**
 * The deep health check's verdict (ADR-0051): whether this deployment can actually serve OxO2.
 *
 * <p>{@code status} is a sentence rather than a code because the EBI traffic manager matches the
 * response body against a regex — the healthy sentence is a contract with its monitor configuration
 * and appears in the body if and only if every core is both reachable and populated. The
 * {@code cores} map is for humans reading a failing probe: one entry per Solr core, in the fixed
 * order mappings, mappingsets, entities.
 */
public record HealthResponse(
        String status,
        Map<String, CoreHealth> cores) {

    /**
     * One core's contribution to the verdict. {@code ok} requires the core to have answered the
     * query <em>and</em> to hold at least one document — an empty core serves an empty site, which
     * for traffic routing counts as down.
     *
     * @param documents {@code numFound} for {@code *:*}; 0 when the core is unreachable
     * @param error     the failure that made the core unreachable, {@code null} when it answered
     */
    public record CoreHealth(boolean ok, long documents, String error) {
    }
}
