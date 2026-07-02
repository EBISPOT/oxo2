package uk.ac.ebi.spot.oxo.integration;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Env {
    // The integration tests run against a workspace + Solr that are kept SEPARATE from the
    // production OXO2_DATA / SOLR_HOME / OXO2_SOLR_HOST, so a test run never wipes or destroys a
    // developer's real data or Solr. The harness reads these *_TEST vars and injects them into the
    // loadData.nextflow subprocess as the plain OXO2_DATA / SOLR_HOME / SOLR_URL it expects.
    // See oxo2-integration-tests/CONTEXT.md § Environment contract.
    public static final String OXO2_DATA_TEST = "OXO2_DATA_TEST";
    public static final String SOLR_HOME_TEST = "SOLR_HOME_TEST";
    public static final String OXO2_SOLR_HOST_TEST = "OXO2_SOLR_HOST_TEST";
    public static final String NEXTFLOW_DIR = "NEXTFLOW_DIR";
    public static final String SOLR_SCRIPT = "SOLR_SCRIPT";

    public static final String RULE_FILTER_PROPERTY = "oxo2.it.rule";
    public static final String KEEP_SOLR_PROPERTY = "oxo2.it.keepSolr";

    private Env() {}

    public static void requireAll() {
        Map<String, String> values = new LinkedHashMap<>();
        for (String variableName : new String[]{OXO2_DATA_TEST, SOLR_HOME_TEST, OXO2_SOLR_HOST_TEST,
                NEXTFLOW_DIR, SOLR_SCRIPT}) {
            String value = System.getenv(variableName);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException(
                        "Required environment variable " + variableName + " is not set. " +
                        "The integration tests run against an isolated test workspace + Solr — set " +
                        OXO2_DATA_TEST + ", " + SOLR_HOME_TEST + " and " + OXO2_SOLR_HOST_TEST +
                        " (and " + NEXTFLOW_DIR + " / " + SOLR_SCRIPT + "). " +
                        "See oxo2-integration-tests/CONTEXT.md § Environment contract.");
            }
            values.put(variableName, value);
        }
    }

    public static Path oxo2Data()     { return Paths.get(System.getenv(OXO2_DATA_TEST)); }
    public static Path solrHome()     { return Paths.get(System.getenv(SOLR_HOME_TEST)); }
    public static String solrHost()   { return System.getenv(OXO2_SOLR_HOST_TEST); }
    public static String solrScript() { return System.getenv(SOLR_SCRIPT); }

    /** Port the isolated test Solr listens on, parsed from {@code OXO2_SOLR_HOST_TEST}. The harness
     *  starts/stops Solr on this port so it never collides with a developer's production Solr on
     *  8983. */
    public static int solrPort() {
        int port = URI.create(solrHost()).getPort();
        if (port < 0) {
            throw new IllegalStateException(OXO2_SOLR_HOST_TEST + " has no explicit port: " + solrHost()
                    + " — the harness needs a port to start/stop the test Solr "
                    + "(e.g. http://localhost:8984/solr).");
        }
        return port;
    }

    public static Path repoRoot() {
        // Walk up from the JVM's working dir looking for the unique repo-root marker:
        // a directory that contains BOTH oxo2-shared/ and oxo2-dataload/ as subdirs.
        // Per-module CONTEXT.md and pom.xml aren't unique enough to identify the root.
        Path candidate = Paths.get("").toAbsolutePath();
        while (candidate != null) {
            if (candidate.resolve("oxo2-shared").toFile().isDirectory()
                    && candidate.resolve("oxo2-dataload").toFile().isDirectory()) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("Could not locate OxO2 repo root from " +
                Paths.get("").toAbsolutePath() + " (no ancestor contains oxo2-shared/ + oxo2-dataload/).");
    }

    public static String ruleFilter() {
        String value = System.getProperty(RULE_FILTER_PROPERTY);
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    /** {@code -Doxo2.it.keepSolr=true} leaves the test Solr running after the run so it can be
     *  inspected while debugging a fixture. Default false: the run stops Solr at the end. */
    public static boolean keepSolr() {
        return Boolean.parseBoolean(System.getProperty(KEEP_SOLR_PROPERTY, "false"));
    }
}
