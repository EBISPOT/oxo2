package uk.ac.ebi.spot.oxo.inferences.nemo;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the two invariants of {@link ShardConclusions} that would otherwise fail silently in
 * production: a conclusion must never straddle two shards (its proof would be split, and the mapping
 * would ship unexplained), and a targets file must be one semicolon-separated line (a newline-separated
 * file makes nmo trace only its FIRST fact).
 */
class ShardConclusionsTest {

    private static final String EXACT = "http://www.w3.org/2004/02/skos/core#exactMatch";
    private static final String EQUIVALENT = "http://www.w3.org/2002/07/owl#equivalentClass";
    private static final String BROAD = "http://www.w3.org/2004/02/skos/core#broadMatch";
    private static final String CLOSE = "http://www.w3.org/2004/02/skos/core#closeMatch";
    private static final String NIL = "urn:uuid:00000000-0000-0000-0000-000000000000";

    @Test
    void parsesFourIriTermsAndRejectsAnythingElse() {
        assertArrayEquals(new String[] {"s", "p", "o", "urn:uuid:1"},
                ShardConclusions.parseQuad("<s> <p> <o> <urn:uuid:1> ."));
        assertNull(ShardConclusions.parseQuad("# a comment"));
        assertNull(ShardConclusions.parseQuad(""));
        assertNull(ShardConclusions.parseQuad("<s> <p> <o> ."), "a triple is not a quad");
    }

    /**
     * Two disjoint strong-predicate components must land in different shards when the entity cap
     * forces it, and every conclusion must be routed to the shard holding both its endpoints.
     * A {@code closeMatch} quad is weak — it never appears in a rule body — so it forms no component
     * of its own, but it IS carried into the shard corpus when both endpoints already live there.
     */
    @Test
    void routesEachComponentToItsOwnShard(@TempDir Path tempDir) throws Exception {
        Path corpus = tempDir.resolve("assertedCorpus.nq");
        Files.writeString(corpus, String.join("\n",
                quad("urn:uuid:a1", "ex:A", EXACT, "ex:B"),
                quad("urn:uuid:a2", "ex:B", EQUIVALENT, "ex:C"),
                quad("urn:uuid:a3", "ex:A", CLOSE, "ex:C"),      // weak: rides along, starts nothing
                quad("urn:uuid:b1", "ex:X", BROAD, "ex:Y"),
                "") , StandardCharsets.UTF_8);

        Path inferences = tempDir.resolve("inferences.ttl");
        Files.writeString(inferences, String.join("\n",
                triple("ex:A", EXACT, "ex:C"),
                triple("ex:C", EXACT, "ex:A"),
                triple("ex:A", EXACT, "ex:A"),   // self-mapping: must be skipped
                triple("ex:Y", "http://www.w3.org/2004/02/skos/core#narrowMatch", "ex:X"),
                "") , StandardCharsets.UTF_8);

        Path outputDir = tempDir.resolve("shards");
        Files.createDirectories(outputDir);
        // Cap of 1 entity forces the two components apart even though both are tiny.
        ShardConclusions.main(new String[] {"-c", corpus.toString(), "-i", inferences.toString(),
                "-o", outputDir.toString(), "-n", "1"});

        JsonNode manifest = new ObjectMapper().readTree(outputDir.resolve("shards-manifest.json").toFile());
        assertEquals(5, manifest.get("entities").asInt(), "A,B,C,X,Y");
        assertEquals(2, manifest.get("shards").asInt(), "{A,B,C} and {X,Y} are separate components");
        assertEquals(3, manifest.get("conclusions").asInt(), "the self-mapping must not be traced");

        // The 3-node identity component owns the two exactMatch conclusions; the other owns narrowMatch.
        List<String> allTargets = targetsOf(outputDir);
        assertEquals(2, allTargets.size());
        String identityShard = allTargets.stream().filter(t -> t.contains("exactMatch")).findFirst().orElseThrow();
        String hierarchyShard = allTargets.stream().filter(t -> t.contains("narrowMatch")).findFirst().orElseThrow();

        assertEquals(2, identityShard.split(";").length, "A->C and C->A, semicolon separated");
        assertTrue(identityShard.startsWith("mapping(<" + NIL + ">,<ex:"), "nil-UUID 4-arity atom");
        assertFalse(identityShard.contains("\n"), "targets must be ONE line or nmo traces only the first");
        assertFalse(identityShard.contains("ex:A>,<" + EXACT + ">,<ex:A>"), "self-mapping excluded");

        assertEquals(1, hierarchyShard.split(";").length);
        assertFalse(hierarchyShard.contains("\n"));
    }

    /** A weak-predicate quad joins no component, but is carried into a shard whose nodes it connects. */
    @Test
    void shardCorpusCarriesWeakQuadsBetweenSameShardNodes(@TempDir Path tempDir) throws Exception {
        Path corpus = tempDir.resolve("assertedCorpus.nq");
        Files.writeString(corpus, String.join("\n",
                quad("urn:uuid:a1", "ex:A", EXACT, "ex:B"),
                quad("urn:uuid:a2", "ex:A", CLOSE, "ex:B"),
                "") , StandardCharsets.UTF_8);
        Path inferences = tempDir.resolve("inferences.ttl");
        Files.writeString(inferences, triple("ex:B", EXACT, "ex:A") + "\n", StandardCharsets.UTF_8);

        Path outputDir = tempDir.resolve("shards");
        Files.createDirectories(outputDir);
        ShardConclusions.main(new String[] {"-c", corpus.toString(), "-i", inferences.toString(),
                "-o", outputDir.toString()});

        String shardCorpus = Files.readString(outputDir.resolve("shard00000.nq"), StandardCharsets.UTF_8);
        assertTrue(shardCorpus.contains(EXACT), "the strong edge is the shard's reason to exist");
        assertTrue(shardCorpus.contains(CLOSE), "weak quads between same-shard nodes ride along verbatim");
    }

    /** Re-running into a populated directory must replace, not append (the buffers flush in append mode). */
    @Test
    void rerunReplacesStaleShardFiles(@TempDir Path tempDir) throws Exception {
        Path corpus = tempDir.resolve("assertedCorpus.nq");
        Files.writeString(corpus, quad("urn:uuid:a1", "ex:A", EXACT, "ex:B") + "\n", StandardCharsets.UTF_8);
        Path inferences = tempDir.resolve("inferences.ttl");
        Files.writeString(inferences, triple("ex:B", EXACT, "ex:A") + "\n", StandardCharsets.UTF_8);
        Path outputDir = tempDir.resolve("shards");
        Files.createDirectories(outputDir);

        String[] arguments = {"-c", corpus.toString(), "-i", inferences.toString(), "-o", outputDir.toString()};
        ShardConclusions.main(arguments);
        String first = Files.readString(outputDir.resolve("shard00000-targets.txt"), StandardCharsets.UTF_8);
        ShardConclusions.main(arguments);
        String second = Files.readString(outputDir.resolve("shard00000-targets.txt"), StandardCharsets.UTF_8);

        assertEquals(first, second, "a second run must not append onto the first run's targets");
        assertEquals(1, second.split(";").length);
    }

    private static List<String> targetsOf(Path outputDir) throws IOException {
        try (var files = Files.newDirectoryStream(outputDir, "shard*-targets.txt")) {
            java.util.List<String> contents = new java.util.ArrayList<>();
            for (Path file : files) {
                contents.add(Files.readString(file, StandardCharsets.UTF_8));
            }
            return contents;
        }
    }

    /** `<s> <p> <o> <urn:uuid:mapping_id> .` — the shape json2nquads emits (ADR-0010). */
    private static String quad(String graph, String subject, String predicate, String object) {
        return "<" + subject + "> <" + predicate + "> <" + object + "> <" + graph + "> .";
    }

    private static String triple(String subject, String predicate, String object) {
        return "<" + subject + "> <" + predicate + "> <" + object + "> .";
    }
}
