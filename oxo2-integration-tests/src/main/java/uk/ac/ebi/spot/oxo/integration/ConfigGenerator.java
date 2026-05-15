package uk.ac.ebi.spot.oxo.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Writes an OXO2_CONFIG JSON listing each rule fixture's local file:// URL, suitable for
 * loadData.nextflow. Output lives under oxo2-integration-tests/target/ so it's disposable
 * via mvn clean and survives loadData.nextflow's `rm -R $OXO2_DATA/*` step. Sorted by rule
 * name for byte-stable output.
 */
public final class ConfigGenerator {

    public static final String GENERATED_CONFIG_FILENAME = "oxo-config-minimal-rules.generated.json";

    private ConfigGenerator() {}

    public static Path generate() throws IOException {
        List<RuleFixtures.Fixture> fixtures = RuleFixtures.discover();
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        ArrayNode registries = root.putArray("mapping_registries");
        for (RuleFixtures.Fixture fixture : fixtures) {
            ObjectNode entry = registries.addObject();
            entry.put("id", fixture.rule);
            entry.put("url", fixture.tsv.toUri().toString());
        }

        Path outputDir = Env.repoRoot().resolve("oxo2-integration-tests").resolve("target");
        Files.createDirectories(outputDir);
        Path outputFile = outputDir.resolve(GENERATED_CONFIG_FILENAME);
        mapper.writerWithDefaultPrettyPrinter().writeValue(outputFile.toFile(), root);
        return outputFile;
    }
}
