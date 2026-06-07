package uk.ac.ebi.spot.oxo.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import uk.ac.ebi.spot.oxo.model.sssom.InferenceType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Maven entry point for `exec:java@captureExpected` (ADR-0009 two-phase model). Runs loadData.nextflow
 * once per fixture in isolation, then writes the canonicalised actual output for that fixture to
 * testcases_expected_output/minimal/&lt;fixture&gt;/. Honours -Doxo2.it.rule=&lt;name&gt; to scope to one fixture.
 */
public final class CaptureExpected {

    public static void main(String[] args) throws Exception {
        Env.requireAll();
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

        List<RuleFixtures.Fixture> fixtures = RuleFixtures.discover();
        for (RuleFixtures.Fixture fixture : fixtures) {
            System.out.println("=== Capturing fixture " + fixture.name + " ("
                    + fixture.setBaseNames() + ") ===");
            Pipeline.runLoadDataNextflow(ConfigGenerator.generate(fixture));

            for (ArtifactPaths.LayerArtifact artifact : ArtifactPaths.artifactsFor(fixture)) {
                copyCanonical(artifact);
            }

            ObjectNode numFoundRoot = mapper.createObjectNode();
            for (String collection : new String[]{SolrCheck.MAPPINGS_COLLECTION, SolrCheck.MAPPINGSETS_COLLECTION}) {
                ObjectNode counts = mapper.createObjectNode();
                for (InferenceType type : InferenceType.values()) {
                    counts.put(type.getCode(), SolrCheck.numFoundByInferenceType(collection, type.getCode()));
                }
                numFoundRoot.set(collection, counts);
            }
            Path numFoundPath = ArtifactPaths.expectedNumFound(fixture.name);
            Files.createDirectories(numFoundPath.getParent());
            Files.writeString(numFoundPath, mapper.writeValueAsString(numFoundRoot) + "\n", StandardCharsets.UTF_8);
            System.out.println("  [ok]   " + numFoundPath);
        }
    }

    private static void copyCanonical(ArtifactPaths.LayerArtifact artifact) throws IOException {
        Path source = artifact.actual();
        Path destination = artifact.expected();
        if (!Files.isRegularFile(source)) {
            // Layer not produced for this fixture — remove any stale expected so the comparator
            // would flag it if the pipeline later starts producing output here.
            if (Files.isRegularFile(destination)) {
                Files.delete(destination);
                System.out.println("  [del]  removed stale expected " + destination);
            }
            return;
        }
        Files.createDirectories(destination.getParent());
        String content = Files.readString(source, StandardCharsets.UTF_8);
        String canonical = canonicaliseLayer(content, artifact.layer());
        if (!canonical.endsWith("\n")) canonical = canonical + "\n";
        Files.writeString(destination, canonical, StandardCharsets.UTF_8);
        System.out.println("  [ok]   " + destination);
    }

    private static String canonicaliseLayer(String content, ArtifactPaths.Layer layer) throws IOException {
        return switch (layer) {
            case TTL -> Canonicalisers.canonicaliseTtl(content);
            case JSON -> Canonicalisers.canonicaliseGenericJson(content);
            case EXPLAINED -> Canonicalisers.canonicaliseExplainedJson(content);
        };
    }
}
