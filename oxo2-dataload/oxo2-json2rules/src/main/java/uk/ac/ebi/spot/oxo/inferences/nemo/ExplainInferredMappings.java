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

        String inferencesToParse = cmd.getOptionValue("inferencesToParse");
        String outputFile = cmd.getOptionValue("outputFile");

        logger.info("Input File:  {}", inferencesToParse);
        logger.info("Output File: {}", outputFile);

        long startTime = System.currentTimeMillis();
        try {
            NemoInferences nemoInferences = NemoInferenceReader.readInferences(inferencesToParse);
            Set<InferredMapping> inferredMappings = NemoHelper.fromNemoInferencesToInferredMappings(nemoInferences);
            List<Mapping> mappings = createMappings(inferredMappings);

            writeMappingsAsJson(mappings, outputFile);
        } catch (Exception e) {
            logger.error("Error processing mappings", e);
        }
        long endTime = System.currentTimeMillis();
        logger.info("Processing took {} s", (endTime - startTime)/1000);
    }

    public static void writeMappingsAsJson(List<Mapping> mappings, String outputFile) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new Jdk8Module());
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(new java.io.File(outputFile), mappings);
            logger.info("Mappings successfully written to {}", outputFile);
        } catch (Exception e) {
            logger.error("Error writing mappings to JSON file", e);
        }
    }

    public static List<Mapping> createMappings(Set<InferredMapping> inferredMappings) {
        List<Mapping> mappings = new ArrayList<>(inferredMappings.size());
        for (InferredMapping inferredMapping : inferredMappings) {
            List explanations = getExplanations(inferredMapping);
            Mapping mapping = new Mapping.Builder()
                    .subjectId(inferredMapping.getSubjectIRI().asStringIRI())
                    .predicateId(inferredMapping.getPredicateIRI().asStringIRI())
                    .objectId(inferredMapping.getObjectIRI().asStringIRI())
                    .mappingJustification(inferredMapping.getMappingJustification())
                    .mappingTool(inferredMapping.getMappingTool())
                    .explanation(explanations)
//                    .distance(explanations.size()+1)
                    .mappingSetId("https://www.ebi.ac.uk/spot/oxo/inferences/")
                    .build();
            mappings.add(mapping);
        }
        return mappings;
    }

    private static List<Explanation> getExplanations(InferredMapping inferredMapping) {
        List<Explanation> explanations = new LinkedList<>();

        if (inferredMapping.getChainRuleApplications().isPresent()) {
            InferredMapping.ChainRuleApplications chainRuleApplications = inferredMapping.getChainRuleApplications().get();
            Explanation explanation = new Explanation(
                inferredMapping.getAsConclusion(), chainRuleApplications.getAsPremises(),
                    chainRuleApplications.getChainRule());
            explanations.add(explanation);

            chainRuleApplications.getPremises().stream()
                    .filter(p -> p.getChainRuleApplications().isPresent())
                    .forEach(premise -> {
                explanations.addAll(getExplanations(premise));
            });
        } else {
            logger.error("Chain rule applications not present");
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
