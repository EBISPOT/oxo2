package uk.ac.ebi.spot.oxo.backend.controller.api.dto.request;

import com.fasterxml.jackson.annotation.JsonValue;
import uk.ac.ebi.spot.oxo.model.sssom.MappingEnum;

public enum MappingFacetEnum {
    MAPPING_SET_ID(MappingEnum.MAPPING_SET_ID.getField()),
    OBJECT_ID_PREFIX(MappingEnum.OBJECT_ID_PREFIX.getField()),
    SUBJECT_ID_PREFIX(MappingEnum.SUBJECT_ID_PREFIX.getField());

    private final String value;

    MappingFacetEnum(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
