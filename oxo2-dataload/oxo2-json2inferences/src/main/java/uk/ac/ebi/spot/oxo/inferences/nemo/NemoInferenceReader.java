package uk.ac.ebi.spot.oxo.inferences.nemo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.spot.oxo.inferences.nemo.model.NemoInferences;

import java.io.File;
import java.io.IOException;

public class NemoInferenceReader { // Renamed from InferenceReader

    private static final Logger logger = LoggerFactory.getLogger(NemoInferenceReader.class);

    public static NemoInferences readInferences(String filePath) throws IOException {

        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(new File(filePath), NemoInferences.class);
    }

    public static void main(String[] args) {
        Options options = getOptions();
        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            logger.error("Error parsing command line arguments: {}", e.getMessage());
            formatter.printHelp("NemoInferenceReader", options); // Updated class reference
            System.exit(1);
            return;
        }

        String nemoInferences = cmd.getOptionValue("nemoInferences");
        String derivedMappings = cmd.getOptionValue("derivedMappings");

        logger.info("Nemo Inferences File: {}", nemoInferences);
        logger.info("Derived Mappings File: {}", derivedMappings);

        try {
            NemoInferences nemoInference = readInferences(nemoInferences);
            logger.info("Final Conclusions: {}", nemoInference.getFinalConclusion());
            logger.info("Inferences: {}", nemoInference.getInferences());
            // Additional logic for derivedMappings can be added here if needed
        } catch (IOException e) {
            logger.error("Error reading inferences: {}", e.getMessage());
        }
    }

    private static Options getOptions() {
        Options options = new Options();

        Option nemoInferences = new Option("n", "nemoInferences", true, "Path to the Nemo Inferences JSON file");
        nemoInferences.setRequired(true);
        options.addOption(nemoInferences);

        Option derivedMappings = new Option("d", "derivedMappings", true, "Path to the Derived Mappings file");
        derivedMappings.setRequired(true);
        options.addOption(derivedMappings);

        return options;
    }
}
