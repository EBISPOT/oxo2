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
 * Maven entry point for `exec:java@captureExpected` (ADR-0016). Runs loadData.nextflow
 * once per fixture in isolation, then writes the canonicalised actual output for that fixture to
 * testcases_expected_output/minimal/&lt;fixture&gt;/. Honours -Doxo2.it.rule=&lt;name&gt; to scope to one fixture.
 */
public final class CaptureExpected {

    public static void main(String[] args) throws Exception {
        Env.requireAll();
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

        // One isolated test Solr for the whole capture run (cleared before each fixture), stopped at
        // the end unless -Doxo2.it.keepSolr=true. Mirrors the Failsafe IT lifecycle.
        SolrLifecycle.startFresh();
        try {
            List<RuleFixtures.Fixture> fixtures = RuleFixtures.discover();
            for (RuleFixtures.Fixture fixture : fixtures) {
                System.out.println("=== Capturing fixture " + fixture.name + " ("
                        + fixture.setBaseNames() + ") ===");
                SolrLifecycle.clearCollections();
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
        } finally {
            if (Env.keepSolr()) {
                System.out.println("-D" + Env.KEEP_SOLR_PROPERTY + "=true: leaving test Solr running at "
                        + Env.solrHost());
            } else {
                SolrLifecycle.stop();
            }
        }
    }

    private static void copyCanonical(ArtifactPaths.LayerArtifact artifact) throws IOException {
        List<Path> sources = artifact.actual().stream().filter(Files::isRegularFile).toList();
        Path destination = artifact.expected();
        if (sources.isEmpty()) {
            // Layer not produced for this fixture — remove any stale expected so the comparator
            // would flag it if the pipeline later starts producing output here.
            if (Files.isRegularFile(destination)) {
                Files.delete(destination);
                System.out.println("  [del]  removed stale expected " + destination);
            }
            return;
        }
        Files.createDirectories(destination.getParent());
        // The explained-mapping layer arrives as one file per explanation bundle (ADR-0028); merge
        // them into the single golden. The canonicaliser sorts, so bundling never shows up here.
        String canonical = canonicaliseLayer(sources, artifact.layer());
        if (!canonical.endsWith("\n")) canonical = canonical + "\n";
        Files.writeString(destination, canonical, StandardCharsets.UTF_8);
        System.out.println("  [ok]   " + destination + " (from " + sources.size() + " file(s))");
    }

    private static String canonicaliseLayer(List<Path> sources, ArtifactPaths.Layer layer) throws IOException {
        return switch (layer) {
            case TTL -> Canonicalisers.canonicaliseTtl(readSingle(sources, layer));
            case JSON -> Canonicalisers.canonicaliseGenericJson(readSingle(sources, layer));
            case EXPLAINED -> Canonicalisers.readExplainedJson(sources);
        };
    }

    private static String readSingle(List<Path> sources, ArtifactPaths.Layer layer) throws IOException {
        if (sources.size() != 1) {
            throw new IllegalStateException(layer + " layer expects exactly one file, got: " + sources);
        }
        return Files.readString(sources.get(0), StandardCharsets.UTF_8);
    }
}
