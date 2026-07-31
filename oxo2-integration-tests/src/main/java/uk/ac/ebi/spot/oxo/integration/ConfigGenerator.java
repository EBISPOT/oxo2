package uk.ac.ebi.spot.oxo.integration;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Writes an OXO2_CONFIG JSON listing fixture TSVs as {@code file://} registries, suitable for
 * loadData.nextflow. Output lives under oxo2-integration-tests/target/ so it's disposable via
 * mvn clean and is untouched by loadData.nextflow's cleanup (which only wipes paths under $OXO2_DATA).
 *
 * Each fixture is run in isolation (ADR-0009), so the integration test calls {@link #generate(RuleFixtures.Fixture)}
 * once per fixture. {@link #generateAll()} backs the standalone generateConfig tool for a manual
 * all-fixtures run.
 */
public final class ConfigGenerator {

    public static final String GENERATED_CONFIG_FILENAME = "oxo-config-minimal-rules.generated.json";

    /**
     * A fixture set whose base name ends with this is declared {@code "obsolete": true} in the generated
     * config, so every subject of it is an obsolete term (ADR-0041). The naming convention is the whole
     * mechanism: `mapping_registries` flags are operator knowledge and cannot be expressed inside a SSSOM
     * TSV, so a fixture that needs the flag has to say so through its filename.
     *
     * <p>Exists for ADR-0045: without an obsolete registry, every {@code _live} count bucket in every
     * golden equals its base bucket, so a fold that wrongly credited an obsolete-endpoint sighting to the
     * live bucket would leave all 22 goldens byte-identical and pass. Exactly the hole
     * {@code HIDDEN_PREDICATES} was added to close for the weak-predicate buckets.
     */
    public static final String OBSOLETE_SET_SUFFIX = "-obsolete";

    private ConfigGenerator() {}

    /** Per-fixture config: only this fixture's set(s). */
    public static Path generate(RuleFixtures.Fixture fixture) throws IOException {
        return write(List.of(fixture), "oxo-config-" + fixture.name + ".generated.json");
    }

    /** Combined config across all discovered fixtures (honours -Doxo2.it.rule). Standalone tool only. */
    public static Path generateAll() throws IOException {
        return write(RuleFixtures.discover(), GENERATED_CONFIG_FILENAME);
    }

    private static Path write(List<RuleFixtures.Fixture> fixtures, String filename) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        ArrayNode registries = root.putArray("mapping_registries");
        for (RuleFixtures.Fixture fixture : fixtures) {
            for (Path tsv : fixture.tsvs) {
                String filenameNoSuffix = tsv.getFileName().toString();
                String id = filenameNoSuffix.endsWith(RuleFixtures.FIXTURE_SUFFIX)
                        ? filenameNoSuffix.substring(0, filenameNoSuffix.length() - RuleFixtures.FIXTURE_SUFFIX.length())
                        : filenameNoSuffix;
                ObjectNode entry = registries.addObject();
                entry.put("id", id);
                entry.put("url", tsv.toUri().toString());
                if (id.endsWith(OBSOLETE_SET_SUFFIX)) {
                    entry.put("obsolete", true);
                }
            }
        }

        Path outputDir = Env.repoRoot().resolve("oxo2-integration-tests").resolve("target");
        Files.createDirectories(outputDir);
        Path outputFile = outputDir.resolve(filename);
        mapper.writerWithDefaultPrettyPrinter().writeValue(outputFile.toFile(), root);
        return outputFile;
    }
}
