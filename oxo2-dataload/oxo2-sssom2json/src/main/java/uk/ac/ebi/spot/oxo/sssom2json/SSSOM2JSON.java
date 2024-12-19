package uk.ac.ebi.spot.oxo.sssom2json;

import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static uk.ac.ebi.spot.oxo.sssom2json.parser.TSV2JSON.processDirectory;

public class SSSOM2JSON {

    private static final Logger logger = LoggerFactory.getLogger(SSSOM2JSON.class);

    public static void main(String[] args) throws IOException {
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


        processMappingSets(inputDirectory, outputDirectory);
    }

    private static void processMappingSets(String inputDirectory, String outputDirectory) throws IOException {
        Stream<Path> directoriesOfMappingSets = getDirectories(inputDirectory);

        String mappingSetDirectory = outputDirectory + File.separator + "mappingSet";
        String mappingDirectory = outputDirectory + File.separator + "mapping";

        try {
            Files.createDirectories(Paths.get(mappingSetDirectory));
            Files.createDirectories(Paths.get(mappingDirectory));
        } catch (IOException e) {
            logger.error("Error creating output directories {} and {}", mappingDirectory, mappingDirectory, e);
            throw new IOException("Error creating output directories", e);
        }

        directoriesOfMappingSets.forEach(path -> processDirectory(path.toString(), mappingSetDirectory, mappingDirectory));
    }

    private static Stream<Path> getDirectories(String inputDirectory) throws IOException {
        try (Stream<Path> paths = Files.walk(Paths.get(inputDirectory))) {
            return paths.filter(Files::isDirectory).collect(Collectors.toList()).stream();
        } catch (IOException e) {
            logger.error("Error traversing input directory", e);
            throw new IOException("Error traversing input directory", e);
        }
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
