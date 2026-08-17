package uk.ac.ebi.spot.oxo.backend.controller.api.v1;

import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.ac.ebi.spot.oxo.backend.service.EntityLabelResolver;
import uk.ac.ebi.spot.oxo.backend.service.OxOSolrClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The real EntityLabelResolver is imported rather than mocked: the v1 label contract IS its
// precedence rule plus the query it sends, so mocking it would assert nothing that matters.
@WebMvcTest(V1SearchController.class)
@Import(EntityLabelResolver.class)
class V1SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OxOSolrClient solrClient;

    /** Only non-null fields are set, so a test can model a mapping row that carries no label. */
    private static SolrDocument doc(String subjectId, String subjectLabel, String objectId,
                                    String objectLabel, String inferenceType) {
        SolrDocument document = new SolrDocument();
        document.setField("mapping_id", "123e4567-e89b-12d3-a456-426614174000");
        document.setField("subject_id", subjectId);
        if (subjectLabel != null) {
            document.setField("subject_label", subjectLabel);
        }
        document.setField("object_id", objectId);
        if (objectLabel != null) {
            document.setField("object_label", objectLabel);
        }
        document.setField("inference_type", inferenceType);
        return document;
    }

    private static SolrDocument entity(String id, String label) {
        SolrDocument document = new SolrDocument();
        document.setField("id", id);
        document.setField("label", label);
        return document;
    }

    private static QueryResponse responseWith(SolrDocument... docs) {
        SolrDocumentList list = new SolrDocumentList();
        for (SolrDocument document : docs) {
            list.add(document);
        }
        list.setNumFound(docs.length);
        NamedList<Object> root = new NamedList<>();
        root.add("response", list);
        QueryResponse queryResponse = new QueryResponse();
        queryResponse.setResponse(root);
        return queryResponse;
    }

    @BeforeEach
    void entityLookupReturnsNothingByDefault() throws Exception {
        when(solrClient.queryEntities(any(SolrParams.class), any(SolrRequest.METHOD.class)))
                .thenReturn(responseWith());
    }

    @Test
    void returnsV1HalEnvelopeWithMappingsAndUnmappedInput() throws Exception {
        // First id maps (asserted); second id has no hits.
        when(solrClient.queryMappings(any(SolrParams.class)))
                .thenReturn(responseWith(
                        doc("DOID:9352", "diabetes", "EFO:0000400", "diabetes mellitus", "ASSERTED")))
                .thenReturn(responseWith());

        String body = """
                { "ids": ["DOID:9352", "DOID:0000"], "mappingTarget": ["EFO"], "distance": 1 }
                """;

        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.searchResults.length()").value(2))
                .andExpect(jsonPath("$._embedded.searchResults[0].queryId").value("DOID:9352"))
                .andExpect(jsonPath("$._embedded.searchResults[0].curie").value("DOID:9352"))
                .andExpect(jsonPath("$._embedded.searchResults[0].label").value("diabetes"))
                .andExpect(jsonPath("$._embedded.searchResults[0].mappingResponseList[0].curie")
                        .value("EFO:0000400"))
                .andExpect(jsonPath("$._embedded.searchResults[0].mappingResponseList[0].targetPrefix")
                        .value("EFO"))
                .andExpect(jsonPath("$._embedded.searchResults[0].mappingResponseList[0].distance")
                        .value(1))
                .andExpect(jsonPath("$._embedded.searchResults[1].queryId").value("DOID:0000"))
                .andExpect(jsonPath("$._embedded.searchResults[1].mappingResponseList.length()").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$._links.self.href").value("/api/search"));
    }

    @Test
    void rejectsIdsContainingHtmlMetacharacters() throws Exception {
        when(solrClient.queryMappings(any(SolrParams.class)))
                .thenReturn(responseWith(
                        doc("DOID:9352", "diabetes", "EFO:0000400", "diabetes mellitus", "ASSERTED")));

        String body = """
                { "ids": ["DOID:9352", "<script>alert(1)</script>"], "mappingTarget": ["EFO"], "distance": 1 }
                """;

        // The XSS payload fails the input allowlist and is dropped; only the valid CURIE survives, so it
        // can never reach the CSV/TSV export writer (CodeQL java/xss, alert #2).
        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.searchResults.length()").value(1))
                .andExpect(jsonPath("$._embedded.searchResults[0].queryId").value("DOID:9352"));
    }

    @Test
    void setsNosniffHeaderOnEveryResponse() throws Exception {
        when(solrClient.queryMappings(any(SolrParams.class))).thenReturn(responseWith());

        String body = """
                { "ids": ["DOID:9352"], "mappingTarget": ["EFO"], "distance": 1 }
                """;

        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void inferredMappingGetsDistanceSentinelTwo() throws Exception {
        when(solrClient.queryMappings(any(SolrParams.class)))
                .thenReturn(responseWith(
                        doc("DOID:9352", "diabetes", "MONDO:0005148", "type 2 diabetes", "SSSOM_INFERENCE")));

        String body = """
                { "ids": ["DOID:9352"], "mappingTarget": ["MONDO"], "distance": -1 }
                """;

        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.searchResults[0].mappingResponseList[0].distance")
                        .value(2));
    }

    @Test
    void unlabelledMappingRowTakesItsLabelFromTheEntityCollection() throws Exception {
        // The row carries no object_label at all — the case that returned null to v1 clients.
        when(solrClient.queryMappings(any(SolrParams.class)))
                .thenReturn(responseWith(
                        doc("MONDO:0005259", null, "EFO:1000466", null, "ASSERTED")));
        when(solrClient.queryEntities(any(SolrParams.class), any(SolrRequest.METHOD.class)))
                .thenReturn(responseWith(
                        entity("MONDO:0005259", "hearing impairment"),
                        entity("EFO:1000466", "Penile Fibromatosis")));

        String body = """
                { "ids": ["MONDO:0005259"], "distance": 1 }
                """;

        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.searchResults[0].label").value("hearing impairment"))
                .andExpect(jsonPath("$._embedded.searchResults[0].mappingResponseList[0].label")
                        .value("Penile Fibromatosis"));
    }

    @Test
    void entityLabelWinsOverADifferingMappingRowLabel() throws Exception {
        // EFO:0000313 is stored as "carcinoma" on the gwas row but "obsolete_carcinoma" on the entity,
        // and OxO v1 returns the latter — so the entity collection is the authority.
        when(solrClient.queryMappings(any(SolrParams.class)))
                .thenReturn(responseWith(
                        doc("MESH:D002277", "Carcinoma", "EFO:0000313", "carcinoma", "ASSERTED")));
        when(solrClient.queryEntities(any(SolrParams.class), any(SolrRequest.METHOD.class)))
                .thenReturn(responseWith(entity("EFO:0000313", "obsolete_carcinoma")));

        String body = """
                { "ids": ["MESH:D002277"], "distance": 1 }
                """;

        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.searchResults[0].mappingResponseList[0].label")
                        .value("obsolete_carcinoma"));
    }

    @Test
    void aTermWithNoLabelAnywhereFallsBackToItsCurie() throws Exception {
        // ICD10:J45.0 is real but unlabelled throughout the corpus. v1 never emits a null label, so the
        // CURIE stands in — a client reading label unconditionally keeps working.
        when(solrClient.queryMappings(any(SolrParams.class)))
                .thenReturn(responseWith(
                        doc("MONDO:0004979", null, "ICD10:J45.0", null, "ASSERTED")));

        String body = """
                { "ids": ["MONDO:0004979"], "distance": 1 }
                """;

        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.searchResults[0].label").value("MONDO:0004979"))
                .andExpect(jsonPath("$._embedded.searchResults[0].mappingResponseList[0].label")
                        .value("ICD10:J45.0"));
    }

    @Test
    void anUnmappedInputKeepsV1sNullCurieAndLabel() throws Exception {
        // The CURIE fallback must NOT fire here: v1 reports an unknown input as null/null rather than
        // echoing the input back as though it were a known term.
        when(solrClient.queryMappings(any(SolrParams.class))).thenReturn(responseWith());

        String body = """
                { "ids": ["OMIM:314580"], "distance": 1 }
                """;

        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.searchResults[0].curie").doesNotExist())
                .andExpect(jsonPath("$._embedded.searchResults[0].label").doesNotExist())
                .andExpect(jsonPath("$._embedded.searchResults[0].mappingResponseList.length()").value(0));
    }

    @Test
    void theEntityLookupDoesNotFilterOutObsoleteTerms() throws Exception {
        // ADR-0045 hides obsolete terms from the typeahead by default. Inheriting that default here
        // would leave exactly the obsolete terms unlabelled — the symptom this fix exists to remove.
        when(solrClient.queryMappings(any(SolrParams.class)))
                .thenReturn(responseWith(
                        doc("MONDO:0005562", null, "EFO:0005782", null, "ASSERTED")));
        when(solrClient.queryEntities(any(SolrParams.class), any(SolrRequest.METHOD.class)))
                .thenReturn(responseWith(
                        entity("EFO:0005782", "obsolete_age-related hearing impairment")));

        String body = """
                { "ids": ["MONDO:0005562"], "distance": 1 }
                """;

        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.searchResults[0].mappingResponseList[0].label")
                        .value("obsolete_age-related hearing impairment"));

        ArgumentCaptor<SolrParams> captor = ArgumentCaptor.forClass(SolrParams.class);
        verify(solrClient).queryEntities(captor.capture(), any(SolrRequest.METHOD.class));
        SolrParams entityQuery = captor.getValue();
        assertThat(entityQuery.getParams("fq")).isNull();
        assertThat(entityQuery.get("q")).doesNotContain("obsolete");
    }
}
