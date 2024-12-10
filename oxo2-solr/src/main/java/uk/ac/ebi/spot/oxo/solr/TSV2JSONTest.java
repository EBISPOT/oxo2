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

        List<Mapping> mappingList = new ArrayList<>();
        Mapping.Builder mappingBuilder1 = Mapping.Builder.builder()
                .subjectId("EFO:0003777")
                .subjectLabel("heart disease")
                .subjectCategory("Disease")
                .subjectType(EntityTypeEnum.OWL_CLASS)
                .subjectCategory("Disease")
                .subjectSource("https://www.ebi.ac.uk/ols4/ontologies/efo/classes/http%253A%252F%252Fwww.ebi.ac.uk%252Fefo%252FEFO_0003777")
                .subjectSourceVersion("v3.72")
                .subjectMatchField("rdfs:label|skos:prefLabel")
                .subjectPreprocessing(new ArrayList<>(List.of(
                        new EntityReference("semapv:Stemming"),
                        new EntityReference("semapv:StopwordsRemoval"))))
                .predicateId("oboInOwl:hasDbXref")
                .predicateLabel("has database cross reference")
                .predicateModifier(PredicateModifierEnum.NOT)
                .objectId("NCIT:C3079")
                .objectLabel("Heart Disorder")
                .objectType(EntityTypeEnum.OWL_CLASS)
                .objectSource("http://purl.obolibrary.org/obo/NCIT_C3079")
                .mappingJustification(
                        "https://www.ebi.ac.uk/ols4/ontologies/efo/classes/http%253A%252F%252Fwww.ebi.ac.uk%252Fefo%252FEFO_0003777")
                .authorId(new TreeSet<>(new ArrayList<>(List.of(
                        new EntityReference("https://orcid.org/0000-0001-7251-9504")))))
                .authorLabel(new TreeSet<>(new ArrayList<>(List.of("Zoe May Pendlington","Henriette"))))
                .license("CC-BY-4.0")
                .mappingProvider("https://www.ebi.ac.uk/")
                .mappingSource("https://www.ebi.ac.uk/ols4")
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
                .issueTrackerItem("https://github.com/mapping-commons/mapping-commons-template/issues");



        Mapping mapping1 = mappingBuilder1.build();


        mappingList.add(mapping1);

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
