package uk.ac.ebi.spot.oxo.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.beans.DocumentObjectBinder;
import org.apache.solr.client.solrj.impl.HttpJdkSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.SolrParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.response.MappingSearchResponse;
import uk.ac.ebi.spot.oxo.backend.service.helper.SolrConstants;
import uk.ac.ebi.spot.oxo.model.sssom.Mapping;
import uk.ac.ebi.spot.oxo.model.sssom.MappingConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

@Service
public class OxOSolrClient {
    private SolrClient solrMappingClient;
    private SolrClient solrMappingSetClient;
    private SolrClient solrEntityClient;

    @Value("${solr.url}")
    private String solrUrl;

    @Value("${connectionTimeoutMillis}")
    private int connectionTimeoutMillis;

    @Value("${socketTimeoutMillis}")
    private int socketTimeoutMillis;

    // The app-configured Jackson mapper (Optionals unwrapped, same as response serialisation), used
    // to build the group_members JSON string on a grouped query's representative (ADR-0013).
    @Autowired
    private ObjectMapper objectMapper;

    private static final Logger logger = LoggerFactory.getLogger(OxOSolrClient.class);

    @PostConstruct
    public void init() {
        logger.debug("Init solrMappingClient: oxoSolrUrl={}", solrUrl);
        this.solrMappingClient = new  HttpJdkSolrClient.Builder(solrUrl + "/oxo2-mappings")
                .withConnectionTimeout(connectionTimeoutMillis, MILLISECONDS)
                .withIdleTimeout(socketTimeoutMillis, MILLISECONDS)
                .build();
        this.solrMappingSetClient = new HttpJdkSolrClient.Builder(solrUrl + "/oxo2-mappingsets")
                .withConnectionTimeout(connectionTimeoutMillis, MILLISECONDS)
                .withIdleTimeout(socketTimeoutMillis, MILLISECONDS)
                .build();
        this.solrEntityClient = new HttpJdkSolrClient.Builder(solrUrl + "/oxo2-entities")
                .withConnectionTimeout(connectionTimeoutMillis, MILLISECONDS)
                .withIdleTimeout(socketTimeoutMillis, MILLISECONDS)
                .build();
    }

    public QueryResponse queryMappingSets(SolrParams params) throws Exception {
        return solrMappingSetClient.query(params);
    }

    /**
     * Raw query against the oxo2-entities collection — the per-entity typeahead read model (ADR-0034).
     * Returns the {@link QueryResponse} directly: an entity document is not a {@link Mapping} and has
     * no bean, and the suggest endpoint maps it straight to its DTO.
     */
    public QueryResponse queryEntities(SolrParams params) throws Exception {
        return solrEntityClient.query(params);
    }

    /**
     * Raw query against the oxo2-mappings collection, for callers that read the {@link QueryResponse}
     * directly rather than as a page of {@link Mapping} beans — e.g. the /api/v2/ontologies facet
     * counts over subject_prefix / object_prefix (ADR-0024).
     */
    public QueryResponse queryMappings(SolrParams params) throws Exception {
        return solrMappingClient.query(params);
    }

    public MappingSearchResponse query(SolrParams params, Pageable pageable) throws Exception {
        QueryResponse response = solrMappingClient.query(params);

        // A same-SPO collapse query (ADR-0023) sets expand=true; its representatives come back as a
        // flat result with the expanded members alongside, so it takes the collapse-aware path.
        boolean collapsed = params.getBool(SolrConstants.EXPAND, false);
        Page<Mapping> mappingPage = collapsed
                ? buildCollapsedPage(response, pageable)
                : buildFlatPage(response, pageable);

        return new MappingSearchResponse(mappingPage);
    }

    /**
     * Page of mappings plus the raw {@link QueryResponse}, so the SSSOM-API surface (ADR-0032) can read
     * the facet/stats components that fill its envelope's {@code facets} block off the same query — one
     * round trip settles both the page and the facets.
     */
    public record SssomMappingResult(Page<Mapping> page, QueryResponse response) {
    }

    /**
     * Run a SSSOM-API mapping query, returning the collapsed-or-flat page (the same page-building the
     * v2 search uses) together with the {@link QueryResponse} carrying the facets.
     */
    public SssomMappingResult querySssomMappings(SolrParams params, Pageable pageable) throws Exception {
        QueryResponse response = solrMappingClient.query(params);
        boolean collapsed = params.getBool(SolrConstants.EXPAND, false);
        Page<Mapping> mappingPage = collapsed
                ? buildCollapsedPage(response, pageable)
                : buildFlatPage(response, pageable);
        return new SssomMappingResult(mappingPage, response);
    }

    private Page<Mapping> buildFlatPage(QueryResponse response, Pageable pageable) {
        List<Mapping> mappings = response.getBeans(Mapping.Builder.class).stream()
                .map(Mapping.Builder::build)
                .collect(Collectors.toList());
        return new PageImpl<>(mappings, pageable, response.getResults().getNumFound());
    }

    /**
     * Build a page of collapse representatives (ADR-0023, superseding ADR-0013's result grouping).
     * Each representative is its spo_key group's highest inference-tier document (via the collapse
     * score sort); {@code response.getResults()} is the flat list of representatives whose
     * {@code numFound} is the exact group count, and {@code response.getExpandedResults()} carries
     * each representative's other members keyed by spo_key. The representative leads its own
     * group_members list ({@code {"total":N,"members":[...]}}, mirroring ADR-0013) so the detail view
     * still shows it first; the true group size is one (the representative) plus the expanded member
     * count, which can exceed the inlined cap. The page total is the group count, so pagination
     * counts triples, not documents.
     */
    private Page<Mapping> buildCollapsedPage(QueryResponse response, Pageable pageable) throws Exception {
        DocumentObjectBinder binder = solrMappingClient.getBinder();
        SolrDocumentList representativeDocs = response.getResults();
        Map<String, SolrDocumentList> expandedBySpoKey = response.getExpandedResults();

        List<Mapping> representatives = new ArrayList<>();
        for (SolrDocument representativeDoc : representativeDocs) {
            Mapping.Builder representativeBuilder = binder.getBean(Mapping.Builder.class, representativeDoc);
            String spoKey = String.valueOf(representativeDoc.getFieldValue(MappingConstants.SPO_KEY));

            // group_members leads with the representative itself, then its expanded members.
            List<Mapping> members = new ArrayList<>();
            members.add(representativeBuilder.build());
            long groupSize = 1;
            SolrDocumentList expandedMembers =
                    expandedBySpoKey != null ? expandedBySpoKey.get(spoKey) : null;
            if (expandedMembers != null) {
                binder.getBeans(Mapping.Builder.class, expandedMembers).stream()
                        .map(Mapping.Builder::build)
                        .forEach(members::add);
                groupSize += expandedMembers.getNumFound();
            }

            String groupMembers = serializeGroupMembers(members, groupSize);
            representatives.add(representativeBuilder.groupMembersAsString(groupMembers).build());
        }
        return new PageImpl<>(representatives, pageable, representativeDocs.getNumFound());
    }

    private String serializeGroupMembers(List<Mapping> members, long total) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("total", total);
        root.set("members", objectMapper.valueToTree(members));
        return objectMapper.writeValueAsString(root);
    }

    /**
     * Close every client. This used to close only the mapping client, leaking the mapping-set one;
     * with a third collection (ADR-0034) that omission would leak two. Each close is guarded so one
     * failure cannot strand the others.
     */
    @PreDestroy
    public void close() {
        closeQuietly(solrMappingClient, "oxo2-mappings");
        closeQuietly(solrMappingSetClient, "oxo2-mappingsets");
        closeQuietly(solrEntityClient, "oxo2-entities");
    }

    private static void closeQuietly(SolrClient client, String collection) {
        if (client == null) {
            return;
        }
        try {
            client.close();
        } catch (Exception closeFailure) {
            logger.warn("Failed to close the Solr client for {}", collection, closeFailure);
        }
    }
}
