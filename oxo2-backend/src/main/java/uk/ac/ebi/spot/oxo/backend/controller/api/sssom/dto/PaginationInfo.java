package uk.ac.ebi.spot.oxo.backend.controller.api.sssom.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Pagination block of the SSSOM-API envelope (ADR-0032). Paging is 1-based ({@code page_number}) with
 * a {@code limit} query parameter, matching the reference API. {@code previous} / {@code next} are
 * absolute URLs to the adjacent pages (built from the current request, so they are correct behind the
 * ingress once {@code server.forward-headers-strategy=framework} honours the {@code X-Forwarded-*}
 * headers), or {@code null} at the ends of the range — serialised explicitly as {@code null}, as the
 * reference does, rather than omitted.
 */
public record PaginationInfo(
        @JsonProperty("previous") String previous,
        @JsonProperty("next") String next,
        @JsonProperty("page_number") int pageNumber,
        @JsonProperty("total_items") long totalItems,
        @JsonProperty("total_pages") int totalPages) {
}
