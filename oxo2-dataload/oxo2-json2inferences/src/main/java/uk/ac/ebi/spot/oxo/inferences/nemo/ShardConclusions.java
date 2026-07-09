package uk.ac.ebi.spot.oxo.inferences.nemo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.cli.*;
import org.apache.jena.graph.Triple;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.system.StreamRDFBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.spot.oxo.inferences.nemo.model.OXOInferenceConstants;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Partitions the cross-set corpus into independently-chaseable <em>explanation shards</em>
 * (ADR-0028), so every inferred conclusion can be traced against a store of a few thousand facts
 * instead of the whole 55.9M-fact materialisation.
 *
 * <h2>Why a shard is enough</h2>
 * Every rule in {@code sssom.rls} joins its head's subject to its object through shared body
 * variables, so a derived {@code mapping(<nil>, s, p, o)} always has {@code s} and {@code o} in the
 * same connected component of the corpus's {@link OXOInferenceConstants#STRONG_PREDICATES} edges —
 * and, by induction over the same argument, so does every atom in its proof DAG. A shard corpus
 * that holds every asserted quad whose subject <em>and</em> object are in the shard therefore
 * admits exactly the same derivations for that shard's conclusions as the full corpus does.
 * {@code mapping} is derived monotonically, so a sub-corpus can never invent a conclusion either.
 *
 * <h2>Why shards are capped by entity count, not conclusion count</h2>
 * nmo's backward search re-joins against the global tables at every derivation step, so per-trace
 * cost is linear in the shard's <em>dictionary</em> size, not in its fact count: measured
 * {@code ms_per_trace ~= 0.95 + 3.47e-4 * entities} (R^2 = 0.93). Capping by entities rather than
 * by conclusions nearly halves CPU per conclusion (2.429 -> 1.325 ms, measured A/B over the same
 * components). Hence {@code --maxShardEntities}. Components are never split — a component is the
 * unit of proof closure — so a component larger than the cap becomes a shard of its own.
 *
 * <h2>Output</h2>
 * Into {@code --outputDir}, for each shard that owns at least one conclusion:
 * <ul>
 *   <li>{@code shardNNNNN.nq} — the shard's asserted quads (nmo's {@code importfile})</li>
 *   <li>{@code shardNNNNN-targets.txt} — its conclusions as one {@code ;}-separated line, the
 *       format {@code nmo --trace-input-file} expects. A newline-separated file makes nmo silently
 *       trace only the first fact.</li>
 * </ul>
 * plus {@code shards-manifest.json} describing them. Self-mappings ({@code s == o}) are skipped:
 * the inferred-mapping indexer drops them, so tracing them would be pure waste — on the dev corpus
 * that is 4.4M of 19.3M conclusions.
 */
public class ShardConclusions {

    private static final Logger logger = LoggerFactory.getLogger(ShardConclusions.class);

    /** Measured optimum on the dev corpus; see the class javadoc. */
    private static final int DEFAULT_MAX_SHARD_ENTITIES = 1200;

    /**
     * Chars of routed corpus/target text held in memory before every shard buffer is flushed.
     * Bounds heap independently of shard count, and keeps the number of open file descriptors at
     * one — a writer-per-shard would need thousands, well past a container's default {@code
     * ulimit -n}.
     */
    private static final int FLUSH_THRESHOLD_CHARS = 32 << 20;

    public static void main(String[] args) throws Exception {
        Options options = getOptions();
        CommandLine cmd;
        try {
            cmd = new DefaultParser().parse(options, args);
        } catch (ParseException e) {
            logger.error("Error parsing command line arguments", e);
            new HelpFormatter().printHelp("ShardConclusions", options);
            System.exit(1);
            return;
        }

        Path corpusFile = Path.of(cmd.getOptionValue("corpusFile"));
        Path inferencesFile = Path.of(cmd.getOptionValue("inferencesFile"));
        Path outputDir = Path.of(cmd.getOptionValue("outputDir"));
        int maxShardEntities = Integer.parseInt(
                cmd.getOptionValue("maxShardEntities", String.valueOf(DEFAULT_MAX_SHARD_ENTITIES)));
        if (maxShardEntities < 1) {
            throw new IllegalArgumentException("--maxShardEntities must be >= 1");
        }
        Files.createDirectories(outputDir);

        long startedAt = System.currentTimeMillis();
        new ShardConclusions().shard(corpusFile, inferencesFile, outputDir, maxShardEntities);
        logger.info("Sharding took {} s", (System.currentTimeMillis() - startedAt) / 1000);
    }

    // ---- interned IRI -> dense node id, plus a union-find over those ids ----

    private final Map<String, Integer> nodeIds = new HashMap<>();
    private int[] parent = new int[1 << 16];
    private int[] componentSize = new int[1 << 16];
    private int nodeCount = 0;

    private int internNode(String iri) {
        Integer existing = nodeIds.get(iri);
        if (existing != null) {
            return existing;
        }
        if (nodeCount == parent.length) {
            parent = Arrays.copyOf(parent, parent.length * 2);
            componentSize = Arrays.copyOf(componentSize, componentSize.length * 2);
        }
        parent[nodeCount] = nodeCount;
        componentSize[nodeCount] = 1;
        nodeIds.put(iri, nodeCount);
        return nodeCount++;
    }

    /** Iterative find with full path compression; recursion would blow the stack on long chains. */
    private int find(int node) {
        int root = node;
        while (parent[root] != root) {
            root = parent[root];
        }
        while (parent[node] != root) {
            int next = parent[node];
            parent[node] = root;
            node = next;
        }
        return root;
    }

    private void union(int left, int right) {
        int leftRoot = find(left);
        int rightRoot = find(right);
        if (leftRoot == rightRoot) {
            return;
        }
        if (componentSize[leftRoot] < componentSize[rightRoot]) {
            int swap = leftRoot;
            leftRoot = rightRoot;
            rightRoot = swap;
        }
        parent[rightRoot] = leftRoot;
        componentSize[leftRoot] += componentSize[rightRoot];
    }

    // ---- the four passes ----

    private void shard(Path corpusFile, Path inferencesFile, Path outputDir, int maxShardEntities)
            throws IOException {
        removeStaleShards(outputDir);
        buildComponents(corpusFile);
        int[] nodeShard = packShardsByEntityCount(maxShardEntities);
        int shardCount = Arrays.stream(nodeShard).max().orElse(-1) + 1;
        logger.info("{} nodes in {} components -> {} shards (cap {} entities)",
                nodeCount, countComponents(), shardCount, maxShardEntities);

        long[] targetsPerShard = writeTargets(inferencesFile, nodeShard, shardCount, outputDir);
        long[] quadsPerShard = writeShardCorpora(corpusFile, nodeShard, targetsPerShard, outputDir);
        writeManifest(outputDir, nodeShard, targetsPerShard, quadsPerShard, maxShardEntities);
    }

    /**
     * {@link ShardBuffers} flushes by <em>appending</em>, so a shard file left over from an earlier run
     * would be silently concatenated onto rather than replaced — producing a corpus with duplicate
     * quads and a targets file with two conclusions per line. Nextflow always hands us a fresh work
     * dir, but the subcommand is also runnable by hand; clear the slate either way.
     */
    private void removeStaleShards(Path outputDir) throws IOException {
        int removed = 0;
        try (DirectoryStream<Path> stale = Files.newDirectoryStream(outputDir, "shard*")) {
            for (Path path : stale) {
                Files.delete(path);
                removed++;
            }
        }
        if (removed > 0) {
            logger.info("Removed {} stale shard file(s) from {}", removed, outputDir);
        }
    }

    /** Pass 1: union the endpoints of every strong-predicate quad. */
    private void buildComponents(Path corpusFile) throws IOException {
        long quads = 0;
        long strongEdges = 0;
        try (BufferedReader reader = Files.newBufferedReader(corpusFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] terms = parseQuad(line);
                if (terms == null) {
                    continue;
                }
                quads++;
                if (OXOInferenceConstants.STRONG_PREDICATES.contains(terms[1])) {
                    union(internNode(terms[0]), internNode(terms[2]));
                    strongEdges++;
                }
            }
        }
        logger.info("Read {} quads; {} strong-predicate edges over {} entities",
                quads, strongEdges, nodeCount);
    }

    private int countComponents() {
        int roots = 0;
        for (int node = 0; node < nodeCount; node++) {
            if (find(node) == node) {
                roots++;
            }
        }
        return roots;
    }

    /**
     * Pass 2: greedily pack whole components into shards, largest first, until adding the next one
     * would exceed {@code maxShardEntities}. Returns {@code nodeShard[nodeId] -> shard index}.
     */
    private int[] packShardsByEntityCount(int maxShardEntities) {
        List<Integer> roots = new ArrayList<>();
        for (int node = 0; node < nodeCount; node++) {
            if (find(node) == node) {
                roots.add(node);
            }
        }
        roots.sort((left, right) -> Integer.compare(componentSize[right], componentSize[left]));

        int[] rootShard = new int[nodeCount];
        Arrays.fill(rootShard, -1);
        int shard = 0;
        int entitiesInShard = 0;
        for (int root : roots) {
            int size = componentSize[root];
            if (entitiesInShard > 0 && entitiesInShard + size > maxShardEntities) {
                shard++;
                entitiesInShard = 0;
            }
            rootShard[root] = shard;
            entitiesInShard += size;
        }

        int[] nodeShard = new int[nodeCount];
        for (int node = 0; node < nodeCount; node++) {
            nodeShard[node] = rootShard[find(node)];
        }
        return nodeShard;
    }

    /**
     * Pass 3: stream {@code inferences.ttl} and route each non-self conclusion to its shard as a
     * nil-UUID 4-arity atom. Subject and object must land in the same shard — if they ever do not,
     * the component decomposition disagrees with the ruleset and the proof would be split, so we
     * fail loudly rather than emit an unexplainable mapping.
     */
    private long[] writeTargets(Path inferencesFile, int[] nodeShard, int shardCount, Path outputDir)
            throws IOException {
        long[] targetsPerShard = new long[shardCount];
        ShardBuffers buffers = new ShardBuffers(shardCount, outputDir, "-targets.txt");
        TargetRouter router = new TargetRouter(nodeIds, nodeShard, targetsPerShard, buffers);
        RDFParser.source(inferencesFile.toString()).parse(router);
        router.rethrowIfFailed();
        buffers.flushAll();

        logger.info("Routed {} conclusions ({} self-mappings skipped) into {} non-empty shards",
                router.routed, router.selfMappings,
                Arrays.stream(targetsPerShard).filter(count -> count > 0).count());
        return targetsPerShard;
    }

    /** Pass 4: re-stream the corpus, writing each quad whose endpoints share a shard. */
    private long[] writeShardCorpora(Path corpusFile, int[] nodeShard, long[] targetsPerShard,
            Path outputDir) throws IOException {
        long[] quadsPerShard = new long[targetsPerShard.length];
        ShardBuffers buffers = new ShardBuffers(targetsPerShard.length, outputDir, ".nq");
        try (BufferedReader reader = Files.newBufferedReader(corpusFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] terms = parseQuad(line);
                if (terms == null) {
                    continue;
                }
                Integer subject = nodeIds.get(terms[0]);
                Integer object = nodeIds.get(terms[2]);
                if (subject == null || object == null) {
                    continue;
                }
                int shard = nodeShard[subject];
                if (shard != nodeShard[object] || targetsPerShard[shard] == 0) {
                    continue;
                }
                // Verbatim line: the shard corpus must be byte-equivalent to the slice of the
                // full corpus it stands for, including the mapping_id graph term (ADR-0010).
                buffers.append(shard, line);
                buffers.append(shard, "\n");
                quadsPerShard[shard]++;
            }
        }
        buffers.flushAll();
        return quadsPerShard;
    }

    private void writeManifest(Path outputDir, int[] nodeShard, long[] targetsPerShard,
            long[] quadsPerShard, int maxShardEntities) throws IOException {
        long[] entitiesPerShard = new long[targetsPerShard.length];
        for (int node = 0; node < nodeCount; node++) {
            entitiesPerShard[nodeShard[node]]++;
        }

        List<Map<String, Object>> shards = new ArrayList<>();
        long totalTargets = 0;
        for (int shard = 0; shard < targetsPerShard.length; shard++) {
            if (targetsPerShard[shard] == 0) {
                continue;
            }
            totalTargets += targetsPerShard[shard];
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("shard", shardName(shard));
            entry.put("entities", entitiesPerShard[shard]);
            entry.put("quads", quadsPerShard[shard]);
            entry.put("conclusions", targetsPerShard[shard]);
            shards.add(entry);
        }

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("maxShardEntities", maxShardEntities);
        manifest.put("entities", nodeCount);
        manifest.put("shards", shards.size());
        manifest.put("conclusions", totalTargets);
        manifest.put("perShard", shards);
        new ObjectMapper().writerWithDefaultPrettyPrinter()
                .writeValue(outputDir.resolve("shards-manifest.json").toFile(), manifest);
        logger.info("Wrote manifest: {} shards, {} conclusions", shards.size(), totalTargets);
    }

    // ---- helpers ----

    private static String shardName(int shard) {
        return String.format("shard%05d", shard);
    }

    /**
     * Extracts the four IRIs of an N-Quads line {@code <s> <p> <o> <g> .}, or null if the line is a
     * comment, blank, or not four bracketed terms. IRIs cannot contain {@code >}, so scanning
     * bracket pairs is both correct and much faster than a regex over 7.2M lines.
     */
    static String[] parseQuad(String line) {
        String[] terms = new String[4];
        int cursor = 0;
        for (int term = 0; term < 4; term++) {
            int open = line.indexOf('<', cursor);
            if (open < 0) {
                return null;
            }
            int close = line.indexOf('>', open + 1);
            if (close < 0) {
                return null;
            }
            terms[term] = line.substring(open + 1, close);
            cursor = close + 1;
        }
        return terms;
    }

    /** Jena sink that turns each inferred triple into a nil-UUID trace target. */
    private static final class TargetRouter extends StreamRDFBase {
        private final Map<String, Integer> nodeIds;
        private final int[] nodeShard;
        private final long[] targetsPerShard;
        private final ShardBuffers buffers;
        private final boolean[] shardHasTarget;
        long routed = 0;
        long selfMappings = 0;
        private RuntimeException failure = null;

        TargetRouter(Map<String, Integer> nodeIds, int[] nodeShard, long[] targetsPerShard,
                ShardBuffers buffers) {
            this.nodeIds = nodeIds;
            this.nodeShard = nodeShard;
            this.targetsPerShard = targetsPerShard;
            this.buffers = buffers;
            this.shardHasTarget = new boolean[targetsPerShard.length];
        }

        @Override
        public void triple(Triple triple) {
            if (failure != null) {
                return;
            }
            String subject = triple.getSubject().toString();
            String object = triple.getObject().toString();
            if (subject.equals(object)) {
                selfMappings++;
                return;
            }
            try {
                int shard = shardOf(subject, object);
                if (shardHasTarget[shard]) {
                    buffers.append(shard, ";");
                }
                buffers.append(shard, "mapping(<");
                buffers.append(shard, OXOInferenceConstants.NIL_MAPPING_ID_IRI);
                buffers.append(shard, ">,<");
                buffers.append(shard, subject);
                buffers.append(shard, ">,<");
                buffers.append(shard, triple.getPredicate().toString());
                buffers.append(shard, ">,<");
                buffers.append(shard, object);
                buffers.append(shard, ">)");
                shardHasTarget[shard] = true;
                targetsPerShard[shard]++;
                routed++;
            } catch (IOException e) {
                failure = new RuntimeException("Failed writing trace targets", e);
            } catch (RuntimeException e) {
                failure = e;
            }
        }

        private int shardOf(String subject, String object) {
            Integer subjectNode = nodeIds.get(subject);
            Integer objectNode = nodeIds.get(object);
            if (subjectNode == null || objectNode == null) {
                throw new IllegalStateException(
                        "Inferred conclusion has an endpoint absent from the strong-predicate graph: <"
                                + subject + "> -> <" + object + ">. STRONG_PREDICATES is out of sync "
                                + "with sssom.rls.");
            }
            int shard = nodeShard[subjectNode];
            if (shard != nodeShard[objectNode]) {
                throw new IllegalStateException(
                        "Inferred conclusion spans two shards: <" + subject + "> -> <" + object
                                + ">. Its proof would be split and the mapping left unexplained.");
            }
            return shard;
        }

        void rethrowIfFailed() {
            if (failure != null) {
                throw failure;
            }
        }
    }

    /**
     * Per-shard text buffers flushed together once {@link #FLUSH_THRESHOLD_CHARS} is exceeded, so
     * only one file descriptor is ever open. Flushes append, so a shard may be written across
     * several rounds.
     */
    private static final class ShardBuffers {
        private final StringBuilder[] buffers;
        private final Path outputDir;
        private final String suffix;
        private long bufferedChars = 0;

        ShardBuffers(int shardCount, Path outputDir, String suffix) {
            this.buffers = new StringBuilder[shardCount];
            this.outputDir = outputDir;
            this.suffix = suffix;
        }

        void append(int shard, String text) throws IOException {
            if (buffers[shard] == null) {
                buffers[shard] = new StringBuilder();
            }
            buffers[shard].append(text);
            bufferedChars += text.length();
            if (bufferedChars > FLUSH_THRESHOLD_CHARS) {
                flushAll();
            }
        }

        void flushAll() throws IOException {
            for (int shard = 0; shard < buffers.length; shard++) {
                StringBuilder buffer = buffers[shard];
                if (buffer == null || buffer.isEmpty()) {
                    continue;
                }
                File file = outputDir.resolve(shardName(shard) + suffix).toFile();
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, true))) {
                    writer.write(buffer.toString());
                }
                buffer.setLength(0);
            }
            bufferedChars = 0;
        }
    }

    private static Options getOptions() {
        Options options = new Options();

        Option corpusFile = new Option("c", "corpusFile", true,
                "The concatenated cross-set corpus (assertedCorpus.nq).");
        corpusFile.setRequired(true);
        options.addOption(corpusFile);

        Option inferencesFile = new Option("i", "inferencesFile", true,
                "Turtle file of inferred mappings (inferences.ttl) whose conclusions are to be traced.");
        inferencesFile.setRequired(true);
        options.addOption(inferencesFile);

        Option outputDir = new Option("o", "outputDir", true,
                "Directory to write shardNNNNN.nq, shardNNNNN-targets.txt and shards-manifest.json into.");
        outputDir.setRequired(true);
        options.addOption(outputDir);

        Option maxShardEntities = new Option("n", "maxShardEntities", true,
                "Cap on a shard's entity (dictionary) count — the knob that bounds per-trace cost. "
                        + "Default " + DEFAULT_MAX_SHARD_ENTITIES + ".");
        maxShardEntities.setRequired(false);
        options.addOption(maxShardEntities);

        return options;
    }
}
