package uk.ac.ebi.spot.oxo.backend.controller.api.v2;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.SolrParams;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
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
        assertThat(solrQuery.getFields()).contains("mapping_set_category", "prefix", "ontology",
                "issue_tracker", "mailing_list");
    }

    @Test
    void exposesReportingContactsOnListAndDetails() throws Exception {
        SolrDocument document = ontologySet();
        document.setField("issue_tracker", "https://github.com/example/ontology/issues");
        document.setField("mailing_list", "users@example.org");
        QueryResponse response = responseOf(document);
        when(solrClient.queryMappingSets(any())).thenReturn(response);

        mockMvc.perform(get("/api/v2/mapping-sets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].issue_tracker").value("https://github.com/example/ontology/issues"))
                .andExpect(jsonPath("$[0].mailing_list").value("users@example.org"));
        mockMvc.perform(get("/api/v2/mapping-sets/by-id")
                        .param("mappingSetId", document.getFieldValue("mapping_set_id").toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issue_tracker").value("https://github.com/example/ontology/issues"))
                .andExpect(jsonPath("$.mailing_list").value("users@example.org"));

        ArgumentCaptor<SolrParams> captor = ArgumentCaptor.forClass(SolrParams.class);
        verify(solrClient, org.mockito.Mockito.times(2)).queryMappingSets(captor.capture());
        for (SolrParams query : captor.getAllValues()) {
            assertThat(query.get("fl")).contains("issue_tracker", "mailing_list");
        }
    }

    @Test
    void detailsExposeMetadataWithoutCurieMapOrSolrInternals() throws Exception {
        SolrDocument doc = curatedSet();
        doc.setField("mapping_set_version", "0.6.1-dev-9e9cd532");
        doc.setField("mapping_set_confidence", 0.0);
        doc.setField("license", "https://creativecommons.org/publicdomain/zero/1.0/");
        doc.setField("publisher", java.util.List.of("Example publisher"));
        doc.setField("creator_id", java.util.List.of("ORCID:0000-0003-4423-4370"));
        doc.setField("publication_date", java.util.Date.from(java.time.Instant.parse("2026-11-05T00:00:00Z")));
        doc.setField("curie_map", "orcid:https://orcid.org/");
        doc.setField("_version_", 123);
        doc.setField("mapping_set_title_str", "Internal sort value");
        QueryResponse response = responseOf(doc);
        when(solrClient.queryMappingSets(any())).thenReturn(response);

        mockMvc.perform(get("/api/v2/mapping-sets/by-id").param("mappingSetId", "ictv_to_ncbitaxon"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mapping_set_confidence").value(0.0))
                .andExpect(jsonPath("$.mapping_set_version").value("0.6.1-dev-9e9cd532"))
                .andExpect(jsonPath("$.license").value("https://creativecommons.org/publicdomain/zero/1.0/"))
                .andExpect(jsonPath("$.publisher[0]").value("Example publisher"))
                .andExpect(jsonPath("$.creator_id[0]").value("ORCID:0000-0003-4423-4370"))
                .andExpect(jsonPath("$.publication_date").value("2026-11-05T00:00:00Z"))
                .andExpect(jsonPath("$.curie_map").doesNotExist())
                .andExpect(jsonPath("$._version_").doesNotExist())
                .andExpect(jsonPath("$.mapping_set_title_str").doesNotExist());
        ArgumentCaptor<SolrParams> query = ArgumentCaptor.forClass(SolrParams.class);
        verify(solrClient).queryMappingSets(query.capture());
        assertThat(query.getValue().get("fl")).contains("mapping_set_confidence", "publisher", "license", "creator_id")
                .doesNotContain("curie_map", "_version_");
    }

    @Test
    void missingContactsRemainAbsentOnOlderIndexedSets() throws Exception {
        QueryResponse response = responseOf(curatedSet());
        when(solrClient.queryMappingSets(any())).thenReturn(response);
        mockMvc.perform(get("/api/v2/mapping-sets/by-id").param("mappingSetId", "ictv_to_ncbitaxon"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issue_tracker").doesNotExist())
                .andExpect(jsonPath("$.mailing_list").doesNotExist());
    }
}
