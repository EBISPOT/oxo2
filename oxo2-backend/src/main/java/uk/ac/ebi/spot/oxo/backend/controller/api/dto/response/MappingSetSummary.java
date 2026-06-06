package uk.ac.ebi.spot.oxo.backend.controller.api.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

import static uk.ac.ebi.spot.oxo.model.sssom.MappingSetConstants.*;

public record MappingSetSummary(
        @JsonProperty(MAPPING_SET_ID) String mappingSetId,
        @JsonProperty(MAPPING_SET_TITLE) String mappingSetTitle,
        @JsonProperty(MAPPING_SET_DESCRIPTION) String mappingSetDescription,
        @JsonProperty(CREATOR_LABEL) List<String> creatorLabel,
        @JsonProperty(MAPPING_PROVIDER) String mappingProvider,
        @JsonProperty(INFERENCE_TYPE) String inferenceType,
        @JsonProperty(MAPPING_SET_SOURCE) List<String> mappingSetSource
) {}
