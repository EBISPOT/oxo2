package uk.ac.ebi.spot.oxo.inferences.nemo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.spot.oxo.inferences.nemo.helpers.NemoHelper;
import uk.ac.ebi.spot.oxo.inferences.nemo.model.EntityDetails;
import uk.ac.ebi.spot.oxo.inferences.nemo.model.MinimalMapping;
import uk.ac.ebi.spot.oxo.inferences.nemo.model.NemoInferences;
import uk.ac.ebi.spot.oxo.inferences.nemo.model.OXOInferenceConstants;
import uk.ac.ebi.spot.oxo.model.sssom.ChainRulesEnum;
import uk.ac.ebi.spot.oxo.model.sssom.InferredMapping;
import uk.ac.ebi.spot.oxo.model.sssom.Mapping;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

public class ExplainInferredMappings {
    private static final Logger logger = LoggerFactory.getLogger(ExplainInferredMappings.class);

    public static void main(String[] args) {
        Options options = getOptions();
        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            logger.error("Error parsing command line arguments", e);
            formatter.printHelp("ExplainInferredMappings", options);
            System.exit(1);
            return;
        }

        String nemoInferencesToParse = cmd.getOptionValue("nemoInferences");
        String inputDirectory = cmd.getOptionValue("inputDirectory");
        String outputFile = cmd.getOptionValue("outputFile");

        logger.info("nemoInferences:  {}", nemoInferencesToParse);
        logger.info("Output File: {}", outputFile);

        // Validate input file
        File inputFile = new File(nemoInferencesToParse);
        if (!inputFile.exists() || !inputFile.isFile()) {
            logger.error("Input file does not exist or is not a file: {}", nemoInferencesToParse);
            System.exit(1);
            return;
        }

        // Create output directory if it doesn't exist
        File output = new File(outputFile);
        File outputDir = output.getParentFile();
        if (outputDir != null && !outputDir.exists()) {
            if (!outputDir.mkdirs()) {
                logger.error("Failed to create output directory: {}", outputDir.getAbsolutePath());
                System.exit(1);
                return;
            }
        }

        long startTime = System.currentTimeMillis();
        try {
            logger.info("Reading inferences from file...");
            NemoInferences nemoInferences = NemoInferenceReader.readInferences(nemoInferencesToParse);
            if (nemoInferences == null) {
                logger.error("Failed to read inferences from file or file is empty");
                System.exit(1);
                return;
            }

            Map<MinimalMapping, List<Mapping>> assertedMappings = readExistingMappings(inputDirectory);

            Map<String, EntityDetails> iriToEntityDetails = extractIriToEntityDetails(assertedMappings);
            
            logger.info("Converting to inferred mappings...");
            Set<InferredMapping> inferredMappings = NemoHelper.fromNemoInferencesToInferredMappings(
                    nemoInferences, assertedMappings, iriToEntityDetails);
            if (inferredMappings.isEmpty()) {
                logger.warn("No inferred mappings were generated");
            } else {
                logger.info("Generated {} inferred mappings", inferredMappings.size());
            }
            
            logger.info("Creating mappings...");
            List<Mapping> mappings = createMappings(inferredMappings, assertedMappings, iriToEntityDetails);
            logger.info("Created {} mappings", mappings.size());

            logger.info("Writing mappings to file...");
            writeMappingsAsJson(mappings, outputFile);
            logger.info("Successfully completed processing");
        } catch (Exception e) {
            logger.error("Error processing mappings", e);
            System.exit(1);
        }
        long endTime = System.currentTimeMillis();
        logger.info("Processing took {} s", (endTime - startTime)/1000);
    }

    public static Map<String, EntityDetails> extractIriToEntityDetails(Map<MinimalMapping, List<Mapping>> assertedMappings) {
        Map<String, EntityDetails> entities = new HashMap<>();

        assertedMappings.values().forEach(mappingList -> {
            mappingList.forEach(mapping -> {
                updateEntityUsingSubject(mapping, entities);
                updateEntityUsingPredicate(mapping, entities);
                updateEntityUsingObject(mapping, entities);
            });
        });
        return entities;
    }

    private static void updateEntityUsingSubject(Mapping mapping, Map<String, EntityDetails> entities) {
        EntityDetails existingSubject = entities.get(mapping.subjectId().get().getDataAsString());
        if (existingSubject == null) {
            EntityDetails subject = new EntityDetails();
            mapping.subjectId().ifPresent(id -> subject.setCurie(id.getDataAsString()));
            mapping.subjectIRI().ifPresent(uri -> subject.setIri(uri.getDataAsString()));
            mapping.subjectLabel().ifPresent(subject::setLabel);

            entities.put(subject.getIri(), subject);
        } else {
            mapping.subjectId().ifPresent(id -> existingSubject.setCurie(id.getDataAsString()));
            mapping.subjectLabel().ifPresent(existingSubject::setLabel);

            entities.put(existingSubject.getIri(), existingSubject);
        }
    }

    private static void updateEntityUsingPredicate(Mapping mapping, Map<String, EntityDetails> entities) {
        EntityDetails existingPredicate = entities.get(mapping.predicateId().get().getDataAsString());
        if (existingPredicate == null) {
            EntityDetails predicate = new EntityDetails();
            mapping.predicateId().ifPresent(id -> predicate.setCurie(id.getDataAsString()));
            mapping.predicateIRI().ifPresent(uri -> predicate.setIri(uri.getDataAsString()));
            mapping.predicateLabel().ifPresent(predicate::setLabel);

            entities.put(predicate.getIri(), predicate);
        } else {
            mapping.predicateId().ifPresent(id -> existingPredicate.setCurie(id.getDataAsString()));
            mapping.predicateLabel().ifPresent(existingPredicate::setLabel);

            entities.put(existingPredicate.getIri(), existingPredicate);
        }
    }

    private static void updateEntityUsingObject(Mapping mapping, Map<String, EntityDetails> entities) {
        EntityDetails existingObject = entities.get(mapping.objectId().get().getDataAsString());
        if (existingObject == null) {
            EntityDetails object = new EntityDetails();
            mapping.objectId().ifPresent(id -> object.setCurie(id.getDataAsString()));
            mapping.objectIRI().ifPresent(uri -> object.setIri(uri.getDataAsString()));
            mapping.objectLabel().ifPresent(object::setLabel);

            entities.put(object.getIri(), object);
        } else {
            mapping.objectId().ifPresent(id -> existingObject.setCurie(id.getDataAsString()));
            mapping.objectLabel().ifPresent(existingObject::setLabel);

            entities.put(existingObject.getIri(), existingObject);
        }
    }


    public static void writeMappingsAsJson(List<Mapping> mappings, String outputFile) throws IOException {
        if (mappings == null || mappings.isEmpty()) {
            logger.warn("No mappings to write to file");
            return;
        }
        
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new Jdk8Module());
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        
        File file = new File(outputFile);
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, mappings);
            logger.info("Mappings successfully written to {} ({} bytes)", outputFile, file.length());
        } catch (IOException e) {
            logger.error("Error writing mappings to JSON file: {}", e.getMessage());
            throw e;
        }
    }

    private static MinimalMapping getMinimalMapping(InferredMapping inferredMapping) {
        return new MinimalMapping(
                inferredMapping.getSubjectIRI().asStringIRI(),
                inferredMapping.getPredicateIRI().asStringIRI(),
                inferredMapping.getObjectIRI().asStringIRI());
    }

    public static List<Mapping> createMappings(Set<InferredMapping> inferredMappings,
                                               Map<MinimalMapping, List<Mapping>> assertedMappingsMap,
                                               Map<String, EntityDetails> iriToEntityDetails) {
        if (inferredMappings == null || inferredMappings.isEmpty()) {
            logger.warn("No inferred mappings to process");
            return new ArrayList<>();
        }
        
        List<Mapping> mappings = new ArrayList<>(inferredMappings.size());
        int count = 0;
        
        for (InferredMapping inferredMapping : inferredMappings) {
            try {
                if (inferredMapping.getSubjectIRI() == null || inferredMapping.getPredicateIRI() == null || 
                    inferredMapping.getObjectIRI() == null) {
                    logger.warn("Skipping mapping with null IRI values: {}", inferredMapping);
                    continue;
                }

                EntityDetails subjectDetails = iriToEntityDetails.get(inferredMapping.getSubjectIRI().asStringIRI());
                EntityDetails predicateDetails = iriToEntityDetails.get(inferredMapping.getPredicateIRI().asStringIRI());
                EntityDetails objectDetails = iriToEntityDetails.get(inferredMapping.getObjectIRI().asStringIRI());

                Mapping mapping = new Mapping.Builder()
                    .subjectIRI(inferredMapping.getSubjectIRI().asStringIRI())
                    .subjectId((subjectDetails != null) ? subjectDetails.getCurie() : "")
                    .subjectLabel((subjectDetails != null) ? subjectDetails.getLabel() : "")
                    .predicateIRI(inferredMapping.getPredicateIRI().asStringIRI())
                    .predicateId((predicateDetails != null) ? predicateDetails.getCurie() : "")
                    .predicateLabel((predicateDetails != null) ? predicateDetails.getLabel() : "")
                    .objectIRI(inferredMapping.getObjectIRI().asStringIRI())
                    .objectId((objectDetails != null) ? objectDetails.getCurie() : "")
                    .objectLabel((objectDetails != null) ? objectDetails.getLabel() : "")
                    .mappingJustification(OXOInferenceConstants.OXO_MAPPING_JUSTIFICATION)
                    .mappingTool(OXOInferenceConstants.OXO_MAPPING_TOOL)
                    .explanation(inferredMapping)
                    .assertedMappings(determineAssertedMappingsForExplanation(inferredMapping))
                    .explanationLength(determineExplanationLength(inferredMapping, 0))
                    .distance(calculateMappingDistance(inferredMapping))
                    .mappingSetId(OXOInferenceConstants.OXO_MAPPING_SET_ID)
                    .build();
                mappings.add(mapping);
                
                count++;
                if (count % 1000 == 0) {
                    logger.info("Processed {} mappings", count);
                }
            } catch (Exception e) {
                logger.error("Error creating mapping for: {}", inferredMapping, e);
            }
        }
        
        return mappings;
    }

    /**
     * When we determine explanations, we only provide a single asserted mapping as evidence for why an inference was made.
     * However, it is possible that the same mapping is asserted in multiple mapping sets. Hence, this method retrieves
     * all asserted mappings. This can be useful for users who need to debug incorrect derived mappings.
     *
     * @param explanation
     * @return
     */
    private static List<InferredMapping> determineAssertedMappingsForExplanation(
            InferredMapping explanation) {

        List<InferredMapping> assertedMappings = new ArrayList<>();

        if (explanation.getChainRuleApplications().isEmpty())
            return assertedMappings;

        InferredMapping.ChainRuleApplications chainRuleApplications = explanation.getChainRuleApplications().get();

        if (chainRuleApplications.getChainRule().isPresent() &&
                chainRuleApplications.getChainRule().get().equals(ChainRulesEnum.ASSERTED))
            assertedMappings.add(explanation);

        explanation.getChainRuleApplications().get().getPremises().forEach(premise -> {
            List<InferredMapping> assertedMappingsToAdd = determineAssertedMappingsForExplanation(premise);
            assertedMappings.addAll(assertedMappingsToAdd);
        });

        return assertedMappings;
    }

    private static int determineExplanationLength(InferredMapping explanation, int startExplanationLength) {
        int explanationLength = startExplanationLength;

        if (explanation.getChainRuleApplications().isEmpty())
            return explanationLength;
        explanationLength++;
        for (InferredMapping premise: explanation.getChainRuleApplications().get().getPremises()) {
            explanationLength = determineExplanationLength(premise, explanationLength);
        }

        return explanationLength;
    }

    private static int calculateMappingDistance(InferredMapping explanation) {
        Set<String> extractedParts = new HashSet<>();

        extractParts(explanation.getSubjectIRI().asStringIRI(), extractedParts);
        extractParts(explanation.getObjectIRI().asStringIRI(), extractedParts);

        if (explanation.getChainRuleApplications().isEmpty())
            return extractedParts.size() - 1;


        for (InferredMapping premise: explanation.getChainRuleApplications().get().getPremises()) {
            extractParts(premise.getSubjectIRI().asStringIRI(), extractedParts);
            extractParts(premise.getObjectIRI().asStringIRI(), extractedParts);
        }

        return extractedParts.size() - 1;
    }

    private static void extractParts(String input, Set<String> extractedParts) {
        if (input != null) {
            String[] parts = input.split("/");
            String lastPart = parts[parts.length - 1];
            if (lastPart.contains("_")) {
                extractedParts.add(lastPart.split("_")[0]);
            }
        }
    }

    private static InferredMapping populateFromEntities(InferredMapping inferredMapping,
                                                        Map<String, EntityDetails> iriToEntityDetails) {
        EntityDetails subjectDetails = iriToEntityDetails.get(inferredMapping.getSubjectIRI());
        if (subjectDetails != null) {
            if (subjectDetails.isCuriePresent())
                inferredMapping.setSubjectId(subjectDetails.getCurie());
            if (subjectDetails.isLabelPresent())
                inferredMapping.setSubjectLabel(subjectDetails.getLabel());
        }

        EntityDetails predicateDetails = iriToEntityDetails.get(inferredMapping.getPredicateIRI());
        if (predicateDetails != null) {
            if (predicateDetails.isCuriePresent())
                inferredMapping.setPredicateId(predicateDetails.getCurie());
            if (predicateDetails.isLabelPresent())
                inferredMapping.setPredicateLabel(predicateDetails.getLabel());
        }

        EntityDetails objectDetails = iriToEntityDetails.get(inferredMapping.getObjectIRI());
        if (objectDetails != null) {
            if (objectDetails.isCuriePresent())
                inferredMapping.setObjectId(objectDetails.getCurie());
            if (objectDetails.isLabelPresent())
                inferredMapping.setObjectLabel(objectDetails.getLabel());
        }
        return inferredMapping;
    }

    private static Options getOptions() {
        Options options = new Options();

        Option nemoInferences = new Option("n", "nemoInferences", true,
                "The file containing Nemo traces for inferred mappings");
        nemoInferences.setRequired(true);
        options.addOption(nemoInferences);

        Option inputDirectory = new Option("i", "inputDirectory", true,
                "A directory containing mappings in .json format.");
        inputDirectory.setRequired(true);
        options.addOption(inputDirectory);

        Option outputFile = new Option("o", "outputFile", true,
                "JSON output file of inferred mappings with explanations for each inferred mapping.");
        outputFile.setRequired(true);
        options.addOption(outputFile);

        return options;
    }

    /**
     * Recursively reads all .json files in the inputDirectory, deserializes them as Mapping objects,
     * and stores them in a Map<MappingId, Mapping>.
     *
     * @param inputDirectory the directory to search for .json files
     * @return a Map from MappingId (UUID) to Mapping
     */
    public static Map<MinimalMapping, List<Mapping>> readExistingMappings(String inputDirectory) {
        Map<MinimalMapping, List<Mapping>> mappingMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new Jdk8Module());
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

        try (Stream<Path> paths = Files.walk(Paths.get(inputDirectory))) {
            paths.filter(Files::isRegularFile)
                 .filter(path -> path.toString().endsWith(".json"))
                 .forEach(path -> {
                     try {
                         List<Mapping> mappings = objectMapper.readValue(path.toFile(),
                                 new com.fasterxml.jackson.core.type.TypeReference<List<Mapping>>() {});

                         mappings.forEach(mapping -> {
                             MinimalMapping miniMapping;
                             try {
                                 miniMapping = new MinimalMapping(mapping.subjectIRI().get().getDataAsString(),
                                         mapping.predicateIRI().get().getDataAsString(), mapping.objectIRI().get().getDataAsString());
                                 List correspondingMappings = mappingMap.get(miniMapping);

                                 if (correspondingMappings == null) {
                                     correspondingMappings = new ArrayList();
                                 }
                                 correspondingMappings.add(mapping);
                                 mappingMap.put(miniMapping, correspondingMappings);
                             } catch (Throwable t) {
                                logger.error("Error reading mapping ({}, {}, {})", mapping.subjectIRI(),
                                        mapping.predicateIRI(), mapping.objectIRI(), t);
                             }
                         });
                     } catch (Exception e) {
                         logger.error("Failed to read Mapping from file: {}", path, e);
                     }
                 });
        } catch (IOException e) {
            logger.error("Error walking input directory: {}", inputDirectory, e);
        }
        return mappingMap;
    }

}