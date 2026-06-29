package uk.ac.ebi.spot.oxo.backend.service.helper;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.request.FieldQuery;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.request.MappingSearchRequest;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.request.SortedField;
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

    public static SolrQuery buildSolrQuery(MappingSearchRequest mappingSearchRequest, Pageable pageable) {

        SolrQuery solrQuery = new SolrQuery();

        solrQuery.setStart((int) pageable.getOffset());
        solrQuery.setRows(pageable.getPageSize());

        // Dispatch order (most specific first):
        //   1. advancedFieldQueries non-empty → AND-joined per-field clauses (Advanced tab).
        //   2. queryFields non-empty       → legacy edismax/qf path.
        //   3. otherwise                    → classified-by-shape path (default search).
        // Do not silently rewire — each path is independent and documented.
        List<FieldQuery> advancedFieldQueries = mappingSearchRequest.getAdvancedFieldQueries();
        List<MappingEnum> queryFields = mappingSearchRequest.getQueryFields();
        if (advancedFieldQueries != null && !advancedFieldQueries.isEmpty()) {
            solrQuery.setQuery(constructAdvancedQuery(advancedFieldQueries));
        } else if (queryFields != null && !queryFields.isEmpty()) {
            // Override path: caller pinned the fields via qf.
            solrQuery.setQuery(constructUsingQueryFields(mappingSearchRequest.getQueries()));
            solrQuery.set(QF, constructQueryFields(queryFields));
        } else {
            // Default path: classify each term by shape and route to type-appropriate fields.
            solrQuery.setQuery(constructClassifiedQuery(mappingSearchRequest.getQueries()));
        }

        // ADR-0011: every path uses edismax so the soft inference-type + distance ranking applies
        // uniformly. The fielded queries on the advanced/classified paths carry their own field
        // selectors, which edismax parses unchanged; qf (set above) only affects the queryFields path.
        solrQuery.set(SolrConstants.DEF_TYPE, SolrConstants.EDISMAX);
        applyInferenceRanking(solrQuery);

        solrQuery.setFields(constructFieldList(mappingSearchRequest.getFieldList()));
        solrQuery.setFilterQueries(constructFilterQueries(
                mappingSearchRequest.getColumnFilters(),
                mappingSearchRequest.getMappingSetIds(),
                mappingSearchRequest.getInferenceType()));
        solrQuery = constructSortedFields(solrQuery, mappingSearchRequest);

        if (mappingSearchRequest.isGroupBySpo()) {
            applySpoGrouping(solrQuery);
        }

        return solrQuery;
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


    private static String[] constructFilterQueries(List<MappingSearchRequest.ColumnFilter> queryFilters,
                                                   List<String> mappingSetIds,
                                                   List<InferenceType> inferenceTypes) {
        List<String> filterQueriesList = queryFilters.stream()
                .map(SolrQueryBuilder::constructFilterClause)
                .filter(clause -> !clause.isEmpty())
                .collect(Collectors.toList());

        // Multi-select inference-type filter (ADR-0011). inference_type is a denormalised string
        // whose values are the InferenceType codes (safe enum names, no escaping needed); absent or
        // empty means all types. An OR of exact term matches, never the substring columnFilter path.
        if (inferenceTypes != null && !inferenceTypes.isEmpty()) {
            String field = MappingEnum.INFERENCE_TYPE.getField();
            String clause = inferenceTypes.stream()
                    .filter(Objects::nonNull)
                    .map(InferenceType::getCode)
                    .distinct()
                    .map(code -> field + ":" + code)
                    .collect(Collectors.joining(" OR "));
            if (!clause.isEmpty()) {
                filterQueriesList.add("(" + clause + ")");
            }
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

        return filterQueriesList.toArray(new String[filterQueriesList.size()]);
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
