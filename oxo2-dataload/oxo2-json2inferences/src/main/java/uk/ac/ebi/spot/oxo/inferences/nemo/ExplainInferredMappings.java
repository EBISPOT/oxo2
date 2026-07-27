package uk.ac.ebi.spot.oxo.inferences.nemo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SequenceWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.spot.oxo.dataload.solr.DataloadSolr;
import uk.ac.ebi.spot.oxo.dataload.solr.EntityDetails;
import uk.ac.ebi.spot.oxo.inferences.nemo.helpers.NemoHelper;
import uk.ac.ebi.spot.oxo.inferences.nemo.model.NemoInferences;
import uk.ac.ebi.spot.oxo.inferences.nemo.model.OXOInferenceConstants;
import uk.ac.ebi.spot.oxo.model.sssom.ChainRulesEnum;
import uk.ac.ebi.spot.oxo.model.sssom.InferenceType;
import uk.ac.ebi.spot.oxo.model.sssom.EntityReference;
import uk.ac.ebi.spot.oxo.model.sssom.InferredMapping;
import uk.ac.ebi.spot.oxo.model.sssom.Mapping;
import uk.ac.ebi.spot.oxo.model.sssom.MappingSet;
import uk.ac.ebi.spot.oxo.model.sssom.PrefixMap;
import uk.ac.ebi.spot.oxo.model.sssom.Uri;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.*;

public class ExplainInferredMappings {
    private static final Logger logger = LoggerFactory.getLogger(ExplainInferredMappings.class);

    public static void main(String[] args) {
        Options options = getOptions();
        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            logger.error("Error parsing command line arguments", e);
            formatter.printHelp("ExplainInferredMappings", options);
            System.exit(1);
            return;
        }

        // ADR-0011/0016: the inference type stamped on every inferred mapping and on the
        // inferred mapping set. Required.
        InferenceType inferenceType;
        try {
            inferenceType = InferenceType.fromCode(cmd.getOptionValue("inferenceType"));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid --inferenceType: {}", cmd.getOptionValue("inferenceType"), e);
            formatter.printHelp("ExplainInferredMappings", options);
            System.exit(1);
            return;
        }
        if (inferenceType == null || inferenceType == InferenceType.ASSERTED) {
            logger.error("--inferenceType is required and must be SSSOM_INFERENCE.");
            formatter.printHelp("ExplainInferredMappings", options);
            System.exit(1);
            return;
        }

        // ADR-0016: SSSOM cross-set reasoning lands every inference in the single
        // https://www.ebi.ac.uk/oxo2/inferences set, with the source-set union recovered
        // from the per-leaf mapping_id provenance rather than from a single source set.
        boolean crossSet = cmd.hasOption("crossSet");

        String[] inputFilePaths = cmd.getOptionValues("inputFile");

        if (inputFilePaths != null && inputFilePaths.length > 0 && cmd.hasOption("outputFile")) {
            String sourceMappingSetId = cmd.getOptionValue("sourceMappingSetId");
            if (!crossSet && (sourceMappingSetId == null || sourceMappingSetId.isBlank())) {
                logger.error("--sourceMappingSetId is required unless --crossSet is set.");
                formatter.printHelp("ExplainInferredMappings", options);
                System.exit(1);
                return;
            }
            String mappingSetOutputFile = cmd.getOptionValue("mappingSetOutputFile");
            if (mappingSetOutputFile == null || mappingSetOutputFile.isBlank()) {
                logger.error("--mappingSetOutputFile is required.");
                formatter.printHelp("ExplainInferredMappings", options);
                System.exit(1);
                return;
            }
            processTraceBundle(inputFilePaths, cmd.getOptionValue("outputFile"),
                    mappingSetOutputFile, sourceMappingSetId, inferenceType, crossSet);
        } else {
            logger.error("Invalid arguments. Provide -i inputFile [inputFile...] -f outputFile -m mappingSetOutputFile (-x crossSet | -s sourceMappingSetId).");
            formatter.printHelp("ExplainInferredMappings", getOptions());
            System.exit(1);
        }
    }

    /**
     * Explain one <em>bundle</em> of nmo trace files — under ADR-0028 that is a group of
     * per-component explanation shards, batched so one JVM (and one Solr connection) amortises over
     * many shards instead of paying startup per shard. A single trace file is just a bundle of one,
     * which is what the pre-ADR-0020 per-chunk fan-out passed.
     *
     * <p>Bundling is sound because a shard's trace is self-contained: every nil-UUID premise
     * reachable from its final conclusions also appears as a conclusion in the same file (a proof
     * never leaves its component). Conclusions are disjoint across shards, so the shared chain store
     * needs no cross-file de-duplication, and {@code contributingSources} simply accumulates over
     * the whole bundle.
     */
    private static void processTraceBundle(String[] inputFilePaths, String outputFilePath,
                                           String mappingSetOutputFilePath, String sourceMappingSetId,
                                           InferenceType inferenceType, boolean crossSet) {
        String inferredMappingSetId = crossSet
                ? OXOInferenceConstants.CROSS_SET_INFERENCES_SET_ID
                : OXOInferenceConstants.inferredMappingSetIdFor(sourceMappingSetId);
        logger.info("Trace bundle of {} file(s) - Output: {}, MappingSet output: {}, Source mapping set: {}, inferred set: {}, inferenceType: {}, crossSet: {}",
                inputFilePaths.length, outputFilePath, mappingSetOutputFilePath, sourceMappingSetId,
                inferredMappingSetId, inferenceType, crossSet);

        for (String inputFilePath : inputFilePaths) {
            File inputFile = new File(inputFilePath);
            if (!inputFile.exists() || !inputFile.isFile()) {
                logger.error("Input file does not exist or is not a file: {}", inputFilePath);
                System.exit(1);
                return;
            }
        }

        long startTime = System.currentTimeMillis();
        DataloadSolr solrClient = null;
        OnDiskChainStore store = null;
        File tempDir = new File(outputFilePath).getAbsoluteFile().getParentFile();
        File recordsFile = null;
        File finalConclusionsFile = null;
        try {
            solrClient = new DataloadSolr();

            // Pass 1 — stream the chains file into an on-disk store so the cross-set closure never
            // sits in heap (ADR-0018). While streaming, collect the asserted-leaf mapping_ids for a
            // single bulk Solr prefetch and spill the list of final conclusions for Pass 2. Temp
            // files live next to the output (the per-task work dir), so they share its filesystem
            // and space rather than a possibly-tiny /tmp.
            recordsFile = File.createTempFile("oxo2-chain-records", ".bin", tempDir);
            finalConclusionsFile = File.createTempFile("oxo2-final-conclusions", ".txt", tempDir);
            Set<String> assertedMappingIds = new HashSet<>();
            long finalConclusionCount = 0;
            try (OnDiskChainStore.Builder builder = new OnDiskChainStore.Builder(recordsFile);
                 BufferedWriter finalConclusionWriter =
                         Files.newBufferedWriter(finalConclusionsFile.toPath())) {
                for (String inputFilePath : inputFilePaths) {
                    logger.info("Indexing inference chains to disk: {}", inputFilePath);
                    finalConclusionCount += indexChainsFile(inputFilePath, builder, assertedMappingIds,
                            finalConclusionWriter);
                }
                store = builder.build();
            }
            logger.info("Indexed {} inferences; {} final conclusions; {} asserted ids to prefetch",
                    store.size(), finalConclusionCount, assertedMappingIds.size());

            // Bulk-load the asserted leaves once. This also indexes the curie/label of every
            // subject/predicate/object IRI they carry, which enrichEntityDetails reads back, so no
            // separate per-IRI Solr round-trips are needed.
            long prefetchStart = System.currentTimeMillis();
            solrClient.prefetchMappingsByIds(assertedMappingIds);
            logger.info("Prefetched {} asserted mappings in {} ms",
                    assertedMappingIds.size(), System.currentTimeMillis() - prefetchStart);

            // For cross-set inference, the inferred set's source is the union of every source
            // set that contributed an asserted premise, recovered per leaf during the walk.
            SortedSet<Uri> contributingSources = new TreeSet<>();

            // Pass 2 — build and write one inferred mapping per final conclusion, streaming. A
            // bounded LRU shares sub-chains for speed without holding the whole DAG; because the
            // explanation is identity-independent, an eviction only costs a recompute, not a
            // different result.
            Map<String, InferredMapping> memo = boundedMemo(MAX_MEMO_ENTRIES);
            Iterator<InferredMapping> inferredMappings = streamFinalConclusionMappings(
                    finalConclusionsFile, store, solrClient, memo, inferredMappingSetId);

            logger.info("Creating mappings and streaming to file: {}", outputFilePath);
            long writtenCount = streamMappingsToJson(inferredMappings, solrClient, inferredMappingSetId,
                    inferenceType, crossSet, sourceMappingSetId, contributingSources, outputFilePath);
            logger.info("Wrote {} mappings", writtenCount);

            if (writtenCount > 0) {
                logger.info("Writing inferred MappingSet metadata to file: {}", mappingSetOutputFilePath);
                writeInferredMappingSet(inferredMappingSetId, sourceMappingSetId, contributingSources,
                        inferenceType, crossSet, mappingSetOutputFilePath);
            } else {
                logger.warn("Skipping inferred MappingSet emission because no inferred mappings were produced for inferred set {}",
                        inferredMappingSetId);
            }
            logger.info("Successfully completed processing for {} trace file(s)", inputFilePaths.length);

            solrClient.close();
        } catch (Exception e) {
            if (solrClient != null) {
                try {
                    solrClient.close();
                } catch (Throwable t) {
                    logger.error("Error closing Solr", t);
                }
            }
            logger.error("Error processing trace bundle: {}", Arrays.toString(inputFilePaths), e);
            System.exit(1);
        } finally {
            if (store != null) {
                try {
                    store.close(); // deletes the records file
                } catch (IOException e) {
                    logger.warn("Failed to close on-disk chain store", e);
                }
            }
            deleteQuietly(recordsFile);
            deleteQuietly(finalConclusionsFile);
        }

        long endTime = System.currentTimeMillis();
        logger.info("Trace bundle processing took {} s", (endTime - startTime) / 1000);
    }

    /** Max distinct sub-chains kept in the Pass-2 LRU; eviction only forces a (rare) recompute. */
    private static final int MAX_MEMO_ENTRIES = 200_000;

    /**
     * Stream the chains file once: append every inference to {@code builder}, collect the asserted
     * leaf mapping_ids into {@code assertedMappingIds}, and write each final conclusion (one per
     * line) to {@code finalConclusionWriter}. Order-independent: {@code inferences} and
     * {@code finalConclusion} are handled wherever they appear.
     *
     * @return the number of final conclusions written.
     */
    static long indexChainsFile(String inputFilePath, OnDiskChainStore.Builder builder,
            Set<String> assertedMappingIds, BufferedWriter finalConclusionWriter) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonFactory jsonFactory = objectMapper.getFactory();
        long finalConclusionCount = 0;
        try (JsonParser parser = jsonFactory.createParser(new File(inputFilePath))) {
            parser.setCodec(objectMapper);
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new IOException("Expected a JSON object at the root of " + inputFilePath);
            }
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String fieldName = parser.currentName();
                parser.nextToken(); // advance to the field value
                if ("inferences".equals(fieldName)) {
                    expectArray(parser, "inferences", inputFilePath);
                    while (parser.nextToken() != JsonToken.END_ARRAY) {
                        NemoInferences.NemoInference inference =
                                parser.readValueAs(NemoInferences.NemoInference.class);
                        List<String> premises = inference.getPremises() == null
                                ? List.of() : inference.getPremises();
                        builder.add(inference.getConclusion(), inference.getRuleName(), premises);
                        NemoHelper.collectAssertedMappingId(inference.getConclusion(), assertedMappingIds);
                        for (String premise : premises) {
                            NemoHelper.collectAssertedMappingId(premise, assertedMappingIds);
                        }
                    }
                } else if ("finalConclusion".equals(fieldName)) {
                    expectArray(parser, "finalConclusion", inputFilePath);
                    while (parser.nextToken() != JsonToken.END_ARRAY) {
                        finalConclusionWriter.write(parser.getValueAsString());
                        finalConclusionWriter.write('\n');
                        finalConclusionCount++;
                    }
                } else {
                    parser.skipChildren();
                }
            }
        }
        return finalConclusionCount;
    }

    private static void expectArray(JsonParser parser, String field, String inputFilePath)
            throws IOException {
        if (parser.currentToken() != JsonToken.START_ARRAY) {
            throw new IOException("Expected '" + field + "' to be an array in " + inputFilePath);
        }
    }

    /**
     * Lazily build an {@link InferredMapping} per final conclusion, reading the spilled conclusion
     * list line by line and resolving each chain through {@code lookup}. Null builds (malformed
     * atoms) are skipped. The underlying reader is closed when the stream is exhausted.
     */
    private static Iterator<InferredMapping> streamFinalConclusionMappings(File finalConclusionsFile,
            InferenceLookup lookup, DataloadSolr solrClient, Map<String, InferredMapping> memo,
            String inferredMappingSetId) throws IOException {
        BufferedReader reader = Files.newBufferedReader(finalConclusionsFile.toPath());
        return new Iterator<>() {
            private InferredMapping nextMapping = advance();

            private InferredMapping advance() {
                try {
                    String conclusion;
                    while ((conclusion = reader.readLine()) != null) {
                        InferredMapping mapping = NemoHelper.buildInferredMapping(
                                lookup, conclusion, solrClient, memo, inferredMappingSetId);
                        if (mapping != null) {
                            return mapping;
                        }
                    }
                    reader.close();
                    return null;
                } catch (IOException e) {
                    throw new UncheckedIOException("Failed reading final conclusions", e);
                }
            }

            @Override
            public boolean hasNext() {
                return nextMapping != null;
            }

            @Override
            public InferredMapping next() {
                if (nextMapping == null) {
                    throw new NoSuchElementException();
                }
                InferredMapping current = nextMapping;
                nextMapping = advance();
                return current;
            }
        };
    }

    /**
     * Access-order LRU bounding the shared-sub-chain cache to {@code maxEntries}, so Pass-2 heap
     * stays flat regardless of how big the cross-set closure is.
     */
    private static Map<String, InferredMapping> boundedMemo(int maxEntries) {
        return new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, InferredMapping> eldest) {
                return size() > maxEntries;
            }
        };
    }

    private static void deleteQuietly(File file) {
        if (file != null) {
            try {
                Files.deleteIfExists(file.toPath());
            } catch (IOException e) {
                logger.warn("Failed to delete temp file {}", file, e);
            }
        }
    }

    /**
     * Build and write a {@link MappingSet} record describing the inferred set
     * derived from the given source mapping set. Output is a JSON array
     * containing a single MappingSet, matching the format produced by
     * sssom2json so the same json2solr loader can index it.
     */
    private static void writeInferredMappingSet(String inferredMappingSetId, String sourceMappingSetId,
                                                SortedSet<Uri> contributingSources, InferenceType inferenceType,
                                                boolean crossSet, String outputFilePath) throws IOException {
        String mappingSetTitle;
        String mappingSetDescription;
        SortedSet<Uri> sources;
        if (crossSet) {
            // Cross-set (ADR-0016): a single set whose source is the union of every set that
            // contributed an asserted premise, recovered from the per-leaf mapping_id provenance.
            sources = contributingSources;
            mappingSetTitle = "OxO2 SSSOM cross-set inferences";
            mappingSetDescription = "Inferred mappings derived via SEMAPV:MappingChaining across all "
                    + "mapping sets (SSSOM reasoning).";
        } else {
            // Single-source: one inferred set attributed to a single source set.
            sources = new TreeSet<>();
            sources.add(new Uri(sourceMappingSetId));
            mappingSetTitle = "Inferences from " + sourceMappingSetId;
            mappingSetDescription = "Inferred mappings derived via SSSOM reasoning over asserted mappings in "
                    + sourceMappingSetId;
        }

        MappingSet mappingSet = MappingSet.builder()
                .mappingSetId(inferredMappingSetId)
                .mappingSetTitle(mappingSetTitle)
                .mappingSetDescription(mappingSetDescription)
                .mappingSetSource(sources)
                .mappingTool(OXOInferenceConstants.OXO_MAPPING_TOOL)
                .inferenceType(inferenceType)
                .build();

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new Jdk8Module());
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        File file = new File(outputFilePath);
        objectMapper.writeValue(file, List.of(mappingSet));
        logger.info("Inferred MappingSet successfully written to {} ({} bytes)", outputFilePath, file.length());
    }

    /**
     * Build {@link Mapping} records from the inferred-mapping DAG and stream
     * them straight to {@code outputFilePath} as a JSON array. Each Mapping is
     * serialized and dropped within its own loop iteration, so the working set
     * never holds the full result list. The output file is opened lazily on
     * the first emitted mapping; if no mapping survives the skip filters, no
     * file is written (matching the previous {@code writeMappingsAsJson}
     * behaviour and the downstream {@code [ ! -s file ]} cleanup in the .nf).
     *
     * @return the number of {@link Mapping} records written.
     */
    /**
     * Resolve an inferred mapping's subject/predicate/object CURIEs + labels from the asserted Solr
     * index ({@link DataloadSolr#queryEntityDetailsForIRI}, served from the entity cache the caller
     * must have populated via {@link DataloadSolr#prefetchMappingsByIds} over the chain's asserted
     * leaves) and return a {@link Mapping.Builder} carrying the s/p/o + ids/labels, justification,
     * tool, set id, and inference type — the fields common to every inferred mapping doc. The
     * explanation-derived fields (explanation, asserted evidence, explanationLength) are added by
     * the caller that has them.
     *
     * <p>The leaf prefetch suffices: an inferred conclusion's endpoints are always the endpoints of
     * some asserted premise in its own proof, because every rule chains its head's subject and
     * object through body atoms.
     */
    static Mapping.Builder baseInferredMappingBuilder(DataloadSolr solrClient, String subjectIRI,
            String predicateIRI, String objectIRI, String inferredMappingSetId,
            InferenceType inferenceType) {
        EntityDetails subjectDetails = solrClient.queryEntityDetailsForIRI(subjectIRI);
        Optional<String> predicateCurie = PrefixMap.toCurie(predicateIRI);
        String predicateId;
        String predicateLabel;
        if (predicateCurie.isPresent()) {
            predicateId = predicateCurie.get();
            predicateLabel = "";
        } else {
            EntityDetails predicateDetails = solrClient.queryEntityDetailsForIRI(predicateIRI);
            predicateId = (predicateDetails != null && predicateDetails.getCurie() != null) ?
                    predicateDetails.getCurie() : "";
            predicateLabel = (predicateDetails != null && predicateDetails.getLabel() != null) ?
                    predicateDetails.getLabel() : "";
        }
        EntityDetails objectDetails = solrClient.queryEntityDetailsForIRI(objectIRI);
        return new Mapping.Builder()
            .subjectIRI(subjectIRI)
            .subjectId((subjectDetails != null && subjectDetails.getCurie() != null) ?
                    subjectDetails.getCurie() : "")
            .subjectLabel((subjectDetails != null && subjectDetails.getLabel() != null) ?
                    subjectDetails.getLabel() : "")
            .predicateIRI(predicateIRI)
            .predicateId(predicateId)
            .predicateLabel(predicateLabel)
            .objectIRI(objectIRI)
            .objectId((objectDetails != null && objectDetails.getCurie() != null) ?
                    objectDetails.getCurie() : "")
            .objectLabel((objectDetails != null && objectDetails.getLabel() != null) ?
                    objectDetails.getLabel() : "")
            // ADR-0041: an inferred mapping's endpoints carry the obsolescence of the terms they name,
            // harvested from the asserted premises. A live<->live inference that merely bridged an
            // obsolete term has neither flag set and so stays visible by default.
            .subjectObsolete(subjectDetails != null && subjectDetails.isObsolete())
            .objectObsolete(objectDetails != null && objectDetails.isObsolete())
            .mappingJustification(OXOInferenceConstants.OXO_MAPPING_JUSTIFICATION)
            .mappingTool(OXOInferenceConstants.OXO_MAPPING_TOOL)
            .mappingSetId(inferredMappingSetId)
            .inferenceType(inferenceType.getCode());
    }

    public static long streamMappingsToJson(Iterator<InferredMapping> inferredMappings,
                                             DataloadSolr solrClient,
                                             String inferredMappingSetId,
                                             InferenceType inferenceType,
                                             boolean crossSet,
                                             String sourceMappingSetId,
                                             SortedSet<Uri> contributingSourcesOut,
                                             String outputFilePath) throws IOException {
        if (inferredMappings == null || !inferredMappings.hasNext()) {
            logger.warn("No inferred mappings to process");
            return 0;
        }

        // Per-mapping memos for the recursive walks over one InferredMapping chain. They are
        // cleared at the top of each iteration (below) so they stay bounded to a single chain
        // instead of growing across the whole stream. Identity-keyed is correct because, within one
        // chain, NemoHelper hands back one object per distinct conclusion; even if the bounded LRU
        // ever forced a duplicate, the walks' outputs are identity-independent.
        IdentityHashMap<InferredMapping, List<InferredMapping>> assertedMemo = new IdentityHashMap<>();
        IdentityHashMap<InferredMapping, Integer> lengthMemo = new IdentityHashMap<>();
        IdentityHashMap<InferredMapping, Boolean> danglingMemo = new IdentityHashMap<>();

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new Jdk8Module());
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        File outputFile = new File(outputFilePath);
        SequenceWriter seqWriter = null;
        long written = 0;
        int processed = 0;
        long danglingChains = 0;
        long foldedCycleChains = 0;
        boolean closedCleanly = false;
        try {
            while (inferredMappings.hasNext()) {
                InferredMapping inferredMapping = inferredMappings.next();
                // Each top-level mapping's explanation is self-contained, so reset the per-walk
                // memos to keep them bounded to one chain.
                assertedMemo.clear();
                lengthMemo.clear();
                danglingMemo.clear();
                try {
                    if (inferredMapping.getSubjectIRI() == null || inferredMapping.getPredicateIRI() == null ||
                        inferredMapping.getObjectIRI() == null) {
                        logger.warn("Skipping mapping with null IRI values: {}", inferredMapping);
                        continue;
                    }
                    if (inferredMapping.isMappingToSelf()) {
                        logger.debug("Skipping self-mapping: {}", inferredMapping);
                        continue;
                    }
                    if (inferredMapping.getChainRuleApplications().isPresent() &&
                        inferredMapping.getChainRuleApplications().get().getPremises().isEmpty()) {
                        logger.debug("Skipping mapping with no premises - hence it is an asserted mapping: {}", inferredMapping);
                        continue;
                    }
                    // A node whose chainRuleApplications is ABSENT (as opposed to present-but-empty,
                    // which is an asserted leaf) means NemoHelper could not find its derivation in
                    // the chain store. Under ADR-0028 that cannot happen — a proof never leaves its
                    // shard — so it signals a broken shard, not a bad mapping. Left unchecked it
                    // emits a doc with a truncated explanation and no asserted evidence, which is
                    // indistinguishable from an ADR-0020 bare doc. Count it and fail the task below.
                    if (hasDanglingPremise(inferredMapping, danglingMemo)) {
                        logger.error("Dangling explanation chain for {}: a premise is missing from the "
                                + "trace. The shard is not self-contained.", inferredMapping);
                        danglingChains++;
                        continue;
                    }
                    // Defence-in-depth. Two independent things can make a premise restate an ancestor
                    // conclusion — a non-well-founded (circular) explanation:
                    //   1. A derived nil-UUID copy of an already-asserted triple (an involution such
                    //      as SYM-* twice, or RI4 then RI5, re-deriving an asserted fact). Prevented
                    //      at the source by the ~assertedTriple head guards in sssom.rls (ADR-0033).
                    //   2. Two raw IRIs that alias one entity, folding to a single CURIE (e.g.
                    //      divergent MESH stems). Prevented by iri-prefix-overrides.json (ADR-0029).
                    // Both are upstream fixes; this is the detector that says one of them regressed.
                    // Observability only — the doc is still emitted (the conclusion is valid; only its
                    // proof is redundant).
                    if (hasFoldedCycle(inferredMapping)) {
                        foldedCycleChains++;
                        if (foldedCycleChains <= 20) {
                            logger.warn("Non-well-founded explanation (a conclusion restates an ancestor) "
                                    + "for {}. Either a rule in sssom.rls lost its ~assertedTriple head "
                                    + "guard (ADR-0033), or an aliased prefix is not yet in "
                                    + "iri-prefix-overrides.json (ADR-0029).",
                                    foldedSpoKey(inferredMapping));
                        }
                    }

                    List<InferredMapping> assertedEvidence =
                            determineAssertedMappingsForExplanation(inferredMapping, assertedMemo);
                    // Accumulate the source-set union for the cross-set inferred set metadata: every
                    // asserted leaf carries its own source set in mappingSetId (ADR-0010).
                    for (InferredMapping assertedLeaf : assertedEvidence) {
                        String leafSource = assertedLeaf.getMappingSetId();
                        if (leafSource != null && !leafSource.isBlank()) {
                            contributingSourcesOut.add(new Uri(leafSource));
                        }
                    }

                    // `distance` = how many ontologies this mapping spans, minus one (ADR-0031).
                    // OxO2's notion of a term's ontology is its CURIE prefix (ADR-0024): an asserted
                    // mapping, or an inferred one whose entities lie in at most two ontologies, is
                    // distance 1; three ontologies is 2, and so on. Set it on the explanation root too
                    // so the serialised proof reports the same span as the doc. See
                    // calculateMappingDistance.
                    int distance = calculateMappingDistance(inferredMapping);
                    inferredMapping.setDistance(distance);

                    // Reuse the shared entity-resolution + base builder so the s/p/o + ids/labels
                    // are built one place — the same place the bare inferred-mapping indexer uses
                    // (ADR-0020) — then add the explanation-derived fields that only the trace gives.
                    Mapping.Builder mappingBuilder = baseInferredMappingBuilder(solrClient,
                            inferredMapping.getSubjectIRI().asStringIRI(),
                            inferredMapping.getPredicateIRI().asStringIRI(),
                            inferredMapping.getObjectIRI().asStringIRI(),
                            inferredMappingSetId, inferenceType)
                        .explanation(inferredMapping)
                        .assertedMappings(assertedEvidence)
                        .explanationLength(explanationLength(inferredMapping, lengthMemo))
                        .distance(distance);
                    // Single-source inference records its one source set. In cross-set inference a
                    // mapping can draw on several sets (captured in the explanation and the set-level
                    // source union), so a single mappingSource would be lossy — leave it unset.
                    if (!crossSet && sourceMappingSetId != null && !sourceMappingSetId.isBlank()) {
                        mappingBuilder.mappingSource(sourceMappingSetId);
                    }
                    Mapping mapping = mappingBuilder.build();

                    if (seqWriter == null) {
                        seqWriter = objectMapper.writer().writeValuesAsArray(outputFile);
                    }
                    seqWriter.write(mapping);
                    written++;

                    processed++;
                    if (processed % 1000 == 0) {
                        logger.info("Processed {} mappings", processed);
                    }
                } catch (Exception e) {
                    logger.error("Error creating mapping for: {}", inferredMapping, e);
                }
            }
            if (seqWriter != null) {
                seqWriter.close();
                seqWriter = null;
            }
            closedCleanly = true;
        } finally {
            if (seqWriter != null) {
                try { seqWriter.close(); } catch (Exception ignored) { /* swallow during cleanup */ }
            }
            // On any failure that left a partial JSON on disk, remove it so the
            // .nf "[ ! -s file ]" cleanup doesn't preserve a corrupt artefact
            // that downstream Solr indexing would later fail to parse.
            if (!closedCleanly && written > 0 && outputFile.exists()) {
                if (!outputFile.delete()) {
                    logger.warn("Failed to delete partial output file after error: {}", outputFilePath);
                }
            }
        }

        logger.info("Finished streaming inferred mappings: {} processed, {} written", processed, written);

        if (foldedCycleChains > 0) {
            logger.warn("{} inferred mapping(s) had a non-well-founded (folded-cycle) explanation. Check "
                    + "that every derivation rule in sssom.rls still carries its ~assertedTriple head "
                    + "guard (ADR-0033); if they do, the cause is an aliased entity IRI — run "
                    + "PrefixDivergenceDetector, add the offending prefix(es) to "
                    + "iri-prefix-overrides.json, and re-run the dataload (ADR-0029).",
                    foldedCycleChains);
        }

        if (danglingChains > 0) {
            // Fail rather than quietly under-explain: a dangling chain means the shard corpus was
            // missing a premise, so every mapping in this bundle is suspect.
            throw new IOException(danglingChains + " inferred mapping(s) had a dangling explanation "
                    + "chain — the trace files are not self-contained. Check that "
                    + "OXOInferenceConstants.STRONG_PREDICATES covers every predicate appearing in a "
                    + "sssom.rls rule body.");
        }

        if (written > 0) {
            logger.info("Mappings successfully streamed to {} ({} mappings, {} bytes)",
                    outputFilePath, written, outputFile.length());
        } else {
            logger.warn("No mappings written to file");
        }
        return written;
    }

    /**
     * When we determine explanations, we only provide a single asserted mapping as evidence for why an inference was made.
     * However, it is possible that the same mapping is asserted in multiple mapping sets. Hence, this method retrieves
     * all asserted mappings. This can be useful for users who need to debug incorrect derived mappings.
     *
     * <p>Memoized: shared sub-chains in the DAG (created via NemoHelper's
     * conclusion-keyed memo) are walked once, not once per parent.
     */
    static List<InferredMapping> determineAssertedMappingsForExplanation(
            InferredMapping explanation,
            IdentityHashMap<InferredMapping, List<InferredMapping>> memo) {
        List<InferredMapping> cached = memo.get(explanation);
        if (cached != null) return cached;

        List<InferredMapping> assertedMappings = new ArrayList<>();

        if (explanation.getChainRuleApplications().isEmpty()) {
            memo.put(explanation, assertedMappings);
            return assertedMappings;
        }

        InferredMapping.ChainRuleApplications chainRuleApplications = explanation.getChainRuleApplications().get();

        if (chainRuleApplications.getChainRule().isPresent() &&
                chainRuleApplications.getChainRule().get().equals(ChainRulesEnum.ASSERTED))
            assertedMappings.add(explanation);

        for (InferredMapping premise : chainRuleApplications.getPremises()) {
            assertedMappings.addAll(determineAssertedMappingsForExplanation(premise, memo));
        }

        memo.put(explanation, assertedMappings);
        return assertedMappings;
    }

    /**
     * Length of the chain rooted at {@code explanation}: 0 for an asserted/leaf
     * node, otherwise 1 + sum of premise lengths. Memoized so shared sub-chains
     * are walked once.
     */
    static int explanationLength(InferredMapping explanation,
                                 IdentityHashMap<InferredMapping, Integer> memo) {
        Integer cached = memo.get(explanation);
        if (cached != null) return cached;

        int length;
        if (explanation.getChainRuleApplications().isEmpty()) {
            length = 0;
        } else {
            length = 1;
            for (InferredMapping premise : explanation.getChainRuleApplications().get().getPremises()) {
                length += explanationLength(premise, memo);
            }
        }
        memo.put(explanation, length);
        return length;
    }

    /**
     * Distance = the number of distinct ontologies the mapping spans, minus one (ADR-0031). OxO2's
     * notion of a term's ontology is its CURIE prefix (ADR-0024 — the same value emitted as
     * {@code subject_prefix} / {@code object_prefix}), so this counts the distinct prefixes of every
     * subject and object across the whole explanation DAG: the conclusion plus all premises,
     * transitively. An asserted mapping, or an explanation confined to at most two ontologies, is
     * distance 1; three ontologies is 2, and so on.
     *
     * <p>Floored at 1 so a wholly intra-ontology chain still ranks as a direct mapping and, crucially,
     * so the {@code SolrQueryBuilder} decay {@code div(INFERRED_BOOST, pow(5, distance-1))} can never
     * exceed the asserted tier — a distance below 1 would boost an inferred mapping <em>above</em> a
     * curated one. Predicates are not counted; only the mapped entities (subject/object) contribute.
     */
    static int calculateMappingDistance(InferredMapping explanation) {
        Set<String> ontologies = new HashSet<>();
        collectOntologyPrefixes(explanation, ontologies,
                Collections.newSetFromMap(new IdentityHashMap<>()));
        return Math.max(1, ontologies.size() - 1);
    }

    /**
     * Depth-first collect the CURIE prefix of every subject and object in the sub-DAG rooted at
     * {@code node}. Identity-visited so shared sub-chains (diamonds) are walked once; nmo's trace DAG
     * is acyclic over object identity, so this always terminates.
     */
    private static void collectOntologyPrefixes(InferredMapping node, Set<String> ontologies,
                                                Set<InferredMapping> visited) {
        if (node == null || !visited.add(node)) return;
        addOntologyPrefix(node.getSubjectId(), ontologies);
        addOntologyPrefix(node.getObjectId(), ontologies);
        if (node.getChainRuleApplications().isPresent()) {
            List<InferredMapping> premises = node.getChainRuleApplications().get().getPremises();
            if (premises != null) {
                for (InferredMapping premise : premises) {
                    collectOntologyPrefixes(premise, ontologies, visited);
                }
            }
        }
    }

    /**
     * Add an entity's CURIE prefix to {@code ontologies}, if it has one. The {@code subjectId} /
     * {@code objectId} fields are unset {@link Optional} references on a node that only carried IRIs,
     * so the null guard keeps a sparsely-populated node from aborting the walk (it just contributes no
     * ontology, and the distance floor still yields a direct mapping).
     */
    private static void addOntologyPrefix(Optional<EntityReference> entityId, Set<String> ontologies) {
        if (entityId != null) {
            entityId.flatMap(EntityReference::getCuriePrefix).ifPresent(ontologies::add);
        }
    }

    /**
     * True if any node below {@code explanation} has no {@code chainRuleApplications} at all — i.e.
     * {@link NemoHelper} could not resolve its derivation. An asserted leaf is <em>present</em> with
     * an empty premise list, so it is not dangling. Memoized over the DAG; shared sub-chains are
     * visited once.
     */
    /**
     * True if any descendant of {@code explanation} restates an ancestor's folded (CURIE-level) S-P-O,
     * i.e. the conclusion appears inside its own proof.
     *
     * <p>nmo's trace DAG is acyclic over its own 4-ary {@code mapping(id, s, p, o)} atoms, but this
     * walker sees the S-P-O triple <em>after</em> the mapping_id has been projected away, and two
     * atoms can fold onto one triple in two ways:
     * <ul>
     *   <li>a derived nil-UUID copy of a triple that is also asserted under a real UUID — prevented
     *       by the {@code ~assertedTriple} head guards in {@code sssom.rls} (ADR-0033);</li>
     *   <li>two raw IRIs aliasing one entity, folding to one CURIE — prevented by
     *       {@code iri-prefix-overrides.json} (ADR-0029).</li>
     * </ul>
     * Both are prevented upstream, so this is a regression detector, not the fix. Path-scoped: a fact
     * reused across sibling branches is a legitimate diamond; only ancestor reuse counts.
     */
    static boolean hasFoldedCycle(InferredMapping explanation) {
        return hasFoldedCycle(explanation, new HashSet<>());
    }

    private static boolean hasFoldedCycle(InferredMapping node, Set<String> ancestorSpoKeys) {
        String spoKey = foldedSpoKey(node);
        if (!ancestorSpoKeys.add(spoKey)) {
            return true;
        }
        if (node.getChainRuleApplications().isPresent()) {
            for (InferredMapping premise : node.getChainRuleApplications().get().getPremises()) {
                if (hasFoldedCycle(premise, ancestorSpoKeys)) {
                    return true;
                }
            }
        }
        ancestorSpoKeys.remove(spoKey);
        return false;
    }

    /** The folded (CURIE-level) subject/predicate/object identity of a node; falls back to the IRI. */
    private static String foldedSpoKey(InferredMapping node) {
        return foldedTerm(node.getSubjectId(), node.getSubjectIRI()) + " "
                + foldedTerm(node.getPredicateId(), node.getPredicateIRI()) + " "
                + foldedTerm(node.getObjectId(), node.getObjectIRI());
    }

    /** The CURIE when the node carries one, else the raw IRI. Tolerates a null or empty Optional. */
    private static String foldedTerm(Optional<EntityReference> curie, Object iri) {
        if (curie != null && curie.isPresent()) {
            return curie.get().getDataAsString();
        }
        return String.valueOf(iri);
    }

    static boolean hasDanglingPremise(InferredMapping explanation,
                                      IdentityHashMap<InferredMapping, Boolean> memo) {
        Boolean cached = memo.get(explanation);
        if (cached != null) return cached;

        // Provisionally false so a cycle (which nmo never emits) cannot recurse forever.
        memo.put(explanation, Boolean.FALSE);

        if (explanation.getChainRuleApplications().isEmpty()) {
            memo.put(explanation, Boolean.TRUE);
            return true;
        }
        for (InferredMapping premise : explanation.getChainRuleApplications().get().getPremises()) {
            if (hasDanglingPremise(premise, memo)) {
                memo.put(explanation, Boolean.TRUE);
                return true;
            }
        }
        return false;
    }

    private static Options getOptions() {
        Options options = new Options();

        // One or more nmo trace files, processed together as a bundle by a single JVM.
        Option inputFile = new Option("i", "inputFile", true,
                "nmo --trace-output JSON file(s) to explain. Repeatable, or several paths after one "
                        + "-i; all are processed as one bundle sharing a chain store and Solr client.");
        inputFile.setArgs(Option.UNLIMITED_VALUES);
        inputFile.setRequired(false);
        options.addOption(inputFile);

        Option outputFile = new Option("f", "outputFile", true,
                "Output file for the bundle's inferred mappings (JSON array).");
        outputFile.setRequired(false);
        options.addOption(outputFile);

        Option mappingSetOutputFile = new Option("m", "mappingSetOutputFile", true,
                "Output file for the inferred MappingSet JSON metadata. In cross-set mode this "
                        + "carries only the bundle's PARTIAL source union; merge across bundles.");
        mappingSetOutputFile.setRequired(false);
        options.addOption(mappingSetOutputFile);

        Option sourceMappingSetId = new Option("s", "sourceMappingSetId", true,
                "Source mapping set ID (URI) whose chain file is being processed.");
        sourceMappingSetId.setRequired(false);
        options.addOption(sourceMappingSetId);

        // Inference type (ADR-0011/0016): stamped on every inferred mapping and the inferred set.
        Option inferenceType = new Option("t", "inferenceType", true,
                "Inference type stamped on the inferred mappings/set: SSSOM_INFERENCE. Required.");
        inferenceType.setRequired(false);
        options.addOption(inferenceType);

        Option crossSet = new Option("x", "crossSet", false,
                "Cross-set mode: land all inferences in the single "
                        + "https://www.ebi.ac.uk/oxo2/inferences set with a source-set union. "
                        + "Single-file mode only; --sourceMappingSetId is not required.");
        crossSet.setRequired(false);
        options.addOption(crossSet);

        return options;
    }

}
