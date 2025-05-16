package uk.ac.ebi.spot.oxo.inferences;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.spot.oxo.inferences.nemo.NemoInferenceReader;
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

class NemoNemoInferenceReaderTest {
    private static final Logger logger = LoggerFactory.getLogger(NemoNemoInferenceReaderTest.class);

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
        List<Mapping> mappingsWithNullChainRuleInExplanation = mappings.stream()
                .filter(m -> m.explanation().stream()
                        .anyMatch(e -> e.getChainRules().isEmpty()))
                .collect(Collectors.toList());

        writeMappingsAsJson(mappings, "/home/henriette007/ebi-dev/oxo2/data/sssom_as_json/inferred-mappings.json");

    }
}
