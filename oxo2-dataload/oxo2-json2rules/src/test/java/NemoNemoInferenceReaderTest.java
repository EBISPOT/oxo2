package uk.ac.ebi.spot.oxo.inferences;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.spot.oxo.inferences.nemo.NemoInferenceReader;
import uk.ac.ebi.spot.oxo.inferences.nemo.helpers.NemoHelper;
import uk.ac.ebi.spot.oxo.inferences.nemo.model.NemoInferences;
import uk.ac.ebi.spot.oxo.model.sssom.InferredMapping;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

class NemoNemoInferenceReaderTest {
    private static final Logger logger = LoggerFactory.getLogger(NemoNemoInferenceReaderTest.class);

    @Test
    void testReadInferences() throws IOException {
        String filePath = "src/test/resources/inferences-chains.json";

        // Read the inferences using NemoInferenceReader
        NemoInferences nemoInferences = NemoInferenceReader.readInferences(filePath);

        // Assert final conclusions
        List<String> finalConclusions = nemoInferences.getFinalConclusion();
//        assertEquals(3, finalConclusions.size());
//        assertTrue(finalConclusions.contains("mapping(<http://www.orpha.net/ORDO/Orphanet_60033>, <http://www.geneontology.org/formats/oboInOwl#hasDbXref>, <https://omim.org/MIM:613021>)"));
//        assertTrue(finalConclusions.contains("mapping(<http://www.orpha.net/ORDO/Orphanet_60033>, <http://www.geneontology.org/formats/oboInOwl#hasDbXref>, <https://icd.codes/icd10cm/J47>)"));
//        assertTrue(finalConclusions.contains("mapping(<http://purl.obolibrary.org/obo/DOID_9563>, <http://www.geneontology.org/formats/oboInOwl#hasDbXref>, <https://omim.org/MIM:613021>)"));

        // Assert inferences
        List<NemoInferences.NemoInference> inferences = nemoInferences.getInferences();
//        assertEquals(5, inferences.size());
//
//        NemoInferences.NemoInference firstNemoInference = inferences.get(0);
//        assertEquals("mapping(?a, ?p, ?c) :- mapping(?a, <http://www.w3.org/2002/07/owl#equivalentClass>, ?b), mapping(?b, ?p, ?c) .", firstNemoInference.getRuleName());
//        assertEquals("mapping(<http://www.orpha.net/ORDO/Orphanet_60033>, <http://www.geneontology.org/formats/oboInOwl#hasDbXref>, <https://omim.org/MIM:613021>)", firstNemoInference.getConclusion());
//        assertEquals(2, firstNemoInference.getPremises().size());
//        assertTrue(firstNemoInference.getPremises().contains("mapping(<http://www.orpha.net/ORDO/Orphanet_60033>, <http://www.w3.org/2002/07/owl#equivalentClass>, <http://purl.obolibrary.org/obo/DOID_9563>)"));
//        assertTrue(firstNemoInference.getPremises().contains("mapping(<http://purl.obolibrary.org/obo/DOID_9563>, <http://www.geneontology.org/formats/oboInOwl#hasDbXref>, <https://omim.org/MIM:613021>)"));
//
//        NemoInferences.NemoInference secondNemoInference = inferences.get(1);
//        assertEquals("Asserted", secondNemoInference.getRuleName());
//        assertEquals("mapping(<http://purl.obolibrary.org/obo/DOID_9563>, <http://www.geneontology.org/formats/oboInOwl#hasDbXref>, <https://omim.org/MIM:613021>)", secondNemoInference.getConclusion());
//        assertTrue(secondNemoInference.getPremises().isEmpty());

        // Generate DerivedMapping instances from inferences
        Set<InferredMapping> inferredMappings = NemoHelper.fromNemoInferencesToInferredMappings(nemoInferences);
        Set<InferredMapping> inferredMappingsWithChainRules = inferredMappings.stream()
                .filter(m -> m.getChainRuleApplications().isPresent())
//                .filter(m -> m.getChainRuleApplications().get().getPremises().get(0).getChainRuleApplications().isPresent())
                .collect(Collectors.toSet());

        int i = 0;
        inferredMappingsWithChainRules.forEach(inferredMapping -> {logger.info(inferredMapping.toString());});

//        // Assert DerivedMappings
//        assertEquals(5, inferredMappings.size());
//        InferredMapping firstMapping = inferredMappings.get(0);
//        assertEquals("http://www.orpha.net/ORDO/Orphanet_60033", firstMapping.subjectIRI().orElseThrow().toString());
//        assertEquals("http://www.geneontology.org/formats/oboInOwl#hasDbXref", firstMapping.predicateIRI().orElseThrow().toString());
//        assertEquals("https://omim.org/MIM:613021", firstMapping.objectIRI().orElseThrow().toString());
//
//        InferredMapping secondMapping = inferredMappings.get(1);
//        assertEquals("http://purl.obolibrary.org/obo/DOID_9563", secondMapping.subjectIRI().orElseThrow().toString());
//        assertEquals("http://www.geneontology.org/formats/oboInOwl#hasDbXref", secondMapping.predicateIRI().orElseThrow().toString());
//        assertEquals("https://omim.org/MIM:613021", secondMapping.objectIRI().orElseThrow().toString());
    }
}
