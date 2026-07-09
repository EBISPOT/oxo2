package uk.ac.ebi.spot.oxo.inferences.nemo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Set;
import java.util.TreeSet;

/**
 * Unions the {@code mapping_set_source} of the per-bundle inferred {@link
 * uk.ac.ebi.spot.oxo.model.sssom.MappingSet} JSONs into the single cross-set inferred set
 * (ADR-0016, ADR-0028).
 *
 * <p>Under component sharding each {@code explanations2json} bundle sees only the asserted premises
 * of its own shards, so each writes a MappingSet carrying a <em>partial</em> source union. They all
 * share one {@code mapping_set_id}, so posting them to Solr unmerged would collapse to whichever
 * bundle was indexed last — silently losing most of the provenance. This merges them instead.
 *
 * <p>Every field other than {@code mapping_set_source} is identical across bundles (it is derived
 * from the inferred set id, not from the data), so the first file supplies the template.
 */
public class MergeInferredMappingSets {

    private static final Logger logger = LoggerFactory.getLogger(MergeInferredMappingSets.class);

    private static final String MAPPING_SET_SOURCE = "mapping_set_source";

    public static void main(String[] args) throws Exception {
        Options options = getOptions();
        CommandLine cmd;
        try {
            cmd = new DefaultParser().parse(options, args);
        } catch (ParseException e) {
            logger.error("Error parsing command line arguments", e);
            new HelpFormatter().printHelp("MergeInferredMappingSets", options);
            System.exit(1);
            return;
        }

        String[] inputFiles = cmd.getOptionValues("inputFile");
        String outputFile = cmd.getOptionValue("outputFile");
        if (inputFiles == null || inputFiles.length == 0) {
            logger.error("At least one -i inputFile is required.");
            System.exit(1);
            return;
        }

        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode merged = null;
        Set<String> sources = new TreeSet<>();

        for (String inputFile : inputFiles) {
            JsonNode root = objectMapper.readTree(new File(inputFile));
            if (!root.isArray() || root.isEmpty()) {
                logger.warn("Skipping {}: not a non-empty JSON array", inputFile);
                continue;
            }
            ObjectNode mappingSet = (ObjectNode) root.get(0);
            if (merged == null) {
                merged = mappingSet.deepCopy();
            }
            JsonNode sourceNode = mappingSet.get(MAPPING_SET_SOURCE);
            if (sourceNode != null && sourceNode.isArray()) {
                sourceNode.forEach(source -> sources.add(source.asText()));
            }
        }

        if (merged == null) {
            logger.error("No inferred MappingSet found in any of the {} input file(s)", inputFiles.length);
            System.exit(1);
            return;
        }

        ArrayNode sourceArray = merged.putArray(MAPPING_SET_SOURCE);
        sources.forEach(sourceArray::add);

        ArrayNode output = objectMapper.createArrayNode();
        output.add(merged);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(outputFile), output);
        logger.info("Merged {} partial inferred MappingSet(s) into {} with {} contributing source set(s)",
                inputFiles.length, outputFile, sources.size());
    }

    private static Options getOptions() {
        Options options = new Options();

        Option inputFile = new Option("i", "inputFile", true,
                "Per-bundle inferred MappingSet JSON file(s) to merge. Repeatable.");
        inputFile.setArgs(Option.UNLIMITED_VALUES);
        inputFile.setRequired(true);
        options.addOption(inputFile);

        Option outputFile = new Option("o", "outputFile", true,
                "Output file for the merged inferred MappingSet (JSON array of one).");
        outputFile.setRequired(true);
        options.addOption(outputFile);

        return options;
    }
}
