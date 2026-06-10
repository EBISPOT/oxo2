package uk.ac.ebi.spot.oxo.inferences.nemo;

import com.fasterxml.jackson.annotation.JsonInclude;
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
import uk.ac.ebi.spot.oxo.model.sssom.InferredMapping;
import uk.ac.ebi.spot.oxo.model.sssom.Mapping;
import uk.ac.ebi.spot.oxo.model.sssom.MappingSet;
import uk.ac.ebi.spot.oxo.model.sssom.PrefixMap;
import uk.ac.ebi.spot.oxo.model.sssom.Uri;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ExplainInferredMappings {
    private static final Logger logger = LoggerFactory.getLogger(ExplainInferredMappings.class);

    private static final String CHAIN_FILE_SUFFIX = "-chains";

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

        boolean singleFileMode = cmd.hasOption("inputFile") && cmd.hasOption("outputFile");

        if (singleFileMode) {
            String sourceMappingSetId = cmd.getOptionValue("sourceMappingSetId");
            if (!crossSet && (sourceMappingSetId == null || sourceMappingSetId.isBlank())) {
                logger.error("--sourceMappingSetId is required in single-file mode unless --crossSet is set.");
                formatter.printHelp("ExplainInferredMappings", options);
                System.exit(1);
                return;
            }
            String mappingSetOutputFile = cmd.getOptionValue("mappingSetOutputFile");
            if (mappingSetOutputFile == null || mappingSetOutputFile.isBlank()) {
                logger.error("--mappingSetOutputFile is required in single-file mode.");
                formatter.printHelp("ExplainInferredMappings", options);
                System.exit(1);
                return;
            }
            processSingleFile(cmd.getOptionValue("inputFile"), cmd.getOptionValue("outputFile"),
                    mappingSetOutputFile, sourceMappingSetId, inferenceType, crossSet);
        } else {
            logger.error("Invalid arguments. Provide -i inputFile -f outputFile -m mappingSetOutputFile (-x crossSet | -s sourceMappingSetId) for single-file mode.");
            formatter.printHelp("ExplainInferredMappings", getOptions());
            System.exit(1);
        }
    }

    /**
     * Process a single file - used for Nextflow parallel processing.
     * Each invocation creates its own Solr client connection.
     */
    private static void processSingleFile(String inputFilePath, String outputFilePath,
                                           String mappingSetOutputFilePath, String sourceMappingSetId,
                                           InferenceType inferenceType, boolean crossSet) {
        String inferredMappingSetId = crossSet
                ? OXOInferenceConstants.CROSS_SET_INFERENCES_SET_ID
                : OXOInferenceConstants.inferredMappingSetIdFor(sourceMappingSetId);
        logger.info("Single file mode - Input: {}, Output: {}, MappingSet output: {}, Source mapping set: {}, inferred set: {}, inferenceType: {}, crossSet: {}",
                inputFilePath, outputFilePath, mappingSetOutputFilePath, sourceMappingSetId,
                inferredMappingSetId, inferenceType, crossSet);

        File inputFile = new File(inputFilePath);
        if (!inputFile.exists() || !inputFile.isFile()) {
            logger.error("Input file does not exist or is not a file: {}", inputFilePath);
            System.exit(1);
            return;
        }

        long startTime = System.currentTimeMillis();
        DataloadSolr solrClient = null;
        try {
            solrClient = new DataloadSolr();

            logger.info("Reading inferences from file...");
            NemoInferences nemoInferences = NemoInferenceReader.readInferences(inputFilePath);
            if (nemoInferences == null) {
                logger.error("Failed to read inferences from file or file is empty: {}", inputFilePath);
                solrClient.close();
                System.exit(1);
                return;
            }

            logger.info("Converting to inferred mappings...");
            Set<InferredMapping> inferredMappings = NemoHelper.fromNemoInferencesToInferredMappings(
                    nemoInferences, solrClient, inferredMappingSetId);
            if (inferredMappings.isEmpty()) {
                logger.warn("No inferred mappings were generated for file: {}", inputFilePath);
            } else {
                logger.info("Generated {} inferred mappings", inferredMappings.size());
            }

            // For cross-set inference, the inferred set's source is the union of every source
            // set that contributed an asserted premise, recovered per leaf during the walk.
            SortedSet<Uri> contributingSources = new TreeSet<>();

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
            logger.info("Successfully completed processing for file: {}", inputFilePath);

            solrClient.close();
        } catch (Exception e) {
            if (solrClient != null) {
                try {
                    solrClient.close();
                } catch (Throwable t) {
                    logger.error("Error closing Solr", t);
                }
            }
            logger.error("Error processing file: {}", inputFilePath, e);
            System.exit(1);
        }

        long endTime = System.currentTimeMillis();
        logger.info("Single file processing took {} s", (endTime - startTime) / 1000);
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
    public static long streamMappingsToJson(Set<InferredMapping> inferredMappings,
                                             DataloadSolr solrClient,
                                             String inferredMappingSetId,
                                             InferenceType inferenceType,
                                             boolean crossSet,
                                             String sourceMappingSetId,
                                             SortedSet<Uri> contributingSourcesOut,
                                             String outputFilePath) throws IOException {
        if (inferredMappings == null || inferredMappings.isEmpty()) {
            logger.warn("No inferred mappings to process");
            return 0;
        }

        // Per-run memos for the two recursive walks over the InferredMapping DAG.
        // NemoHelper.determineInferencesLeadingToConclusion guarantees one object
        // per distinct conclusion string, so identity-keyed maps are correct and
        // cheaper than equals-keyed (InferredMapping.hashCode hashes 3 IRI Strings).
        IdentityHashMap<InferredMapping, List<InferredMapping>> assertedMemo = new IdentityHashMap<>();
        IdentityHashMap<InferredMapping, Integer> lengthMemo = new IdentityHashMap<>();

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
        boolean closedCleanly = false;
        try {
            for (InferredMapping inferredMapping : inferredMappings) {
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

                    EntityDetails subjectDetails = solrClient.queryEntityDetailsForIRI(
                            inferredMapping.getSubjectIRI().asStringIRI());
                    String predicateIri = inferredMapping.getPredicateIRI().asStringIRI();
                    Optional<String> predicateCurie = PrefixMap.toCurie(predicateIri);
                    String predicateId;
                    String predicateLabel;
                    if (predicateCurie.isPresent()) {
                        predicateId = predicateCurie.get();
                        predicateLabel = "";
                    } else {
                        EntityDetails predicateDetails = solrClient.queryEntityDetailsForIRI(predicateIri);
                        predicateId = (predicateDetails != null && predicateDetails.getCurie() != null) ?
                                predicateDetails.getCurie() : "";
                        predicateLabel = (predicateDetails != null && predicateDetails.getLabel() != null) ?
                                predicateDetails.getLabel() : "";
                    }
                    EntityDetails objectDetails = solrClient.queryEntityDetailsForIRI(
                            inferredMapping.getObjectIRI().asStringIRI());

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

                    Mapping.Builder mappingBuilder = new Mapping.Builder()
                        .subjectIRI(inferredMapping.getSubjectIRI().asStringIRI())
                        .subjectId((subjectDetails != null && subjectDetails.getCurie() != null) ?
                                subjectDetails.getCurie() : "")
                        .subjectLabel((subjectDetails != null && subjectDetails.getLabel() != null) ?
                                subjectDetails.getLabel() : "")
                        .predicateIRI(predicateIri)
                        .predicateId(predicateId)
                        .predicateLabel(predicateLabel)
                        .objectIRI(inferredMapping.getObjectIRI().asStringIRI())
                        .objectId((objectDetails != null && objectDetails.getCurie() != null) ?
                                objectDetails.getCurie() : "")
                        .objectLabel((objectDetails != null && objectDetails.getLabel() != null) ?
                                objectDetails.getLabel() : "")
                        .mappingJustification(OXOInferenceConstants.OXO_MAPPING_JUSTIFICATION)
                        .mappingTool(OXOInferenceConstants.OXO_MAPPING_TOOL)
                        .explanation(inferredMapping)
                        .assertedMappings(assertedEvidence)
                        .explanationLength(explanationLength(inferredMapping, lengthMemo))
                        .distance(calculateMappingDistance(inferredMapping))
                        .mappingSetId(inferredMappingSetId)
                        .inferenceType(inferenceType.getCode());
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

        logger.info("Walk memos: {} top-level mappings, {} memoized assertedMappings entries, {} memoized explanationLength entries",
                inferredMappings.size(), assertedMemo.size(), lengthMemo.size());

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

    private static int calculateMappingDistance(InferredMapping explanation) {
        Set<String> extractedParts = new HashSet<>();

        extractParts(explanation.getSubjectIRI().asStringIRI(), extractedParts);
        extractParts(explanation.getObjectIRI().asStringIRI(), extractedParts);

        if (explanation.getChainRuleApplications().isEmpty())
            return extractedParts.size() - 1;


        for (InferredMapping premise: explanation.getChainRuleApplications().get().getPremises()) {
            extractParts(premise.getSubjectIRI().asStringIRI(), extractedParts);
            extractParts(premise.getObjectIRI().asStringIRI(), extractedParts);
        }

        return extractedParts.size() - 1;
    }

    private static void extractParts(String input, Set<String> extractedParts) {
        if (input != null) {
            String[] parts = input.split("/");
            String lastPart = parts[parts.length - 1];
            if (lastPart.contains("_")) {
                extractedParts.add(lastPart.split("_")[0]);
            }
        }
    }

    private static Options getOptions() {
        Options options = new Options();

        // Single file mode options (for Nextflow parallel processing)
        Option inputFile = new Option("i", "inputFile", true,
                "Single input file to process (for parallel processing with Nextflow)");
        inputFile.setRequired(false);
        options.addOption(inputFile);

        Option outputFile = new Option("f", "outputFile", true,
                "Single output file (for parallel processing with Nextflow)");
        outputFile.setRequired(false);
        options.addOption(outputFile);

        Option mappingSetOutputFile = new Option("m", "mappingSetOutputFile", true,
                "Output file for the inferred MappingSet JSON metadata (single-file mode).");
        mappingSetOutputFile.setRequired(false);
        options.addOption(mappingSetOutputFile);

        Option sourceMappingSetId = new Option("s", "sourceMappingSetId", true,
                "Source mapping set ID (URI) whose chain file is being processed (single-file mode).");
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
