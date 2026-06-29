package uk.ac.ebi.spot.oxo.backend.controller.api.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Batch cross-ontology mapping result (ADR-0024): the page of matching mappings, plus the inputs that
 * mapped to nothing in the targets — the unmapped report unique to the explicit-term-list mode.
 */
@Schema(description = "Batch mapping result: matching mappings, the unmapped inputs, and a summary.")
public record BatchMapResponse(
        @Schema(description = "Page of matching mappings for the input terms.")
        @JsonProperty("mappings") MappingSearchResponse mappings,
        @Schema(description = "Input terms that produced no mapping into the targets.")
        @JsonProperty("unmappedInputs") List<String> unmappedInputs,
        @Schema(description = "Counts over the input set.")
        @JsonProperty("summary") Summary summary) {

    @Schema(description = "Counts over the (cleaned, de-duplicated) input set.")
    public record Summary(
            @Schema(description = "Number of distinct, non-blank input terms.")
            @JsonProperty("inputCount") int inputCount,
            @Schema(description = "Inputs with at least one mapping into the targets.")
            @JsonProperty("mappedCount") int mappedCount,
            @Schema(description = "Inputs with no mapping into the targets.")
            @JsonProperty("unmappedCount") int unmappedCount) {}
}
