package uk.ac.ebi.spot.oxo.backend.service.helper;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.params.FacetParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.request.FieldQuery;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.request.MappingSearchRequest;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.request.MappingSearchRequest.ColumnFilter;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.request.SortedField;
import uk.ac.ebi.spot.oxo.model.sssom.EntityReference;
import uk.ac.ebi.spot.oxo.model.sssom.FilterMatchType;
import uk.ac.ebi.spot.oxo.model.sssom.InferenceType;
import uk.ac.ebi.spot.oxo.model.sssom.LabelMatchType;
import uk.ac.ebi.spot.oxo.model.sssom.MappingEnum;
import uk.ac.ebi.spot.oxo.model.sssom.MappingSetCategory;
import uk.ac.ebi.spot.oxo.model.sssom.WeakPredicate;
import uk.ac.ebi.spot.oxo.utils.StringUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static org.apache.solr.common.params.DisMaxParams.QF;
import static uk.ac.ebi.spot.oxo.model.sssom.MappingEnum.MINIMAL_LIST_OF_FIELDS;

public class SolrQueryBuilder {

    private static final Logger logger = LoggerFactory.getLogger(SolrQueryBuilder.class);

    // ---------- Provenance-led ranking (ADR-0027, superseding ADR-0011's boost) ----------
    //
    // Ranking tiers, strongest first. Each tier is a multiplicative factor; the tiers below only ever
    // break ties within a tier above (see #rankingTiersAreLexicographic below and the unit test of the
    // same name). "Trust provenance over predicate": who asserts a mapping outweighs how strong the
    // predicate is, because a skos:exactMatch in an unreviewed lexical-match file is weaker evidence
    // than an ontology's own skos:closeMatch cross-reference.
    //
    //   1. Provenance    ontology-asserted > curator-asserted > inferred (fewer hops first)
    //   2. Predicate     strict identity > exactMatch > closeMatch > broad/narrow > everything else
    //   3. Curation      manually curated > everything else
    //   4. Confidence    the mapping's own confidence, when it declares one
    //
    // Recency is deliberately NOT a boost factor: mapping_date is sparsely populated and a date
    // function in the boost would silently reorder on every query. It is offered as an explicit sort.

    /** Tier 1. An ontology's own asserted cross-references (mapping_set_category = ONTOLOGY). */
    private static final double ONTOLOGY_BOOST = 10_000;
    /** Tier 1. A curator's asserted mapping — also the pre-reindex fallback for any asserted doc. */
    private static final double CURATED_BOOST = 1_000;
    /** Tier 1. An OxO-derived mapping, at one hop. */
    private static final double INFERRED_BOOST = 100;
    /** Tier 1. Each extra inference hop divides the inferred boost by this. */
    private static final double DISTANCE_DECAY = 5;

    /** Tier 2. owl:equivalentClass / owl:equivalentProperty / owl:sameAs — logical identity (ADR-0016). */
    private static final double PREDICATE_STRICT_IDENTITY = 2.0;
    /** Tier 2. skos:exactMatch — interchangeable in practice, but not logical identity (ADR-0016). */
    private static final double PREDICATE_WEAK_IDENTITY = 1.7;
    /** Tier 2. skos:closeMatch. */
    private static final double PREDICATE_CLOSE = 1.4;
    /** Tier 2. skos:broadMatch / skos:narrowMatch — a hierarchy edge, not a mapping between equals. */
    private static final double PREDICATE_HIERARCHY = 1.2;
    /** Tier 2 floor. skos:relatedMatch, oboInOwl:hasDbXref, the crossSpecies predicates, anything else. */
    private static final double PREDICATE_OTHER = 1.0;

    /** Tier 3. semapv:ManualMappingCuration — a human decided this mapping. */
    private static final double CURATION_MANUAL = 1.3;
    /** Tier 3 floor. Lexical matching, logical reasoning, unspecified — anything not hand-curated. */
    private static final double CURATION_OTHER = 1.0;

    /** Tier 4. confidence in [0,1] scales the boost by 1 + this; absent confidence contributes 1.0. */
    private static final double CONFIDENCE_WEIGHT = 0.3;

    /**
     * Predicate IRIs by tier-2 strength. A doc matches at most one IRI, so the strengths are unordered
     * semantically — but the iteration order must be stable, or the generated boost string would differ
     * between JVM runs. Hence an insertion-ordered map, not {@code Map.of}.
     */
    private static final Map<Double, List<String>> PREDICATE_STRENGTH_IRIS = predicateStrengthIris();

    private static Map<Double, List<String>> predicateStrengthIris() {
        Map<Double, List<String>> byStrength = new LinkedHashMap<>();
        byStrength.put(PREDICATE_STRICT_IDENTITY, List.of(
                "http://www.w3.org/2002/07/owl#equivalentClass",
                "http://www.w3.org/2002/07/owl#equivalentProperty",
                "http://www.w3.org/2002/07/owl#sameAs"));
        byStrength.put(PREDICATE_WEAK_IDENTITY, List.of("http://www.w3.org/2004/02/skos/core#exactMatch"));
        byStrength.put(PREDICATE_CLOSE, List.of("http://www.w3.org/2004/02/skos/core#closeMatch"));
        byStrength.put(PREDICATE_HIERARCHY, List.of(
                "http://www.w3.org/2004/02/skos/core#broadMatch",
                "http://www.w3.org/2004/02/skos/core#narrowMatch"));
        return Collections.unmodifiableMap(byStrength);
    }

    /**
     * mapping_justification values meaning "a human curated this". A {@code string} field, so the match
     * is case-sensitive and the prefix casing varies with each set's curie_map — hence both observed
     * spellings. A justification we do not recognise simply falls to {@link #CURATION_OTHER}, so a
     * missed spelling costs a tie-break, never a wrong result. (A {@code _ci} copy, as ADR-0026 added
     * for labels, would remove the guesswork.)
     */
    private static final List<String> MANUAL_CURATION_JUSTIFICATIONS = List.of(
            "semapv:ManualMappingCuration", "SEMAPV:ManualMappingCuration");

    /**
     * Soft ranking as a MULTIPLICATIVE edismax boost function, so a highly relevant inferred mapping
     * can still outrank a weakly matching asserted one. Multiplicative — not an additive {@code bq} —
     * because the tier boost must be independent of term idf: ASSERTED is common (low idf) and
     * SSSOM_INFERENCE rare (high idf), so an additive {@code bq} would actually boost SSSOM more than
     * ASSERTED, inverting the intended order (ADR-0011).
     *
     * <p>Pre-reindex, {@code mapping_set_category} is empty on every doc (ADR-0027), so every asserted
     * mapping scores {@link #CURATED_BOOST} and the ranking degrades exactly to ADR-0011's
     * asserted-over-inferred order rather than to nonsense. Tiers 2–4 read fields that already exist,
     * so they take effect immediately.
     */
    private static final String RANKING_BOOST = "mul("
            + provenanceFactor() + "," + predicateStrengthFactor() + ","
            + curationFactor() + "," + confidenceFactor() + ")";

    /**
     * Tier 1. Keyed on inference_type (always present) rather than on the category, so that a doc with
     * no category is read as "asserted, corpus unknown" — the pre-reindex state — and not as inferred.
     * Inferred docs decay by {@link #DISTANCE_DECAY} per hop; a missing distance reads as one hop.
     */
    private static String provenanceFactor() {
        String assertedTier = "if(termfreq(" + MappingEnum.MAPPING_SET_CATEGORY.getField() + ",'"
                + MappingSetCategory.ONTOLOGY.getCode() + "')," + num(ONTOLOGY_BOOST) + ","
                + num(CURATED_BOOST) + ")";
        String inferredTier = "div(" + num(INFERRED_BOOST) + ",pow(" + num(DISTANCE_DECAY)
                + ",sub(def(" + MappingEnum.DISTANCE.getField() + ",1),1)))";
        return "if(termfreq(" + MappingEnum.INFERENCE_TYPE.getField() + ",'"
                + InferenceType.ASSERTED.getCode() + "')," + assertedTier + "," + inferredTier + ")";
    }

    /**
     * Tier 2. {@code predicate_iri} is single-valued, so at most one termfreq is 1 and the rest are 0;
     * {@code max(...)} therefore selects the matching strength, falling through to the
     * {@link #PREDICATE_OTHER} floor when the predicate is one we do not rank.
     */
    private static String predicateStrengthFactor() {
        String field = MappingEnum.PREDICATE_IRI.getField();
        StringBuilder factor = new StringBuilder("max(");
        PREDICATE_STRENGTH_IRIS.forEach((strength, iris) -> {
            String anyOf = iris.stream()
                    .map(iri -> "termfreq(" + field + ",'" + iri + "')")
                    .collect(Collectors.joining(","));
            factor.append("mul(").append(num(strength)).append(",")
                    .append(iris.size() == 1 ? anyOf : "sum(" + anyOf + ")").append("),");
        });
        return factor.append(num(PREDICATE_OTHER)).append(")").toString();
    }

    /** Tier 3. Same single-valued {@code max(...)} shape as the predicate factor. */
    private static String curationFactor() {
        String field = MappingEnum.MAPPING_JUSTIFICATION.getField();
        String anyOf = MANUAL_CURATION_JUSTIFICATIONS.stream()
                .map(justification -> "termfreq(" + field + ",'" + justification + "')")
                .collect(Collectors.joining(","));
        return "max(mul(" + num(CURATION_MANUAL) + ",sum(" + anyOf + ")),"
                + num(CURATION_OTHER) + ")";
    }

    /** Tier 4. {@code 1 + weight * confidence}; a doc without a confidence contributes exactly 1. */
    private static String confidenceFactor() {
        return "sum(1,mul(" + num(CONFIDENCE_WEIGHT) + ",def("
                + MappingEnum.CONFIDENCE.getField() + ",0)))";
    }

    /**
     * The invariant that makes the tiers a ranking rather than a blend: the closest two provenance
     * values differ by more than the widest possible swing of every lower tier combined, so no
     * predicate, curation or confidence advantage can lift a curated mapping above an ontology one.
     * Asserted by {@code SolrQueryBuilderTest#rankingTiersAreLexicographic}; kept here so a future
     * tweak to any constant above is checked against it.
     *
     * @return true when tier 1 strictly dominates tiers 2–4.
     */
    static boolean rankingTiersAreLexicographic() {
        double closestProvenanceRatio = Math.min(
                Math.min(ONTOLOGY_BOOST / CURATED_BOOST, CURATED_BOOST / INFERRED_BOOST),
                DISTANCE_DECAY);
        double widestLowerTierSwing = (PREDICATE_STRICT_IDENTITY / PREDICATE_OTHER)
                * (CURATION_MANUAL / CURATION_OTHER)
                * (1 + CONFIDENCE_WEIGHT);
        return closestProvenanceRatio > widestLowerTierSwing;
    }

    /** Render a boost constant without a locale-dependent separator or a stray trailing {@code .0}. */
    private static String num(double value) {
        return value == Math.rint(value)
                ? String.valueOf((long) value)
                : BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

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
     * The case-insensitive exact-match copy of a label field ({@code string_ci}: keyword tokenizer +
     * lowercase). Populated by copyField in the mappings schema; see ADR-0026.
     */
    private static String textGeneralFieldAsCaseInsensitive(MappingEnum mappingEnum) {
        return mappingEnum.getField() + "_ci";
    }

    /** The subject label field a classified free-text clause targets, per match mode (ADR-0026). */
    private static String subjectLabelFieldFor(LabelMatchType labelMatch) {
        LabelMatchType mode = labelMatch == null ? LabelMatchType.DEFAULT : labelMatch;
        return switch (mode) {
            case PARTIAL -> MappingEnum.SUBJECT_LABEL.getField();
            case EXACT_CASE_INSENSITIVE -> textGeneralFieldAsCaseInsensitive(MappingEnum.SUBJECT_LABEL);
            case EXACT_CASE_SENSITIVE -> textGeneralFieldAsString(MappingEnum.SUBJECT_LABEL);
        };
    }

    /**
     * The weak predicates, hidden from mapping-search results unless asked for. The inference corpus
     * has no rule that chains them and their objects are frequently bare database codes (see
     * {@code ApplicablePredicatesEnum} in oxo2-json2inferences), so they add noise to ordinary
     * searches. Matched on {@code predicate_iri} (the canonical, prefix-independent IRI) rather than
     * {@code predicate_id}, whose stored CURIE prefix and casing vary by source set.
     *
     * <p>The exclusion is lifted two ways. Explicitly, per predicate, by
     * {@code MappingSearchRequest.includeWeakPredicates} — the two search-page checkboxes (ADR-0035).
     * And implicitly, whenever the caller filters on a predicate field at all (see
     * {@link #hasExplicitPredicateFilter}), so it can never hide a row the caller deliberately asked
     * for.
     *
     * <p>The list itself lives in {@link WeakPredicate} rather than here, because the entity fold that
     * builds the typeahead must bucket its counts by exactly these predicates: if the two ever
     * disagreed, the typeahead would offer entities whose mappings this filter then hides.
     */
    private static final List<WeakPredicate> ALL_WEAK_PREDICATES = List.of(WeakPredicate.values());

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

        // ADR-0027: every path uses edismax so the soft provenance-led ranking applies
        // uniformly. The fielded queries on the advanced/classified paths carry their own field
        // selectors, which edismax parses unchanged; qf (set above) only affects the queryFields path.
        solrQuery.set(SolrConstants.DEF_TYPE, SolrConstants.EDISMAX);
        applyProvenanceRanking(solrQuery);

        solrQuery.setFields(constructFieldList(mappingSearchRequest.getFieldList()));
        solrQuery.setFilterQueries(constructFilterQueries(
                mappingSearchRequest.getColumnFilters(),
                mappingSearchRequest.getMappingSetIds(),
                mappingSearchRequest.getInferenceType(),
                mappingSearchRequest.getMappingSetCategory(),
                mappingSearchRequest.getSubjectPrefixes(),
                mappingSearchRequest.getObjectPrefixes(),
                hiddenWeakPredicates(mappingSearchRequest)));
        solrQuery = constructSortedFields(solrQuery, mappingSearchRequest);

        if (mappingSearchRequest.isGroupBySpo()) {
            applySpoGrouping(solrQuery);
        }

        return solrQuery;
    }

    // ---------------------------------------------------------------------------------------------
    // Value suggestions (ADR-0034)
    // ---------------------------------------------------------------------------------------------

    /**
     * Global distinct values of one controlled-vocabulary field, most common first. Backs the
     * Advanced tab's vocabulary typeahead: the whole list is fetched once, cached, and filtered
     * client-side — the same shape the ontology-prefix selector uses (ADR-0024).
     *
     * <p>Only {@link SuggestFields#VOCAB_FIELDS} may be passed. A high-cardinality field would
     * enumerate millions of terms, and a {@code text_general} field would return analyzed tokens
     * rather than values; the caller rejects both before reaching here.
     */
    public static SolrQuery buildDistinctValuesQuery(MappingEnum field, int limit) {
        SolrQuery solrQuery = new SolrQuery("*:*");
        solrQuery.setRows(0);
        solrQuery.setFacet(true);
        solrQuery.setFacetMinCount(1);
        solrQuery.setFacetLimit(limit);
        solrQuery.setFacetSort(FacetParams.FACET_SORT_COUNT);
        solrQuery.addFacetField(SuggestFields.facetFieldFor(field));
        return solrQuery;
    }

    /**
     * Suggestions for one field's values WITHIN the live search's result set — so a suggested filter
     * value can never yield zero rows, and it arrives with the count of rows it would leave.
     *
     * <p><b>It reuses {@link #buildSolrQuery}, and that is the point.</b> The suggestions must be
     * scoped by exactly the same restrictions the search itself applies — the other column filters,
     * the weak-predicate exclusion, the corpus / inference-type / ontology-prefix / mapping-set
     * filters. Rebuilding that list here would be a second implementation of it, free to drift; so
     * the real search query is built and then turned into a facet request.
     *
     * <p>Two things are deliberately undone from that query:
     * <ul>
     *   <li><b>the same-SPO collapse</b> — a facet counts documents, and the collapse post-filter
     *       exists only to pick one representative row per triple for display; leaving it on would
     *       make the counts disagree with the filter the user is about to apply, and the expand pass
     *       would be pure waste at {@code rows=0}.</li>
     *   <li><b>the in-progress filter on the field being suggested</b> — otherwise the facet is
     *       scoped by the half-typed value it is trying to complete, so both the values and their
     *       counts would be wrong (the frontend strips it too; this is the half with a test).</li>
     * </ul>
     */
    public static SolrQuery buildValueSuggestQuery(MappingSearchRequest request, MappingEnum field,
                                                   String typedPrefix, int limit) {
        MappingSearchRequest scopedRequest = withoutColumnFilterOn(request, field);
        // A facet needs no page of documents, and no collapse/expand pass to pick representatives.
        scopedRequest.setGroupBySpo(false);

        SolrQuery solrQuery = buildSolrQuery(scopedRequest, PageRequest.of(0, 1));
        solrQuery.setRows(0);
        solrQuery.setFacet(true);
        solrQuery.setFacetMinCount(1);
        solrQuery.setFacetLimit(limit);
        solrQuery.setFacetSort(FacetParams.FACET_SORT_COUNT);
        addPrefixFacets(solrQuery, field, typedPrefix);
        return solrQuery;
    }

    /**
     * Facet {@code field} under each realistic casing of what the user typed, merging the buckets in
     * the caller.
     *
     * <p>{@code facet.prefix} is a RAW BYTE PREFIX over the term dictionary: it is neither analyzed,
     * nor query-parsed, nor case-folded. Two consequences, and both are easy to get wrong:
     *
     * <ul>
     *   <li>It must <b>never</b> be {@code escapeQueryChars}-escaped. Escaping is a query-syntax
     *       concern; here it would put literal backslashes into the term being matched.</li>
     *   <li>It is case-sensitive, and the field it reads preserves the original casing (it must — a
     *       case-folded field would hand back {@code mondo:0005148} as the value to display and
     *       filter on). So a user typing "mel" would miss "Melanoma". Issuing the prefix under a few
     *       casings covers real-world label and CURIE casing. It does not cover a mid-token camelCase
     *       boundary — "oboinowl" will not find "oboInOwl:hasDbXref" — which is the accepted, recorded
     *       gap in ADR-0034; such a value is still reachable by typing it as-cased.</li>
     * </ul>
     *
     * <p>Each casing gets its own {@code {!key=...}} local param so Solr returns it as a separate
     * bucket list rather than silently keeping only the last {@code facet.prefix} for the field.
     */
    private static void addPrefixFacets(SolrQuery solrQuery, MappingEnum field, String typedPrefix) {
        String facetField = SuggestFields.facetFieldFor(field);
        if (typedPrefix == null || typedPrefix.isBlank()) {
            solrQuery.addFacetField(facetField);
            return;
        }

        String typed = typedPrefix.strip();
        // LinkedHashSet: dedupe (an all-lowercase input collides with its own lowercase variant) while
        // keeping as-typed first, so the caller's merge prefers the user's own casing on a tie.
        Set<String> casings = new LinkedHashSet<>();
        casings.add(typed);
        casings.add(typed.toLowerCase(Locale.ROOT));
        casings.add(typed.toUpperCase(Locale.ROOT));
        casings.add(capitalise(typed));

        int keyIndex = 0;
        for (String casing : casings) {
            String key = FACET_CASING_KEY_PREFIX + keyIndex++;
            solrQuery.add(FacetParams.FACET_FIELD,
                    "{!key=" + key + " facet.prefix=" + casing + "}" + facetField);
        }
    }

    /** Prefix of the {@code {!key=...}} names the casing-variant facets come back under. */
    static final String FACET_CASING_KEY_PREFIX = "suggest_";

    private static String capitalise(String value) {
        if (value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1).toLowerCase(Locale.ROOT);
    }

    /**
     * A copy of the request with any column filter on {@code field} removed. A copy, not a mutation:
     * the caller's request is the live search, and quietly stripping a filter from it would change
     * the results the user is looking at.
     */
    private static MappingSearchRequest withoutColumnFilterOn(MappingSearchRequest request,
                                                              MappingEnum field) {
        MappingSearchRequest copy = request.copy();
        List<ColumnFilter> columnFilters = copy.getColumnFilters();
        if (columnFilters != null && !columnFilters.isEmpty()) {
            copy.setColumnFilters(columnFilters.stream()
                    .filter(columnFilter -> !field.getField().equals(columnFilter.getId()))
                    .collect(Collectors.toList()));
        }
        return copy;
    }

    /**
     * Set q on the query (and qf on the override path). Dispatch order, most specific first:
     * <ol>
     *   <li>advancedFieldQueries non-empty → AND-joined per-field clauses (Advanced tab).</li>
     *   <li>queryFields non-empty → legacy edismax/qf path (caller-pinned fields).</li>
     *   <li>otherwise → classified-by-shape path (default search, subject side — ADR-0030).</li>
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
            solrQuery.setQuery(constructClassifiedQuery(request.getQueries(), request.getLabelMatch()));
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
    public static void applySpoGrouping(SolrQuery solrQuery) {
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
     * subject_label) — the default search's subject-side classification (ADR-0030), pinned to partial
     * label matching (batch-map does not expose the label-match mode, ADR-0026). Reuses the soft
     * ranking, field list, default weak-predicate exclusion and same-SPO collapse of /search.
     */
    public static SolrQuery buildBatchMapQuery(List<String> terms, List<String> objectPrefixes,
                                               List<InferenceType> inferenceTypes, boolean groupBySpo,
                                               Pageable pageable) {
        SolrQuery solrQuery = new SolrQuery();
        solrQuery.setStart((int) pageable.getOffset());
        solrQuery.setRows(pageable.getPageSize());
        solrQuery.setQuery(subjectSideDisjunction(terms));
        solrQuery.set(SolrConstants.DEF_TYPE, SolrConstants.EDISMAX);
        applyProvenanceRanking(solrQuery);
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
     * The subject-side query clause for one input term, classified by shape with partial label
     * matching, or null if blank. Public so the batch controller can recompute an input's clause to
     * look up its {@code facet.query} count.
     */
    public static String subjectSideClause(String term) {
        return subjectSideClause(term, LabelMatchType.PARTIAL);
    }

    /**
     * The subject-side query clause for one term, classified by shape, or null if blank. IRI →
     * subject_iri; CURIE → subject_id, normalised to its stored representation (prefix upper-cased)
     * so a lower-cased input still matches; anything else → the subject label field the match mode
     * selects (ADR-0026). The one classification behind the default search (ADR-0030), batch-map and
     * the v1 adapter.
     */
    private static String subjectSideClause(String term, LabelMatchType labelMatch) {
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
            field = MappingEnum.SUBJECT_ID.getField();
            value = new EntityReference(stripped).getDataRepresentation()
                    .map(Object::toString).orElse(stripped);
        } else {
            field = subjectLabelFieldFor(labelMatch);
            value = stripped;
        }
        return "(" + field + ":\"" + ClientUtils.escapeQueryChars(value) + "\")";
    }

    private static String subjectSideDisjunction(List<String> terms) {
        return constructClassifiedQuery(terms, LabelMatchType.PARTIAL);
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
        filterQueriesList.add(weakPredicateExclusionClause(ALL_WEAK_PREDICATES));
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
        solrQuery.setFilterQueries(constructFilterQueries(
                request.getColumnFilters(),
                request.getMappingSetIds(),
                request.getInferenceType(),
                request.getMappingSetCategory(),
                request.getSubjectPrefixes(),
                request.getObjectPrefixes(),
                hiddenWeakPredicates(request)));
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
            filterQueriesList.add(weakPredicateExclusionClause(ALL_WEAK_PREDICATES));
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
                                                   List<MappingSetCategory> mappingSetCategories,
                                                   List<String> subjectPrefixes,
                                                   List<String> objectPrefixes,
                                                   List<WeakPredicate> hiddenWeakPredicates) {
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

        // Which asserted corpora to search (ADR-0027).
        String categoryClause = mappingSetCategoryFilterClause(mappingSetCategories);
        if (categoryClause != null) {
            filterQueriesList.add(categoryClause);
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

        String weakPredicateExclusion = weakPredicateExclusionClause(hiddenWeakPredicates);
        if (weakPredicateExclusion != null) {
            filterQueriesList.add(weakPredicateExclusion);
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
     * Which asserted corpora to search (ADR-0027), or null for all of them. {@code mapping_set_category}
     * is a denormalised string whose values are the MappingSetCategory codes (safe enum names, no
     * escaping needed).
     *
     * <p>Inferred mappings are ORed back in unconditionally. An inference chains premises from several
     * sets, so it carries no category and would otherwise be silently dropped by <em>any</em> choice of
     * corpus — making this control secretly duplicate the inference-type filter. The two stay
     * orthogonal: this one picks the asserted corpora, {@code inferenceType} decides whether
     * inferences appear at all.
     *
     * <p>Before the reindex that populates the field, no asserted doc has a category, so a request
     * that names one returns only inferences. That is why the default is "all corpora" (no clause).
     */
    private static String mappingSetCategoryFilterClause(List<MappingSetCategory> mappingSetCategories) {
        if (mappingSetCategories == null || mappingSetCategories.isEmpty()) {
            return null;
        }
        String field = MappingEnum.MAPPING_SET_CATEGORY.getField();
        String clause = mappingSetCategories.stream()
                .filter(Objects::nonNull)
                .map(MappingSetCategory::getCode)
                .distinct()
                .map(code -> field + ":" + code)
                .collect(Collectors.joining(" OR "));
        if (clause.isEmpty()) {
            return null;
        }
        String inferred = MappingEnum.INFERENCE_TYPE.getField() + ":"
                + InferenceType.SSSOM_INFERENCE.getCode();
        return "(" + clause + " OR " + inferred + ")";
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
     * Pure-negative filter excluding the given weak predicates. The leading {@code *:*} is required
     * because Solr matches nothing for a filter query that is only negative; it supplies the all-docs
     * base from which the predicates are subtracted. Each IRI is escaped and quoted (an exact term
     * match on the {@code string}-typed {@code predicate_iri} field), mirroring the mapping_set_id
     * clause above.
     *
     * @param hidden the predicates to subtract; null when nothing is hidden — the caller has ticked
     *               both checkboxes, or filtered on a predicate explicitly. Returning null rather
     *               than an all-docs clause keeps an unfiltered query free of a no-op {@code fq},
     *               which is what the contextual value suggest compares against.
     */
    private static String weakPredicateExclusionClause(List<WeakPredicate> hidden) {
        if (hidden.isEmpty()) {
            return null;
        }
        String field = MappingEnum.PREDICATE_IRI.getField();
        String excluded = hidden.stream()
                .map(predicate -> field + ":\"" + ClientUtils.escapeQueryChars(predicate.iri()) + "\"")
                .collect(Collectors.joining(" OR "));
        return "*:* -(" + excluded + ")";
    }

    /**
     * Which weak predicates this request hides: all of them, minus the ones the caller ticked — and
     * none at all when the caller filters on a predicate field, since they are then asking about
     * predicates directly and hiding any would contradict them.
     */
    private static List<WeakPredicate> hiddenWeakPredicates(MappingSearchRequest request) {
        if (hasExplicitPredicateFilter(request)) {
            return List.of();
        }
        List<WeakPredicate> included = request.getIncludeWeakPredicates();
        return ALL_WEAK_PREDICATES.stream()
                .filter(predicate -> !included.contains(predicate))
                .toList();
    }

    /**
     * True when the request explicitly constrains a predicate field — via a column filter or an
     * advanced field query carrying a non-blank value. Such a caller is asking about predicates
     * directly, so the default weak-predicate exclusion is switched off for that request. The
     * default (classified) search path never counts: it matches the subject side only (ADR-0030),
     * so a main-box term can never constitute a predicate filter.
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

        // The value was PICKED from a suggestion (ADR-0034), so it came verbatim out of the index and
        // is unambiguous: match the WHOLE value. Falling through to the substring path here would be
        // the surprising behaviour — a user who explicitly picked "melanoma" would also get "familial
        // melanoma" and "melanoma of skin", values they were shown and did not choose. Targets the
        // whole-value field the suggestion was faceted out of, so it returns exactly the rows the
        // suggestion's count promised.
        if (filter.getMatch() == FilterMatchType.EXACT) {
            return SuggestFields.exactMatchFieldFor(field)
                    + ":\"" + ClientUtils.escapeQueryChars(value) + "\"";
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
    /**
     * Apply the caller's sort, or fall back to relevance.
     *
     * <p>The fallback is load-bearing, not cosmetic. An explicit Solr {@code sort} <em>replaces</em>
     * {@code score}, so any sorted field silently discards the whole provenance ranking
     * ({@link #RANKING_BOOST}). Worse, {@link #applySpoGrouping} appends {@code spo_key asc} as a
     * paging tiebreaker: with no sort at all, that tiebreaker would become the <em>primary</em> key and
     * order the page by an opaque hash. Naming {@code score desc} here keeps relevance primary and
     * demotes {@code spo_key} to the tiebreaker it was meant to be. Outside grouping this is exactly
     * Solr's own default, so it changes nothing.
     */
    private static SolrQuery constructSortedFields(SolrQuery solrQuery, MappingSearchRequest mappingSearchRequest) {
        List<SortedField> sortedFields = mappingSearchRequest.getSortedFields();
        if (sortedFields == null || sortedFields.isEmpty()) {
            solrQuery.addSort(SolrConstants.SCORE, SolrQuery.ORDER.desc);
            return solrQuery;
        }
        for (SortedField sortedField : sortedFields) {
            solrQuery.addSort(
                    textGeneralToDocValues.containsKey(sortedField.getId()) ?
                        textGeneralToDocValues.get(sortedField.getId()) :
                        sortedField.getId().getField(),
                    sortedField.isDesc() == true ? SolrQuery.ORDER.desc : SolrQuery.ORDER.asc
            );
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
     * Default query construction. A mapping search is asked from the subject's perspective
     * (ADR-0030), so each term becomes a {@link #subjectSideClause} — IRI → subject_iri, CURIE →
     * subject_id (normalised to its stored representation), free text → the subject label field the
     * match mode selects (ADR-0026) — and the clauses OR together. Mappings <em>into</em> a term via
     * a strong predicate are still found: the inference closure materialises the symmetric/inverse
     * row, whose subject is the term.
     */
    private static String constructClassifiedQuery(List<String> queries, LabelMatchType labelMatch) {
        if (queries == null || queries.isEmpty()) {
            return "*:*";
        }

        String query = queries.stream()
                .map(term -> subjectSideClause(term, labelMatch))
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" OR "));
        if (query.isEmpty()) {
            return "*:*";
        }
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
     * Apply the provenance-led ranking ({@link #RANKING_BOOST}, ADR-0027) as a multiplicative edismax
     * {@code boost} function. Requires {@code defType=edismax}. The boost multiplies the relevance
     * score, so every tier still appears — ontology-asserted mappings just float above curated ones,
     * curated above inferred, and shorter inference chains above longer ones — without a hard filter
     * or sort.
     */
    public static void applyProvenanceRanking(SolrQuery solrQuery) {
        solrQuery.set(SolrConstants.BOOST, RANKING_BOOST);
    }
}
