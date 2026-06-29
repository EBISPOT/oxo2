package uk.ac.ebi.spot.oxo.backend.controller.api.v1.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Hand-rolled HAL+JSON envelope matching OxO v1's {@code PagedResources<SearchResult>} output
 * (ADR-0024): {@code _embedded.searchResults}, {@code _links}, and a Spring-Data-style {@code page}
 * block. Hand-rolled rather than via spring-hateoas so the wire shape is reproduced exactly without a
 * new dependency or rel-naming surprises.
 */
public record HalSearchResponse(
        @JsonProperty("_embedded") Embedded embedded,
        @JsonProperty("_links") Map<String, Link> links,
        @JsonProperty("page") PageMetadata page) {

    public record Embedded(@JsonProperty("searchResults") List<V1SearchResult> searchResults) {}

    public record Link(@JsonProperty("href") String href) {}

    public record PageMetadata(
            @JsonProperty("size") int size,
            @JsonProperty("totalElements") long totalElements,
            @JsonProperty("totalPages") int totalPages,
            @JsonProperty("number") int number) {}
}
