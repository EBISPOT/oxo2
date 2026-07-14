package uk.ac.ebi.spot.oxo.model.entity;

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

    public static final String MAPPING_COUNT = "mapping_count";
    public static final String SUBJECT_COUNT = "subject_count";
    public static final String OBJECT_COUNT = "object_count";

    /** True when the entity appears as the subject of at least one mapping. Backs ADR-0030. */
    public static final String IS_SUBJECT = "is_subject";

    public static final String IS_OBJECT = "is_object";

    /** Whole-string prefix over the CURIE: the ':' is not a token boundary. */
    public static final String ID_PREFIX_NGRAM = "id_prefix_ngram";

    /** Whole-string prefix over the label. */
    public static final String LABEL_PREFIX_NGRAM = "label_prefix_ngram";

    /** Prefix of any token of the label. */
    public static final String LABEL_NGRAM = "label_ngram";

    private EntityConstants() {}
}
