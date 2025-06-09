package uk.ac.ebi.spot.oxo.inferences.nemo;

import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Collectors;

public class Result2InferenceToTrace {

    private static final Logger logger = LoggerFactory.getLogger(Result2InferenceToTrace.class);

    public static void main(String[] args) {
        Options options = getOptions();
        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            logger.error("Error parsing command line arguments", e);
            formatter.printHelp("Result2InferenceToTrace", options);
            System.exit(1);
            return;
        }

        String inputCsvFile = cmd.getOptionValue("inputFile");
        String outputFile = cmd.getOptionValue("outputFile");

        logger.info("Input CSV File: {}", inputCsvFile);
        logger.info("Output File: {}", outputFile);

        try {
            processMappings(inputCsvFile, outputFile);
        } catch (IOException e) {
            logger.error("Error processing mappings", e);
        }
    }

    private static void processMappings(String inputCsvFile, String outputFile) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(inputCsvFile));
             BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputFile))) {

            String result = reader.lines()
                    .map(line -> {
                        String[] parts = line.split(",");
                        if (parts.length == 3) {
                            return String.format("mapping(<%s>,<%s>,<%s>)", parts[0], parts[1], parts[2]);
                        } else {
                            throw new IllegalArgumentException("Invalid CSV format: " + line);
                        }
                    })
                    .collect(Collectors.joining(";"));

            writer.write(result);
        }
    }

    private static Options getOptions() {
        Options options = new Options();

        Option inputFile = new Option("i", "inputFile", true, "Input CSV file");
        inputFile.setRequired(true);
        options.addOption(inputFile);

        Option outputFile = new Option("o", "outputFile", true, "Output file");
        outputFile.setRequired(true);
        options.addOption(outputFile);

        return options;
    }
}
