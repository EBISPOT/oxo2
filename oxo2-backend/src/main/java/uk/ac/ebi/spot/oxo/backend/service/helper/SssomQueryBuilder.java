package uk.ac.ebi.spot.oxo.backend.service.helper;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.springframework.data.domain.Pageable;
import uk.ac.ebi.spot.oxo.backend.controller.api.sssom.dto.SearchEntityRequest;
import uk.ac.ebi.spot.oxo.model.sssom.EntityReference;
import uk.ac.ebi.spot.oxo.model.sssom.MappingConstants;
import uk.ac.ebi.spot.oxo.model.sssom.MappingEnum;
import uk.ac.ebi.spot.oxo.utils.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Builds the Solr queries behind the SSSOM-API surface ({@code /api/sssom}, ADR-0032): the reference
 * API's {@code filter=field|operator|value} grammar, the {@code /entities} CURIE lookup, and the
 * {@code field/value} equality path, plus the facet/stats parameters that fill the envelope's
 * {@code facets} block.
 *
 * <p>Separate from {@link SolrQueryBuilder} (which serves the OxO2 v2 search UI and the v1 compat
 * layer) because the two speak different request languages, but it reuses that class's provenance
 * ranking and same-SPO collapse so a mapping ranks and de-duplicates identically however it is
 * reached. Unlike the v2 search, the SSSOM surface applies <em>no</em> default weak-predicate
 * exclusion: the spec's contract is "retrieve all mappings", so hiding predicates would be wrong.
 */
public final class SssomQueryBuilder {

    private SssomQueryBuilder() {
    }

    /** Filter operators of the reference {@code field|operator|value} grammar, plus {@code eq}. */
    public enum Operator {
        EQ, GE, GT, LE, LT, CONTAINS;

        static Operator fromString(String raw) {
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "eq" -> EQ;
                case "ge" -> GE;
                case "gt" -> GT;
                case "le" -> LE;
                case "lt" -> LT;
                case "contains" -> CONTAINS;
                default -> throw new IllegalArgumentException("Unknown filter operator: " + raw);
            };
        }
    }

    /** One parsed {@code field|operator|value} clause, its field resolved to a known mapping slot. */
    public record SssomFilter(MappingEnum field, Operator operator, String value) {
    }

    /**
     * Id-typed fields carry a CURIE in the {@code *_id} slot and the canonical IRI in a parallel
     * {@code *_iri} slot. A filter value is classified by shape: an IRI matches the {@code *_iri}
     * field, a CURIE the (prefix-normalised) {@code *_id} field.
     */
    private static final Map<MappingEnum, MappingEnum> ID_TO_IRI_FIELD = Map.of(
            MappingEnum.SUBJECT_ID, MappingEnum.SUBJECT_IRI,
            MappingEnum.OBJECT_ID, MappingEnum.OBJECT_IRI,
            MappingEnum.PREDICATE_ID, MappingEnum.PREDICATE_IRI);

    /** Label fields backed by a word-n-gram copy, so {@code contains} can match a substring. */
    private static final Map<MappingEnum, String> LABEL_NGRAM_FIELD = Map.of(
            MappingEnum.SUBJECT_LABEL, MappingConstants.SUBJECT_LABEL + "_ngram",
            MappingEnum.OBJECT_LABEL, MappingConstants.OBJECT_LABEL + "_ngram",
            MappingEnum.PREDICATE_LABEL, MappingConstants.PREDICATE_LABEL + "_ngram");

    /**
     * CURIE-valued fields (SSSOM {@code EntityReference} slots) that are not one of the id/iri-dual
     * fields above. Their stored value is the prefix-normalised CURIE (the {@code SSSOMDataType}
     * serialiser writes the upper-cased-prefix representation — the same reason {@code *_id} matches
     * are normalised), so an equality/contains value here is normalised the same way before matching.
     * A full IRI passed in is left unchanged by the normaliser.
     */
    private static final java.util.Set<MappingEnum> CURIE_VALUED_FIELDS =
            java.util.Set.of(MappingEnum.MAPPING_JUSTIFICATION);

    /** Range-bound values are restricted to this safe alphabet (numbers, ISO dates, CURIEs) so an
     *  unescaped range endpoint cannot smuggle Solr query syntax. */
    private static final Pattern SAFE_RANGE_VALUE = Pattern.compile("[A-Za-z0-9:._+\\-]+");

    // ---------- Filter grammar ----------

    /**
     * Parse the repeatable {@code filter} parameter — each entry {@code field|operator|value} — into
     * validated clauses. Mirrors the reference grammar but also accepts {@code eq} (explicit
     * equality). Throws {@link IllegalArgumentException} (surfaced as 400) on a malformed entry, an
     * unknown field, or an unknown operator; a null/empty list yields no clauses.
     *
     * @param fieldResolver maps a raw field name to a known slot, or null if the field is unknown for
     *                      this collection (mappings vs mapping sets).
     */
    public static List<SssomFilter> parseFilters(List<String> filters,
                                                 java.util.function.Function<String, MappingEnum> fieldResolver) {
        List<SssomFilter> parsed = new ArrayList<>();
        if (filters == null) {
            return parsed;
        }
        for (String entry : filters) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            String[] parts = entry.split("\\|", -1);
            if (parts.length != 3) {
                throw new IllegalArgumentException(
                        "Filter must be field|operator|value: " + entry);
            }
            MappingEnum field = fieldResolver.apply(parts[0].trim());
            if (field == null) {
                throw new IllegalArgumentException("Unknown filter field: " + parts[0]);
            }
            Operator operator = Operator.fromString(parts[1]);
            String value = parts[2];
            if (value.isBlank()) {
                throw new IllegalArgumentException("Filter value must not be blank: " + entry);
            }
            parsed.add(new SssomFilter(field, operator, value));
        }
        return parsed;
    }

    /** Resolve a filter field against the mapping slots (includes OxO2 extensions like inference_type). */
    public static MappingEnum resolveMappingField(String field) {
        return MappingEnum.fromString(field);
    }

    /**
     * SSSOM slots that exist on the mapping-set (as opposed to mapping) collection. A filter on a field
     * absent from the collection's schema would make Solr fault ("undefined field"), so the set-listing
     * filter is validated against this allowlist rather than the full mapping slot set.
     */
    private static final java.util.Set<MappingEnum> MAPPING_SET_FILTER_FIELDS = java.util.Set.of(
            MappingEnum.MAPPING_SET_ID, MappingEnum.MAPPING_SET_TITLE,
            MappingEnum.MAPPING_SET_DESCRIPTION, MappingEnum.MAPPING_SET_VERSION,
            MappingEnum.MAPPING_SET_SOURCE, MappingEnum.CREATOR_ID, MappingEnum.CREATOR_LABEL,
            MappingEnum.LICENSE, MappingEnum.MAPPING_PROVIDER, MappingEnum.MAPPING_TOOL,
            MappingEnum.MAPPING_TOOL_VERSION, MappingEnum.INFERENCE_TYPE,
            MappingEnum.MAPPING_SET_CATEGORY, MappingEnum.MAPPING_DATE, MappingEnum.PUBLICATION_DATE,
            MappingEnum.SUBJECT_SOURCE, MappingEnum.OBJECT_SOURCE, MappingEnum.SUBJECT_TYPE,
            MappingEnum.OBJECT_TYPE, MappingEnum.SEE_ALSO, MappingEnum.COMMENT, MappingEnum.OTHER);

    /** Resolve a mapping-set filter field, or null if it is not a filterable set slot. */
    public static MappingEnum resolveMappingSetField(String field) {
        MappingEnum resolved = MappingEnum.fromString(field);
        return (resolved != null && MAPPING_SET_FILTER_FIELDS.contains(resolved)) ? resolved : null;
    }

    // ---------- Mapping list / field-value / entities queries ----------

    /**
     * {@code GET /api/sssom/mappings?filter=...}: all mappings narrowed by the parsed filter clauses
     * (AND-joined), ranked by provenance and same-SPO collapsed, with the envelope facets attached.
     */
    public static SolrQuery buildMappingsQuery(List<SssomFilter> filters, boolean groupBySpo,
                                               Pageable pageable) {
        SolrQuery solrQuery = baseMappingQuery("*:*", pageable);
        for (SssomFilter filter : filters) {
            solrQuery.addFilterQuery(filterClause(filter));
        }
        finishMappingQuery(solrQuery, groupBySpo);
        return solrQuery;
    }

    /**
     * {@code GET /api/sssom/mappings/{field}/{value}}: the equality special case of the filter
     * grammar — a single {@code field eq value} clause.
     */
    public static SolrQuery buildFieldValueQuery(MappingEnum field, String value, boolean groupBySpo,
                                                 Pageable pageable) {
        SolrQuery solrQuery = baseMappingQuery("*:*", pageable);
        solrQuery.addFilterQuery(filterClause(new SssomFilter(field, Operator.EQ, value)));
        finishMappingQuery(solrQuery, groupBySpo);
        return solrQuery;
    }

    /**
     * {@code GET /api/sssom/mappings/{id}}: a single mapping by its {@code mapping_id} (a stable
     * name-based UUID). No collapse, no facets — a bare document lookup.
     */
    public static SolrQuery buildByMappingIdQuery(String mappingId) {
        SolrQuery solrQuery = new SolrQuery();
        solrQuery.setQuery(MappingEnum.MAPPING_ID.getField()
                + ":\"" + ClientUtils.escapeQueryChars(mappingId) + "\"");
        solrQuery.setRows(1);
        solrQuery.setFields(MappingEnum.MINIMAL_LIST_OF_FIELDS);
        return solrQuery;
    }

    /**
     * {@code POST /api/sssom/entities}: every mapping where any of the given CURIEs/IRIs appears as
     * {@code subject_id} or {@code object_id}, optionally restricted to the given justifications and
     * predicates. Ranked, collapsed and facetted like the mapping list.
     */
    public static SolrQuery buildEntitiesQuery(SearchEntityRequest request, boolean groupBySpo,
                                               Pageable pageable) {
        SolrQuery solrQuery = baseMappingQuery(entityDisjunction(request.getCuries()), pageable);

        List<String> justifications = request.getMappingJustification();
        if (justifications != null && !justifications.isEmpty()) {
            String clause = justifications.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(value -> MappingEnum.MAPPING_JUSTIFICATION.getField()
                            + ":\"" + ClientUtils.escapeQueryChars(normaliseCurie(value.strip())) + "\"")
                    .collect(Collectors.joining(" OR "));
            if (!clause.isEmpty()) {
                solrQuery.addFilterQuery("(" + clause + ")");
            }
        }

        List<String> predicates = request.getPredicateId();
        if (predicates != null && !predicates.isEmpty()) {
            String clause = predicates.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(value -> idOrIriClause(MappingEnum.PREDICATE_ID, value.strip()))
                    .collect(Collectors.joining(" OR "));
            if (!clause.isEmpty()) {
                solrQuery.addFilterQuery("(" + clause + ")");
            }
        }

        finishMappingQuery(solrQuery, groupBySpo);
        return solrQuery;
    }

    /**
     * {@code GET /api/sssom/mapping_sets?filter=...}: the mapping sets, over the sets collection,
     * narrowed by the parsed filters (AND-joined) and sorted by title for stable paging. No id/iri
     * classification or n-gram fields apply — the sets collection has neither — so each clause is a
     * plain field/operator/value. No facets: a mapping set carries none of the facetted slots.
     */
    public static SolrQuery buildMappingSetsQuery(List<SssomFilter> filters, Pageable pageable) {
        SolrQuery solrQuery = new SolrQuery("*:*");
        solrQuery.setStart((int) pageable.getOffset());
        solrQuery.setRows(pageable.getPageSize());
        for (SssomFilter filter : filters) {
            solrQuery.addFilterQuery(
                    operatorClause(filter.field().getField(), filter.operator(), filter.value().strip()));
        }
        solrQuery.addSort(MappingEnum.MAPPING_SET_TITLE.getField() + "_str", SolrQuery.ORDER.asc);
        return solrQuery;
    }

    /** The flat, cursor-streamable form of a mapping query for {@code ?format=} exports (no collapse,
     *  no facets, unique-key sorted). Callers set rows / fl / cursorMark. */
    public static SolrQuery toExportQuery(SolrQuery pagedQuery) {
        SolrQuery exportQuery = new SolrQuery();
        exportQuery.setQuery(pagedQuery.getQuery());
        exportQuery.set(SolrConstants.DEF_TYPE, SolrConstants.EDISMAX);
        String[] filterQueries = pagedQuery.getFilterQueries();
        if (filterQueries != null) {
            for (String filterQuery : filterQueries) {
                // Drop the collapse post-filter; an export is flat.
                if (!filterQuery.startsWith("{!collapse")) {
                    exportQuery.addFilterQuery(filterQuery);
                }
            }
        }
        exportQuery.setSort("id", SolrQuery.ORDER.asc);
        return exportQuery;
    }

    private static SolrQuery baseMappingQuery(String query, Pageable pageable) {
        SolrQuery solrQuery = new SolrQuery();
        solrQuery.setQuery(query);
        solrQuery.setStart((int) pageable.getOffset());
        solrQuery.setRows(pageable.getPageSize());
        solrQuery.set(SolrConstants.DEF_TYPE, SolrConstants.EDISMAX);
        SolrQueryBuilder.applyProvenanceRanking(solrQuery);
        solrQuery.setFields(MappingEnum.MINIMAL_LIST_OF_FIELDS);
        // Name the relevance sort explicitly so the provenance ranking orders the page. Load-bearing
        // under collapse: applySpoGrouping appends `spo_key asc` as a paging tiebreaker, and with no
        // primary sort that hash would become the primary key and order the page by an opaque value
        // (the trap SolrQueryBuilder#constructSortedFields documents). score desc keeps relevance
        // primary and demotes spo_key to the tiebreaker.
        solrQuery.addSort(SolrConstants.SCORE, SolrQuery.ORDER.desc);
        return solrQuery;
    }

    private static void finishMappingQuery(SolrQuery solrQuery, boolean groupBySpo) {
        applyFacets(solrQuery);
        if (groupBySpo) {
            SolrQueryBuilder.applySpoGrouping(solrQuery);
        }
    }

    /**
     * Attach the envelope facets: term counts on {@code mapping_justification} and {@code
     * predicate_id}, and min/max stats on {@code confidence}. Solr computes these over the filtered
     * doc set (the same one the page is drawn from, collapse post-filter included), so they describe
     * the whole result, not just the page.
     */
    private static void applyFacets(SolrQuery solrQuery) {
        solrQuery.setFacet(true);
        solrQuery.setFacetMinCount(1);
        solrQuery.setFacetLimit(-1);
        solrQuery.addFacetField(
                MappingEnum.MAPPING_JUSTIFICATION.getField(), MappingEnum.PREDICATE_ID.getField());
        solrQuery.setGetFieldStatistics(MappingEnum.CONFIDENCE.getField());
    }

    // ---------- Clause construction ----------

    /** One Solr filter clause for a parsed filter, dispatching on field type and operator. */
    private static String filterClause(SssomFilter filter) {
        MappingEnum field = filter.field();
        Operator operator = filter.operator();
        String value = filter.value().strip();

        if (ID_TO_IRI_FIELD.containsKey(field)) {
            return idOrIriClauseForOperator(field, operator, value);
        }
        if (LABEL_NGRAM_FIELD.containsKey(field) && operator == Operator.CONTAINS) {
            return ngramContainsClause(LABEL_NGRAM_FIELD.get(field), value);
        }
        if (CURIE_VALUED_FIELDS.contains(field)
                && (operator == Operator.EQ || operator == Operator.CONTAINS)) {
            return operatorClause(field.getField(), operator, normaliseCurie(value));
        }
        return operatorClause(field.getField(), operator, value);
    }

    /** An id-typed field's clause: classify the value, then apply the operator on the chosen side. */
    private static String idOrIriClauseForOperator(MappingEnum idField, Operator operator, String value) {
        MappingEnum iriField = ID_TO_IRI_FIELD.get(idField);
        if (StringUtils.isIri(value)) {
            return operatorClause(iriField.getField(), operator, value);
        }
        String normalised = normaliseCurie(value);
        return operatorClause(idField.getField(), operator, normalised);
    }

    /** Exact-match variant used by {@code /entities} for curie/predicate lookup. */
    private static String idOrIriClause(MappingEnum idField, String value) {
        return idOrIriClauseForOperator(idField, Operator.EQ, value);
    }

    /**
     * Match one entity (CURIE or IRI) on either side: {@code (subject match) OR (object match)}. The
     * per-curie disjunction is what makes {@code /entities} undirected.
     */
    private static String entityDisjunction(List<String> curies) {
        if (curies == null || curies.isEmpty()) {
            return "*:*";
        }
        String disjunction = curies.stream()
                .filter(curie -> curie != null && !curie.isBlank())
                .map(String::strip)
                .map(curie -> "(" + idOrIriClause(MappingEnum.SUBJECT_ID, curie)
                        + " OR " + idOrIriClause(MappingEnum.OBJECT_ID, curie) + ")")
                .collect(Collectors.joining(" OR "));
        return disjunction.isEmpty() ? "*:*" : disjunction;
    }

    private static String operatorClause(String field, Operator operator, String value) {
        return switch (operator) {
            case EQ -> field + ":\"" + ClientUtils.escapeQueryChars(value) + "\"";
            case CONTAINS -> field + ":*" + ClientUtils.escapeQueryChars(value) + "*";
            case GE -> field + ":[" + safeRange(value) + " TO *]";
            case GT -> field + ":{" + safeRange(value) + " TO *]";
            case LE -> field + ":[* TO " + safeRange(value) + "]";
            case LT -> field + ":[* TO " + safeRange(value) + "}";
        };
    }

    /** Order-independent "contains all these words" over a word-n-gram label field. */
    private static String ngramContainsClause(String ngramField, String value) {
        String clause = java.util.Arrays.stream(value.split("\\s+"))
                .filter(word -> !word.isEmpty())
                .map(word -> ngramField + ":*" + ClientUtils.escapeQueryChars(word) + "*")
                .collect(Collectors.joining(" AND "));
        return clause.isEmpty() ? ngramField + ":*" : "(" + clause + ")";
    }

    private static String safeRange(String value) {
        if (!SAFE_RANGE_VALUE.matcher(value).matches()) {
            throw new IllegalArgumentException("Range filter value is not a number, date or CURIE: " + value);
        }
        return value;
    }

    /** Normalise a CURIE to its stored representation (prefix upper-cased, as EntityReference indexes it). */
    private static String normaliseCurie(String curie) {
        return new EntityReference(curie).getDataRepresentation()
                .map(Object::toString).orElse(curie);
    }
}
