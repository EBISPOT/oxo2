package uk.ac.ebi.spot.oxo.integration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/**
 * Resolves the assertion-target artifacts for a fixture (ADR-0016), both on the actual side
 * (rooted at $OXO2_DATA/inferences) and the expected side (rooted per fixture at
 * testcases_expected_output/minimal/&lt;fixture&gt;/).
 *
 * Because each fixture runs in isolation, its single SSSOM cross-set pass produces the one set of
 * inferences-* files asserted here. A layer with no output is simply absent on both sides and the
 * comparator passes it silently.
 *
 * The explained-mapping layer is a SET of files, one per explanation bundle (ADR-0028), so its
 * actual side is a glob. The golden stays a single file: the comparator merges the bundles and the
 * canonicaliser sorts, so the comparison is independent of how shards were bundled.
 */
public final class ArtifactPaths {

    private ArtifactPaths() {}

    /** Which canonicaliser a layer uses. */
    public enum Layer { TTL, JSON, EXPLAINED, ENTITIES }

    /**
     * {@code actual} may resolve to several files (one per explanation bundle); {@code expected} is
     * always one.
     *
     * <p>Resolution is deferred: {@code artifactsFor} is called while the dynamic tests are being
     * *built*, which is before the fixture's pipeline pass has run, so an eagerly-globbed directory
     * would always come back empty.
     */
    public record LayerArtifact(String label, Supplier<List<Path>> actualSupplier, Path expected,
                                Layer layer) {
        public List<Path> actual() {
            return actualSupplier.get();
        }
    }

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

        // SSSOM cross-set inference: a single inferences-* output for the isolated run (ADR-0016),
        // now carrying explanation chains again, built per bundle of explanation shards (ADR-0028).
        artifacts.add(layer("crossSet/inferences.ttl",
                actual.resolve("crossSet").resolve("inferences.ttl"),
                expected.resolve("crossSet").resolve("inferences.ttl"), Layer.TTL));
        artifacts.add(new LayerArtifact("solr/mapping/inferences-explained-*.json",
                () -> explainedMappingFiles(actual),
                expected.resolve("solr").resolve("mapping").resolve("inferences-explained.json"),
                Layer.EXPLAINED));
        artifacts.add(layer("solr/mappingSet/inferences-mappingSet.json",
                actual.resolve("solr").resolve("mappingSet").resolve("inferences-mappingSet.json"),
                expected.resolve("solr").resolve("mappingSet").resolve("inferences-mappingSet.json"), Layer.JSON));

        // The per-entity typeahead read model (ADR-0034). Rooted at $OXO2_DATA/entities, NOT under
        // inferences/: it is folded from the Solr index after index-inferred, not produced by the
        // reasoner. Pinning the documents — not just the count — is what makes this layer worth
        // having: it catches a wrong label/IRI pick, a miscounted degree, and an entity that appears
        // only on the object side of an inferred mapping being dropped.
        artifacts.add(new LayerArtifact("entities/*.json",
                () -> globSorted(Env.oxo2Data().resolve("entities"), "*.json"),
                expected.resolve("entities").resolve("entities.json"), Layer.ENTITIES));

        return artifacts;
    }

    /**
     * Every bundle's inferred-mapping JSON, in name order. Sorted so a merge is deterministic even
     * though the canonicaliser would sort the union anyway.
     */
    private static List<Path> explainedMappingFiles(Path actualInferences) {
        return globSorted(actualInferences.resolve("solr").resolve("mapping"),
                "inferences-explained-*.json");
    }

    /**
     * The files matching {@code glob} in {@code directory}, in name order — empty if the directory
     * does not exist, which is how a layer a fixture never exercises stays absent on the actual side.
     */
    private static List<Path> globSorted(Path directory, String glob) {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, glob)) {
            stream.forEach(files::add);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed listing " + glob + " in " + directory, e);
        }
        files.sort(Comparator.comparing(Path::getFileName));
        return files;
    }

    private static LayerArtifact layer(String label, Path actual, Path expected, Layer layer) {
        return new LayerArtifact(label, () -> List.of(actual), expected, layer);
    }

    /** Per-fixture inference_type count golden. */
    public static Path expectedNumFound(String fixtureName) {
        return expectedRoot(fixtureName).resolve("numFound.json");
    }
}
