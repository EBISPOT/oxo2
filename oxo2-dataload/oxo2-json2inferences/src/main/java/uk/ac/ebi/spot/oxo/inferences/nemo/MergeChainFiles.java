package uk.ac.ebi.spot.oxo.inferences.nemo;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.spot.oxo.inferences.nemo.model.NemoInferences;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Merge per-chunk Nemo chain trace JSON files into a single per-mapping-set chain file.
 *
 * Inputs: any number of files, each containing one NemoInferences JSON object (same schema
 * NemoInferenceReader consumes). Output: one NemoInferences JSON object whose finalConclusion
 * is the de-duplicated union of input finalConclusions and whose inferences are de-duplicated
 * by conclusion (the same conclusion can be re-derived in multiple chunks because tracing chunk
 * N may walk into premises that chunk M's facts also depend on; nemo derives one canonical trace
 * per conclusion, so the first occurrence is representative).
 *
 * The merge streams each chunk's inferences straight to the output via a {@link JsonGenerator}
 * and keeps only the SET OF CONCLUSIONS already written as the dedup key — never the whole
 * inference union in memory. Merge memory is therefore proportional to the number of distinct
 * conclusions (one string each) rather than to the full size of every chain (premises + rule
 * text). The previous implementation accumulated every NemoInference in a LinkedHashSet and
 * OOM'd the cross-set merge on a large corpus.
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
        // Flush once at close() rather than after every streamed inference.
        objectMapper.disable(SerializationFeature.FLUSH_AFTER_WRITE_VALUE);

        // Held in memory: the conclusions already written (dedup key) and the union of final
        // conclusions (the smaller, leaf-only set). The inference objects themselves — premises and
        // rule text, the bulk of the data — are streamed to disk and never accumulated.
        Set<String> writtenConclusions = new HashSet<>();
        LinkedHashSet<String> mergedFinalConclusion = new LinkedHashSet<>();
        long writtenInferences = 0;

        try (JsonGenerator generator =
                     objectMapper.getFactory().createGenerator(new File(outputFile), JsonEncoding.UTF8)) {
            generator.writeStartObject();

            // Stream inferences first, deduplicating by conclusion as each chunk is read. Each chunk
            // is parsed and released one at a time (a chunk is bounded by trace_chunk_size); only the
            // dedup key set persists. Field order is irrelevant to downstream readers (they map by
            // name) and to the integration comparator (it canonicalises keys + array order).
            generator.writeArrayFieldStart("inferences");
            for (String inputFile : inputFiles) {
                logger.info("Merging chunk: {}", inputFile);
                NemoInferences chunk = objectMapper.readValue(new File(inputFile), NemoInferences.class);

                if (chunk.getFinalConclusion() != null) {
                    mergedFinalConclusion.addAll(chunk.getFinalConclusion());
                }
                if (chunk.getInferences() != null) {
                    for (NemoInferences.NemoInference inference : chunk.getInferences()) {
                        // First occurrence of a conclusion wins, matching the old conclusion-keyed
                        // LinkedHashSet semantics.
                        if (writtenConclusions.add(inference.getConclusion())) {
                            objectMapper.writeValue(generator, inference);
                            writtenInferences++;
                        }
                    }
                }
            }
            generator.writeEndArray();

            generator.writeArrayFieldStart("finalConclusion");
            for (String finalConclusion : mergedFinalConclusion) {
                generator.writeString(finalConclusion);
            }
            generator.writeEndArray();

            generator.writeEndObject();
        }

        logger.info("Merged {} chunk(s) into {} ({} unique conclusions, {} unique inferences)",
                inputFiles.length, outputFile,
                mergedFinalConclusion.size(), writtenInferences);
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
