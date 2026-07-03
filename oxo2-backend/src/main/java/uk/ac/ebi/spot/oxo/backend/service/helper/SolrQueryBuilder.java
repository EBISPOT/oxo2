package uk.ac.ebi.spot.oxo.backend.service.helper;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.request.FieldQuery;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.request.MappingSearchRequest;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.request.SortedField;
import uk.ac.ebi.spot.oxo.model.sssom.EntityReference;
import uk.ac.ebi.spot.oxo.model.sssom.InferenceType;
import uk.ac.ebi.spot.oxo.model.sssom.MappingEnum;
import uk.ac.ebi.spot.oxo.utils.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

import static org.apache.solr.common.params.DisMaxParams.QF;
import static uk.ac.ebi.spot.oxo.model.sssom.MappingEnum.MINIMAL_LIST_OF_FIELDS;

public class SolrQueryBuilder {

    private static final Logger logger = LoggerFactory.getLogger(SolrQueryBuilder.class);

    /**
     * Soft ranking (ADR-0011) as a MULTIPLICATIVE edismax boost function, so a highly relevant
     * inferred mapping can still outrank a weakly matching asserted one. Multiplicative — not an
     * additive {@code bq} — because the tier boost must be independent of term idf: ASSERTED is
     * common (low idf) and SSSOM_INFERENCE rare (high idf), so an additive {@code bq} would
     * actually boost SSSOM more than ASSERTED, inverting the intended order.
     *
     * <p>Tier multiplier ASSERTED (3) &gt; SSSOM_INFERENCE (2), multiplied by
     * a distance factor 1 + 0.4/(distance+1). The distance factor is bounded to [1.0, 1.4]
     * (distance 0 -> 1.4, large -> 1.0): a within-tier tie-breaker that favours shorter chains but
     * is always smaller than the 1.5x adjacent-tier ratio, so it can never invert the tier order
     * (an SSSOM doc with distance 0 must not outrank an asserted doc at equal relevance). Missing
     * distance (asserted docs) defaults to 1.
     */
    private static final String RANKING_BOOST =
            "mul("
                + "if(termfreq(" + MappingEnum.INFERENCE_TYPE.getField() + ",'"
                    + InferenceType.ASSERTED.getCode() + "'),3,"
                + "if(termfreq(" + MappingEnum.INFERENCE_TYPE.getField() + ",'"
                    + InferenceType.SSSOM_INFERENCE.getCode() + "'),2,1)),"
                + "sum(1,div(0.4,sum(def(distance,1),1))))";

    private static final Map<MappingEnum, String> textGeneralToDocValues = Map.of(
        MappingEnum.OBJECT_LABEL, textGeneralFieldAsString(MappingEnum.OBJECT_LABEL),
        MappingEnum.PREDICATE_LABEL, textGeneralFieldAsString(MappingEnum.PREDICATE_LABEL),
        MappingEnum.SUBJECT_LABEL, textGeneralFieldAsString(MappingEnum.SUBJECT_LABEL)
    );

    private static final Map<MappingEnum, String> textGeneralToNGram = Map.of(
        MappingEnum.OBJECT_LABEL, textGeneralFieldAsNGram(MappingEnum.OBJECT_LABEL),
        MappingEnum.PREDICATE_LABEL, textGeneralFieldAsNGram(MappingEnum.PREDICATE_LABEL),
        MappingEnum.SUBJECT_LABEL, textGeneralFieldAsNGram(MappingEnum.SUBJECT_LABEL)
    );

    private static String textGeneralFieldAsString(MappingEnum mappingEnum) {
        return mappingEnum.getField() + "_str";
    }

    private static String textGeneralFieldAsNGram(MappingEnum mappingEnum) {
        return mappingEnum.getField() + "_ngram";
    }

    /**
     * Weak predicates excluded from mapping-search results by default. The inference corpus has no
     * rule that chains them and their objects are frequently bare database codes (see
     * {@code ApplicablePredicatesEnum} in oxo2-json2inferences), so they add noise to ordinary
     * searches. Matched on {@code predicate_iri} (the canonical, prefix-independent IRI) rather than
     * {@code predicate_id}, whose stored CURIE prefix and casing vary by source set. The exclusion is
     * bypassed whenever the caller explicitly filters on a predicate field (see
     * {@link #hasExplicitPredicateFilter}), so it can never hide a row the caller deliberately asked
     * for.
     */
    private static final List<String> DEFAULT_EXCLUDED_PREDICATE_IRIS = List.of(
            "http://www.w3.org/2000/01/rdf-schema#subClassOf",
            "http://www.geneontology.org/formats/oboInOwl#hasDbXref");

    /** Predicate fields whose explicit filtering bypasses the default weak-predicate exclusion. */
    private static final Set<MappingEnum> PREDICATE_FILTER_FIELDS = Set.of(
            MappingEnum.PREDICATE_ID,
            MappingEnum.PREDICATE_LABEL,
            MappingEnum.PREDICATE_IRI,
            MappingEnum.PREDICATE_MODIFIER);

    /** The oxo2-mappings unique key — a total order for cursorMark streaming export (ADR-0024). */
    private static final String UNIQUE_KEY = "id";

    public static SolrQuery buildSolrQuery(MappingSearchRequest mappingSearchRequest, Pageable pageable) {

        SolrQuery solrQuery = new SolrQuery();

        solrQuery.setStart((int) pageable.getOffset());
        solrQuery.setRows(pageable.getPageSize());

        // Build q (and qf on the override path) — see applyQuery for the dispatch order.
        applyQuery(solrQuery, mappingSearchRequest);

        // ADR-0011: every path uses edismax so the soft inference-type + distance ranking applies
        // uniformly. The fielded queries on the advanced/classified paths carry their own field
        // selectors, which edismax parses unchanged; qf (set above) only affects the queryFields path.
        solrQuery.set(SolrConstants.DEF_TYPE, SolrConstants.EDISMAX);
        applyInferenceRanking(solrQuery);

        solrQuery.setFields(constructFieldList(mappingSearchRequest.getFieldList()));
        // Hide the weak predicates (rdfs:subClassOf, oboInOwl:hasDbXref) by default, unless the caller
        // explicitly filters on a predicate field — they then clearly want whatever predicates match,
        // so the default exclusion would be wrong.
        boolean excludeWeakPredicates = !hasExplicitPredicateFilter(mappingSearchRequest);
        solrQuery.setFilterQueries(constructFilterQueries(
                mappingSearchRequest.getColumnFilters(),
                mappingSearchRequest.getMappingSetIds(),
                mappingSearchRequest.getInferenceType(),
                mappingSearchRequest.getSubjectPrefixes(),
                mappingSearchRequest.getObjectPrefixes(),
                excludeWeakPredicates));
        solrQuery = constructSortedFields(solrQuery, mappingSearchRequest);

        if (mappingSearchRequest.isGroupBySpo()) {
            applySpoGrouping(solrQuery);
        }

        return solrQuery;
    }

    /**
     * Set q on the query (and qf on the override path). Dispatch order, most specific first:
     * <ol>
     *   <li>advancedFieldQueries non-empty → AND-joined per-field clauses (Advanced tab).</li>
     *   <li>queryFields non-empty → legacy edismax/qf path (caller-pinned fields).</li>
     *   <li>otherwise → classified-by-shape path (default search).</li>
     * </ol>
     * Do not silently rewire — each path is independent and documented.
     */
    private static void applyQuery(SolrQuery solrQuery, MappingSearchRequest request) {
        List<FieldQuery> advancedFieldQueries = request.getAdvancedFieldQueries();
        List<MappingEnum> queryFields = request.getQueryFields();
        if (advancedFieldQueries != null && !advancedFieldQueries.isEmpty()) {
            solrQuery.setQuery(constructAdvancedQuery(advancedFieldQueries));
        } else if (queryFields != null && !queryFields.isEmpty()) {
            solrQuery.setQuery(constructUsingQueryFields(request.getQueries()));
            solrQuery.set(QF, constructQueryFields(queryFields));
        } else {
            solrQuery.setQuery(constructClassifiedQuery(request.getQueries()));
        }
    }

    /**
     * Same-SPO collapse (ADR-0023, superseding ADR-0013's result grouping): keep one representative
     * row per {@code spo_key} via the CollapsingQParserPlugin as a post-filter. The collapse
     * {@code sort='score desc'} selects the highest inference-tier member (the {@link #RANKING_BOOST})
     * as the representative; {@code numFound} on the collapsed set is the exact group count (a page is
     * N groups), replacing the prohibitively slow {@code group.ngroups} pass. The ExpandComponent
     * returns each page representative's other members (the rows the detail view inlines, previously
     * {@code group.limit}). Collapse ANDs with the existing filter queries, so a group's members
     * reflect only what passed the inference-type filter. The main sort (set above) orders the
     * representatives, with {@code spo_key} appended as a total-order tiebreaker so paging is stable.
     */
    private static void applySpoGrouping(SolrQuery solrQuery) {
        String spoKey = MappingEnum.SPO_KEY.getField();
        solrQuery.addFilterQuery(String.format(
                SolrConstants.COLLAPSE_FQ_TEMPLATE, spoKey, SolrConstants.REPRESENTATIVE_SORT));
        solrQuery.set(SolrConstants.EXPAND, true);
        solrQuery.set(SolrConstants.EXPAND_FIELD, spoKey);
        solrQuery.set(SolrConstants.EXPAND_SORT, SolrConstants.REPRESENTATIVE_SORT);
        solrQuery.set(SolrConstants.EXPAND_ROWS, SolrConstants.GROUP_MEMBER_LIMIT);
        // spo_key is docValues-only (not stored): request it so each representative carries the key
        // used to join its expanded members, and append it as the total-order paging tiebreaker.
        solrQuery.addField(spoKey);
        solrQuery.addSort(spoKey, SolrQuery.ORDER.asc);
    }

    // ---------- Cross-ontology batch mapping (ADR-0024) ----------

    /**
     * Display query for batch term mapping: subjects matched to the given input terms (source-side
     * only — a batch input is a source, never an object), objects restricted to the target ontologies.
     * Each input is classified by shape (IRI → subject_iri, CURIE → subject_id, else label →
     * subject_label) — the default search's classification, but on the subject side alone. Reuses the
     * soft ranking, field list, default weak-predicate exclusion and same-SPO collapse of /search.
     */
    public static SolrQuery buildBatchMapQuery(List<String> terms, List<String> objectPrefixes,
                                               List<InferenceType> inferenceTypes, boolean groupBySpo,
                                               Pageable pageable) {
        SolrQuery solrQuery = new SolrQuery();
        solrQuery.setStart((int) pageable.getOffset());
        solrQuery.setRows(pageable.getPageSize());
        solrQuery.setQuery(subjectSideDisjunction(terms));
        solrQuery.set(SolrConstants.DEF_TYPE, SolrConstants.EDISMAX);
        applyInferenceRanking(solrQuery);
        solrQuery.setFields(constructFieldList(null));
        solrQuery.setFilterQueries(batchFilterQueries(objectPrefixes, inferenceTypes));
        if (groupBySpo) {
            applySpoGrouping(solrQuery);
        }
        return solrQuery;
    }

    /**
     * Unmapped-input probe for batch mapping: a {@code rows=0} query over the same target/inference
     * filters, carrying one {@code facet.query} per input (its subject-side clause). Each input's
     * facet count is the number of mappings it produces into the targets; a zero count means the input
     * is unmapped. One Solr request settles the whole unmapped set, independent of the display paging.
     */
    public static SolrQuery buildBatchMapUnmappedQuery(List<String> terms, List<String> objectPrefixes,
                                                       List<InferenceType> inferenceTypes) {
        SolrQuery solrQuery = new SolrQuery("*:*");
        solrQuery.setRows(0);
        solrQuery.setFilterQueries(batchFilterQueries(objectPrefixes, inferenceTypes));
        solrQuery.setFacet(true);
        if (terms != null) {
            for (String term : terms) {
                String clause = subjectSideClause(term);
                if (clause != null) {
                    solrQuery.addFacetQuery(clause);
                }
            }
        }
        return solrQuery;
    }

    /**
     * The subject-side query clause for one input term, classified by shape, or null if blank. Public
     * so the batch controller can recompute an input's clause to look up its {@code facet.query} count.
     */
    public static String subjectSideClause(String term) {
        if (term == null) {
            return null;
        }
        String stripped = term.strip();
        if (stripped.isEmpty()) {
            return null;
        }
        String field;
        String value;
        if (StringUtils.isIri(stripped)) {
            field = MappingEnum.SUBJECT_IRI.getField();
            value = stripped;
        } else if (StringUtils.isCurie(stripped)) {
            // Normalise the CURIE to its stored representation (prefix upper-cased) so a lower-cased
            // input still matches the indexed subject_id.
            field = MappingEnum.SUBJECT_ID.getField();
            value = new EntityReference(stripped).getDataRepresentation()
                    .map(Object::toString).orElse(stripped);
        } else {
            field = MappingEnum.SUBJECT_LABEL.getField();
            value = stripped;
        }
        return "(" + field + ":\"" + ClientUtils.escapeQueryChars(value) + "\")";
    }

    private static String subjectSideDisjunction(List<String> terms) {
        if (terms == null || terms.isEmpty()) {
            return "*:*";
        }
        String query = terms.stream()
                .map(SolrQueryBuilder::subjectSideClause)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" OR "));
        return query.isEmpty() ? "*:*" : query;
    }

    /**
     * Filter queries shared by the batch display and unmapped-probe queries: target-ontology
     * restriction (object_prefix), the inference-type filter, and the default weak-predicate exclusion
     * (batch inputs are sources, so there is never an explicit predicate filter to lift it).
     */
    private static String[] batchFilterQueries(List<String> objectPrefixes,
                                               List<InferenceType> inferenceTypes) {
        List<String> filterQueriesList = new ArrayList<>();
        String inferenceClause = inferenceTypeFilterClause(inferenceTypes);
        if (inferenceClause != null) {
            filterQueriesList.add(inferenceClause);
        }
        addPrefixFilter(filterQueriesList, MappingEnum.OBJECT_PREFIX, objectPrefixes);
        filterQueriesList.add(weakPredicateExclusionClause());
        return filterQueriesList.toArray(new String[0]);
    }

    /**
     * Export query for /search-style cross-ontology results (ADR-0024): the same q + filter queries as
     * {@link #buildSolrQuery}, but flat (no same-SPO collapse) and sorted by the unique key so a
     * cursorMark stream can read the whole result. rows / fl / cursorMark are set by the exporter.
     */
    public static SolrQuery buildExportQuery(MappingSearchRequest request) {
        SolrQuery solrQuery = new SolrQuery();
        applyQuery(solrQuery, request);
        solrQuery.set(SolrConstants.DEF_TYPE, SolrConstants.EDISMAX);
        boolean excludeWeakPredicates = !hasExplicitPredicateFilter(request);
        solrQuery.setFilterQueries(constructFilterQueries(
                request.getColumnFilters(),
                request.getMappingSetIds(),
                request.getInferenceType(),
                request.getSubjectPrefixes(),
                request.getObjectPrefixes(),
                excludeWeakPredicates));
        solrQuery.setSort(UNIQUE_KEY, SolrQuery.ORDER.asc);
        return solrQuery;
    }

    /**
     * Per-term query for the OxO v1 {@code /api/search} adapter (ADR-0024): one input term matched on
     * the subject side, objects restricted to the v1 {@code mappingTarget} datasources, with v1's
     * {@code distance} mapped onto the inference tiers — {@code distance == 1} → asserted only;
     * anything else (incl. {@code -1} = unlimited) → asserted ∪ inferred (no tier filter).
     */
    public static SolrQuery buildV1TermQuery(String term, List<String> mappingTarget, int distance,
                                             int maxRows) {
        SolrQuery solrQuery = new SolrQuery();
        String clause = subjectSideClause(term);
        solrQuery.setQuery(clause == null ? "*:*" : clause);
        solrQuery.set(SolrConstants.DEF_TYPE, SolrConstants.EDISMAX);
        List<InferenceType> inferenceTypes = (distance == 1) ? List.of(InferenceType.ASSERTED) : null;
        solrQuery.setFilterQueries(batchFilterQueries(mappingTarget, inferenceTypes));
        // mapping_id is requested so Mapping.Builder.build() uses it rather than re-deriving a UUID
        // (which would NPE without mapping_set_id in the field list).
        solrQuery.setFields(
                MappingEnum.MAPPING_ID.getField(),
                MappingEnum.SUBJECT_ID.getField(), MappingEnum.SUBJECT_LABEL.getField(),
                MappingEnum.OBJECT_ID.getField(), MappingEnum.OBJECT_LABEL.getField(),
                MappingEnum.INFERENCE_TYPE.getField());
        solrQuery.setRows(maxRows);
        return solrQuery;
    }

    /**
     * Query for the OxO v1 {@code GET /api/mappings} compatibility endpoint (ADR-0025). Reproduces v1's
     * undirected term filter over the mappings index:
     * <ul>
     *   <li>both {@code fromId} and {@code toId} → mappings between the two terms in either
     *       direction;</li>
     *   <li>{@code fromId} only → mappings with that term as subject <em>or</em> object;</li>
     *   <li>{@code toId} only → no term filter (v1 ignored a lone {@code toId} and returned all
     *       mappings; reproduced verbatim rather than "fixed", since the {@code fromId}-only form is
     *       already undirected and answers the into-a-term question);</li>
     *   <li>neither → all mappings.</li>
     * </ul>
     * Always restricted to asserted mappings (v1 exposed no inference tier here). The weak predicates
     * {@code rdfs:subClassOf} / {@code oboInOwl:hasDbXref} are shown by default — v1 was built on
     * xrefs — and hidden only when {@code hideWeakPredicates} is set. Sorted by the unique key so paging
     * is stable; no same-SPO collapse, so every stored mapping is a distinct row as in v1.
     */
    public static SolrQuery buildV1MappingsQuery(String fromId, String toId, boolean hideWeakPredicates,
                                                 Pageable pageable) {
        SolrQuery solrQuery = new SolrQuery();
        solrQuery.setStart((int) pageable.getOffset());
        solrQuery.setRows(pageable.getPageSize());
        solrQuery.setQuery(v1MappingsQueryClause(fromId, toId));

        List<String> filterQueriesList = new ArrayList<>();
        filterQueriesList.add(
                MappingEnum.INFERENCE_TYPE.getField() + ":" + InferenceType.ASSERTED.getCode());
        if (hideWeakPredicates) {
            filterQueriesList.add(weakPredicateExclusionClause());
        }
        solrQuery.setFilterQueries(filterQueriesList.toArray(new String[0]));

        solrQuery.setSort(UNIQUE_KEY, SolrQuery.ORDER.asc);
        return solrQuery;
    }

    /** The undirected subject/object term clause for v1 {@code /api/mappings} (see above). */
    private static String v1MappingsQueryClause(String fromId, String toId) {
        boolean hasFrom = fromId != null && !fromId.isBlank();
        boolean hasTo = toId != null && !toId.isBlank();
        if (hasFrom && hasTo) {
            String fromAsSubject = termIdClause(MappingEnum.SUBJECT_ID, MappingEnum.SUBJECT_IRI, fromId);
            String fromAsObject = termIdClause(MappingEnum.OBJECT_ID, MappingEnum.OBJECT_IRI, fromId);
            String toAsSubject = termIdClause(MappingEnum.SUBJECT_ID, MappingEnum.SUBJECT_IRI, toId);
            String toAsObject = termIdClause(MappingEnum.OBJECT_ID, MappingEnum.OBJECT_IRI, toId);
            return "((" + fromAsSubject + " AND " + toAsObject + ") OR ("
                    + toAsSubject + " AND " + fromAsObject + "))";
        }
        if (hasFrom) {
            String fromAsSubject = termIdClause(MappingEnum.SUBJECT_ID, MappingEnum.SUBJECT_IRI, fromId);
            String fromAsObject = termIdClause(MappingEnum.OBJECT_ID, MappingEnum.OBJECT_IRI, fromId);
            return "(" + fromAsSubject + " OR " + fromAsObject + ")";
        }
        // toId-only (v1 quirk) and the no-argument case both match everything.
        return "*:*";
    }

    /**
     * Exact-match clause for a term on one side, classified by shape: an IRI matches the {@code *_iri}
     * field; anything else is treated as a CURIE and normalised to its stored representation (prefix
     * upper-cased, as EntityReference indexes it) before matching the {@code *_id} field.
     */
    private static String termIdClause(MappingEnum idField, MappingEnum iriField, String value) {
        String stripped = value.strip();
        if (StringUtils.isIri(stripped)) {
            return iriField.getField() + ":\"" + ClientUtils.escapeQueryChars(stripped) + "\"";
        }
        String normalised = new EntityReference(stripped).getDataRepresentation()
                .map(Object::toString).orElse(stripped);
        return idField.getField() + ":\"" + ClientUtils.escapeQueryChars(normalised) + "\"";
    }

    /**
     * Export query for batch term mapping: the subject-side disjunction and batch filters of
     * {@link #buildBatchMapQuery}, flat and sorted by the unique key for cursorMark streaming.
     */
    public static SolrQuery buildBatchExportQuery(List<String> terms, List<String> objectPrefixes,
                                                  List<InferenceType> inferenceTypes) {
        SolrQuery solrQuery = new SolrQuery();
        solrQuery.setQuery(subjectSideDisjunction(terms));
        solrQuery.set(SolrConstants.DEF_TYPE, SolrConstants.EDISMAX);
        solrQuery.setFilterQueries(batchFilterQueries(objectPrefixes, inferenceTypes));
        solrQuery.setSort(UNIQUE_KEY, SolrQuery.ORDER.asc);
        return solrQuery;
    }


    private static String[] constructFilterQueries(List<MappingSearchRequest.ColumnFilter> queryFilters,
                                                   List<String> mappingSetIds,
                                                   List<InferenceType> inferenceTypes,
                                                   List<String> subjectPrefixes,
                                                   List<String> objectPrefixes,
                                                   boolean excludeWeakPredicates) {
        List<String> filterQueriesList = new ArrayList<>();

        if (queryFilters != null) {
            queryFilters.stream()
                    .map(SolrQueryBuilder::constructFilterClause)
                    .filter(clause -> !clause.isEmpty())
                    .forEach(filterQueriesList::add);
        }

        // Cross-ontology mapping (ADR-0024): restrict subject/object to the given ontologies via an
        // OR'd exact-term filter on the denormalised subject_prefix / object_prefix fields. Directional
        // (subject = source, object = target). The closure is precomputed (ADR-0016), so this is a
        // plain filter, not a traversal.
        addPrefixFilter(filterQueriesList, MappingEnum.SUBJECT_PREFIX, subjectPrefixes);
        addPrefixFilter(filterQueriesList, MappingEnum.OBJECT_PREFIX, objectPrefixes);

        // Multi-select inference-type filter (ADR-0011).
        String inferenceClause = inferenceTypeFilterClause(inferenceTypes);
        if (inferenceClause != null) {
            filterQueriesList.add(inferenceClause);
        }

        if (mappingSetIds != null && !mappingSetIds.isEmpty()) {
            String field = MappingEnum.MAPPING_SET_ID.getField();
            String clause = mappingSetIds.stream()
                    .filter(id -> id != null && !id.isBlank())
                    .map(id -> field + ":\"" + ClientUtils.escapeQueryChars(id) + "\"")
                    .collect(Collectors.joining(" OR "));
            if (!clause.isEmpty()) {
                filterQueriesList.add("(" + clause + ")");
            }
        }

        if (excludeWeakPredicates) {
            filterQueriesList.add(weakPredicateExclusionClause());
        }

        return filterQueriesList.toArray(new String[filterQueriesList.size()]);
    }

    /**
     * Multi-select inference-type filter clause (ADR-0011), or null if none. inference_type is a
     * denormalised string whose values are the InferenceType codes (safe enum names, no escaping
     * needed); an OR of exact term matches, never the substring columnFilter path.
     */
    private static String inferenceTypeFilterClause(List<InferenceType> inferenceTypes) {
        if (inferenceTypes == null || inferenceTypes.isEmpty()) {
            return null;
        }
        String field = MappingEnum.INFERENCE_TYPE.getField();
        String clause = inferenceTypes.stream()
                .filter(Objects::nonNull)
                .map(InferenceType::getCode)
                .distinct()
                .map(code -> field + ":" + code)
                .collect(Collectors.joining(" OR "));
        return clause.isEmpty() ? null : "(" + clause + ")";
    }

    /**
     * Add an OR-of-exact-terms filter clause restricting an ontology-prefix field to the given
     * prefixes, or nothing if the list is null/empty. Prefix values are escaped (a user could pass an
     * arbitrary string), then matched as exact terms on the {@code string}-typed prefix field.
     */
    private static void addPrefixFilter(List<String> filterQueriesList, MappingEnum prefixField,
                                        List<String> prefixes) {
        if (prefixes == null || prefixes.isEmpty()) {
            return;
        }
        String field = prefixField.getField();
        String clause = prefixes.stream()
                .filter(prefix -> prefix != null && !prefix.isBlank())
                .map(prefix -> field + ":" + ClientUtils.escapeQueryChars(prefix.strip()))
                .collect(Collectors.joining(" OR "));
        if (!clause.isEmpty()) {
            filterQueriesList.add("(" + clause + ")");
        }
    }

    /**
     * Pure-negative filter excluding the {@link #DEFAULT_EXCLUDED_PREDICATE_IRIS}. The leading
     * {@code *:*} is required because Solr matches nothing for a filter query that is only negative;
     * it supplies the all-docs base from which the predicates are subtracted. Each IRI is escaped and
     * quoted (an exact term match on the {@code string}-typed {@code predicate_iri} field), mirroring
     * the mapping_set_id clause above.
     */
    private static String weakPredicateExclusionClause() {
        String field = MappingEnum.PREDICATE_IRI.getField();
        String excluded = DEFAULT_EXCLUDED_PREDICATE_IRIS.stream()
                .map(iri -> field + ":\"" + ClientUtils.escapeQueryChars(iri) + "\"")
                .collect(Collectors.joining(" OR "));
        return "*:* -(" + excluded + ")";
    }

    /**
     * True when the request explicitly constrains a predicate field — via a column filter or an
     * advanced field query carrying a non-blank value. Such a caller is asking about predicates
     * directly, so the default weak-predicate exclusion is switched off for that request. Note the
     * default (classified) search path that merely searches {@code predicate_*} alongside subject and
     * object does <em>not</em> count: that is a broad term search, not an explicit predicate filter.
     */
    private static boolean hasExplicitPredicateFilter(MappingSearchRequest request) {
        List<MappingSearchRequest.ColumnFilter> columnFilters = request.getColumnFilters();
        if (columnFilters != null) {
            for (MappingSearchRequest.ColumnFilter filter : columnFilters) {
                if (isPredicateField(filter.getId()) && isNonBlank(filter.getValue())) {
                    return true;
                }
            }
        }
        List<FieldQuery> advancedFieldQueries = request.getAdvancedFieldQueries();
        if (advancedFieldQueries != null) {
            for (FieldQuery fieldQuery : advancedFieldQueries) {
                if (isPredicateField(fieldQuery.getField()) && isNonBlank(fieldQuery.getValue())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isPredicateField(String fieldId) {
        MappingEnum field = MappingEnum.fromString(fieldId);
        return field != null && PREDICATE_FILTER_FIELDS.contains(field);
    }

    private static boolean isNonBlank(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Builds one Solr filter clause ("contains" semantics) for a single column filter.
     *
     * <p>Label fields are backed by an n-gram field whose indexed terms are n-grams of
     * individual words: the analyzer tokenises on whitespace <em>before</em> generating
     * n-grams, so no indexed term contains a space. A single {@code *value*} wildcard
     * therefore cannot match a multi-word value — e.g. {@code "CDISC Questionnaire"}
     * matched nothing. We split the value on whitespace and AND one substring wildcard
     * per word, giving order-independent "contains all of these words" matching while
     * preserving the partial-word (substring) matching a single wildcard provides.
     *
     * <p>Non-label fields are {@code string}-typed: the whole value is a single indexed
     * term, so an escaped-space wildcard already matches a literal multi-word substring.
     * They keep the single-clause form.
     *
     * <p>Every word is passed through {@link ClientUtils#escapeQueryChars} so user input
     * cannot inject query syntax.
     */
    private static String constructFilterClause(MappingSearchRequest.ColumnFilter filter) {
        MappingEnum field = MappingEnum.fromString(filter.getId());
        String value = filter.getValue() == null ? "" : filter.getValue().strip();
        if (value.isEmpty()) {
            return "";
        }

        if (textGeneralToNGram.containsKey(field)) {
            String ngramField = textGeneralToNGram.get(field);
            return Arrays.stream(value.split("\\s+"))
                    .filter(word -> !word.isEmpty())
                    .map(word -> ngramField + ":*" + ClientUtils.escapeQueryChars(word) + "*")
                    .collect(Collectors.joining(" AND ", "(", ")"));
        }

        return field.getField() + ":*" + ClientUtils.escapeQueryChars(value) + "*";
    }

    /**
     * Solr can only sort on docValue fields. Text_general fields (used for labels) are not docValue fields.
     * To enable sorting these fields are copied to _str fields that are docValue fields.
     *
     *
     * @param solrQuery
     * @param mappingSearchRequest
     * @return
     */
    private static SolrQuery constructSortedFields(SolrQuery solrQuery, MappingSearchRequest mappingSearchRequest) {
        if (mappingSearchRequest.getSortedFields() != null) {
            for (SortedField sortedField : mappingSearchRequest.getSortedFields()) {
                solrQuery.addSort(
                        textGeneralToDocValues.containsKey(sortedField.getId()) ?
                            textGeneralToDocValues.get(sortedField.getId()) :
                            sortedField.getId().getField(),
                        sortedField.isDesc() == true ? SolrQuery.ORDER.desc : SolrQuery.ORDER.asc
                );
            }
        }
        return solrQuery;
    }

    /**
     * Query construction used by the override path (caller-pinned {@code queryFields}).
     * Joins all terms with {@code OR}; relies on edismax {@code qf} to select fields.
     */
    private static String constructUsingQueryFields(List<String> queries) {
        if (queries == null || queries.isEmpty()) {
            return "*:*";
        }

        String query = queries.stream()
                .map(q -> ClientUtils.escapeQueryChars(q))
                .collect(Collectors.joining(" OR "));

        logger.debug("Legacy query string: {}", query);
        return query;
    }

    /**
     * Default query construction. Classifies each term by shape and emits a parenthesised
     * OR-clause across the type-appropriate fields:
     * <ul>
     *   <li>IRI ({@code http(s)://...}) → subject_iri / object_iri / predicate_iri</li>
     *   <li>CURIE ({@code prefix:local}) → subject_id / object_id / predicate_id</li>
     *   <li>Free text → subject_label / object_label / predicate_label (phrase match)</li>
     * </ul>
     * Quoting the value gives a TermQuery on {@code string} fields and a PhraseQuery on
     * {@code text_general} fields after analysis.
     */
    private static String constructClassifiedQuery(List<String> queries) {
        if (queries == null || queries.isEmpty()) {
            return "*:*";
        }

        List<String> clauses = new ArrayList<>();
        for (String raw : queries) {
            if (raw == null) continue;
            String term = raw.strip();
            if (term.isEmpty()) continue;

            String escaped = ClientUtils.escapeQueryChars(term);
            String[] fields;
            if (StringUtils.isIri(term)) {
                fields = new String[]{
                        MappingEnum.SUBJECT_IRI.getField(),
                        MappingEnum.OBJECT_IRI.getField(),
                        MappingEnum.PREDICATE_IRI.getField()
                };
            } else if (StringUtils.isCurie(term)) {
                fields = new String[]{
                        MappingEnum.SUBJECT_ID.getField(),
                        MappingEnum.OBJECT_ID.getField(),
                        MappingEnum.PREDICATE_ID.getField()
                };
            } else {
                fields = new String[]{
                        MappingEnum.SUBJECT_LABEL.getField(),
                        MappingEnum.OBJECT_LABEL.getField(),
                        MappingEnum.PREDICATE_LABEL.getField()
                };
            }

            StringBuilder clause = new StringBuilder("(");
            for (int i = 0; i < fields.length; i++) {
                if (i > 0) clause.append(" OR ");
                clause.append(fields[i]).append(":\"").append(escaped).append("\"");
            }
            clause.append(")");
            clauses.add(clause.toString());
        }

        if (clauses.isEmpty()) {
            return "*:*";
        }

        String query = String.join(" OR ", clauses);
        logger.debug("Classified query string: {}", query);
        return query;
    }

    /**
     * Advanced search path. Builds an AND-joined query from per-field (field, value) pairs.
     * Each clause is {@code (field:"<escaped value>")}. The same syntax works for
     * {@code string} (exact term) and {@code text_general} (analyzed phrase) field types —
     * Solr applies the appropriate semantics based on the field type from the schema.
     * For multiValued fields, Solr matches if any element in the list matches; no special
     * handling needed here.
     */
    private static String constructAdvancedQuery(List<FieldQuery> advancedFieldQueries) {
        if (advancedFieldQueries == null || advancedFieldQueries.isEmpty()) {
            return "*:*";
        }

        List<String> clauses = new ArrayList<>();
        for (FieldQuery fq : advancedFieldQueries) {
            if (fq == null || fq.getField() == null || fq.getValue() == null) continue;
            String value = fq.getValue().strip();
            if (value.isEmpty()) continue;

            MappingEnum me = MappingEnum.fromString(fq.getField());
            if (me == null) {
                logger.warn("Unknown field in advancedFieldQueries: {}", fq.getField());
                continue;
            }

            String escaped = ClientUtils.escapeQueryChars(value);
            clauses.add("(" + me.getField() + ":\"" + escaped + "\")");
        }

        if (clauses.isEmpty()) {
            return "*:*";
        }

        String query = String.join(" AND ", clauses);
        logger.debug("Advanced query string: {}", query);
        return query;
    }

    private static String[] constructQueryFields(List<MappingEnum> queryFields) {
        String[] fields = new String[queryFields.size()];
        queryFields.forEach(f -> fields[queryFields.indexOf(f)] = f.getField());
        return fields;
    }

    private static String[] constructFieldList(List<MappingEnum> fieldList) {
        Set<String> fieldsSet = new HashSet<>();

        if (fieldList != null) {
            fieldList.forEach(f -> {
                if (f != null) {
                    fieldsSet.add(f.getField());
                }
            });
        }

        for (String minimalField : MINIMAL_LIST_OF_FIELDS) {
            fieldsSet.add(minimalField);
        }

        return fieldsSet.toArray(new String[0]);
    }

    /**
     * Apply the soft inference-type + distance ranking (ADR-0011) as a multiplicative edismax
     * {@code boost} function. Requires {@code defType=edismax}. The boost multiplies the relevance
     * score, so every tier still appears — asserted/SSSOM just float up, and within the inferred
     * results shorter chains rank above longer ones — without a hard filter or sort.
     */
    private static void applyInferenceRanking(SolrQuery solrQuery) {
        solrQuery.set(SolrConstants.BOOST, RANKING_BOOST);
    }
}
