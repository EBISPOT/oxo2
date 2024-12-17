package uk.ac.ebi.spot.oxo.sssom2json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;


import java.util.*;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.spot.oxo.model.sssom.*;

public class TSV2JSONTest {
    private static final Logger logger = LoggerFactory.getLogger(TSV2JSONTest.class);
    public static void main(String[] args) {

        List<Mapping> mappingList = new ArrayList<>();
        Mapping.Builder mappingBuilder1 = Mapping.builder()
                .authorId("https://orcid.org/0000-0001-7251-9504")
                .authorLabel("Zoe May Pendlington|Henriette")
                .comment("This is a comment")
                .confidence("0.9")
                .curationRule("DISEASE_MAPPING_COMMONS_RULES:MPR2|DISEASE_MAPPING_COMMONS_RULES:MPR3")
                .issueTrackerItem("https://github.com/mapping-commons/mapping-commons-template/issues")
                .license("CC-BY-4.0")
                .mappingCardinality("1:1")
                .mappingDate("2021-05-11")
                .mappingJustification(
                        "https://www.ebi.ac.uk/ols4/ontologies/efo/classes/http%253A%252F%252Fwww.ebi.ac.uk%252Fefo%252FEFO_0003777")
                .mappingProvider("https://www.ebi.ac.uk/")
                .mappingSetDescription("Mapping set between EFO and Disease Ontology")
                .mappingSetId("EFO to Disease")
                .mappingSetSource("https://www.ebi.ac.uk/ols4")
                .mappingSetTitle("EFO to Disease Mapping Set")
                .mappingSetVersion("v0.0.1")
                .mappingSource("https://www.ebi.ac.uk/ols4")
                .mappingTool("OxO2")
                .mappingToolVersion("2.0.0")
                .matchString("heart|disease")
                .objectCategory("Disease|Illness")
                .objectId("NCIT:C3079")
                .objectLabel("Heart Disorder")
                .objectMatchField("rdfs:label|skos:prefLabel", new TreeSet<>())
                .objectPreprocessing("semapv:Stemming|semapv:StopwordsRemoval", new ArrayList<>())
                .objectSource("http://purl.obolibrary.org/obo/NCIT_C3079")
                .objectSourceVersion("v1.0", Optional.empty())
                .objectType("owl class", Optional.empty())
                .other("key1=value1|key2=value2")
                .predicateId("oboInOwl:hasDbXref")
                .predicateLabel("has database cross reference")
                .predicateModifier("not")
                .publicationDate("2021-06-01")
                .reviewerId("https://orcid.org/0000-0002-7356-1779")
                .reviewerLabel("Nicolas Matentzoglu")
                .seeAlso("https://github.com/mapping-commons/mapping-commons-template|https://mapping-commons.github.io/sssom/Mapping/")
                .similarityMeasure("https://www.wikidata.org/entity/Q865360 - Jaccard index")
                .similarityScore("0.9")
                .subjectCategory("Disease")
                .subjectId("EFO:0003777")
                .subjectLabel("heart disease")
                .subjectMatchField("rdfs:label|skos:prefLabel")
                .subjectPreprocessing("semapv:Stemming|semapv:StopwordsRemoval", new ArrayList<>())
                .subjectSource("https://www.ebi.ac.uk/ols4/ontologies/efo/classes/http%253A%252F%252Fwww.ebi.ac.uk%252Fefo%252FEFO_0003777")
                .subjectSourceVersion("v3.72")
                .subjectType("owl class", Optional.empty());

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
                v -> logger.info("Mappings as JSON = {}", v),
                () -> logger.error("Mappings is empty"));

        SortedMap<String, String> curieMap = new TreeMap<>();
        curieMap.put("EFO", "http://www.ebi.ac.uk/efo/");
        curieMap.put("NCIT", "http://purl.obolibrary.org/obo/NCIT_");
        curieMap.put("DOID", "http://purl.obolibrary.org/obo/DOID_");

        List<MappingSet> mappingSets = new ArrayList<>();

        MappingSet mappingSet1 = MappingSet.builder()
                .comment("This is a comment")
                .creatorId(new TreeSet<>(Arrays.asList(new EntityReference("https://orcid.org/0000-0001-7251-9504"),
                        new EntityReference("https://orcid.org/0000-0002-7356-1779"))))
                .creatorLabel(new TreeSet<>(Arrays.asList("Zoe", "Arwa")))
                .curieMap(curieMap)
                .issueTracker("https://github.com/mapping-commons/mapping-commons-template/issues")
                .license("CC-BY-4.0")
                .mappingDate("2021-05-11")
                .mappingProvider("https://www.ebi.ac.uk/")
                .mappings(new TreeSet<>(mappingList))
                .mappingSetDescription("Mapping set between EFO and Disease Ontology")
                .mappingSetId("EFO to Disease")
                .mappingSetSource(new TreeSet<>(Arrays.asList(new Uri("https://www.ebi.ac.uk/ols4"), new Uri("https://www.ebi.ac.uk/ols4"))))
                .mappingSetTitle("EFO to Disease Mapping Set")
                .mappingSetVersion("v0.0.1")
                .mappingTool("OxO2")
                .mappingToolVersion("2.0.0")
                .objectMatchField(new TreeSet<>(Arrays.asList(new EntityReference("rdfs:label"), new EntityReference("skos:prefLabel"))))
                .objectPreprocessing(new ArrayList<>(Arrays.asList(new EntityReference("semapv:Stemming"), new EntityReference("semapv:StopwordsRemoval"))))
                .objectSource(new EntityReference("http://purl.obolibrary.org/obo/NCIT_C3079"))
                .objectSourceVersion("v1.0")
                .objectType(EntityTypeEnum.OWL_CLASS)
                .other(Optional.of(new KeyValuePairsAsString("key1=value1|key2=value2")))
                .publicationDate("2021-06-01")
                .seeAlso(new TreeSet(Arrays.asList("https://github.com/mapping-commons/mapping-commons-template|https://mapping-commons.github.io/sssom/Mapping/",
                        "https://www.ebi.ac.uk/ols4")))
                .subjectMatchField(new TreeSet<>(Arrays.asList(new EntityReference("rdfs:label"), new EntityReference("skos:prefLabel"))))
                .subjectPreprocessing(new ArrayList<>(Arrays.asList(new EntityReference("semapv:Stemming"), new EntityReference("semapv:StopwordsRemoval"))))
                .subjectSource(Optional.of(new EntityReference("https://www.ebi.ac.uk/ols4/ontologies/efo/classes/http%253A%252F%252Fwww.ebi.ac.uk%252Fefo%252FEFO_0003777")))
                .subjectSourceVersion("v3.72")
                .subjectType(EntityTypeEnum.OWL_CLASS)
                .build();

        mappingSets.add(mappingSet1);

        MappingSet mappingSet2 = MappingSet.builder()
                .comment("This is a comment")
                .creatorId(new TreeSet<>(Arrays.asList(new EntityReference("https://orcid.org/0000-0001-7251-9504"),
                        new EntityReference("https://orcid.org/0000-0002-7356-1779"))))
                .creatorLabel(new TreeSet<>(Arrays.asList("Zoe", "Arwa")))
                .curieMap(curieMap)
                .issueTracker("https://github.com/mapping-commons/mapping-commons-template/issues")
                .license("CC-BY-4.0")
                .mappingDate("2021-05-11")
                .mappingProvider("https://www.ebi.ac.uk/")
                .mappings(new TreeSet<>(mappingList))
                .mappingSetDescription("Mapping set between EFO and Disease Ontology")
                .mappingSetId("EFO to Disease - 001")
                .mappingSetSource(new TreeSet<>(Arrays.asList(new Uri("https://www.ebi.ac.uk/ols4"), new Uri("https://www.ebi.ac.uk/ols4"))))
                .mappingSetTitle("EFO to Disease Mapping Set")
                .mappingSetVersion("v0.0.1")
                .mappingTool("OxO2")
                .mappingToolVersion("2.0.0")
                .objectMatchField(new TreeSet<>(Arrays.asList(new EntityReference("rdfs:label"), new EntityReference("skos:prefLabel"))))
                .objectPreprocessing(new ArrayList<>(Arrays.asList(new EntityReference("semapv:Stemming"), new EntityReference("semapv:StopwordsRemoval"))))
                .objectSource(new EntityReference("http://purl.obolibrary.org/obo/NCIT_C3079"))
                .objectSourceVersion("v1.0")
                .objectType(EntityTypeEnum.OWL_CLASS)
                .other(Optional.of(new KeyValuePairsAsString("key1=value1|key2=value2")))
                .publicationDate("2021-06-01")
                .seeAlso(new TreeSet(Arrays.asList("https://github.com/mapping-commons/mapping-commons-template|https://mapping-commons.github.io/sssom/Mapping/",
                        "https://www.ebi.ac.uk/ols4")))
                .subjectMatchField(new TreeSet<>(Arrays.asList(new EntityReference("rdfs:label"), new EntityReference("skos:prefLabel"))))
                .subjectPreprocessing(new ArrayList<>(Arrays.asList(new EntityReference("semapv:Stemming"), new EntityReference("semapv:StopwordsRemoval"))))
                .subjectSource(Optional.of(new EntityReference("https://www.ebi.ac.uk/ols4/ontologies/efo/classes/http%253A%252F%252Fwww.ebi.ac.uk%252Fefo%252FEFO_0003777")))
                .subjectSourceVersion("v3.72")
                .subjectType(EntityTypeEnum.OWL_CLASS)
                .build();

        mappingSets.add(mappingSet2);

        Optional<String> jsonResult2 = Optional.empty();
        try {
            jsonResult2 = Optional.of(objectMapper.writeValueAsString(mappingSets));
        } catch (JsonProcessingException e) {
            logger.error("Error writing JSON: {}", e);
        }


        jsonResult2.ifPresentOrElse(
                v -> logger.info("mappingSets as JSON = {}", v),
                () -> logger.error("MappingSets is empty"));

    }
}
