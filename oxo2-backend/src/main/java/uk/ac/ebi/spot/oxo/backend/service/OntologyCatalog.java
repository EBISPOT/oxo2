package uk.ac.ebi.spot.oxo.backend.service;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.PivotField;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.response.OntologySummary;
import uk.ac.ebi.spot.oxo.model.entity.EntityConstants;
import uk.ac.ebi.spot.oxo.model.sssom.MappingConstants;
import uk.ac.ebi.spot.oxo.model.sssom.MappingSetConstants;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Immutable ontology metadata for one serving index. A data release must restart the backend. */
@Service
public class OntologyCatalog {
    private static final Logger logger = LoggerFactory.getLogger(OntologyCatalog.class);
    private static final int MAX_ONTOLOGY_SETS = 10_000;
    private static final String PREFIX_NAMESPACE_PIVOT =
            EntityConstants.PREFIX + "," + EntityConstants.NAMESPACE;

    private final OxOSolrClient solrClient;
    private List<OntologySummary> cachedOntologies;

    public OntologyCatalog(OxOSolrClient solrClient) {
        this.solrClient = solrClient;
    }

    /** The monitor also serialises startup warming and concurrent HTTP requests. */
    public synchronized List<OntologySummary> allOntologies() throws Exception {
        if (cachedOntologies != null) return cachedOntologies;
        return loadOntologies();
    }

    synchronized boolean isWarm() {
        return cachedOntologies != null;
    }

    /** Facet over both prefix fields in one query, merging subject/object counts per prefix. */
    private List<OntologySummary> loadOntologies() throws Exception {
        SolrQuery solrQuery = new SolrQuery("*:*");
        solrQuery.setRows(0);
        solrQuery.setFacet(true);
        solrQuery.setFacetMinCount(1);
        solrQuery.setFacetLimit(-1);
        solrQuery.addFacetField(MappingConstants.SUBJECT_PREFIX, MappingConstants.OBJECT_PREFIX);

        QueryResponse response = solrClient.queryMappings(solrQuery);
        requireComplete(response);
        Map<String, long[]> counts = new LinkedHashMap<>(); // prefix -> [asSubject, asObject]
        accumulate(response.getFacetField(MappingConstants.SUBJECT_PREFIX), counts, 0);
        accumulate(response.getFacetField(MappingConstants.OBJECT_PREFIX), counts, 1);

        boolean complete = true;
        Map<String, String> namespaces;
        try {
            namespaces = queryNamespacesByPrefix();
        } catch (Exception failure) {
            logger.warn("Could not read namespaces; serving uncached counts.", failure);
            namespaces = Map.of();
            complete = false;
        }
        Map<String, String> ontologyIris;
        try {
            ontologyIris = queryOntologyIrisByUpperPrefix();
        } catch (Exception failure) {
            logger.warn("Could not read ontology IRIs; serving uncached counts.", failure);
            ontologyIris = Map.of();
            complete = false;
        }

        List<OntologySummary> summaries = new ArrayList<>(counts.size());
        for (Map.Entry<String, long[]> entry : counts.entrySet()) {
            String prefix = entry.getKey();
            long[] subjectObject = entry.getValue();
            summaries.add(new OntologySummary(
                    prefix,
                    namespaces.get(prefix),
                    ontologyIris.get(prefix.toUpperCase(Locale.ROOT)),
                    subjectObject[0],
                    subjectObject[1]));
        }
        summaries.sort(Comparator.comparing(OntologySummary::prefix));
        List<OntologySummary> snapshot = List.copyOf(summaries);
        // Empty cores can be visible before the first dataload. Keep those results retryable.
        if (complete && !snapshot.isEmpty()) cachedOntologies = snapshot;
        return snapshot;
    }

    private static void accumulate(FacetField facetField, Map<String, long[]> counts, int slot) {
        if (facetField == null) return;
        for (FacetField.Count value : facetField.getValues()) {
            if (value.getName() == null || value.getName().isBlank()) continue;
            counts.computeIfAbsent(value.getName(), key -> new long[2])[slot] = value.getCount();
        }
    }

    /**
     * One IRI stem per CURIE prefix, from the entity index (ADR-0047).
     *
     * <p>A single pivot facet rather than a lookup per prefix: the listing carries over a thousand
     * prefixes, and a per-prefix query would turn one request into a thousand.
     *
     * <p>Where a prefix has more than one stem — the same ontology reached over {@code http} and
     * {@code https}, say — the one carried by the most entities wins, with the lexicographically
     * smaller stem breaking an exact tie so the answer does not move between identical indexes.
     */
    private Map<String, String> queryNamespacesByPrefix() throws Exception {
        SolrQuery solrQuery = new SolrQuery("*:*");
        solrQuery.setRows(0);
        solrQuery.setFacet(true);
        solrQuery.setFacetMinCount(1);
        solrQuery.setFacetLimit(-1);
        solrQuery.addFacetPivotField(PREFIX_NAMESPACE_PIVOT);

        QueryResponse response = solrClient.queryEntities(solrQuery);
        requireComplete(response);
        List<PivotField> pivots = response.getFacetPivot() == null
                ? null
                : response.getFacetPivot().get(PREFIX_NAMESPACE_PIVOT);
        if (pivots == null) {
            logger.warn("No {} pivot in the entity response; namespaces will be absent. An index "
                    + "written before ADR-0047 carries no namespace field.", PREFIX_NAMESPACE_PIVOT);
            return Map.of();
        }

        Map<String, String> namespaces = new HashMap<>();
        for (PivotField prefixPivot : pivots) {
            String prefix = asString(prefixPivot.getValue());
            if (prefix == null || prefixPivot.getPivot() == null) continue;
            PivotField best = null;
            for (PivotField candidate : prefixPivot.getPivot()) {
                if (asString(candidate.getValue()) == null) continue;
                if (best == null || outranks(candidate, best)) {
                    best = candidate;
                }
            }
            if (best != null) {
                namespaces.put(prefix, asString(best.getValue()));
            }
        }
        return namespaces;
    }

    /** Higher count wins; an exact tie falls back to the smaller stem so the choice is stable. */
    private static boolean outranks(PivotField candidate, PivotField incumbent) {
        if (candidate.getCount() != incumbent.getCount()) {
            return candidate.getCount() > incumbent.getCount();
        }
        return asString(candidate.getValue()).compareTo(asString(incumbent.getValue())) < 0;
    }

    /**
     * The ontology IRI behind each CURIE prefix, from the ontology-derived mapping sets (ADR-0047).
     *
     * <p><b>Keyed by UPPERCASE prefix.</b> The listing's prefixes are upper-cased when an entity
     * reference is normalised, while a set's prefix is verbatim from the producer's metadata and keeps
     * its own casing — {@code NCBITaxon}, {@code HGNC}, {@code mesh}. An exact-string join therefore
     * misses the largest ontologies in the corpus while still looking like it worked.
     *
     * <p>Several sets can share a prefix (an ontology and its obsolete-terms companion). They are
     * expected to agree; if they ever do not, the smaller IRI is kept so the response is stable, and
     * the disagreement is logged rather than resolved silently.
     */
    private Map<String, String> queryOntologyIrisByUpperPrefix() throws Exception {
        SolrQuery solrQuery = new SolrQuery("*:*");
        solrQuery.setRows(MAX_ONTOLOGY_SETS);
        solrQuery.addFilterQuery(MappingSetConstants.ONTOLOGY_IRI + ":[* TO *]");
        solrQuery.setFields(MappingSetConstants.PREFIX, MappingSetConstants.ONTOLOGY_IRI);

        QueryResponse response = solrClient.queryMappingSets(solrQuery);
        requireComplete(response);
        if (response.getResults() == null) {
            throw new IllegalStateException("Missing ontology mapping-set results");
        }
        if (response.getResults().getNumFound() > response.getResults().size()) {
            throw new IllegalStateException("Ontology mapping-set results were truncated");
        }
        Map<String, String> byPrefix = new HashMap<>();
        for (SolrDocument document : response.getResults()) {
            String prefix = asString(document.getFieldValue(MappingSetConstants.PREFIX));
            String ontologyIri = asString(document.getFieldValue(MappingSetConstants.ONTOLOGY_IRI));
            if (prefix == null || ontologyIri == null) continue;
            byPrefix.merge(prefix.toUpperCase(Locale.ROOT), ontologyIri, (existing, incoming) -> {
                if (existing.equals(incoming)) {
                    return existing;
                }
                logger.warn("Mapping sets disagree on the ontology IRI for prefix {}: {} vs {}; "
                        + "keeping the lexicographically smaller.", prefix, existing, incoming);
                return existing.compareTo(incoming) <= 0 ? existing : incoming;
            });
        }
        return byPrefix;
    }

    private static String asString(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }

    private static void requireComplete(QueryResponse response) {
        if (response == null || response.getStatus() != 0) {
            throw new IllegalStateException("Missing or failed ontology query response");
        }
        Object partial = response.getResponseHeader() == null ? null
                : response.getResponseHeader().get("partialResults");
        if (partial != null && !"false".equalsIgnoreCase(partial.toString())) {
            throw new IllegalStateException("Incomplete ontology query response");
        }
    }
}
