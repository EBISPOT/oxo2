package uk.ac.ebi.spot.oxo.integration;

import java.nio.file.Path;

/**
 * Resolves the four assertion-target paths for a given rule fixture, both on the actual
 * pipeline output side (rooted at $OXO2_DATA) and the expected side (rooted at
 * testcases_expected_output/minimal/rules/).
 */
public final class ArtifactPaths {

    private ArtifactPaths() {}

    public static Path actualInferredTtl(String baseName) {
        return Env.oxo2Data().resolve("inferences").resolve("inferredMappings").resolve(baseName + ".ttl");
    }

    public static Path actualChainJson(String baseName) {
        return Env.oxo2Data().resolve("inferences").resolve("inferenceChains").resolve(baseName + "-chains.json");
    }

    public static Path actualExplainedJson(String baseName) {
        return Env.oxo2Data().resolve("inferences").resolve("solr").resolve("mapping").resolve(baseName + "-explained.json");
    }

    public static Path actualMappingSetJson(String baseName) {
        return Env.oxo2Data().resolve("inferences").resolve("solr").resolve("mappingSet").resolve(baseName + "-mappingSet.json");
    }

    public static Path expectedInferredTtl(String baseName) {
        return RuleFixtures.expectedDir().resolve("inferredMappings").resolve(baseName + ".ttl");
    }

    public static Path expectedChainJson(String baseName) {
        return RuleFixtures.expectedDir().resolve("inferenceChains").resolve(baseName + "-chains.json");
    }

    public static Path expectedExplainedJson(String baseName) {
        return RuleFixtures.expectedDir().resolve("solr").resolve("mapping").resolve(baseName + "-explained.json");
    }

    public static Path expectedMappingSetJson(String baseName) {
        return RuleFixtures.expectedDir().resolve("solr").resolve("mappingSet").resolve(baseName + "-mappingSet.json");
    }

    public static Path expectedNumFound() {
        return RuleFixtures.expectedDir().resolve("numFound.json");
    }
}
