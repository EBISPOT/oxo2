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

import java.io.File;
import java.io.IOException;
import java.util.*;

import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.*;

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

        String nemoInferencesToParse = cmd.getOptionValue("nemoInferences");
        String outputFile = cmd.getOptionValue("outputFile");

        logger.info("nemoInferences:  {}", nemoInferencesToParse);
        logger.info("Output File: {}", outputFile);

        File inputFile = new File(nemoInferencesToParse);
        if (!inputFile.exists() || !inputFile.isFile()) {
            logger.error("Input file does not exist or is not a file: {}", nemoInferencesToParse);
            System.exit(1);
            return;
        }

        File output = new File(outputFile);
        File outputDir = output.getParentFile();
        if (outputDir != null && !outputDir.exists()) {
            if (!outputDir.mkdirs()) {
                logger.error("Failed to create output directory: {}", outputDir.getAbsolutePath());
                System.exit(1);
                return;
            }
        }

        long startTime = System.currentTimeMillis();
        DataloadSolr solrClient = null;
        try {
            logger.info("Reading inferences from file...");
            NemoInferences nemoInferences = NemoInferenceReader.readInferences(nemoInferencesToParse);
            if (nemoInferences == null) {
                logger.error("Failed to read inferences from file or file is empty");
                System.exit(1);
                return;
            }

            logger.info("Converting to inferred mappings...");

            solrClient = new DataloadSolr();
            Set<InferredMapping> inferredMappings = NemoHelper.fromNemoInferencesToInferredMappings(
                    nemoInferences, solrClient);
            if (inferredMappings.isEmpty()) {
                logger.warn("No inferred mappings were generated");
            } else {
                logger.info("Generated {} inferred mappings", inferredMappings.size());
            }
            
            logger.info("Creating mappings...");
            List<Mapping> mappings = createMappings(inferredMappings, solrClient);
            logger.info("Created {} mappings", mappings.size());

            logger.info("Writing mappings to file...");
            writeMappingsAsJson(mappings, outputFile);
            logger.info("Successfully completed processing");
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
        logger.info("Processing took {} s", (endTime - startTime)/1000);
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
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, mappings);
            logger.info("Mappings successfully written to {} ({} bytes)", outputFile, file.length());
        } catch (IOException e) {
            logger.error("Error writing mappings to JSON file = {}, {}", file.getName(), e.getMessage());
            throw e;
        }
    }

    public static List<Mapping> createMappings(Set<InferredMapping> inferredMappings,
                                               DataloadSolr solrClient) {
        if (inferredMappings == null || inferredMappings.isEmpty()) {
            logger.warn("No inferred mappings to process");
            return new ArrayList<>();
        }
        
        List<Mapping> mappings = new ArrayList<>(inferredMappings.size());
        int count = 0;
        
        for (InferredMapping inferredMapping : inferredMappings) {
            try {
                if (inferredMapping.getSubjectIRI() == null || inferredMapping.getPredicateIRI() == null || 
                    inferredMapping.getObjectIRI() == null) {
                    logger.warn("Skipping mapping with null IRI values: {}", inferredMapping);
                    continue;
                }

                EntityDetails subjectDetails = solrClient.queryEntityDetailsForIRI(SUBJECT_IRI,
                        inferredMapping.getSubjectIRI().asStringIRI(), SUBJECT_ID, SUBJECT_LABEL);
                EntityDetails predicateDetails = solrClient.queryEntityDetailsForIRI(PREDICATE_IRI,
                        inferredMapping.getPredicateIRI().asStringIRI(), PREDICATE_ID, PREDICATE_LABEL);
                EntityDetails objectDetails = solrClient.queryEntityDetailsForIRI(OBJECT_IRI,
                        inferredMapping.getObjectIRI().asStringIRI(), OBJECT_ID, OBJECT_LABEL);

                Mapping mapping = new Mapping.Builder()
                    .subjectIRI(inferredMapping.getSubjectIRI().asStringIRI())
                    .subjectId((subjectDetails != null && subjectDetails.getCurie() != null) ?
                            subjectDetails.getCurie() : "")
                    .subjectLabel((subjectDetails != null && subjectDetails.getLabel() != null) ?
                            subjectDetails.getLabel() : "")
                    .predicateIRI(inferredMapping.getPredicateIRI().asStringIRI())
                    .predicateId((predicateDetails != null && predicateDetails.getCurie() != null) ?
                            predicateDetails.getCurie() : "")
                    .predicateLabel((predicateDetails != null && predicateDetails.getLabel() != null) ?
                            predicateDetails.getLabel() : "")
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
                    .mappingSetId(OXOInferenceConstants.OXO_MAPPING_SET_ID)
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

        Option nemoInferences = new Option("n", "nemoInferences", true,
                "The file containing Nemo traces for inferred mappings");
        nemoInferences.setRequired(true);
        options.addOption(nemoInferences);

        Option outputFile = new Option("o", "outputFile", true,
                "JSON output file of inferred mappings with explanations for each inferred mapping.");
        outputFile.setRequired(true);
        options.addOption(outputFile);

        return options;
    }

}