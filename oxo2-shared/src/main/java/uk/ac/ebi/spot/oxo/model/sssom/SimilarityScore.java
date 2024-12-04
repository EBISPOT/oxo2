package uk.ac.ebi.spot.oxo.model.sssom;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Optional;

public class SimilarityScore {
    @JsonValue
    private final String scoreAsString;

    private final Optional<java.lang.Double> scoreRepresentation;


    public SimilarityScore(String score, String measure) {
        this.scoreAsString = score;
        Optional<java.lang.Double> tempDouble;
        try {
            tempDouble = Optional.of(java.lang.Double.parseDouble(score));

        } catch (Exception e) {
            tempDouble = Optional.empty();
        }
        this.scoreRepresentation = tempDouble;
    }

    @Override
    public String toString() {
        return "SimilarityScore{" +
                "scoreAsString='" + scoreAsString + '\'' +
                ", scoreRepresentation=" + scoreRepresentation +
                '}';
    }
}
