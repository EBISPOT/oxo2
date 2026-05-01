package uk.ac.ebi.spot.oxo.inferences.nemo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import uk.ac.ebi.spot.oxo.model.sssom.InferredMapping;
import uk.ac.ebi.spot.oxo.model.sssom.Mapping;
import uk.ac.ebi.spot.oxo.model.sssom.MappingSet;
import uk.ac.ebi.spot.oxo.model.sssom.PrefixMap;
import uk.ac.ebi.spot.oxo.model.sssom.Uri;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.*;

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

        boolean singleFileMode = cmd.hasOption("inputFile") && cmd.hasOption("outputFile");
        boolean directoryMode = cmd.hasOption("nemoInferencesDirectory") && cmd.hasOption("outputDirectory");

        if (singleFileMode) {
            String sourceMappingSetId = cmd.getOptionValue("sourceMappingSetId");
            if (sourceMappingSetId == null || sourceMappingSetId.isBlank()) {
                logger.error("--sourceMappingSetId is required in single-file mode.");
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
                    mappingSetOutputFile, sourceMappingSetId);
        } else if (directoryMode) {
            String mappingSetJsonDir = cmd.getOptionValue("mappingSetJsonDir");
            String mappingSetOutputDirectory = cmd.getOptionValue("mappingSetOutputDirectory");
            if (mappingSetJsonDir == null || mappingSetJsonDir.isBlank()) {
                logger.error("--mappingSetJsonDir is required in directory mode (used to resolve source mapping set IDs per chain file).");
                formatter.printHelp("ExplainInferredMappings", options);
                System.exit(1);
                return;
            }
            if (mappingSetOutputDirectory == null || mappingSetOutputDirectory.isBlank()) {
                logger.error("--mappingSetOutputDirectory is required in directory mode.");
                formatter.printHelp("ExplainInferredMappings", options);
                System.exit(1);
                return;
            }
            processDirectory(cmd.getOptionValue("nemoInferencesDirectory"), cmd.getOptionValue("outputDirectory"),
                    mappingSetOutputDirectory, mappingSetJsonDir);
        } else {
            logger.error("Invalid arguments. Provide either (-i inputFile -f outputFile -m mappingSetOutputFile -s sourceMappingSetId) for single file mode or (-n nemoInferencesDirectory -o outputDirectory -q mappingSetOutputDirectory -d mappingSetJsonDir) for directory mode.");
            formatter.printHelp("ExplainInferredMappings", getOptions());
            System.exit(1);
        }
    }

    /**
     * Process a single file - used for Nextflow parallel processing.
     * Each invocation creates its own Solr client connection.
     */
    private static void processSingleFile(String inputFilePath, String outputFilePath,
                                           String mappingSetOutputFilePath, String sourceMappingSetId) {
        logger.info("Single file mode - Input: {}, Output: {}, MappingSet output: {}, Source mapping set: {}",
                inputFilePath, outputFilePath, mappingSetOutputFilePath, sourceMappingSetId);

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
                    nemoInferences, solrClient, sourceMappingSetId);
            if (inferredMappings.isEmpty()) {
                logger.warn("No inferred mappings were generated for file: {}", inputFilePath);
            } else {
                logger.info("Generated {} inferred mappings", inferredMappings.size());
            }

            logger.info("Creating mappings...");
            List<Mapping> mappings = createMappings(inferredMappings, solrClient, sourceMappingSetId);
            logger.info("Created {} mappings", mappings.size());

            logger.info("Writing mappings to file: {}", outputFilePath);
            writeMappingsAsJson(mappings, outputFilePath);

            if (!mappings.isEmpty()) {
                logger.info("Writing inferred MappingSet metadata to file: {}", mappingSetOutputFilePath);
                writeInferredMappingSet(sourceMappingSetId, mappingSetOutputFilePath);
            } else {
                logger.warn("Skipping inferred MappingSet emission because no inferred mappings were produced for source set {}",
                        sourceMappingSetId);
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
     * Process all files in a directory - used for sequential processing.
     * Uses a single shared Solr client for all files.
     */
    private static void processDirectory(String nemoInferencesToParseDirectory, String outputDirectory,
                                          String mappingSetOutputDirectory, String mappingSetJsonDirectory) {
        logger.info("Directory mode - Input: {}, Output: {}, MappingSet output: {}, MappingSet JSON dir: {}",
                nemoInferencesToParseDirectory, outputDirectory, mappingSetOutputDirectory, mappingSetJsonDirectory);

        File inputDir = new File(nemoInferencesToParseDirectory);
        if (!inputDir.exists() || !inputDir.isDirectory()) {
            logger.error("Input directory does not exist or is not a directory: {}", nemoInferencesToParseDirectory);
            System.exit(1);
            return;
        }

        File outputDir = new File(outputDirectory);
        if (!outputDir.exists()) {
            if (!outputDir.mkdirs()) {
                logger.error("Failed to create output directory: {}", outputDir.getAbsolutePath());
                System.exit(1);
                return;
            }
        }

        File mappingSetOutputDir = new File(mappingSetOutputDirectory);
        if (!mappingSetOutputDir.exists()) {
            if (!mappingSetOutputDir.mkdirs()) {
                logger.error("Failed to create mappingSet output directory: {}", mappingSetOutputDir.getAbsolutePath());
                System.exit(1);
                return;
            }
        }

        File mappingSetJsonDir = new File(mappingSetJsonDirectory);
        if (!mappingSetJsonDir.exists() || !mappingSetJsonDir.isDirectory()) {
            logger.error("MappingSet JSON directory does not exist or is not a directory: {}", mappingSetJsonDirectory);
            System.exit(1);
            return;
        }

        long startTime = System.currentTimeMillis();
        DataloadSolr solrClient = null;
        try {
            solrClient = new DataloadSolr();

            File[] inputFiles = inputDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".json") || name.toLowerCase().endsWith(".ttl"));
            if (inputFiles == null || inputFiles.length == 0) {
                logger.warn("No files found in input directory: {}", nemoInferencesToParseDirectory);
                solrClient.close();
                return;
            }

            logger.info("Found {} files to process", inputFiles.length);

            int processedCount = 0;
            int failedCount = 0;

            for (File inputFile : inputFiles) {
                try {
                    String inputFilePath = inputFile.getAbsolutePath();
                    logger.info("Processing file: {}", inputFilePath);

                    String inputFileName = inputFile.getName();
                    String baseName = inputFileName.substring(0, inputFileName.lastIndexOf('.'));

                    String sourceMappingSetId = resolveSourceMappingSetId(mappingSetJsonDir, baseName);
                    if (sourceMappingSetId == null) {
                        logger.error("Could not resolve source mapping set ID for chain file {} (looked under {}). Skipping.",
                                inputFile.getName(), mappingSetJsonDirectory);
                        failedCount++;
                        continue;
                    }
                    logger.info("Resolved source mapping set ID: {}", sourceMappingSetId);

                    logger.info("Reading inferences from file...");
                    NemoInferences nemoInferences = NemoInferenceReader.readInferences(inputFilePath);
                    if (nemoInferences == null) {
                        logger.error("Failed to read inferences from file or file is empty: {}", inputFilePath);
                        failedCount++;
                        continue;
                    }

                    logger.info("Converting to inferred mappings...");
                    Set<InferredMapping> inferredMappings = NemoHelper.fromNemoInferencesToInferredMappings(
                            nemoInferences, solrClient, sourceMappingSetId);
                    if (inferredMappings.isEmpty()) {
                        logger.warn("No inferred mappings were generated for file: {}", inputFilePath);
                    } else {
                        logger.info("Generated {} inferred mappings", inferredMappings.size());
                    }

                    logger.info("Creating mappings...");
                    List<Mapping> mappings = createMappings(inferredMappings, solrClient, sourceMappingSetId);
                    logger.info("Created {} mappings", mappings.size());

                    String outputFilePath = new File(outputDir, baseName + ".json").getAbsolutePath();

                    logger.info("Writing mappings to file: {}", outputFilePath);
                    writeMappingsAsJson(mappings, outputFilePath);

                    if (!mappings.isEmpty()) {
                        String mappingSetBaseName = baseName.endsWith(CHAIN_FILE_SUFFIX)
                                ? baseName.substring(0, baseName.length() - CHAIN_FILE_SUFFIX.length())
                                : baseName;
                        String mappingSetOutputFilePath = new File(mappingSetOutputDir,
                                mappingSetBaseName + ".json").getAbsolutePath();
                        logger.info("Writing inferred MappingSet metadata to file: {}", mappingSetOutputFilePath);
                        writeInferredMappingSet(sourceMappingSetId, mappingSetOutputFilePath);
                    }

                    logger.info("Successfully completed processing for file: {}", inputFilePath);
                    processedCount++;
                } catch (Exception e) {
                    logger.error("Error processing file: {}", inputFile.getAbsolutePath(), e);
                    failedCount++;
                }
            }

            logger.info("Processing complete. Successfully processed: {}, Failed: {}", processedCount, failedCount);
            solrClient.close();
        } catch (Exception e) {
            if (solrClient != null) {
                try {
                    solrClient.close();
                } catch (Throwable t) {
                    logger.error("Error closing Solr", t);
                }
            }
            logger.error("Error processing mappings", e);
            System.exit(1);
        }
        long endTime = System.currentTimeMillis();

        logger.info("Total processing took {} s", (endTime - startTime)/1000);
        logger.info("Processing took {} s", (endTime - startTime)/1000);
    }

    /**
     * Resolve the source mapping set ID for a chain file by reading the
     * corresponding {@code <baseName-without-chains-suffix>.json} from the
     * sssom-as-json mappingSet directory.
     */
    private static String resolveSourceMappingSetId(File mappingSetJsonDir, String chainFileBaseName) {
        String sourceBaseName = chainFileBaseName.endsWith(CHAIN_FILE_SUFFIX)
                ? chainFileBaseName.substring(0, chainFileBaseName.length() - CHAIN_FILE_SUFFIX.length())
                : chainFileBaseName;
        File mappingSetFile = new File(mappingSetJsonDir, sourceBaseName + ".json");
        if (!mappingSetFile.exists() || !mappingSetFile.isFile()) {
            logger.error("MappingSet JSON file not found: {}", mappingSetFile.getAbsolutePath());
            return null;
        }
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.registerModule(new Jdk8Module());
            objectMapper.registerModule(new JavaTimeModule());
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            // sssom2json writes a JSON array containing a single MappingSet
            List<MappingSet> mappingSets = objectMapper.readValue(mappingSetFile,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, MappingSet.class));
            if (mappingSets == null || mappingSets.isEmpty()) {
                logger.error("MappingSet JSON file is empty or unparsable: {}", mappingSetFile.getAbsolutePath());
                return null;
            }
            MappingSet mappingSet = mappingSets.get(0);
            if (mappingSet.mappingSetId() == null) {
                logger.error("MappingSet JSON has no mapping_set_id: {}", mappingSetFile.getAbsolutePath());
                return null;
            }
            return mappingSet.mappingSetId().asStringIRI();
        } catch (IOException e) {
            logger.error("Error reading MappingSet JSON file: {}", mappingSetFile.getAbsolutePath(), e);
            return null;
        }
    }

    /**
     * Build and write a {@link MappingSet} record describing the inferred set
     * derived from the given source mapping set. Output is a JSON array
     * containing a single MappingSet, matching the format produced by
     * sssom2json so the same json2solr loader can index it.
     */
    private static void writeInferredMappingSet(String sourceMappingSetId, String outputFilePath) throws IOException {
        String inferredMappingSetId = OXOInferenceConstants.inferredMappingSetIdFor(sourceMappingSetId);
        SortedSet<Uri> sources = new TreeSet<>();
        sources.add(new Uri(sourceMappingSetId));

        MappingSet mappingSet = MappingSet.builder()
                .mappingSetId(inferredMappingSetId)
                .mappingSetTitle("Inferences from " + sourceMappingSetId)
                .mappingSetDescription("Inferred mappings derived via SEMAPV:MappingChaining over asserted mappings in "
                        + sourceMappingSetId)
                .mappingSetSource(sources)
                .mappingTool(OXOInferenceConstants.OXO_MAPPING_TOOL)
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

    public static void writeMappingsAsJson(List<Mapping> mappings, String outputFile) throws IOException {
        if (mappings == null || mappings.isEmpty()) {
            logger.warn("No mappings to write to file");
            return;
        }

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new Jdk8Module());
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        File file = new File(outputFile);
        try {
            objectMapper.writeValue(file, mappings);
            logger.info("Mappings successfully written to {} ({} bytes)", outputFile, file.length());
        } catch (IOException e) {
            logger.error("Error writing mappings to JSON file = {}, {}", file.getName(), e.getMessage());
            throw e;
        }
    }

    public static List<Mapping> createMappings(Set<InferredMapping> inferredMappings,
                                               DataloadSolr solrClient,
                                               String sourceMappingSetId) {
        if (inferredMappings == null || inferredMappings.isEmpty()) {
            logger.warn("No inferred mappings to process");
            return new ArrayList<>();
        }

        String inferredMappingSetId = OXOInferenceConstants.inferredMappingSetIdFor(sourceMappingSetId);
        List<Mapping> mappings = new ArrayList<>(inferredMappings.size());
        int count = 0;

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
                    inferredMapping.getChainRuleApplications().get().getPremises().size() <= 1) {
                    logger.debug("Skipping mapping with no premises - hence it is an asserted mapping: {}", inferredMapping);
                    continue;
                }

                EntityDetails subjectDetails = solrClient.queryEntityDetailsForIRI(SUBJECT_IRI,
                        inferredMapping.getSubjectIRI().asStringIRI(), SUBJECT_ID, SUBJECT_LABEL);
                String predicateIri = inferredMapping.getPredicateIRI().asStringIRI();
                Optional<String> predicateCurie = PrefixMap.toCurie(predicateIri);
                String predicateId;
                String predicateLabel;
                if (predicateCurie.isPresent()) {
                    predicateId = predicateCurie.get();
                    predicateLabel = "";
                } else {
                    EntityDetails predicateDetails = solrClient.queryEntityDetailsForIRI(PREDICATE_IRI,
                            predicateIri, PREDICATE_ID, PREDICATE_LABEL);
                    predicateId = (predicateDetails != null && predicateDetails.getCurie() != null) ?
                            predicateDetails.getCurie() : "";
                    predicateLabel = (predicateDetails != null && predicateDetails.getLabel() != null) ?
                            predicateDetails.getLabel() : "";
                }
                EntityDetails objectDetails = solrClient.queryEntityDetailsForIRI(OBJECT_IRI,
                        inferredMapping.getObjectIRI().asStringIRI(), OBJECT_ID, OBJECT_LABEL);

                Mapping mapping = new Mapping.Builder()
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
                    .assertedMappings(determineAssertedMappingsForExplanation(inferredMapping))
                    .explanationLength(determineExplanationLength(inferredMapping, 0))
                    .distance(calculateMappingDistance(inferredMapping))
                    .mappingSetId(inferredMappingSetId)
                    .build();
                mappings.add(mapping);

                count++;
                if (count % 1000 == 0) {
                    logger.info("Processed {} mappings", count);
                }
            } catch (Exception e) {
                logger.error("Error creating mapping for: {}", inferredMapping, e);
            }

        }

        return mappings;
    }

    /**
     * When we determine explanations, we only provide a single asserted mapping as evidence for why an inference was made.
     * However, it is possible that the same mapping is asserted in multiple mapping sets. Hence, this method retrieves
     * all asserted mappings. This can be useful for users who need to debug incorrect derived mappings.
     *
     * @param explanation
     * @return
     */
    private static List<InferredMapping> determineAssertedMappingsForExplanation(
            InferredMapping explanation) {

        List<InferredMapping> assertedMappings = new ArrayList<>();

        if (explanation.getChainRuleApplications().isEmpty())
            return assertedMappings;

        InferredMapping.ChainRuleApplications chainRuleApplications = explanation.getChainRuleApplications().get();

        if (chainRuleApplications.getChainRule().isPresent() &&
                chainRuleApplications.getChainRule().get().equals(ChainRulesEnum.ASSERTED))
            assertedMappings.add(explanation);

        explanation.getChainRuleApplications().get().getPremises().forEach(premise -> {
            List<InferredMapping> assertedMappingsToAdd = determineAssertedMappingsForExplanation(premise);
            assertedMappings.addAll(assertedMappingsToAdd);
        });

        return assertedMappings;
    }

    private static int determineExplanationLength(InferredMapping explanation, int startExplanationLength) {
        int explanationLength = startExplanationLength;

        if (explanation.getChainRuleApplications().isEmpty())
            return explanationLength;
        explanationLength++;
        for (InferredMapping premise: explanation.getChainRuleApplications().get().getPremises()) {
            explanationLength = determineExplanationLength(premise, explanationLength);
        }

        return explanationLength;
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

        // Directory mode options (for sequential processing)
        Option nemoInferencesDirectory = new Option("n", "nemoInferencesDirectory", true,
                "The directory containing Nemo traces for inferred mappings");
        nemoInferencesDirectory.setRequired(false);
        options.addOption(nemoInferencesDirectory);

        Option outputDirectory = new Option("o", "outputDirectory", true,
                "Output directory of inferred mappings with explanations for each inferred mapping in JSON format.");
        outputDirectory.setRequired(false);
        options.addOption(outputDirectory);

        Option mappingSetOutputDirectory = new Option("q", "mappingSetOutputDirectory", true,
                "Output directory for inferred MappingSet JSON metadata, one per source mapping set.");
        mappingSetOutputDirectory.setRequired(false);
        options.addOption(mappingSetOutputDirectory);

        Option mappingSetJsonDir = new Option("d", "mappingSetJsonDir", true,
                "Directory containing source MappingSet JSON files (e.g. sssom-as-json/mappingSet) used to resolve source mapping set IDs per chain file.");
        mappingSetJsonDir.setRequired(false);
        options.addOption(mappingSetJsonDir);

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

        return options;
    }

}
