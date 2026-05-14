package uk.ac.ebi.spot.oxo.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.TreeMap;

/**
 * Maven entry point for `exec:java@captureExpected`. Runs loadData.nextflow, then writes the
 * canonicalised actual output to testcases_expected_output/minimal/rules/ for each fixture.
 * Honours -Doxo2.it.rule=<RULE> to scope to a single fixture. Always writes all 4 layers.
 */
public final class CaptureExpected {

    public static void main(String[] args) throws Exception {
        Env.requireAll();
        Pipeline.runLoadDataNextflow();

        List<RuleFixtures.Fixture> fixtures = RuleFixtures.discover();
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

        // numFound.json: merge with existing so partial captures don't wipe other rules' counts.
        Path numFoundPath = ArtifactPaths.expectedNumFound();
        ObjectNode numFoundRoot;
        if (Files.isRegularFile(numFoundPath)) {
            numFoundRoot = (ObjectNode) mapper.readTree(numFoundPath.toFile());
        } else {
            numFoundRoot = mapper.createObjectNode();
        }

        for (RuleFixtures.Fixture f : fixtures) {
            String base = f.baseName();
            System.out.println("Capturing " + f.rule + " (" + base + ")");

            copyCanonical(ArtifactPaths.actualInferredTtl(base),
                          ArtifactPaths.expectedInferredTtl(base),
                          Canonicalisers::canonicaliseTtl);
            copyCanonical(ArtifactPaths.actualChainJson(base),
                          ArtifactPaths.expectedChainJson(base),
                          Canonicalisers::canonicaliseGenericJson);
            copyCanonical(ArtifactPaths.actualExplainedJson(base),
                          ArtifactPaths.expectedExplainedJson(base),
                          Canonicalisers::canonicaliseExplainedJson);
            copyCanonical(ArtifactPaths.actualMappingSetJson(base),
                          ArtifactPaths.expectedMappingSetJson(base),
                          Canonicalisers::canonicaliseGenericJson);

            // Both asserted and inferred sets live in the same two Solr collections,
            // distinguished by mapping_set_id. Nested keys here keep that fact obvious
            // rather than implying "-inferred" suffixed collections that don't exist.
            int mappingsAsserted = SolrCheck.numFound(SolrCheck.MAPPINGS_COLLECTION,
                    SolrCheck.mappingSetIdForRule(f.rule));
            int mappingsInferred = SolrCheck.numFound(SolrCheck.MAPPINGS_COLLECTION,
                    SolrCheck.inferredMappingSetIdForRule(f.rule));
            int mappingSetsAsserted = SolrCheck.numFound(SolrCheck.MAPPINGSETS_COLLECTION,
                    SolrCheck.mappingSetIdForRule(f.rule));
            int mappingSetsInferred = SolrCheck.numFound(SolrCheck.MAPPINGSETS_COLLECTION,
                    SolrCheck.inferredMappingSetIdForRule(f.rule));

            ObjectNode mappings = mapper.createObjectNode();
            mappings.put("asserted", mappingsAsserted);
            mappings.put("inferred", mappingsInferred);
            ObjectNode mappingSets = mapper.createObjectNode();
            mappingSets.put("asserted", mappingSetsAsserted);
            mappingSets.put("inferred", mappingSetsInferred);
            ObjectNode counts = mapper.createObjectNode();
            counts.set(SolrCheck.MAPPINGS_COLLECTION, mappings);
            counts.set(SolrCheck.MAPPINGSETS_COLLECTION, mappingSets);
            numFoundRoot.set(f.rule, counts);
        }

        // Sort keys for deterministic on-disk shape.
        ObjectNode sortedNumFound = mapper.createObjectNode();
        TreeMap<String, com.fasterxml.jackson.databind.JsonNode> sorted = new TreeMap<>();
        numFoundRoot.fields().forEachRemaining(e -> sorted.put(e.getKey(), e.getValue()));
        sorted.forEach(sortedNumFound::set);

        Files.createDirectories(numFoundPath.getParent());
        Files.writeString(numFoundPath, mapper.writeValueAsString(sortedNumFound) + "\n",
                          StandardCharsets.UTF_8);
        System.out.println("Wrote numFound: " + numFoundPath);
    }

    @FunctionalInterface
    private interface Canonicaliser {
        String canonicalise(String content) throws IOException;
    }

    private static void copyCanonical(Path src, Path dst, Canonicaliser fn) throws IOException {
        if (!Files.isRegularFile(src)) {
            System.out.println("  [skip] no actual file at " + src);
            // Still write an empty expected file so the comparator can flag a real regression
            // if Nemo later starts producing output for this layer. Or, if previously empty,
            // we leave the dst alone — but for simplicity always remove a stale expected file.
            if (Files.isRegularFile(dst)) {
                Files.delete(dst);
                System.out.println("  [del]  removed stale expected " + dst);
            }
            return;
        }
        Files.createDirectories(dst.getParent());
        String content = Files.readString(src, StandardCharsets.UTF_8);
        String canon = fn.canonicalise(content);
        if (!canon.endsWith("\n")) canon = canon + "\n";
        Files.writeString(dst, canon, StandardCharsets.UTF_8);
        System.out.println("  [ok]   " + dst);
    }
}
