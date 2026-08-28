package uk.ac.ebi.spot.oxo.inferences.nemo.helpers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.spot.oxo.inferences.nemo.model.OXOInferenceConstants;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The asserted quads of a shard corpus, indexed by their (subject, predicate, object) IRIs
 * (ADR-0049). The corpus {@code .nq} files are the ground truth for which asserted mappings
 * participated in inference — every inclusion rule (applicable predicate, no predicate modifier,
 * valid IRIs, the ADR-0037 confidence gate) has already been applied by the time a quad exists —
 * so reading them back needs no second copy of those rules.
 *
 * <p>The same triple asserted in more than one mapping set produces several corpus quads that are
 * byte-identical in subject/predicate/object and differ only in the {@code mapping_id} graph term
 * (ADR-0010). Nemo's trace carries whichever one its derivation happened to use; this index
 * recovers <em>all</em> of them, so an asserted leaf can be canonicalised (lowest mapping_id) and
 * expanded to its full evidence set deterministically.
 *
 * <p>Explanation shards are disjoint strong-predicate components (ADR-0028), so no (s, p, o) can
 * occur in two shards and one merged index safely serves a whole bundle.
 */
public final class ShardAssertedIndex {

    private static final Logger logger = LoggerFactory.getLogger(ShardAssertedIndex.class);

    /** Directory holding the shard {@code .nq} files; set by explanations2json.nf. */
    public static final String SHARDS_DIR_ENV = "OXO2_SHARDS_DIR";

    private static final String CHAINS_SUFFIX = "-chains.json";
    private static final String SHARD_SUFFIX = ".nq";
    private static final char KEY_SEPARATOR = '';

    /** (s, p, o) IRI key → bare mapping_ids of every corpus quad with that triple, sorted. */
    private final Map<String, List<String>> mappingIdsByTriple;

    private ShardAssertedIndex(Map<String, List<String>> mappingIdsByTriple) {
        this.mappingIdsByTriple = mappingIdsByTriple;
    }

    /**
     * Load the shard corpora backing a bundle of chain files, resolving each chain file's
     * {@code .nq} via {@link #locateShardFileFor}. Returns {@code null} when no shard file
     * resolves at all — the caller then behaves exactly as before ADR-0049 (each leaf keeps the
     * trace's own mapping_id), which keeps old on-disk layouts working. A partially resolvable
     * bundle is loaded with a WARN per missing file.
     */
    public static ShardAssertedIndex loadForChainFiles(String[] chainFilePaths) {
        Map<String, List<String>> mappingIdsByTriple = new HashMap<>();
        int resolvedFiles = 0;
        for (String chainFilePath : chainFilePaths) {
            Path shardFile = locateShardFileFor(Path.of(chainFilePath));
            if (shardFile == null) {
                logger.warn("No shard corpus (.nq) found for chain file {} — its asserted leaves "
                        + "will keep the trace's own mapping_id instead of being expanded to all "
                        + "corpus duplicates (ADR-0049). Set {} to the shards directory.",
                        chainFilePath, SHARDS_DIR_ENV);
                continue;
            }
            try {
                loadShardFile(shardFile, mappingIdsByTriple);
                resolvedFiles++;
            } catch (IOException e) {
                logger.warn("Failed to read shard corpus {} for chain file {}; its asserted leaves "
                        + "will keep the trace's own mapping_id.", shardFile, chainFilePath, e);
            }
        }
        if (resolvedFiles == 0) {
            return null;
        }
        for (List<String> mappingIds : mappingIdsByTriple.values()) {
            Collections.sort(mappingIds);
        }
        logger.info("Shard corpus index: {} distinct (s, p, o) triples from {} of {} shard file(s)",
                mappingIdsByTriple.size(), resolvedFiles, chainFilePaths.length);
        return new ShardAssertedIndex(mappingIdsByTriple);
    }

    /**
     * The {@code .nq} for {@code shardNNNNN-chains.json} is {@code shardNNNNN.nq}, looked for in:
     * the {@value #SHARDS_DIR_ENV} directory (how the pipeline runs — Nextflow stages chain files
     * into a work dir away from the shards), then next to the chain file itself, then in the
     * {@code shards} directory sibling to the chain file's own directory (the on-disk layout,
     * {@code crossSet/shardChains} beside {@code crossSet/shards} — how a local debug run finds it).
     */
    static Path locateShardFileFor(Path chainFile) {
        String chainFileName = chainFile.getFileName().toString();
        if (!chainFileName.endsWith(CHAINS_SUFFIX)) {
            logger.warn("Chain file {} does not follow the <shard>-chains.json naming convention; "
                    + "cannot locate its shard corpus.", chainFile);
            return null;
        }
        String shardFileName =
                chainFileName.substring(0, chainFileName.length() - CHAINS_SUFFIX.length())
                + SHARD_SUFFIX;

        String shardsDirFromEnv = System.getenv(SHARDS_DIR_ENV);
        if (shardsDirFromEnv != null && !shardsDirFromEnv.isBlank()) {
            Path candidate = Path.of(shardsDirFromEnv).resolve(shardFileName);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        Path chainDir = chainFile.toAbsolutePath().getParent();
        if (chainDir != null) {
            Path candidate = chainDir.resolve(shardFileName);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            Path candidateInSiblingShardsDir = chainDir.resolveSibling("shards").resolve(shardFileName);
            if (Files.isRegularFile(candidateInSiblingShardsDir)) {
                return candidateInSiblingShardsDir;
            }
        }
        return null;
    }

    /**
     * Parse one shard corpus. Each line is {@code <s> <p> <o> <urn:uuid:mapping_id> .} as written
     * by JSON2NQuads; a line that does not have exactly four IRI terms is skipped with a WARN
     * rather than aborting the shard.
     */
    private static void loadShardFile(Path shardFile, Map<String, List<String>> mappingIdsByTriple)
            throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(shardFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                int firstAngle = line.indexOf('<');
                int lastAngle = line.lastIndexOf('>');
                if (firstAngle < 0 || lastAngle <= firstAngle) {
                    logger.warn("Skipping malformed quad in {}: {}", shardFile, line);
                    continue;
                }
                String[] terms = line.substring(firstAngle + 1, lastAngle).split(">\\s*<");
                if (terms.length != 4) {
                    logger.warn("Skipping quad without 4 IRI terms in {}: {}", shardFile, line);
                    continue;
                }
                String bareMappingId = OXOInferenceConstants.toBareMappingId(terms[3]);
                mappingIdsByTriple
                        .computeIfAbsent(tripleKey(terms[0], terms[1], terms[2]),
                                key -> new ArrayList<>(1))
                        .add(bareMappingId);
            }
        }
    }

    /**
     * The bare mapping_ids of every corpus quad carrying exactly this (s, p, o), sorted; empty when
     * the triple is not in any loaded shard.
     */
    public List<String> idsFor(String subjectIRI, String predicateIRI, String objectIRI) {
        List<String> mappingIds = mappingIdsByTriple.get(tripleKey(subjectIRI, predicateIRI, objectIRI));
        return mappingIds == null ? List.of() : mappingIds;
    }

    /** Every bare mapping_id in the loaded corpora — the ids a bulk Solr prefetch should cover. */
    public Set<String> allMappingIds() {
        Set<String> allMappingIds = new TreeSet<>();
        for (Collection<String> mappingIds : mappingIdsByTriple.values()) {
            allMappingIds.addAll(mappingIds);
        }
        return allMappingIds;
    }

    private static String tripleKey(String subjectIRI, String predicateIRI, String objectIRI) {
        return subjectIRI + KEY_SEPARATOR + predicateIRI + KEY_SEPARATOR + objectIRI;
    }
}
