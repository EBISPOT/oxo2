package uk.ac.ebi.spot.oxo.entities;

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
 */
final class EntityDoc {

    private final String id;
    private final String prefix;
    private String label;
    private String iri;
    private long subjectCount;
    private long objectCount;

    EntityDoc(String id, String prefix) {
        this.id = id;
        this.prefix = prefix;
    }

    /**
     * Record one sighting of this entity on one side of one mapping.
     *
     * @param asSubject true when the entity is this mapping's subject, false when it is the object
     */
    void observe(String observedLabel, String observedIri, boolean asSubject) {
        if (isBlank(label) && !isBlank(observedLabel)) {
            label = observedLabel;
        }
        if (isBlank(iri) && !isBlank(observedIri)) {
            iri = observedIri;
        }
        if (asSubject) {
            subjectCount++;
        } else {
            objectCount++;
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

    long subjectCount() {
        return subjectCount;
    }

    long objectCount() {
        return objectCount;
    }

    /**
     * Total mappings this entity participates in. An entity that is both the subject and the object
     * of the same mapping (a self-mapping) is counted twice, which is the honest reading of "how
     * many mapping endpoints is this entity" and is what the popularity boost wants.
     */
    long mappingCount() {
        return subjectCount + objectCount;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
