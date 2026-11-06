package uk.ac.ebi.spot.oxo.sssom2json;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import uk.ac.ebi.spot.oxo.model.sssom.MappingSetCategory;
import uk.ac.ebi.spot.oxo.sssom2json.parser.TSV2JSON;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TSV2JSONReportingContactsTest {
    @TempDir
    Path tempDir;

    private JsonNode convert(String contacts) throws IOException {
        return convert(contacts, false);
    }

    private JsonNode convert(String contacts, boolean compactHeader) throws IOException {
        Path input = tempDir.resolve("mappings.sssom.tsv");
        String header = """
                # mapping_set_id: https://example.org/set
                # mapping_set_title: Example mappings
                # curie_map:
                #   MONDO: http://purl.obolibrary.org/obo/MONDO_
                #   EFO: http://www.ebi.ac.uk/efo/EFO_
                #   skos: http://www.w3.org/2004/02/skos/core#
                #   semapv: https://w3id.org/semapv/vocab/
                """ + contacts;
        Files.writeString(input, (compactHeader ? header.replace("# ", "#") : header) + """
                subject_id\tpredicate_id\tobject_id\tmapping_justification
                MONDO:0000001\tskos:exactMatch\tEFO:0000001\tsemapv:UnspecifiedMatching
                """);
        Path mappingSets = Files.createDirectory(tempDir.resolve("mappingSets"));
        Path mappings = Files.createDirectory(tempDir.resolve("mappings"));
        TSV2JSON.processFile(input.toFile(), mappingSets.toString(), mappings.toString(),
                MappingSetCategory.ONTOLOGY, Set.of(), false, null);
        try (var files = Files.list(mappingSets)) {
            Path output = files.filter(path -> path.toString().endsWith(".json")).findFirst().orElseThrow();
            return new ObjectMapper().readTree(output.toFile()).get(0);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"users@example.org", "https://groups.google.com/g/example", "mailto:users@example.org"})
    void preservesContactsFromSssomThroughMappingSetReconstruction(String mailingList) throws IOException {
        JsonNode mappingSet = convert("# issue_tracker: https://github.com/example/ontology/issues\n"
                + "# mailing_list: " + mailingList + "\n");

        assertEquals("https://github.com/example/ontology/issues", mappingSet.path("issue_tracker").asText());
        assertEquals(mailingList, mappingSet.path("mailing_list").asText());
    }

    @Test
    void preservesSetMetadataIncludingZeroConfidenceAndPublisher() throws IOException {
        JsonNode mappingSet = convert("""
                # mapping_set_confidence: 0
                # mapping_set_description: Community curated mappings.
                # mapping_set_version: 0.6.1-dev-9e9cd532
                # license: https://creativecommons.org/publicdomain/zero/1.0/
                # creator_id:
                # - orcid:0000-0003-4423-4370
                # publisher:
                # - Example publisher
                # - https://example.org/publisher
                """, true);
        assertEquals(0.0, mappingSet.path("mapping_set_confidence").asDouble());
        assertTrue(mappingSet.has("mapping_set_confidence"));
        assertEquals("0.6.1-dev-9e9cd532", mappingSet.path("mapping_set_version").asText());
        assertEquals("Community curated mappings.", mappingSet.path("mapping_set_description").asText());
        assertEquals("https://creativecommons.org/publicdomain/zero/1.0/", mappingSet.path("license").asText());
        assertEquals("ORCID:0000-0003-4423-4370", mappingSet.path("creator_id").get(0).asText());
        assertEquals(2, mappingSet.path("publisher").size());
        assertEquals("Example publisher", mappingSet.path("publisher").get(0).asText());
    }

    @Test
    void doesNotInventContactsWhenMissing() throws IOException {
        JsonNode mappingSet = convert("");
        assertFalse(mappingSet.has("issue_tracker"));
        assertFalse(mappingSet.has("mailing_list"));
        assertFalse(mappingSet.has("mapping_set_confidence"));
        assertFalse(mappingSet.has("publisher"));
    }

    @Test
    void supportsMailingListWithoutIssueTracker() throws IOException {
        JsonNode mappingSet = convert("# mailing_list: users@example.org\n");
        assertEquals("users@example.org", mappingSet.path("mailing_list").asText());
        assertFalse(mappingSet.has("issue_tracker"));
    }

    @Test
    void preservesMetadataWhenCommentPrefixesAreMixed() throws IOException {
        JsonNode mappingSet = convert("#issue_tracker: https://github.com/example/ontology/issues\n"
                + "# mailing_list: users@example.org\n");
        assertEquals("https://example.org/set", mappingSet.path("mapping_set_id").asText());
        assertEquals("https://github.com/example/ontology/issues", mappingSet.path("issue_tracker").asText());
        assertEquals("users@example.org", mappingSet.path("mailing_list").asText());
        assertTrue(mappingSet.path("curie_map").asText().contains("MONDO:http://purl.obolibrary.org/obo/MONDO_"));
    }

    @Test
    void preservesCompactHeadersIncludingNestedCurieMap() throws IOException {
        JsonNode mappingSet = convert("# issue_tracker: https://github.com/example/ontology/issues\n", true);
        assertEquals("https://github.com/example/ontology/issues", mappingSet.path("issue_tracker").asText());
        assertTrue(mappingSet.path("curie_map").asText().contains("MONDO:http://purl.obolibrary.org/obo/MONDO_"));
    }

    @Test
    void rejectsMalformedHeaderInsteadOfInventingContactlessMetadata() throws IOException {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> convert("# efo-users@lists.sourceforge.net\n"
                        + "#issue_tracker: https://github.com/example/ontology/issues\n"));
        assertTrue(error.getMessage().contains("mappings.sssom.tsv"));
        try (var outputs = Files.list(tempDir.resolve("mappingSets"))) {
            assertEquals(0, outputs.count());
        }
    }

    @Test
    void rejectsMalformedExternalMetadata() throws IOException {
        Path sidecar = tempDir.resolve("mappings.sssom.yml");
        Files.writeString(sidecar, "mapping_set_id: https://example.org/set\nusers@example.org\n");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> TSV2JSON.readYaml(sidecar.toFile()));
        assertTrue(error.getMessage().contains(sidecar.toString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"--inputFile", "--inputDir"})
    void cliPropagatesMalformedMetadataFailure(String inputOption) throws IOException {
        Path input = tempDir.resolve("mappings.tsv");
        Files.writeString(input, "# mapping_set_id: https://example.org/set\n# users@example.org\n"
                + "subject_id\tpredicate_id\tobject_id\nMONDO:1\tskos:exactMatch\tEFO:1\n");
        assertThrows(IllegalArgumentException.class, () -> SSSOM2JSON.main(new String[] {
                inputOption, inputOption.equals("--inputFile") ? input.toString() : tempDir.toString(),
                "--outputDir", tempDir.resolve("output").toString()
        }));
    }
}
