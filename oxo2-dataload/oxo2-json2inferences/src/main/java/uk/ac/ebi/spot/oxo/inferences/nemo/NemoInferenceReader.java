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

//    public static NemoInferences normalizeInferences(NemoInferences nemoInferences) {
//        nemoInferences.getInferencesSet()
//    }
}
