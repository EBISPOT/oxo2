package uk.ac.ebi.spot.oxo.backend.controller.api.v2;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.params.SolrParams;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.response.MappingSearchResponse;
import uk.ac.ebi.spot.oxo.backend.service.OxOSolrClient;
import uk.ac.ebi.spot.oxo.model.sssom.Mapping;
import uk.ac.ebi.spot.oxo.model.sssom.MappingEnum;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MappingController.class)
class MappingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OxOSolrClient solrClient;

    private static MappingSearchResponse emptyResponse() {
        Page<Mapping> emptyPage = new PageImpl<>(Collections.emptyList());
        return new MappingSearchResponse(emptyPage);
    }

    private SolrQuery captureQuery() throws Exception {
        ArgumentCaptor<SolrParams> captor = ArgumentCaptor.forClass(SolrParams.class);
        verify(solrClient).query(captor.capture(), any(Pageable.class));
        SolrParams captured = captor.getValue();
        assertThat(captured).isInstanceOf(SolrQuery.class);
        return (SolrQuery) captured;
    }

    // ---------- GET /api/v2/mappings/{subjectId} ----------

    @Test
    void getMappingsByIdEscapesInjectionPayload() throws Exception {
        when(solrClient.query(any(SolrParams.class), any(Pageable.class)))
                .thenReturn(emptyResponse());

        String rawPayload = "a\" OR *:* OR subject_id:\"a";
        String encoded = "a%22%20OR%20*:*%20OR%20subject_id:%22a";

        mockMvc.perform(get("/api/v2/mappings/" + encoded))
                .andExpect(status().isOk());

        SolrQuery solrQuery = captureQuery();
        String expected = MappingEnum.SUBJECT_ID.getField()
                + ":\"" + ClientUtils.escapeQueryChars(rawPayload) + "\"";
        assertThat(solrQuery.getQuery()).isEqualTo(expected);
        assertThat(solrQuery.getQuery()).doesNotContain("\"" + rawPayload + "\"");
    }

    @Test
    void getMappingsByIdRoundTripsCurie() throws Exception {
        when(solrClient.query(any(SolrParams.class), any(Pageable.class)))
                .thenReturn(emptyResponse());

        mockMvc.perform(get("/api/v2/mappings/DOID:0014667"))
                .andExpect(status().isOk());

        SolrQuery solrQuery = captureQuery();
        String expected = MappingEnum.SUBJECT_ID.getField()
                + ":\"" + ClientUtils.escapeQueryChars("DOID:0014667") + "\"";
        assertThat(solrQuery.getQuery()).isEqualTo(expected);
    }

    @Test
    void getMappingsByIdReturns400OnNegativePage() throws Exception {
        mockMvc.perform(get("/api/v2/mappings/DOID:1").param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getMappingsByIdReturns400OnZeroSize() throws Exception {
        mockMvc.perform(get("/api/v2/mappings/DOID:1").param("size", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getMappingsByIdReturns400OnOversizeSize() throws Exception {
        mockMvc.perform(get("/api/v2/mappings/DOID:1").param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getMappingsByIdReturns500WhenSolrClientThrows() throws Exception {
        when(solrClient.query(any(SolrParams.class), any(Pageable.class)))
                .thenThrow(new RuntimeException("solr down"));

        mockMvc.perform(get("/api/v2/mappings/DOID:1"))
                .andExpect(status().isInternalServerError());
    }

    // ---------- POST /api/v2/mappings/search ----------

    @Test
    void postSearchDeserializesRequestAndDelegatesToBuilder() throws Exception {
        when(solrClient.query(any(SolrParams.class), any(Pageable.class)))
                .thenReturn(emptyResponse());

        String body = """
                {
                  "queries": ["UBERON:0000948"],
                  "queryFields": ["subject_id"],
                  "columnFilters": [],
                  "page": 0,
                  "size": 10
                }
                """;

        mockMvc.perform(post("/api/v2/mappings/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        SolrQuery solrQuery = captureQuery();
        String escapedTerm = ClientUtils.escapeQueryChars("UBERON:0000948");
        assertThat(solrQuery.getQuery()).isEqualTo(escapedTerm);
        assertThat(solrQuery.getParams("qf"))
                .containsExactly(MappingEnum.SUBJECT_ID.getField());
        assertThat(solrQuery.get("defType")).isEqualTo("edismax");
        assertThat(solrQuery.getStart()).isZero();
        assertThat(solrQuery.getRows()).isEqualTo(10);
    }

    @Test
    void postSearchReturns400OnOversizeSize() throws Exception {
        String body = """
                {
                  "queries": ["UBERON:0000948"],
                  "queryFields": ["subject_id"],
                  "columnFilters": [],
                  "page": 0,
                  "size": 101
                }
                """;

        mockMvc.perform(post("/api/v2/mappings/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ---------- GET /api/v2/mappings?from=&to= (cross-ontology) ----------

    @Test
    void mapOntologiesAppliesPrefixFiltersAndCollapse() throws Exception {
        when(solrClient.query(any(SolrParams.class), any(Pageable.class)))
                .thenReturn(emptyResponse());

        mockMvc.perform(get("/api/v2/mappings").param("from", "DOID").param("to", "EFO,MONDO"))
                .andExpect(status().isOk());

        SolrQuery solrQuery = captureQuery();
        String subjectClause = "(" + MappingEnum.SUBJECT_PREFIX.getField() + ":DOID)";
        String objectClause = "(" + MappingEnum.OBJECT_PREFIX.getField() + ":EFO OR "
                + MappingEnum.OBJECT_PREFIX.getField() + ":MONDO)";
        assertThat(solrQuery.getFilterQueries()).contains(subjectClause, objectClause);
        // groupBySpo defaults true on this endpoint → same-SPO collapse (ExpandComponent) is applied.
        assertThat(solrQuery.get("expand")).isEqualTo("true");
    }

    @Test
    void mapOntologiesReturns400OnOversizeSize() throws Exception {
        mockMvc.perform(get("/api/v2/mappings").param("from", "DOID").param("size", "101"))
                .andExpect(status().isBadRequest());
    }
}
