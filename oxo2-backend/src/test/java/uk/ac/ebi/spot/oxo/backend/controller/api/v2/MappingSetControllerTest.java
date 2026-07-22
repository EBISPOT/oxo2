package uk.ac.ebi.spot.oxo.backend.controller.api.v2;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.SolrParams;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.ac.ebi.spot.oxo.backend.service.OxOSolrClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the mapping-set curation category (ADR-0027) and the ontology prefix/name promoted from the
 * OLS {@code other} block (ADR-0038) now surfaced on {@code GET /api/v2/mapping-sets}. These fields
 * drive the frontend's split of the picker into a curated table and an ontologies table.
 */
@WebMvcTest(MappingSetController.class)
class MappingSetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OxOSolrClient solrClient;

    private static QueryResponse responseOf(SolrDocument... documents) {
        SolrDocumentList docs = new SolrDocumentList();
        for (SolrDocument doc : documents) {
            docs.add(doc);
        }
        docs.setNumFound(documents.length);
        QueryResponse response = mock(QueryResponse.class);
        when(response.getResults()).thenReturn(docs);
        return response;
    }

    private static SolrDocument ontologySet() {
        SolrDocument doc = new SolrDocument();
        doc.setField("mapping_set_id", "https://w3id.org/commons/ols/mappings/addicto.ols.sssom.tsv");
        doc.setField("mapping_set_title", "OLS extracted ADDICTO mappings");
        doc.setField("inference_type", "ASSERTED");
        doc.setField("mapping_set_category", "ONTOLOGY");
        doc.setField("prefix", "ADDICTO");
        doc.setField("ontology", "Addiction Ontology (ADDICTO)");
        return doc;
    }

    private static SolrDocument curatedSet() {
        SolrDocument doc = new SolrDocument();
        doc.setField("mapping_set_id", "ictv_to_ncbitaxon");
        doc.setField("mapping_set_title", "ICTV to NCBITaxon exact lexical mappings");
        doc.setField("inference_type", "ASSERTED");
        doc.setField("mapping_set_category", "CURATED");
        // A curated set carries no `other` ontology block, so no prefix / ontology.
        return doc;
    }

    @Test
    void listExposesCategoryPrefixAndOntologyForOntologySets() throws Exception {
        QueryResponse response = responseOf(ontologySet());
        when(solrClient.queryMappingSets(any())).thenReturn(response);

        mockMvc.perform(get("/api/v2/mapping-sets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].mapping_set_category").value("ONTOLOGY"))
                .andExpect(jsonPath("$[0].prefix").value("ADDICTO"))
                .andExpect(jsonPath("$[0].ontology").value("Addiction Ontology (ADDICTO)"));
    }

    @Test
    void listReportsCategoryButNoOntologyColumnsForCuratedSets() throws Exception {
        QueryResponse response = responseOf(curatedSet());
        when(solrClient.queryMappingSets(any())).thenReturn(response);

        mockMvc.perform(get("/api/v2/mapping-sets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].mapping_set_category").value("CURATED"))
                .andExpect(jsonPath("$[0].prefix").doesNotExist())
                .andExpect(jsonPath("$[0].ontology").doesNotExist());
    }

    @Test
    void listRequestsTheNewFieldsFromSolr() throws Exception {
        QueryResponse response = responseOf(ontologySet());
        when(solrClient.queryMappingSets(any())).thenReturn(response);

        mockMvc.perform(get("/api/v2/mapping-sets")).andExpect(status().isOk());

        ArgumentCaptor<SolrParams> captor = ArgumentCaptor.forClass(SolrParams.class);
        verify(solrClient).queryMappingSets(captor.capture());
        SolrQuery solrQuery = (SolrQuery) captor.getValue();
        assertThat(solrQuery.getFields()).contains("mapping_set_category", "prefix", "ontology");
    }
}
