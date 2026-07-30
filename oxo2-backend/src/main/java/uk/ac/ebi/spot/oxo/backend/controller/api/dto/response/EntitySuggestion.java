package uk.ac.ebi.spot.oxo.backend.controller.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One entity offered as a typeahead completion, read from the {@code oxo2-entities} collection
 * (ADR-0034). Carries the CURIE, label and IRI together so the suggestion row can render the
 * label &rsaquo; id &rsaquo; IRI stack the result table already uses for an entity reference.
 */
@Schema(description = "An entity suggested as a completion of what the user has typed.")
public record EntitySuggestion(
        @Schema(description = "The entity's CURIE.", example = "MONDO:0005148")
        @JsonProperty("id") String id,

        @Schema(description = "The entity's label. Absent when no mapping carries one.",
                example = "type 2 diabetes mellitus")
        @JsonProperty("label") String label,

        @Schema(description = "The entity's IRI.",
                example = "http://purl.obolibrary.org/obo/MONDO_0005148")
        @JsonProperty("iri") String iri,

        @Schema(description = "The entity's CURIE prefix (its ontology).", example = "MONDO")
        @JsonProperty("prefix") String prefix,

        @Schema(description = "How many mappings picking this suggestion will actually return: the "
                + "entity's mappings on the side being searched, counting only the predicates the "
                + "caller's `includeWeakPredicates` makes visible (ADR-0035). Non-zero when present — an "
                + "entity with nothing to show is not suggested. Deliberately NOT the entity's total "
                + "mapping count, which would promise rows the search then hides. Drives the "
                + "popularity ranking, and is shown alongside the suggestion.\n\n"
                + "ABSENT under a `mappingSetId` restriction (ADR-0044): the counts behind it are "
                + "corpus-wide, so reporting one for a narrowed set would overstate the rows. The "
                + "filtering is still exact — the suggestion does return rows — only the number is "
                + "withheld, and the suggestion row then shows none.")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonProperty("mapping_count") Long mappingCount
) {}
