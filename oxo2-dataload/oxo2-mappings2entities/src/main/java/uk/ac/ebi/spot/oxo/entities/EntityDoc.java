package uk.ac.ebi.spot.oxo.entities;

import uk.ac.ebi.spot.oxo.model.entity.EntityConstants;
import uk.ac.ebi.spot.oxo.model.sssom.WeakPredicate;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * One accumulating {@code oxo2-entities} document while a prefix shard is being folded (ADR-0034).
 *
 * <p>Mutable on purpose: an entity is seen once per mapping it participates in — potentially
 * thousands of times — and this is the accumulator that collapses those sightings into the single
 * document the collection stores. That collapse is the whole reason the collection exists.
 *
 * <p>Label and IRI use first-non-blank-wins. The same entity can carry a label in one mapping set
 * and none in another, and (rarely) differing labels across sets; there is no authority here to
 * arbitrate between them, so the first non-blank sighting is taken rather than the last, which at
 * least makes the result independent of how many blank sightings follow it. The CURIE is the
 * identity, so it is never overwritten.
 *
 * <p><b>Set membership is kept, counts are not per-set</b> (ADR-0044). Alongside the counts, each
 * sighting records the (mapping set, side, predicate bucket) it was seen in, as a set of tokens. That
 * is what lets the typeahead honour a mapping-set restriction: the tokens say which sets an entity is
 * findable in and how, so a restricted suggest can still promise rows. What they deliberately do not
 * carry is a count PER set — that would be a number per (set × side × bucket) on every document, and
 * the suggest would have to sum an unbounded subset of them as a function query. So a restricted
 * suggest filters exactly and reports no count at all, rather than reporting the corpus-wide one and
 * overstating what the user will get.
 *
 * <p><b>Counts are bucketed by predicate as well as by side</b> (ADR-0035). A search hides the weak
 * predicates unless the user asks for them, so a single total would tell the typeahead nothing about
 * whether a suggestion will actually return rows: an entity can hold a thousand
 * {@code oboInOwl:hasDbXref} mappings and still be invisible to a default search. Summing the buckets
 * the user has enabled gives the true visible count, which is what the suggest both filters and ranks
 * on.
 *
 * <p><b>…and each bucket has a LIVE twin</b> (ADR-0045), counting only sightings whose mapping has no
 * obsolete endpoint. {@link #obsolete} answers "is this term obsolete"; the search asks something else —
 * "does this mapping touch an obsolete term on either side" — and hides the row if it does. A live
 * entity whose every mapping points AT an obsolete term falls in the gap: not obsolete itself, so
 * suggested, yet every row hidden. The twins close it, and are exactly why a sighting needs to know
 * about the endpoint at the OTHER end of its mapping.
 */
final class EntityDoc {

    private final String id;
    private final String prefix;
    private String label;
    private String iri;

    /**
     * Sightings per bucket name, per side — {@code strong}, one per {@link WeakPredicate}, and the
     * {@code _live} twin of each (ADR-0045). Keyed by NAME rather than by enum because the live twins
     * are not enum values, and because it makes one sighting's two increments (its bucket and, when the
     * mapping is live, that bucket's twin) the same operation twice.
     */
    private final Map<String, Long> subjectCounts = new HashMap<>();
    private final Map<String, Long> objectCounts = new HashMap<>();

    /**
     * True once this entity is seen as an obsolete endpoint of any mapping (ADR-0041). Obsolescence is a
     * property of the term, so a single obsolete sighting settles it — the suggest hides the entity by
     * default so it never offers a term the default search would then hide.
     */
    private boolean obsolete;

    /**
     * One token per (mapping set, side, predicate bucket) this entity has been seen in (ADR-0044).
     * TreeSet so a shard's JSON is byte-stable run to run — the integration-test goldens pin it.
     */
    private final Set<String> setScopes = new TreeSet<>();

    EntityDoc(String id, String prefix) {
        this.id = id;
        this.prefix = prefix;
    }

    /**
     * Record one sighting of this entity on one side of one mapping.
     *
     * @param asSubject     true when the entity is this mapping's subject, false when it is the object
     * @param weakPredicate the mapping's predicate when it is weak, else null — null is the common
     *                      case and means "strong", i.e. any predicate the search shows by default
     * @param observedObsolete true when this sighting has the entity as an obsolete endpoint (ADR-0041)
     * @param mappingLive   true when NEITHER endpoint of this mapping is obsolete, i.e. when a default
     *                      search would show the row (ADR-0045). Not the negation of
     *                      {@code observedObsolete}: this entity can be perfectly live while the term at
     *                      the other end of the mapping is obsolete, and the search hides that row all
     *                      the same
     * @param mappingSetId  the set this mapping came from, recorded so a mapping-set-restricted
     *                      typeahead can still promise rows (ADR-0044); null or blank when the mapping
     *                      carries no set id, in which case no scope token is recorded and the entity
     *                      is simply not offered under any set restriction
     */
    void observe(String observedLabel, String observedIri, boolean asSubject,
                 WeakPredicate weakPredicate, boolean observedObsolete, boolean mappingLive,
                 String mappingSetId) {
        if (isBlank(label) && !isBlank(observedLabel)) {
            label = observedLabel;
        }
        if (isBlank(iri) && !isBlank(observedIri)) {
            iri = observedIri;
        }
        if (observedObsolete) {
            obsolete = true;
        }

        String bucket = weakPredicate == null
                ? EntityConstants.STRONG_BUCKET
                : weakPredicate.bucket();
        // A live sighting counts twice: once in its bucket, once in that bucket's live twin. "All" is
        // every sighting; "live" is the subset the default search can reach.
        record(asSubject, bucket, mappingSetId);
        if (mappingLive) {
            record(asSubject, EntityConstants.bucketFor(bucket, false), mappingSetId);
        }
    }

    /**
     * Credit one sighting to one bucket, and record the (set, side, bucket) it was seen in. The count
     * and the scope token are written together so they can never disagree about how this entity is
     * findable.
     */
    private void record(boolean asSubject, String bucket, String mappingSetId) {
        (asSubject ? subjectCounts : objectCounts).merge(bucket, 1L, Long::sum);
        if (!isBlank(mappingSetId)) {
            setScopes.add(EntityConstants.setScopeToken(mappingSetId, asSubject, bucket));
        }
    }

    String id() {
        return id;
    }

    String prefix() {
        return prefix;
    }

    String label() {
        return label;
    }

    String iri() {
        return iri;
    }

    boolean obsolete() {
        return obsolete;
    }

    /** The (set, side, bucket) tokens this entity was seen in, ascending (ADR-0044). */
    Set<String> setScopes() {
        return setScopes;
    }

    /** This bucket's subject-side count, e.g. for {@code strong} or {@code hasdbxref_live}. */
    long subjectCount(String bucket) {
        return subjectCounts.getOrDefault(bucket, 0L);
    }

    /** This bucket's object-side count. */
    long objectCount(String bucket) {
        return objectCounts.getOrDefault(bucket, 0L);
    }

    /**
     * Every subject-side sighting, weak predicates included. Display only — see the class note.
     *
     * <p>Sums the BASE buckets only. The live twins are a subset of them, not additional sightings, so
     * including them would double-count every live mapping.
     */
    long subjectCount() {
        return sumBaseBuckets(subjectCounts);
    }

    /** Every object-side sighting, weak predicates included. Display only — see the class note. */
    long objectCount() {
        return sumBaseBuckets(objectCounts);
    }

    /**
     * Total mappings this entity participates in. An entity that is both the subject and the object
     * of the same mapping (a self-mapping) is counted twice, which is the honest reading of "how
     * many mapping endpoints is this entity".
     */
    long mappingCount() {
        return subjectCount() + objectCount();
    }

    private static long sumBaseBuckets(Map<String, Long> counts) {
        return EntityConstants.baseBuckets().stream()
                .mapToLong(bucket -> counts.getOrDefault(bucket, 0L))
                .sum();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
