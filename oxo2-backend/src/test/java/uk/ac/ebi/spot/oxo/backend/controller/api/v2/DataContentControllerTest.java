package uk.ac.ebi.spot.oxo.backend.controller.api.v2;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.FacetField;
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

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The landing page's Data Content summary (ADR-0043). Mirrors the {@code MappingControllerTest}
 * conventions: {@code @WebMvcTest} with a mocked {@link OxOSolrClient}, and an
 * {@code ArgumentCaptor<SolrParams>} to assert on the queries the controller builds.
 *
 * <p>The controller issues two queries against the mapping-set collection — the release-date read first,
 * then the category facet — so the mapping-set stub returns them in that order.
 */
@WebMvcTest(DataContentController.class)
class DataContentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OxOSolrClient solrClient;

    /** Mappings collection: numFound is the total, the inference_type facet is the origin split. */
    private void stubMappings(long total, long asserted, long inferred) throws Exception {
        SolrDocumentList documents = new SolrDocumentList();
        documents.setNumFound(total);
        FacetField inferenceTypeFacet = new FacetField("inference_type");
        inferenceTypeFacet.add("ASSERTED", asserted);
        inferenceTypeFacet.add("SSSOM_INFERENCE", inferred);
        QueryResponse response = mock(QueryResponse.class);
        when(response.getResults()).thenReturn(documents);
        when(response.getFacetField("inference_type")).thenReturn(inferenceTypeFacet);
        when(solrClient.queryMappings(any())).thenReturn(response);
    }

    /** Mapping-set collection: the release-date read, then the numFound + category facet. */
    private void stubMappingSets(String releaseDate, long total, long curated, long ontologies)
            throws Exception {
        SolrDocumentList releaseDocuments = new SolrDocumentList();
        if (releaseDate != null) {
            SolrDocument document = new SolrDocument();
            // A pdate field comes back from SolrJ as a java.util.Date, not a String.
            document.setField("data_release_date", Date.from(Instant.parse(releaseDate)));
            releaseDocuments.add(document);
            releaseDocuments.setNumFound(1);
        }
        QueryResponse releaseResponse = mock(QueryResponse.class);
        when(releaseResponse.getResults()).thenReturn(releaseDocuments);

        SolrDocumentList setDocuments = new SolrDocumentList();
        setDocuments.setNumFound(total);
        FacetField categoryFacet = new FacetField("mapping_set_category");
        categoryFacet.add("CURATED", curated);
        categoryFacet.add("ONTOLOGY", ontologies);
        QueryResponse setsResponse = mock(QueryResponse.class);
        when(setsResponse.getResults()).thenReturn(setDocuments);
        when(setsResponse.getFacetField("mapping_set_category")).thenReturn(categoryFacet);

        when(solrClient.queryMappingSets(any())).thenReturn(releaseResponse, setsResponse);
    }

    @Test
    void reportsReleaseDateAndBothCountBreakdowns() throws Exception {
        stubMappings(28_400_000L, 25_000_000L, 3_400_000L);
        stubMappingSets("2026-07-30T09:15:00Z", 1_200L, 5L, 1_195L);

        mockMvc.perform(get("/api/v2/data-content"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.releaseDate").value("2026-07-30T09:15:00Z"))
                .andExpect(jsonPath("$.mappings.total").value(28_400_000L))
                .andExpect(jsonPath("$.mappings.asserted").value(25_000_000L))
                .andExpect(jsonPath("$.mappings.inferred").value(3_400_000L))
                .andExpect(jsonPath("$.mappingSets.total").value(1_200L))
                .andExpect(jsonPath("$.mappingSets.curated").value(5L))
                .andExpect(jsonPath("$.mappingSets.ontologies").value(1_195L));
    }

    @Test
    void reportsNullReleaseDateWhenNoMappingSetCarriesOne() throws Exception {
        // Data indexed before the release-date field existed. The counts must still be served — a
        // missing release date is not an error, and the landing page renders the rest.
        stubMappings(186_000L, 184_159L, 1_841L);
        stubMappingSets(null, 3L, 3L, 0L);

        mockMvc.perform(get("/api/v2/data-content"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.releaseDate").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.mappings.total").value(186_000L))
                .andExpect(jsonPath("$.mappingSets.total").value(3L))
                .andExpect(jsonPath("$.mappingSets.curated").value(3L))
                .andExpect(jsonPath("$.mappingSets.ontologies").value(0L));
    }

    @Test
    void excludesTheSyntheticInferencesSetFromTheMappingSetCounts() throws Exception {
        stubMappings(10L, 8L, 2L);
        stubMappingSets("2026-07-30T09:15:00Z", 2L, 1L, 1L);

        mockMvc.perform(get("/api/v2/data-content")).andExpect(status().isOk());

        ArgumentCaptor<SolrParams> captor = ArgumentCaptor.forClass(SolrParams.class);
        verify(solrClient, org.mockito.Mockito.times(2)).queryMappingSets(captor.capture());
        List<SolrParams> mappingSetQueries = captor.getAllValues();

        // Second call is the category facet — the one whose numFound becomes mappingSets.total.
        String[] filterQueries = mappingSetQueries.get(1).getParams("fq");
        assertThat(filterQueries)
                .as("the mapping-set counts must exclude the synthetic SSSOM_INFERENCE set")
                .containsExactly("inference_type:ASSERTED");
    }

    @Test
    void readsTheNewestReleaseDateAndOnlyFromSetsThatHaveOne() throws Exception {
        stubMappings(10L, 8L, 2L);
        stubMappingSets("2026-07-30T09:15:00Z", 2L, 1L, 1L);

        mockMvc.perform(get("/api/v2/data-content")).andExpect(status().isOk());

        ArgumentCaptor<SolrParams> captor = ArgumentCaptor.forClass(SolrParams.class);
        verify(solrClient, org.mockito.Mockito.times(2)).queryMappingSets(captor.capture());
        SolrParams releaseDateQuery = captor.getAllValues().get(0);

        // The existence filter is what makes the descending sort unambiguous: with no missing values in
        // the result set, the first document is the newest release regardless of missing-value ordering.
        assertThat(releaseDateQuery.getParams("fq"))
                .containsExactly("data_release_date:[* TO *]");
        assertThat(releaseDateQuery.get("sort")).isEqualTo("data_release_date desc");
        assertThat(releaseDateQuery.getInt("rows")).isEqualTo(1);
    }

    @Test
    void doesNotUseAUniqueAggregationOnTheMappingsCollection() throws Exception {
        // A full-collection unique() facet over the mappings collection has crash-killed Solr on the
        // real corpus. The landing page must never be the thing that does it.
        stubMappings(28_400_000L, 25_000_000L, 3_400_000L);
        stubMappingSets("2026-07-30T09:15:00Z", 1_200L, 5L, 1_195L);

        mockMvc.perform(get("/api/v2/data-content")).andExpect(status().isOk());

        ArgumentCaptor<SolrParams> captor = ArgumentCaptor.forClass(SolrParams.class);
        verify(solrClient).queryMappings(captor.capture());
        SolrParams mappingsQuery = captor.getValue();

        assertThat(mappingsQuery.get("json.facet"))
                .as("no json.facet aggregation belongs on the full mappings collection")
                .isNull();
        assertThat(mappingsQuery.getInt("rows")).isEqualTo(0);
        assertThat(mappingsQuery.getParams("facet.field")).containsExactly("inference_type");
    }

    @Test
    void returns500WhenSolrFails() throws Exception {
        when(solrClient.queryMappings(any())).thenThrow(new RuntimeException("Solr is down"));

        mockMvc.perform(get("/api/v2/data-content"))
                .andExpect(status().isInternalServerError());
    }

    /** Guards the SolrQuery-shaped assumption that the sort is a single descending clause. */
    @Test
    void releaseDateQueryRequestsOnlyTheReleaseDateField() throws Exception {
        stubMappings(10L, 8L, 2L);
        stubMappingSets("2026-07-30T09:15:00Z", 2L, 1L, 1L);

        mockMvc.perform(get("/api/v2/data-content")).andExpect(status().isOk());

        ArgumentCaptor<SolrParams> captor = ArgumentCaptor.forClass(SolrParams.class);
        verify(solrClient, org.mockito.Mockito.times(2)).queryMappingSets(captor.capture());
        SolrQuery releaseDateQuery = new SolrQuery();
        captor.getAllValues().get(0).forEach(entry ->
                releaseDateQuery.set(entry.getKey(), entry.getValue()));

        assertThat(releaseDateQuery.getFields()).isEqualTo("data_release_date");
    }
}
