package uk.ac.ebi.spot.oxo.inferences.nemo;

import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.jena.rdf.model.*;
import org.apache.jena.util.FileManager;

import java.io.*;
import java.util.stream.Collectors;

/**
 * Currently Inferences2Trace reads in 2 .ttl files representing mappings. The first file represents original mappings
 * while the second represents mappings along with inferred mappings. Mappings that appear in the inferredMappingsFile
 * and not in the originalMappingsFile represent inferred mappings. To be able to provide a trace of how these mappings
 * have been derived, we generate a file consisting of these mappings in the format required for nemo.
 *
 * In time Nemo will likely provide dump of inferences without needs to determine this ourselves. See
 * https://github.com/knowsys/nemo/issues/668.
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

        String originalMappingsFile = cmd.getOptionValue("originalMappings");
        String originalMappingsPlusInferences = cmd.getOptionValue("inferredMappings");
        String outputFile = cmd.getOptionValue("outputFile");

        logger.info("Original mappings: {}", originalMappingsFile);
        logger.info("Mappings with inferred mappings: {}", originalMappingsPlusInferences);
        logger.info("Output File: {}", outputFile);

        try {
            if (originalMappingsFile == null) {
                processMappings(originalMappingsPlusInferences, outputFile);
            } else {
                processMappings(originalMappingsFile, originalMappingsPlusInferences, outputFile);
            }
        } catch (IOException e) {
            logger.error("Error processing mappings", e);
        }
    }

    private static void processMappings(String originalTTL, String inferredTTL, String outputFile) throws IOException {
        // Load the first Turtle file into a Jena Model
        Model original = FileManager.get().loadModel(originalTTL);

        // Load the second Turtle file into another Jena Model
        Model originalPlusInferences = FileManager.get().loadModel(inferredTTL);

        // Find the difference: triples in originalPlusInferences but not in original
        Model difference = originalPlusInferences.difference(original);

        // Write the resulting triples to the output file
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            String result = difference.listStatements()
                    .toList()
                    .stream()
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


    private static void processMappings(String inferredTTL, String outputFile) throws IOException {
        Model inferences = FileManager.get().loadModel(inferredTTL);

        // Write the resulting triples to the output file
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            String result = inferences.listStatements()
                    .toList()
                    .stream()
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

        Option originalMappingsFile = new Option("i1", "originalMappings", true,
                "Turtle file consisting of original mappings.");
        originalMappingsFile.setRequired(false);
        options.addOption(originalMappingsFile);

        Option inferredMappingsFile = new Option("i2", "inferredMappings", true,
                "Turtle file consisting original and inferred mappings.");
        inferredMappingsFile.setRequired(true);
        options.addOption(inferredMappingsFile);

        Option outputFile = new Option("o", "outputFile", true, "Output file");
        outputFile.setRequired(true);
        options.addOption(outputFile);

        return options;
    }
}