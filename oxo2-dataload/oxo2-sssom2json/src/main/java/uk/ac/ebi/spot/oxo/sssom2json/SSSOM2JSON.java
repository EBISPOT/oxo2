package uk.ac.ebi.spot.oxo.sssom2json;

import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.File;
import java.io.IOException;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static uk.ac.ebi.spot.oxo.sssom2json.parser.TSV2JSON.processDirectory;

/**
 * @Todo:
 * 1. Parallelize SSSOM2JSON
 */
public class SSSOM2JSON {

    private static final Logger logger = LoggerFactory.getLogger(SSSOM2JSON.class);

    public static class EntityDetails implements Comparable<EntityDetails> {
        private String curie;
        private String iri;
        private String label;

        public String getCurie() {
            return curie;
        }

        public void setCurie(String curie) {
            this.curie = curie;
        }

        public String getIri() {
            return iri;
        }

        public void setIri(String iri) {
            this.iri = iri;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            EntityDetails that = (EntityDetails) o;
            return compareTo(that) == 0;
        }

        @Override
        public int hashCode() {
            return Objects.hash(getCurie(), getIri(), getLabel());
        }

        @Override
        public int compareTo(EntityDetails other) {
            int compare = nullSafeStringCompare(this.curie, other.curie);
            if (compare != 0) return compare;
            compare = nullSafeStringCompare(this.iri, other.iri);
            if (compare != 0) return compare;
            return nullSafeStringCompare(this.label, other.label);
        }

        private static int nullSafeStringCompare(String s1, String s2) {
            if (s1 == null && s2 == null) return 0;
            if (s1 == null) return -1;
            if (s2 == null) return 1;
            return s1.compareTo(s2);
        }
    }

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

        long startTime = System.currentTimeMillis();
        processMappingSets(inputDirectory, outputDirectory);
        long endTime = System.currentTimeMillis();

        logger.info("Time taken to process SSSOM files: {} s", (endTime - startTime)/1000);
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

        long usedMemoryBytes = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        double usedMemoryMB = usedMemoryBytes / (1024.0 * 1024.0);
        logger.info("Memory used: {} MB", usedMemoryMB);

        usedMemoryBytes = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        usedMemoryMB = usedMemoryBytes / (1024.0 * 1024.0);
        logger.info("Memory used: {} MB", usedMemoryMB);
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