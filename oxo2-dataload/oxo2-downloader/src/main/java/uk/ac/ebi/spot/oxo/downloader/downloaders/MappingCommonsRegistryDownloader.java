package uk.ac.ebi.spot.oxo.downloader.downloaders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.spot.oxo.downloader.util.SafeFilename;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Consumes a Mapping Commons aggregated registry catalogue: the {@code mapping-specifications.json}
 * published by {@code mapping-commons.github.io}, a flat JSON array of mapping-set entries each
 * carrying a {@code content_url} that points at a SSSOM TSV (possibly gzipped). Rather than crawling
 * the registry-of-registries by hand, the downloader reads this pre-aggregated catalogue, filters it
 * to the SSSOM mapping sets worth ingesting, and downloads each one. See ADR-0014.
 *
 * <p>Selection rules (all enforced in the pure, unit-tested {@link #select}):
 * <ul>
 *   <li>only {@code type == "sssom"} entries with a non-empty {@code content_url};</li>
 *   <li>the FAIR-schema transform registry ({@link #TRANSFORMS_REGISTRY_ID}) is dropped — its
 *       entries are LinkML slot-mappings, not ontology mappings;</li>
 *   <li>basenames listed in the registry's {@code exclude} config are dropped (used to keep the
 *       closure-exploding SeMRA {@code processed}/{@code raw} assemblies out of the corpus);</li>
 *   <li>entries are namespaced into a per-source-registry subdirectory so identical filenames from
 *       different registries (e.g. {@code mondo_hasdbxref_hp.sssom.tsv} in both Monarch and C-Path)
 *       do not collide;</li>
 *   <li>distinct sets that nonetheless share a filename <em>within</em> a registry are kept and
 *       disambiguated by a per-set subdirectory (the biopragmatics SeMRA landscapes each publish a
 *       {@code priority.sssom.tsv.gz} under a different Zenodo record — they are different content,
 *       not versions). The aggregated catalogue drops each set's {@code mapping_set_group}, so the
 *       subdirectory name is fetched from the owning source {@code registry.yml} (gene/cell/protein/
 *       anatomy/disease), falling back to the Zenodo record id if that lookup is unavailable; only an
 *       exact-duplicate {@code content_url} is dropped.</li>
 * </ul>
 *
 * <p>Gzipped entries ({@code *.gz}) are decompressed on download so the {@code .tsv} lands where the
 * downstream {@code sssom2json.nf} {@code **.tsv} glob can see it.
 */
public class MappingCommonsRegistryDownloader {

    private static final Logger logger = LoggerFactory.getLogger(MappingCommonsRegistryDownloader.class);

    /** Registry whose entries are FAIR-schema slot-mapping transforms, not ontology mappings; always skipped. */
    static final String TRANSFORMS_REGISTRY_ID = "https://w3id.org/mapping-commons/transforms";

    /** Matches the numeric record id in a Zenodo URL ({@code /records/<n>/} or legacy {@code /record/<n>/}). */
    private static final Pattern ZENODO_RECORD = Pattern.compile("/records?/(\\d+)/");

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final YAMLMapper YAML_MAPPER = new YAMLMapper();

    /** Candidate filenames for a SSSOM mapping registry, in the order the spec uses them. */
    private static final List<String> REGISTRY_FILE_NAMES = List.of("registry.yml", "mappings.yml");

    /** HTTP statuses worth retrying: rate-limiting plus the transient 5xx gateway/server errors. */
    private static final Set<Integer> RETRYABLE_STATUS = Set.of(429, 500, 502, 503, 504);

    private static final int MAX_ATTEMPTS = 4;
    private static final long INITIAL_BACKOFF_MILLIS = 2_000L;
    private static final long MAX_BACKOFF_MILLIS = 30_000L;

    private MappingCommonsRegistryDownloader() {}

    /**
     * A single mapping set selected for download. {@code discriminator} is a per-set subdirectory used
     * only when several distinct sets in the same registry share a {@code fileName} (otherwise null); the
     * on-disk location is {@code registrySlug[/discriminator]/fileName}.
     */
    public record SelectedMapping(String contentUrl, String registrySlug, String discriminator,
                                  String fileName, boolean gzipped) {

        /** Directory (relative to the registry's download root) this set is written into. */
        public String targetDir() {
            return discriminator == null ? registrySlug : registrySlug + "/" + discriminator;
        }
    }

    /**
     * Pure selection step with no landscape enrichment: equivalent to {@link #select(JsonNode, Set, Map)}
     * with an empty landscape map, so same-filename collisions fall back to the Zenodo record id. Kept as
     * the simple, network-free entry point for unit tests.
     */
    public static List<SelectedMapping> select(JsonNode catalogue, Set<String> excludeBasenames) {
        return select(catalogue, excludeBasenames, Map.of());
    }

    /**
     * Pure selection step: filter, namespace, and disambiguate the catalogue entries. No network I/O,
     * so it is exhaustively unit-testable. {@code excludeBasenames} are matched against the basename of
     * each entry's {@code content_url} (i.e. the gzipped name, e.g. {@code processed.sssom.tsv.gz}).
     *
     * <p>Distinct sets sharing a filename within a registry are all kept; only an exact-duplicate
     * {@code content_url} is dropped. {@code landscapeByUrl} maps a {@code content_url} to a friendly
     * disambiguation name (e.g. the biopragmatics {@code mapping_set_group}: gene/cell/…); where present
     * it is preferred over the Zenodo record id when naming the per-set subdirectory.
     */
    public static List<SelectedMapping> select(JsonNode catalogue, Set<String> excludeBasenames,
                                               Map<String, String> landscapeByUrl) {
        return assignDiscriminators(rawSelections(catalogue, excludeBasenames), landscapeByUrl);
    }

    /**
     * The filtering pass: drops non-SSSOM, empty-URL, transform-registry, {@code exclude}d, exact-duplicate,
     * and unsafe-named entries, returning the survivors in catalogue order. Pure; used both by
     * {@link #select} and by {@code DownloadRegistryTask} to discover which registries need landscape
     * enrichment.
     */
    static List<RawSelection> rawSelections(JsonNode catalogue, Set<String> excludeBasenames) {
        if (catalogue == null || !catalogue.isArray()) {
            return List.of();
        }
        List<RawSelection> raw = new ArrayList<>();
        Set<String> seenUrls = new HashSet<>();
        for (JsonNode entry : catalogue) {
            if (!"sssom".equals(entry.path("type").asText(""))) {
                continue;
            }
            String contentUrl = entry.path("content_url").asText("").trim();
            if (contentUrl.isEmpty()) {
                continue;
            }
            if (belongsToTransformsRegistry(entry)) {
                continue;
            }
            String sourceBasename = basename(contentUrl);
            if (sourceBasename.isEmpty()) {
                logger.warn("Skipping registry entry with no derivable filename: {}", contentUrl);
                continue;
            }
            if (excludeBasenames.contains(sourceBasename)) {
                logger.info("Excluding registry entry {} (matched exclude list)", sourceBasename);
                continue;
            }
            if (!seenUrls.add(contentUrl)) {
                logger.debug("Skipping exact-duplicate content_url {}", contentUrl);
                continue;
            }
            boolean gzipped = sourceBasename.endsWith(".gz");
            String fileName = gzipped ? sourceBasename.substring(0, sourceBasename.length() - ".gz".length())
                    : sourceBasename;
            String slug = registrySlug(entry);

            if (!SafeFilename.isSafe(fileName) || !SafeFilename.isSafe(slug)) {
                logger.error("Skipping registry entry with unsafe name (slug={}, file={}) from {}",
                        slug, fileName, contentUrl);
                continue;
            }
            raw.add(new RawSelection(contentUrl, slug, fileName, gzipped, registryRepoUrl(entry)));
        }
        return raw;
    }

    /**
     * Groups by {@code slug/fileName}; singletons keep a null discriminator, while a group of distinct
     * sets sharing a filename each gets a unique subdirectory: the friendly landscape name from
     * {@code landscapeByUrl} if available, else the Zenodo record id, else a positional {@code set-N}.
     * Original order is preserved.
     */
    static List<SelectedMapping> assignDiscriminators(List<RawSelection> raw,
                                                      Map<String, String> landscapeByUrl) {
        Map<String, List<RawSelection>> groups = new LinkedHashMap<>();
        for (RawSelection selection : raw) {
            groups.computeIfAbsent(selection.slug + "/" + selection.fileName, key -> new ArrayList<>())
                    .add(selection);
        }
        // content_url is unique after the seenUrls de-dup, so it is a safe key for the discriminator map.
        Map<String, String> discriminatorByUrl = new HashMap<>();
        for (List<RawSelection> group : groups.values()) {
            if (group.size() == 1) {
                continue;
            }
            Set<String> usedTokens = new HashSet<>();
            for (int index = 0; index < group.size(); index++) {
                RawSelection selection = group.get(index);
                String token = preferredToken(selection.contentUrl, landscapeByUrl);
                if (token == null || !SafeFilename.isSafe(token)) {
                    token = "set-" + (index + 1);
                }
                String uniqueToken = token;
                for (int suffix = 2; !usedTokens.add(uniqueToken); suffix++) {
                    uniqueToken = token + "-" + suffix;
                }
                discriminatorByUrl.put(selection.contentUrl, uniqueToken);
                logger.info("Disambiguating colliding {}/{} -> {}/{}/{} ({})",
                        selection.slug, selection.fileName, selection.slug, uniqueToken, selection.fileName,
                        selection.contentUrl);
            }
        }
        List<SelectedMapping> selected = new ArrayList<>();
        for (RawSelection selection : raw) {
            selected.add(new SelectedMapping(selection.contentUrl, selection.slug,
                    discriminatorByUrl.get(selection.contentUrl), selection.fileName, selection.gzipped));
        }
        return selected;
    }

    /** Landscape name if the source registry supplied one, else the Zenodo record id. */
    private static String preferredToken(String contentUrl, Map<String, String> landscapeByUrl) {
        String landscape = landscapeByUrl.get(contentUrl);
        if (landscape != null && !landscape.isBlank()) {
            return sanitiseSegment(landscape);
        }
        return disambiguationToken(contentUrl);
    }

    record RawSelection(String contentUrl, String slug, String fileName, boolean gzipped, String registryRepoUrl) {}

    private static boolean belongsToTransformsRegistry(JsonNode entry) {
        JsonNode registries = entry.path("registries");
        if (registries.isArray()) {
            for (JsonNode registry : registries) {
                if (TRANSFORMS_REGISTRY_ID.equals(registry.path("id").asText(""))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The entry's source registry id (a repo URL, e.g. {@code https://github.com/biopragmatics/mapping-registry}). */
    private static String registryRepoUrl(JsonNode entry) {
        JsonNode registries = entry.path("registries");
        if (registries.isArray() && !registries.isEmpty()) {
            return registries.get(0).path("id").asText("");
        }
        return "";
    }

    /**
     * Derive a filesystem-safe subdirectory name for the entry's source registry from the last path
     * segment of its registry id (e.g. {@code .../commons/mouse-human} -> {@code mouse-human}).
     */
    private static String registrySlug(JsonNode entry) {
        String sanitised = sanitiseSegment(basename(registryRepoUrl(entry)));
        return sanitised.isEmpty() ? "unknown" : sanitised;
    }

    /** Replace any character outside the {@link SafeFilename} allowlist with {@code -}, and drop a leading {@code .}/{@code -}. */
    private static String sanitiseSegment(String segment) {
        if (segment == null) {
            return "";
        }
        return segment.replaceAll("[^A-Za-z0-9._-]", "-").replaceFirst("^[.-]+", "");
    }

    /** Last path segment of a URL, with any query string already excluded by URI parsing. */
    private static String basename(String url) {
        String path;
        try {
            path = new URI(url).getPath();
        } catch (URISyntaxException e) {
            path = url;
        }
        if (path == null || path.isEmpty()) {
            return "";
        }
        int lastSlash = path.lastIndexOf('/');
        return lastSlash == -1 ? path : path.substring(lastSlash + 1);
    }

    /**
     * A short, filesystem-safe token that distinguishes one set from another sharing its filename: the
     * Zenodo record id when present (e.g. the biopragmatics per-landscape deposits), else the parent
     * path segment of the URL. May return null if nothing usable is found (caller falls back to an index).
     */
    private static String disambiguationToken(String url) {
        Matcher matcher = ZENODO_RECORD.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }
        String path;
        try {
            path = new URI(url).getPath();
        } catch (URISyntaxException e) {
            return null;
        }
        if (path == null) {
            return null;
        }
        String[] segments = path.split("/");
        // segments[last] is the filename; walk back to the first non-empty parent segment.
        for (int index = segments.length - 2; index >= 0; index--) {
            String sanitised = segments[index].replaceAll("[^A-Za-z0-9._-]", "-").replaceFirst("^[.-]+", "");
            if (!sanitised.isEmpty()) {
                return sanitised;
            }
        }
        return null;
    }

    /** A transient failure (retryable status, or a network-level I/O error) worth re-attempting. */
    static final class RetryableDownloadException extends IOException {
        RetryableDownloadException(String message) {
            super(message);
        }

        RetryableDownloadException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** A single attempt at an HTTP fetch; may throw {@link RetryableDownloadException} for transient failures. */
    @FunctionalInterface
    interface Fetch<T> {
        T attempt() throws IOException;
    }

    /**
     * Runs {@code fetch}, retrying on {@link RetryableDownloadException} with exponential backoff up to
     * {@code maxAttempts}. Non-retryable {@link IOException}s (e.g. a permanent 404) propagate immediately.
     * {@code initialBackoffMillis} is a parameter so tests can disable the sleep.
     */
    static <T> T withRetry(String what, int maxAttempts, long initialBackoffMillis, Fetch<T> fetch)
            throws IOException {
        long backoffMillis = initialBackoffMillis;
        for (int attempt = 1; ; attempt++) {
            try {
                return fetch.attempt();
            } catch (RetryableDownloadException e) {
                if (attempt >= maxAttempts) {
                    throw new IOException("Giving up on " + what + " after " + attempt + " attempts", e);
                }
                logger.warn("Transient failure for {} (attempt {}/{}): {}; retrying in {} ms",
                        what, attempt, maxAttempts, e.getMessage(), backoffMillis);
                sleep(backoffMillis);
                backoffMillis = Math.min(backoffMillis * 2, MAX_BACKOFF_MILLIS);
            }
        }
    }

    private static void sleep(long millis) throws IOException {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while backing off before retry", e);
        }
    }

    /**
     * Executes a GET and hands the successful (200) response body to {@code handler}. A retryable status
     * becomes a {@link RetryableDownloadException}; any other non-200 a plain {@link IOException}; a
     * network-level {@link IOException} (reset, read timeout, premature EOF) is reclassified as retryable.
     */
    private static <T> T httpGet(String url, BodyHandler<T> handler) throws IOException {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(url);
            request.addHeader("User-Agent", "oxo2-downloader");
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                int statusCode = response.getCode();
                if (statusCode != 200) {
                    String message = "HTTP GET failed for " + url + " (status=" + statusCode + ")";
                    if (RETRYABLE_STATUS.contains(statusCode)) {
                        throw new RetryableDownloadException(message);
                    }
                    throw new IOException(message);
                }
                try (InputStream body = response.getEntity().getContent()) {
                    return handler.handle(body);
                }
            }
        } catch (RetryableDownloadException | RuntimeException e) {
            throw e;
        } catch (IOException e) {
            throw new RetryableDownloadException("I/O error during GET " + url + ": " + e.getMessage(), e);
        }
    }

    @FunctionalInterface
    private interface BodyHandler<T> {
        T handle(InputStream body) throws IOException;
    }

    /** Fetches and parses a catalogue JSON document, retrying transient failures. */
    static JsonNode fetchCatalogue(String registryUrl) throws IOException {
        logger.debug("Fetching Mapping Commons registry catalogue {}", registryUrl);
        return withRetry(registryUrl, MAX_ATTEMPTS, INITIAL_BACKOFF_MILLIS,
                () -> httpGet(registryUrl, OBJECT_MAPPER::readTree));
    }

    /** Downloads one mapping set to {@code target}, gunzipping on the fly, retrying transient failures. */
    static void downloadMappingSet(String url, Path target, boolean gzipped) throws IOException {
        withRetry(url, MAX_ATTEMPTS, INITIAL_BACKOFF_MILLIS, () -> httpGet(url, body -> {
            try (InputStream in = gzipped ? new GzipCompressorInputStream(body) : body;
                 OutputStream out = Files.newOutputStream(target)) {
                in.transferTo(out);
            }
            return null;
        }));
    }

    /**
     * Best-effort landscape enrichment. The aggregated catalogue drops each set's {@code mapping_set_group},
     * so for every same-filename collision this fetches the owning source registry's {@code registry.yml}
     * and maps each colliding {@code content_url} to its group (e.g. the biopragmatics gene/cell/protein/
     * anatomy/disease landscapes). Any failure degrades silently to the Zenodo-record-id fallback, so a
     * missing, unreachable, or non-GitHub registry never blocks the download. Returns {@code content_url ->
     * landscape name} for the colliding sets only.
     */
    static Map<String, String> resolveLandscapes(List<RawSelection> raw) {
        Map<String, List<RawSelection>> groups = new LinkedHashMap<>();
        for (RawSelection selection : raw) {
            groups.computeIfAbsent(selection.slug() + "/" + selection.fileName(), key -> new ArrayList<>())
                    .add(selection);
        }
        Set<String> collidingRepos = new LinkedHashSet<>();
        for (List<RawSelection> group : groups.values()) {
            if (group.size() > 1) {
                for (RawSelection selection : group) {
                    if (selection.registryRepoUrl() != null && !selection.registryRepoUrl().isBlank()) {
                        collidingRepos.add(selection.registryRepoUrl());
                    }
                }
            }
        }
        Map<String, String> landscapeByUrl = new HashMap<>();
        for (String repoUrl : collidingRepos) {
            try {
                landscapeByUrl.putAll(fetchRegistryGroups(repoUrl));
            } catch (IOException e) {
                logger.warn("Could not resolve landscape names from registry {} ({}); "
                        + "falling back to record ids", repoUrl, e.getMessage());
            }
        }
        return landscapeByUrl;
    }

    /** Fetches a source registry's {@code registry.yml}/{@code mappings.yml} and extracts its group names. */
    private static Map<String, String> fetchRegistryGroups(String repoUrl) throws IOException {
        List<String> candidateUrls = rawRegistryUrls(repoUrl);
        IOException lastFailure = null;
        for (String rawUrl : candidateUrls) {
            try {
                JsonNode registry = withRetry(rawUrl, MAX_ATTEMPTS, INITIAL_BACKOFF_MILLIS,
                        () -> httpGet(rawUrl, YAML_MAPPER::readTree));
                Map<String, String> groups = parseRegistryGroups(registry);
                if (!groups.isEmpty()) {
                    logger.info("Resolved {} landscape name(s) from {}", groups.size(), rawUrl);
                    return groups;
                }
            } catch (IOException e) {
                lastFailure = e;  // e.g. registry.yml is absent (404); try the next candidate name
            }
        }
        if (lastFailure != null) {
            throw lastFailure;
        }
        return Map.of();
    }

    /**
     * Maps {@code mapping_set_id -> mapping_set_group} from a parsed SSSOM mapping-registry document. The
     * registry's {@code mapping_set_id} equals the catalogue's {@code content_url}, so the result keys join
     * straight onto the selections. Pure (operates on an already-parsed tree).
     */
    static Map<String, String> parseRegistryGroups(JsonNode registry) {
        Map<String, String> groups = new HashMap<>();
        JsonNode references = registry.path("mapping_set_references");
        if (references.isArray()) {
            for (JsonNode reference : references) {
                String mappingSetId = reference.path("mapping_set_id").asText("").trim();
                String group = reference.path("mapping_set_group").asText("").trim();
                if (!mappingSetId.isEmpty() && !group.isEmpty()) {
                    groups.put(mappingSetId, group);
                }
            }
        }
        return groups;
    }

    /**
     * The {@code raw.githubusercontent.com} URLs to try for a GitHub repo's registry file (at {@code HEAD},
     * so the default branch is followed). Non-GitHub repos (e.g. GitLab) yield none — those fall back to
     * record-id disambiguation.
     */
    private static List<String> rawRegistryUrls(String repoUrl) {
        String marker = "github.com/";
        int markerIndex = repoUrl == null ? -1 : repoUrl.indexOf(marker);
        if (markerIndex < 0) {
            return List.of();
        }
        String[] ownerRepo = repoUrl.substring(markerIndex + marker.length()).split("/");
        if (ownerRepo.length < 2 || ownerRepo[0].isBlank() || ownerRepo[1].isBlank()) {
            return List.of();
        }
        String base = "https://raw.githubusercontent.com/" + ownerRepo[0] + "/" + ownerRepo[1] + "/HEAD/";
        List<String> urls = new ArrayList<>();
        for (String fileName : REGISTRY_FILE_NAMES) {
            urls.add(base + fileName);
        }
        return urls;
    }

    /**
     * Fetches and parses the catalogue, then submits one download task per selected mapping set.
     * Returns the spawned futures so {@code DownloadMappings} can wait on them (mirrors
     * {@link GitHubDownloader.DownloadGithubDirectoryTask}).
     */
    public static class DownloadRegistryTask implements Callable<Collection<Future>> {

        private final ExecutorService executorService;
        private final String registryUrl;
        private final Set<String> excludeBasenames;
        private final String destination;

        public DownloadRegistryTask(ExecutorService executorService, String registryUrl,
                                    List<String> exclude, String destination) {
            this.executorService = executorService;
            this.registryUrl = registryUrl;
            this.excludeBasenames = exclude == null ? Set.of() : Set.copyOf(exclude);
            this.destination = destination;
        }

        @Override
        public Collection<Future> call() {
            Collection<Future> futures = new LinkedList<>();
            try {
                JsonNode catalogue = fetchCatalogue(registryUrl);
                List<RawSelection> raw = rawSelections(catalogue, excludeBasenames);
                Map<String, String> landscapeByUrl = resolveLandscapes(raw);
                List<SelectedMapping> selected = assignDiscriminators(raw, landscapeByUrl);
                logger.info("Mapping Commons registry {}: {} SSSOM mapping sets selected for download",
                        registryUrl, selected.size());
                Files.createDirectories(Paths.get(destination));
                for (SelectedMapping mapping : selected) {
                    futures.add(executorService.submit(new DownloadEntryTask(mapping, destination)));
                }
            } catch (IOException e) {
                logger.error("Error consuming Mapping Commons registry {}", registryUrl, e);
            }
            return futures;
        }
    }

    /** Downloads one selected mapping set, gunzipping on the fly when the source is gzipped. */
    static class DownloadEntryTask implements Runnable {

        private final SelectedMapping mapping;
        private final String destination;

        DownloadEntryTask(SelectedMapping mapping, String destination) {
            this.mapping = mapping;
            this.destination = destination;
        }

        @Override
        public void run() {
            try {
                Path destinationRoot = Paths.get(destination).toAbsolutePath().normalize();
                Path target = destinationRoot;
                for (String segment : mapping.targetDir().split("/")) {
                    target = target.resolve(segment);
                }
                target = target.resolve(mapping.fileName()).normalize();
                // Defence in depth: select() already validated every segment via SafeFilename.
                if (!target.startsWith(destinationRoot)) {
                    logger.error("Refusing to write outside destination directory: {}", target);
                    return;
                }
                Files.createDirectories(target.getParent());
                downloadMappingSet(mapping.contentUrl(), target, mapping.gzipped());
                logger.debug("Downloaded {} -> {}", mapping.contentUrl(), target);
            } catch (Exception e) {
                logger.error("Error downloading mapping set {} from {}",
                        mapping.fileName(), mapping.contentUrl(), e);
            }
        }
    }
}
