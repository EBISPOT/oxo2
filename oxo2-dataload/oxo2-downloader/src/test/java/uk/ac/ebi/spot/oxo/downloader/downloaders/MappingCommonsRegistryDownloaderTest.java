package uk.ac.ebi.spot.oxo.downloader.downloaders;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.spot.oxo.downloader.downloaders.MappingCommonsRegistryDownloader.RetryableDownloadException;
import uk.ac.ebi.spot.oxo.downloader.downloaders.MappingCommonsRegistryDownloader.SelectedMapping;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the pure {@link MappingCommonsRegistryDownloader#select} step against a hand-built
 * catalogue covering every selection rule from ADR-0014.
 */
class MappingCommonsRegistryDownloaderTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static JsonNode catalogue(String json) {
        try {
            return OBJECT_MAPPER.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<String, SelectedMapping> byKey(List<SelectedMapping> selected) {
        return selected.stream().collect(Collectors.toMap(
                mapping -> mapping.registrySlug() + "/" + mapping.fileName(), Function.identity()));
    }

    @Test
    void keepsOnlySssomEntriesWithAContentUrl() {
        JsonNode catalogue = catalogue("""
            [
              {"type": "sssom",      "content_url": "https://w3id.org/commons/mh/mappings/mp_hp.sssom.tsv",
               "registries": [{"id": "https://w3id.org/commons/mh"}]},
              {"type": "linkml_map", "content_url": "https://w3id.org/x/mappings/transform.yaml",
               "registries": [{"id": "https://w3id.org/commons/mh"}]},
              {"type": "sssom",      "content_url": "",
               "registries": [{"id": "https://w3id.org/commons/mh"}]}
            ]
            """);

        List<SelectedMapping> selected = MappingCommonsRegistryDownloader.select(catalogue, Set.of());

        assertEquals(1, selected.size());
        assertEquals("mp_hp.sssom.tsv", selected.get(0).fileName());
        assertEquals("mh", selected.get(0).registrySlug());
        assertFalse(selected.get(0).gzipped());
    }

    @Test
    void dropsTheFairTransformsRegistry() {
        JsonNode catalogue = catalogue("""
            [
              {"type": "sssom", "content_url": "https://mapping-commons.github.io/mappings/sssom-to-fair.sssom.tsv",
               "registries": [{"id": "https://w3id.org/mapping-commons/transforms"}]}
            ]
            """);

        assertTrue(MappingCommonsRegistryDownloader.select(catalogue, Set.of()).isEmpty());
    }

    @Test
    void appliesTheExcludeListAgainstTheGzippedBasename() {
        JsonNode catalogue = catalogue("""
            [
              {"type": "sssom", "content_url": "https://zenodo.org/records/100/files/priority.sssom.tsv.gz",
               "registries": [{"id": "https://github.com/biopragmatics/mapping-registry"}]},
              {"type": "sssom", "content_url": "https://zenodo.org/records/100/files/processed.sssom.tsv.gz",
               "registries": [{"id": "https://github.com/biopragmatics/mapping-registry"}]}
            ]
            """);

        List<SelectedMapping> selected =
                MappingCommonsRegistryDownloader.select(catalogue, Set.of("processed.sssom.tsv.gz"));

        assertEquals(1, selected.size());
        SelectedMapping priority = selected.get(0);
        assertEquals("priority.sssom.tsv", priority.fileName(), "the .gz suffix is stripped for the on-disk name");
        assertEquals("mapping-registry", priority.registrySlug());
        assertTrue(priority.gzipped(), "the source is still gzipped and must be decompressed on download");
    }

    @Test
    void keepsDistinctSetsSharingAFilenameAndDisambiguatesByRecord() {
        // The biopragmatics SeMRA landscapes each publish priority.sssom.tsv.gz under a different Zenodo
        // record — different content, not versions. All must be kept, namespaced by the record id.
        JsonNode catalogue = catalogue("""
            [
              {"type": "sssom", "content_url": "https://zenodo.org/records/15826794/files/priority.sssom.tsv.gz",
               "registries": [{"id": "https://github.com/biopragmatics/mapping-registry"}]},
              {"type": "sssom", "content_url": "https://zenodo.org/records/15826779/files/priority.sssom.tsv.gz",
               "registries": [{"id": "https://github.com/biopragmatics/mapping-registry"}]},
              {"type": "sssom", "content_url": "https://zenodo.org/records/15826693/files/priority.sssom.tsv.gz",
               "registries": [{"id": "https://github.com/biopragmatics/mapping-registry"}]}
            ]
            """);

        List<SelectedMapping> selected = MappingCommonsRegistryDownloader.select(catalogue, Set.of());

        assertEquals(3, selected.size(), "all three distinct landscapes are kept");
        Set<String> targetDirs = selected.stream().map(SelectedMapping::targetDir).collect(Collectors.toSet());
        assertEquals(Set.of("mapping-registry/15826794", "mapping-registry/15826779", "mapping-registry/15826693"),
                targetDirs, "each is namespaced under its Zenodo record id");
        assertTrue(selected.stream().allMatch(mapping -> mapping.fileName().equals("priority.sssom.tsv")));
        assertTrue(selected.stream().allMatch(SelectedMapping::gzipped));
    }

    @Test
    void usesLandscapeNamesAsDiscriminatorWhenProvided() {
        String gene = "https://zenodo.org/records/15826794/files/priority.sssom.tsv.gz";
        String cell = "https://zenodo.org/records/15826779/files/priority.sssom.tsv.gz";
        String disease = "https://zenodo.org/records/15826693/files/priority.sssom.tsv.gz";
        JsonNode catalogue = catalogue("""
            [
              {"type": "sssom", "content_url": "%s",
               "registries": [{"id": "https://github.com/biopragmatics/mapping-registry"}]},
              {"type": "sssom", "content_url": "%s",
               "registries": [{"id": "https://github.com/biopragmatics/mapping-registry"}]},
              {"type": "sssom", "content_url": "%s",
               "registries": [{"id": "https://github.com/biopragmatics/mapping-registry"}]}
            ]
            """.formatted(gene, cell, disease));

        List<SelectedMapping> selected = MappingCommonsRegistryDownloader.select(catalogue, Set.of(),
                Map.of(gene, "gene", cell, "cell", disease, "disease"));

        Set<String> targetDirs = selected.stream().map(SelectedMapping::targetDir).collect(Collectors.toSet());
        assertEquals(Set.of("mapping-registry/gene", "mapping-registry/cell", "mapping-registry/disease"),
                targetDirs, "landscape names win over record ids");
    }

    @Test
    void fallsBackToRecordIdWhenLandscapeMissingForAnEntry() {
        String gene = "https://zenodo.org/records/15826794/files/priority.sssom.tsv.gz";
        String cell = "https://zenodo.org/records/15826779/files/priority.sssom.tsv.gz";
        JsonNode catalogue = catalogue("""
            [
              {"type": "sssom", "content_url": "%s",
               "registries": [{"id": "https://github.com/biopragmatics/mapping-registry"}]},
              {"type": "sssom", "content_url": "%s",
               "registries": [{"id": "https://github.com/biopragmatics/mapping-registry"}]}
            ]
            """.formatted(gene, cell));

        List<SelectedMapping> selected = MappingCommonsRegistryDownloader.select(catalogue, Set.of(),
                Map.of(gene, "gene"));  // only the gene URL is resolved

        Set<String> targetDirs = selected.stream().map(SelectedMapping::targetDir).collect(Collectors.toSet());
        assertEquals(Set.of("mapping-registry/gene", "mapping-registry/15826779"), targetDirs);
    }

    @Test
    void parsesGroupsFromRegistryYaml() throws Exception {
        JsonNode registry = new YAMLMapper().readTree("""
            mapping_registry_id: https://github.com/biopragmatics/mapping-registry
            mapping_set_references:
              - mapping_set_id: https://zenodo.org/records/15826794/files/priority.sssom.tsv.gz
                mapping_set_group: gene
              - mapping_set_id: https://zenodo.org/records/15826794/files/processed.sssom.tsv.gz
                mapping_set_group: gene
              - mapping_set_id: https://zenodo.org/records/15826693/files/priority.sssom.tsv.gz
                mapping_set_group: disease
              - mapping_set_id: https://w3id.org/biopragmatics/biomappings/sssom/biomappings.sssom.tsv
                local_name: biomappings.sssom.tsv
            """);

        Map<String, String> groups = MappingCommonsRegistryDownloader.parseRegistryGroups(registry);

        assertEquals("gene", groups.get("https://zenodo.org/records/15826794/files/priority.sssom.tsv.gz"));
        assertEquals("disease", groups.get("https://zenodo.org/records/15826693/files/priority.sssom.tsv.gz"));
        // entries without a mapping_set_group are not mapped
        assertFalse(groups.containsKey(
                "https://w3id.org/biopragmatics/biomappings/sssom/biomappings.sssom.tsv"));
    }

    @Test
    void exactDuplicateContentUrlIsDroppedButNotDistinctOnes() {
        JsonNode catalogue = catalogue("""
            [
              {"type": "sssom", "content_url": "https://zenodo.org/records/1/files/priority.sssom.tsv.gz",
               "registries": [{"id": "https://github.com/biopragmatics/mapping-registry"}]},
              {"type": "sssom", "content_url": "https://zenodo.org/records/1/files/priority.sssom.tsv.gz",
               "registries": [{"id": "https://github.com/biopragmatics/mapping-registry"}]}
            ]
            """);

        List<SelectedMapping> selected = MappingCommonsRegistryDownloader.select(catalogue, Set.of());

        assertEquals(1, selected.size(), "an exact-duplicate content_url is collapsed");
        // A lone set keeps the bare registry dir, no record subdirectory.
        assertEquals("mapping-registry", selected.get(0).targetDir());
    }

    @Test
    void sameFilenameInDifferentRegistriesStaysDistinct() {
        JsonNode catalogue = catalogue("""
            [
              {"type": "sssom", "content_url": "https://w3id.org/commons/monarch/mappings/mondo_hasdbxref_hp.sssom.tsv",
               "registries": [{"id": "https://w3id.org/sssom/commons/monarch"}]},
              {"type": "sssom", "content_url": "https://gitlab.example.org/cpont/mappings/mondo_hasdbxref_hp.sssom.tsv",
               "registries": [{"id": "https://w3id.org/cpont/mappings"}]}
            ]
            """);

        List<SelectedMapping> selected = MappingCommonsRegistryDownloader.select(catalogue, Set.of());

        assertEquals(2, selected.size());
        Map<String, SelectedMapping> byKey = byKey(selected);
        assertTrue(byKey.containsKey("monarch/mondo_hasdbxref_hp.sssom.tsv"));
        assertTrue(byKey.containsKey("mappings/mondo_hasdbxref_hp.sssom.tsv"));
    }

    @Test
    void skipsEntriesWithAnUnsafeFilename() {
        JsonNode catalogue = catalogue("""
            [
              {"type": "sssom", "content_url": "https://example.org/mappings/a%20b;rm.sssom.tsv",
               "registries": [{"id": "https://w3id.org/commons/mh"}]}
            ]
            """);

        assertTrue(MappingCommonsRegistryDownloader.select(catalogue, Set.of()).isEmpty());
    }

    @Test
    void returnsEmptyForNonArrayCatalogue() {
        assertTrue(MappingCommonsRegistryDownloader.select(catalogue("{}"), Set.of()).isEmpty());
        assertTrue(MappingCommonsRegistryDownloader.select(null, Set.of()).isEmpty());
    }

    @Test
    void retriesTransientFailuresThenSucceeds() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        // Fails with a (retryable) 504-style error twice, then succeeds — exactly the Zenodo case.
        String result = MappingCommonsRegistryDownloader.withRetry("zenodo", 4, 0, () -> {
            if (attempts.incrementAndGet() < 3) {
                throw new RetryableDownloadException("status=504");
            }
            return "ok";
        });

        assertEquals("ok", result);
        assertEquals(3, attempts.get());
    }

    @Test
    void givesUpAfterMaxAttemptsOnPersistentTransientFailure() {
        AtomicInteger attempts = new AtomicInteger();

        IOException thrown = assertThrows(IOException.class, () ->
                MappingCommonsRegistryDownloader.withRetry("zenodo", 4, 0, () -> {
                    attempts.incrementAndGet();
                    throw new RetryableDownloadException("status=504");
                }));

        assertEquals(4, attempts.get(), "stops at maxAttempts");
        assertTrue(thrown.getMessage().contains("Giving up"));
    }

    @Test
    void doesNotRetryNonTransientFailures() {
        AtomicInteger attempts = new AtomicInteger();

        assertThrows(IOException.class, () ->
                MappingCommonsRegistryDownloader.withRetry("not-found", 4, 0, () -> {
                    attempts.incrementAndGet();
                    throw new IOException("status=404");  // permanent, not a RetryableDownloadException
                }));

        assertEquals(1, attempts.get(), "a permanent failure is not retried");
    }
}
