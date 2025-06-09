package uk.ac.ebi.spot.oxo.inferences.nemo;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.spot.oxo.inferences.nemo.helpers.NemoHelper;
import uk.ac.ebi.spot.oxo.inferences.nemo.model.NemoInferences;
import uk.ac.ebi.spot.oxo.model.sssom.InferredMapping;
import uk.ac.ebi.spot.oxo.model.sssom.Mapping;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static uk.ac.ebi.spot.oxo.inferences.nemo.ExplainInferredMappings.createMappings;
import static uk.ac.ebi.spot.oxo.inferences.nemo.ExplainInferredMappings.writeMappingsAsJson;

class NemoInferenceReaderTest {
    private static final Logger logger = LoggerFactory.getLogger(NemoInferenceReaderTest.class);

    @Test
    void testReadInferences() throws IOException {
        String filePath = "/home/henriette007/ebi-dev/oxo2/data/inferences-chains.json";

        // Read the inferences using NemoInferenceReader
        NemoInferences nemoInferences = NemoInferenceReader.readInferences(filePath);
        Set<InferredMapping> inferredMappings = NemoHelper.fromNemoInferencesToInferredMappings(nemoInferences);
        Set<InferredMapping> inferredMappingsWithNullChainRules = inferredMappings.stream()
                .filter(m -> m.getChainRuleApplications().get().getChainRule().isEmpty())
                .collect(Collectors.toSet());


        List<Mapping> mappings = createMappings(inferredMappings);
        List<Mapping> mappingsToWrite = mappings.stream()
//                .filter(m -> m.distance() > 3)
//                .filter(m -> m.subjectId().get().)
                .collect(Collectors.toList());

        writeMappingsAsJson(mappingsToWrite, "/home/henriette007/ebi-dev/oxo2/data/sssom_as_json/inferred-mappings-new.json");

    }
}
