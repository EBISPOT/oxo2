package uk.ac.ebi.spot.oxo.backend.controller.api.v2;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.ac.ebi.spot.oxo.backend.service.OxOSolrClient;
import uk.ac.ebi.spot.oxo.backend.service.helper.EntitySuggestQueryBuilder;
import uk.ac.ebi.spot.oxo.model.entity.EntityConstants;
import uk.ac.ebi.spot.oxo.model.sssom.WeakPredicate;

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

    /**
     * The count comes back on the {@code visible_mapping_count} pseudo-field — Solr computes it as a
     * function over the buckets the caller's checkboxes make visible (ADR-0035), so it is what the
     * search will actually return, not the entity's stored total.
     */
    private static SolrDocument entity(String id, String label, String iri, String prefix,
                                       long visibleMappingCount) {
        SolrDocument document = new SolrDocument();
        document.addField(EntityConstants.ID, id);
        document.addField(EntityConstants.LABEL, label);
        document.addField(EntityConstants.IRI, iri);
        document.addField(EntityConstants.PREFIX, prefix);
        document.addField(EntitySuggestQueryBuilder.VISIBLE_MAPPING_COUNT, visibleMappingCount);
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
     * The main search box's contract: it must not offer an entity that the default search would then
     * fail to find. That means the subject side only (ADR-0030) AND strong predicates only
     * (ADR-0035) — an entity whose subject-side mappings are all xrefs completes to zero rows just as
     * surely as one that only ever appears as an object.
     */
    @Test
    void restrictsToWhatADefaultSearchCanShow() throws Exception {
        when(solrClient.queryEntities(any())).thenReturn(entityResponse());

        mockMvc.perform(get("/api/v2/suggest/entities").param("q", "mel"))
                .andExpect(status().isOk());

        assertThat(captureEntityQuery().getFilterQueries())
                .contains(EntityConstants.subjectCountField(
                        EntityConstants.bucketFor(EntityConstants.STRONG_BUCKET, false)) + ":[1 TO *]");
    }

    /** The checkbox state reaches the suggest, so the dropdown matches the table it completes into. */
    @Test
    void includeWeakPredicatesWidensTheSuggest() throws Exception {
        when(solrClient.queryEntities(any())).thenReturn(entityResponse());

        mockMvc.perform(get("/api/v2/suggest/entities")
                        .param("q", "mel")
                        .param("includeWeakPredicates", "hasDbXref"))
                .andExpect(status().isOk());

        assertThat(String.join(" ", captureEntityQuery().getFilterQueries()))
                .contains(EntityConstants.subjectCountField(EntityConstants.bucketFor(
                        WeakPredicate.HAS_DB_XREF.bucket(), false)) + ":[1 TO *]");
    }

    /**
     * An unrecognised predicate is a 400, never a silent drop: silently ignoring it would return
     * suggestions filtered differently from the search the caller believes it is completing — the
     * very mismatch ADR-0035 exists to remove.
     */
    @Test
    void unknownWeakPredicateIsRejected() throws Exception {
        mockMvc.perform(get("/api/v2/suggest/entities")
                        .param("q", "mel")
                        .param("includeWeakPredicates", "skos:exactMatch"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(solrClient);
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
                .contains(EntityConstants.objectCountField(
                        EntityConstants.bucketFor(EntityConstants.STRONG_BUCKET, false)) + ":[1 TO *]");
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
        noLabel.addField(EntitySuggestQueryBuilder.VISIBLE_MAPPING_COUNT, 3L);
        when(solrClient.queryEntities(any())).thenReturn(entityResponse(noLabel));

        mockMvc.perform(get("/api/v2/suggest/entities").param("q", "MONDO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("MONDO:0005148"))
                .andExpect(jsonPath("$[0].label").doesNotExist())
                .andExpect(jsonPath("$[0].mapping_count").value(3));
    }

    /**
     * The reported bug (ADR-0044): with a mapping set checked, the typeahead used to ignore it entirely
     * and go on offering entities from the whole corpus. The restriction has to reach Solr, and it has
     * to carry the side with it — an entity that is merely an object in the chosen set completes to no
     * rows under the subject-side default search.
     */
    @Test
    void mappingSetRestrictionReachesSolrCarryingTheSide() throws Exception {
        when(solrClient.queryEntities(any())).thenReturn(entityResponse());

        mockMvc.perform(get("/api/v2/suggest/entities")
                        .param("q", "mel")
                        .param("mappingSetId", "https://www.ebi.ac.uk/oxo2/inferences"))
                .andExpect(status().isOk());

        assertThat(String.join(" ", captureEntityQuery().getFilterQueries()))
                .contains(EntityConstants.SET_SCOPE + ":")
                .contains(ClientUtils.escapeQueryChars(EntityConstants.setScopeToken("https://www.ebi.ac.uk/oxo2/inferences", true,
                        EntityConstants.bucketFor(EntityConstants.STRONG_BUCKET, false))));
    }

    @Test
    void severalMappingSetsAreAllPassedThrough() throws Exception {
        when(solrClient.queryEntities(any())).thenReturn(entityResponse());

        mockMvc.perform(get("/api/v2/suggest/entities")
                        .param("q", "mel")
                        .param("mappingSetId", "https://example.org/setA")
                        .param("mappingSetId", "https://example.org/setB"))
                .andExpect(status().isOk());

        String filterQueries = String.join(" ", captureEntityQuery().getFilterQueries());
        assertThat(filterQueries)
                .contains(ClientUtils.escapeQueryChars(EntityConstants.setScopeToken("https://example.org/setA", true,
                        EntityConstants.bucketFor(EntityConstants.STRONG_BUCKET, false))))
                .contains(ClientUtils.escapeQueryChars(EntityConstants.setScopeToken("https://example.org/setB", true,
                        EntityConstants.bucketFor(EntityConstants.STRONG_BUCKET, false))));
    }

    /**
     * Under a restriction the count is withheld rather than reported wrong: the buckets behind it are
     * corpus-wide, so a number computed from them would overstate what the narrowed search returns.
     * Solr sends no pseudo-field, and the response must omit the property rather than say zero — a zero
     * would read as "this suggestion returns nothing", which is exactly what it does not mean.
     */
    @Test
    void restrictedSuggestOmitsTheMappingCount() throws Exception {
        SolrDocument noCount = new SolrDocument();
        noCount.addField(EntityConstants.ID, "MONDO:0005148");
        noCount.addField(EntityConstants.LABEL, "type 2 diabetes mellitus");
        when(solrClient.queryEntities(any())).thenReturn(entityResponse(noCount));

        mockMvc.perform(get("/api/v2/suggest/entities")
                        .param("q", "type 2")
                        .param("mappingSetId", "https://www.ebi.ac.uk/oxo2/inferences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("MONDO:0005148"))
                .andExpect(jsonPath("$[0].label").value("type 2 diabetes mellitus"))
                .andExpect(jsonPath("$[0].mapping_count").doesNotExist());
    }

    /** No selection is the common case: it must add no set filter and still report the count. */
    @Test
    void noMappingSetRestrictionLeavesTheQueryAndTheCountAlone() throws Exception {
        when(solrClient.queryEntities(any())).thenReturn(entityResponse(
                entity("MONDO:0005148", "type 2 diabetes mellitus", null, "MONDO", 42L)));

        mockMvc.perform(get("/api/v2/suggest/entities").param("q", "type 2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].mapping_count").value(42));

        assertThat(String.join(" ", captureEntityQuery().getFilterQueries()))
                .doesNotContain(EntityConstants.SET_SCOPE);
    }

    /**
     * The EFO:0006471 report (ADR-0045). Its one mapping was
     * {@code EFO:0006471 SKOS:exactMatch MONDO:0005603} with {@code object_obsolete:true} — so the
     * default search returned nothing while the suggest offered the term with {@code mapping_count: 1},
     * because EFO:0006471 is not itself obsolete. The default suggest must therefore filter on the live
     * bucket, and must never fall back to the unrestricted one.
     */
    @Test
    void defaultSuggestNeverReadsTheUnrestrictedBuckets() throws Exception {
        when(solrClient.queryEntities(any())).thenReturn(entityResponse());

        mockMvc.perform(get("/api/v2/suggest/entities").param("q", "EFO:00064"))
                .andExpect(status().isOk());

        SolrQuery solrQuery = captureEntityQuery();
        String live = EntityConstants.subjectCountField(
                EntityConstants.bucketFor(EntityConstants.STRONG_BUCKET, false));
        assertThat(solrQuery.getFilterQueries()).contains(live + ":[1 TO *]");
        assertThat(String.join(" ", solrQuery.getFilterQueries()))
                .doesNotContain(EntityConstants.SUBJECT_COUNT_STRONG + ":[1 TO *]");
        // The displayed count has to come off the same bucket, or the row promises rows the table lacks.
        assertThat(solrQuery.getFields())
                .contains(EntitySuggestQueryBuilder.VISIBLE_MAPPING_COUNT + ":sum(" + live + ")");
    }

    /** Ticking "show obsolete terms" widens both the filter and the count back to every mapping. */
    @Test
    void includeObsoleteReadsTheUnrestrictedBuckets() throws Exception {
        when(solrClient.queryEntities(any())).thenReturn(entityResponse());

        mockMvc.perform(get("/api/v2/suggest/entities")
                        .param("q", "EFO:00064")
                        .param("includeObsolete", "true"))
                .andExpect(status().isOk());

        SolrQuery solrQuery = captureEntityQuery();
        assertThat(solrQuery.getFilterQueries())
                .contains(EntityConstants.SUBJECT_COUNT_STRONG + ":[1 TO *]");
        assertThat(String.join(" ", solrQuery.getFilterQueries()))
                .doesNotContain(EntityConstants.LIVE_BUCKET_SUFFIX);
    }

    @Test
    void solrFailureIsA500NotAStackTrace() throws Exception {
        when(solrClient.queryEntities(any())).thenThrow(new RuntimeException("solr is down"));

        mockMvc.perform(get("/api/v2/suggest/entities").param("q", "mel"))
                .andExpect(status().isInternalServerError());
    }
}
