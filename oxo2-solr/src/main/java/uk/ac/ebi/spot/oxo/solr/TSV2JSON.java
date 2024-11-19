package uk.ac.ebi.spot.oxo.solr;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;


import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.spot.oxo.model.sssom.NodeReference;

public class TSV2JSON {
    private static final Logger logger = LoggerFactory.getLogger(TSV2JSON.class);
    public static void main(String[] args) {
        List<NodeReference> nodeReferenceList = new ArrayList<>();
        NodeReference.Builder nodeReferenceBuilder1 = new NodeReference.Builder(
                "EFO:0000001", "experimental factor", NodeReference.NodeReferenceEnum.SUBJECT);
        nodeReferenceBuilder1.source("https://www.ebi.ac.uk/ols4/ontologies/efo");
        NodeReference.Builder nodeReferenceBuilder2 = new NodeReference.Builder(
                "DUO:0000001", "data use", NodeReference.NodeReferenceEnum.SUBJECT);
        nodeReferenceBuilder2.source("https://www.ebi.ac.uk/ols4/ontologies/duo");
        nodeReferenceBuilder2.sourceVersion("v1.0");

        NodeReference nodeReference1 = nodeReferenceBuilder1.build();
        NodeReference nodeReference2 = nodeReferenceBuilder2.build();

        nodeReferenceList.add(nodeReference1);
        nodeReferenceList.add(nodeReference2);

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new Jdk8Module());
        Optional<String> jsonResult = Optional.empty();
        try {
            jsonResult = Optional.of(mapper.writeValueAsString(nodeReferenceList));
        } catch (JsonProcessingException e) {
            logger.error("Error writing JSON: {}", e);
        }

        jsonResult.ifPresentOrElse(
                v -> logger.info("JSON string = {}", v),
                () -> logger.error("JSON string is empty"));  ;

    }
}
