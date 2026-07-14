package uk.ac.ebi.spot.oxo.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
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
 * Full-pipeline integration test for the SSSOM cross-set reasoning fixtures (ADR-0016).
 *
 * Each fixture is run through loadData.nextflow in <b>isolation</b> — one pipeline pass over only
 * that fixture's set(s) — so the single {@code oxo2/inferences} cross-set output belongs to exactly
 * that fixture and can be asserted per-rule. The {@link SolrLifecycle} harness starts one isolated
 * test Solr for the whole run ({@code @BeforeAll}) and stops it at the end ({@code @AfterAll}, unless
 * {@code -Doxo2.it.keepSolr=true}); each fixture's pipeline pass first clears the shared collections
 * with a {@code delete *:*}, so the per-fixture run and its assertions stay interleaved: a fixture's
 * golden-file + Solr assertions run before the next fixture clears + reloads. JUnit executes dynamic
 * containers (and their children) in encounter order, which guarantees that interleaving.
 *
 * <p>Run one fixture (start + stop Solr around just that fixture) while debugging with
 * {@code -Doxo2.it.rule=<name>}.
 *
 * See oxo2-integration-tests/CONTEXT.md for the env-var contract and operational consequences.
 */
public class ChainRulesIntegrationTest {

    private static List<RuleFixtures.Fixture> fixtures;

    @BeforeAll
    static void startTestSolr() throws Exception {
        Env.requireAll();
        fixtures = RuleFixtures.discover();
        if (fixtures.isEmpty()) {
            throw new IllegalStateException("No fixtures discovered under " + RuleFixtures.rulesDir()
                    + " or " + RuleFixtures.crossSetDir());
        }
        SolrLifecycle.startFresh();
    }

    @AfterAll
    static void stopTestSolr() throws Exception {
        if (Env.keepSolr()) {
            System.out.println("-D" + Env.KEEP_SOLR_PROPERTY + "=true: leaving test Solr running at "
                    + Env.solrHost());
            return;
        }
        SolrLifecycle.stop();
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
        // First child: clear the shared collections, then run the isolated pipeline pass for this
        // fixture. Runs before the assertions in the same container, and before any later fixture
        // clears + reloads the shared Solr.
        tests.add(DynamicTest.dynamicTest("pipeline", () -> {
            SolrLifecycle.clearCollections();
            Pipeline.runLoadDataNextflow(ConfigGenerator.generate(fixture));
        }));
        for (ArtifactPaths.LayerArtifact artifact : ArtifactPaths.artifactsFor(fixture)) {
            tests.add(DynamicTest.dynamicTest(artifact.label(), () -> compareLayer(artifact)));
        }
        tests.add(DynamicTest.dynamicTest("explanation well-founded",
                () -> assertExplanationsWellFounded(fixture)));
        tests.add(DynamicTest.dynamicTest("Solr numFound", () -> compareNumFound(fixture)));
        return tests;
    }

    /**
     * Every explanation must be well-founded: no premise may restate an ancestor conclusion's
     * subject/predicate/object (ADR-0033). Asserted structurally rather than left to the golden-file
     * diff, because a cyclic proof is wrong <em>however</em> it is baselined — the RCE2_2 golden
     * carried one, since a text diff cannot tell a correct proof from a circular one.
     */
    private void assertExplanationsWellFounded(RuleFixtures.Fixture fixture) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        for (ArtifactPaths.LayerArtifact artifact : ArtifactPaths.artifactsFor(fixture)) {
            if (artifact.layer() != ArtifactPaths.Layer.EXPLAINED) {
                continue;
            }
            for (Path bundle : artifact.actual()) {
                if (!Files.isRegularFile(bundle)) {
                    continue;
                }
                for (JsonNode doc : mapper.readTree(bundle.toFile())) {
                    JsonNode explanation = doc.get("explanation");
                    if (explanation == null || explanation.isNull()) {
                        continue;
                    }
                    List<String> cyclicPath = findCyclicPath(explanation, new ArrayList<>());
                    if (cyclicPath != null) {
                        throw new AssertionError("Cyclic explanation for " + spo(doc) + " in "
                                + bundle.getFileName() + " — a proof step restates an ancestor "
                                + "conclusion (ADR-0033):\n  " + String.join("\n  ", cyclicPath));
                    }
                }
            }
        }
    }

    /**
     * The root-to-offender path if any node below {@code node} restates an ancestor's S-P-O, else
     * null. Path-scoped: a fact reused across sibling branches is a legitimate diamond; only ancestor
     * reuse is a cycle.
     */
    private static List<String> findCyclicPath(JsonNode node, List<String> ancestors) {
        String spo = spo(node);
        if (ancestors.contains(spo)) {
            List<String> cyclicPath = new ArrayList<>(ancestors);
            cyclicPath.add(spo + "   <-- restates an ancestor");
            return cyclicPath;
        }
        JsonNode applications = node.get("chain_rule_applications");
        JsonNode premises = applications == null ? null : applications.get("premises");
        if (premises == null || premises.isNull()) {
            return null;
        }
        ancestors.add(spo);
        try {
            for (JsonNode premise : premises) {
                List<String> cyclicPath = findCyclicPath(premise, ancestors);
                if (cyclicPath != null) {
                    return cyclicPath;
                }
            }
        } finally {
            ancestors.remove(ancestors.size() - 1);
        }
        return null;
    }

    /** A node's subject/predicate/object identity, as displayed to a user. */
    private static String spo(JsonNode node) {
        return text(node, "subject_iri") + " " + text(node, "predicate_iri") + " "
                + text(node, "object_iri");
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "?" : value.asText();
    }

    private void compareLayer(ArtifactPaths.LayerArtifact artifact) throws IOException {
        Path expected = artifact.expected();
        // The explained-mapping layer is one file per explanation bundle (ADR-0028); the others are
        // a single file. Either way, only the files the pipeline actually produced participate.
        List<Path> actual = artifact.actual().stream().filter(Files::isRegularFile).toList();
        boolean expectedExists = Files.isRegularFile(expected);
        boolean actualExists = !actual.isEmpty();
        if (!expectedExists && !actualExists) {
            // Layer not exercised by this fixture (absent on both sides).
            return;
        }
        if (!expectedExists) {
            throw new AssertionError("Expected file missing: " + expected + "\nActual: " + actual +
                    "\nRun `mvn -pl oxo2-integration-tests exec:java@captureExpected` to baseline.");
        }
        if (!actualExists) {
            throw new AssertionError("Actual file(s) missing (pipeline did not produce " +
                    artifact.label() + "): " + artifact.actual() + "\nExpected: " + expected);
        }
        String expectedText = trimTrailingNewline(Files.readString(expected, StandardCharsets.UTF_8));
        String actualText = trimTrailingNewline(readCanonical(actual, artifact.layer()));
        assertEquals(expectedText, actualText, "Layer drift: " + actual + " differs from " + expected);
    }

    static String readCanonical(List<Path> actual, ArtifactPaths.Layer layer) throws IOException {
        return switch (layer) {
            case TTL -> Canonicalisers.readTtl(single(actual, layer));
            case JSON -> Canonicalisers.readJson(single(actual, layer));
            case EXPLAINED -> Canonicalisers.readExplainedJson(actual);
        };
    }

    private static Path single(List<Path> paths, ArtifactPaths.Layer layer) {
        if (paths.size() != 1) {
            throw new IllegalStateException(layer + " layer expects exactly one file, got: " + paths);
        }
        return paths.get(0);
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
