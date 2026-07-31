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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.response.MappingSearchResponse;
import uk.ac.ebi.spot.oxo.backend.service.OxOSolrClient;
import uk.ac.ebi.spot.oxo.backend.service.export.ExportFormat;
import uk.ac.ebi.spot.oxo.backend.service.export.MappingTsvExporter;
import uk.ac.ebi.spot.oxo.backend.service.helper.SolrQueryBuilder;
import uk.ac.ebi.spot.oxo.model.sssom.Mapping;
import uk.ac.ebi.spot.oxo.model.sssom.MappingEnum;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MappingController.class)
class MappingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OxOSolrClient solrClient;

    // MappingController depends on the exporter; mock it so the @WebMvcTest slice context loads.
    @MockitoBean
    private MappingTsvExporter exporter;

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

    // ---------- POST /api/v2/mappings/batch-map ----------

    /** QueryResponse carrying one facet.query count per (clause -> count). */
    private static QueryResponse facetQueryResponse(Map<String, Integer> queryCounts) {
        NamedList<Object> facetQueries = new NamedList<>();
        queryCounts.forEach(facetQueries::add);
        NamedList<Object> facetCounts = new NamedList<>();
        facetCounts.add("facet_queries", facetQueries);
        facetCounts.add("facet_fields", new NamedList<>());
        NamedList<Object> root = new NamedList<>();
        root.add("response", new SolrDocumentList());
        root.add("facet_counts", facetCounts);
        QueryResponse queryResponse = new QueryResponse();
        queryResponse.setResponse(root);
        return queryResponse;
    }

    @Test
    void batchMapComputesUnmappedInputsFromFacetCounts() throws Exception {
        when(solrClient.query(any(SolrParams.class), any(Pageable.class)))
                .thenReturn(emptyResponse());
        // Per-input facet.query counts: DOID:9352 maps (2), DOID:0000 maps to nothing (0), diabetes maps (1).
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put(SolrQueryBuilder.subjectSideClause("DOID:9352"), 2);
        counts.put(SolrQueryBuilder.subjectSideClause("DOID:0000"), 0);
        counts.put(SolrQueryBuilder.subjectSideClause("diabetes"), 1);
        when(solrClient.queryMappings(any(SolrParams.class)))
                .thenReturn(facetQueryResponse(counts));

        String body = """
                {
                  "terms": ["DOID:9352", "DOID:0000", "diabetes"],
                  "objectPrefixes": ["EFO"],
                  "page": 0,
                  "size": 10
                }
                """;

        mockMvc.perform(post("/api/v2/mappings/batch-map")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unmappedInputs.length()").value(1))
                .andExpect(jsonPath("$.unmappedInputs[0]").value("DOID:0000"))
                .andExpect(jsonPath("$.summary.inputCount").value(3))
                .andExpect(jsonPath("$.summary.mappedCount").value(2))
                .andExpect(jsonPath("$.summary.unmappedCount").value(1));

        // The display query restricts objects to the target ontology.
        SolrQuery displayQuery = captureQuery();
        assertThat(displayQuery.getFilterQueries())
                .contains("(" + MappingEnum.OBJECT_PREFIX.getField() + ":EFO)");
    }

    @Test
    void batchMapReturns400OnOversizeSize() throws Exception {
        String body = """
                {
                  "terms": ["DOID:9352"],
                  "size": 101
                }
                """;
        mockMvc.perform(post("/api/v2/mappings/batch-map")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void batchMapReturns400WhenTooManyTerms() throws Exception {
        String terms = IntStream.rangeClosed(0, MappingController.MAX_INPUT_TERMS) // 1001 distinct terms
                .mapToObj(i -> "\"DOID:" + i + "\"")
                .collect(Collectors.joining(","));
        String body = "{ \"terms\": [" + terms + "] }";

        mockMvc.perform(post("/api/v2/mappings/batch-map")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ---------- ?format= export routing (ADR-0024) ----------

    @Test
    void searchWithFormatRoutesToStreamingExportNotJsonPath() throws Exception {
        String body = """
                { "subjectPrefixes": ["DOID"], "objectPrefixes": ["EFO"], "page": 0, "size": 10 }
                """;

        // A non-json format streams the export and must NOT issue the paged JSON search query.
        mockMvc.perform(post("/api/v2/mappings/search")
                        .param("format", "sssom-tsv")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/tab-separated-values"));

        verify(solrClient, never()).query(any(SolrParams.class), any(Pageable.class));
        verify(exporter).stream(any(SolrQuery.class), eq(ExportFormat.SSSOM_TSV), any());
    }

    @Test
    void searchWithoutFormatStaysOnJsonPath() throws Exception {
        when(solrClient.query(any(SolrParams.class), any(Pageable.class)))
                .thenReturn(emptyResponse());

        mockMvc.perform(post("/api/v2/mappings/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"queries\": [\"diabetes\"], \"page\": 0, \"size\": 10 }"))
                .andExpect(status().isOk());

        verify(solrClient).query(any(SolrParams.class), any(Pageable.class));
    }

    // ---------- column-filter deserialization (ADR-0034) ----------

    /**
     * Column filters must survive the JSON round-trip. This is not hypothetical: adding a second
     * ColumnFilter constructor for the EXACT match type made Jackson's implicit creator ambiguous, and
     * EVERY column filter — including ones that never mention `match` — started failing with a
     * type-definition error. The builder unit tests could not see it, because they construct
     * ColumnFilter in Java and never go through Jackson. This one goes through the wire.
     */
    @Test
    void columnFilterWithoutAMatchTypeDeserialisesAndContains() throws Exception {
        when(solrClient.query(any(SolrParams.class), any(Pageable.class))).thenReturn(emptyResponse());
        String body = """
                {
                  "queries": ["cataract"],
                  "columnFilters": [{"id": "object_label", "value": "eye"}],
                  "page": 0,
                  "size": 10
                }
                """;

        mockMvc.perform(post("/api/v2/mappings/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        assertThat(String.join(" ", captureQuery().getFilterQueries()))
                .contains(MappingEnum.OBJECT_LABEL.getField() + "_ngram:*"
                        + ClientUtils.escapeQueryChars("eye") + "*");
    }

    /** And an EXACT filter — a value the user PICKED from a suggestion — matches the whole value. */
    @Test
    void exactColumnFilterDeserialisesAndMatchesTheWholeValue() throws Exception {
        when(solrClient.query(any(SolrParams.class), any(Pageable.class))).thenReturn(emptyResponse());
        String body = """
                {
                  "queries": ["cataract"],
                  "columnFilters": [{"id": "object_label", "value": "Cataract", "match": "EXACT"}],
                  "page": 0,
                  "size": 10
                }
                """;

        mockMvc.perform(post("/api/v2/mappings/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        String filterQueries = String.join(" ", captureQuery().getFilterQueries());
        assertThat(filterQueries)
                .contains(MappingEnum.OBJECT_LABEL.getField() + "_str:\""
                        + ClientUtils.escapeQueryChars("Cataract") + "\"")
                .doesNotContain(MappingEnum.OBJECT_LABEL.getField() + "_ngram:*");
    }
}
