package uk.ac.ebi.spot.oxo.dataload.solr;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.impl.HttpJdkSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.spot.oxo.model.sssom.Mapping;
import uk.ac.ebi.spot.oxo.model.sssom.MappingEnum;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class DataloadSolr {
    private SolrClient solrMappingClient;

    private String solrUrl = System.getenv("SOLR_URL") != null ? System.getenv("SOLR_URL") : "http://localhost:8983/solr";

    private final int connectionTimeoutMillis = envInt("OXO2_SOLR_CONNECT_TIMEOUT_MS", 10000);

    private final int socketTimeoutMillis = envInt("OXO2_SOLR_SOCKET_TIMEOUT_MS", 60000);

    private final Map<String, EntityDetails> entityDetailsCache = new ConcurrentHashMap<>();

    private final Map<String, List<Mapping>> spoMappingsCache = new ConcurrentHashMap<>();

    /**
     * Chunk size for batched prefetch via the Solr {@code terms} query parser.
     * The {@code terms} parser sidesteps {@code maxBooleanClauses}, and the
     * request is sent as POST so Jetty's URI cap doesn't apply — the only real
     * upper bound is Solr's request-body limit (default 2 MB) and per-query
     * overhead, which leaves plenty of room at 20k IRIs per chunk.
     */
    private static final int PREFETCH_CHUNK_SIZE = 20_000;

    /**
     * Smaller chunk for the SPO prefetch — each subject can have multiple
     * Mapping documents, so we keep the response size manageable.
     */
    private static final int PREFETCH_TRIPLES_CHUNK_SIZE = 5_000;

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

    public List<Mapping> querySubjectPredicateObjectIRI(String subjectIRI, String predicateIRI, String objectIRI)  {
        String cacheKey = subjectIRI + '\0' + predicateIRI + '\0' + objectIRI;
        List<Mapping> cached = spoMappingsCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        try {
            SolrQuery query = new SolrQuery();

            query.setQuery(String.format("{!term f=%s}%s", MappingEnum.SUBJECT_IRI.getField(),subjectIRI));
            query.addFilterQuery(String.format("{!term f=%s}%s", MappingEnum.PREDICATE_IRI.getField(), predicateIRI));
            query.addFilterQuery(String.format("{!term f=%s}%s", MappingEnum.OBJECT_IRI.getField(), objectIRI));

            QueryResponse response = solrMappingClient.query(query);

            List<Mapping.Builder> mappingBuilders = response.getBeans(Mapping.Builder.class);
            List<Mapping> mappings = mappingBuilders.stream()
                    .map(Mapping.Builder::build)
                    .collect(Collectors.toList());

            spoMappingsCache.put(cacheKey, mappings);
            return mappings;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public EntityDetails queryEntityDetailsForIRI(String iriField, String iriValue, String curieField, String labelField)  {
        String cacheKey = iriField + '\0' + iriValue;
        EntityDetails cached = entityDetailsCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        try {
            SolrQuery query = new SolrQuery();
            query.setQuery(String.format("%s:\"%s\"",iriField, iriValue));
            query.setFields(curieField, iriField, labelField);

            QueryResponse response = solrMappingClient.query(query);

            SolrDocumentList docs = response.getResults();

            EntityDetails entityDetails = new EntityDetails();
            entityDetails.setIri(iriValue);

            for (SolrDocument doc : docs) {
                if (!entityDetails.isCuriePresent() && doc.getFieldValue(curieField) != null) {
                    entityDetails.setCurie(doc.getFieldValue(curieField).toString());
                }
                if (!entityDetails.isLabelPresent() && doc.getFieldValue(labelField) != null) {
                    entityDetails.setLabel(doc.getFieldValue(labelField).toString());
                }
                if (entityDetails.areAllFieldsPresent()) {
                    break;
                }
            }

            entityDetailsCache.put(cacheKey, entityDetails);
            return entityDetails;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Bulk-populate {@link #entityDetailsCache} for a set of IRIs in one role
     * (subject/predicate/object). Replaces N round-trips through
     * {@link #queryEntityDetailsForIRI} with ~N/PREFETCH_CHUNK_SIZE batched
     * Solr queries. After this returns, {@link #queryEntityDetailsForIRI}
     * calls for any of the supplied IRIs hit the cache.
     *
     * <p>IRIs that already have a cache entry are skipped. IRIs that have no
     * matching Solr document are still cached (with a bare IRI-only
     * EntityDetails) so future lookups don't re-query.
     */
    public void prefetchEntityDetails(String iriField, Collection<String> iris,
                                       String curieField, String labelField) {
        if (iris == null || iris.isEmpty()) {
            return;
        }

        // Deduplicate and skip anything we've already cached.
        Set<String> toFetch = new HashSet<>();
        for (String iri : iris) {
            if (iri == null || iri.isBlank()) continue;
            if (!entityDetailsCache.containsKey(iriField + '\0' + iri)) {
                toFetch.add(iri);
            }
        }
        if (toFetch.isEmpty()) {
            return;
        }

        List<String> fetchList = new ArrayList<>(toFetch);
        int totalChunks = (fetchList.size() + PREFETCH_CHUNK_SIZE - 1) / PREFETCH_CHUNK_SIZE;
        logger.info("Prefetch {}: {} distinct IRIs in {} chunk(s) of up to {}",
                iriField, fetchList.size(), totalChunks, PREFETCH_CHUNK_SIZE);
        long batchStart = System.currentTimeMillis();
        for (int i = 0; i < fetchList.size(); i += PREFETCH_CHUNK_SIZE) {
            List<String> chunk = fetchList.subList(i, Math.min(i + PREFETCH_CHUNK_SIZE, fetchList.size()));
            int chunkIndex = (i / PREFETCH_CHUNK_SIZE) + 1;
            long chunkStart = System.currentTimeMillis();
            int docsReturned = fetchChunk(iriField, chunk, curieField, labelField);
            logger.info("Prefetch {}: chunk {}/{} ({} IRIs) -> {} docs in {} ms",
                    iriField, chunkIndex, totalChunks, chunk.size(), docsReturned,
                    System.currentTimeMillis() - chunkStart);
        }
        logger.info("Prefetch {}: complete in {} ms", iriField,
                System.currentTimeMillis() - batchStart);
    }

    private int fetchChunk(String iriField, List<String> chunk,
                            String curieField, String labelField) {
        try {
            SolrQuery query = new SolrQuery();
            // The {!terms} parser is built for many-value lookups and avoids
            // the maxBooleanClauses limit that an OR-chain would hit. IRIs in
            // this codebase don't contain commas, so the default separator is
            // safe.
            query.setQuery("{!terms f=" + iriField + "}" + String.join(",", chunk));
            query.setFields(iriField, curieField, labelField);
            // Multiple Mappings can share an IRI; cap at a comfortable upper
            // bound so a single batch can return all matches.
            query.setRows(chunk.size() * 20);

            // Use POST: the IRI list in {!terms} would blow past Jetty's default
            // ~8 KB URI cap on a GET (HTTP 414) at this chunk size.
            QueryResponse response = solrMappingClient.query(query, SolrRequest.METHOD.POST);
            SolrDocumentList docs = response.getResults();

            for (SolrDocument doc : docs) {
                Object iriValueObj = doc.getFieldValue(iriField);
                if (iriValueObj == null) continue;
                String iri = iriValueObj.toString();
                String cacheKey = iriField + '\0' + iri;

                EntityDetails entityDetails = entityDetailsCache.computeIfAbsent(cacheKey, k -> {
                    EntityDetails ed = new EntityDetails();
                    ed.setIri(iri);
                    return ed;
                });
                if (!entityDetails.isCuriePresent() && doc.getFieldValue(curieField) != null) {
                    entityDetails.setCurie(doc.getFieldValue(curieField).toString());
                }
                if (!entityDetails.isLabelPresent() && doc.getFieldValue(labelField) != null) {
                    entityDetails.setLabel(doc.getFieldValue(labelField).toString());
                }
            }

            // Pin a bare entry for IRIs with no Solr match so we don't re-query
            // them one-by-one later.
            for (String iri : chunk) {
                String cacheKey = iriField + '\0' + iri;
                entityDetailsCache.computeIfAbsent(cacheKey, k -> {
                    EntityDetails ed = new EntityDetails();
                    ed.setIri(iri);
                    return ed;
                });
            }
            return docs.size();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Bulk-populate {@link #spoMappingsCache} for a set of {@code <s,p,o>}
     * triples. Issues batched queries on {@code subject_iri}, populating the
     * cache for every triple actually returned, then pins an empty entry for
     * any input triple Solr did not match. After this returns,
     * {@link #querySubjectPredicateObjectIRI} is a cache hit for every triple.
     *
     * <p>Each input triple is a {@code String[3]} of {@code {s, p, o}}.
     */
    public void prefetchMappingsForTriples(Collection<String[]> triples) {
        if (triples == null || triples.isEmpty()) {
            return;
        }

        Set<String> distinctSubjects = new HashSet<>();
        Set<String> distinctTripleKeys = new HashSet<>();
        for (String[] t : triples) {
            if (t == null || t.length != 3) continue;
            if (t[0] == null || t[1] == null || t[2] == null) continue;
            String key = t[0] + '\0' + t[1] + '\0' + t[2];
            if (spoMappingsCache.containsKey(key)) continue;
            distinctSubjects.add(t[0]);
            distinctTripleKeys.add(key);
        }
        if (distinctSubjects.isEmpty()) {
            return;
        }

        List<String> fetchList = new ArrayList<>(distinctSubjects);
        int totalChunks = (fetchList.size() + PREFETCH_TRIPLES_CHUNK_SIZE - 1) / PREFETCH_TRIPLES_CHUNK_SIZE;
        logger.info("Prefetch SPO mappings: {} distinct subjects covering {} triples in {} chunk(s) of up to {}",
                fetchList.size(), distinctTripleKeys.size(), totalChunks, PREFETCH_TRIPLES_CHUNK_SIZE);

        long batchStart = System.currentTimeMillis();
        int totalDocs = 0;
        for (int i = 0; i < fetchList.size(); i += PREFETCH_TRIPLES_CHUNK_SIZE) {
            List<String> chunk = fetchList.subList(i, Math.min(i + PREFETCH_TRIPLES_CHUNK_SIZE, fetchList.size()));
            int chunkIndex = (i / PREFETCH_TRIPLES_CHUNK_SIZE) + 1;
            long chunkStart = System.currentTimeMillis();
            int docs = fetchSpoChunk(chunk);
            totalDocs += docs;
            logger.info("Prefetch SPO mappings: chunk {}/{} ({} subjects) -> {} docs in {} ms",
                    chunkIndex, totalChunks, chunk.size(), docs,
                    System.currentTimeMillis() - chunkStart);
        }

        // Pin empty entries for triples that didn't match any returned doc so
        // querySubjectPredicateObjectIRI doesn't fall back to a one-off Solr GET.
        int emptyMarked = 0;
        for (String key : distinctTripleKeys) {
            if (spoMappingsCache.putIfAbsent(key, java.util.Collections.emptyList()) == null) {
                emptyMarked++;
            }
        }
        logger.info("Prefetch SPO mappings: complete. {} docs cached, {} triples marked empty, {} ms total",
                totalDocs, emptyMarked, System.currentTimeMillis() - batchStart);
    }

    private int fetchSpoChunk(List<String> subjectChunk) {
        try {
            SolrQuery query = new SolrQuery();
            query.setQuery("{!terms f=" + MappingEnum.SUBJECT_IRI.getField() + "}"
                    + String.join(",", subjectChunk));
            // Each subject can have multiple Mapping documents; size generously
            // so a single batch returns all matches without paging.
            query.setRows(subjectChunk.size() * 50);

            QueryResponse response = solrMappingClient.query(query, SolrRequest.METHOD.POST);
            List<Mapping.Builder> builders = response.getBeans(Mapping.Builder.class);
            for (Mapping.Builder builder : builders) {
                Mapping mapping = builder.build();
                String s = mapping.subjectIRI().map(uk.ac.ebi.spot.oxo.model.sssom.Uri::asStringIRI).orElse(null);
                String p = mapping.predicateIRI().map(uk.ac.ebi.spot.oxo.model.sssom.Uri::asStringIRI).orElse(null);
                String o = mapping.objectIRI().map(uk.ac.ebi.spot.oxo.model.sssom.Uri::asStringIRI).orElse(null);
                if (s == null || p == null || o == null) continue;
                String key = s + '\0' + p + '\0' + o;
                spoMappingsCache.computeIfAbsent(key, k -> new ArrayList<>()).add(mapping);
            }
            return builders.size();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void close() throws Exception {
        solrMappingClient.close();
    }
}
