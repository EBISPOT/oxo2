package uk.ac.ebi.spot.oxo.model.sssom;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Optional;

/**
 * Represents an xsd:double.
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public class Double {
    @JsonValue
    private final String doubleAsString;

    private final Optional<java.lang.Double> doubleRepresentation;

    public Double(String doubleAsString) {
        this.doubleAsString = doubleAsString;
        Optional<java.lang.Double> tempDouble;
        try {
            tempDouble = Optional.of(java.lang.Double.parseDouble(doubleAsString));
        } catch (Exception e) {
            tempDouble = Optional.empty();
        }
        this.doubleRepresentation = tempDouble;
    }

    public String getDouble() {
        return doubleAsString;
    }

    public Optional<java.lang.Double> getDoubleRepresentation() {
        return doubleRepresentation;
    }

    @Override
    public String toString() {
        return "Double{" +
                "doubleAsString='" + doubleAsString + '\'' +
                ", doubleRepresentation=" + doubleRepresentation +
                '}';
    }
}