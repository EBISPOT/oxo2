package uk.ac.ebi.spot.oxo.backend.controller.api.dto.response;

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

        @Schema(description = "How many mappings this entity participates in, on either side. Drives "
                + "the popularity ranking, and is shown alongside the suggestion.")
        @JsonProperty("mapping_count") long mappingCount
) {}
