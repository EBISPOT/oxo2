package uk.ac.ebi.spot.oxo.backend.service;

import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.response.OntologySummary;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OntologyCatalogTest {
    private OxOSolrClient solrClient;
    private OntologyCatalog catalog;
    private QueryResponse mappings;
    private QueryResponse entities;
    private QueryResponse mappingSets;

    @BeforeEach
    void setUp() throws Exception {
        solrClient = mock(OxOSolrClient.class);
        catalog = new OntologyCatalog(solrClient);
        mappings = mock(QueryResponse.class);
        FacetField subjects = new FacetField("subject_prefix");
        subjects.add("EFO", 12);
        FacetField objects = new FacetField("object_prefix");
        objects.add("MONDO", 8);
        when(mappings.getFacetField("subject_prefix")).thenReturn(subjects);
        when(mappings.getFacetField("object_prefix")).thenReturn(objects);
        entities = mock(QueryResponse.class);
        // An empty pivot is valid for a legacy index without namespace metadata.
        when(entities.getFacetPivot()).thenReturn(new NamedList<>());
        mappingSets = mock(QueryResponse.class);
        SolrDocumentList documents = new SolrDocumentList();
        SolrDocument document = new SolrDocument();
        document.setField("prefix", "EFO");
        document.setField("ontology_iri", "http://www.ebi.ac.uk/efo/efo.owl");
        documents.add(document);
        documents.setNumFound(1);
        when(mappingSets.getResults()).thenReturn(documents);
        when(solrClient.queryMappings(any(SolrParams.class))).thenReturn(mappings);
        when(solrClient.queryEntities(any(SolrParams.class))).thenReturn(entities);
        when(solrClient.queryMappingSets(any(SolrParams.class))).thenReturn(mappingSets);
    }

    @Test
    void repeatedRequestsReuseTheCompleteImmutableResponse() throws Exception {
        List<OntologySummary> first = catalog.allOntologies();
        for (int request = 0; request < 100; request++) {
            assertThat(catalog.allOntologies()).isSameAs(first);
        }
        assertThat(first).hasSize(2);
        assertThat(first.getFirst().uri()).isEqualTo("http://www.ebi.ac.uk/efo/efo.owl");
        assertThatThrownBy(first::clear).isInstanceOf(UnsupportedOperationException.class);
        verifyOneLoad();
    }

    @Test
    void concurrentColdRequestsShareOneLoad() throws Exception {
        CountDownLatch loadEntered = new CountDownLatch(1);
        CountDownLatch releaseLoad = new CountDownLatch(1);
        when(solrClient.queryMappings(any(SolrParams.class))).thenAnswer(invocation -> {
            loadEntered.countDown();
            assertThat(releaseLoad.await(5, TimeUnit.SECONDS)).isTrue();
            return mappings;
        });
        try (var executor = Executors.newFixedThreadPool(12)) {
            List<Future<List<OntologySummary>>> requests = new ArrayList<>();
            try {
                for (int request = 0; request < 12; request++) {
                    requests.add(executor.submit(catalog::allOntologies));
                }
                assertThat(loadEntered.await(5, TimeUnit.SECONDS)).isTrue();
            } finally {
                releaseLoad.countDown();
            }
            List<OntologySummary> first = requests.getFirst().get(5, TimeUnit.SECONDS);
            for (Future<List<OntologySummary>> request : requests) {
                assertThat(request.get(5, TimeUnit.SECONDS)).isSameAs(first);
            }
        }
        verifyOneLoad();
    }

    @Test
    void failedLoadCanBeRetried() throws Exception {
        when(solrClient.queryMappings(any(SolrParams.class)))
                .thenThrow(new IllegalStateException("Solr is starting"))
                .thenReturn(mappings);
        assertThatThrownBy(catalog::allOntologies).hasMessage("Solr is starting");
        assertThat(catalog.isWarm()).isFalse();
        assertThat(catalog.allOntologies()).hasSize(2);
        assertThat(catalog.isWarm()).isTrue();
        verify(solrClient, times(2)).queryMappings(any(SolrParams.class));
    }

    @Test
    void emptyIndexDoesNotBecomeAPermanentEmptyCatalog() throws Exception {
        QueryResponse empty = mock(QueryResponse.class);
        when(solrClient.queryMappings(any(SolrParams.class))).thenReturn(empty, mappings);
        assertThat(catalog.allOntologies()).isEmpty();
        assertThat(catalog.isWarm()).isFalse();
        assertThat(catalog.allOntologies()).hasSize(2);
        assertThat(catalog.isWarm()).isTrue();
    }

    @Test
    void failedEnrichmentServesCountsButIsRetried() throws Exception {
        when(solrClient.queryEntities(any(SolrParams.class)))
                .thenThrow(new IllegalStateException("Entity core is starting"))
                .thenReturn(entities);
        when(solrClient.queryMappingSets(any(SolrParams.class)))
                .thenThrow(new IllegalStateException("Mapping-set core is starting"))
                .thenReturn(mappingSets);
        assertThat(catalog.allOntologies().getFirst().uri()).isNull();
        assertThat(catalog.isWarm()).isFalse();
        assertThat(catalog.allOntologies().getFirst().uri()).isNotNull();
        assertThat(catalog.isWarm()).isTrue();
        verify(solrClient, times(2)).queryEntities(any(SolrParams.class));
        verify(solrClient, times(2)).queryMappingSets(any(SolrParams.class));
    }

    @Test
    void partialCountsAreRejectedAndRetried() throws Exception {
        QueryResponse partial = mock(QueryResponse.class);
        NamedList<Object> header = new NamedList<>();
        header.add("partialResults", true);
        doReturn(header).when(partial).getResponseHeader();
        when(solrClient.queryMappings(any(SolrParams.class))).thenReturn(partial, mappings);
        assertThatThrownBy(catalog::allOntologies).hasMessageContaining("Incomplete");
        assertThat(catalog.isWarm()).isFalse();
        assertThat(catalog.allOntologies()).hasSize(2);
    }

    @Test
    void partialEnrichmentIsNotCached() throws Exception {
        NamedList<Object> header = new NamedList<>();
        header.add("partialResults", true);
        doReturn(header, null).when(entities).getResponseHeader();
        catalog.allOntologies();
        assertThat(catalog.isWarm()).isFalse();
        catalog.allOntologies();
        assertThat(catalog.isWarm()).isTrue();
    }

    @Test
    void truncatedOntologyMetadataIsNotCached() throws Exception {
        mappingSets.getResults().setNumFound(10_001);
        catalog.allOntologies();
        assertThat(catalog.isWarm()).isFalse();
        mappingSets.getResults().setNumFound(1);
        catalog.allOntologies();
        assertThat(catalog.isWarm()).isTrue();
    }

    @Test
    void newBackendInstanceLoadsTheNewRelease() throws Exception {
        List<OntologySummary> oldRelease = catalog.allOntologies();
        FacetField newSubjects = new FacetField("subject_prefix");
        newSubjects.add("EFO", 42);
        when(mappings.getFacetField("subject_prefix")).thenReturn(newSubjects);
        OntologyCatalog restarted = new OntologyCatalog(solrClient);
        assertThat(restarted.isWarm()).isFalse();
        assertThat(restarted.allOntologies().getFirst().asSubject()).isEqualTo(42);
        assertThat(catalog.allOntologies()).isSameAs(oldRelease);
        assertThat(oldRelease.getFirst().asSubject()).isEqualTo(12);
        verify(solrClient, times(2)).queryMappings(any(SolrParams.class));
    }

    @Test
    void shutdownInterruptsAnActiveWarmup() throws Exception {
        CountDownLatch loadEntered = new CountDownLatch(1);
        CountDownLatch loadInterrupted = new CountDownLatch(1);
        when(solrClient.queryMappings(any(SolrParams.class))).thenAnswer(invocation -> {
            loadEntered.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException shutdown) {
                loadInterrupted.countDown();
                throw shutdown;
            }
            return mappings;
        });
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(OntologyCatalogWarmup.class,
                    () -> new OntologyCatalogWarmup(catalog, 30_000));
            context.refresh();
            context.publishEvent(new ApplicationReadyEvent(new SpringApplication(Object.class),
                    new String[0], context, Duration.ZERO));
            assertThat(loadEntered.await(5, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(loadInterrupted.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(catalog.isWarm()).isFalse();
    }

    @Test
    void applicationReadyWarmsAndRetriesWithoutAnHttpRequest() throws Exception {
        CountDownLatch readyToLoad = new CountDownLatch(1);
        when(solrClient.queryMappings(any(SolrParams.class)))
                .thenThrow(new IllegalStateException("Solr is starting"))
                .thenAnswer(invocation -> {
                    readyToLoad.countDown();
                    return mappings;
                });
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(OntologyCatalogWarmup.class,
                    () -> new OntologyCatalogWarmup(catalog, 10));
            context.refresh();
            context.publishEvent(new ApplicationReadyEvent(new SpringApplication(Object.class),
                    new String[0], context, Duration.ZERO));
            assertThat(readyToLoad.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(catalog.isWarm()).isTrue();
            catalog.allOntologies();
            verify(solrClient, times(2)).queryMappings(any(SolrParams.class));
            verify(solrClient).queryEntities(any(SolrParams.class));
            verify(solrClient).queryMappingSets(any(SolrParams.class));
        }
    }

    private void verifyOneLoad() throws Exception {
        verify(solrClient).queryMappings(any(SolrParams.class));
        verify(solrClient).queryEntities(any(SolrParams.class));
        verify(solrClient).queryMappingSets(any(SolrParams.class));
    }
}
