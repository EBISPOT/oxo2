package uk.ac.ebi.spot.oxo.backend.controller.api.v1;

import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.ac.ebi.spot.oxo.backend.service.OxOSolrClient;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(V1SearchController.class)
class V1SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OxOSolrClient solrClient;

    private static SolrDocument doc(String subjectId, String subjectLabel, String objectId,
                                    String objectLabel, String inferenceType) {
        SolrDocument document = new SolrDocument();
        document.setField("mapping_id", "123e4567-e89b-12d3-a456-426614174000");
        document.setField("subject_id", subjectId);
        document.setField("subject_label", subjectLabel);
        document.setField("object_id", objectId);
        document.setField("object_label", objectLabel);
        document.setField("inference_type", inferenceType);
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
}
