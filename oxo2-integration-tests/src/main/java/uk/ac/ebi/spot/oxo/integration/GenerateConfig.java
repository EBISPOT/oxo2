package uk.ac.ebi.spot.oxo.integration;

import java.nio.file.Path;

/**
 * Maven entry point for `exec:java@generateConfig`. Writes the generated OXO2_CONFIG and
 * prints its path. No pipeline invocation.
 */
public final class GenerateConfig {
    public static void main(String[] args) throws Exception {
        Env.requireAll();
        Path configPath = ConfigGenerator.generate();
        System.out.println("Generated config: " + configPath);
        System.out.println("Set OXO2_CONFIG=" + configPath + " before invoking loadData.nextflow.");
    }
}
