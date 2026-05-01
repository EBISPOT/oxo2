package uk.ac.ebi.spot.oxo.inferences.nemo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.spot.oxo.inferences.nemo.model.NemoInferences;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;

/**
 * Merge per-chunk Nemo chain trace JSON files into a single per-mapping-set chain file.
 *
 * Inputs: any number of files, each containing one NemoInferences JSON object (same schema
 * NemoInferenceReader consumes). Output: one NemoInferences JSON object whose finalConclusion
 * is the de-duplicated union of input finalConclusions and whose inferences are de-duplicated
 * by conclusion. NemoInference.equals/hashCode are conclusion-keyed, so a LinkedHashSet
 * deduplicates the same conclusion if it was re-derived in multiple chunks (which can happen
 * because tracing chunk N may walk into premises that chunk M's facts also depend on).
 */
public class MergeChainFiles {

    private static final Logger logger = LoggerFactory.getLogger(MergeChainFiles.class);

    public static void main(String[] args) {
        Options options = getOptions();
        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            logger.error("Error parsing command line arguments", e);
            formatter.printHelp("MergeChainFiles", options);
            System.exit(1);
            return;
        }

        String[] inputFiles = cmd.getOptionValues("inputFiles");
        String outputFile = cmd.getOptionValue("outputFile");

        if (inputFiles == null || inputFiles.length == 0) {
            logger.error("At least one input file is required.");
            formatter.printHelp("MergeChainFiles", options);
            System.exit(1);
            return;
        }

        try {
            mergeChainFiles(inputFiles, outputFile);
        } catch (IOException e) {
            logger.error("Error merging chain files", e);
            System.exit(1);
        }
    }

    static void mergeChainFiles(String[] inputFiles, String outputFile) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();

        LinkedHashSet<String> mergedFinalConclusion = new LinkedHashSet<>();
        LinkedHashSet<NemoInferences.NemoInference> mergedInferences = new LinkedHashSet<>();

        for (String inputFile : inputFiles) {
            logger.info("Merging chunk: {}", inputFile);
            NemoInferences chunk = objectMapper.readValue(new File(inputFile), NemoInferences.class);

            if (chunk.getFinalConclusion() != null) {
                mergedFinalConclusion.addAll(chunk.getFinalConclusion());
            }
            if (chunk.getInferences() != null) {
                mergedInferences.addAll(chunk.getInferences());
            }
        }

        NemoInferences merged = new NemoInferences();
        merged.setFinalConclusion(new ArrayList<>(mergedFinalConclusion));
        merged.setInferences(new ArrayList<>(mergedInferences));

        objectMapper.writeValue(new File(outputFile), merged);
        logger.info("Merged {} chunk(s) into {} ({} unique conclusions, {} unique inferences)",
                inputFiles.length, outputFile,
                mergedFinalConclusion.size(), mergedInferences.size());
    }

    private static Options getOptions() {
        Options options = new Options();

        Option inputFilesOpt = Option.builder("i").longOpt("inputFiles")
                .hasArgs()
                .desc("Input chain JSON files (space-separated).")
                .required()
                .build();
        options.addOption(inputFilesOpt);

        Option outputFileOpt = Option.builder("o").longOpt("outputFile")
                .hasArg()
                .desc("Output merged chain JSON file.")
                .required()
                .build();
        options.addOption(outputFileOpt);

        return options;
    }
}
