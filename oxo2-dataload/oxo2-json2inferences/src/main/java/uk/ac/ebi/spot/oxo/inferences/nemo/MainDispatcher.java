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
        logger.debug("command: " + command);
        logger.debug("remainingArgs: " + Arrays.toString(remainingArgs));

        switch (command) {
            case "json2ttl":
                JSON2Turtle.main(remainingArgs);
                break;
            case "inferences2trace":
                uk.ac.ebi.spot.oxo.inferences.nemo.Inferences2Trace.main(remainingArgs);
                break;
            case "explanations2json":
                uk.ac.ebi.spot.oxo.inferences.nemo.ExplainInferredMappings.main(remainingArgs);
                break;
            default:
                logger.error("Unknown command: {}", command);
                printUsage();
                System.exit(1);
        }
    }

    private static void printUsage() {
        System.out.println("Usage: java -jar oxo2-json2inferences/target/oxo2-json2inferences-1.0.0-SNAPSHOT.jar <command> [args...]");
        System.out.println("Available commands:");
        System.out.println("  json2ttl  - Creates .ttl file from mappings in .json. ");
        System.out.println("  inferences2trace  - Creates a file of inferences to trace in the format expected by Nemo.");
        System.out.println("  explanations2json - Creates a .json file of inferred mappings with their explanations.");
    }
}
