package uk.ac.ebi.spot.oxo.backend.controller.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "An advanced query against a single field.")
public class FieldQuery {

    @Schema(description = "Solr field id to query.", example = "subject_label")
    private String field;
    @Schema(description = "Value to match.", example = "diabetes mellitus")
    private String value;

    public FieldQuery() {}

    public FieldQuery(String field, String value) {
        this.field = field;
        this.value = value;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "FieldQuery{" +
                "field='" + field + '\'' +
                ", value='" + value + '\'' +
                '}';
    }
}
