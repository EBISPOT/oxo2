package uk.ac.ebi.spot.oxo.backend.controller.api.v2;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.SolrParams;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.ac.ebi.spot.oxo.backend.service.OxOSolrClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The deep health check (ADR-0051). Mirrors the {@code MappingControllerTest} conventions:
 * {@code @WebMvcTest} with a mocked {@link OxOSolrClient}, and an {@code ArgumentCaptor<SolrParams>}
 * to assert on the queries the controller builds.
 *
 * <p>The one behaviour worth guarding hardest: the traffic manager regex-matches
 * {@link HealthController#STATUS_OPERATIONAL} against the body, so the sentence must appear verbatim
 * on a healthy response and must never leak into an unhealthy one.
 */
@WebMvcTest(HealthController.class)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OxOSolrClient solrClient;

    private static QueryResponse coreWithDocuments(long numFound) {
        SolrDocumentList documents = new SolrDocumentList();
        documents.setNumFound(numFound);
        QueryResponse response = mock(QueryResponse.class);
        when(response.getResults()).thenReturn(documents);
        return response;
    }

    /**
     * Builds each response before its {@code when(solrClient...)} call: creating the response mock
     * inside {@code thenReturn(...)} would nest a stubbing inside an unfinished one, which Mockito
     * rejects.
     */
    private void stubCores(QueryResponse mappings, QueryResponse mappingSets, QueryResponse entities)
            throws Exception {
        when(solrClient.queryMappings(any())).thenReturn(mappings);
        when(solrClient.queryMappingSets(any())).thenReturn(mappingSets);
        when(solrClient.queryEntities(any())).thenReturn(entities);
    }

    @Test
    void allCoresPopulatedAnswersOperational() throws Exception {
        stubCores(coreWithDocuments(28_400_000L), coreWithDocuments(288L),
                coreWithDocuments(5_100_000L));

        mockMvc.perform(get(HealthController.PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(HealthController.STATUS_OPERATIONAL))
                .andExpect(jsonPath("$.cores['oxo2-mappings'].ok").value(true))
                .andExpect(jsonPath("$.cores['oxo2-mappings'].documents").value(28_400_000L))
                .andExpect(jsonPath("$.cores['oxo2-mappingsets'].documents").value(288L))
                .andExpect(jsonPath("$.cores['oxo2-entities'].documents").value(5_100_000L));

        // The check must stay cheap: numFound only, no rows fetched.
        ArgumentCaptor<SolrParams> queryCaptor = ArgumentCaptor.forClass(SolrParams.class);
        verify(solrClient).queryMappings(queryCaptor.capture());
        SolrQuery countQuery = (SolrQuery) queryCaptor.getValue();
        assertThat(countQuery.getQuery()).isEqualTo("*:*");
        assertThat(countQuery.getRows()).isZero();
    }

    @Test
    void emptyCoreAnswers503WithoutTheOperationalSentence() throws Exception {
        stubCores(coreWithDocuments(28_400_000L), coreWithDocuments(288L), coreWithDocuments(0L));

        String body = mockMvc.perform(get(HealthController.PATH))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(HealthController.STATUS_UNAVAILABLE))
                .andExpect(jsonPath("$.cores['oxo2-entities'].ok").value(false))
                .andExpect(jsonPath("$.cores['oxo2-entities'].documents").value(0L))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(HealthController.STATUS_OPERATIONAL);
    }

    @Test
    void unreachableCoreAnswers503AndStillReportsTheOthers() throws Exception {
        QueryResponse mappingSets = coreWithDocuments(288L);
        QueryResponse entities = coreWithDocuments(5_100_000L);
        when(solrClient.queryMappings(any())).thenThrow(new IOException("Connection refused"));
        when(solrClient.queryMappingSets(any())).thenReturn(mappingSets);
        when(solrClient.queryEntities(any())).thenReturn(entities);

        String body = mockMvc.perform(get(HealthController.PATH))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.cores['oxo2-mappings'].ok").value(false))
                .andExpect(jsonPath("$.cores['oxo2-mappings'].error").value(
                        "java.io.IOException: Connection refused"))
                .andExpect(jsonPath("$.cores['oxo2-mappingsets'].ok").value(true))
                .andExpect(jsonPath("$.cores['oxo2-entities'].ok").value(true))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(HealthController.STATUS_OPERATIONAL);
    }
}
