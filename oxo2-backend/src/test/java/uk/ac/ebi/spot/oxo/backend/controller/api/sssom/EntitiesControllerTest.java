package uk.ac.ebi.spot.oxo.backend.controller.api.sssom;

import jakarta.servlet.http.HttpServletResponse;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.ac.ebi.spot.oxo.model.sssom.EntityReference;
import uk.ac.ebi.spot.oxo.model.sssom.MappingEnum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EntitiesController.class)
class EntitiesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SssomMappingService mappingService;

    private static String normalisedCurie(String curie) {
        return new EntityReference(curie).getDataRepresentation().map(Object::toString).orElse(curie);
    }

    private SolrQuery captureRespondQuery() throws Exception {
        ArgumentCaptor<SolrQuery> captor = ArgumentCaptor.forClass(SolrQuery.class);
        verify(mappingService).respond(captor.capture(), anyInt(), anyInt(), any(),
                any(HttpServletResponse.class));
        return captor.getValue();
    }

    @Test
    void entityLookupMatchesEitherSideWithFilters() throws Exception {
        when(mappingService.respond(any(), anyInt(), anyInt(), any(), any()))
                .thenReturn(ResponseEntity.ok().build());

        String body = """
                {
                  "curies": ["MONDO:0005148"],
                  "mapping_justification": ["semapv:LexicalMatching"],
                  "predicate_id": ["skos:exactMatch"]
                }
                """;

        mockMvc.perform(post("/api/sssom/entities")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        SolrQuery solrQuery = captureRespondQuery();
        String normalised = normalisedCurie("MONDO:0005148");
        assertThat(solrQuery.getQuery())
                .contains(MappingEnum.SUBJECT_ID.getField() + ":\""
                        + ClientUtils.escapeQueryChars(normalised) + "\"")
                .contains(MappingEnum.OBJECT_ID.getField() + ":\""
                        + ClientUtils.escapeQueryChars(normalised) + "\"");
        assertThat(solrQuery.getFilterQueries()).anySatisfy(clause ->
                assertThat(clause).contains(MappingEnum.MAPPING_JUSTIFICATION.getField()));
        assertThat(solrQuery.getFilterQueries()).anySatisfy(clause ->
                assertThat(clause).contains(MappingEnum.PREDICATE_ID.getField()));
    }

    @Test
    void returns400OnZeroPage() throws Exception {
        mockMvc.perform(post("/api/sssom/entities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"curies\":[\"MONDO:0005148\"]}")
                        .param("page", "0"))
                .andExpect(status().isBadRequest());
    }
}
