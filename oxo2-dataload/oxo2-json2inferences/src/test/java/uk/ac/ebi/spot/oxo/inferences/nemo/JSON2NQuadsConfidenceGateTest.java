package uk.ac.ebi.spot.oxo.inferences.nemo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The confidence gate (ADR-0037) in {@link JSON2NQuads}. The gate keeps a mapping out of the N-Quad
 * inference corpus only when its SSSOM {@code confidence} is <b>present and strictly below</b> the
 * {@code --minConfidence} threshold. Everything else — a high confidence, no confidence at all, or an
 * unparseable confidence — passes, and every drop is recorded in the sidecar rather than removed
 * silently.
 *
 * <p>The four fixture mappings all use {@code skos:exactMatch} (an applicable inference predicate) with
 * valid absolute IRIs, so predicate-applicability and IRI-validity never mask the confidence decision.
 * Subjects are distinct so each mapping's fate can be read off the output by its subject IRI.
 */
class JSON2NQuadsConfidenceGateTest {

    private static final String EXACT_MATCH = "http://www.w3.org/2004/02/skos/core#exactMatch";
    private static final String OBJECT = "http://example.org/B";

    private static final String SUBJECT_HIGH = "http://example.org/A_HIGH";
    private static final String SUBJECT_LOW = "http://example.org/A_LOW";
    private static final String SUBJECT_NO_CONFIDENCE = "http://example.org/A_NONE";
    private static final String SUBJECT_UNPARSEABLE = "http://example.org/A_BAD";

    /** Four exactMatch mappings differing only in their confidence value (or its absence). */
    private static final String FIXTURE_JSON = """
            [
              { "subject_iri": "http://example.org/A_HIGH", "predicate_iri": "http://www.w3.org/2004/02/skos/core#exactMatch",
                "object_iri": "http://example.org/B", "mapping_id": "11111111-1111-1111-1111-111111111111", "confidence": 0.9 },
              { "subject_iri": "http://example.org/A_LOW", "predicate_iri": "http://www.w3.org/2004/02/skos/core#exactMatch",
                "object_iri": "http://example.org/B", "mapping_id": "22222222-2222-2222-2222-222222222222", "confidence": 0.3 },
              { "subject_iri": "http://example.org/A_NONE", "predicate_iri": "http://www.w3.org/2004/02/skos/core#exactMatch",
                "object_iri": "http://example.org/B", "mapping_id": "33333333-3333-3333-3333-333333333333" },
              { "subject_iri": "http://example.org/A_BAD", "predicate_iri": "http://www.w3.org/2004/02/skos/core#exactMatch",
                "object_iri": "http://example.org/B", "mapping_id": "44444444-4444-4444-4444-444444444444", "confidence": "n/a" }
            ]
            """;

    private Path writeFixture(Path directory) throws IOException {
        Path jsonFile = directory.resolve("gate-fixture.sssom.json");
        Files.writeString(jsonFile, FIXTURE_JSON, StandardCharsets.UTF_8);
        return jsonFile;
    }

    private static Path sidecarFor(Path outputFile) {
        return outputFile.resolveSibling("gate-fixture.sssom.dropped-low-confidence.tsv");
    }

    @Test
    void dropsOnlyThePresentBelowThresholdConfidence(@TempDir Path tempDir) throws IOException {
        Path jsonFile = writeFixture(tempDir);
        Path outputFile = tempDir.resolve("gate-fixture.sssom.nq");

        JSON2NQuads.generateNQuadsFromJSON(jsonFile, outputFile, 0.5);

        String nquads = Files.readString(outputFile, StandardCharsets.UTF_8);
        assertTrue(nquads.contains(SUBJECT_HIGH), "0.9 >= 0.5 must be emitted");
        assertTrue(nquads.contains(SUBJECT_NO_CONFIDENCE), "no confidence must always pass the gate");
        assertTrue(nquads.contains(SUBJECT_UNPARSEABLE), "unparseable confidence must pass the gate");
        assertFalse(nquads.contains(SUBJECT_LOW), "0.3 < 0.5 must be dropped from the corpus");
    }

    @Test
    void recordsEveryDropInTheSidecarRatherThanSilently(@TempDir Path tempDir) throws IOException {
        Path jsonFile = writeFixture(tempDir);
        Path outputFile = tempDir.resolve("gate-fixture.sssom.nq");

        JSON2NQuads.generateNQuadsFromJSON(jsonFile, outputFile, 0.5);

        Path sidecar = sidecarFor(outputFile);
        assertTrue(Files.exists(sidecar), "a drop must produce the sidecar report");
        String report = Files.readString(sidecar, StandardCharsets.UTF_8);
        assertTrue(report.contains("22222222-2222-2222-2222-222222222222"),
                "the dropped mapping_id must be listed");
        assertTrue(report.contains(SUBJECT_LOW) && report.contains(EXACT_MATCH) && report.contains(OBJECT),
                "the dropped edge's s/p/o must be listed");
        assertFalse(report.contains(SUBJECT_HIGH), "a kept mapping must not appear in the drop report");
    }

    @Test
    void disabledGateEmitsEveryEdgeAndNoSidecar(@TempDir Path tempDir) throws IOException {
        Path jsonFile = writeFixture(tempDir);
        Path outputFile = tempDir.resolve("gate-fixture.sssom.nq");

        JSON2NQuads.generateNQuadsFromJSON(jsonFile, outputFile, 0.0);

        String nquads = Files.readString(outputFile, StandardCharsets.UTF_8);
        assertTrue(nquads.contains(SUBJECT_HIGH) && nquads.contains(SUBJECT_LOW)
                        && nquads.contains(SUBJECT_NO_CONFIDENCE) && nquads.contains(SUBJECT_UNPARSEABLE),
                "a disabled gate (threshold 0) must emit every applicable edge");
        assertFalse(Files.exists(sidecarFor(outputFile)),
                "a disabled gate must not write a drop report");
    }

    @Test
    void missingConfidenceSurvivesEvenAStrictThreshold(@TempDir Path tempDir) throws IOException {
        Path jsonFile = writeFixture(tempDir);
        Path outputFile = tempDir.resolve("gate-fixture.sssom.nq");

        JSON2NQuads.generateNQuadsFromJSON(jsonFile, outputFile, 0.99);

        String nquads = Files.readString(outputFile, StandardCharsets.UTF_8);
        assertTrue(nquads.contains(SUBJECT_NO_CONFIDENCE),
                "a mapping with no confidence must never be dropped, whatever the threshold");
        assertFalse(nquads.contains(SUBJECT_HIGH), "0.9 < 0.99 must be dropped at this threshold");
    }
}
