package uk.ac.ebi.spot.oxo.solr;

import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Stream;

public class SSSOM2JSON {

    private static final Logger logger = LoggerFactory.getLogger(SSSOM2JSON.class);

    public static void main(String[] args) {
        Options options = getOptions();
        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            logger.error("Error parsing command line arguments", e);
            formatter.printHelp("SSSOM2JSON", options);
            System.exit(1);
            return;
        }

        String inputDirectory = cmd.getOptionValue("inputDir");
        String outputDirectory = cmd.getOptionValue("outputDir");

        logger.info("Input Directory: {}", inputDirectory);
        logger.info("Output Directory: {}", outputDirectory);


    }


    private static void processMappingSets(String inputDirectory, String outputDirectory) {
        Collection<Path> directoriesOfMappingSets = getDirectories(inputDirectory);
        for (Path directory : directoriesOfMappingSets) {
            logger.info("Processing directory: {}", directory);
            // 1. Check for external metadata
            // 2. Parse metadata
            // 3. Parse mappings
            // 4. Write mappings to JSON
        }

    }

    private static Collection<Path> getDirectories(String inputDirectory) {
        try (Stream<Path> paths = Files.walk(Paths.get(inputDirectory))) {
            return paths.filter(Files::isDirectory).toList();
        } catch (IOException e) {
            logger.error("Error traversing input directory", e);
        }
        return new ArrayList<>();
    }

    private static Options getOptions() {
        Options options = new Options();

        Option inputDirectory = new Option("i", "inputDir", true, "Input directory containing SSSOM files");
        inputDirectory.setRequired(true);
        options.addOption(inputDirectory);

        Option outputDirectory = new Option("o", "outputDir", true, "Output directory for JSON files");
        outputDirectory.setRequired(true);
        options.addOption(outputDirectory);

        return options;
    }
}
