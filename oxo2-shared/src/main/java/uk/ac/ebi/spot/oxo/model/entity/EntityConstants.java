package uk.ac.ebi.spot.oxo.model.entity;

import uk.ac.ebi.spot.oxo.model.sssom.WeakPredicate;

import java.util.ArrayList;
import java.util.List;

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

    /**
     * The IRI stem this entity's CURIE expanded against, e.g.
     * {@code http://purl.obolibrary.org/obo/MONDO_} for {@code MONDO:0005148} (ADR-0047). Derived from
     * {@link #ID} and {@link #IRI} by {@link #namespaceOf}, so it records what the dataload ACTUALLY
     * minted rather than what any registry says a prefix ought to expand to.
     *
     * <p>That distinction is the reason the field exists. The prefix was resolved at load time by
     * {@code EntityReference.toUri} — an ADR-0029 override first, then the source set's own
     * {@code curie_map}, then the Bioregistry fallback (ADR-0015) — and only the winner reaches the
     * index. Answering "what does this prefix expand to" from any of those inputs instead can
     * contradict the IRIs the API serves; answering it from here cannot.
     */
    public static final String NAMESPACE = "namespace";

    /**
     * The IRI stem {@code curie} expanded against, or {@code null} when it cannot be derived — a blank
     * input, a {@code curie} with no colon, or an {@code iri} that does not end with the CURIE's local
     * part (which happens when a source hands us a bare IRI that never round-tripped through a CURIE).
     *
     * <p>Stripping the local part rather than matching a known stem is deliberate: it needs no registry
     * and therefore works for prefixes no registry knows.
     */
    public static String namespaceOf(String curie, String iri) {
        if (curie == null || iri == null) {
            return null;
        }
        int colon = curie.indexOf(':');
        if (colon < 0) {
            return null;
        }
        String localPart = curie.substring(colon + 1);
        if (localPart.isEmpty() || !iri.endsWith(localPart)) {
            return null;
        }
        return iri.substring(0, iri.length() - localPart.length());
    }

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
        return subjectCountField(predicate.bucket());
    }

    /** This predicate's object-side count field, e.g. {@code object_count_subclassof}. */
    public static String objectCountField(WeakPredicate predicate) {
        return objectCountField(predicate.bucket());
    }

    /** The subject-side count field for a bucket name, e.g. {@code subject_count_strong_live}. */
    public static String subjectCountField(String bucket) {
        return "subject_count_" + bucket;
    }

    /** The object-side count field for a bucket name, e.g. {@code object_count_hasdbxref_live}. */
    public static String objectCountField(String bucket) {
        return "object_count_" + bucket;
    }

    /**
     * Which (mapping set, side, predicate bucket) combinations this entity actually participates in
     * (ADR-0044). Multi-valued; one token per combination, built by {@link #setScopeToken}.
     *
     * <p>A bare {@code mapping_set_id} field would not do. The suggest already restricts by side and
     * by predicate, and those restrictions have to hold WITHIN the chosen set, not merely somewhere in
     * the corpus: an entity that is a subject in set A and an object in set B would satisfy
     * {@code mapping_set_id:B} and {@code subject_count_strong:[1 TO *]} independently and still return
     * no rows for a subject-side search of set B. Folding all three into one token makes the filter a
     * conjunction by construction, so ADR-0035's rule — a suggestion is a promise the search returns
     * rows — survives a mapping-set restriction.
     *
     * <p>The bucket component carries the {@link #LIVE_BUCKET_SUFFIX} variants too (ADR-0045), so the
     * obsolete dimension composes with the set the same way side and predicate do — one field, more
     * tokens, and the reader picks the bucket names it needs.
     */
    public static final String SET_SCOPE = "set_scope";

    /**
     * Separates the three parts of a {@link #SET_SCOPE} token. A {@code |} cannot appear unescaped in
     * an IRI, so it can never occur inside a mapping-set id and the token stays unambiguous.
     */
    public static final String SET_SCOPE_DELIMITER = "|";

    /** Side markers inside a {@link #SET_SCOPE} token. */
    public static final String SUBJECT_SIDE = "S";

    public static final String OBJECT_SIDE = "O";

    /** The bucket name for every predicate that is not a {@link WeakPredicate}. */
    public static final String STRONG_BUCKET = "strong";

    /**
     * Marks the variant of a bucket that counts only sightings a DEFAULT search can reach: mappings
     * where NEITHER endpoint is an obsolete term (ADR-0045).
     *
     * <p>Why a whole parallel set of buckets. {@link #OBSOLETE} is a property of the ENTITY, but the
     * search's obsolete exclusion is a property of the MAPPING — it hides a row when
     * {@code subject_obsolete OR object_obsolete}. Those are not the same question, and the gap between
     * them is a live entity whose every mapping points AT an obsolete term: not obsolete itself, so
     * offered by the suggest, yet its every row hidden. Measured on the worktree corpus, 2,704 of 3,710
     * subject-side suggestions (73%) were dead for exactly this reason — ADR-0035's failure again, one
     * dimension over.
     *
     * <p>Both sets are needed, not just the live one: with {@code includeObsolete} ticked the search
     * shows everything, so the suggest must then read the unrestricted buckets.
     */
    public static final String LIVE_BUCKET_SUFFIX = "_live";

    /**
     * The variant of {@code bucket} counting only mappings with no obsolete endpoint (ADR-0045), or
     * {@code bucket} itself when obsolete rows are being shown. One helper so the fold and the suggest
     * cannot spell the pair differently.
     */
    public static String bucketFor(String bucket, boolean includeObsolete) {
        return includeObsolete ? bucket : bucket + LIVE_BUCKET_SUFFIX;
    }

    /**
     * Every base bucket name in a stable order — strong first, then one per {@link WeakPredicate}. The
     * fold walks this to emit each count field (and its {@link #LIVE_BUCKET_SUFFIX} twin), so adding a
     * weak predicate adds its buckets everywhere at once.
     */
    public static List<String> baseBuckets() {
        List<String> buckets = new ArrayList<>();
        buckets.add(STRONG_BUCKET);
        for (WeakPredicate predicate : WeakPredicate.values()) {
            buckets.add(predicate.bucket());
        }
        return buckets;
    }

    /**
     * The {@link #SET_SCOPE} token for one (set, side, bucket) combination, e.g.
     * {@code https://www.ebi.ac.uk/oxo2/inferences|S|strong}. The single place either side of the
     * writer/reader contract spells a token.
     *
     * @param asSubject true for the subject side of the mapping, false for the object side
     * @param bucket    {@link #STRONG_BUCKET} or a {@link WeakPredicate#bucket()} — the same suffixes
     *                  the count fields use, so the tokens and the counts cannot drift apart
     */
    public static String setScopeToken(String mappingSetId, boolean asSubject, String bucket) {
        return mappingSetId + SET_SCOPE_DELIMITER + (asSubject ? SUBJECT_SIDE : OBJECT_SIDE)
                + SET_SCOPE_DELIMITER + bucket;
    }

    /** Whole-string prefix over the CURIE: the ':' is not a token boundary. */
    public static final String ID_PREFIX_NGRAM = "id_prefix_ngram";

    /** Whole-string prefix over the label. */
    public static final String LABEL_PREFIX_NGRAM = "label_prefix_ngram";

    /** Prefix of any token of the label. */
    public static final String LABEL_NGRAM = "label_ngram";

    private EntityConstants() {}
}
