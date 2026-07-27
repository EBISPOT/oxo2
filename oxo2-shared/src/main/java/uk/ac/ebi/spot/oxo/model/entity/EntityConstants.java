package uk.ac.ebi.spot.oxo.model.entity;

import uk.ac.ebi.spot.oxo.model.sssom.WeakPredicate;

/**
 * Field-name constants for the {@code oxo2-entities} Solr collection: one document per DISTINCT
 * entity, backing the typeahead (ADR-0034).
 *
 * <p>The collection is a read model derived from {@code oxo2-mappings} by the {@code
 * mappings2entities} dataload stage. These names are the contract between the writer
 * ({@code oxo2-mappings2entities}) and the reader ({@code EntitySuggestQueryBuilder} in
 * {@code oxo2-backend}); they must match {@code solr-config/oxo2-entities/conf/managed-schema.xml}.
 *
 * <p>Note the two n-gram fields are not interchangeable: {@link #LABEL_PREFIX_NGRAM} is a
 * whole-string prefix (KeywordTokenizer, so it matches a label from its first character) while
 * {@link #LABEL_NGRAM} is a prefix of ANY token (StandardTokenizer, so "mel" reaches "malignant
 * melanoma"). Ranking boosts the former above the latter.
 */
public final class EntityConstants {

    /** The entity CURIE, e.g. {@code MONDO:0005148}. The collection's uniqueKey. */
    public static final String ID = "id";

    public static final String LABEL = "label";
    public static final String LABEL_STR = "label_str";
    public static final String IRI = "iri";

    /** The entity's CURIE prefix, e.g. {@code MONDO} (ADR-0024). */
    public static final String PREFIX = "prefix";

    /** Totals over every predicate, weak ones included. For display, never for filtering — see below. */
    public static final String MAPPING_COUNT = "mapping_count";
    public static final String SUBJECT_COUNT = "subject_count";
    public static final String OBJECT_COUNT = "object_count";

    /** True when the entity appears as the subject of at least one mapping. Backs ADR-0030. */
    public static final String IS_SUBJECT = "is_subject";

    public static final String IS_OBJECT = "is_object";

    /**
     * True when the entity is an obsolete term (ADR-0041): its IRI is a subject of an obsolete-flagged
     * registry, so it took part in at least one mapping as an obsolete endpoint. The suggest hides
     * obsolete entities unless the caller opts in, so it never offers a term the default search would
     * then hide (ADR-0035's rule that a suggestion must be a promise the search returns rows).
     */
    public static final String OBSOLETE = "obsolete";

    /**
     * Per-side mapping counts split by predicate bucket (ADR-0035). "Strong" is every predicate that
     * is NOT a {@link WeakPredicate}; the rest get one bucket each.
     *
     * <p>These, not {@link #SUBJECT_COUNT}/{@link #OBJECT_COUNT}, are what the typeahead filters and
     * ranks on. The totals count mappings the search hides by default, so an entity whose every
     * mapping is an {@code oboInOwl:hasDbXref} has a large {@code subject_count} and yet nothing a
     * default search would return. Filtering on the totals is what made the typeahead suggest
     * entities that yielded no rows.
     *
     * <p>The split is per SIDE as well as per predicate because the two are ranked independently: a
     * subject-side typeahead must not boost an entity for the thousand mappings in which it is the
     * OBJECT.
     */
    public static final String SUBJECT_COUNT_STRONG = "subject_count_strong";

    public static final String OBJECT_COUNT_STRONG = "object_count_strong";

    /** This predicate's subject-side count field, e.g. {@code subject_count_hasdbxref}. */
    public static String subjectCountField(WeakPredicate predicate) {
        return "subject_count_" + predicate.bucket();
    }

    /** This predicate's object-side count field, e.g. {@code object_count_subclassof}. */
    public static String objectCountField(WeakPredicate predicate) {
        return "object_count_" + predicate.bucket();
    }

    /** Whole-string prefix over the CURIE: the ':' is not a token boundary. */
    public static final String ID_PREFIX_NGRAM = "id_prefix_ngram";

    /** Whole-string prefix over the label. */
    public static final String LABEL_PREFIX_NGRAM = "label_prefix_ngram";

    /** Prefix of any token of the label. */
    public static final String LABEL_NGRAM = "label_ngram";

    private EntityConstants() {}
}
