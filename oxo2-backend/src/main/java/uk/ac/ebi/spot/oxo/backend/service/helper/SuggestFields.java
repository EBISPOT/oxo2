package uk.ac.ebi.spot.oxo.backend.service.helper;

import uk.ac.ebi.spot.oxo.model.sssom.MappingEnum;

import java.util.EnumSet;
import java.util.Set;

/**
 * Which mapping fields may be faceted for suggestions, and which Solr field a facet on them must
 * actually read (ADR-0034).
 *
 * <p><b>Why a whitelist rather than "any field".</b> A facet returns a field's INDEXED TERMS. Point
 * one at a {@code text_general} field and it returns analyzed tokens, not values — an autocomplete on
 * {@code mapping_set_title} would offer "the" and "disease" as completions. Point one at a
 * high-cardinality field globally and it enumerates millions of terms. Neither fails loudly, so both
 * are excluded here rather than left to the caller to avoid.
 *
 * <p><b>Why the facet field is not always the requested field.</b> {@code text_general} fields are
 * faceted through their whole-value {@code _str} twin, which preserves the ORIGINAL CASING. Note it
 * is deliberately not the {@code _ci} twin (ADR-0026): {@code _ci} folds case, so faceting it would
 * return {@code mondo:0005148} — not how a CURIE is written, and wrong to show or to filter back on.
 */
public final class SuggestFields {

    /**
     * Controlled-vocabulary fields: few enough distinct values that the whole set can be fetched
     * once, cached, and filtered client-side. Safe to facet GLOBALLY (no query scope).
     *
     * <p>Excluded on purpose: the entity fields (subject/object id, label, iri) are high-cardinality
     * and are served by the {@code oxo2-entities} typeahead instead; the free-prose fields
     * ({@code comment}, {@code other}, {@code see_also}, {@code mapping_set_description},
     * {@code match_string}) have no vocabulary to suggest; and {@code issue_tracker_item} /
     * {@code asserted_mappings} are {@code indexed="false"} in the schema, so they cannot be faceted
     * OR filtered at all.
     */
    public static final Set<MappingEnum> VOCAB_FIELDS = EnumSet.of(
            MappingEnum.SUBJECT_SOURCE, MappingEnum.SUBJECT_SOURCE_VERSION, MappingEnum.SUBJECT_TYPE,
            MappingEnum.SUBJECT_MATCH_FIELD, MappingEnum.SUBJECT_PREPROCESSING, MappingEnum.SUBJECT_CATEGORY,

            MappingEnum.OBJECT_SOURCE, MappingEnum.OBJECT_SOURCE_VERSION, MappingEnum.OBJECT_TYPE,
            MappingEnum.OBJECT_MATCH_FIELD, MappingEnum.OBJECT_PREPROCESSING, MappingEnum.OBJECT_CATEGORY,

            MappingEnum.PREDICATE_ID, MappingEnum.PREDICATE_IRI, MappingEnum.PREDICATE_LABEL,
            MappingEnum.PREDICATE_MODIFIER,

            MappingEnum.MAPPING_JUSTIFICATION, MappingEnum.MAPPING_CARDINALITY, MappingEnum.MAPPING_SOURCE,
            MappingEnum.MAPPING_TOOL, MappingEnum.MAPPING_TOOL_VERSION, MappingEnum.SIMILARITY_MEASURE,
            MappingEnum.CURATION_RULE,

            MappingEnum.AUTHOR_ID, MappingEnum.AUTHOR_LABEL, MappingEnum.CREATOR_ID,
            MappingEnum.CREATOR_LABEL, MappingEnum.REVIEWER_ID, MappingEnum.REVIEWER_LABEL,

            MappingEnum.MAPPING_SET_ID, MappingEnum.MAPPING_SET_TITLE, MappingEnum.MAPPING_SET_VERSION,
            MappingEnum.MAPPING_SET_SOURCE, MappingEnum.MAPPING_PROVIDER, MappingEnum.LICENSE);

    /**
     * Fields the result-table column filters may suggest values for. The vocabulary fields plus the
     * OBJECT entity fields — high-cardinality, but safe here because a contextual suggest is scoped to
     * the live query AND carries a {@code facet.prefix}, so it never enumerates the whole term
     * dictionary the way a global facet would.
     */
    public static final Set<MappingEnum> CONTEXTUAL_FIELDS = contextualFields();

    private static Set<MappingEnum> contextualFields() {
        EnumSet<MappingEnum> fields = EnumSet.copyOf(VOCAB_FIELDS);
        fields.add(MappingEnum.OBJECT_ID);
        fields.add(MappingEnum.OBJECT_LABEL);
        fields.add(MappingEnum.OBJECT_IRI);
        return fields;
    }

    /**
     * {@code text_general} fields, which must be faceted through their {@code _str} twin. Faceting
     * them directly returns analyzed tokens rather than whole values.
     */
    private static final Set<MappingEnum> TEXT_GENERAL_FIELDS = EnumSet.of(
            MappingEnum.SUBJECT_LABEL, MappingEnum.OBJECT_LABEL, MappingEnum.PREDICATE_LABEL,
            MappingEnum.SUBJECT_CATEGORY, MappingEnum.OBJECT_CATEGORY, MappingEnum.MAPPING_TOOL,
            MappingEnum.MAPPING_SET_TITLE, MappingEnum.AUTHOR_LABEL, MappingEnum.CREATOR_LABEL,
            MappingEnum.REVIEWER_LABEL);

    private SuggestFields() {
    }

    /** The Solr field a facet on {@code field} must read to get whole, original-cased values. */
    public static String facetFieldFor(MappingEnum field) {
        return TEXT_GENERAL_FIELDS.contains(field)
                ? field.getField() + "_str"
                : field.getField();
    }

    /**
     * The Solr field an EXACT filter on {@code field} must target when the user picked a suggestion.
     * The same whole-value field the suggestion came out of — so a picked value round-trips to
     * exactly the rows it was counted from.
     */
    public static String exactMatchFieldFor(MappingEnum field) {
        return facetFieldFor(field);
    }
}
