package uk.ac.ebi.spot.oxo.inferences.nemo;

import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.jena.rdf.model.*;
import org.apache.jena.util.FileManager;

import java.io.*;
import java.util.stream.Collectors;

/**
 * Currently Inferences2Trace reads a .ttl file of inferred inferences and then writes them out in the format necessary for
 * tracing explanations for inferred facts.
 *
 */

public class Inferences2Trace {

    private static final Logger logger = LoggerFactory.getLogger(Inferences2Trace.class);

    public static void main(String[] args) {
        Options options = getOptions();
        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            logger.error("Error parsing command line arguments", e);
            formatter.printHelp("TurtleResult2Trace", options);
            System.exit(1);
            return;
        }

        String inferredMappingsToTrace = cmd.getOptionValue("inferredMappings");
        String outputFile = cmd.getOptionValue("outputFile");

        logger.info("Mappings with inferred mappings: {}", inferredMappingsToTrace);
        logger.info("Output File: {}", outputFile);

        try {
             processMappings(inferredMappingsToTrace, outputFile);
        } catch (IOException e) {
            logger.error("Error processing mappings", e);
        }
    }

    private static void processMappings(String inferredTTL, String outputFile) throws IOException {
        Model inferences = FileManager.get().loadModel(inferredTTL);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
                 String result = inferences.listStatements()
                    .toList()
                    .stream()
                    .filter(statement -> statement.getSubject().toString().compareTo(statement.getObject().toString()) != 0)
                    .map(statement -> {
                        String subject = statement.getSubject().toString();
                        String predicate = statement.getPredicate().toString();
                        String object = statement.getObject().toString();
                        return String.format("mapping(<%s>,<%s>,<%s>)", subject, predicate, object);
                    })
                    .collect(Collectors.joining(";"));                    

            writer.write(result);
        }
    }

    private static Options getOptions() {
        Options options = new Options();

        Option inferredMappingsFile = new Option("i", "inferredMappings", true,
                "Turtle file consisting original and inferred mappings.");
        inferredMappingsFile.setRequired(true);
        options.addOption(inferredMappingsFile);

        Option outputFile = new Option("o", "outputFile", true, "Output file");
        outputFile.setRequired(true);
        options.addOption(outputFile);

        return options;
    }
}