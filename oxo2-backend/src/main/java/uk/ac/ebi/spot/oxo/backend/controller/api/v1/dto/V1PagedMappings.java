package uk.ac.ebi.spot.oxo.backend.controller.api.v1.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Hand-rolled HAL+JSON envelope matching OxO v1's {@code PagedResources<Mapping>} output for
 * {@code GET /api/mappings} (ADR-0025): {@code _embedded.mappings}, {@code _links}, and a
 * Spring-Data-style {@code page} block. Reuses {@link HalSearchResponse.Link} /
 * {@link HalSearchResponse.PageMetadata} so the two v1 envelopes stay identical in shape. The
 * collection {@code _links} carries a relative {@code self} (mirroring the {@code /api/search}
 * envelope); actual paging state lives in the {@code page} block.
 */
public record V1PagedMappings(
        @JsonProperty("_embedded") Embedded embedded,
        @JsonProperty("_links") Map<String, HalSearchResponse.Link> links,
        @JsonProperty("page") HalSearchResponse.PageMetadata page) {

    public record Embedded(@JsonProperty("mappings") List<V1Mapping> mappings) {}
}
