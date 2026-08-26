package uk.ac.ebi.spot.oxo.backend.controller.api.v2;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.PivotField;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.response.OntologySummary;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.response.OntologyTarget;
import uk.ac.ebi.spot.oxo.backend.service.OxOSolrClient;
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

/**
 * Serves the set of ontologies (CURIE prefixes) present in the mappings index, faceting over the
 * denormalised {@code subject_prefix} / {@code object_prefix} fields. Drives the cross-ontology
 * mapping source/target selectors (ADR-0024).
 *
 * <p>Each prefix is enriched with two independent facts (ADR-0047): the {@code namespace} its CURIEs
 * expand against, read from the entity index because only that records what the dataload actually
 * minted; and the {@code uri} of the ontology behind the prefix, joined from the ontology-derived
 * mapping sets. Most prefixes in this listing name no ontology, so most carry no {@code uri}.
 */
@Tag(name = "Ontologies", description = "Ontologies (CURIE prefixes) present in the mappings index, "
        + "for driving cross-ontology mapping source/target selectors (ADR-0024).")
@RestController
@RequestMapping(path = "/api/v2/ontologies", produces = {MediaType.APPLICATION_JSON_VALUE})
public class OntologyController {

    private static final Logger logger = LoggerFactory.getLogger(OntologyController.class);

    /** Bounded so a corpus with an unexpected number of ontology sets cannot pull the whole core. */
    private static final int MAX_ONTOLOGY_SETS = 10_000;

    private static final String PREFIX_NAMESPACE_PIVOT =
            EntityConstants.PREFIX + "," + EntityConstants.NAMESPACE;

    @Autowired
    private OxOSolrClient solrClient;

    @Operation(
            summary = "List ontologies (CURIE prefixes)",
            description = "Without parameters, returns every CURIE prefix in the mappings index with "
                    + "the number of mappings using it as subject and as object, its `namespace` (the "
                    + "IRI stem its CURIEs expand against) and, for prefixes backed by an ontology, the "
                    + "ontology's own `uri`. Both are omitted when unknown. With `forSubject`, returns "
                    + "instead the target ontologies (object prefixes) reachable from that source "
                    + "ontology, each with the number of mappings into it.")
    // The handler returns ResponseEntity<?> because the body's shape depends on `forSubject`, so
    // springdoc can infer nothing from the signature — without this the response shape (and therefore
    // `namespace` and `uri`) is absent from the published API docs entirely.
    @ApiResponse(responseCode = "200",
            description = "Ontology prefixes with counts — `OntologySummary[]`, or `OntologyTarget[]` "
                    + "when `forSubject` is given",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    array = @ArraySchema(schema = @Schema(anyOf = {OntologySummary.class,
                            OntologyTarget.class}))))
    @GetMapping
    public ResponseEntity<?> listOntologies(
            @Parameter(description = "Source ontology prefix. When present, the response is the list of "
                    + "reachable target ontologies with per-target mapping counts.", example = "DOID")
            @RequestParam(required = false) String forSubject) {
        try {
            if (forSubject != null && !forSubject.isBlank()) {
                return ResponseEntity.ok(targetsForSubject(forSubject.trim()));
            }
            return ResponseEntity.ok(allOntologies());
        } catch (Exception e) {
            logger.error("Error listing ontologies (forSubject={})", forSubject, e);
            return ResponseEntity.status(500).build();
        }
    }

    /** Facet over both prefix fields in one query, merging subject/object counts per prefix. */
    private List<OntologySummary> allOntologies() throws Exception {
        SolrQuery solrQuery = new SolrQuery("*:*");
        solrQuery.setRows(0);
        solrQuery.setFacet(true);
        solrQuery.setFacetMinCount(1);
        solrQuery.setFacetLimit(-1);
        solrQuery.addFacetField(MappingConstants.SUBJECT_PREFIX, MappingConstants.OBJECT_PREFIX);

        QueryResponse response = solrClient.queryMappings(solrQuery);
        Map<String, long[]> counts = new LinkedHashMap<>(); // prefix -> [asSubject, asObject]
        accumulate(response.getFacetField(MappingConstants.SUBJECT_PREFIX), counts, 0);
        accumulate(response.getFacetField(MappingConstants.OBJECT_PREFIX), counts, 1);

        Map<String, String> namespaces = namespacesByPrefix();
        Map<String, String> ontologyIris = ontologyIrisByUpperPrefix();

        List<OntologySummary> summaries = new ArrayList<>(counts.size());
        counts.forEach((prefix, subjectObject) -> summaries.add(new OntologySummary(
                prefix,
                namespaces.get(prefix),
                ontologyIris.get(prefix.toUpperCase(Locale.ROOT)),
                subjectObject[0],
                subjectObject[1])));
        summaries.sort(Comparator.comparing(OntologySummary::prefix));
        return summaries;
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
    private Map<String, String> namespacesByPrefix() {
        try {
            return queryNamespacesByPrefix();
        } catch (Exception e) {
            // Enrichment, not the payload: the counts are still worth serving without it.
            logger.warn("Could not read namespaces from the entity index; they will be absent.", e);
            return Map.of();
        }
    }

    private Map<String, String> queryNamespacesByPrefix() throws Exception {
        SolrQuery solrQuery = new SolrQuery("*:*");
        solrQuery.setRows(0);
        solrQuery.setFacet(true);
        solrQuery.setFacetMinCount(1);
        solrQuery.setFacetLimit(-1);
        solrQuery.addFacetPivotField(PREFIX_NAMESPACE_PIVOT);

        QueryResponse response = solrClient.queryEntities(solrQuery);
        List<PivotField> pivots = response == null || response.getFacetPivot() == null
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
    private Map<String, String> ontologyIrisByUpperPrefix() {
        try {
            return queryOntologyIrisByUpperPrefix();
        } catch (Exception e) {
            logger.warn("Could not read ontology IRIs from the mapping sets; they will be absent.", e);
            return Map.of();
        }
    }

    private Map<String, String> queryOntologyIrisByUpperPrefix() throws Exception {
        SolrQuery solrQuery = new SolrQuery("*:*");
        solrQuery.setRows(MAX_ONTOLOGY_SETS);
        solrQuery.addFilterQuery(MappingSetConstants.ONTOLOGY_IRI + ":[* TO *]");
        solrQuery.setFields(MappingSetConstants.PREFIX, MappingSetConstants.ONTOLOGY_IRI);

        QueryResponse response = solrClient.queryMappingSets(solrQuery);
        if (response == null || response.getResults() == null) {
            return Map.of();
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

    /** Targets reachable from a source ontology: facet object_prefix over the subject_prefix subset. */
    private List<OntologyTarget> targetsForSubject(String subjectPrefix) throws Exception {
        SolrQuery solrQuery = new SolrQuery("*:*");
        solrQuery.setRows(0);
        solrQuery.addFilterQuery(
                MappingConstants.SUBJECT_PREFIX + ":" + ClientUtils.escapeQueryChars(subjectPrefix));
        solrQuery.setFacet(true);
        solrQuery.setFacetMinCount(1);
        solrQuery.setFacetLimit(-1);
        solrQuery.addFacetField(MappingConstants.OBJECT_PREFIX);

        QueryResponse response = solrClient.queryMappings(solrQuery);
        FacetField objectFacet = response.getFacetField(MappingConstants.OBJECT_PREFIX);
        List<OntologyTarget> targets = new ArrayList<>();
        if (objectFacet != null) {
            for (FacetField.Count value : objectFacet.getValues()) {
                if (value.getName() == null || value.getName().isBlank()) continue;
                targets.add(new OntologyTarget(value.getName(), value.getCount()));
            }
        }
        targets.sort(Comparator.comparing(OntologyTarget::prefix));
        return targets;
    }
}
