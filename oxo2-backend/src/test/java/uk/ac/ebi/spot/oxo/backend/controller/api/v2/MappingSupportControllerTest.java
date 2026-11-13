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
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.ac.ebi.spot.oxo.backend.service.OxOSolrClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MappingSupportController.class)
class MappingSupportControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean OxOSolrClient client;

    private QueryResponse lookup(String key) {
        SolrDocumentList docs = new SolrDocumentList();
        if (key != null) {
            SolrDocument doc = new SolrDocument();
            doc.setField("spo_key", key);
            docs.add(doc);
        }
        docs.setNumFound(docs.size());
        NamedList<Object> body = new NamedList<>();
        body.add("response", docs);
        QueryResponse response = new QueryResponse();
        response.setResponse(body);
        return response;
    }

    private NamedList<Object> buckets(String... values) {
        NamedList<Object> result = new NamedList<>();
        result.add("buckets", java.util.Arrays.stream(values).map(value -> {
            NamedList<Object> bucket = new NamedList<>();
            bucket.add("val", value);
            bucket.add("count", 1L);
            return bucket;
        }).toList());
        return result;
    }

    @Test void missingMappingIs404() throws Exception {
        when(client.queryMappings(any())).thenReturn(lookup(null));
        mvc.perform(get("/api/v2/mappings/missing/support")).andExpect(status().isNotFound());
        verify(client, never()).querySssomMappings(any(), any());
    }

    @Test void rejectsInvalidAndOverflowingPaginationBeforeQuerying() throws Exception {
        for (String paging : List.of("page=-1", "size=0", "size=101", "page=2147483647&size=100")) {
            mvc.perform(get("/api/v2/mappings/m/support?" + paging)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(client);
    }

    @Test void inferredOnlyGroupHasZeroAssertedSourcesWhenSolrOmitsTheNestedFacet() throws Exception {
        when(client.queryMappings(any())).thenReturn(lookup("fbe10a2b-8ecd-3839-bd48-0228e7dbb405"));
        // Actual Solr response for the reported inferred mapping: an empty query domain has a
        // count but no nested "sources" facet, rather than a facet with an empty buckets array.
        NamedList<Object> asserted = new NamedList<>();
        asserted.add("count", 0L);
        NamedList<Object> facets = new NamedList<>();
        facets.add("count", 1L);
        facets.add("methods", buckets("SEMAPV:MappingChaining"));
        facets.add("asserted", asserted);
        NamedList<Object> body = new NamedList<>();
        body.add("facets", facets);
        QueryResponse response = new QueryResponse();
        response.setResponse(body);
        when(client.querySssomMappings(any(), any())).thenReturn(new OxOSolrClient.SssomMappingResult(
                new PageImpl<>(List.of(), PageRequest.of(0, 20), 1), response));

        mvc.perform(get("/api/v2/mappings/ca236ada-08fd-3887-b551-e6283f613cb2/support?page=0&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mappings.totalElements").value(1))
                .andExpect(jsonPath("$.justificationMethodCount").value(1))
                .andExpect(jsonPath("$.assertedSourceCount").value(0));
    }

    @Test void pagesTheExactIndexedGroupWithoutSearchVisibilityFilters() throws Exception {
        when(client.queryMappings(any())).thenReturn(lookup("literal-and-negation-safe-key"));
        NamedList<Object> asserted = new NamedList<>();
        asserted.add("count", 25L);
        asserted.add("sources", buckets("set-a", "set-b", "set-c", "", " "));
        NamedList<Object> facets = new NamedList<>();
        facets.add("count", 26L);
        facets.add("methods", buckets("semapv:ManualMappingCuration", "semapv:LexicalMatching", ""));
        facets.add("asserted", asserted);
        NamedList<Object> body = new NamedList<>();
        body.add("facets", facets);
        QueryResponse response = new QueryResponse();
        response.setResponse(body);
        when(client.querySssomMappings(any(), any())).thenReturn(new OxOSolrClient.SssomMappingResult(
                new PageImpl<>(List.of(), PageRequest.of(1, 20), 26), response));
        mvc.perform(get("/api/v2/mappings/m/support?page=1&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mappings.totalElements").value(26))
                .andExpect(jsonPath("$.mappings.number").value(1))
                .andExpect(jsonPath("$.justificationMethodCount").value(2))
                .andExpect(jsonPath("$.assertedSourceCount").value(3));
        ArgumentCaptor<SolrParams> query = ArgumentCaptor.forClass(SolrParams.class);
        verify(client).querySssomMappings(query.capture(), any(Pageable.class));
        SolrQuery actual = (SolrQuery) query.getValue();
        assertThat(actual.getQuery()).isEqualTo("spo_key:\"literal\\-and\\-negation\\-safe\\-key\"");
        assertThat(actual.getFilterQueries()).isNull();
        assertThat(actual.getStart()).isEqualTo(20);
        assertThat(actual.getSortField()).isEqualTo("score desc,id asc");
        assertThat(actual.getBool("expand", false)).isFalse();
    }
}
