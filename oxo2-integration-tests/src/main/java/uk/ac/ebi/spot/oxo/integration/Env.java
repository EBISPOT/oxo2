package uk.ac.ebi.spot.oxo.integration;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Env {
    public static final String OXO2_DATA = "OXO2_DATA";
    public static final String NEXTFLOW_DIR = "NEXTFLOW_DIR";
    public static final String SOLR_SCRIPT = "SOLR_SCRIPT";
    public static final String SOLR_HOME = "SOLR_HOME";
    public static final String OXO2_SOLR_HOST = "OXO2_SOLR_HOST";

    public static final String RULE_FILTER_PROPERTY = "oxo2.it.rule";

    private Env() {}

    public static void requireAll() {
        Map<String, String> values = new LinkedHashMap<>();
        for (String k : new String[]{OXO2_DATA, NEXTFLOW_DIR, SOLR_SCRIPT, SOLR_HOME, OXO2_SOLR_HOST}) {
            String v = System.getenv(k);
            if (v == null || v.isBlank()) {
                throw new IllegalStateException(
                        "Required environment variable " + k + " is not set. " +
                        "See oxo2-integration-tests/CONTEXT.md § Environment contract.");
            }
            values.put(k, v);
        }
    }

    public static Path oxo2Data()   { return Paths.get(System.getenv(OXO2_DATA)); }
    public static String solrHost() { return System.getenv(OXO2_SOLR_HOST); }

    public static Path repoRoot() {
        // Walk up from the JVM's working dir looking for the unique repo-root marker:
        // a directory that contains BOTH oxo2-shared/ and oxo2-dataload/ as subdirs.
        // Per-module CONTEXT.md and pom.xml aren't unique enough to identify the root.
        Path p = Paths.get("").toAbsolutePath();
        while (p != null) {
            if (p.resolve("oxo2-shared").toFile().isDirectory()
                    && p.resolve("oxo2-dataload").toFile().isDirectory()) {
                return p;
            }
            p = p.getParent();
        }
        throw new IllegalStateException("Could not locate OxO2 repo root from " +
                Paths.get("").toAbsolutePath() + " (no ancestor contains oxo2-shared/ + oxo2-dataload/).");
    }

    public static String ruleFilter() {
        String v = System.getProperty(RULE_FILTER_PROPERTY);
        return (v == null || v.isBlank()) ? null : v.trim();
    }
}
