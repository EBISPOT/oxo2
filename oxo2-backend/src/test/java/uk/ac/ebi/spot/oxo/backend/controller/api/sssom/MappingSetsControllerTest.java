package uk.ac.ebi.spot.oxo.backend.controller.api.sssom;

import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
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

@WebMvcTest(MappingSetsController.class)
class MappingSetsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OxOSolrClient solrClient;

    // ---------- GET /api/sssom/mapping_sets ----------

    @Test
    void listReturnsCleanedSetDocumentsInEnvelope() throws Exception {
        SolrDocument doc = new SolrDocument();
        doc.setField("mapping_set_id", "https://example.org/set");
        doc.setField("mapping_set_title", "Example set");
        doc.setField("license", "https://creativecommons.org/publicdomain/zero/1.0/");
        // Solr-internal fields that must NOT surface in the response:
        doc.setField("id", "https://example.org/set");
        doc.setField("_version_", 1837L);
        doc.setField("mapping_set_title_str", "Example set");

        SolrDocumentList docs = new SolrDocumentList();
        docs.add(doc);
        docs.setNumFound(1);
        QueryResponse response = mock(QueryResponse.class);
        when(response.getResults()).thenReturn(docs);
        when(solrClient.queryMappingSets(any())).thenReturn(response);

        mockMvc.perform(get("/api/sssom/mapping_sets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].mapping_set_id").value("https://example.org/set"))
                .andExpect(jsonPath("$.data[0].mapping_set_title").value("Example set"))
                .andExpect(jsonPath("$.data[0].license").exists())
                .andExpect(jsonPath("$.data[0]._version_").doesNotExist())
                .andExpect(jsonPath("$.data[0].mapping_set_title_str").doesNotExist())
                .andExpect(jsonPath("$.data[0].id").doesNotExist())
                // mapping-set listings carry no facets block
                .andExpect(jsonPath("$.facets").doesNotExist())
                .andExpect(jsonPath("$.pagination.page_number").value(1));
    }

    @Test
    void listReturns400WhenFilteringOnMappingOnlyField() throws Exception {
        // subject_id is a mapping slot, not a mapping-set slot.
        mockMvc.perform(get("/api/sssom/mapping_sets").param("filter", "subject_id|eq|x"))
                .andExpect(status().isBadRequest());
    }
}
