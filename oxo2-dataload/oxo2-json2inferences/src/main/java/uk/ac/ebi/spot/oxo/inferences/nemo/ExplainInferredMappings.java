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
import uk.ac.ebi.spot.oxo.inferences.nemo.helpers.NemoHelper;
import uk.ac.ebi.spot.oxo.inferences.nemo.model.NemoInferences;
import uk.ac.ebi.spot.oxo.model.sssom.Explanation;
import uk.ac.ebi.spot.oxo.model.sssom.InferredMapping;
import uk.ac.ebi.spot.oxo.model.sssom.Mapping;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

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

        String inferencesToParse = cmd.getOptionValue("inputFile");
        String outputFile = cmd.getOptionValue("outputFile");

        logger.info("Input File:  {}", inferencesToParse);
        logger.info("Output File: {}", outputFile);

        // Validate input file
        File inputFile = new File(inferencesToParse);
        if (!inputFile.exists() || !inputFile.isFile()) {
            logger.error("Input file does not exist or is not a file: {}", inferencesToParse);
            System.exit(1);
            return;
        }

        // Create output directory if it doesn't exist
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
        try {
            logger.info("Reading inferences from file...");
            NemoInferences nemoInferences = NemoInferenceReader.readInferences(inferencesToParse);
            if (nemoInferences == null) {
                logger.error("Failed to read inferences from file or file is empty");
                System.exit(1);
                return;
            }
            
            logger.info("Converting to inferred mappings...");
            Set<InferredMapping> inferredMappings = NemoHelper.fromNemoInferencesToInferredMappings(nemoInferences);
            if (inferredMappings.isEmpty()) {
                logger.warn("No inferred mappings were generated");
            } else {
                logger.info("Generated {} inferred mappings", inferredMappings.size());
            }
            
            logger.info("Creating mappings...");
            List<Mapping> mappings = createMappings(inferredMappings);
            logger.info("Created {} mappings", mappings.size());

            logger.info("Writing mappings to file...");
            writeMappingsAsJson(mappings, outputFile);
            logger.info("Successfully completed processing");
        } catch (Exception e) {
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
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        
        File file = new File(outputFile);
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, mappings);
            logger.info("Mappings successfully written to {} ({} bytes)", outputFile, file.length());
        } catch (IOException e) {
            logger.error("Error writing mappings to JSON file: {}", e.getMessage());
            throw e;
        }
    }

    public static List<Mapping> createMappings(Set<InferredMapping> inferredMappings) {
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
                
                List<Explanation> explanations = getExplanations(inferredMapping);
                Mapping mapping = new Mapping.Builder()
                        .subjectId(inferredMapping.getSubjectIRI().asStringIRI())
                        .predicateId(inferredMapping.getPredicateIRI().asStringIRI())
                        .objectId(inferredMapping.getObjectIRI().asStringIRI())
                        .mappingJustification(inferredMapping.getMappingJustification())
                        .mappingTool(inferredMapping.getMappingTool())
                        .explanation(explanations)
                        .explanationLength(explanations.size() + 1)
                        .distance(calculateMappingDistance(explanations))
                        .mappingSetId("https://www.ebi.ac.uk/spot/oxo/inferences/")
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

    private static int calculateMappingDistance(List<Explanation> explanations) {
        Set<String> extractedParts = new HashSet<>();

        explanations.forEach(explanation -> {
            String conclusion = explanation.getConclusion();
            extractParts(conclusion, extractedParts);

            explanation.getPremises().forEach(premise -> {
                extractParts(premise, extractedParts);
            });
        });

        return extractedParts.size() - 1;
    }

    private static void extractParts(String input, Set<String> extractedParts) {
        if (input != null) {
            String[] parts = input.split("/");
            for (String part : parts) {
                if (part.contains("_")) {
                    extractedParts.add(part.split("_")[0]);
                }
            }
        }
    }

    private static List<Explanation> getExplanations(InferredMapping inferredMapping) {
        List<Explanation> explanations = new LinkedList<>();

        if (inferredMapping == null) {
            logger.warn("Inferred mapping is null, cannot create explanations");
            return explanations;
        }

        if (inferredMapping.getChainRuleApplications().isPresent()) {
            InferredMapping.ChainRuleApplications chainRuleApplications = inferredMapping.getChainRuleApplications().get();

            try {
                Explanation explanation = new Explanation(
                    inferredMapping.getAsConclusion(), 
                    chainRuleApplications.getAsPremises(),
                    chainRuleApplications.getChainRule());
                    
                if (!Explanation.doesConclusionExistAlready(explanations, explanation))
                    explanations.add(explanation);

                chainRuleApplications.getPremises().stream()
                        .filter(p -> p.getChainRuleApplications().isPresent())
                        .forEach(premise -> {
                    explanations.addAll(getExplanations(premise));
                });
            } catch (Exception e) {
                logger.error("Error creating explanation for mapping: {}", inferredMapping, e);
            }
        } else {
            logger.debug("Chain rule applications not present for mapping: {}", inferredMapping);
        }

        return explanations;
    }

    private static Options getOptions() {
        Options options = new Options();

        Option inputFile = new Option("i", "inputFile", true,
                "The file containing Nemo trace for inferred mappings");
        inputFile.setRequired(true);
        options.addOption(inputFile);

        Option outputFile = new Option("o", "outputFile", true,
                "JSON output file of inferred mappings with explanations for each inferred mapping.");
        outputFile.setRequired(true);
        options.addOption(outputFile);

        return options;
    }

}
