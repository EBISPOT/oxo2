package uk.ac.ebi.spot.oxo.backend.service.export;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import uk.ac.ebi.spot.oxo.backend.service.OxOSolrClient;
import uk.ac.ebi.spot.oxo.model.sssom.EntityReference;
import uk.ac.ebi.spot.oxo.model.sssom.Uri;

import java.io.StringWriter;
import java.io.Writer;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MappingExportStreamingTest {
    private OxOSolrClient client;
    private MappingTsvExporter exporter;

    @BeforeEach
    void setUp() {
        client = mock(OxOSolrClient.class);
        exporter = new MappingTsvExporter();
        ReflectionTestUtils.setField(exporter, "solrClient", client);
    }

    @Test
    void headerUsesTheCompleteSearchAndOnlyPrefixesUsedInItsRows() throws Exception {
        SolrQuery search = search();
        List<SolrQuery> queries = new ArrayList<>();
        when(client.queryMappings(any(SolrParams.class))).thenAnswer(invocation -> {
            SolrQuery query = invocation.getArgument(0);
            queries.add(query.getCopy());
            if (query.getBool("facet", false)) {
                QueryResponse response = prefixes();
                // The unrelated mapping is outside q, but inside the same filter domain.
                if (!"diabetes".equals(query.getQuery())) {
                    response.getFacetField("object_prefix").add("HP", 1);
                }
                return response;
            }
            return page("*", row(1));
        });
        StringWriter output = new StringWriter();
        exporter.stream(search, ExportFormat.SSSOM_TSV, output);

        assertThat(prefixNames(output.toString())).containsExactly("DOID", "EFO", "SEMAPV", "SKOS");
        assertThat(queries.getFirst().getQuery()).isEqualTo("diabetes");
        assertThat(queries.getFirst().get("qf")).isEqualTo("subject_label");
        assertThat(queries.getFirst().get("defType")).isEqualTo("edismax");
        assertThat(queries.getFirst().getFilterQueries()).containsExactly("inference_type:ASSERTED");
        assertThat(output.toString()).contains("DOID:export-test-1\tdiabetes\tSKOS:exactMatch");
        assertThat(search.getRows()).isNull(); // Export must not mutate its caller's query.
    }

    @Test
    void streamingAcrossBatchesDoesNotRetainIdentifiersInSharedCaches() throws Exception {
        Map<?, ?> entityCache = (Map<?, ?>) ReflectionTestUtils.getField(EntityReference.class, "uriOrCurieToStringMap");
        Map<?, ?> uriCache = (Map<?, ?>) ReflectionTestUtils.getField(Uri.class, "stringToUriMap");
        Map<?, ?> entitiesBefore = new HashMap<>(entityCache);
        Map<?, ?> urisBefore = new HashMap<>(uriCache);
        SolrDocument[] firstBatch = new SolrDocument[2000];
        for (int index = 0; index < firstBatch.length; index++) firstBatch[index] = row(index);
        QueryResponse facets = prefixes();
        when(client.queryMappings(any(SolrParams.class)))
                .thenReturn(facets, page("next", firstBatch), page("last", row(2000)), page("last"));
        StringWriter output = new StringWriter();
        exporter.stream(search(), ExportFormat.SSSOM_TSV, output);

        assertThat(output.toString().lines().filter(line -> line.startsWith("DOID:")).count()).isEqualTo(2001);
        assertThat(output.toString()).contains("DOID:export-test-2000");
        assertThat(entityCache.size()).isEqualTo(entitiesBefore.size());
        assertThat(uriCache.size()).isEqualTo(urisBefore.size());
        assertThat(entityCache).isEqualTo(entitiesBefore);
        assertThat(uriCache).isEqualTo(urisBefore);
    }

    @Test
    void emptyExportHasAnEmptyMapAndNoUnconditionalPrefixes() throws Exception {
        when(client.queryMappings(any(SolrParams.class))).thenReturn(mock(QueryResponse.class), page("*"));
        StringWriter output = new StringWriter();
        exporter.stream(search(), ExportFormat.SSSOM_TSV, output);
        assertThat(output.toString()).startsWith("# curie_map: {}\n");
        assertThat(prefixNames(output.toString())).isEmpty();
    }

    @Test
    void nonStandardPredicateAndCurieMappingSetPrefixesAreIncluded() throws Exception {
        QueryResponse facets = prefixes();
        when(facets.getFacetField("predicate_id")).thenReturn(facet("predicate_id", "BFO:0000050"));
        when(facets.getFacetField("mapping_set_id")).thenReturn(facet("mapping_set_id", "DOI:10.1234/test"));
        SolrDocument document = row(1);
        document.setField("predicate_id", "BFO:0000050");
        document.setField("mapping_set_id", "DOI:10.1234/test");
        when(client.queryMappings(any(SolrParams.class))).thenReturn(facets, page("*", document));
        StringWriter output = new StringWriter();
        exporter.stream(search(), ExportFormat.SSSOM_TSV, output);
        assertThat(prefixNames(output.toString())).containsExactly("BFO", "DOI", "DOID", "EFO", "SEMAPV");
    }

    @Test
    void partialPrefixResponseFailsBeforeWritingTheFile() throws Exception {
        QueryResponse facets = prefixes();
        NamedList<Object> header = new NamedList<>();
        header.add("partialResults", true);
        doReturn(header).when(facets).getResponseHeader();
        when(client.queryMappings(any(SolrParams.class))).thenReturn(facets, page("*"));
        StringWriter output = new StringWriter();
        assertThatThrownBy(() -> exporter.stream(search(), ExportFormat.SSSOM_TSV, output))
                .hasMessageContaining("partial");
        assertThat(output.toString()).isEmpty();
    }

    @Test
    void missingCursorIsAnErrorInsteadOfASuccessfulTruncatedExport() throws Exception {
        QueryResponse facets = prefixes();
        when(client.queryMappings(any(SolrParams.class))).thenReturn(facets, page(null, row(1)));
        assertThatThrownBy(() -> exporter.stream(search(), ExportFormat.SSSOM_TSV, Writer.nullWriter()))
                .hasMessageContaining("cursor");
    }

    @Test
    void customPrefixesComeFromContributingSetsWithoutPollutingTheGlobalMap() throws Exception {
        QueryResponse facets = prefixes();
        when(facets.getFacetField("subject_prefix")).thenReturn(facet("subject_prefix", "CUSTOM_EXPORT"));
        SolrDocument document = row(1);
        document.setField("subject_id", "CUSTOM_EXPORT:1");
        SolrDocument source = new SolrDocument();
        source.setField("curie_map", "custom_export:https://example.org/custom/, UNUSED:https://example.org/unused/");
        when(client.queryMappings(any(SolrParams.class))).thenReturn(facets, page("*", document));
        when(client.queryMappingSets(any(SolrParams.class))).thenAnswer(invocation -> {
            SolrQuery query = invocation.getArgument(0);
            assertThat(query.getQuery()).isEqualTo("mapping_set_id:"
                    + ClientUtils.escapeQueryChars("https://example.org/export-test"));
            return page(null, source);
        });
        Map<String, String> globalBefore = new HashMap<>(uk.ac.ebi.spot.oxo.model.sssom.PrefixMap.getPrefixMap());
        StringWriter output = new StringWriter();
        exporter.stream(search(), ExportFormat.SSSOM_TSV, output);
        assertThat(prefixNames(output.toString())).containsExactly("CUSTOM_EXPORT", "EFO", "SEMAPV", "SKOS");
        assertThat(output.toString()).contains("'CUSTOM_EXPORT': 'https://example.org/custom/'");
        assertThat(uk.ac.ebi.spot.oxo.model.sssom.PrefixMap.getPrefixMap()).isEqualTo(globalBefore);
    }

    @Test
    void unresolvedPrefixFailsBeforeWritingAnInvalidFile() throws Exception {
        QueryResponse facets = prefixes();
        when(facets.getFacetField("subject_prefix")).thenReturn(facet("subject_prefix", "UNDECLARED_EXPORT"));
        when(client.queryMappings(any(SolrParams.class))).thenReturn(facets);
        when(client.queryMappingSets(any(SolrParams.class))).thenReturn(page(null));
        StringWriter output = new StringWriter();
        assertThatThrownBy(() -> exporter.stream(search(), ExportFormat.SSSOM_TSV, output))
                .hasMessageContaining("UNDECLARED_EXPORT");
        assertThat(output.toString()).isEmpty();
    }

    @Test
    void canonicalNamespacesTakePrecedenceOverRegistryAliases() throws Exception {
        QueryResponse facets = prefixes();
        when(facets.getFacetField("subject_prefix")).thenReturn(facet("subject_prefix", "MESH"));
        SolrDocument document = row(1);
        document.setField("subject_id", "MESH:D020176");
        when(client.queryMappings(any(SolrParams.class))).thenReturn(facets, page("*", document));
        StringWriter output = new StringWriter();
        exporter.stream(search(), ExportFormat.SSSOM_TSV, output);
        assertThat(output.toString()).contains("'MESH': 'http://id.nlm.nih.gov/mesh/'");
    }

    @Test
    void partialRowBatchIsRejectedEvenWithAnUnchangedCursor() throws Exception {
        QueryResponse facets = prefixes();
        QueryResponse rows = page("*", row(1));
        NamedList<Object> header = new NamedList<>();
        header.add("partialResults", true);
        rows.getResponse().add("responseHeader", header);
        rows.setResponse(rows.getResponse());
        when(client.queryMappings(any(SolrParams.class))).thenReturn(facets, rows);
        StringWriter output = new StringWriter();
        assertThatThrownBy(() -> exporter.stream(search(), ExportFormat.SSSOM_TSV, output))
                .hasMessageContaining("partial");
        assertThat(output.toString()).doesNotContain("DOID:export-test-1");
    }

    @Test
    void disconnectedServletWriterStopsFurtherSolrBatches() throws Exception {
        QueryResponse facets = prefixes();
        when(client.queryMappings(any(SolrParams.class))).thenReturn(facets, page("next", row(1)));
        Writer broken = new Writer() {
            public void write(char[] chars, int offset, int length) throws IOException { throw new IOException("disconnected"); }
            public void flush() throws IOException { throw new IOException("disconnected"); }
            public void close() {}
        };
        assertThatThrownBy(() -> exporter.stream(search(), ExportFormat.SSSOM_TSV, new PrintWriter(broken)))
                .hasMessageContaining("connection closed");
        verify(client, times(2)).queryMappings(any(SolrParams.class));
    }

    private static SolrQuery search() {
        SolrQuery query = new SolrQuery("diabetes");
        query.set("defType", "edismax");
        query.set("qf", "subject_label");
        query.addFilterQuery("inference_type:ASSERTED");
        query.setSort("id", SolrQuery.ORDER.asc);
        return query;
    }

    private static List<String> prefixNames(String tsv) {
        return tsv.lines().filter(line -> line.startsWith("#   "))
                .map(line -> line.substring(4, line.indexOf(":", 4)).replace("'", "")).toList();
    }

    private static QueryResponse prefixes() {
        QueryResponse response = mock(QueryResponse.class);
        when(response.getFacetField("subject_prefix")).thenReturn(facet("subject_prefix", "DOID"));
        when(response.getFacetField("object_prefix")).thenReturn(facet("object_prefix", "EFO"));
        when(response.getFacetField("predicate_id")).thenReturn(facet("predicate_id", "SKOS:exactMatch"));
        when(response.getFacetField("mapping_justification"))
                .thenReturn(facet("mapping_justification", "SEMAPV:LexicalMatching"));
        when(response.getFacetField("mapping_set_id"))
                .thenReturn(facet("mapping_set_id", "https://example.org/export-test"));
        return response;
    }

    private static FacetField facet(String name, String value) {
        FacetField facet = new FacetField(name);
        facet.add(value, 1);
        return facet;
    }

    private static SolrDocument row(int index) {
        SolrDocument doc = new SolrDocument();
        doc.setField("subject_id", "DOID:export-test-" + index);
        doc.setField("subject_label", "diabetes");
        doc.setField("predicate_id", "SKOS:exactMatch");
        doc.setField("object_id", "EFO:export-test-" + index);
        doc.setField("mapping_justification", "SEMAPV:LexicalMatching");
        doc.setField("mapping_set_id", "https://example.org/export-test");
        return doc;
    }

    private static QueryResponse page(String nextCursor, SolrDocument... documents) {
        SolrDocumentList rows = new SolrDocumentList();
        rows.addAll(List.of(documents));
        rows.setNumFound(documents.length);
        NamedList<Object> values = new NamedList<>();
        values.add("response", rows);
        values.add("nextCursorMark", nextCursor);
        return new QueryResponse(values, null);
    }
}
