package uk.ac.ebi.spot.oxo.integration;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Owns the isolated test Solr for a whole integration-test run: one {@code copySolrConfig.sh} +
 * {@code solr start} before the first fixture, a fast {@code delete *:*} clear between fixtures, and
 * one {@code solr stop} at the end (unless {@code -Doxo2.it.keepSolr=true}).
 *
 * <p>Solr stays up across all fixtures — that is the whole point: the previous design bounced Solr
 * (stop / on-disk core wipe / start / stop / restart) once per fixture, dominating the run time.
 * Because the collection wipe {@code copySolrConfig.sh} performs is a filesystem {@code rm -rf} of
 * the core dirs, it requires Solr to be down; keeping Solr up means we instead empty the collections
 * with a Solr {@code delete *:*} + hard commit between fixtures (the schema never changes between
 * fixtures, so there is no need to re-lay config).
 *
 * <p>The test Solr runs on its own port ({@link Env#solrPort()} from {@code OXO2_SOLR_HOST_TEST}) and
 * its own {@code SOLR_HOME} ({@code SOLR_HOME_TEST}), so a run never collides with or destroys a
 * developer's production Solr on 8983. See oxo2-integration-tests/CONTEXT.md § Solr lifecycle.
 */
public final class SolrLifecycle {

    private static final String[] COLLECTIONS = { "oxo2-mappings", "oxo2-mappingsets" };

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();

    private SolrLifecycle() {}

    /** Stop any prior test Solr, lay down fresh empty cores in {@code SOLR_HOME_TEST}, start Solr on
     *  the test port, and wait until both collections answer queries. Solr is down while
     *  copySolrConfig.sh runs, so its on-disk core wipe is safe here. */
    public static void startFresh() throws IOException, InterruptedException {
        stop();                 // a previous run may have left the test Solr up on this port
        copySolrConfig();       // fresh empty cores
        Files.createDirectories(Env.oxo2Data().resolve("tmp"));
        startSolr();
        for (String collection : COLLECTIONS) {
            waitForCollectionReady(collection, Duration.ofSeconds(60));
        }
        System.out.println("Test Solr ready at " + Env.solrHost());
    }

    /** Empty both collections ({@code delete *:*} + hard commit). Fast — no Solr restart. Called
     *  before every fixture's pipeline pass so each fixture's numFound is that fixture's data only. */
    public static void clearCollections() throws IOException, InterruptedException {
        String base = Env.solrHost().replaceAll("/+$", "");
        for (String collection : COLLECTIONS) {
            String url = base + "/" + collection + "/update?commit=true";
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"delete\":{\"query\":\"*:*\"}}"))
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("Failed to clear Solr collection " + collection + " ("
                        + response.statusCode() + "): " + url + "\n" + response.body());
            }
        }
        System.out.println("Cleared test Solr collections.");
    }

    /** Stop the test Solr on its port. Best-effort: a no-op if it is not running. */
    public static void stop() throws IOException, InterruptedException {
        System.out.println("Stopping test Solr on port " + Env.solrPort() + " if running...");
        runSolr("stop", "-p", Integer.toString(Env.solrPort()));
    }

    private static void startSolr() throws IOException, InterruptedException {
        System.out.println("Starting test Solr on port " + Env.solrPort()
                + " (SOLR_HOME=" + Env.solrHome() + ")...");
        runSolr("start", "--user-managed",
                "-p", Integer.toString(Env.solrPort()),
                "-Djava.io.tmpdir=" + Env.oxo2Data().resolve("tmp"));
    }

    /** Runs {@code $SOLR_SCRIPT/solr <args>} with SOLR_HOME pinned to the test data dir, inheriting
     *  output. solr start/stop are fast and quiet; exit status is advisory (already-running /
     *  not-running are both fine), so the readiness probe in {@link #startFresh()} is the real gate. */
    private static void runSolr(String... args) throws IOException, InterruptedException {
        Path solr = Path.of(Env.solrScript(), "solr");
        if (!Files.isExecutable(solr)) {
            throw new IllegalStateException("Solr binary not executable: " + solr);
        }
        java.util.List<String> command = new java.util.ArrayList<>();
        command.add(solr.toString());
        for (String arg : args) command.add(arg);
        ProcessBuilder processBuilder = new ProcessBuilder(command).redirectErrorStream(true);
        processBuilder.environment().put("SOLR_HOME", Env.solrHome().toString());
        Process process = processBuilder.start();
        process.waitFor(60, TimeUnit.SECONDS);
        try (var inputStream = process.getInputStream()) {
            inputStream.transferTo(System.out);
        }
    }

    /** Lays down a fresh Solr config + empty cores in {@code SOLR_HOME_TEST} via the same
     *  copySolrConfig.sh the dataload uses. Solr must be down (it {@code rm -rf}s the core dirs). */
    private static void copySolrConfig() throws IOException, InterruptedException {
        Path script = Env.repoRoot().resolve("oxo2-dataload").resolve("copySolrConfig.sh");
        if (!Files.isExecutable(script)) {
            throw new IllegalStateException("copySolrConfig.sh not executable or missing: " + script);
        }
        System.out.println("Laying down fresh Solr config + empty cores in " + Env.solrHome() + "...");
        ProcessBuilder processBuilder = new ProcessBuilder(script.toString()).redirectErrorStream(true);
        processBuilder.environment().put("SOLR_HOME", Env.solrHome().toString());
        Process process = processBuilder.start();
        boolean finished = process.waitFor(60, TimeUnit.SECONDS);
        try (var inputStream = process.getInputStream()) {
            inputStream.transferTo(System.out);
        }
        if (!finished || process.exitValue() != 0) {
            throw new IllegalStateException("copySolrConfig.sh failed (SOLR_HOME=" + Env.solrHome() + ").");
        }
    }

    private static void waitForCollectionReady(String collection, Duration timeout)
            throws InterruptedException {
        // /admin/cores STATUS returns 200 before a core finishes loading, so the first real query
        // hits "SolrCore is loading" 503s. Probe the collection's /select directly until it 200s.
        String url = Env.solrHost().replaceAll("/+$", "")
                + "/" + collection + "/select?q=*:*&rows=0&wt=json";
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (true) {
            if (System.currentTimeMillis() >= deadline) {
                throw new IllegalStateException("Test Solr collection " + collection
                        + " did not become ready at " + url + " within " + timeout);
            }
            try {
                HttpResponse<String> response = HTTP.send(
                        HttpRequest.newBuilder(URI.create(url))
                                .timeout(Duration.ofSeconds(5)).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return;
                }
            } catch (Exception ignored) {
                // Not up yet; sleep and retry.
            }
            Thread.sleep(1000);
        }
    }
}
