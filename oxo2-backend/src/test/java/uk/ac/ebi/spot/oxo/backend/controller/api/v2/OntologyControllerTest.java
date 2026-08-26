package uk.ac.ebi.spot.oxo.backend.controller.api.v2;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.ac.ebi.spot.oxo.backend.service.OxOSolrClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OntologyController.class)
class OntologyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OxOSolrClient solrClient;

    /** Build a QueryResponse carrying facet_fields for the given subject/object prefix counts. */
    private static QueryResponse facetResponse(Map<String, Integer> subjectCounts,
                                               Map<String, Integer> objectCounts) {
        NamedList<Object> facetFields = new NamedList<>();
        if (subjectCounts != null) facetFields.add("subject_prefix", toCountList(subjectCounts));
        if (objectCounts != null) facetFields.add("object_prefix", toCountList(objectCounts));
        NamedList<Object> facetCounts = new NamedList<>();
        facetCounts.add("facet_queries", new NamedList<>());
        facetCounts.add("facet_fields", facetFields);
        NamedList<Object> root = new NamedList<>();
        root.add("response", new SolrDocumentList());
        root.add("facet_counts", facetCounts);
        QueryResponse queryResponse = new QueryResponse();
        queryResponse.setResponse(root);
        return queryResponse;
    }

    private static NamedList<Object> toCountList(Map<String, Integer> counts) {
        NamedList<Object> list = new NamedList<>();
        counts.forEach(list::add);
        return list;
    }

    /**
     * A {@code prefix,namespace} pivot over the entity core: for each prefix, its candidate namespaces
     * with the number of entities carrying each.
     */
    private static QueryResponse pivotResponse(Map<String, Map<String, Integer>> namespacesByPrefix) {
        NamedList<Object> pivotsByField = new NamedList<>();
        List<NamedList<Object>> prefixPivots = new ArrayList<>();
        namespacesByPrefix.forEach((prefix, namespaces) -> {
            List<NamedList<Object>> namespacePivots = new ArrayList<>();
            namespaces.forEach((namespace, count) -> {
                NamedList<Object> entry = new NamedList<>();
                entry.add("field", "namespace");
                entry.add("value", namespace);
                entry.add("count", count);
                namespacePivots.add(entry);
            });
            NamedList<Object> prefixEntry = new NamedList<>();
            prefixEntry.add("field", "prefix");
            prefixEntry.add("value", prefix);
            prefixEntry.add("count", namespaces.values().stream().mapToInt(Integer::intValue).sum());
            prefixEntry.add("pivot", namespacePivots);
            prefixPivots.add(prefixEntry);
        });
        pivotsByField.add("prefix,namespace", prefixPivots);

        NamedList<Object> facetCounts = new NamedList<>();
        facetCounts.add("facet_queries", new NamedList<>());
        facetCounts.add("facet_fields", new NamedList<>());
        facetCounts.add("facet_pivot", pivotsByField);
        NamedList<Object> root = new NamedList<>();
        root.add("response", new SolrDocumentList());
        root.add("facet_counts", facetCounts);
        QueryResponse queryResponse = new QueryResponse();
        queryResponse.setResponse(root);
        return queryResponse;
    }

    /** A mapping-set response carrying (prefix, ontology_iri) pairs, as the ontology sets do. */
    private static QueryResponse mappingSetResponse(List<Map.Entry<String, String>> prefixToIri) {
        SolrDocumentList documents = new SolrDocumentList();
        for (Map.Entry<String, String> entry : prefixToIri) {
            SolrDocument document = new SolrDocument();
            document.setField("prefix", entry.getKey());
            document.setField("ontology_iri", entry.getValue());
            documents.add(document);
        }
        documents.setNumFound(documents.size());
        NamedList<Object> root = new NamedList<>();
        root.add("response", documents);
        QueryResponse queryResponse = new QueryResponse();
        queryResponse.setResponse(root);
        return queryResponse;
    }

    private SolrQuery captureQuery() throws Exception {
        ArgumentCaptor<SolrParams> captor = ArgumentCaptor.forClass(SolrParams.class);
        verify(solrClient).queryMappings(captor.capture());
        SolrParams captured = captor.getValue();
        assertThat(captured).isInstanceOf(SolrQuery.class);
        return (SolrQuery) captured;
    }

    // ---------- GET /api/v2/ontologies ----------

    @Test
    void listOntologiesMergesSubjectAndObjectCountsSortedByPrefix() throws Exception {
        Map<String, Integer> subjectCounts = new LinkedHashMap<>();
        subjectCounts.put("DOID", 5);
        subjectCounts.put("EFO", 2);
        Map<String, Integer> objectCounts = new LinkedHashMap<>();
        objectCounts.put("EFO", 3);
        objectCounts.put("MONDO", 1);
        when(solrClient.queryMappings(any(SolrParams.class)))
                .thenReturn(facetResponse(subjectCounts, objectCounts));
        // An index written before ADR-0047 carries neither enrichment.
        when(solrClient.queryEntities(any(SolrParams.class))).thenReturn(pivotResponse(Map.of()));
        when(solrClient.queryMappingSets(any(SolrParams.class)))
                .thenReturn(mappingSetResponse(List.of()));

        mockMvc.perform(get("/api/v2/ontologies"))
                .andExpect(status().isOk())
                // Neither field is serialised as null — absent means "unknown".
                .andExpect(jsonPath("$[0].namespace").doesNotExist())
                .andExpect(jsonPath("$[0].uri").doesNotExist())
                // sorted by prefix: DOID, EFO, MONDO
                .andExpect(jsonPath("$[0].prefix").value("DOID"))
                .andExpect(jsonPath("$[0].asSubject").value(5))
                .andExpect(jsonPath("$[0].asObject").value(0))
                .andExpect(jsonPath("$[1].prefix").value("EFO"))
                .andExpect(jsonPath("$[1].asSubject").value(2))
                .andExpect(jsonPath("$[1].asObject").value(3))
                .andExpect(jsonPath("$[2].prefix").value("MONDO"))
                .andExpect(jsonPath("$[2].asSubject").value(0))
                .andExpect(jsonPath("$[2].asObject").value(1));

        SolrQuery solrQuery = captureQuery();
        assertThat(solrQuery.getFacetFields()).contains("subject_prefix", "object_prefix");
        assertThat(solrQuery.getRows()).isZero();
        assertThat(solrQuery.getBool("facet")).isTrue();
    }

    // ---------- ADR-0047: namespace and uri ----------

    @Test
    void namespaceComesFromTheEntityIndexAndUriFromTheOntologySets() throws Exception {
        Map<String, Integer> subjectCounts = new LinkedHashMap<>();
        subjectCounts.put("MONDO", 5);
        when(solrClient.queryMappings(any(SolrParams.class)))
                .thenReturn(facetResponse(subjectCounts, null));
        when(solrClient.queryEntities(any(SolrParams.class))).thenReturn(pivotResponse(
                Map.of("MONDO", Map.of("http://purl.obolibrary.org/obo/MONDO_", 12))));
        when(solrClient.queryMappingSets(any(SolrParams.class))).thenReturn(mappingSetResponse(
                List.of(Map.entry("MONDO", "http://purl.obolibrary.org/obo/mondo.owl"))));

        mockMvc.perform(get("/api/v2/ontologies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].prefix").value("MONDO"))
                .andExpect(jsonPath("$[0].namespace").value("http://purl.obolibrary.org/obo/MONDO_"))
                .andExpect(jsonPath("$[0].uri").value("http://purl.obolibrary.org/obo/mondo.owl"));
    }

    /**
     * The listing's prefixes are upper-cased; a set's prefix keeps the producer's casing. An exact
     * join would drop the largest ontologies in the corpus (NCBITaxon, HGNC, mesh) and still look
     * like it worked.
     */
    @Test
    void joinsOntologyIriCaseInsensitively() throws Exception {
        Map<String, Integer> subjectCounts = new LinkedHashMap<>();
        subjectCounts.put("NCBITAXON", 9);
        when(solrClient.queryMappings(any(SolrParams.class)))
                .thenReturn(facetResponse(subjectCounts, null));
        when(solrClient.queryEntities(any(SolrParams.class))).thenReturn(pivotResponse(Map.of()));
        when(solrClient.queryMappingSets(any(SolrParams.class))).thenReturn(mappingSetResponse(
                List.of(Map.entry("NCBITaxon", "http://purl.obolibrary.org/obo/ncbitaxon.owl"))));

        mockMvc.perform(get("/api/v2/ontologies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].prefix").value("NCBITAXON"))
                .andExpect(jsonPath("$[0].uri")
                        .value("http://purl.obolibrary.org/obo/ncbitaxon.owl"));
    }

    /** An ontology and its obsolete-terms companion share a prefix; the entry must not double. */
    @Test
    void collapsesTheOntologyAndItsObsoleteCompanionToOneEntry() throws Exception {
        Map<String, Integer> subjectCounts = new LinkedHashMap<>();
        subjectCounts.put("EFO", 4);
        when(solrClient.queryMappings(any(SolrParams.class)))
                .thenReturn(facetResponse(subjectCounts, null));
        when(solrClient.queryEntities(any(SolrParams.class))).thenReturn(pivotResponse(Map.of()));
        when(solrClient.queryMappingSets(any(SolrParams.class))).thenReturn(mappingSetResponse(
                List.of(Map.entry("EFO", "http://www.ebi.ac.uk/efo/efo.owl"),
                        Map.entry("EFO", "http://www.ebi.ac.uk/efo/efo.owl"))));

        mockMvc.perform(get("/api/v2/ontologies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].uri").value("http://www.ebi.ac.uk/efo/efo.owl"));
    }

    /** Where a prefix carries several stems, the one most entities use wins. */
    @Test
    void picksTheMostFrequentNamespaceWhenAPrefixHasSeveral() throws Exception {
        Map<String, Integer> subjectCounts = new LinkedHashMap<>();
        subjectCounts.put("PR", 3);
        when(solrClient.queryMappings(any(SolrParams.class)))
                .thenReturn(facetResponse(subjectCounts, null));
        Map<String, Integer> stems = new LinkedHashMap<>();
        stems.put("https://purl.obolibrary.org/obo/PR_", 2);
        stems.put("http://purl.obolibrary.org/obo/PR_", 40);
        when(solrClient.queryEntities(any(SolrParams.class)))
                .thenReturn(pivotResponse(Map.of("PR", stems)));
        when(solrClient.queryMappingSets(any(SolrParams.class)))
                .thenReturn(mappingSetResponse(List.of()));

        mockMvc.perform(get("/api/v2/ontologies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].namespace").value("http://purl.obolibrary.org/obo/PR_"));
    }

    /**
     * A prefix that names no ontology — a registry prefix, or an artefact like {@code ATC_CODE} —
     * still gets a namespace, and the absence of {@code uri} is what marks it as not an ontology.
     */
    @Test
    void omitsUriForAPrefixThatNamesNoOntology() throws Exception {
        Map<String, Integer> subjectCounts = new LinkedHashMap<>();
        subjectCounts.put("ABEROWL", 7);
        when(solrClient.queryMappings(any(SolrParams.class)))
                .thenReturn(facetResponse(subjectCounts, null));
        when(solrClient.queryEntities(any(SolrParams.class))).thenReturn(pivotResponse(
                Map.of("ABEROWL", Map.of("http://aber-owl.net/ontology/", 3))));
        when(solrClient.queryMappingSets(any(SolrParams.class))).thenReturn(mappingSetResponse(
                List.of(Map.entry("MONDO", "http://purl.obolibrary.org/obo/mondo.owl"))));

        mockMvc.perform(get("/api/v2/ontologies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].namespace").value("http://aber-owl.net/ontology/"))
                .andExpect(jsonPath("$[0].uri").doesNotExist());
    }

    /** The counts are the payload; a failing enrichment must not take the whole listing down. */
    @Test
    void stillServesCountsWhenEnrichmentFails() throws Exception {
        Map<String, Integer> subjectCounts = new LinkedHashMap<>();
        subjectCounts.put("DOID", 5);
        when(solrClient.queryMappings(any(SolrParams.class)))
                .thenReturn(facetResponse(subjectCounts, null));
        when(solrClient.queryEntities(any(SolrParams.class)))
                .thenThrow(new RuntimeException("entity core down"));
        when(solrClient.queryMappingSets(any(SolrParams.class)))
                .thenThrow(new RuntimeException("mapping set core down"));

        mockMvc.perform(get("/api/v2/ontologies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].prefix").value("DOID"))
                .andExpect(jsonPath("$[0].asSubject").value(5))
                .andExpect(jsonPath("$[0].namespace").doesNotExist())
                .andExpect(jsonPath("$[0].uri").doesNotExist());
    }

    @Test
    void forSubjectReturnsTargetsWithCounts() throws Exception {
        Map<String, Integer> objectCounts = new LinkedHashMap<>();
        objectCounts.put("MONDO", 1);
        objectCounts.put("EFO", 3);
        when(solrClient.queryMappings(any(SolrParams.class)))
                .thenReturn(facetResponse(null, objectCounts));

        mockMvc.perform(get("/api/v2/ontologies").param("forSubject", "DOID"))
                .andExpect(status().isOk())
                // sorted by prefix: EFO, MONDO
                .andExpect(jsonPath("$[0].prefix").value("EFO"))
                .andExpect(jsonPath("$[0].count").value(3))
                .andExpect(jsonPath("$[1].prefix").value("MONDO"))
                .andExpect(jsonPath("$[1].count").value(1));

        SolrQuery solrQuery = captureQuery();
        assertThat(solrQuery.getFilterQueries()).contains("subject_prefix:DOID");
        assertThat(solrQuery.getFacetFields()).containsExactly("object_prefix");
    }

    @Test
    void forSubjectEscapesInjectionPayload() throws Exception {
        when(solrClient.queryMappings(any(SolrParams.class)))
                .thenReturn(facetResponse(null, new LinkedHashMap<>()));

        String rawPayload = "a:b OR *:*";
        mockMvc.perform(get("/api/v2/ontologies").param("forSubject", rawPayload))
                .andExpect(status().isOk());

        SolrQuery solrQuery = captureQuery();
        String expected = "subject_prefix:" + ClientUtils.escapeQueryChars(rawPayload);
        assertThat(solrQuery.getFilterQueries()).contains(expected);
    }

    @Test
    void returns500WhenSolrClientThrows() throws Exception {
        when(solrClient.queryMappings(any(SolrParams.class)))
                .thenThrow(new RuntimeException("solr down"));

        mockMvc.perform(get("/api/v2/ontologies"))
                .andExpect(status().isInternalServerError());
    }
}
