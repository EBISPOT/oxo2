package uk.ac.ebi.spot.oxo.model.sssom;

public class SimilarityScore {
    private final Double score;
    private final String measure;

    public SimilarityScore(Double score, String measure) {
        this.score = score;
        this.measure = measure;
    }
}
