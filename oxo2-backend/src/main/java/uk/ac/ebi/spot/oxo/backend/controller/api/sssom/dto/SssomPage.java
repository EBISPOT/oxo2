package uk.ac.ebi.spot.oxo.backend.controller.api.sssom.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * The SSSOM-API list envelope (ADR-0032): {@code {data, pagination, facets}}, mirroring the
 * mapping-commons reference API (its {@code Page} model). Every list endpoint of {@code /api/sssom}
 * returns this shape.
 *
 * <p>{@code facets} is present on mapping results (their {@code mapping_justification} / {@code
 * predicate_id} / {@code confidence} facets) and omitted for {@code /mapping_sets}, whose documents
 * carry none of those slots — the reference computes the same facets unconditionally and would fault
 * on a mapping set, so omitting them here is the deliberate fix.
 *
 * @param <T> the element type ({@code Mapping} for mapping results, a stored-field map for sets).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SssomPage<T>(
        @JsonProperty("data") List<T> data,
        @JsonProperty("pagination") PaginationInfo pagination,
        @JsonProperty("facets") Facets facets) {
}
