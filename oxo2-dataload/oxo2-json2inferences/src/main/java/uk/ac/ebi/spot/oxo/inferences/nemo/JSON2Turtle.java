package uk.ac.ebi.spot.oxo.inferences.nemo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.spot.oxo.inferences.ApplicablePredicatesEnum;
import uk.ac.ebi.spot.oxo.model.sssom.MappingEnum;
import uk.ac.ebi.spot.oxo.utils.StringUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JSON2Turtle {

    private static final Logger logger = LoggerFactory.getLogger(JSON2Turtle.class);

    public static void main(String[] args) {
        Options options = getOptions();
        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            logger.error("Error parsing command line arguments", e);
            formatter.printHelp("JSON2Turtle", options);
            System.exit(1);
            return;
        }

        String inputDirectory = cmd.getOptionValue("inputDir");
        String outputFile = cmd.getOptionValue("outputFile");

        logger.info("Input Directory: {}", inputDirectory);
        logger.info("Output File: {}", outputFile);

        long startTime = System.currentTimeMillis();
        try {
            processMappings(inputDirectory, outputFile);
        } catch (Exception e) {
            logger.error("Error processing mappings", e);
        }
        long endTime = System.currentTimeMillis();
        logger.info("Processing took {} s", (endTime - startTime)/1000);
    }

    private static void processMappings(String inputDirectory, String outputFile) throws IOException {

        ObjectMapper objectMapper = new ObjectMapper();
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputFile))) {

            try (Stream<Path> paths = Files.walk(Paths.get(inputDirectory))) {
                List<Path> jsonFiles = paths.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".json"))
                        .collect(Collectors.toList());

                for (Path jsonFile : jsonFiles) {
                    logger.info("Processing file: {}", jsonFile);
                    try {
                        JsonNode rootNode = objectMapper.readTree(jsonFile.toFile());
                        if (rootNode.isArray()) {
                            for (JsonNode mappingNode : rootNode) {
                                String predicateIRI = mappingNode.get(MappingEnum.PREDICATE_IRI.getField()).asText();
                                if (isApplicablePredicate(predicateIRI)) {
                                    String subjectIRI = mappingNode.get(MappingEnum.SUBJECT_IRI.getField()).asText();
                                    String objectIRI = mappingNode.get(MappingEnum.OBJECT_IRI.getField()).asText();
                                    if (areURIsValid(subjectIRI, predicateIRI, objectIRI))
                                        writer.write(String.format("<%s> <%s> <%s> .\n", subjectIRI, predicateIRI, objectIRI));
                                }
                            }
                        }
                    } catch (Exception e) {
                        logger.error("Error processing file: {}", jsonFile, e);
                    }
                }
            } catch (Throwable t) {
                logger.error("Error processing file: {}", outputFile, t);
            }
        }
    }

    private static boolean areURIsValid(String subjectIRI, String predicateIRI, String objectIRI) {
        if (!StringUtils.isURIValid(subjectIRI) || !StringUtils.isURIValid(predicateIRI)
                || !StringUtils.isURIValid(objectIRI))
            return false;
        return true;
    }

    private static boolean isApplicablePredicate(String predicateIRI) {
        for (ApplicablePredicatesEnum predicate : ApplicablePredicatesEnum.values()) {
            if (predicate.getIri().equals(predicateIRI)) {
                return true;
            }
        }
        return false;
    }

    private static Options getOptions() {
        Options options = new Options();

        Option inputDirectory = new Option("i", "inputDir", true, "Input directory containing JSON files");
        inputDirectory.setRequired(true);
        options.addOption(inputDirectory);

        Option outputFile = new Option("o", "outputFile", true, "Output CSV file");
        outputFile.setRequired(true);
        options.addOption(outputFile);

        return options;
    }
}