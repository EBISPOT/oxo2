package uk.ac.ebi.spot.oxo.backend.controller.api.sssom;

import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.util.NamedList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.ac.ebi.spot.oxo.backend.service.OxOSolrClient;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StatsController.class)
class StatsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OxOSolrClient solrClient;

    @Test
    void statsAggregatesBothCollections() throws Exception {
        // Mappings query: numFound = nb_mapping, json.facet nb_entity = unique(entity_id).
        SolrDocumentList mappingDocs = new SolrDocumentList();
        mappingDocs.setNumFound(377_000);
        NamedList<Object> jsonFacets = new NamedList<>();
        jsonFacets.add("count", 377_000L);
        jsonFacets.add("nb_entity", 42_000L);
        NamedList<Object> mappingResponseBody = new NamedList<>();
        mappingResponseBody.add("facets", jsonFacets);
        QueryResponse mappingsResponse = mock(QueryResponse.class);
        when(mappingsResponse.getResults()).thenReturn(mappingDocs);
        when(mappingsResponse.getResponse()).thenReturn(mappingResponseBody);
        when(solrClient.queryMappings(any())).thenReturn(mappingsResponse);

        // Mapping-sets query: numFound = nb_mapping_set, provider facet buckets = nb_mapping_provider.
        SolrDocumentList setDocs = new SolrDocumentList();
        setDocs.setNumFound(12);
        FacetField providerFacet = new FacetField("mapping_provider");
        providerFacet.add("https://www.ebi.ac.uk/ols", 7);
        providerFacet.add("https://monarchinitiative.org", 5);
        QueryResponse setsResponse = mock(QueryResponse.class);
        when(setsResponse.getResults()).thenReturn(setDocs);
        when(setsResponse.getFacetField("mapping_provider")).thenReturn(providerFacet);
        when(solrClient.queryMappingSets(any())).thenReturn(setsResponse);

        mockMvc.perform(get("/api/sssom/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nb_mapping").value(377_000))
                .andExpect(jsonPath("$.nb_entity").value(42_000))
                .andExpect(jsonPath("$.nb_mapping_set").value(12))
                .andExpect(jsonPath("$.nb_mapping_provider").value(2));
    }
}
