package uk.ac.ebi.spot.oxo.sssom2json;

import static uk.ac.ebi.spot.oxo.sssom2json.parser.TSV2JSON.extractSubjectIris;
import static uk.ac.ebi.spot.oxo.sssom2json.parser.TSV2JSON.processDirectory;
import static uk.ac.ebi.spot.oxo.sssom2json.parser.TSV2JSON.processFile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
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

import uk.ac.ebi.spot.oxo.model.sssom.MappingSetCategory;

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

        String inputFile = cmd.getOptionValue("inputFile");
        String inputDirectory = cmd.getOptionValue("inputDir");
        String outputDirectory = cmd.getOptionValue("outputDir");

        // The OxO curation category of the registry these TSVs came from (ADR-0027). It is not in the
        // SSSOM data, so the caller — sssom2json.nf, which knows the config — supplies it. An untagged
        // registry is CURATED, so that is also the default when the option is omitted.
        MappingSetCategory mappingSetCategory = MappingSetCategory.DEFAULT;
        String categoryOption = cmd.getOptionValue("category");
        if (categoryOption != null && !categoryOption.isBlank()) {
            try {
                mappingSetCategory = MappingSetCategory.fromCode(categoryOption);
            } catch (IllegalArgumentException e) {
                logger.error("Invalid --category '{}'. Expected one of {}.",
                        categoryOption, java.util.Arrays.toString(MappingSetCategory.values()));
                System.exit(1);
                return;
            }
        }

        if (inputFile != null && inputDirectory != null) {
            logger.error("Cannot specify both inputFile and inputDir");
            formatter.printHelp("SSSOM2JSON", options);
            System.exit(1);
            return;
        }

        if (inputFile == null && inputDirectory == null) {
            logger.error("Must specify either inputFile or inputDir");
            formatter.printHelp("SSSOM2JSON", options);
            System.exit(1);
            return;
        }

        // ADR-0041, Pass 1: emit this input's distinct subject IRIs and stop. sssom2json.nf runs this
        // over the obsolete-flagged registries and unions the results into the global obsolete-entity set.
        if (cmd.hasOption("extract-obsolete-entities")) {
            extractObsoleteEntities(inputFile, inputDirectory, outputDirectory);
            return;
        }

        // ADR-0041, Pass 2 inputs: the set-level flag for this run's registry, and the global
        // obsolete-entity IRI set that both mapping endpoints are stamped against. Absent on a normal load.
        boolean setObsolete = cmd.hasOption("obsolete");
        Set<String> obsoleteEntityIris = loadObsoleteEntities(cmd.getOptionValue("obsolete-entities"));

        logger.info("Output Directory: {}", outputDirectory);
        logger.info("Mapping Set Category: {}", mappingSetCategory.getCode());
        logger.info("Set obsolete: {}; obsolete-entity IRIs loaded: {}", setObsolete, obsoleteEntityIris.size());

        long startTime = System.currentTimeMillis();
        if (inputFile != null) {
            logger.info("Input File: {}", inputFile);
            processSingleFile(inputFile, outputDirectory, mappingSetCategory, obsoleteEntityIris, setObsolete);
        } else {
            logger.info("Input Directory: {}", inputDirectory);
            processMappingSets(inputDirectory, outputDirectory, mappingSetCategory, obsoleteEntityIris, setObsolete);
        }
        long endTime = System.currentTimeMillis();

        logger.info("Time taken to process SSSOM files: {} s", (endTime - startTime) / 1000);
    }

    private static void processSingleFile(String inputFile, String outputDirectory,
                                          MappingSetCategory mappingSetCategory,
                                          Set<String> obsoleteEntityIris, boolean setObsolete) throws IOException {
        File tsvFile = new File(inputFile);
        if (!tsvFile.exists() || !tsvFile.isFile()) {
            throw new IOException("Input file does not exist or is not a file: " + inputFile);
        }

        String mappingSetDirectory = outputDirectory + File.separator + "mappingSet";
        String mappingDirectory = outputDirectory + File.separator + "mapping";

        try {
            Files.createDirectories(Paths.get(mappingSetDirectory));
            Files.createDirectories(Paths.get(mappingDirectory));
        } catch (IOException e) {
            logger.error("Error creating output directories {} and {}", mappingSetDirectory, mappingDirectory, e);
            throw new IOException("Error creating output directories", e);
        }

        try {
            processFile(tsvFile, mappingSetDirectory, mappingDirectory, mappingSetCategory,
                    obsoleteEntityIris, setObsolete);
        } catch (Throwable t) {
            logger.error("Error processing file {}", tsvFile, t);
        }

        long usedMemoryBytes = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        double usedMemoryMB = usedMemoryBytes / (1024.0 * 1024.0);
        logger.info("Memory used: {} MB", usedMemoryMB);
    }

    /**
     * Walks every sub-directory of {@code inputDirectory} and applies the one {@code mappingSetCategory}
     * to all of them. The sssom root holds one sub-directory per config registry, and registries may
     * differ in category, so this whole-tree mode is only correct for a single-registry tree. The
     * supported dataload path (sssom2json.nf, ADR-0003) invokes the per-file mode instead, with the
     * category resolved from the config for that file's registry.
     */
    private static void processMappingSets(String inputDirectory, String outputDirectory,
                                           MappingSetCategory mappingSetCategory,
                                           Set<String> obsoleteEntityIris, boolean setObsolete) throws IOException {
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

        directoriesOfMappingSets.forEach(path ->
                processDirectory(path.toString(), mappingSetDirectory, mappingDirectory, mappingSetCategory,
                        obsoleteEntityIris, setObsolete));

        long usedMemoryBytes = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        double usedMemoryMB = usedMemoryBytes / (1024.0 * 1024.0);
        logger.info("Memory used: {} MB", usedMemoryMB);

        usedMemoryBytes = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        usedMemoryMB = usedMemoryBytes / (1024.0 * 1024.0);
        logger.info("Memory used: {} MB", usedMemoryMB);
    }

    private static Stream<Path> getDirectories(String inputDirectory) throws IOException {
        try (Stream<Path> paths = Files.walk(Paths.get(inputDirectory))) {
            return paths
                    .filter(Files::isDirectory)
                    .map(path -> {
                        try {
                            return path.toRealPath();
                        } catch (IOException e) {
                            return path.toAbsolutePath().normalize();
                        }
                    })
                    .distinct()
                    .collect(Collectors.toList())
                    .stream();
        } catch (IOException e) {
            logger.error("Error traversing input directory", e);
            throw new IOException("Error traversing input directory", e);
        }
    }

    /**
     * Pass 1 of the obsolete-terms dataload (ADR-0041): write the distinct subject IRIs of the input
     * (a single TSV, or every {@code .tsv} under a directory) to {@code <outputDir>/obsolete-entities.txt},
     * one per line, sorted. sssom2json.nf runs this over the obsolete-flagged registries and concatenates
     * the per-file outputs into the global obsolete-entity set fed back to the main (Pass 2) run.
     */
    private static void extractObsoleteEntities(String inputFile, String inputDirectory,
                                                String outputDirectory) throws IOException {
        Set<String> subjectIris = new TreeSet<>();
        if (inputFile != null) {
            subjectIris.addAll(extractSubjectIris(new File(inputFile)));
        } else {
            try (Stream<Path> paths = Files.walk(Paths.get(inputDirectory))) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".tsv"))
                        .forEach(path -> subjectIris.addAll(extractSubjectIris(path.toFile())));
            }
        }
        Path outputPath = Paths.get(outputDirectory, "obsolete-entities.txt");
        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }
        String content = subjectIris.stream().map(iri -> iri + "\n").collect(Collectors.joining());
        Files.writeString(outputPath, content, StandardCharsets.UTF_8);
        logger.info("Extracted {} distinct obsolete subject IRIs to {}", subjectIris.size(), outputPath);
    }

    /**
     * Load the global obsolete-entity IRI set (ADR-0041) from the Pass-1 file: one IRI per line, blanks
     * ignored. A null/blank path or missing file yields an empty set — a load with no obsolete registry.
     */
    private static Set<String> loadObsoleteEntities(String obsoleteEntitiesFile) throws IOException {
        Set<String> obsoleteEntityIris = new HashSet<>();
        if (obsoleteEntitiesFile == null || obsoleteEntitiesFile.isBlank()) {
            return obsoleteEntityIris;
        }
        Path path = Paths.get(obsoleteEntitiesFile);
        if (!Files.exists(path)) {
            logger.warn("Obsolete-entity file {} does not exist; treating the obsolete set as empty.", path);
            return obsoleteEntityIris;
        }
        try (Stream<String> lines = Files.lines(path, StandardCharsets.UTF_8)) {
            lines.map(String::trim).filter(line -> !line.isEmpty()).forEach(obsoleteEntityIris::add);
        }
        return obsoleteEntityIris;
    }

    private static Options getOptions() {
        Options options = new Options();

        Option inputFile = new Option("f", "inputFile", true, "Input TSV file to process");
        inputFile.setRequired(false);
        options.addOption(inputFile);

        Option inputDirectory = new Option("i", "inputDir", true, "Input directory containing SSSOM files");
        inputDirectory.setRequired(false);
        options.addOption(inputDirectory);

        Option outputDirectory = new Option("o", "outputDir", true, "Output directory for JSON files");
        outputDirectory.setRequired(true);
        options.addOption(outputDirectory);

        Option category = new Option("c", "category", true,
                "OxO curation category of the registry these SSSOM files came from: "
                        + "ONTOLOGY or CURATED (default). Applies to every file processed in this run.");
        category.setRequired(false);
        options.addOption(category);

        // ADR-0041: obsolete-terms support. The set-level flag marks this run's registry obsolete; the
        // entity list is the global obsolete-entity IRI set (Pass 1's output) against which both mapping
        // endpoints are stamped; the extract flag switches to Pass 1 — emit this file's subject IRIs.
        Option obsolete = new Option("s", "obsolete", false,
                "Mark this run's mapping set(s) as obsolete (all their subjects are obsolete terms).");
        obsolete.setRequired(false);
        options.addOption(obsolete);

        Option obsoleteEntities = new Option("b", "obsolete-entities", true,
                "Path to the global obsolete-entity IRI list (one IRI per line): a mapping endpoint whose "
                        + "IRI is in this list is stamped obsolete. Omit on a load with no obsolete registry.");
        obsoleteEntities.setRequired(false);
        options.addOption(obsoleteEntities);

        Option extractObsoleteEntities = new Option("x", "extract-obsolete-entities", false,
                "Pass 1: instead of producing mapping JSON, write the input's distinct subject IRIs to "
                        + "<outputDir>/obsolete-entities.txt (one per line).");
        extractObsoleteEntities.setRequired(false);
        options.addOption(extractObsoleteEntities);

        return options;
    }
}