package uk.ac.ebi.spot.oxo.backend.controller.api.v1.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Set;

/**
 * OxO v1 {@code Datasource} shape, reproduced for the {@code /api/mappings} compatibility endpoint
 * (ADR-0025). OxO2 has no datasource node — an ontology is just a CURIE prefix (ADR-0024) — so the
 * remaining v1 fields are kept in the wire shape but left null/empty, since OxO2 holds no equivalent
 * registry entry. Two flavours are synthesized: {@link #ofPrefix} for a <em>term's</em> datasource
 * (its ontology, keyed by CURIE prefix) and {@link #ofMappingSet} for a <em>mapping's</em> datasource
 * (the SSSOM mapping set it came from, keyed by {@code mapping_set_id}).
 */
public record V1Datasource(
        @JsonProperty("prefix") String prefix,
        @JsonProperty("preferredPrefix") String preferredPrefix,
        @JsonProperty("idorgNamespace") String idorgNamespace,
        @JsonProperty("alternatePrefix") Set<String> alternatePrefix,
        @JsonProperty("alternateIris") Set<String> alternateIris,
        @JsonProperty("name") String name,
        @JsonProperty("orcid") String orcid,
        @JsonProperty("description") String description,
        @JsonProperty("source") String source,
        @JsonProperty("licence") String licence,
        @JsonProperty("versionInfo") String versionInfo) {

    /**
     * Synthesize a datasource carrying only the ontology prefix (v1's {@code preferredPrefix} defaulted
     * to {@code prefix}); a blank prefix — e.g. a bare IRI that never resolved to a CURIE — yields null,
     * so the enclosing {@code datasource} field is present but null rather than an all-empty object.
     */
    public static V1Datasource ofPrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return null;
        }
        return new V1Datasource(prefix, prefix, null, Set.of(), Set.of(),
                null, null, null, null, null, null);
    }

    /**
     * Synthesize a mapping's datasource from the SSSOM mapping set it came from — OxO2's notion of
     * "where this mapping came from" (v1 left the mapping-level datasource null on this endpoint, so a
     * populated value cannot break wire-compat). {@code mapping_set_id} carries the identity
     * ({@code prefix} / {@code preferredPrefix}, the fields a v1 client reads for datasource identity)
     * and {@code mapping_set_title}, when present, the human {@code name}. A blank id yields null, so
     * the enclosing {@code datasource} field is present but null rather than an all-empty object.
     */
    public static V1Datasource ofMappingSet(String mappingSetId, String mappingSetTitle) {
        if (mappingSetId == null || mappingSetId.isBlank()) {
            return null;
        }
        String name = (mappingSetTitle == null || mappingSetTitle.isBlank()) ? null : mappingSetTitle;
        return new V1Datasource(mappingSetId, mappingSetId, null, Set.of(), Set.of(),
                name, null, null, null, null, null);
    }
}
