package uk.ac.ebi.spot.oxo.inferences.nemo;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import uk.ac.ebi.spot.oxo.inferences.ApplicablePredicatesEnum;
import uk.ac.ebi.spot.oxo.model.sssom.MappingEnum;
import uk.ac.ebi.spot.oxo.utils.StringUtils;

// To do: Skip negation.


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
        String outputDir = cmd.getOptionValue("outputDir");
        String inputFile = cmd.getOptionValue("inputFile");
        String outputFile = cmd.getOptionValue("outputFile");

        // Validate that either directory mode or file mode is used, but not both or neither
        boolean hasDirMode = (inputDirectory != null && outputDir != null);
        boolean hasFileMode = (inputFile != null && outputFile != null);
        
        if (!hasDirMode && !hasFileMode) {
            logger.error("Either inputDir/outputDir OR inputFile/outputFile must be provided");
            formatter.printHelp("JSON2Turtle", options);
            System.exit(1);
            return;
        }
        
        if (hasDirMode && hasFileMode) {
            logger.error("Cannot use both directory mode (inputDir/outputDir) and file mode (inputFile/outputFile) simultaneously");
            formatter.printHelp("JSON2Turtle", options);
            System.exit(1);
            return;
        }
        
        if (hasDirMode && (inputDirectory == null || outputDir == null)) {
            logger.error("Both inputDir and outputDir must be provided for directory mode");
            formatter.printHelp("JSON2Turtle", options);
            System.exit(1);
            return;
        }
        
        if (hasFileMode && (inputFile == null || outputFile == null)) {
            logger.error("Both inputFile and outputFile must be provided for file mode");
            formatter.printHelp("JSON2Turtle", options);
            System.exit(1);
            return;
        }

        long startTime = System.currentTimeMillis();
        try {
            if (hasDirMode) {
                logger.info("Input Directory: {}", inputDirectory);
                logger.info("Output Directory: {}", outputDir);
                processMappings(inputDirectory, outputDir);
            } else {
                logger.info("Input File: {}", inputFile);
                logger.info("Output File: {}", outputFile);
                generateTTLfromJSON(Paths.get(inputFile), Paths.get(outputFile));
            }
        } catch (Exception e) {
            logger.error("Error processing mappings", e);
            System.exit(0);
        }
        long endTime = System.currentTimeMillis();
        logger.info("Processing took {} s", (endTime - startTime)/1000);
    }

    /**
     * Converts a single JSON file to a TTL (Turtle) file.
     * Reads mappings from the JSON file and writes valid triples to the TTL file.
     * 
     * @param jsonFile Path to the input JSON file
     * @param outputFile Path to the output TTL file
     * @throws IOException if an I/O error occurs
     */
    private static void generateTTLfromJSON(Path jsonFile, Path outputFile) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();

        logger.info("Processing file: {}", jsonFile);
        try (BufferedWriter writer = Files.newBufferedWriter(outputFile);
             JsonParser parser = objectMapper.getFactory().createParser(jsonFile.toFile())) {
            JsonToken firstToken = parser.nextToken();
            if (firstToken != JsonToken.START_ARRAY) {
                return;
            }
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                JsonNode mappingNode = objectMapper.readTree(parser);
                String predicateIRI = mappingNode.get(MappingEnum.PREDICATE_IRI.getField()).asText();
                if (isApplicablePredicate(predicateIRI)) {
                    String subjectIRI = mappingNode.get(MappingEnum.SUBJECT_IRI.getField()).asText();
                    String objectIRI = mappingNode.get(MappingEnum.OBJECT_IRI.getField()).asText();

                    if (!isSkipOnPredicateModifier(mappingNode) && areURIsValid(subjectIRI, predicateIRI, objectIRI))
                        writer.write(String.format("<%s> <%s> <%s> .\n", subjectIRI, predicateIRI, objectIRI));
                }
            }
        }
    }

    /**
     * There is no indication from SSSOM that there is an intent to reason on for example negation.
     * Moreover, SSSOM gives no guidance as to the impact of negation on chain_rules. For this reason we exclude it from
     * all reasoning tasks.
     *
     * @param jsonNode
     * @return
     */
    private static boolean isSkipOnPredicateModifier(JsonNode jsonNode) {
        JsonNode predicateModifier = jsonNode.get(MappingEnum.PREDICATE_MODIFIER.getField());
        if (predicateModifier != null)
            return true;
        else  return false;
    }

    private static void processMappings(String inputDirectory, String outputDirectory) throws IOException {
        try (Stream<Path> paths = Files.walk(Paths.get(inputDirectory))) {
            List<Path> jsonFiles = paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .collect(Collectors.toList());

            for (Path jsonFile : jsonFiles) {
                String outputFile = outputDirectory + File.separator + jsonFile.getFileName().toString().replace(".json", ".ttl");
                try {
                    generateTTLfromJSON(jsonFile, Paths.get(outputFile));
                } catch (Exception e) {
                    logger.error("Error processing file: {}", jsonFile, e);
                }
            }
        } catch (Throwable t) {
            logger.error("Error processing directory: {}", inputDirectory, t);
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
        inputDirectory.setRequired(false);
        options.addOption(inputDirectory);

        Option outputDirectory = new Option("o", "outputDir", true, "Output directory for turtle files");
        outputDirectory.setRequired(false);
        options.addOption(outputDirectory);

        Option inputFile = new Option("f", "inputFile", true, "Input JSON file");
        inputFile.setRequired(false);
        options.addOption(inputFile);

        Option outputFile = new Option("p", "outputFile", true, "Output TTL file");
        outputFile.setRequired(false);
        options.addOption(outputFile);        

        return options;
    }
}