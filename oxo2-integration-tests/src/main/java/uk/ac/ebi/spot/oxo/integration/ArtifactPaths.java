package uk.ac.ebi.spot.oxo.integration;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves the assertion-target artifacts for a fixture (ADR-0016), both on the actual side
 * (rooted at $OXO2_DATA/inferences) and the expected side (rooted per fixture at
 * testcases_expected_output/minimal/&lt;fixture&gt;/).
 *
 * Because each fixture runs in isolation, its single SSSOM cross-set pass produces the one set of
 * inferences-* files asserted here. A layer with no output is simply absent on both sides and the
 * comparator passes it silently.
 */
public final class ArtifactPaths {

    private ArtifactPaths() {}

    /** Which canonicaliser a layer uses. */
    public enum Layer { TTL, JSON, EXPLAINED }

    public record LayerArtifact(String label, Path actual, Path expected, Layer layer) {}

    private static Path actualInferences() {
        return Env.oxo2Data().resolve("inferences");
    }

    private static Path expectedRoot(String fixtureName) {
        return RuleFixtures.expectedDir().resolve(fixtureName);
    }

    /** The full set of layer artifacts to compare/capture for a fixture. */
    public static List<LayerArtifact> artifactsFor(RuleFixtures.Fixture fixture) {
        Path actual = actualInferences();
        Path expected = expectedRoot(fixture.name);
        List<LayerArtifact> artifacts = new ArrayList<>();

        // SSSOM cross-set inference: a single inferences-* output for the isolated run (ADR-0016).
        artifacts.add(layer("crossSet/inferences.ttl",
                actual.resolve("crossSet").resolve("inferences.ttl"),
                expected.resolve("crossSet").resolve("inferences.ttl"), Layer.TTL));
        artifacts.add(layer("inferenceChainsCrossSet/inferences-chains.json",
                actual.resolve("inferenceChainsCrossSet").resolve("inferences-chains.json"),
                expected.resolve("inferenceChainsCrossSet").resolve("inferences-chains.json"), Layer.JSON));
        artifacts.add(layer("solr/mapping/inferences-explained.json",
                actual.resolve("solr").resolve("mapping").resolve("inferences-explained.json"),
                expected.resolve("solr").resolve("mapping").resolve("inferences-explained.json"), Layer.EXPLAINED));
        artifacts.add(layer("solr/mappingSet/inferences-mappingSet.json",
                actual.resolve("solr").resolve("mappingSet").resolve("inferences-mappingSet.json"),
                expected.resolve("solr").resolve("mappingSet").resolve("inferences-mappingSet.json"), Layer.JSON));

        return artifacts;
    }

    private static LayerArtifact layer(String label, Path actual, Path expected, Layer layer) {
        return new LayerArtifact(label, actual, expected, layer);
    }

    /** Per-fixture inference_type count golden. */
    public static Path expectedNumFound(String fixtureName) {
        return expectedRoot(fixtureName).resolve("numFound.json");
    }
}
