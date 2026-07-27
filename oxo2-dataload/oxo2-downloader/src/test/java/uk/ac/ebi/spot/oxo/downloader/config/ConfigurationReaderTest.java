package uk.ac.ebi.spot.oxo.downloader.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ConfigurationReader} deserializes the OxO config with its production {@code ObjectMapper}
 * (strict — unknown properties fail). These tests pin the global {@code min_inference_confidence}
 * knob (ADR-0037): it deserializes when present, is empty when absent, and — because the mapper is
 * strict — an <em>un</em>registered top-level key is still rejected, which is exactly why the field
 * had to be added to {@link OxoConfiguration}.
 */
class ConfigurationReaderTest {

    private Path writeConfig(Path directory, String json) throws IOException {
        Path configFile = directory.resolve("oxo-config.json");
        Files.writeString(configFile, json, StandardCharsets.UTF_8);
        return configFile;
    }

    @Test
    void readsMinInferenceConfidenceWhenPresent(@TempDir Path tempDir) throws IOException {
        Path configFile = writeConfig(tempDir, """
                {
                  "min_inference_confidence": 0.75,
                  "mapping_registries": [ { "id": "evora", "url": "https://example.org/x.sssom.tsv" } ]
                }
                """);

        Optional<OxoConfiguration> configuration = ConfigurationReader.read(configFile.toString());

        assertTrue(configuration.isPresent(), "a valid config must deserialize");
        assertEquals(Optional.of(0.75), configuration.get().getMinInferenceConfidence());
        assertEquals(1, configuration.get().getMappingRegistries().size());
    }

    @Test
    void minInferenceConfidenceIsEmptyWhenAbsent(@TempDir Path tempDir) throws IOException {
        Path configFile = writeConfig(tempDir, """
                {
                  "mapping_registries": [ { "id": "evora", "url": "https://example.org/x.sssom.tsv" } ]
                }
                """);

        Optional<OxoConfiguration> configuration = ConfigurationReader.read(configFile.toString());

        assertTrue(configuration.isPresent());
        assertTrue(configuration.get().getMinInferenceConfidence().isEmpty(),
                "an absent key must leave the gate unconfigured, not default to a number");
    }

    @Test
    void readsRegistryObsoleteFlagWhenPresent(@TempDir Path tempDir) throws IOException {
        // The strict mapper would reject `obsolete` if MappingRegistry did not declare it (ADR-0041) —
        // and rejecting it would take the whole download stage down, so this pins the registration.
        Path configFile = writeConfig(tempDir, """
                {
                  "mapping_registries": [
                    { "id": "efo.live", "url": "https://example.org/live.sssom.tsv" },
                    { "id": "efo.obsolete", "url": "https://example.org/obs.sssom.tsv", "obsolete": true }
                  ]
                }
                """);

        Optional<OxoConfiguration> configuration = ConfigurationReader.read(configFile.toString());

        assertTrue(configuration.isPresent(), "a config carrying obsolete must still deserialize");
        var registries = configuration.get().getMappingRegistries();
        assertEquals(2, registries.size());
        assertTrue(registries.get(0).getObsolete().isEmpty(),
                "a registry without the key leaves obsolete unset (its subjects are live)");
        assertEquals(Optional.of(true), registries.get(1).getObsolete());
    }

    @Test
    void unknownTopLevelKeyIsStillRejected(@TempDir Path tempDir) throws IOException {
        Path configFile = writeConfig(tempDir, """
                {
                  "min_confidence_typo": 0.75,
                  "mapping_registries": [ { "id": "evora", "url": "https://example.org/x.sssom.tsv" } ]
                }
                """);

        Optional<OxoConfiguration> configuration = ConfigurationReader.read(configFile.toString());

        assertFalse(configuration.isPresent(),
                "the strict mapper must reject an unregistered key — the reason the field is declared");
    }
}
