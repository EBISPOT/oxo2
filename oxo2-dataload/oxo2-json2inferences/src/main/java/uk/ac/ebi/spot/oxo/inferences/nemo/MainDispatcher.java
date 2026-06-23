package uk.ac.ebi.spot.oxo.inferences.nemo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public class MainDispatcher {
    private static final Logger logger = LoggerFactory.getLogger(MainDispatcher.class);

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }

        String command = args[0];
        String[] remainingArgs = new String[args.length - 1];
        System.arraycopy(args, 1, remainingArgs, 0, args.length - 1);
        logger.debug("command: {}", command);
        logger.debug("remainingArgs: {}", Arrays.toString(remainingArgs));

        try {
            switch (command) {
                case "json2nquads":
                    JSON2NQuads.main(remainingArgs);
                    break;
                case "inferences2trace":
                    uk.ac.ebi.spot.oxo.inferences.nemo.Inferences2Trace.main(remainingArgs);
                    break;
                case "explanations2json":
                    uk.ac.ebi.spot.oxo.inferences.nemo.ExplainInferredMappings.main(remainingArgs);
                    break;
                case "mergeChainFiles":
                    uk.ac.ebi.spot.oxo.inferences.nemo.MergeChainFiles.main(remainingArgs);
                    break;
                default:
                    logger.error("Unknown command: {}", command);
                    printUsage();
                    System.exit(1);
            }
        } catch (Throwable t) {
            logger.error("Error while processing command: {}", command, t);
            long freeMemory = Runtime.getRuntime().freeMemory();
            long totalMemory = Runtime.getRuntime().totalMemory();
            long maxMemory = Runtime.getRuntime().maxMemory();
            long availableMemory = maxMemory - (totalMemory - freeMemory);

            logger.error("Free memory: {}", freeMemory);
            logger.error("Total memory: {}", totalMemory);
            logger.error("Max memory: {}", maxMemory);
            logger.error("Available memory: {} ", availableMemory);

            // Fail loudly. Without this, a fatal error (e.g. OutOfMemoryError from the chain
            // merge) would be logged and then the JVM would exit 0, so the wrapper's `set -e`
            // and Nextflow both see success while the expected output file is missing. Combined
            // with `optional: true` outputs that silently dropped cross-set inferences instead of
            // failing the run. Exit non-zero so the failure surfaces as a failed task.
            System.exit(1);
        }
    }

    private static void printUsage() {
        System.out.println("Usage: java -jar oxo2-json2inferences/target/oxo2-json2inferences-1.0.0-SNAPSHOT.jar <command> [args...]");
        System.out.println("Available commands:");
        System.out.println("  json2nquads  - Creates .nq (N-Quads) file from mappings in .json, carrying mapping_id. ");
        System.out.println("  inferences2trace  - Creates a file of inferences to trace in the format expected by Nemo.");
        System.out.println("  explanations2json - Creates a .json file of inferred mappings with their explanations.");
        System.out.println("  mergeChainFiles  - Merges per-chunk Nemo chain trace JSON files into one per-mapping-set file.");
    }
}
