package uk.ac.ebi.spot.oxo.model.sssom;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * How a mapping (or mapping set) came to be — its origin/derivation category.
 *
 * <p>Replaces the former boolean {@code is_inferred} (see ADR-0011). Stored as the code string in the Solr
 * {@code inference_type} field on both {@code oxo2-mappings} and {@code oxo2-mappingsets}; the frontend maps
 * the codes to display labels ("Asserted" / "SSSOM inference").
 */
public enum InferenceType {

    /** Came directly from an input SSSOM file. */
    ASSERTED,

    /** Derived by SSSOM reasoning ({@code sssom.rls}), applied across all mapping sets (ADR-0016). */
    SSSOM_INFERENCE;

    @JsonValue
    public String getCode() {
        return name();
    }

    @JsonCreator
    public static InferenceType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (InferenceType inferenceType : values()) {
            if (inferenceType.name().equalsIgnoreCase(code)) {
                return inferenceType;
            }
        }
        throw new IllegalArgumentException("Unknown InferenceType: " + code);
    }
}
