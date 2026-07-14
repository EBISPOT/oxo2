package uk.ac.ebi.spot.oxo.backend.controller.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import uk.ac.ebi.spot.oxo.model.sssom.MappingEnum;

/**
 * Ask for one field's values WITHIN a live search (ADR-0034) — the result-table column filters'
 * contextual typeahead.
 *
 * <p>It <b>wraps</b> a {@link MappingSearchRequest} rather than re-listing its filters. The
 * suggestions have to be scoped by exactly what the search is scoped by (the other column filters,
 * the corpus, the inference types, the ontology prefixes, the mapping sets, the weak-predicate
 * exclusion); carrying the search itself is what makes that true by construction instead of by
 * remembering to keep two field lists in step.
 */
@Schema(description = "Suggestions for one field's values, scoped to a live mapping search so that a "
        + "suggested value can never yield zero rows.")
public class ValueSuggestRequest {

    @Schema(description = "The field whose values to suggest.", example = "predicate_id")
    private MappingEnum field;

    @Schema(description = "The partial value the user has typed. Matched as a prefix.", example = "skos")
    private String query;

    @Schema(description = "Maximum suggestions to return.", defaultValue = "10")
    private int size = 10;

    @Schema(description = "The live mapping search the suggestions must be scoped to — exactly the "
            + "body POSTed to /api/v2/mappings/search.")
    private MappingSearchRequest search;

    public MappingEnum getField() {
        return field;
    }

    public void setField(MappingEnum field) {
        this.field = field;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public MappingSearchRequest getSearch() {
        return search;
    }

    public void setSearch(MappingSearchRequest search) {
        this.search = search;
    }
}
