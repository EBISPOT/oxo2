package uk.ac.ebi.spot.oxo.sssom2json.parser;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.spot.oxo.model.sssom.Mapping;
import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.*;
import uk.ac.ebi.spot.oxo.model.sssom.MappingSet;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.apache.commons.csv.CSVFormat.TDF;

/**
 *  A SSSOM TSV file contains 1 MappingSet object. See structure of TSV discussed
 * <a href="https://mapping-commons.github.io/sssom/spec-formats-tsv/#structure">here</a>.
 */
public class TSV2JSON {

    private static final Logger logger = LoggerFactory.getLogger(TSV2JSON.class);


    public static void processDirectory(String directory, String mappingSetOutputDirectory, String mappingsOutputDirectiory) {

        Map<String, MappingSet.Builder> filenameToExternalMetadataMap = parseExternalMetadata(directory);

        try (Stream<Path> paths = Files.walk(Paths.get(directory))) {
            paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".tsv"))
                    .forEach(path -> {
                        String filename = getFilenameWithoutExtension(path);
                        Optional<MappingSet.Builder> externalMappingSetBuilderOptional = Optional.empty();
                        if (filenameToExternalMetadataMap.containsKey(filename)) {
                            MappingSet.Builder externalMappingBuilderSet = filenameToExternalMetadataMap.get(filename);
                            externalMappingSetBuilderOptional = Optional.of(externalMappingBuilderSet);
                        }
                        Optional<MappingSet> mappingSetOptional = readTSVFile(path.toFile(), externalMappingSetBuilderOptional);
                        if (mappingSetOptional.isPresent()) {
                            writeJSONFile(mappingSetOptional.get(), mappingSetOutputDirectory, mappingsOutputDirectiory);
                        }
                    });
        } catch (IOException e) {
            logger.error("Error while looking for .yml files in {}",directory, e);
        }
    }

    private static void writeJSONFile(MappingSet mappingSet, String mappingSetOutputDirectory, String mappingsOutputDirectiory) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new Jdk8Module());
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        String mappingSetFilename = mappingSetOutputDirectory + File.separator +
                mappingSet.mappingSetId().extractFragmentOrLastPathSegment() + ".json";
        String mappingsFilename = mappingsOutputDirectiory + File.separator +
                mappingSet.mappingSetId().extractFragmentOrLastPathSegment() + ".json";

        try {
            objectMapper.writeValue(new File(mappingSetFilename), mappingSet);
            List<Mapping> mappingsForMappingSet = new ArrayList<>(mappingSet.mappings());

            objectMapper.writeValue(new File(mappingsFilename), mappingsForMappingSet);
        } catch (IOException e) {
            logger.error("Error while writing JSON file for MappingSet {}", mappingSet.mappingSetId(), e);
        }
    }

    private static Optional<MappingSet> readTSVFile(File file, Optional<MappingSet.Builder> externalMappingSetBuilderOptional) {
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
            Mapping.Builder mappingBuilder = Mapping.builder();
            for (CSVRecord record : parser) {
                mappingBuilder
                        .subjectId(record.isSet(SUBJECT_ID) ? record.get(SUBJECT_ID) : "")
                        .subjectLabel(record.isSet(SUBJECT_LABEL) ? record.get(SUBJECT_LABEL) : "")
                        .subjectCategory(record.isSet(SUBJECT_CATEGORY) ? record.get(SUBJECT_CATEGORY) : "")
                        .predicateId(record.isSet(PREDICATE_ID) ? record.get(PREDICATE_ID) : "")
                        .predicateLabel(record.isSet(PREDICATE_LABEL) ? record.get(PREDICATE_LABEL) : "")
                        .predicateModifier(record.isSet(PREDICATE_MODIFIER) ? record.get(PREDICATE_MODIFIER) : "")
                        .objectId(record.isSet(OBJECT_ID) ? record.get(OBJECT_ID) : "")
                        .objectLabel(record.isSet(OBJECT_LABEL) ? record.get(OBJECT_LABEL) : "")
                        .mappingJustification(record.isSet(MAPPING_JUSTIFICATION) ? record.get(MAPPING_JUSTIFICATION) : "")
                        .authorId(record.isSet(AUTHOR_ID) ? record.get(AUTHOR_ID) : "")
                        .authorLabel(record.isSet(AUTHOR_LABEL) ? record.get(AUTHOR_LABEL) : "")
                        .license(record.isSet(LICENSE) ? record.get(LICENSE) : "")
                        .mappingSource(record.isSet(MAPPING_SOURCE) ? record.get(MAPPING_SOURCE) : "")
                        .mappingCardinality(record.isSet(MAPPING_CARDINALITY) ? record.get(MAPPING_CARDINALITY) : "")
                        .publicationDate(record.isSet(PUBLICATION_DATE) ? record.get(PUBLICATION_DATE) : "")
                        .confidence(record.isSet(CONFIDENCE) ? record.get(CONFIDENCE) : "")
                        .curationRule(record.isSet(CURATION_RULE) ? record.get(CURATION_RULE) : "")
                        .matchString(record.isSet(MATCH_STRING) ? record.get(MATCH_STRING) : "")
                        .similarityScore(record.isSet(SIMILARITY_SCORE) ? record.get(SIMILARITY_SCORE) : "");

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

        mappingBuilder.mappingSetId(tempMappingSet.mappingSetId().getUriAsString());
        mappingBuilder.mappingDate(record.isSet(MAPPING_DATE) ? record.get(MAPPING_DATE) : "", tempMappingSet.mappingDate());mappingBuilder.mappingProvider(record.isSet(MAPPING_PROVIDER) ? record.get(MAPPING_PROVIDER) : "", tempMappingSet.mappingProvider());
        mappingBuilder.mappingTool(record.isSet(MAPPING_TOOL) ? record.get(MAPPING_TOOL) : "", tempMappingSet.mappingTool());
        mappingBuilder.mappingToolVersion(record.isSet(MAPPING_TOOL_VERSION) ? record.get(MAPPING_TOOL_VERSION) : "", tempMappingSet.mappingToolVersion());
        mappingBuilder.objectMatchField(record.isSet(OBJECT_MATCH_FIELD) ? record.get(OBJECT_MATCH_FIELD) : "", tempMappingSet.objectMatchField());
        mappingBuilder.objectPreprocessing(record.isSet(OBJECT_PREPROCESSING) ? record.get(OBJECT_PREPROCESSING) : "", tempMappingSet.objectPreprocessing());
        mappingBuilder.objectSource(record.isSet(OBJECT_SOURCE) ? record.get(OBJECT_SOURCE) : "", tempMappingSet.objectSource());
        mappingBuilder.objectSourceVersion(record.isSet(OBJECT_SOURCE_VERSION) ? record.get(OBJECT_SOURCE_VERSION) : "", tempMappingSet.objectSourceVersion());
        mappingBuilder.objectType(record.isSet(OBJECT_TYPE) ? record.get(OBJECT_TYPE) : "", tempMappingSet.objectType());
        mappingBuilder.subjectMatchField(record.isSet(SUBJECT_MATCH_FIELD) ? record.get(SUBJECT_MATCH_FIELD) : "", tempMappingSet.subjectMatchField());
        mappingBuilder.mappingProvider(record.isSet(MAPPING_PROVIDER) ? record.get(MAPPING_PROVIDER) : "", tempMappingSet.mappingProvider());
        mappingBuilder.subjectSource(record.isSet(SUBJECT_SOURCE) ? record.get(SUBJECT_SOURCE) : "", tempMappingSet.subjectSource());
        mappingBuilder.subjectSourceVersion(record.isSet(SUBJECT_SOURCE_VERSION) ? record.get(SUBJECT_SOURCE_VERSION) : "", tempMappingSet.subjectSourceVersion());
        mappingBuilder.subjectType(record.isSet(SUBJECT_TYPE) ? record.get(SUBJECT_TYPE) : "", tempMappingSet.subjectType());

        return mappingBuilder;
    }

    private static Optional<MappingSet.Builder> readYamlHeader(File file)
            throws IOException {
        String yamlCommentsAsString = getCommentsFromTSVAsYaml(file);
        return readYaml(yamlCommentsAsString);
    }

    private static String getCommentsFromTSVAsYaml(File file) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        FileInputStream fileInputStream = null;
        Scanner scanner = null;
        try {
            fileInputStream = new FileInputStream(file);
            scanner = new Scanner(fileInputStream, "UTF-8");
            String line = "# ";
            while (scanner.hasNextLine() && line.startsWith("# ")) {
                if (line.length() > 2) {
                    stringBuilder.append(line.substring(2));
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
    private static Map<String, MappingSet.Builder> parseExternalMetadata(String directory) {
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


    public static void main(String args[]) {
        readYaml(new File("/home/henriette007/ebi-dev/oxo2/oxo2/testcases/test.yml"));
    }
}
