package uk.ac.ebi.spot.oxo.backend.controller.api.dto.request;

import com.fasterxml.jackson.annotation.JsonValue;

public enum SortOrderEnum {
    ASC("asc"),
    DESC("desc");

    private final String value;

    SortOrderEnum(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
