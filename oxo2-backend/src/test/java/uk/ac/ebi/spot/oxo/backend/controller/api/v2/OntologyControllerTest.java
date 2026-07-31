package uk.ac.ebi.spot.oxo.backend.controller.api.v2;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
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

import java.util.LinkedHashMap;
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

        mockMvc.perform(get("/api/v2/ontologies"))
                .andExpect(status().isOk())
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
