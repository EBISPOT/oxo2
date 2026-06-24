package uk.ac.ebi.spot.oxo.inferences.nemo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uk.ac.ebi.spot.oxo.inferences.nemo.model.NemoInferences;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the streaming Pass-1 parse: {@link ExplainInferredMappings#indexChainsFile} must reconstruct
 * exactly what the old whole-file {@code readValue(file, NemoInferences.class)} did — every
 * inference into the on-disk store, every asserted leaf id collected, every final conclusion spilled.
 */
class ChainsFileIndexingTest {

    private static final String NIL = "urn:uuid:00000000-0000-0000-0000-000000000000";
    private static final String ASSERTED_ID = "urn:uuid:11111111-1111-1111-1111-111111111111";

    private static String mappingAtom(String id, String subject, String object) {
        return "mapping(<" + id + ">, <" + subject + ">, <skos:exactMatch>, <" + object + ">)";
    }

    @Test
    void indexesInferencesCollectsAssertedIdsAndSpillsFinalConclusions(@TempDir Path tempDir)
            throws IOException {
        String inferredConclusion = mappingAtom(NIL, "ex:A", "ex:C");
        String intermediate = mappingAtom(NIL, "ex:B", "ex:C");
        String assertedPremise = mappingAtom(ASSERTED_ID, "ex:A", "ex:B");

        // Field order deliberately mixed (finalConclusion before inferences) to prove the parser is
        // order-independent, and a null ruleName/rule on the asserted-backed leaf inference.
        String chainsJson = """
                {
                  "finalConclusion": ["%s"],
                  "inferences": [
                    {"rule":"r1","ruleName":"T1","conclusion":"%s","premises":["%s","%s"]},
                    {"rule":null,"ruleName":null,"conclusion":"%s","premises":["%s"]}
                  ]
                }
                """.formatted(inferredConclusion,
                inferredConclusion, assertedPremise, intermediate,
                intermediate, assertedPremise);

        Path chainsFile = tempDir.resolve("inferences-chains.json");
        Files.writeString(chainsFile, chainsJson, StandardCharsets.UTF_8);
        Path finalConclusionsFile = tempDir.resolve("final-conclusions.txt");

        Set<String> assertedIds = new HashSet<>();
        long finalCount;
        OnDiskChainStore store;
        try (OnDiskChainStore.Builder builder =
                     new OnDiskChainStore.Builder(tempDir.resolve("records.bin").toFile());
             BufferedWriter writer = Files.newBufferedWriter(finalConclusionsFile)) {
            finalCount = ExplainInferredMappings.indexChainsFile(
                    chainsFile.toString(), builder, assertedIds, writer);
            store = builder.build();
        }

        try (store) {
            assertEquals(1, finalCount);
            assertEquals(2, store.size());

            NemoInferences.NemoInference top = store.find(inferredConclusion).orElseThrow();
            assertEquals("T1", top.getRuleName());
            assertEquals(List.of(assertedPremise, intermediate), top.getPremises());

            NemoInferences.NemoInference leaf = store.find(intermediate).orElseThrow();
            // ruleName null round-trips; the asserted premise is preserved.
            assertEquals(null, leaf.getRuleName());
            assertEquals(List.of(assertedPremise), leaf.getPremises());

            // The asserted leaf's bare mapping_id is collected; the nil (inferred) id is not.
            assertTrue(assertedIds.contains("11111111-1111-1111-1111-111111111111"),
                    "asserted leaf id should be collected for prefetch");
            assertFalse(assertedIds.contains("00000000-0000-0000-0000-000000000000"),
                    "nil/inferred id must not be collected as an asserted leaf");
            assertEquals(1, assertedIds.size());

            // The single final conclusion was spilled verbatim, one per line.
            assertEquals(List.of(inferredConclusion),
                    Files.readAllLines(finalConclusionsFile, StandardCharsets.UTF_8));
        }
    }
}
