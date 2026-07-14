package uk.ac.ebi.spot.oxo.backend.controller.api.v2;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.request.MappingSearchRequest;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.request.ValueSuggestRequest;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.response.EntitySuggestion;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.response.ValueSuggestion;
import uk.ac.ebi.spot.oxo.backend.service.OxOSolrClient;
import uk.ac.ebi.spot.oxo.backend.service.helper.EntitySuggestQueryBuilder;
import uk.ac.ebi.spot.oxo.backend.service.helper.SolrQueryBuilder;
import uk.ac.ebi.spot.oxo.backend.service.helper.SuggestFields;
import uk.ac.ebi.spot.oxo.model.entity.EntityConstants;
import uk.ac.ebi.spot.oxo.model.entity.EntitySide;
import uk.ac.ebi.spot.oxo.model.sssom.MappingEnum;
import uk.ac.ebi.spot.oxo.model.sssom.WeakPredicate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Typeahead suggestions (ADR-0034).
 *
 * <p>Entity suggestions come from the {@code oxo2-entities} collection — the per-entity read model —
 * because {@code oxo2-mappings} is denormalised and would suggest an entity once per mapping it
 * participates in rather than once.
 */
@Tag(name = "Suggest", description = "Typeahead suggestions for the search box, the result-table "
        + "column filters and the Advanced tab (ADR-0034).")
@RestController
@RequestMapping(path = "/api/v2/suggest", produces = {MediaType.APPLICATION_JSON_VALUE})
public class SuggestController {

    private static final Logger logger = LoggerFactory.getLogger(SuggestController.class);

    private static final int DEFAULT_SIZE = 10;

    /** Contextual suggestions fill a dropdown, so the cap matches the entity typeahead's. */
    private static final int MAX_SUGGEST_VALUES = 25;

    /**
     * A vocabulary list is fetched WHOLE and filtered client-side, so its default has to be big
     * enough to hold every value of the largest controlled vocabulary — a truncated list would
     * silently make some values un-suggestable, which looks like "that value doesn't exist".
     */
    private static final int DEFAULT_VOCAB_SIZE = 500;
    private static final int MAX_VOCAB_SIZE = 1000;

    @Autowired
    private OxOSolrClient solrClient;

    @Operation(
            summary = "Suggest entities by CURIE or label prefix",
            description = "Entities whose label or CURIE starts with what has been typed. Matching is "
                    + "prefix-of-any-token on the label (so \"mel\" reaches \"malignant melanoma\") and "
                    + "whole-string prefix on the CURIE (so \"MONDO:00\" is a prefix of "
                    + "\"MONDO:0000001\"). Results rank a leading-edge match above a mid-label token "
                    + "match, and are boosted by how many mappings the entity participates in. A query "
                    + "shorter than " + EntitySuggestQueryBuilder.MIN_QUERY_LENGTH + " characters "
                    + "returns an empty list without querying Solr.\n\n"
                    + "Only entities a search would actually return are suggested: an entity whose "
                    + "every mapping uses a hidden predicate is offered only once "
                    + "`includeWeakPredicates` asks for that predicate (ADR-0035).")
    @ApiResponse(responseCode = "200", description = "Matching entities, most relevant first")
    @ApiResponse(responseCode = "400", description = "Unknown value in includeWeakPredicates")
    @GetMapping("/entities")
    public ResponseEntity<List<EntitySuggestion>> suggestEntities(
            @Parameter(description = "What the user has typed so far.", example = "mel")
            @RequestParam(name = "q", required = false) String query,

            @Parameter(description = "Which side of a mapping the entity must appear on. The main "
                    + "search box uses SUBJECT, because the default search matches the subject side "
                    + "only (ADR-0030) — suggesting an object-only entity would complete to no rows.")
            @RequestParam(name = "side", required = false) EntitySide side,

            @Parameter(description = "Suggest entities whose mappings use these normally-hidden "
                    + "predicates too: `subClassOf`, `hasDbXref`. Must match the checkbox state of the "
                    + "search this completes into, or a suggestion could return no rows.",
                    example = "hasDbXref")
            @RequestParam(name = "includeWeakPredicates", required = false)
            List<String> includeWeakPredicates,

            @Parameter(description = "Restrict to these CURIE prefixes (ontologies).", example = "MONDO")
            @RequestParam(name = "prefix", required = false) List<String> prefixes,

            @Parameter(description = "Maximum suggestions to return.")
            @RequestParam(name = "size", required = false, defaultValue = "" + DEFAULT_SIZE) int size) {

        // Never let a blank or one-character query reach Solr: a single character is a prefix of a
        // large fraction of the collection, so the query is both expensive and useless.
        if (EntitySuggestQueryBuilder.isTooShort(query)) {
            return ResponseEntity.ok(List.of());
        }

        List<WeakPredicate> weakPredicates = parseWeakPredicates(includeWeakPredicates);
        if (weakPredicates == null) {
            return ResponseEntity.badRequest().build();
        }

        try {
            SolrQuery solrQuery = EntitySuggestQueryBuilder.buildEntitySuggestQuery(
                    query, side, prefixes, weakPredicates, size);
            QueryResponse response = solrClient.queryEntities(solrQuery);
            return ResponseEntity.ok(toSuggestions(response));
        } catch (Exception queryFailure) {
            logger.error("Error suggesting entities (q={}, side={}, prefixes={}, weak={})",
                    query, side, prefixes, includeWeakPredicates, queryFailure);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Null when any code is unknown, so the caller can answer 400. Silently dropping an unrecognised
     * predicate would hand back suggestions filtered differently from the search the caller believes
     * it is completing — the exact class of mismatch ADR-0035 exists to remove.
     */
    private static List<WeakPredicate> parseWeakPredicates(List<String> codes) {
        if (codes == null) {
            return List.of();
        }
        List<WeakPredicate> parsed = new ArrayList<>();
        for (String code : codes) {
            WeakPredicate predicate = WeakPredicate.fromCode(code);
            if (predicate == null) {
                return null;
            }
            parsed.add(predicate);
        }
        return parsed;
    }

    private static List<EntitySuggestion> toSuggestions(QueryResponse response) {
        List<EntitySuggestion> suggestions = new ArrayList<>();
        for (SolrDocument document : response.getResults()) {
            suggestions.add(new EntitySuggestion(
                    string(document, EntityConstants.ID),
                    string(document, EntityConstants.LABEL),
                    string(document, EntityConstants.IRI),
                    string(document, EntityConstants.PREFIX),
                    // The count under the caller's checkbox state, computed by Solr as an fl function
                    // — not the stored mapping_count, which counts predicates the search hides.
                    number(document, EntitySuggestQueryBuilder.VISIBLE_MAPPING_COUNT)));
        }
        return suggestions;
    }

    private static String string(SolrDocument document, String field) {
        Object value = document.getFirstValue(field);
        return value == null ? null : value.toString();
    }

    private static long number(SolrDocument document, String field) {
        Object value = document.getFirstValue(field);
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    // ---------------------------------------------------------------------------------------------
    // Field-value suggestions
    // ---------------------------------------------------------------------------------------------

    @Operation(
            summary = "List a controlled-vocabulary field's distinct values",
            description = "Every distinct value of one low-cardinality field, most common first. "
                    + "Intended to be fetched once and cached: the Advanced tab filters the list "
                    + "client-side as the user types, the same way the ontology selector does. Only "
                    + "controlled-vocabulary fields are allowed — a high-cardinality field (a subject "
                    + "or object id/label) would enumerate millions of terms and is served by "
                    + "/suggest/entities instead.")
    @ApiResponse(responseCode = "200", description = "Distinct values with counts")
    @ApiResponse(responseCode = "400", description = "Field is unknown or not a vocabulary field")
    @GetMapping("/values")
    public ResponseEntity<List<ValueSuggestion>> distinctValues(
            @Parameter(description = "The field whose values to list.", example = "mapping_justification")
            @RequestParam(name = "field") String field,

            @Parameter(description = "Maximum values to return.")
            @RequestParam(name = "size", required = false, defaultValue = "" + DEFAULT_VOCAB_SIZE) int size) {

        MappingEnum mappingField = MappingEnum.fromString(field);
        if (mappingField == null || !SuggestFields.VOCAB_FIELDS.contains(mappingField)) {
            return ResponseEntity.badRequest().build();
        }

        try {
            SolrQuery solrQuery = SolrQueryBuilder.buildDistinctValuesQuery(
                    mappingField, Math.min(Math.max(size, 1), MAX_VOCAB_SIZE));
            QueryResponse response = solrClient.queryMappings(solrQuery);
            return ResponseEntity.ok(mergeFacetBuckets(response));
        } catch (Exception queryFailure) {
            logger.error("Error listing distinct values (field={})", field, queryFailure);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(
            summary = "Suggest a field's values within a live search",
            description = "Values of one field that are present in the CURRENT result set, with the "
                    + "count of rows each would leave. Because the suggestions are faceted over the "
                    + "live search rather than the whole corpus, applying one as a filter can never "
                    + "yield zero rows. The search body is exactly what is POSTed to "
                    + "/api/v2/mappings/search.")
    @ApiResponse(responseCode = "200", description = "Values present in the result set, with counts")
    @ApiResponse(responseCode = "400", description = "Field is unknown or not suggestible")
    @PostMapping("/values")
    public ResponseEntity<List<ValueSuggestion>> suggestValues(@RequestBody ValueSuggestRequest request) {
        MappingEnum field = request.getField();
        if (field == null || !SuggestFields.CONTEXTUAL_FIELDS.contains(field)) {
            return ResponseEntity.badRequest().build();
        }

        MappingSearchRequest search = request.getSearch() != null
                ? request.getSearch()
                : new MappingSearchRequest();

        try {
            SolrQuery solrQuery = SolrQueryBuilder.buildValueSuggestQuery(
                    search, field, request.getQuery(),
                    Math.min(Math.max(request.getSize(), 1), MAX_SUGGEST_VALUES));
            QueryResponse response = solrClient.queryMappings(solrQuery);
            return ResponseEntity.ok(mergeFacetBuckets(response));
        } catch (Exception queryFailure) {
            logger.error("Error suggesting values (field={}, q={})", field, request.getQuery(), queryFailure);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Merge every facet bucket list in the response into one value list, most common first.
     *
     * <p>A contextual suggest issues the same facet under several casings of the typed prefix (see
     * {@code SolrQueryBuilder.addPrefixFacets}), so the response carries several bucket lists that
     * overlap. Dedupe by value, keeping the highest count — the same value can only be reached by one
     * casing of the prefix, so "highest" is really "the one that matched", and taking a max rather
     * than a sum is what stops a value being double-counted when two casing variants coincide (an
     * all-lowercase term matched by both the as-typed and the lowercase prefix).
     */
    private static List<ValueSuggestion> mergeFacetBuckets(QueryResponse response) {
        List<FacetField> facetFields = response.getFacetFields();
        if (facetFields == null) {
            return List.of();
        }

        Map<String, Long> countByValue = new LinkedHashMap<>();
        for (FacetField facetField : facetFields) {
            List<FacetField.Count> counts = facetField.getValues();
            if (counts == null) {
                continue;
            }
            for (FacetField.Count count : counts) {
                if (count.getName() == null || count.getName().isBlank()) {
                    continue;
                }
                countByValue.merge(count.getName(), count.getCount(), Math::max);
            }
        }

        return countByValue.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> new ValueSuggestion(entry.getKey(), entry.getValue()))
                .toList();
    }
}
