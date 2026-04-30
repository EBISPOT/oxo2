package uk.ac.ebi.spot.oxo.backend.controller.api.dto.request;

public class FieldQuery {

    private String field;
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
