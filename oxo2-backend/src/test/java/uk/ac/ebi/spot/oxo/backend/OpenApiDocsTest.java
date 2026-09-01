package uk.ac.ebi.spot.oxo.backend;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.ac.ebi.spot.oxo.backend.service.OxOSolrClient;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boots the full application context so springdoc's auto-configuration runs, and asserts the
 * generated OpenAPI document is served and describes every endpoint. This is what verifies the
 * springdoc/Spring Boot/Java combination actually works at runtime — generating the spec exercises
 * model resolution over the whole {@code Mapping} graph, which a compile alone does not.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocsTest {

    @Autowired
    private MockMvc mockMvc;

    // The real OxOSolrClient builds Solr clients in @PostConstruct; mock it so the context needs no
    // running Solr.
    @MockitoBean
    private OxOSolrClient solrClient;

    @Test
    void apiDocsServedAndDescribeAllEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.info.title").value("OxO2 API"))
                .andExpect(jsonPath("$.paths['/api/v2/mappings/search'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v2/mappings/{subjectId}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v2/mapping-sets'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v2/mapping-sets/by-id'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v2/ontologies'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v2/mappings'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v2/mappings/batch-map'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v2/health'].get").exists())
                .andExpect(jsonPath("$.paths['/api/search'].post").exists())
                .andExpect(jsonPath("$.paths['/api/mappings'].get").exists())
                // SSSOM-API surface (ADR-0032).
                .andExpect(jsonPath("$.paths['/api/sssom/mappings'].get").exists())
                .andExpect(jsonPath("$.paths['/api/sssom/mappings/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/sssom/entities'].post").exists())
                .andExpect(jsonPath("$.paths['/api/sssom/mapping_sets'].get").exists())
                // set-scoped mappings are a mapping_set_id query param on /mappings, not a sub-path
                .andExpect(jsonPath(
                        "$.paths['/api/sssom/mappings'].get.parameters[?(@.name=='mapping_set_id')]").exists())
                .andExpect(jsonPath("$.paths['/api/sssom/stats'].get").exists());
    }

    /**
     * The v1 GET must expose the request bean's fields as individual query parameters. Without
     * {@code @ParameterObject} springdoc emits a single opaque parameter named "request", and Swagger
     * UI's "Try it out" then builds {@code ?request=<json>} — a call Spring cannot bind, so the
     * documented example silently returns nothing.
     */
    @Test
    void v1SearchGetExposesItsRequestFieldsAsIndividualQueryParameters() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/search'].get").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/search'].get.parameters[?(@.name=='ids')]").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/search'].get.parameters[?(@.name=='mappingTarget')]").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/search'].get.parameters[?(@.name=='distance')]").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/search'].get.parameters[?(@.name=='request')]").doesNotExist());
    }

    /**
     * Two @PostMapping methods share this path, and OpenAPI allows one operation per path+method. The
     * form variant is hidden so the merge cannot drag its @ModelAttribute in as a bogus "request"
     * query parameter; the form media type rides on the JSON operation's request body instead.
     */
    @Test
    void v1SearchPostDocumentsBothMediaTypesAndNoStrayQueryObject() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['/api/search'].post.requestBody.content['application/json']").exists())
                .andExpect(jsonPath("$.paths['/api/search'].post.requestBody"
                        + ".content['application/x-www-form-urlencoded']").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/search'].post.parameters[?(@.name=='request')]").doesNotExist());
    }

    @Test
    void searchOperationDocumentsDefaultPredicateHiding() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v2/mappings/search'].post.description",
                        Matchers.containsString("oboInOwl:hasDbXref")));
    }

    @Test
    void swaggerUiIsAvailable() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
