package uk.ac.ebi.spot.oxo.sssom2json.parser;

import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.AUTHOR_ID;
import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.AUTHOR_LABEL;
import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.COMMENT;
import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.CONFIDENCE;
import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.CREATOR_ID;
import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.CREATOR_LABEL;
import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.CURATION_RULE;
import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.ISSUE_TRACKER_ITEM;
import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.LICENSE;
import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.MAPPING_CARDINALITY;
import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.MAPPING_DATE;
import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.MAPPING_JUSTIFICATION;
import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.MAPPING_PROVIDER;
import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.MAPPING_SOURCE;
import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.MAPPING_TOOL;
import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.MAPPING_TOOL_VERSION;
import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.MATCH_STRING;
import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.OBJECT_CATEGORY;
import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.OBJECT_ID;
import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.OBJECT_LABEL;
import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.OBJECT_MATCH_FIELD;
import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.OBJECT_PREPROCESSING;
import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.OBJECT_SOURCE;
import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.OBJECT_SOURCE_VERSION;
import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.OBJECT_TYPE;
import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.OTHER;
import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.PREDICATE_ID;
import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.PREDICATE_LABEL;
import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.PREDICATE_MODIFIER;
import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.PUBLICATION_DATE;
import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.REVIEWER_ID;
import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.REVIEWER_LABEL;
import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.SEE_ALSO;
import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.SIMILARITY_MEASURE;
import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.SIMILARITY_SCORE;
import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.SUBJECT_CATEGORY;
import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.SUBJECT_ID;
import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.SUBJECT_LABEL;
import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.SUBJECT_MATCH_FIELD;
import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.SUBJECT_PREPROCESSING;
import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.SUBJECT_SOURCE;
import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.SUBJECT_SOURCE_VERSION;
import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.SUBJECT_TYPE;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import uk.ac.ebi.spot.oxo.model.sssom.CurieMap;
import uk.ac.ebi.spot.oxo.model.sssom.Mapping;
import uk.ac.ebi.spot.oxo.model.sssom.MappingSet;

/**
 *  A SSSOM TSV file contains 1 MappingSet object. See structure of TSV discussed
 * <a href="https://mapping-commons.github.io/sssom/spec-formats-tsv/#structure">here</a>.
 */
public class TSV2JSON {

    private static final Logger logger = LoggerFactory.getLogger(TSV2JSON.class);


    public static void processDirectory(String directory, String mappingSetOutputDirectory,
                                        String mappingsOutputDirectory) {

        Map<String, MappingSet.Builder> filenameToExternalMetadataMap = readExternalMetadata(directory);

        try (Stream<Path> paths = Files.walk(Paths.get(directory))) {
            paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".tsv"))
                    .forEach(path -> {
                        logger.info("Processing file: {}", path);
                        String filename = getFilenameWithoutExtension(path);
                        Optional<MappingSet.Builder> externalMappingSetBuilderOptional = Optional.empty();
                        if (filenameToExternalMetadataMap.containsKey(filename)) {
                            MappingSet.Builder externalMappingBuilderSet = filenameToExternalMetadataMap.get(filename);
                            externalMappingSetBuilderOptional = Optional.of(externalMappingBuilderSet);
                        }
                        long startReadTime = System.currentTimeMillis();
                        Optional<MappingSet> mappingSetOptional = readTSVFile(path.toFile(), externalMappingSetBuilderOptional);
                        long endReadTime = System.currentTimeMillis();
                        logger.info("Time taken to read TSV file: {} s", (endReadTime - startReadTime)/1000);

                        if (mappingSetOptional.isPresent() && mappingSetOptional.get().mappings().size() > 0) {
                            MappingSet mappingSet = mappingSetOptional.get();

                            writeJSONFile(mappingSet, mappingSetOutputDirectory, mappingsOutputDirectory);
                            long endWriteTime = System.currentTimeMillis();
                            logger.info("Time taken to write JSON file: {} s", (endWriteTime - endReadTime)/1000);
                        }
                    });
        } catch (Throwable t) {
            logger.error("Error while looking for .yml files in {}", directory, t);
        }
    }


    private static void writeJSONFile(MappingSet mappingSet, String mappingSetOutputDirectory,
                                      String mappingsOutputDirectory) {

        logger.info("Writing JSON file for MappingSet {}", mappingSet.mappingSetId());
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new Jdk8Module());
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        String baseFilename = mappingSet.mappingSetId().extractFragmentOrLastPathSegment();
        String mappingSetFilename = getUniqueFilename(mappingSetOutputDirectory, baseFilename);
        String mappingsFilename = getUniqueFilename(mappingsOutputDirectory, baseFilename);


        if (!mappingSet.mappings().isEmpty()) {
            try {
                objectMapper.writeValue(new File(mappingSetFilename), List.of(mappingSet));
                List<Mapping> mappingsForMappingSet = new ArrayList<>(mappingSet.mappings());

                objectMapper.writeValue(new File(mappingsFilename), mappingsForMappingSet);
            } catch (IOException e) {
                logger.error("Error while writing JSON file for MappingSet {}", mappingSet.mappingSetId(), e);
            }
        }
    }

    /**
     * Generates a unique filename by appending a counter if the file already exists.
     * For example: "mapping.json" -> "mapping.json", "mapping_1.json", "mapping_2.json", etc.
     *
     * @param directory The output directory
     * @param baseFilename The base filename without extension
     * @return A unique filename with .json extension
     */    
    private static String getUniqueFilename(String directory, String baseFilename) {
        String basePath = directory + File.separator + baseFilename;
        String filename = basePath + ".json";
        File file = new File(filename);
        
        if (!file.exists()) {
            return filename;
        }
        
        // File exists, append counter
        int counter = 1;
        do {
            filename = basePath + "_" + counter + ".json";
            file = new File(filename);
            counter++;
        } while (file.exists());
        
        logger.warn("File {} already exists, using unique filename: {}", basePath + ".json", filename);
        return filename;
    }

    public static Optional<MappingSet> readTSVFile(File file, Optional<MappingSet.Builder> externalMappingSetBuilderOptional) {
        logger.info("Reading TSV file {}", file);
        SortedSet<Mapping> mappings = new TreeSet<>();
        Optional<MappingSet> mappingSetOptional = Optional.empty();
        Optional<MappingSet.Builder> embeddedMappingSetBuilderOptional = Optional.empty();

        try {
            embeddedMappingSetBuilderOptional = readYamlHeader(file);
            if (externalMappingSetBuilderOptional.isEmpty() && embeddedMappingSetBuilderOptional.isEmpty()) {
                logger.error("Both external and embedded metadata are missing. See TSV file {}", file);
                throw new IllegalArgumentException("Both external and embedded metadata are missing. See TSV file " + file);
            }
            CSVParser parser = CSVParser.parse(file, java.nio.charset.StandardCharsets.UTF_8,
                    CSVFormat.TDF.builder().setCommentMarker('#').setHeader().build());

            Optional<CurieMap> optionalCurieMap = mergeCurieMaps(externalMappingSetBuilderOptional, embeddedMappingSetBuilderOptional);


            for (CSVRecord record : parser) {
                logger.debug("Processing record {}", record);
                Mapping.Builder mappingBuilder = Mapping.builder();
                mappingBuilder
                        .authorId(record.isSet(AUTHOR_ID) ? record.get(AUTHOR_ID) : "")
                        .authorLabel(record.isSet(AUTHOR_LABEL) ? record.get(AUTHOR_LABEL) : "")
                        .comment(record.isSet(COMMENT) ? record.get(COMMENT) : "")
                        .confidence(record.isSet(CONFIDENCE) ? record.get(CONFIDENCE) : "")
                        .creatorId(record.isSet(CREATOR_ID) ? record.get(CREATOR_ID) : "")
                        .creatorLabel(record.isSet(CREATOR_LABEL) ? record.get(CREATOR_LABEL) : "")
                        .curationRule(record.isSet(CURATION_RULE) ? record.get(CURATION_RULE) : "")
                        .distance(1)
                        .issueTrackerItem(record.isSet(ISSUE_TRACKER_ITEM) ? record.get(ISSUE_TRACKER_ITEM) : "")
                        .license(record.isSet(LICENSE) ? record.get(LICENSE) : "")
                        .mappingCardinality(record.isSet(MAPPING_CARDINALITY) ? record.get(MAPPING_CARDINALITY) : "")
                        .mappingDate(record.isSet(MAPPING_DATE) ? record.get(MAPPING_DATE) : "")
                        .mappingJustification(record.isSet(MAPPING_JUSTIFICATION) ? record.get(MAPPING_JUSTIFICATION) : "")
                        .mappingProvider(record.isSet(MAPPING_PROVIDER) ? record.get(MAPPING_PROVIDER) : "")
                        .mappingSource(record.isSet(MAPPING_SOURCE) ? record.get(MAPPING_SOURCE) : "")
                        .mappingTool(record.isSet(MAPPING_TOOL) ? record.get(MAPPING_TOOL) : "")
                        .mappingToolVersion(record.isSet(MAPPING_TOOL_VERSION) ? record.get(MAPPING_TOOL_VERSION) : "")
                        .matchString(record.isSet(MATCH_STRING) ? record.get(MATCH_STRING) : "")
                        .objectCategory(record.isSet(OBJECT_CATEGORY) ? record.get(OBJECT_CATEGORY) : "")
                        .objectId(record.isSet(OBJECT_ID) ? record.get(OBJECT_ID) : "", optionalCurieMap)
                        .objectLabel(record.isSet(OBJECT_LABEL) ? record.get(OBJECT_LABEL) : "")
                        .objectMatchField(record.isSet(OBJECT_MATCH_FIELD) ? record.get(OBJECT_MATCH_FIELD) : "")
                        .objectPreprocessing(record.isSet(OBJECT_PREPROCESSING) ? record.get(OBJECT_PREPROCESSING) : "")
                        .objectSource(record.isSet(OBJECT_SOURCE) ? record.get(OBJECT_SOURCE) : "")
                        .objectSourceVersion(record.isSet(OBJECT_SOURCE_VERSION) ? record.get(OBJECT_SOURCE_VERSION) : "")
                        .objectType(record.isSet(OBJECT_TYPE) ? record.get(OBJECT_TYPE) : "")
                        .other(record.isSet(OTHER) ? record.get(OTHER) : "")
                        .predicateId(record.isSet(PREDICATE_ID) ? record.get(PREDICATE_ID) : "", optionalCurieMap)
                        .predicateLabel(record.isSet(PREDICATE_LABEL) ? record.get(PREDICATE_LABEL) : "")
                        .predicateModifier(record.isSet(PREDICATE_MODIFIER) ? record.get(PREDICATE_MODIFIER) : "")
                        .publicationDate(record.isSet(PUBLICATION_DATE) ? record.get(PUBLICATION_DATE) : "")
                        .reviewerId(record.isSet(REVIEWER_ID) ? record.get(REVIEWER_ID) : "")
                        .reviewerLabel(record.isSet(REVIEWER_LABEL) ? record.get(REVIEWER_LABEL) : "")
                        .seeAlso(record.isSet(SEE_ALSO) ? record.get(SEE_ALSO) : "")
                        .similarityMeasure(record.isSet(SIMILARITY_MEASURE) ? record.get(SIMILARITY_MEASURE) : "")
                        .similarityScore(record.isSet(SIMILARITY_SCORE) ? record.get(SIMILARITY_SCORE) : "")
                        .subjectCategory(record.isSet(SUBJECT_CATEGORY) ? record.get(SUBJECT_CATEGORY) : "")
                        .subjectId(record.isSet(SUBJECT_ID) ? record.get(SUBJECT_ID) : "", optionalCurieMap)
                        .subjectLabel(record.isSet(SUBJECT_LABEL) ? record.get(SUBJECT_LABEL) : "")
                        .subjectMatchField(record.isSet(SUBJECT_MATCH_FIELD) ? record.get(SUBJECT_MATCH_FIELD) : "")
                        .subjectPreprocessing(record.isSet(SUBJECT_PREPROCESSING) ? record.get(SUBJECT_PREPROCESSING) : "")
                        .subjectSource(record.isSet(SUBJECT_SOURCE) ? record.get(SUBJECT_SOURCE) : "")
                        .subjectSourceVersion(record.isSet(SUBJECT_SOURCE_VERSION) ? record.get(SUBJECT_SOURCE_VERSION) : "")
                        .subjectType(record.isSet(SUBJECT_TYPE) ? record.get(SUBJECT_TYPE) : "");

                if (externalMappingSetBuilderOptional.isPresent()) {
                    externalMappingSetBuilderOptional.get().setMappingSetIdIfNotSetAlready(file.getName());
                    mappingBuilder = propagateValuesFromMappingSet(
                            mappingBuilder, externalMappingSetBuilderOptional.get(), record);
                }
                if (embeddedMappingSetBuilderOptional.isPresent()) {
                    embeddedMappingSetBuilderOptional.get().setMappingSetIdIfNotSetAlready(file.getName());
                    mappingBuilder = propagateValuesFromMappingSet(
                            mappingBuilder, embeddedMappingSetBuilderOptional.get(), record);
                }

                mappings.add(mappingBuilder.build());
            }
            MappingSet.Builder mappingSetBuilder = MappingSet.builder();
            if (externalMappingSetBuilderOptional.isPresent() && embeddedMappingSetBuilderOptional.isPresent()) {
                MappingSet.Builder tempMappingSet = embeddedMappingSetBuilderOptional.get();
                mappingSetBuilder = updateBuilder(mappingSetBuilder, tempMappingSet.build());
            } else if (externalMappingSetBuilderOptional.isPresent()) {
                mappingSetBuilder = externalMappingSetBuilderOptional.get();
            } else if (embeddedMappingSetBuilderOptional.isPresent()) {
                mappingSetBuilder = embeddedMappingSetBuilderOptional.get();
            }
            mappingSetBuilder.mappings(mappings);
            mappingSetOptional = Optional.of(mappingSetBuilder.build());
        } catch (IOException e) {
            logger.error("Error while reading TSV file {}", file, e);
        }
        return mappingSetOptional;
    }

    private static Optional<CurieMap> mergeCurieMaps(Optional<MappingSet.Builder> externalMappingSetBuilderOptional, Optional<MappingSet.Builder> embeddedMappingSetBuilderOptional) {
        Optional<MappingSet> tempOptionalExternalMappingSet = externalMappingSetBuilderOptional.isPresent() ?
                Optional.of(externalMappingSetBuilderOptional.get().build()) : Optional.empty();
        Optional<MappingSet> tempOptionalEmbeddedMappingSet = embeddedMappingSetBuilderOptional.isPresent() ?
                Optional.of(embeddedMappingSetBuilderOptional.get().build()) : Optional.empty();
        Optional<CurieMap> mergedCurieMap =
                (tempOptionalExternalMappingSet.isPresent() && tempOptionalEmbeddedMappingSet.isEmpty()) ?
                    Optional.of(tempOptionalExternalMappingSet.get().curieMap()) :
                        (tempOptionalEmbeddedMappingSet.isPresent() && tempOptionalExternalMappingSet.isEmpty()) ?
                            Optional.of(tempOptionalEmbeddedMappingSet.get().curieMap()) :
                                CurieMap.merge(tempOptionalEmbeddedMappingSet.get().curieMap(),
                                    tempOptionalExternalMappingSet.get().curieMap());
        return mergedCurieMap;
    }

    /**
     * Some values from mapping sets are propagated to mappings. For the list of propagated values see
     * <a href="https://mapping-commons.github.io/sssom/spec-model/#propagation-of-mapping-set-slots">.
     *
     * @param mappingBuilder
     * @param mappingSetBuilder
     * @param record
     * @return
     */
    private static Mapping.Builder propagateValuesFromMappingSet(Mapping.Builder mappingBuilder,
                                                                 MappingSet.Builder mappingSetBuilder,
                                                                 CSVRecord record) {
        MappingSet tempMappingSet = mappingSetBuilder.build();

        // Fields to propagate according to SSSOM specification
        mappingBuilder.mappingSetId(tempMappingSet.mappingSetId().getDataAsString(), Optional.of(tempMappingSet.mappingSetId()));
        mappingBuilder.mappingDate(record.isSet(MAPPING_DATE) ? record.get(MAPPING_DATE) : "", tempMappingSet.mappingDate());
        mappingBuilder.mappingProvider(record.isSet(MAPPING_PROVIDER) ? record.get(MAPPING_PROVIDER) : "", tempMappingSet.mappingProvider());
        mappingBuilder.mappingTool(record.isSet(MAPPING_TOOL) ? record.get(MAPPING_TOOL) : "", tempMappingSet.mappingTool());
        mappingBuilder.mappingToolVersion(record.isSet(MAPPING_TOOL_VERSION) ? record.get(MAPPING_TOOL_VERSION) : "", tempMappingSet.mappingToolVersion());

        mappingBuilder.objectMatchField(record.isSet(OBJECT_MATCH_FIELD) ? record.get(OBJECT_MATCH_FIELD) : "", tempMappingSet.objectMatchField());
        mappingBuilder.objectPreprocessing(record.isSet(OBJECT_PREPROCESSING) ? record.get(OBJECT_PREPROCESSING) : "", tempMappingSet.objectPreprocessing());
        mappingBuilder.objectSource(record.isSet(OBJECT_SOURCE) ? record.get(OBJECT_SOURCE) : "", tempMappingSet.objectSource());
        mappingBuilder.objectSourceVersion(record.isSet(OBJECT_SOURCE_VERSION) ? record.get(OBJECT_SOURCE_VERSION) : "", tempMappingSet.objectSourceVersion());
        mappingBuilder.objectType(record.isSet(OBJECT_TYPE) ? record.get(OBJECT_TYPE) : "", tempMappingSet.objectType());
        mappingBuilder.subjectMatchField(record.isSet(SUBJECT_MATCH_FIELD) ? record.get(SUBJECT_MATCH_FIELD) : "", tempMappingSet.subjectMatchField());
        mappingBuilder.subjectSource(record.isSet(SUBJECT_SOURCE) ? record.get(SUBJECT_SOURCE) : "", tempMappingSet.subjectSource());
        mappingBuilder.subjectSourceVersion(record.isSet(SUBJECT_SOURCE_VERSION) ? record.get(SUBJECT_SOURCE_VERSION) : "", tempMappingSet.subjectSourceVersion());
        mappingBuilder.subjectType(record.isSet(SUBJECT_TYPE) ? record.get(SUBJECT_TYPE) : "", tempMappingSet.subjectType());


        // We also propagate the following fields from the mapping set to mappings
        mappingBuilder.mappingSetDescription(tempMappingSet.mappingSetDescription());
        mappingBuilder.mappingSetSource(tempMappingSet.mappingSetSource());
        mappingBuilder.mappingSetTitle(tempMappingSet.mappingSetTitle());
        mappingBuilder.mappingSetVersion(tempMappingSet.mappingSetVersion());

        return mappingBuilder;
    }

    private static Optional<MappingSet.Builder> readYamlHeader(File file)
            throws IOException {
        String yamlCommentsAsString = getCommentsFromTSVAsYaml(file);
        return readYaml(yamlCommentsAsString);
    }

    private static String getCommentsFromTSVAsYaml(File file) throws IOException {
        String header = getCommentsFromTSVAsYaml(file, "# ");
        if (header.isEmpty()) {
            header = getCommentsFromTSVAsYaml(file, "#");
        }
        return header;
    }

    private static String getCommentsFromTSVAsYaml(File file, String startsWith) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        FileInputStream fileInputStream = null;
        Scanner scanner = null;
        try {
            fileInputStream = new FileInputStream(file);
            scanner = new Scanner(fileInputStream, "UTF-8");
            String line = startsWith;
            while (scanner.hasNextLine() && line.startsWith(startsWith)) {
                if (line.length() > startsWith.length()) {
                    stringBuilder.append(line.substring(startsWith.length()));
                    stringBuilder.append("\n");
                }
                line = scanner.nextLine();
            }
            // note that Scanner suppresses exceptions
            if (scanner.ioException() != null) {
                throw scanner.ioException();
            }
        } finally {
            if (fileInputStream != null) {
                fileInputStream.close();
            }
            if (scanner != null) {
                scanner.close();
            }
        }
        return stringBuilder.toString();
    }

    /**
     *
     * @param directory
     * @return Map of filenames mapped to MappingSet.Builders.
     */
    private static Map<String, MappingSet.Builder> readExternalMetadata(String directory) {
        Collection<Path> externalMetadata = findExternalMetadata(directory);
        Map<String, MappingSet.Builder> filenameToBuilderMap = new HashMap<>();

        if (externalMetadata.isEmpty()) {
            return new HashMap<>();
        }

        for (Path path : externalMetadata) {
            if (path.toString().endsWith(".yml")) {
                Optional<MappingSet.Builder> mappingSetBuilderOptional = readYaml(path.toFile());
                if (mappingSetBuilderOptional.isPresent()) {
                    filenameToBuilderMap.put(getFilenameWithoutExtension(path), mappingSetBuilderOptional.get());
                }
            }
        }

        return filenameToBuilderMap;
    }
    private static String getFilenameWithoutExtension(Path path) {
        String filename = path.getFileName().toString();
        int dotIndex = filename.lastIndexOf('.');
        return (dotIndex == -1) ? filename : filename.substring(0, dotIndex);
    }

    public static Optional<MappingSet.Builder> readYaml(File file) {
        ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
        objectMapper.registerModule(new Jdk8Module());
        objectMapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        Optional<MappingSet.Builder> mappingSetBuilderOptional = Optional.empty();
        try {
            mappingSetBuilderOptional = Optional.of(objectMapper.readValue(file, MappingSet.Builder.class));
        } catch (IOException e) {
            logger.error("Error while reading YAML file {}", file, e);
        }
        return mappingSetBuilderOptional;
    }

    private static Optional<MappingSet.Builder> readYaml(String yaml) {
        ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
        objectMapper.registerModule(new Jdk8Module());
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        Optional<MappingSet.Builder> mappingSetBuilderOptional = Optional.empty();
        try {
            mappingSetBuilderOptional = Optional.of(objectMapper.readValue(yaml, MappingSet.Builder.class));
        } catch (IOException e) {
            logger.error("Error while reading YAML String={}", yaml, e);
        }
        return mappingSetBuilderOptional;
    }
    private static MappingSet.Builder updateBuilder(MappingSet.Builder builder, MappingSet tempMappingSet) {
        builder.curieMap(tempMappingSet.curieMap())
                .mappings(tempMappingSet.mappings())
                .mappingSetId(tempMappingSet.mappingSetId())
                .mappingSetVersion(tempMappingSet.mappingSetVersion())
                .mappingSetSource(tempMappingSet.mappingSetSource())
                .mappingSetTitle(tempMappingSet.mappingSetTitle())
                .mappingSetDescription(tempMappingSet.mappingSetDescription())
                .creatorId(tempMappingSet.creatorId())
                .creatorLabel(tempMappingSet.creatorLabel())
                .license(tempMappingSet.license())
                .subjectType(tempMappingSet.subjectType())
                .subjectSource(tempMappingSet.subjectSource())
                .subjectSourceVersion(tempMappingSet.subjectSourceVersion())
                .objectType(tempMappingSet.objectType())
                .objectSource(tempMappingSet.objectSource())
                .objectSourceVersion(tempMappingSet.objectSourceVersion())
                .other(tempMappingSet.other())
                .mappingProvider(tempMappingSet.mappingProvider())
                .mappingTool(tempMappingSet.mappingTool())
                .mappingToolVersion(tempMappingSet.mappingToolVersion())
                .mappingDate(tempMappingSet.mappingDate())
                .publicationDate(tempMappingSet.publicationDate())
                .subjectMatchField(tempMappingSet.subjectMatchField())
                .objectMatchField(tempMappingSet.objectMatchField())
                .subjectPreprocessing(tempMappingSet.subjectPreprocessing())
                .objectPreprocessing(tempMappingSet.objectPreprocessing())
                .seeAlso(tempMappingSet.seeAlso())
                .issueTracker(tempMappingSet.issueTracker())
                .other(tempMappingSet.other())
                .comment(tempMappingSet.comment());
        return builder;
    }


    /**
     * Check if the directory contains external metadata. See
     * <a href="https://mapping-commons.github.io/sssom/spec-formats-tsv/#external-metadata-mode">
     *   TSV external metadata mode </a>.
     *
     * @param directory
     * @return
     */
    private static Collection<Path> findExternalMetadata(String directory) {
        try (Stream<Path> paths = Files.walk(Paths.get(directory))) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".yml"))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            logger.error("Error while looking for .yml files in {}",directory, e);
        }
        return new ArrayList<>();
    }

}
