package uk.ac.ebi.spot.oxo.sssom2json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import uk.ac.ebi.spot.oxo.model.sssom.MappingSetCategory;
import uk.ac.ebi.spot.oxo.sssom2json.parser.TSV2JSON;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Data-release-date stamping in the SSSOM-to-JSON stage (ADR-0043). The orchestrator resolves one UTC
 * instant per run and {@link TSV2JSON#processFile} writes it verbatim onto the mapping-set document, so
 * the newest {@code data_release_date} in the {@code oxo2-mappingsets} collection identifies the corpus
 * currently loaded.
 *
 * <p>The two cases that matter are the round trip (the string reaches the JSON unaltered, so Solr's date
 * field parses exactly what the orchestrator minted) and the absent case (no stamp supplied writes no
 * field at all, rather than an empty string Solr could not parse).
 */
class TSV2JSONReleaseDateTest {

    private static final String TSV = String.join("\n",
            "# mapping_set_id: https://example.org/mondo.sssom.tsv",
            "# curie_map:",
            "#   MONDO: http://purl.obolibrary.org/obo/MONDO_",
            "#   efo: http://www.ebi.ac.uk/efo/EFO_",
            "#   skos: http://www.w3.org/2004/02/skos/core#",
            "#   semapv: https://w3id.org/semapv/vocab/",
            "subject_id\tpredicate_id\tobject_id\tmapping_justification\tsubject_label\tobject_label",
            "MONDO:0000001\tskos:exactMatch\tefo:2000000\tsemapv:UnspecifiedMatching\tDisease\tLive EFO",
            "");

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Run the stage over one TSV with the given release date and return the mapping-set document. */
    private JsonNode mappingSetFor(Path tempDir, String releaseDate) throws IOException {
        Path tsvFile = Files.createDirectory(tempDir.resolve("in")).resolve("mondo.sssom.tsv");
        Files.writeString(tsvFile, TSV, StandardCharsets.UTF_8);

        Path mappingSetDir = Files.createDirectory(tempDir.resolve("mappingSet"));
        Path mappingDir = Files.createDirectory(tempDir.resolve("mapping"));
        TSV2JSON.processFile(tsvFile.toFile(), mappingSetDir.toString(), mappingDir.toString(),
                MappingSetCategory.ONTOLOGY, Set.of(), false, releaseDate);

        try (var files = Files.list(mappingSetDir)) {
            Path only = files.filter(path -> path.toString().endsWith(".json")).findFirst()
                    .orElseThrow(() -> new AssertionError("no mapping-set JSON written"));
            return objectMapper.readTree(only.toFile()).get(0);
        }
    }

    @Test
    void stampsTheRunLevelReleaseDateVerbatimOnTheMappingSet(@TempDir Path tempDir) throws IOException {
        JsonNode mappingSet = mappingSetFor(tempDir, "2026-07-30T09:15:00Z");

        // Verbatim matters: any reformatting here (a Date round trip, a locale-dependent format) is what
        // would reach Solr, and Solr's date field accepts only the ISO-8601 UTC form.
        assertEquals("2026-07-30T09:15:00Z", mappingSet.path("data_release_date").asText(),
                "the orchestrator's instant must reach the mapping-set JSON unaltered");
    }

    @Test
    void omitsTheFieldWhenNoReleaseDateWasSupplied(@TempDir Path tempDir) throws IOException {
        JsonNode mappingSet = mappingSetFor(tempDir, null);

        assertFalse(mappingSet.has("data_release_date"),
                "no supplied release date must omit the field, not write an empty unparseable value");
    }

    @Test
    void omitsTheFieldWhenTheReleaseDateIsBlank(@TempDir Path tempDir) throws IOException {
        // sssom2json.nf omits the flag on an empty value, but a blank reaching the builder must not
        // produce `"data_release_date": ""` — Solr rejects that at index time for a date field.
        JsonNode mappingSet = mappingSetFor(tempDir, "   ");

        assertFalse(mappingSet.has("data_release_date"),
                "a blank release date must be treated as absent");
    }
}
