package uk.ac.ebi.spot.oxo.integration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Invokes loadData.nextflow as a subprocess against the generated OXO2_CONFIG, in "unmanaged Solr"
 * mode. Inherits stdout/stderr so users see the same output as a manual run. Throws on non-zero exit.
 *
 * <p>The {@link SolrLifecycle} harness owns the Solr process and the per-fixture collection wipe for
 * the whole run, so each loadData pass runs with {@code OXO2_SOLR_UNMANAGED=true}: it indexes into the
 * already-running, already-cleared test collections and never starts/stops Solr or rewrites the Solr
 * config (which would corrupt the live cores it shares with the other fixtures).
 *
 * <p>Every pass runs against the isolated test workspace + Solr: the harness reads the {@code *_TEST}
 * env vars and injects them as the plain {@code OXO2_DATA} / {@code SOLR_HOME} / {@code SOLR_URL}
 * loadData.nextflow expects, so a run never touches a developer's production data or Solr.
 */
public final class Pipeline {

    private Pipeline() {}

    /** Runs loadData.nextflow against the given OXO2_CONFIG, in isolation (the run wipes the test
     *  {@code $OXO2_DATA} intermediates; the harness has already cleared the shared Solr collections),
     *  then loads only the sets that config lists into the harness-managed test Solr. */
    public static void runLoadDataNextflow(Path config) throws IOException, InterruptedException {
        Path repoRoot = Env.repoRoot();
        Path loadDataScript = repoRoot.resolve("oxo2-dataload").resolve("loadData.nextflow");
        if (!Files.isExecutable(loadDataScript)) {
            throw new IllegalStateException("loadData.nextflow not executable or missing: " + loadDataScript);
        }
        Path testConfig = repoRoot.resolve("oxo2-integration-tests").resolve("nextflow-test.config");
        if (!Files.isRegularFile(testConfig)) {
            throw new IllegalStateException("nextflow-test.config missing: " + testConfig);
        }

        ProcessBuilder processBuilder = new ProcessBuilder(loadDataScript.toString())
                .directory(repoRoot.toFile())
                .inheritIO();
        Map<String, String> environment = processBuilder.environment();
        environment.put("OXO2_CONFIG", config.toString());
        // Isolated test workspace + Solr: inject the *_TEST values as the plain vars loadData reads.
        environment.put("OXO2_DATA", Env.oxo2Data().toString());
        environment.put("SOLR_HOME", Env.solrHome().toString());
        environment.put("SOLR_URL", Env.solrHost());
        environment.put("OXO2_SOLR_HOST", Env.solrHost());
        // Solr is owned by the SolrLifecycle harness: loadData must not copy config / start / stop it.
        environment.put("OXO2_SOLR_UNMANAGED", "true");
        // Pick the test profile and layer in resource overrides sized for tiny fixtures. The standard
        // profile asks for many GB per INFER_CROSS_SET task against a 24 GB executor pool, which
        // deadlocks the local scheduler across the fixtures.
        environment.put("NF_PROFILE", "test");
        environment.put("NF_EXTRA_CONFIG", testConfig.toString());

        long started = System.currentTimeMillis();
        Process process = processBuilder.start();
        boolean finished = process.waitFor(30, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("loadData.nextflow did not finish within 30 minutes; aborted.");
        }
        int exit = process.exitValue();
        long elapsedSec = (System.currentTimeMillis() - started) / 1000;
        System.out.println("loadData.nextflow exited " + exit + " after " + elapsedSec + "s.");
        if (exit != 0) {
            throw new IllegalStateException("loadData.nextflow exited with code " + exit);
        }
    }
}
