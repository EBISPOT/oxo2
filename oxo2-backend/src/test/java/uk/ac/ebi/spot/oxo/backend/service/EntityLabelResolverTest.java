package uk.ac.ebi.spot.oxo.backend.service;

import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;

class EntityLabelResolverTest {

    private OxOSolrClient solrClient;
    private EntityLabelResolver resolver;

    @BeforeEach
    void setUp() {
        solrClient = mock(OxOSolrClient.class);
        resolver = new EntityLabelResolver();
        setField(resolver, "solrClient", solrClient);
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

    private static SolrDocument entity(String id, String label) {
        SolrDocument document = new SolrDocument();
        document.setField("id", id);
        if (label != null) {
            document.setField("label", label);
        }
        return document;
    }

    @Test
    void resolvesLabelsAndSendsATermsQueryAsPost() throws Exception {
        when(solrClient.queryEntities(any(SolrParams.class), any(SolrRequest.METHOD.class)))
                .thenReturn(responseWith(entity("EFO:1000466", "Penile Fibromatosis")));

        Map<String, String> labels = resolver.resolveLabels(List.of("EFO:1000466"));

        assertThat(labels).containsEntry("EFO:1000466", "Penile Fibromatosis");

        ArgumentCaptor<SolrParams> captor = ArgumentCaptor.forClass(SolrParams.class);
        verify(solrClient).queryEntities(captor.capture(), any(SolrRequest.METHOD.class));
        assertThat(captor.getValue().get("q")).isEqualTo("{!terms f=id}EFO:1000466");
    }

    @Test
    void aDocumentWithNoLabelIsAbsentRatherThanMappedToNull() throws Exception {
        when(solrClient.queryEntities(any(SolrParams.class), any(SolrRequest.METHOD.class)))
                .thenReturn(responseWith(entity("HP:0003570", null), entity("HP:0004444", "  ")));

        Map<String, String> labels = resolver.resolveLabels(List.of("HP:0003570", "HP:0004444"));

        // Absent, not null-valued: the caller distinguishes "no label" from "not looked up".
        assertThat(labels).isEmpty();
    }

    @Test
    void curiesAreDeduplicatedAndBlanksDropped() throws Exception {
        when(solrClient.queryEntities(any(SolrParams.class), any(SolrRequest.METHOD.class)))
                .thenReturn(responseWith(entity("EFO:1000466", "Penile Fibromatosis")));

        resolver.resolveLabels(new ArrayList<>(List.of(
                "EFO:1000466", "EFO:1000466", "  ", "EFO:1000466")));

        ArgumentCaptor<SolrParams> captor = ArgumentCaptor.forClass(SolrParams.class);
        verify(solrClient).queryEntities(captor.capture(), any(SolrRequest.METHOD.class));
        assertThat(captor.getValue().get("q")).isEqualTo("{!terms f=id}EFO:1000466");
    }

    @Test
    void aCurieContainingACommaIsSkippedRatherThanSplitByTheTermsParser() throws Exception {
        when(solrClient.queryEntities(any(SolrParams.class), any(SolrRequest.METHOD.class)))
                .thenReturn(responseWith(entity("EFO:1000466", "Penile Fibromatosis")));

        resolver.resolveLabels(List.of("EFO:1000466", "BAD:a,b"));

        ArgumentCaptor<SolrParams> captor = ArgumentCaptor.forClass(SolrParams.class);
        verify(solrClient).queryEntities(captor.capture(), any(SolrRequest.METHOD.class));
        // Passing it through would split into two bogus terms and could match the wrong entity.
        assertThat(captor.getValue().get("q")).isEqualTo("{!terms f=id}EFO:1000466");
    }

    @Test
    void curiesAreChunkedSoOneQueryNeverCarriesTheWholeResultSet() throws Exception {
        when(solrClient.queryEntities(any(SolrParams.class), any(SolrRequest.METHOD.class)))
                .thenReturn(responseWith());

        List<String> curies = new ArrayList<>();
        for (int i = 0; i < EntityLabelResolver.CHUNK_SIZE + 1; i++) {
            curies.add("EFO:" + i);
        }
        resolver.resolveLabels(curies);

        verify(solrClient, times(2))
                .queryEntities(any(SolrParams.class), any(SolrRequest.METHOD.class));
    }

    @Test
    void aFailedLookupDegradesToNoLabelsRatherThanPropagating() throws Exception {
        when(solrClient.queryEntities(any(SolrParams.class), any(SolrRequest.METHOD.class)))
                .thenThrow(new RuntimeException("solr down"));

        // The search around it must still answer; labels just fall back to the row/CURIE.
        assertThat(resolver.resolveLabels(List.of("EFO:1000466"))).isEmpty();
    }

    @Test
    void anEmptyOrNullInputMakesNoQuery() throws Exception {
        assertThat(resolver.resolveLabels(List.of())).isEmpty();
        assertThat(resolver.resolveLabels(null)).isEmpty();

        verify(solrClient, times(0))
                .queryEntities(any(SolrParams.class), any(SolrRequest.METHOD.class));
    }
}
