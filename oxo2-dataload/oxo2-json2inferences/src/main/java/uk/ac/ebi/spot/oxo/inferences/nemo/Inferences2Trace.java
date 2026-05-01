package uk.ac.ebi.spot.oxo.inferences.nemo;

import org.apache.commons.cli.*;
import org.apache.jena.graph.Triple;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.system.StreamRDFBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

/**
 * Reads a .ttl file of inferred mappings and writes them in the {@code ;}-separated
 * {@code mapping(<s>,<p>,<o>);...} format expected by nmo's --trace-input-file.
 *
 * Streams triples directly from the parser to the output writer rather than loading the
 * entire Model into memory: ncbitaxon's inferred TTL OOM-killed the previous Model-based
 * implementation even at 16 GB, because that path materialised the Model, then a List of
 * Statements, then a single concatenated String. This version uses constant memory
 * regardless of input size.
 */
public class Inferences2Trace {

    private static final Logger logger = LoggerFactory.getLogger(Inferences2Trace.class);

    public static void main(String[] args) {
        Options options = getOptions();
        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            logger.error("Error parsing command line arguments", e);
            formatter.printHelp("Inferences2Trace", options);
            System.exit(1);
            return;
        }

        String inferredMappingsToTrace = cmd.getOptionValue("inferredMappings");
        String outputFile = cmd.getOptionValue("outputFile");

        logger.info("Mappings with inferred mappings: {}", inferredMappingsToTrace);
        logger.info("Output File: {}", outputFile);

        try {
            processMappings(inferredMappingsToTrace, outputFile);
        } catch (IOException e) {
            logger.error("Error processing mappings", e);
            System.exit(1);
        }
    }

    private static void processMappings(String inferredTTL, String outputFile) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            StreamingMappingWriter sink = new StreamingMappingWriter(writer);
            RDFParser.source(inferredTTL).parse(sink);
            sink.checkError();
            logger.info("Wrote {} mapping(s)", sink.getCount());
        }
    }

    /**
     * StreamRDF callback that writes each triple as {@code mapping(<s>,<p>,<o>)} to a
     * BufferedWriter, separated by {@code ;}. Skips self-mappings (subject == object,
     * matching the prior behaviour). Captures any IOException so the parser can finish
     * gracefully and the caller can re-throw.
     */
    private static class StreamingMappingWriter extends StreamRDFBase {
        private final BufferedWriter writer;
        private boolean first = true;
        private long count = 0;
        private IOException error = null;

        StreamingMappingWriter(BufferedWriter writer) {
            this.writer = writer;
        }

        @Override
        public void triple(Triple triple) {
            if (error != null) return;
            String s = triple.getSubject().toString();
            String o = triple.getObject().toString();
            if (s.equals(o)) return;
            String p = triple.getPredicate().toString();
            try {
                if (!first) writer.write(';');
                writer.write("mapping(<");
                writer.write(s);
                writer.write(">,<");
                writer.write(p);
                writer.write(">,<");
                writer.write(o);
                writer.write(">)");
                first = false;
                count++;
            } catch (IOException e) {
                error = e;
            }
        }

        long getCount() {
            return count;
        }

        void checkError() throws IOException {
            if (error != null) throw error;
        }
    }

    private static Options getOptions() {
        Options options = new Options();

        Option inferredMappingsFile = new Option("i", "inferredMappings", true,
                "Turtle file consisting original and inferred mappings.");
        inferredMappingsFile.setRequired(true);
        options.addOption(inferredMappingsFile);

        Option outputFile = new Option("o", "outputFile", true, "Output file");
        outputFile.setRequired(true);
        options.addOption(outputFile);

        return options;
    }
}
