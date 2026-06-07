package uk.ac.ebi.spot.oxo.integration;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves the assertion-target artifacts for a fixture (ADR-0009 two-phase model), both on the
 * actual side (rooted at $OXO2_DATA/inferences) and the expected side (rooted per fixture at
 * testcases_expected_output/minimal/&lt;fixture&gt;/).
 *
 * Because each fixture runs in isolation, the same per-fixture expected subtree holds both the
 * phase-1 per-set output (one set of files per source set) and the phase-2 cross-set output (the
 * single inferences-* files for the whole run). A layer with no output is simply absent on both
 * sides and the comparator passes it silently.
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

        // Phase 1 (per-set OWL): one set of files per source set the fixture loads.
        for (String set : fixture.setBaseNames()) {
            artifacts.add(layer("inferredMappings/" + set + ".ttl",
                    actual.resolve("inferredMappings").resolve(set + ".ttl"),
                    expected.resolve("inferredMappings").resolve(set + ".ttl"), Layer.TTL));
            artifacts.add(layer("inferenceChains/" + set + "-chains.json",
                    actual.resolve("inferenceChains").resolve(set + "-chains.json"),
                    expected.resolve("inferenceChains").resolve(set + "-chains.json"), Layer.JSON));
            artifacts.add(layer("solr/mapping/" + set + "-explained.json",
                    actual.resolve("solr").resolve("mapping").resolve(set + "-explained.json"),
                    expected.resolve("solr").resolve("mapping").resolve(set + "-explained.json"), Layer.EXPLAINED));
            artifacts.add(layer("solr/mappingSet/" + set + "-mappingSet.json",
                    actual.resolve("solr").resolve("mappingSet").resolve(set + "-mappingSet.json"),
                    expected.resolve("solr").resolve("mappingSet").resolve(set + "-mappingSet.json"), Layer.JSON));
        }

        // Phase 2 (cross-set SSSOM): a single inferences-* output for the whole isolated run.
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
