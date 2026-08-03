package uk.ac.ebi.spot.oxo.sssom2json;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Obsolete-terms stamping in the SSSOM-to-JSON stage (ADR-0041). Pass 1 ({@link
 * TSV2JSON#extractSubjectIris}) yields the expanded subject IRIs of an obsolete registry; Pass 2
 * ({@link TSV2JSON#processFile}) stamps {@code subject_obsolete}/{@code object_obsolete} on every
 * mapping against that set. The two passes share one IRI expansion, so a term obsolete on the subject
 * side of its own set is recognised on the object side of another set — the case a single file cannot
 * see on its own.
 */
class TSV2JSONObsoleteTest {

    private static final String OBSOLETE_TSV = String.join("\n",
            "# mapping_set_id: https://example.org/efo.obsolete.sssom.tsv",
            "# curie_map:",
            "#   efo: http://www.ebi.ac.uk/efo/EFO_",
            "#   ncit: http://purl.obolibrary.org/obo/NCIT_",
            "#   skos: http://www.w3.org/2004/02/skos/core#",
            "#   semapv: https://w3id.org/semapv/vocab/",
            "subject_id\tpredicate_id\tobject_id\tmapping_justification\tsubject_label\tobject_label",
            "efo:1000466\tskos:exactMatch\tncit:C3316\tsemapv:UnspecifiedMatching\tObsolete term\tNCIt term",
            "");

    // A live set whose first row maps to the obsolete term above (object-side), and whose second row
    // maps to a term that is not obsolete anywhere.
    private static final String LIVE_TSV = String.join("\n",
            "# mapping_set_id: https://example.org/mondo.sssom.tsv",
            "# curie_map:",
            "#   MONDO: http://purl.obolibrary.org/obo/MONDO_",
            "#   efo: http://www.ebi.ac.uk/efo/EFO_",
            "#   skos: http://www.w3.org/2004/02/skos/core#",
            "#   semapv: https://w3id.org/semapv/vocab/",
            "subject_id\tpredicate_id\tobject_id\tmapping_justification\tsubject_label\tobject_label",
            "MONDO:0000001\tskos:exactMatch\tefo:1000466\tsemapv:UnspecifiedMatching\tDisease\tObsolete EFO",
            "MONDO:0000002\tskos:exactMatch\tefo:2000000\tsemapv:UnspecifiedMatching\tDisease two\tLive EFO",
            "");

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Path writeTsv(Path directory, String name, String content) throws IOException {
        Path file = directory.resolve(name);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private JsonNode readSingleJson(Path directory) throws IOException {
        try (var files = Files.list(directory)) {
            Path only = files.filter(path -> path.toString().endsWith(".json")).findFirst()
                    .orElseThrow(() -> new AssertionError("no JSON output written to " + directory));
            return objectMapper.readTree(only.toFile());
        }
    }

    @Test
    void extractSubjectIrisExpandsViaTheCurieMap(@TempDir Path tempDir) throws IOException {
        Path obsoleteFile = writeTsv(tempDir, "efo.obsolete.sssom.tsv", OBSOLETE_TSV);

        Set<String> subjectIris = TSV2JSON.extractSubjectIris(obsoleteFile.toFile());

        assertTrue(subjectIris.contains("http://www.ebi.ac.uk/efo/EFO_1000466"),
                "the obsolete subject CURIE must expand via the file's curie_map; got " + subjectIris);
    }

    @Test
    void stampsSubjectObsoleteAndSetObsoleteOnTheObsoleteFile(@TempDir Path tempDir) throws IOException {
        Path obsoleteDir = Files.createDirectory(tempDir.resolve("obsolete-in"));
        Path obsoleteFile = writeTsv(obsoleteDir, "efo.obsolete.sssom.tsv", OBSOLETE_TSV);
        Set<String> obsoleteIris = TSV2JSON.extractSubjectIris(obsoleteFile.toFile());
        assertFalse(obsoleteIris.isEmpty(), "Pass 1 must find at least one obsolete subject IRI");

        Path mappingSetDir = Files.createDirectory(tempDir.resolve("mappingSet"));
        Path mappingDir = Files.createDirectory(tempDir.resolve("mapping"));
        // null release date: these tests exercise the obsolete flags, not the ADR-0043 stamp.
        TSV2JSON.processFile(obsoleteFile.toFile(), mappingSetDir.toString(), mappingDir.toString(),
                MappingSetCategory.ONTOLOGY, obsoleteIris, true, null);

        JsonNode mappings = readSingleJson(mappingDir);
        assertEquals(1, mappings.size());
        assertTrue(mappings.get(0).path("subject_obsolete").asBoolean(false),
                "the obsolete file's subject must be stamped obsolete");

        JsonNode mappingSet = readSingleJson(mappingSetDir);
        assertTrue(mappingSet.get(0).path("obsolete").asBoolean(false),
                "a config-flagged obsolete registry must stamp obsolete=true on its set");
    }

    @Test
    void stampsObjectObsoleteOnlyWhenTheObjectIsAnObsoleteTerm(@TempDir Path tempDir) throws IOException {
        Path obsoleteFile = writeTsv(Files.createDirectory(tempDir.resolve("obs")),
                "efo.obsolete.sssom.tsv", OBSOLETE_TSV);
        Set<String> obsoleteIris = TSV2JSON.extractSubjectIris(obsoleteFile.toFile());

        Path liveDir = Files.createDirectory(tempDir.resolve("live-in"));
        Path liveFile = writeTsv(liveDir, "mondo.sssom.tsv", LIVE_TSV);

        Path mappingSetDir = Files.createDirectory(tempDir.resolve("mappingSet"));
        Path mappingDir = Files.createDirectory(tempDir.resolve("mapping"));
        TSV2JSON.processFile(liveFile.toFile(), mappingSetDir.toString(), mappingDir.toString(),
                MappingSetCategory.ONTOLOGY, obsoleteIris, false, null);

        JsonNode mappings = readSingleJson(mappingDir);
        assertEquals(2, mappings.size());

        int objectObsoleteCount = 0;
        JsonNode obsoleteRow = null;
        for (JsonNode mapping : mappings) {
            assertFalse(mapping.path("subject_obsolete").asBoolean(false),
                    "a live MONDO subject is never obsolete");
            if (mapping.path("object_obsolete").asBoolean(false)) {
                objectObsoleteCount++;
                obsoleteRow = mapping;
            }
        }
        assertEquals(1, objectObsoleteCount,
                "exactly the row whose object is the obsolete term must be stamped object_obsolete");
        assertTrue(obsoleteRow.path("object_iri").asText().endsWith("EFO_1000466"),
                "the stamped row's object is the obsolete EFO term, not the live one");
    }

    @Test
    void stampsNothingWhenNoRegistryIsObsolete(@TempDir Path tempDir) throws IOException {
        // The empty obsolete set is the normal-load path: output must be free of the obsolete flags.
        Path liveFile = writeTsv(Files.createDirectory(tempDir.resolve("live-in")),
                "mondo.sssom.tsv", LIVE_TSV);
        Path mappingSetDir = Files.createDirectory(tempDir.resolve("mappingSet"));
        Path mappingDir = Files.createDirectory(tempDir.resolve("mapping"));

        TSV2JSON.processFile(liveFile.toFile(), mappingSetDir.toString(), mappingDir.toString(),
                MappingSetCategory.ONTOLOGY, Set.of(), false, null);

        JsonNode mappings = readSingleJson(mappingDir);
        for (JsonNode mapping : mappings) {
            assertFalse(mapping.has("subject_obsolete"), "NON_DEFAULT omits a false subject_obsolete");
            assertFalse(mapping.has("object_obsolete"), "NON_DEFAULT omits a false object_obsolete");
        }
        assertFalse(readSingleJson(mappingSetDir).get(0).has("obsolete"),
                "NON_DEFAULT omits a false set-level obsolete");
    }
}
