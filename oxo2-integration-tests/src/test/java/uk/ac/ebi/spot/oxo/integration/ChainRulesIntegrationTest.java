package uk.ac.ebi.spot.oxo.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import uk.ac.ebi.spot.oxo.model.sssom.InferenceType;

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
 * Full-pipeline integration test for the two-phase reasoning fixtures (ADR-0009).
 *
 * Each fixture is run through loadData.nextflow in <b>isolation</b> — one pipeline pass over only
 * that fixture's set(s) — so a phase-2 (cross-set) rule's single {@code oxo2/inferences} output
 * belongs to exactly that fixture and can be asserted per-rule. Because each pass wipes
 * {@code $OXO2_DATA} and the Solr collections, the per-fixture run and its assertions are
 * interleaved: a fixture's golden-file + Solr assertions run before the next fixture's pass.
 * JUnit executes dynamic containers (and their children) in encounter order, which guarantees that
 * interleaving.
 *
 * See oxo2-integration-tests/CONTEXT.md for the env-var contract and operational consequences.
 */
public class ChainRulesIntegrationTest {

    private static List<RuleFixtures.Fixture> fixtures;

    @BeforeAll
    static void discoverFixtures() throws Exception {
        Env.requireAll();
        fixtures = RuleFixtures.discover();
        if (fixtures.isEmpty()) {
            throw new IllegalStateException("No fixtures discovered under " + RuleFixtures.rulesDir()
                    + " or " + RuleFixtures.crossSetDir());
        }
    }

    @TestFactory
    Collection<DynamicNode> perFixtureTests() {
        List<DynamicNode> nodes = new ArrayList<>();
        for (RuleFixtures.Fixture fixture : fixtures) {
            nodes.add(DynamicContainer.dynamicContainer(fixture.name, fixtureTests(fixture)));
        }
        return nodes;
    }

    private List<DynamicTest> fixtureTests(RuleFixtures.Fixture fixture) {
        List<DynamicTest> tests = new ArrayList<>();
        // First child: the isolated pipeline pass for this fixture. Runs before the assertions in
        // the same container, and before any later fixture's pass wipes the shared state.
        tests.add(DynamicTest.dynamicTest("pipeline", () ->
                Pipeline.runLoadDataNextflow(ConfigGenerator.generate(fixture))));
        for (ArtifactPaths.LayerArtifact artifact : ArtifactPaths.artifactsFor(fixture)) {
            tests.add(DynamicTest.dynamicTest(artifact.label(), () -> compareLayer(artifact)));
        }
        tests.add(DynamicTest.dynamicTest("Solr numFound", () -> compareNumFound(fixture)));
        return tests;
    }

    private void compareLayer(ArtifactPaths.LayerArtifact artifact) throws IOException {
        Path expected = artifact.expected();
        Path actual = artifact.actual();
        boolean expectedExists = Files.isRegularFile(expected);
        boolean actualExists = Files.isRegularFile(actual);
        if (!expectedExists && !actualExists) {
            // Layer not exercised by this fixture (e.g. a phase-2 rule produces no per-set output).
            return;
        }
        if (!expectedExists) {
            throw new AssertionError("Expected file missing: " + expected + "\nActual: " + actual +
                    "\nRun `mvn -pl oxo2-integration-tests exec:java@captureExpected` to baseline.");
        }
        if (!actualExists) {
            throw new AssertionError("Actual file missing (pipeline did not produce it): " + actual +
                    "\nExpected: " + expected);
        }
        String expectedText = trimTrailingNewline(Files.readString(expected, StandardCharsets.UTF_8));
        String actualText = trimTrailingNewline(readCanonical(actual, artifact.layer()));
        assertEquals(expectedText, actualText, "Layer drift: " + actual + " differs from " + expected);
    }

    static String readCanonical(Path actual, ArtifactPaths.Layer layer) throws IOException {
        return switch (layer) {
            case TTL -> Canonicalisers.readTtl(actual);
            case JSON -> Canonicalisers.readJson(actual);
            case EXPLAINED -> Canonicalisers.readExplainedJson(actual);
        };
    }

    private static String trimTrailingNewline(String text) {
        while (text.endsWith("\n") || text.endsWith("\r")) text = text.substring(0, text.length() - 1);
        return text;
    }

    private void compareNumFound(RuleFixtures.Fixture fixture) throws IOException, InterruptedException {
        Path numFoundPath = ArtifactPaths.expectedNumFound(fixture.name);
        assertTrue(Files.isRegularFile(numFoundPath),
                "Expected numFound.json missing: " + numFoundPath +
                "\nRun `mvn -pl oxo2-integration-tests exec:java@captureExpected` to baseline.");
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(numFoundPath.toFile());

        for (String collection : new String[]{SolrCheck.MAPPINGS_COLLECTION, SolrCheck.MAPPINGSETS_COLLECTION}) {
            JsonNode collectionCounts = root.path(collection);
            assertTrue(collectionCounts.isObject(),
                    "numFound.json for " + fixture.name + " has no object for collection " + collection);
            for (InferenceType type : InferenceType.values()) {
                String code = type.getCode();
                int expected = collectionCounts.path(code).asInt();
                int actual = SolrCheck.numFoundByInferenceType(collection, code);
                assertEquals(expected, actual,
                        "Solr " + collection + " " + code + " count differs for fixture " + fixture.name);
            }
        }
    }
}
