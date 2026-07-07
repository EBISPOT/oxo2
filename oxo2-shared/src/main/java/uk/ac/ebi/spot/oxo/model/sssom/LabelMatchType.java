package uk.ac.ebi.spot.oxo.model.sssom;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * How a free-text (label) query in a normal search is matched against mapping labels (ADR-0026).
 *
 * <p>Only affects the classified-by-shape query path when the term is neither an IRI nor a CURIE
 * (those are always exact lookups on the {@code *_id} / {@code *_iri} string fields). Each mode
 * selects which Solr field the subject/object/predicate label clause targets:
 *
 * <ul>
 *   <li>{@link #PARTIAL} — the analyzed {@code *_label} ({@code text_general}) fields: lowercased,
 *       stopword-stripped, synonym-expanded, matches a token subsequence.</li>
 *   <li>{@link #EXACT_CASE_INSENSITIVE} — the {@code *_label_ci} ({@code string_ci}) fields: the whole
 *       label must equal the query, case-folded. The default.</li>
 *   <li>{@link #EXACT_CASE_SENSITIVE} — the {@code *_label_str} ({@code string}) fields: the whole
 *       label must equal the query, byte-for-byte.</li>
 * </ul>
 */
public enum LabelMatchType {

    /** Query tokens appear (in order) within the label; analyzed text_general match. */
    PARTIAL,

    /** Whole label equals the query, ignoring case. The default for API and UI normal search. */
    EXACT_CASE_INSENSITIVE,

    /** Whole label equals the query, case-sensitive. */
    EXACT_CASE_SENSITIVE;

    /** The default when a request omits the mode. */
    public static final LabelMatchType DEFAULT = EXACT_CASE_INSENSITIVE;

    @JsonValue
    public String getCode() {
        return name();
    }

    @JsonCreator
    public static LabelMatchType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (LabelMatchType labelMatchType : values()) {
            if (labelMatchType.name().equalsIgnoreCase(code)) {
                return labelMatchType;
            }
        }
        throw new IllegalArgumentException("Unknown LabelMatchType: " + code);
    }
}
