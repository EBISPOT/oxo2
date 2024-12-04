package uk.ac.ebi.spot.oxo.solr.parser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.spot.oxo.model.sssom.Mapping;
import uk.ac.ebi.spot.oxo.model.sssom.MappingSet;

import static org.apache.commons.csv.CSVFormat.TDF;

/**
 *  A SSSOM TSV file contains 1 MappingSet object. See structure of TSV discussed
 * <a href="https://mapping-commons.github.io/sssom/spec-formats-tsv/#structure">here</a>.
 */
public class TSV2JSON {

    private static final Logger logger = LoggerFactory.getLogger(TSV2JSON.class);


    public static Collection<MappingSet> processDirectory(String directory) {
        Map<String, MappingSet.Builder> filenameToExternalMetadataMap = parseExternalMetadata(directory);

        try (Stream<Path> paths = Files.walk(Paths.get(directory))) {
            paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".tsv"))
                    .forEach(path -> {
                        String filename = getFilenameWithoutExtension(path);
                        if (filenameToExternalMetadataMap.containsKey(filename)) {
                            MappingSet.Builder builder = filenameToExternalMetadataMap.get(filename);
                        }
                        Collection<Mapping> mappings = readTSVFile(path.toFile());
                    });
        } catch (IOException e) {
            logger.error("Error while looking for .yml files in {}",directory, e);
        }

        return null;
    }

    private static Collection<Mapping> readTSVFile(File file) {
        List<Mapping> mappings = new ArrayList<>();
        try {
            CSVParser parser = CSVParser.parse(file, java.nio.charset.StandardCharsets.UTF_8, TDF.withFirstRecordAsHeader());
            for (CSVRecord record : parser) {
                Mapping.Builder mappingBuilder = new Mapping.Builder();

                record.get("subject_id");

                mappings.add(mappingBuilder.build());
            }
        } catch (IOException e) {
            logger.error("Error while reading TSV file {}", file, e);
        }
        return mappings;
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
                filenameToBuilderMap.put(getFilenameWithoutExtension(path), readYaml(path.toFile()));
            }
        }

        return filenameToBuilderMap;
    }
    private static String getFilenameWithoutExtension(Path path) {
        String filename = path.getFileName().toString();
        int dotIndex = filename.lastIndexOf('.');
        return (dotIndex == -1) ? filename : filename.substring(0, dotIndex);
    }

    public static MappingSet.Builder readYaml(File file) {
        ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
        objectMapper.registerModule(new Jdk8Module());
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        MappingSet.Builder builder = new MappingSet.Builder();
        try {
            MappingSet tempMappingSet = objectMapper.readValue(file, MappingSet.class);

            builder.curieMap(tempMappingSet.getCurieMap())
                    .mappings(tempMappingSet.getMappings())
                    .mappingSetId(tempMappingSet.getMappingSetId())
                    .mappingSetVersion(tempMappingSet.getMappingSetVersion())
                    .mappingSetSource(tempMappingSet.getMappingSetSource())
                    .mappingSetTitle(tempMappingSet.getMappingSetTitle())
                    .mappingSetDescription(tempMappingSet.getMappingSetDescription())
                    .creatorId(tempMappingSet.getCreatorId())
                    .creatorLabel(tempMappingSet.getCreatorLabel())
                    .license(tempMappingSet.getLicense())
                    .subjectType(tempMappingSet.getSubjectType())
                    .subjectSource(tempMappingSet.getSubjectSource())
                    .subjectSourceVersion(tempMappingSet.getSubjectSourceVersion())
                    .objectType(tempMappingSet.getObjectType())
                    .objectSource(tempMappingSet.getObjectSource())
                    .objectSourceVersion(tempMappingSet.getObjectSourceVersion())
                    .mappingProvider(tempMappingSet.getMappingProvider())
                    .mappingTool(tempMappingSet.getMappingTool())
                    .mappingToolVersion(tempMappingSet.getMappingToolVersion())
                    .mappingDate(tempMappingSet.getMappingDate())
                    .publicationDate(tempMappingSet.getPublicationDate())
                    .subjectMatchField(tempMappingSet.getSubjectMatchField())
                    .objectMatchField(tempMappingSet.getObjectMatchField())
                    .subjectPreprocessing(tempMappingSet.getSubjectPreprocessing())
                    .objectPreprocessing(tempMappingSet.getObjectPreprocessing())
                    .seeAlso(tempMappingSet.getSeeAlso())
                    .issueTracker(tempMappingSet.getIssueTracker())
                    .other(tempMappingSet.getOther())
                    .comment(tempMappingSet.getComment())
                    .extensionDefinitions(tempMappingSet.getExtensionDefinitions());
        } catch (IOException e) {
            logger.error("Error while reading YAML file {}", file, e);
        }
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
        Path path = Paths.get("/home/henriette007/ebi-dev/oxo2/oxo2/mappings/mondo_diseases/mp_hp_example.sssom.tsv");
        logger.trace("Filename = {}", getFilenameWithoutExtension(path));
    }

}
