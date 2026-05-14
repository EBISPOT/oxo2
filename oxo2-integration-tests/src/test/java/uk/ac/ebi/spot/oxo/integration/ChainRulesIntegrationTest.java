package uk.ac.ebi.spot.oxo.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Full-pipeline integration test for SSSOM chain-rule fixtures.
 *
 * One pipeline run (loadData.nextflow) per JVM invocation; one dynamic test per rule.
 * See oxo2-integration-tests/CONTEXT.md for the env-var contract and operational
 * consequences (this run replaces the dev's local Solr state).
 */
public class ChainRulesIntegrationTest {

    private static List<RuleFixtures.Fixture> fixtures;

    @BeforeAll
    static void runPipeline() throws Exception {
        Env.requireAll();
        fixtures = RuleFixtures.discover();
        if (fixtures.isEmpty()) {
            throw new IllegalStateException("No rule fixtures discovered under " + RuleFixtures.fixturesDir());
        }
        Pipeline.runLoadDataNextflow();
    }

    @TestFactory
    Collection<DynamicNode> perRuleTests() {
        List<DynamicNode> nodes = new ArrayList<>();
        for (RuleFixtures.Fixture f : fixtures) {
            nodes.add(DynamicContainer.dynamicContainer(f.rule, ruleAssertions(f)));
        }
        return nodes;
    }

    private List<DynamicTest> ruleAssertions(RuleFixtures.Fixture f) {
        String base = f.baseName();
        List<DynamicTest> tests = new ArrayList<>();
        tests.add(DynamicTest.dynamicTest("inferredMappings.ttl", () -> compareTextLayer(
                ArtifactPaths.expectedInferredTtl(base),
                ArtifactPaths.actualInferredTtl(base),
                Canonicalisers::readTtl)));
        tests.add(DynamicTest.dynamicTest("inferenceChains.json", () -> compareTextLayer(
                ArtifactPaths.expectedChainJson(base),
                ArtifactPaths.actualChainJson(base),
                Canonicalisers::readJson)));
        tests.add(DynamicTest.dynamicTest("solr/mapping explained.json", () -> compareTextLayer(
                ArtifactPaths.expectedExplainedJson(base),
                ArtifactPaths.actualExplainedJson(base),
                Canonicalisers::readExplainedJson)));
        tests.add(DynamicTest.dynamicTest("solr/mappingSet.json", () -> compareTextLayer(
                ArtifactPaths.expectedMappingSetJson(base),
                ArtifactPaths.actualMappingSetJson(base),
                Canonicalisers::readJson)));
        tests.add(DynamicTest.dynamicTest("Solr numFound", () -> compareSolrNumFound(f.rule)));
        return tests;
    }

    @FunctionalInterface
    private interface Reader {
        String read(Path p) throws IOException;
    }

    private void compareTextLayer(Path expected, Path actual, Reader reader) throws IOException {
        boolean expectedExists = Files.isRegularFile(expected);
        boolean actualExists = Files.isRegularFile(actual);
        if (!expectedExists && !actualExists) {
            // Both absent — nothing for this layer in this rule. Pass silently.
            return;
        }
        if (!expectedExists) {
            throw new AssertionError(
                    "Expected file missing: " + expected +
                    "\nActual: " + actual +
                    "\nRun `mvn -pl oxo2-integration-tests -am exec:java@captureExpected` to baseline.");
        }
        if (!actualExists) {
            throw new AssertionError(
                    "Actual file missing (pipeline did not produce it): " + actual +
                    "\nExpected: " + expected);
        }
        String expectedText = trimTrailingNewline(Files.readString(expected, StandardCharsets.UTF_8));
        String actualText = trimTrailingNewline(reader.read(actual));
        assertEquals(expectedText, actualText,
                "Layer drift: " + actual + " differs from " + expected);
    }

    private static String trimTrailingNewline(String s) {
        while (s.endsWith("\n") || s.endsWith("\r")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private void compareSolrNumFound(String rule) throws IOException, InterruptedException {
        Path numFoundPath = ArtifactPaths.expectedNumFound();
        assertTrue(Files.isRegularFile(numFoundPath),
                "Expected numFound.json missing: " + numFoundPath +
                "\nRun `mvn -pl oxo2-integration-tests -am exec:java@captureExpected` to baseline.");
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(numFoundPath.toFile());
        JsonNode counts = root.get(rule);
        assertTrue(counts != null && counts.isObject(),
                "Expected numFound.json has no entry for rule " + rule);

        int actualMappingsAsserted = SolrCheck.numFound(SolrCheck.MAPPINGS_COLLECTION,
                SolrCheck.mappingSetIdForRule(rule));
        int actualMappingsInferred = SolrCheck.numFound(SolrCheck.MAPPINGS_COLLECTION,
                SolrCheck.inferredMappingSetIdForRule(rule));
        int actualMappingSetsAsserted = SolrCheck.numFound(SolrCheck.MAPPINGSETS_COLLECTION,
                SolrCheck.mappingSetIdForRule(rule));
        int actualMappingSetsInferred = SolrCheck.numFound(SolrCheck.MAPPINGSETS_COLLECTION,
                SolrCheck.inferredMappingSetIdForRule(rule));

        JsonNode mappings = counts.path(SolrCheck.MAPPINGS_COLLECTION);
        JsonNode mappingSets = counts.path(SolrCheck.MAPPINGSETS_COLLECTION);
        assertTrue(mappings.isObject() && mappingSets.isObject(),
                "Expected numFound.json entry for " + rule + " missing nested collection objects");
        assertEquals(mappings.get("asserted").asInt(), actualMappingsAsserted,
                "Solr " + SolrCheck.MAPPINGS_COLLECTION + " asserted-set numFound differs for rule " + rule);
        assertEquals(mappings.get("inferred").asInt(), actualMappingsInferred,
                "Solr " + SolrCheck.MAPPINGS_COLLECTION + " inferred-set numFound differs for rule " + rule);
        assertEquals(mappingSets.get("asserted").asInt(), actualMappingSetsAsserted,
                "Solr " + SolrCheck.MAPPINGSETS_COLLECTION + " asserted-set numFound differs for rule " + rule);
        assertEquals(mappingSets.get("inferred").asInt(), actualMappingSetsInferred,
                "Solr " + SolrCheck.MAPPINGSETS_COLLECTION + " inferred-set numFound differs for rule " + rule);
    }
}
