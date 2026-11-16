package uk.ac.ebi.spot.oxo.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SupportedMappingsLoaderTest {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    @TempDir Path temporary;

    private ObjectNode doc(String id, long group, String source) {
        ObjectNode doc = MAPPER.createObjectNode();
        doc.put("mapping_id", id);
        doc.put("spo_key", new UUID(group, 0).toString());
        doc.put("inference_type", "ASSERTED");
        if (source != null) doc.put("mapping_set_id", source);
        doc.put("mapping_justification", "semapv:ManualMappingCuration");
        doc.put("confidence", 0);
        doc.put("subject_label", "A quoted \"label\" with\na newline");
        doc.putArray("unknown_extension").add("retained").add("verbatim");
        return doc;
    }

    @Test void countsAcrossFilesBeforeFirstWriteAndPreservesEveryOtherField() throws Exception {
        Path input = Files.createDirectory(temporary.resolve("mapping"));
        var a = doc("a", 1, "source\tA\nwith special characters");
        var b = doc("b", 1, "source\tA\nwith special characters");
        b.put("mapping_justification", "semapv:LexicalMatching");
        var c = doc("c", 1, "source B");
        var d = doc("d", 2, "source B"); // another predicate/modifier/literal group is separate
        var e = doc("e", 3, null);
        var f = doc("f", 3, " ");
        a.put("asserted_source_count", 99); // regenerate, never preserve stale statistics
        MAPPER.writeValue(input.resolve("a.json").toFile(), List.of(a, b, d, e, f));
        MAPPER.writeValue(input.resolve("z.json").toFile(), List.of(c));
        byte[] original = Files.readAllBytes(input.resolve("a.json"));
        List<JsonNode> loaded = new ArrayList<>();
        SupportedMappingsLoader.load(input, file -> {
            JsonNode array = MAPPER.readTree(file.toFile());
            if (loaded.isEmpty()) assertEquals(2, array.get(0).path("asserted_source_count").asInt());
            array.forEach(loaded::add);
        });
        assertEquals(List.of(2, 2, 1, 0, 0, 2), loaded.stream().map(n -> n.path("asserted_source_count").asInt()).toList());
        var originals = List.of(a, b, d, e, f, c);
        for (int i = 0; i < originals.size(); i++) {
            ObjectNode expected = originals.get(i).deepCopy();
            expected.put("asserted_source_count", loaded.get(i).path("asserted_source_count").asLong());
            assertEquals(MAPPER.readTree(expected.toString()), loaded.get(i));
        }
        assertArrayEquals(original, Files.readAllBytes(input.resolve("a.json")));
        assertNoScratch();
    }

    @Test void rerunRecomputesCountsAndFailureDoesNotModifyInputs() throws Exception {
        Path input = Files.createDirectory(temporary.resolve("mapping"));
        MAPPER.writeValue(input.resolve("a.json").toFile(), List.of(doc("a", 1, "one")));
        MAPPER.writeValue(input.resolve("b.json").toFile(), List.of(doc("b", 1, "two")));
        assertThrows(IOException.class, () -> SupportedMappingsLoader.load(input, file -> { throw new IOException("Solr unavailable"); }));
        assertNoScratch();
        Files.delete(input.resolve("b.json"));
        SupportedMappingsLoader.load(input, file -> assertEquals(1, MAPPER.readTree(file.toFile()).get(0).path("asserted_source_count").asInt()));
        assertFalse(MAPPER.readTree(input.resolve("a.json").toFile()).get(0).has("asserted_source_count"));
        assertNoScratch();
    }

    @Test void validatesAllInputsBeforeSendingAnythingToSolr() throws Exception {
        Path input = Files.createDirectory(temporary.resolve("mapping"));
        MAPPER.writeValue(input.resolve("a.json").toFile(), List.of(doc("a", 1, "one")));
        var bad = doc("bad", 1, "inferences");
        bad.put("inference_type", "SSSOM_INFERENCE");
        MAPPER.writeValue(input.resolve("z.json").toFile(), List.of(bad));
        assertThrows(IOException.class, () -> SupportedMappingsLoader.load(input, file -> fail("must not index mixed input")));
        Files.writeString(input.resolve("z.json"), "[{\"spo_key\":\"invalid\"}]");
        assertThrows(IOException.class, () -> SupportedMappingsLoader.load(input, file -> fail("must not index invalid input")));
        Files.writeString(input.resolve("z.json"), "[{}");
        assertThrows(Exception.class, () -> SupportedMappingsLoader.load(input, file -> fail("must not index malformed input")));
        assertNoScratch();
    }

    @Test void emptyInputAndEmptySourceIndexAreValid() throws Exception {
        Path input = Files.createDirectory(temporary.resolve("mapping"));
        SupportedMappingsLoader.load(input, file -> fail("nothing to index"));
        MAPPER.writeValue(input.resolve("a.json").toFile(), List.of(doc("a", 1, null)));
        SupportedMappingsLoader.load(input, file -> assertEquals(0, MAPPER.readTree(file.toFile()).get(0).path("asserted_source_count").asInt()));
        assertNoScratch();
    }

    @Test void diskLookupCrossesSegmentsAndUsesUnsignedUuidOrder() throws Exception {
        Path counts = temporary.resolve("counts.bin");
        try (var out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(counts)))) {
            for (long i = 0; i <= 1_000_000; i++) {
                out.writeLong(i); out.writeLong(0); out.writeLong(i + 1);
            }
            out.writeLong(Long.MIN_VALUE); out.writeLong(0); out.writeLong(7);
            out.writeLong(-1); out.writeLong(-1); out.writeLong(8);
        }
        try (var lookup = new SupportedMappingsLoader.DiskCounts(counts)) {
            assertEquals(1, lookup.get(new UUID(0, 0)));
            assertEquals(1_000_001, lookup.get(new UUID(1_000_000, 0)));
            assertEquals(7, lookup.get(new UUID(Long.MIN_VALUE, 0)));
            assertEquals(8, lookup.get(new UUID(-1, -1)));
            assertEquals(0, lookup.get(new UUID(2, 1)));
        }
    }

    private void assertNoScratch() throws IOException {
        try (var files = Files.list(temporary)) {
            assertFalse(files.anyMatch(p -> p.getFileName().toString().startsWith(".mapping-support-")));
        }
    }
}
