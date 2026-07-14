package uk.ac.ebi.spot.oxo.backend.controller.api.sssom;

import jakarta.servlet.http.HttpServletResponse;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.ac.ebi.spot.oxo.backend.service.OxOSolrClient;
import uk.ac.ebi.spot.oxo.model.sssom.EntityReference;
import uk.ac.ebi.spot.oxo.model.sssom.Mapping;
import uk.ac.ebi.spot.oxo.model.sssom.MappingEnum;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MappingsController.class)
class MappingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SssomMappingService mappingService;
    @MockitoBean
    private OxOSolrClient solrClient;

    private static String normalisedCurie(String curie) {
        return new EntityReference(curie).getDataRepresentation().map(Object::toString).orElse(curie);
    }

    private SolrQuery captureRespondQuery() throws Exception {
        ArgumentCaptor<SolrQuery> captor = ArgumentCaptor.forClass(SolrQuery.class);
        verify(mappingService).respond(captor.capture(), anyInt(), anyInt(), any(),
                any(HttpServletResponse.class));
        return captor.getValue();
    }

    // ---------- GET /api/sssom/mappings ----------

    @Test
    void listMappingsBuildsFilteredQueryAndDelegates() throws Exception {
        when(mappingService.respond(any(), anyInt(), anyInt(), any(), any()))
                .thenReturn(ResponseEntity.ok().build());

        mockMvc.perform(get("/api/sssom/mappings").param("filter", "predicate_id|eq|skos:exactMatch"))
                .andExpect(status().isOk());

        SolrQuery solrQuery = captureRespondQuery();
        String normalised = normalisedCurie("skos:exactMatch");
        String expected = MappingEnum.PREDICATE_ID.getField() + ":\""
                + ClientUtils.escapeQueryChars(normalised) + "\"";
        assertThat(solrQuery.getFilterQueries()).contains(expected);
    }

    @Test
    void mappingSetIdParamScopesToThatSet() throws Exception {
        // ?mapping_set_id=<iri> is the reference's /mapping_sets/{id}/mappings, expressed as a filter
        // on the /mappings collection. mapping_set_id is a plain string field (no prefix normalisation).
        when(mappingService.respond(any(), anyInt(), anyInt(), any(), any()))
                .thenReturn(ResponseEntity.ok().build());

        mockMvc.perform(get("/api/sssom/mappings")
                        .param("mapping_set_id", "https://example.org/set"))
                .andExpect(status().isOk());

        SolrQuery solrQuery = captureRespondQuery();
        String expected = MappingEnum.MAPPING_SET_ID.getField() + ":\""
                + ClientUtils.escapeQueryChars("https://example.org/set") + "\"";
        assertThat(solrQuery.getFilterQueries()).contains(expected);
    }

    @Test
    void mappingSetIdParamCombinesWithFilter() throws Exception {
        // The set scope AND-joins with any explicit filter — both clauses present.
        when(mappingService.respond(any(), anyInt(), anyInt(), any(), any()))
                .thenReturn(ResponseEntity.ok().build());

        mockMvc.perform(get("/api/sssom/mappings")
                        .param("mapping_set_id", "https://example.org/set")
                        .param("filter", "predicate_id|eq|skos:exactMatch"))
                .andExpect(status().isOk());

        SolrQuery solrQuery = captureRespondQuery();
        String setClause = MappingEnum.MAPPING_SET_ID.getField() + ":\""
                + ClientUtils.escapeQueryChars("https://example.org/set") + "\"";
        String predicateClause = MappingEnum.PREDICATE_ID.getField() + ":\""
                + ClientUtils.escapeQueryChars(normalisedCurie("skos:exactMatch")) + "\"";
        assertThat(solrQuery.getFilterQueries()).contains(setClause, predicateClause);
    }

    @Test
    void listMappingsPassesOneBasedPageThrough() throws Exception {
        when(mappingService.respond(any(), anyInt(), anyInt(), any(), any()))
                .thenReturn(ResponseEntity.ok().build());

        mockMvc.perform(get("/api/sssom/mappings").param("page", "2").param("limit", "5"))
                .andExpect(status().isOk());

        // page/limit are forwarded verbatim (1-based); the service maps to the 0-based Pageable.
        verify(mappingService).respond(any(), eq(2), eq(5), any(), any(HttpServletResponse.class));
    }

    @Test
    void listMappingsReturns400OnMalformedFilter() throws Exception {
        mockMvc.perform(get("/api/sssom/mappings").param("filter", "predicate_id|eq"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listMappingsReturns400OnUnknownField() throws Exception {
        mockMvc.perform(get("/api/sssom/mappings").param("filter", "not_a_field|eq|x"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listMappingsReturns400OnZeroPage() throws Exception {
        mockMvc.perform(get("/api/sssom/mappings").param("page", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listMappingsReturns400OnOversizeLimit() throws Exception {
        mockMvc.perform(get("/api/sssom/mappings").param("limit", "101"))
                .andExpect(status().isBadRequest());
    }

    // ---------- GET /api/sssom/mappings/{field}/{value} ----------

    @Test
    void fieldValueBuildsEqualityQuery() throws Exception {
        when(mappingService.respond(any(), anyInt(), anyInt(), any(), any()))
                .thenReturn(ResponseEntity.ok().build());

        mockMvc.perform(get("/api/sssom/mappings/object_id/MONDO:0005148"))
                .andExpect(status().isOk());

        SolrQuery solrQuery = captureRespondQuery();
        String expected = MappingEnum.OBJECT_ID.getField() + ":\""
                + ClientUtils.escapeQueryChars(normalisedCurie("MONDO:0005148")) + "\"";
        assertThat(solrQuery.getFilterQueries()).contains(expected);
    }

    @Test
    void fieldValueReturns400OnUnknownField() throws Exception {
        mockMvc.perform(get("/api/sssom/mappings/not_a_field/value"))
                .andExpect(status().isBadRequest());
    }

    // ---------- GET /api/sssom/mappings/{id} ----------

    @Test
    void byIdReturns404WhenAbsent() throws Exception {
        Page<Mapping> empty = new PageImpl<>(Collections.emptyList());
        when(solrClient.querySssomMappings(any(), any(Pageable.class)))
                .thenReturn(new OxOSolrClient.SssomMappingResult(empty, new QueryResponse()));

        mockMvc.perform(get("/api/sssom/mappings/0b3d1f2a-6c4e-3a2b-9f10-2c8e7d6a5b4c"))
                .andExpect(status().isNotFound());
    }

    @Test
    void byIdReturnsBareMappingWhenFound() throws Exception {
        Mapping mapping = Mapping.builder()
                .mappingId("0b3d1f2a-6c4e-3a2b-9f10-2c8e7d6a5b4c")
                .mappingSetId("https://example.org/set")
                .subjectId("MONDO:0005148")
                .predicateId("skos:exactMatch")
                .objectId("DOID:9351")
                .build();
        Page<Mapping> found = new PageImpl<>(List.of(mapping));
        when(solrClient.querySssomMappings(any(), any(Pageable.class)))
                .thenReturn(new OxOSolrClient.SssomMappingResult(found, new QueryResponse()));

        mockMvc.perform(get("/api/sssom/mappings/0b3d1f2a-6c4e-3a2b-9f10-2c8e7d6a5b4c"))
                .andExpect(status().isOk())
                // a bare SSSOM mapping, not the {data, pagination, facets} envelope
                .andExpect(jsonPath("$.subject_id").value("MONDO:0005148"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void byIdSerializesFullDocumentSlots() throws Exception {
        // The by-id lookup returns the full document (no field list), so slots outside
        // MINIMAL_LIST_OF_FIELDS — provenance, set metadata, the OxO2 extension slots — must
        // survive serialization; the frontend mapping-details page renders them.
        Mapping mapping = Mapping.builder()
                .mappingId("0b3d1f2a-6c4e-3a2b-9f10-2c8e7d6a5b4c")
                .mappingSetId("https://example.org/set")
                .mappingSetDescription("A test set")
                .subjectId("MONDO:0005148")
                .predicateId("skos:exactMatch")
                .objectId("DOID:9351")
                .authorId("https://orcid.org/0000-0000-0000-0000")
                .confidence(0.9)
                .assertedMappingsAsString("[{\"subject_id\":\"MONDO:0005148\"}]")
                .explanationAsString("{\"subject_id\":\"MONDO:0005148\"}")
                .build();
        Page<Mapping> found = new PageImpl<>(List.of(mapping));
        when(solrClient.querySssomMappings(any(), any(Pageable.class)))
                .thenReturn(new OxOSolrClient.SssomMappingResult(found, new QueryResponse()));

        mockMvc.perform(get("/api/sssom/mappings/0b3d1f2a-6c4e-3a2b-9f10-2c8e7d6a5b4c"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mapping_set_description").value("A test set"))
                .andExpect(jsonPath("$.author_id[0]").value("https://orcid.org/0000-0000-0000-0000"))
                .andExpect(jsonPath("$.confidence").value(0.9))
                .andExpect(jsonPath("$.asserted_mappings").isNotEmpty())
                .andExpect(jsonPath("$.explanation").isNotEmpty());
    }
}
