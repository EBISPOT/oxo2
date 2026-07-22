package uk.ac.ebi.spot.oxo.backend.controller.api.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

import static uk.ac.ebi.spot.oxo.model.sssom.MappingSetConstants.*;

@Schema(description = "Summary view of a mapping set.")
public record MappingSetSummary(
        @Schema(description = "Mapping-set id (full IRI).") @JsonProperty(MAPPING_SET_ID) String mappingSetId,
        @Schema(description = "Human-readable title.") @JsonProperty(MAPPING_SET_TITLE) String mappingSetTitle,
        @Schema(description = "Free-text description.") @JsonProperty(MAPPING_SET_DESCRIPTION) String mappingSetDescription,
        @Schema(description = "Creator labels.") @JsonProperty(CREATOR_LABEL) List<String> creatorLabel,
        @Schema(description = "Mapping provider URL.") @JsonProperty(MAPPING_PROVIDER) String mappingProvider,
        @Schema(description = "Inference type of the set (`ASSERTED` or `SSSOM_INFERENCE`).") @JsonProperty(INFERENCE_TYPE) String inferenceType,
        @Schema(description = "Source sets this set was derived from (full IRIs).") @JsonProperty(MAPPING_SET_SOURCE) List<String> mappingSetSource,
        @Schema(description = "OxO curation category: `ONTOLOGY` (OLS-derived) or `CURATED`; absent on the synthetic inferences set.") @JsonProperty(MAPPING_SET_CATEGORY) String mappingSetCategory,
        @Schema(description = "CURIE prefix of the ontology (ONTOLOGY sets only), e.g. `ADDICTO`.") @JsonProperty(PREFIX) String prefix,
        @Schema(description = "Human-readable ontology name (ONTOLOGY sets only), e.g. `Addiction Ontology (ADDICTO)`.") @JsonProperty(ONTOLOGY) String ontology
) {}
