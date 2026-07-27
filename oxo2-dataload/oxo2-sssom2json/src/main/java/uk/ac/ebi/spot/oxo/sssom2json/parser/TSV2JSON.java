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
import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.MAPPING_SET_ID;
import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.MAPPING_SET_TITLE;
import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.MAPPING_SET_VERSION;
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import uk.ac.ebi.spot.oxo.model.sssom.BioregistryPrefixMap;
import uk.ac.ebi.spot.oxo.model.sssom.CurieMap;
import uk.ac.ebi.spot.oxo.model.sssom.EntityReference;
import uk.ac.ebi.spot.oxo.model.sssom.Mapping;
import uk.ac.ebi.spot.oxo.model.sssom.MappingSet;
import uk.ac.ebi.spot.oxo.model.sssom.MappingSetCategory;
import uk.ac.ebi.spot.oxo.model.sssom.Uri;

/**
 *  A SSSOM TSV file contains 1 MappingSet object. See structure of TSV discussed
 * <a href="https://mapping-commons.github.io/sssom/spec-formats-tsv/#structure">here</a>.
 */
public class TSV2JSON {

    private static final Logger logger = LoggerFactory.getLogger(TSV2JSON.class);


    public static void processDirectory(String directory, String mappingSetOutputDirectory,
                                        String mappingsOutputDirectory,
                                        MappingSetCategory mappingSetCategory,
                                        Set<String> obsoleteEntityIris, boolean setObsolete) {

        Map<String, MappingSet.Builder> filenameToExternalMetadataMap = readExternalMetadata(directory);

        try (Stream<Path> paths = Files.list(Paths.get(directory))) {
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
                        processOneTSV(path.toFile(), externalMappingSetBuilderOptional,
                                mappingSetOutputDirectory, mappingsOutputDirectory, mappingSetCategory,
                                obsoleteEntityIris, setObsolete);
                    });
        } catch (Throwable t) {
            logger.error("Error while looking for .yml files in {}", directory, t);
        }
    }


    /**
     * Process a single TSV file. The file's directory is used to find external metadata.
     *
     * @param tsvFile The TSV file to process
     * @param mappingSetOutputDirectory Output directory for mapping set JSON files
     * @param mappingsOutputDirectory Output directory for mapping JSON files
     * @param mappingSetCategory The OxO curation category of the registry this TSV came from (ADR-0027)
     * @param obsoleteEntityIris The global obsolete-entity IRI set (ADR-0041); an endpoint is stamped
     *                           obsolete iff its expanded IRI is in this set. Empty on a normal load.
     * @param setObsolete        True iff this TSV's registry was config-flagged obsolete; stamped on the
     *                           mapping-set doc.
     */
    public static void processFile(File tsvFile, String mappingSetOutputDirectory,
                                   String mappingsOutputDirectory,
                                   MappingSetCategory mappingSetCategory,
                                   Set<String> obsoleteEntityIris, boolean setObsolete) {
        if (!tsvFile.exists() || !tsvFile.isFile()) {
            logger.error("TSV file does not exist or is not a file: {}", tsvFile);
            return;
        }

        if (!tsvFile.toString().endsWith(".tsv")) {
            logger.error("File is not a TSV file: {}", tsvFile);
            return;
        }

        String directory = tsvFile.getParent();
        if (directory == null) {
            directory = ".";
        }

        Map<String, MappingSet.Builder> filenameToExternalMetadataMap = readExternalMetadata(directory);

        logger.info("Processing file: {}", tsvFile);
        String filename = getFilenameWithoutExtension(tsvFile.toPath());
        Optional<MappingSet.Builder> externalMappingSetBuilderOptional = Optional.empty();
        if (filenameToExternalMetadataMap.containsKey(filename)) {
            MappingSet.Builder externalMappingBuilderSet = filenameToExternalMetadataMap.get(filename);
            externalMappingSetBuilderOptional = Optional.of(externalMappingBuilderSet);
        }

        processOneTSV(tsvFile, externalMappingSetBuilderOptional,
                mappingSetOutputDirectory, mappingsOutputDirectory, mappingSetCategory,
                obsoleteEntityIris, setObsolete);
    }


    /**
     * Stream a TSV file straight to its JSON outputs without ever materialising the full
     * SortedSet&lt;Mapping&gt; in memory. Heap retention is bounded by the dedup set of mappingId
     * UUIDs (~32 bytes/row) plus the current row's builder, so a 5M-row file uses ~200 MB
     * regardless of per-row payload size.
     *
     * <p>Order of mappings in the JSON output follows TSV order (no longer sorted by mappingId
     * as was the case when a TreeSet was used). Dedup on mappingId is preserved, matching the
     * old TreeSet contract which considered Mappings equal iff their mappingId matched.
     */
    private static void processOneTSV(File file,
                                       Optional<MappingSet.Builder> externalMappingSetBuilderOptional,
                                       String mappingSetOutputDirectory,
                                       String mappingsOutputDirectory,
                                       MappingSetCategory mappingSetCategory,
                                       Set<String> obsoleteEntityIris, boolean setObsolete) {
        // Drop prior file's CURIE/URI caches before parsing this one. The caches
        // speed up repeated lookups within a single mapping set, but if left to
        // accumulate across files they retain every distinct entity string for
        // the lifetime of the JVM and OOM on large inputs (e.g. NCBI taxon).
        EntityReference.clearCache();
        Uri.clearCache();
        logger.info("Reading TSV file {}", file);

        Optional<MappingSet.Builder> embeddedMappingSetBuilderOptional;
        try {
            embeddedMappingSetBuilderOptional = readYamlHeader(file);
        } catch (IOException e) {
            logger.error("Error while reading YAML header for TSV file {}", file, e);
            return;
        }

        if (externalMappingSetBuilderOptional.isEmpty() && embeddedMappingSetBuilderOptional.isEmpty()) {
            // No embedded YAML header and no external .yml sidecar. Rather than dropping the
            // set, synthesise its metadata from the per-row set-level columns and fall back to
            // the bundled Bioregistry prefix map so the CURIEs still expand to IRIs (ADR-0015).
            // This recovers bare published sets such as the biopragmatics SeMRA landscape
            // `priority` views, which ship no metadata header at all.
            embeddedMappingSetBuilderOptional = synthesizeMetadataFromColumns(file);
            if (embeddedMappingSetBuilderOptional.isEmpty()) {
                logger.error("Both external and embedded metadata are missing, and there are no "
                        + "data rows to synthesise set metadata from. Skipping TSV file {}", file);
                return;
            }
            logger.warn("No embedded or external SSSOM metadata for {}; synthesised set metadata "
                    + "from row columns and applied the bundled Bioregistry prefix map as the "
                    + "curie_map.", file);
        }

        // Pin the mappingSetId once before parsing; output filenames depend on it.
        externalMappingSetBuilderOptional.ifPresent(b -> b.setMappingSetIdIfNotSetAlready(file.getName()));
        embeddedMappingSetBuilderOptional.ifPresent(b -> b.setMappingSetIdIfNotSetAlready(file.getName()));

        MappingSet.Builder mappingSetBuilder = MappingSet.builder();
        if (externalMappingSetBuilderOptional.isPresent() && embeddedMappingSetBuilderOptional.isPresent()) {
            mappingSetBuilder = updateBuilder(mappingSetBuilder, embeddedMappingSetBuilderOptional.get().build());
        } else if (externalMappingSetBuilderOptional.isPresent()) {
            mappingSetBuilder = externalMappingSetBuilderOptional.get();
        } else {
            mappingSetBuilder = embeddedMappingSetBuilderOptional.get();
        }
        // mappings is @JsonIgnore on MappingSet, so the metadata file does not contain
        // them anyway; keeping the field empty avoids retaining references after streaming.
        mappingSetBuilder.mappings(new TreeSet<>());
        // The curation category is external to SSSOM — it comes from the OxO config entry for the
        // registry this TSV was downloaded from, not from any TSV column or metadata slot (ADR-0027).
        mappingSetBuilder.mappingSetCategory(mappingSetCategory.getCode());
        // Set-level obsolescence (ADR-0041): stamped from the registry's config `obsolete` flag, so the
        // mapping-set picker can hide and label obsolete ontology sets.
        mappingSetBuilder.obsolete(setObsolete);
        MappingSet mappingSetMetadata = mappingSetBuilder.build();

        String baseFilename = mappingSetMetadata.mappingSetId().extractFragmentOrLastPathSegment();
        String mappingSetFilename = getUniqueFilename(mappingSetOutputDirectory, baseFilename);
        String mappingsFilename = getUniqueFilename(mappingsOutputDirectory, baseFilename);
        File mappingsFile = new File(mappingsFilename);

        Optional<CurieMap> optionalCurieMap = mergeCurieMaps(externalMappingSetBuilderOptional, embeddedMappingSetBuilderOptional);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new Jdk8Module());
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        long startReadTime = System.currentTimeMillis();
        long mappingsWritten = 0;
        Set<UUID> seenMappingIds = new HashSet<>();

        try (CSVParser parser = CSVParser.parse(file, java.nio.charset.StandardCharsets.UTF_8,
                    CSVFormat.TDF.builder().setCommentMarker('#').setHeader().build());
             JsonGenerator gen = objectMapper.getFactory().createGenerator(mappingsFile, JsonEncoding.UTF8)) {

            gen.writeStartArray();
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
                    mappingBuilder = propagateValuesFromMappingSet(
                            mappingBuilder, externalMappingSetBuilderOptional.get(), record);
                }
                if (embeddedMappingSetBuilderOptional.isPresent()) {
                    mappingBuilder = propagateValuesFromMappingSet(
                            mappingBuilder, embeddedMappingSetBuilderOptional.get(), record);
                }

                // Denormalised onto every mapping so the backend can filter and rank on it without a
                // join back to oxo2-mappingsets (ADR-0027). Not a propagated SSSOM slot.
                mappingBuilder.mappingSetCategory(mappingSetCategory.getCode());

                // Endpoint obsolescence (ADR-0041): a subject/object is obsolete iff its expanded IRI is
                // in the global obsolete-entity set (the subjects of every obsolete-flagged registry).
                // Keyed on the IRI, not the CURIE, because CURIE casing varies by source. The default
                // search hides a mapping with either endpoint obsolete.
                mappingBuilder.subjectObsolete(isObsoleteEndpoint(
                        record.isSet(SUBJECT_ID) ? record.get(SUBJECT_ID) : "", optionalCurieMap, obsoleteEntityIris));
                mappingBuilder.objectObsolete(isObsoleteEndpoint(
                        record.isSet(OBJECT_ID) ? record.get(OBJECT_ID) : "", optionalCurieMap, obsoleteEntityIris));

                Mapping mapping = mappingBuilder.build();
                if (seenMappingIds.add(mapping.mappingId())) {
                    objectMapper.writeValue(gen, mapping);
                    mappingsWritten++;
                }
            }
            gen.writeEndArray();
        } catch (IOException e) {
            logger.error("Error while processing TSV file {}", file, e);
            if (mappingsFile.exists() && !mappingsFile.delete()) {
                logger.warn("Failed to delete partial mappings output {}", mappingsFile);
            }
            return;
        }

        long endReadTime = System.currentTimeMillis();
        logger.info("Time taken to read TSV file: {} s, wrote {} mappings",
                (endReadTime - startReadTime) / 1000, mappingsWritten);

        if (mappingsWritten == 0) {
            logger.warn("No mappings found in file: {}", file);
            if (mappingsFile.exists() && !mappingsFile.delete()) {
                logger.warn("Failed to delete empty mappings output {}", mappingsFile);
            }
            return;
        }

        try {
            objectMapper.writeValue(new File(mappingSetFilename), List.of(mappingSetMetadata));
        } catch (IOException e) {
            logger.error("Error while writing JSON file for MappingSet {}",
                    mappingSetMetadata.mappingSetId(), e);
        }
        long endWriteTime = System.currentTimeMillis();
        logger.info("Time taken to write MappingSet JSON file: {} s", (endWriteTime - endReadTime) / 1000);
    }

    /**
     * True iff the CURIE expands (via this file's curie map) to an IRI in the global obsolete-entity set
     * (ADR-0041). An empty set — a load with no obsolete-flagged registry — short-circuits to false so
     * the output is byte-for-byte unchanged.
     */
    private static boolean isObsoleteEndpoint(String curie, Optional<CurieMap> optionalCurieMap,
                                              Set<String> obsoleteEntityIris) {
        if (obsoleteEntityIris.isEmpty()) {
            return false;
        }
        return resolveIri(curie, optionalCurieMap).map(obsoleteEntityIris::contains).orElse(false);
    }

    /** Expand a CURIE to its IRI string via the file's curie map, matching the main pass's subjectIRI. */
    private static Optional<String> resolveIri(String curie, Optional<CurieMap> optionalCurieMap) {
        if (curie == null || curie.isBlank() || optionalCurieMap.isEmpty()) {
            return Optional.empty();
        }
        return new EntityReference(curie).toUri(optionalCurieMap.get()).map(Uri::asStringIRI);
    }

    /**
     * Pass 1 of the obsolete-terms dataload (ADR-0041): the distinct expanded subject IRIs of one
     * obsolete-flagged TSV. The union of these across every obsolete registry is the global
     * obsolete-entity set that {@link #processOneTSV} stamps both endpoints against. Reuses the same
     * curie-map resolution and {@link EntityReference#toUri} expansion as the main pass, so the IRIs the
     * two produce for one term cannot disagree. External {@code .yml} sidecars are not consulted here:
     * obsolete registries are OLS exports that carry an embedded SSSOM header.
     */
    public static Set<String> extractSubjectIris(File tsvFile) {
        EntityReference.clearCache();
        Uri.clearCache();
        Set<String> subjectIris = new HashSet<>();
        if (!tsvFile.exists() || !tsvFile.isFile()) {
            logger.error("TSV file does not exist or is not a file: {}", tsvFile);
            return subjectIris;
        }

        Optional<MappingSet.Builder> embeddedMappingSetBuilderOptional;
        try {
            embeddedMappingSetBuilderOptional = readYamlHeader(tsvFile);
        } catch (IOException e) {
            logger.error("Error while reading YAML header for TSV file {}", tsvFile, e);
            return subjectIris;
        }
        if (embeddedMappingSetBuilderOptional.isEmpty()) {
            embeddedMappingSetBuilderOptional = synthesizeMetadataFromColumns(tsvFile);
        }
        Optional<CurieMap> optionalCurieMap =
                mergeCurieMaps(Optional.empty(), embeddedMappingSetBuilderOptional);

        try (CSVParser parser = CSVParser.parse(tsvFile, java.nio.charset.StandardCharsets.UTF_8,
                CSVFormat.TDF.builder().setCommentMarker('#').setHeader().build())) {
            for (CSVRecord record : parser) {
                resolveIri(record.isSet(SUBJECT_ID) ? record.get(SUBJECT_ID) : "", optionalCurieMap)
                        .ifPresent(subjectIris::add);
            }
        } catch (IOException e) {
            logger.error("Error while extracting subject IRIs from TSV file {}", tsvFile, e);
        }
        return subjectIris;
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

    /**
     * Synthesise mapping-set metadata for a TSV that has no embedded YAML header and no external
     * {@code .yml} sidecar. Some published sets ship a bare TSV whose set-level slots live in
     * per-row columns (constant per set) and whose prefixes are implicitly the Bioregistry's —
     * notably the biopragmatics SeMRA landscape {@code priority} views. We read the set-level
     * slots from the first data row and apply the bundled Bioregistry prefix map as the
     * {@code curie_map} so the row CURIEs expand to IRIs. See ADR-0015.
     *
     * @return a builder seeded from the first data row, or empty if the file has no data rows.
     */
    private static Optional<MappingSet.Builder> synthesizeMetadataFromColumns(File file) {
        try (CSVParser parser = CSVParser.parse(file, java.nio.charset.StandardCharsets.UTF_8,
                CSVFormat.TDF.builder().setCommentMarker('#').setHeader().build())) {
            Iterator<CSVRecord> records = parser.iterator();
            if (!records.hasNext()) {
                return Optional.empty();
            }
            CSVRecord firstRecord = records.next();
            MappingSet.Builder builder = MappingSet.builder();
            SortedMap<String, String> defaultPrefixes = BioregistryPrefixMap.get();
            builder.curieMap(defaultPrefixes);
            if (firstRecord.isSet(MAPPING_SET_ID) && !firstRecord.get(MAPPING_SET_ID).isBlank()) {
                builder.mappingSetId(firstRecord.get(MAPPING_SET_ID));
            }
            if (firstRecord.isSet(MAPPING_SET_TITLE) && !firstRecord.get(MAPPING_SET_TITLE).isBlank()) {
                builder.mappingSetTitle(firstRecord.get(MAPPING_SET_TITLE));
            }
            if (firstRecord.isSet(MAPPING_SET_VERSION) && !firstRecord.get(MAPPING_SET_VERSION).isBlank()) {
                builder.mappingSetVersion(firstRecord.get(MAPPING_SET_VERSION));
            }
            if (firstRecord.isSet(LICENSE) && !firstRecord.get(LICENSE).isBlank()) {
                builder.license(firstRecord.get(LICENSE));
            }
            return Optional.of(builder);
        } catch (IOException e) {
            logger.error("Error while synthesising metadata from columns for TSV file {}", file, e);
            return Optional.empty();
        }
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
        if (yaml == null || yaml.isBlank()) {
            // No commented metadata header. Not an error: the caller falls back to an external
            // .yml sidecar or to synthesising the set from the row columns.
            return Optional.empty();
        }
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
