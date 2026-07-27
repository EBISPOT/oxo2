package uk.ac.ebi.spot.oxo.entities;

import uk.ac.ebi.spot.oxo.model.sssom.WeakPredicate;

import java.util.EnumMap;
import java.util.Map;

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
 * <p><b>Counts are bucketed by predicate as well as by side</b> (ADR-0035). A search hides the weak
 * predicates unless the user asks for them, so a single total would tell the typeahead nothing about
 * whether a suggestion will actually return rows: an entity can hold a thousand
 * {@code oboInOwl:hasDbXref} mappings and still be invisible to a default search. Summing the buckets
 * the user has enabled gives the true visible count, which is what the suggest both filters and ranks
 * on.
 */
final class EntityDoc {

    private final String id;
    private final String prefix;
    private String label;
    private String iri;

    /** Sightings whose predicate is NOT weak — the ones a default search shows. */
    private long subjectCountStrong;
    private long objectCountStrong;

    /** Sightings per weak predicate, each independently switchable by the user. */
    private final Map<WeakPredicate, Long> subjectCountsWeak = new EnumMap<>(WeakPredicate.class);
    private final Map<WeakPredicate, Long> objectCountsWeak = new EnumMap<>(WeakPredicate.class);

    /**
     * True once this entity is seen as an obsolete endpoint of any mapping (ADR-0041). Obsolescence is a
     * property of the term, so a single obsolete sighting settles it — the suggest hides the entity by
     * default so it never offers a term the default search would then hide.
     */
    private boolean obsolete;

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
     */
    void observe(String observedLabel, String observedIri, boolean asSubject,
                 WeakPredicate weakPredicate, boolean observedObsolete) {
        if (isBlank(label) && !isBlank(observedLabel)) {
            label = observedLabel;
        }
        if (isBlank(iri) && !isBlank(observedIri)) {
            iri = observedIri;
        }
        if (observedObsolete) {
            obsolete = true;
        }
        if (weakPredicate == null) {
            if (asSubject) {
                subjectCountStrong++;
            } else {
                objectCountStrong++;
            }
        } else {
            Map<WeakPredicate, Long> counts = asSubject ? subjectCountsWeak : objectCountsWeak;
            counts.merge(weakPredicate, 1L, Long::sum);
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

    long subjectCountStrong() {
        return subjectCountStrong;
    }

    long objectCountStrong() {
        return objectCountStrong;
    }

    long subjectCount(WeakPredicate weakPredicate) {
        return subjectCountsWeak.getOrDefault(weakPredicate, 0L);
    }

    long objectCount(WeakPredicate weakPredicate) {
        return objectCountsWeak.getOrDefault(weakPredicate, 0L);
    }

    /** Every subject-side sighting, weak predicates included. Display only — see the class note. */
    long subjectCount() {
        return subjectCountStrong + sum(subjectCountsWeak);
    }

    /** Every object-side sighting, weak predicates included. Display only — see the class note. */
    long objectCount() {
        return objectCountStrong + sum(objectCountsWeak);
    }

    /**
     * Total mappings this entity participates in. An entity that is both the subject and the object
     * of the same mapping (a self-mapping) is counted twice, which is the honest reading of "how
     * many mapping endpoints is this entity".
     */
    long mappingCount() {
        return subjectCount() + objectCount();
    }

    private static long sum(Map<WeakPredicate, Long> counts) {
        return counts.values().stream().mapToLong(Long::longValue).sum();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
