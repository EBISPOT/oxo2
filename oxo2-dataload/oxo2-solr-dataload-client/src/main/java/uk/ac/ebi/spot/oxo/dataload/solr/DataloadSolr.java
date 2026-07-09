package uk.ac.ebi.spot.oxo.dataload.solr;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.impl.HttpJdkSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.spot.oxo.model.sssom.EntityReference;
import uk.ac.ebi.spot.oxo.model.sssom.Mapping;
import uk.ac.ebi.spot.oxo.model.sssom.MappingEnum;
import uk.ac.ebi.spot.oxo.model.sssom.Uri;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class DataloadSolr {
    private SolrClient solrMappingClient;

    private String solrUrl = System.getenv("SOLR_URL") != null ? System.getenv("SOLR_URL") : "http://localhost:8983/solr";

    private final int connectionTimeoutMillis = envInt("OXO2_SOLR_CONNECT_TIMEOUT_MS", 10000);

    private final int socketTimeoutMillis = envInt("OXO2_SOLR_SOCKET_TIMEOUT_MS", 60000);

    /**
     * Entity details (curie + label) keyed by bare IRI, derived from the asserted
     * {@link Mapping}s in {@link #mappingByIdCache} as they are loaded (see
     * {@link #indexEntityDetails}). Every subject/predicate/object IRI in a Nemo trace
     * appears as an endpoint or predicate of some asserted premise, so this index —
     * populated purely from {@link #prefetchMappingsByIds}/{@link #queryByMappingId} —
     * covers every IRI the chain walker asks about, with no separate Solr round-trip.
     */
    private final Map<String, EntityDetails> entityDetailsByIri = new ConcurrentHashMap<>();

    /**
     * Cache of asserted mappings keyed by their bare {@code mapping_id} (UUID string).
     * Populated by {@link #prefetchMappingsByIds} and read by {@link #queryByMappingId}.
     * Used to recover the provenance (source mapping set, curie/labels) of an asserted
     * premise from the {@code mapping_id} carried in the Nemo trace (ADR-0010).
     */
    private final Map<String, Mapping> mappingByIdCache = new ConcurrentHashMap<>();

    /** {@code mapping_id}s known to have no Solr document, pinned so we don't re-query them. */
    private final Set<String> missingMappingIds = ConcurrentHashMap.newKeySet();

    /**
     * Chunk size for batched prefetch via the Solr {@code terms} query parser.
     * The {@code terms} parser sidesteps {@code maxBooleanClauses}, and the
     * request is sent as POST so Jetty's URI cap doesn't apply — the only real
     * upper bound is Solr's request-body limit (default 2 MB) and per-query
     * overhead, which leaves plenty of room at 20k IRIs per chunk.
     */
    private static final int PREFETCH_CHUNK_SIZE = 20_000;

    private static final Logger logger = LoggerFactory.getLogger(DataloadSolr.class);

    public DataloadSolr() {

        this.solrMappingClient = new  HttpJdkSolrClient.Builder(solrUrl + "/oxo2-mappings")
                .withConnectionTimeout(connectionTimeoutMillis, MILLISECONDS)
                .withIdleTimeout(socketTimeoutMillis, MILLISECONDS)
                .useHttp1_1(true)
                .build();
    }

    private static int envInt(String name, int defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            LoggerFactory.getLogger(DataloadSolr.class)
                    .warn("Invalid integer for env var {}={}, using default {}", name, value, defaultValue);
            return defaultValue;
        }
    }

    /**
     * Look up a single asserted mapping by its bare {@code mapping_id} (UUID string).
     * This is the exact-provenance lookup used by the chain walker (ADR-0010): the
     * {@code mapping_id} carried by an asserted premise in the Nemo trace uniquely
     * identifies the asserted mapping, so cross-set provenance is recovered exactly.
     *
     * @return the asserted {@link Mapping}, or {@code null} if no document matches.
     */
    public Mapping queryByMappingId(String mappingId) {
        if (mappingId == null || mappingId.isBlank()) {
            return null;
        }
        Mapping cached = mappingByIdCache.get(mappingId);
        if (cached != null) {
            return cached;
        }
        if (missingMappingIds.contains(mappingId)) {
            return null;
        }
        try {
            SolrQuery query = new SolrQuery();
            query.setQuery(String.format("{!term f=%s}%s", MappingEnum.MAPPING_ID.getField(), mappingId));
            query.setRows(1);

            QueryResponse response = solrMappingClient.query(query);
            List<Mapping.Builder> builders = response.getBeans(Mapping.Builder.class);
            if (builders.isEmpty()) {
                missingMappingIds.add(mappingId);
                return null;
            }
            Mapping mapping = builders.get(0).build();
            mappingByIdCache.put(mappingId, mapping);
            indexEntityDetails(mapping);
            return mapping;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Bulk-populate {@link #mappingByIdCache} for a set of bare {@code mapping_id}s in
     * batched {@code {!terms f=mapping_id}} queries (sent as POST to dodge the URI cap).
     * After this returns, {@link #queryByMappingId} is a cache hit for every supplied id
     * (or a pinned miss for ids with no document), and {@link #entityDetailsByIri} carries
     * the curie/label of every subject/predicate/object IRI those mappings reference.
     */
    public void prefetchMappingsByIds(Collection<String> mappingIds) {
        if (mappingIds == null || mappingIds.isEmpty()) {
            return;
        }

        Set<String> toFetch = new HashSet<>();
        for (String mappingId : mappingIds) {
            if (mappingId == null || mappingId.isBlank()) continue;
            if (!mappingByIdCache.containsKey(mappingId) && !missingMappingIds.contains(mappingId)) {
                toFetch.add(mappingId);
            }
        }
        if (toFetch.isEmpty()) {
            return;
        }

        List<String> fetchList = new ArrayList<>(toFetch);
        int totalChunks = (fetchList.size() + PREFETCH_CHUNK_SIZE - 1) / PREFETCH_CHUNK_SIZE;
        logger.info("Prefetch mappings by id: {} distinct mapping_ids in {} chunk(s) of up to {}",
                fetchList.size(), totalChunks, PREFETCH_CHUNK_SIZE);
        long batchStart = System.currentTimeMillis();
        for (int i = 0; i < fetchList.size(); i += PREFETCH_CHUNK_SIZE) {
            List<String> chunk = fetchList.subList(i, Math.min(i + PREFETCH_CHUNK_SIZE, fetchList.size()));
            int chunkIndex = (i / PREFETCH_CHUNK_SIZE) + 1;
            long chunkStart = System.currentTimeMillis();
            int docsReturned = fetchMappingIdChunk(chunk);
            logger.info("Prefetch mappings by id: chunk {}/{} ({} ids) -> {} docs in {} ms",
                    chunkIndex, totalChunks, chunk.size(), docsReturned,
                    System.currentTimeMillis() - chunkStart);
        }
        logger.info("Prefetch mappings by id: complete in {} ms", System.currentTimeMillis() - batchStart);
    }

    private int fetchMappingIdChunk(List<String> chunk) {
        try {
            SolrQuery query = new SolrQuery();
            query.setQuery("{!terms f=" + MappingEnum.MAPPING_ID.getField() + "}" + String.join(",", chunk));
            // One document per mapping_id, but size generously to be safe.
            query.setRows(chunk.size() * 2);

            QueryResponse response = solrMappingClient.query(query, SolrRequest.METHOD.POST);
            List<Mapping.Builder> builders = response.getBeans(Mapping.Builder.class);
            for (Mapping.Builder builder : builders) {
                Mapping mapping = builder.build();
                if (mapping.mappingId() != null) {
                    mappingByIdCache.put(mapping.mappingId().toString(), mapping);
                    indexEntityDetails(mapping);
                }
            }
            // Pin a miss for any id with no returned document so we don't re-query it.
            for (String mappingId : chunk) {
                if (!mappingByIdCache.containsKey(mappingId)) {
                    missingMappingIds.add(mappingId);
                }
            }
            return builders.size();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Look up the {@link EntityDetails} (curie + label) for a bare IRI. Served entirely from
     * {@link #entityDetailsByIri}, which is derived from the asserted {@link Mapping}s loaded by
     * {@link #prefetchMappingsByIds}/{@link #queryByMappingId} — no Solr round-trip. Returns an
     * IRI-only {@link EntityDetails} when the IRI was not carried by any loaded asserted mapping.
     */
    public EntityDetails queryEntityDetailsForIRI(String iri) {
        EntityDetails cached = entityDetailsByIri.get(iri);
        if (cached != null) {
            return cached;
        }
        EntityDetails entityDetails = new EntityDetails();
        entityDetails.setIri(iri);
        return entityDetails;
    }

    /**
     * Harvest the subject/predicate/object curie+label of an asserted {@link Mapping} into
     * {@link #entityDetailsByIri}, keyed by the corresponding IRI. Called whenever a mapping
     * enters {@link #mappingByIdCache}. Curie and label are properties of the entity, independent
     * of the role the IRI plays in a mapping, so the index is role-agnostic; the first non-blank
     * value wins, mirroring the fill-from-any-document behaviour of the former entity-details
     * Solr query.
     */
    private void indexEntityDetails(Mapping mapping) {
        mapping.subjectIRI().map(Uri::asStringIRI).ifPresent(iri -> mergeEntityDetails(iri,
                mapping.subjectId().map(EntityReference::getDataAsString).orElse(null),
                mapping.subjectLabel().orElse(null)));
        mapping.predicateIRI().map(Uri::asStringIRI).ifPresent(iri -> mergeEntityDetails(iri,
                mapping.predicateId().map(EntityReference::getDataAsString).orElse(null),
                mapping.predicateLabel().orElse(null)));
        mapping.objectIRI().map(Uri::asStringIRI).ifPresent(iri -> mergeEntityDetails(iri,
                mapping.objectId().map(EntityReference::getDataAsString).orElse(null),
                mapping.objectLabel().orElse(null)));
    }

    private void mergeEntityDetails(String iri, String curie, String label) {
        EntityDetails entityDetails = entityDetailsByIri.computeIfAbsent(iri, key -> {
            EntityDetails created = new EntityDetails();
            created.setIri(iri);
            return created;
        });
        if (!entityDetails.isCuriePresent() && curie != null && !curie.isBlank()) {
            entityDetails.setCurie(curie);
        }
        if (!entityDetails.isLabelPresent() && label != null && !label.isBlank()) {
            entityDetails.setLabel(label);
        }
    }

    public void close() throws Exception {
        solrMappingClient.close();
    }
}
