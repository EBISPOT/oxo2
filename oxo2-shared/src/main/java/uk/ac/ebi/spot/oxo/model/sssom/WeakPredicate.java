package uk.ac.ebi.spot.oxo.model.sssom;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.List;

/**
 * A mapping predicate that is hidden from search results unless the user asks for it (ADR-0035).
 *
 * <p>Neither {@code rdfs:subClassOf} nor {@code oboInOwl:hasDbXref} asserts that two entities are the
 * same thing, and neither participates in SSSOM inference. On an OLS-derived corpus they are the
 * overwhelming majority of mappings, so showing them by default drowns the equivalences a user is
 * usually looking for. They are therefore excluded by default — but they are excluded, not absent,
 * and each can be switched back on independently: hierarchy and cross-references are different
 * questions and a user may want one without the other.
 *
 * <p>This enum is the single source of truth for the pair, shared by the two places that must agree
 * on them by construction:
 * <ul>
 *   <li>{@code SolrQueryBuilder}, which subtracts the enabled ones from the exclusion filter it puts
 *       on {@code oxo2-mappings}; and</li>
 *   <li>{@code Mappings2Entities}, which buckets every mapping sighting by predicate so the
 *       {@code oxo2-entities} typeahead can offer exactly the entities the search can then show.</li>
 * </ul>
 * If those two disagreed, the typeahead would suggest entities whose mappings the search hides — the
 * bug ADR-0035 exists to fix.
 *
 * <p>{@link #code()} is the wire form (API and URL); {@link #bucket()} names the entity-count fields
 * in {@code oxo2-entities} and so is lower-case, matching the Solr field-naming convention.
 */
public enum WeakPredicate {

    /** Ontology hierarchy, not equivalence. */
    SUB_CLASS_OF("subClassOf", "http://www.w3.org/2000/01/rdf-schema#subClassOf", "subclassof"),

    /** A loose cross-reference: no semantics beyond "these two are related somehow". */
    HAS_DB_XREF("hasDbXref", "http://www.geneontology.org/formats/oboInOwl#hasDbXref", "hasdbxref");

    private final String code;
    private final String iri;
    private final String bucket;

    WeakPredicate(String code, String iri, String bucket) {
        this.code = code;
        this.iri = iri;
        this.bucket = bucket;
    }

    /** The wire form, as it appears in the API and in the {@code wp} URL parameter. */
    @JsonValue
    public String code() {
        return code;
    }

    /**
     * The canonical predicate IRI. Filtering matches on {@code predicate_iri} rather than
     * {@code predicate_id}, whose CURIE prefix and casing vary by source set.
     */
    public String iri() {
        return iri;
    }

    /**
     * Names this predicate's count fields in {@code oxo2-entities}; see
     * {@code EntityConstants.subjectCountField}.
     */
    public String bucket() {
        return bucket;
    }

    /** The IRIs of the given predicates — what the mappings-side filter clauses are built from. */
    public static List<String> irisOf(List<WeakPredicate> predicates) {
        return predicates.stream().map(WeakPredicate::iri).toList();
    }

    /** Null rather than throwing, so a caller can turn an unknown value into a 400. */
    @JsonCreator
    public static WeakPredicate fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (WeakPredicate predicate : values()) {
            if (predicate.code.equalsIgnoreCase(code.strip())) {
                return predicate;
            }
        }
        return null;
    }
}
