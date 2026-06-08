package uk.ac.ebi.spot.oxo.integration;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Invokes loadData.nextflow as a subprocess against the generated OXO2_CONFIG. Inherits stdout
 * and stderr so users see the same output as a manual run. Throws on non-zero exit.
 *
 * loadData.nextflow stops Solr at the end. The harness needs Solr running afterwards both
 * to query numFound and to leave it usable for backend/frontend dev work, so this class
 * restarts Solr and waits for readiness before returning.
 */
public final class Pipeline {

    private Pipeline() {}

    /** Runs loadData.nextflow against the given OXO2_CONFIG, in isolation (the run wipes
     *  $OXO2_DATA and the Solr collections, then loads only the sets that config lists). */
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

        // A previous run leaves Solr running. loadData.nextflow's copySolrConfig.sh would then
        // rm the collection data dirs while Solr still has open file handles on them, and the
        // subsequent `solr start` is a no-op because port 8983 is taken — so json2solr posts
        // documents to the soon-to-be-evicted old core and commits go to deleted files. After
        // loadData's `solr stop` + our restart, Solr opens the fresh empty config and the data
        // looks like it was never loaded. Stop any running Solr first to make the run idempotent.
        stopSolrIfRunning();

        List<String> command = new ArrayList<>();
        command.add(loadDataScript.toString());

        ProcessBuilder processBuilder = new ProcessBuilder(command)
                .directory(repoRoot.toFile())
                .inheritIO();
        processBuilder.environment().put("OXO2_CONFIG", config.toString());
        // Pick the test profile and layer in resource overrides sized for tiny fixtures.
        // The standard profile asks 16 GB per INFER_MAPPINGS task against a 26 GB executor
        // pool, which deadlocks the local scheduler on 22 fixtures.
        processBuilder.environment().put("NF_PROFILE", "test");
        processBuilder.environment().put("NF_EXTRA_CONFIG", testConfig.toString());

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

        startSolrAndWait();
    }

    /** Best-effort Solr stop: returns once $SOLR_SCRIPT/solr stop reports success, or
     *  immediately if Solr wasn't running. Errors are advisory — the next start will surface
     *  any real problem. */
    private static void stopSolrIfRunning() throws IOException, InterruptedException {
        Path solrBinary = Path.of(System.getenv(Env.SOLR_SCRIPT), "solr");
        if (!Files.isExecutable(solrBinary)) {
            throw new IllegalStateException("Solr binary not executable: " + solrBinary);
        }
        System.out.println("Stopping Solr if it is already running...");
        ProcessBuilder processBuilder = new ProcessBuilder(solrBinary.toString(), "stop", "-p", "8983")
                .redirectErrorStream(true);
        Process process = processBuilder.start();
        process.waitFor(60, TimeUnit.SECONDS);
        try (var inputStream = process.getInputStream()) {
            inputStream.transferTo(System.out);
        }
    }

    /** Starts Solr via $SOLR_SCRIPT/solr start (no-op if it's already running) and waits
     *  until the admin ping endpoint responds 200. */
    private static void startSolrAndWait() throws IOException, InterruptedException {
        Path solrBinary = Path.of(System.getenv(Env.SOLR_SCRIPT), "solr");
        if (!Files.isExecutable(solrBinary)) {
            throw new IllegalStateException("Solr binary not executable: " + solrBinary);
        }
        System.out.println("Starting Solr (in case loadData stopped it)...");
        ProcessBuilder processBuilder = new ProcessBuilder(solrBinary.toString(), "start", "--user-managed")
                .redirectErrorStream(true);
        Process process = processBuilder.start();
        // solr start is fast; treat exit code as advisory (already-running is fine).
        process.waitFor(60, TimeUnit.SECONDS);
        try (var inputStream = process.getInputStream()) {
            inputStream.transferTo(System.out);
        }
        waitForSolrReady(Duration.ofSeconds(60));
    }

    private static void waitForSolrReady(Duration timeout) throws InterruptedException {
        // /admin/cores?action=STATUS returns 200 before cores have finished loading, so the
        // first real query hits "SolrCore is loading" 503s. Probe each collection directly
        // until select?rows=0 returns 200 — that means the core has finished loading.
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3)).build();
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        String baseUrl = Env.solrHost().replaceAll("/+$", "");
        String[] collections = { "oxo2-mappings", "oxo2-mappingsets" };
        for (String collection : collections) {
            String url = baseUrl + "/" + collection + "/select?q=*:*&rows=0&wt=json";
            while (true) {
                if (System.currentTimeMillis() >= deadline) {
                    throw new IllegalStateException(
                            "Solr collection " + collection + " did not become ready at "
                                    + url + " within " + timeout);
                }
                try {
                    HttpResponse<String> response = client.send(
                            HttpRequest.newBuilder(URI.create(url))
                                    .timeout(Duration.ofSeconds(5)).GET().build(),
                            HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() == 200) {
                        break;
                    }
                } catch (Exception ignored) {
                    // Not up yet; sleep and retry.
                }
                Thread.sleep(1000);
            }
        }
        System.out.println("Solr ready at " + Env.solrHost());
    }
}
