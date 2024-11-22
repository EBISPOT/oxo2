package uk.ac.ebi.spot.oxo.solr;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;


import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.spot.oxo.model.sssom.*;

public class TSV2JSONTest {
    private static final Logger logger = LoggerFactory.getLogger(TSV2JSONTest.class);
    public static void main(String[] args) {
        List<NodeReference> nodeReferenceList = new ArrayList<>();
        NodeReference.Builder nodeReferenceBuilder1 = new NodeReference.Builder(
                "EFO:0003777", "heart disease", NodeReference.NodeReferenceEnum.SUBJECT)
                .type(EntityTypeEnum.OWL_CLASS)
                .catergory("Disease")
                .source("https://www.ebi.ac.uk/ols4/ontologies/efo/classes/http%253A%252F%252Fwww.ebi.ac.uk%252Fefo%252FEFO_0003777")
                .sourceVersion("v3.72")
                .matchField(new TreeSet<>(List.of("rdfs:label", "skos:prefLabel")))
                .preprocessing(new ArrayList<>(List.of(
                        new EntityReference("semapv:Stemming"),
                        new EntityReference("semapv:StopwordsRemoval"))));

        NodeReference.Builder nodeReferenceBuilder2 = new NodeReference.Builder(
                "MONDO:0005267", "heart disorder", NodeReference.NodeReferenceEnum.OBJECT);
        nodeReferenceBuilder2.source("http://purl.obolibrary.org/obo/MONDO_0005267");


        NodeReference.Builder nodeReferenceBuilder3 = new NodeReference.Builder(
                "NCIT:C3079", "Heart Disorder", NodeReference.NodeReferenceEnum.OBJECT);
        nodeReferenceBuilder3.source("http://purl.obolibrary.org/obo/NCIT_C3079");

        NodeReference nodeReference1 = nodeReferenceBuilder1.build();
        NodeReference nodeReference2 = nodeReferenceBuilder2.build();
        NodeReference nodeReference3 = nodeReferenceBuilder3.build();

        nodeReferenceList.add(nodeReference1);
        nodeReferenceList.add(nodeReference2);


        List<Mapping> mappingList = new ArrayList<>();
        Mapping.Builder mappingBuilder1 = new Mapping.Builder()
                .subject(nodeReference1)
                .predicate((new PredicateReference.Builder("oboInOwl:hasDbXref",
                        "has database cross reference")).build())
                .object(nodeReference2)
                .mappingJustification(
                        new EntityReference("https://www.ebi.ac.uk/ols4/ontologies/efo/classes/http%253A%252F%252Fwww.ebi.ac.uk%252Fefo%252FEFO_0003777"))
                .author(new TreeSet<>(new ArrayList<>(List.of(
                                new LabelledReference("Zoe May Pendlington"),
                                new LabelledReference("https://orcid.org/0000-0001-7251-9504","Henriette")))))
                .license("CC-BY-4.0")
                .mappingProvider("https://www.ebi.ac.uk/")
                .mappingSource(new EntityReference("https://www.ebi.ac.uk/ols4"))
                .mappingCardinality(MappingCardinalityEnum.ONE_TO_ONE)
                .mappingTool("OxO2")
                .mappingToolVersion("2.0.0")
                .mappingDate("2021-05-11")
                .publicationDate("2021-06-01")
                .confidence("0.9")
                .curationRule(new TreeSet<>(new ArrayList<>(List.of(
                        new CurationRule(new EntityReference("DISEASE_MAPPING_COMMONS_RULES:MPR2"),Optional.of("MPR2")),
                        new CurationRule(new EntityReference("DISEASE_MAPPING_COMMONS_RULES:MPR3"), Optional.empty())))))
                .matchString(new TreeSet<>(new ArrayList<>(List.of(
                        "heart",
                        "disease"))))
                .similarityScore("0.9")
                .similarityMeasure("https://www.wikidata.org/entity/Q865360 - Jaccard index")
                .seeAlso(new TreeSet<>(new ArrayList<>(List.of(
                        "https://github.com/mapping-commons/mapping-commons-template",
                        "https://mapping-commons.github.io/sssom/Mapping/"))))
                .other("key1=value1|key2=value2")
                .issueTrackerItem(new EntityReference("https://github.com/mapping-commons/mapping-commons-template/issues"));


        Mapping.Builder mappingBuilder2 = new Mapping.Builder()
                .subject(nodeReference1)
                .predicate((new PredicateReference.Builder("oboInOwl:hasDbXref","has database cross reference"))
                        .predicateModifier(PredicateModifierEnum.NOT)
                        .build())
                .object(nodeReference3);

        Mapping mapping1 = mappingBuilder1.build();
        Mapping mapping2 = mappingBuilder2.build();

        mappingList.add(mapping1);
        mappingList.add(mapping2);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new Jdk8Module());
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_ABSENT);

        Optional<String> jsonResult = Optional.empty();
        try {
            jsonResult = Optional.of(objectMapper.writeValueAsString(mappingList));
        } catch (JsonProcessingException e) {
            logger.error("Error writing JSON: {}", e);
        }

        jsonResult.ifPresentOrElse(
                v -> logger.info("JSON string = {}", v),
                () -> logger.error("JSON string is empty"));


    }
}
