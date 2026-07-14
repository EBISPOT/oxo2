package uk.ac.ebi.spot.oxo.backend.controller.api.v2;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.ac.ebi.spot.oxo.backend.service.OxOSolrClient;
import uk.ac.ebi.spot.oxo.backend.service.helper.EntitySuggestQueryBuilder;
import uk.ac.ebi.spot.oxo.model.entity.EntityConstants;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SuggestController.class)
class SuggestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OxOSolrClient solrClient;

    /** A QueryResponse carrying the given entity documents, as oxo2-entities would return them. */
    private static QueryResponse entityResponse(SolrDocument... documents) {
        SolrDocumentList results = new SolrDocumentList();
        results.addAll(List.of(documents));
        results.setNumFound(documents.length);
        NamedList<Object> root = new NamedList<>();
        root.add("response", results);
        QueryResponse queryResponse = new QueryResponse();
        queryResponse.setResponse(root);
        return queryResponse;
    }

    private static SolrDocument entity(String id, String label, String iri, String prefix, long mappingCount) {
        SolrDocument document = new SolrDocument();
        document.addField(EntityConstants.ID, id);
        document.addField(EntityConstants.LABEL, label);
        document.addField(EntityConstants.IRI, iri);
        document.addField(EntityConstants.PREFIX, prefix);
        document.addField(EntityConstants.MAPPING_COUNT, mappingCount);
        return document;
    }

    /** The SolrQuery actually handed to the (mocked) entities collection. */
    private SolrQuery captureEntityQuery() throws Exception {
        ArgumentCaptor<SolrParams> captor = ArgumentCaptor.forClass(SolrParams.class);
        verify(solrClient).queryEntities(captor.capture());
        return (SolrQuery) captor.getValue();
    }

    @Test
    void mapsSolrDocumentsToSuggestions() throws Exception {
        when(solrClient.queryEntities(any())).thenReturn(entityResponse(
                entity("MONDO:0005148", "type 2 diabetes mellitus",
                        "http://purl.obolibrary.org/obo/MONDO_0005148", "MONDO", 42L)));

        mockMvc.perform(get("/api/v2/suggest/entities").param("q", "type 2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("MONDO:0005148"))
                .andExpect(jsonPath("$[0].label").value("type 2 diabetes mellitus"))
                .andExpect(jsonPath("$[0].iri").value("http://purl.obolibrary.org/obo/MONDO_0005148"))
                .andExpect(jsonPath("$[0].prefix").value("MONDO"))
                .andExpect(jsonPath("$[0].mapping_count").value(42));
    }

    /**
     * The main search box's contract: it must not offer an entity that only ever appears as an
     * object, because the default search matches the subject side only (ADR-0030) and such a
     * suggestion would complete to zero rows.
     */
    @Test
    void restrictsToTheSubjectSideByDefault() throws Exception {
        when(solrClient.queryEntities(any())).thenReturn(entityResponse());

        mockMvc.perform(get("/api/v2/suggest/entities").param("q", "mel"))
                .andExpect(status().isOk());

        assertThat(captureEntityQuery().getFilterQueries())
                .contains(EntityConstants.IS_SUBJECT + ":true");
    }

    @Test
    void passesTheOntologyPrefixFilterThrough() throws Exception {
        when(solrClient.queryEntities(any())).thenReturn(entityResponse());

        mockMvc.perform(get("/api/v2/suggest/entities")
                        .param("q", "mel")
                        .param("prefix", "MONDO")
                        .param("prefix", "EFO"))
                .andExpect(status().isOk());

        assertThat(String.join(" ", captureEntityQuery().getFilterQueries()))
                .contains(EntityConstants.PREFIX + ":MONDO")
                .contains(EntityConstants.PREFIX + ":EFO");
    }

    @Test
    void objectSideIsSelectable() throws Exception {
        when(solrClient.queryEntities(any())).thenReturn(entityResponse());

        mockMvc.perform(get("/api/v2/suggest/entities").param("q", "mel").param("side", "OBJECT"))
                .andExpect(status().isOk());

        assertThat(captureEntityQuery().getFilterQueries())
                .contains(EntityConstants.IS_OBJECT + ":true");
    }

    /** A one-character prefix matches a large fraction of the collection; it must never reach Solr. */
    @Test
    void tooShortQueryReturnsEmptyWithoutQueryingSolr() throws Exception {
        mockMvc.perform(get("/api/v2/suggest/entities").param("q", "m"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());

        verifyNoInteractions(solrClient);
    }

    @Test
    void missingQueryReturnsEmptyWithoutQueryingSolr() throws Exception {
        mockMvc.perform(get("/api/v2/suggest/entities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        verifyNoInteractions(solrClient);
    }

    @Test
    void sizeIsCapped() throws Exception {
        when(solrClient.queryEntities(any())).thenReturn(entityResponse());

        mockMvc.perform(get("/api/v2/suggest/entities").param("q", "mel").param("size", "5000"))
                .andExpect(status().isOk());

        assertThat(captureEntityQuery().getRows())
                .isEqualTo(EntitySuggestQueryBuilder.MAX_SUGGEST_ROWS);
    }

    /** An entity with no label is legal (no mapping carried one); it must not blow up the mapping. */
    @Test
    void handlesAnEntityWithNoLabel() throws Exception {
        SolrDocument noLabel = new SolrDocument();
        noLabel.addField(EntityConstants.ID, "MONDO:0005148");
        noLabel.addField(EntityConstants.MAPPING_COUNT, 3L);
        when(solrClient.queryEntities(any())).thenReturn(entityResponse(noLabel));

        mockMvc.perform(get("/api/v2/suggest/entities").param("q", "MONDO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("MONDO:0005148"))
                .andExpect(jsonPath("$[0].label").doesNotExist())
                .andExpect(jsonPath("$[0].mapping_count").value(3));
    }

    @Test
    void solrFailureIsA500NotAStackTrace() throws Exception {
        when(solrClient.queryEntities(any())).thenThrow(new RuntimeException("solr is down"));

        mockMvc.perform(get("/api/v2/suggest/entities").param("q", "mel"))
                .andExpect(status().isInternalServerError());
    }
}
