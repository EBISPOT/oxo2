package uk.ac.ebi.spot.oxo.model.sssom;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * How a column filter or an advanced field query matches its value (ADR-0034).
 *
 * <p>This is the "picking is not typing" distinction that autocomplete introduces. A value the user
 * <em>typed</em> is a search — they may have typed a fragment, so it stays a substring match. A value
 * the user <em>picked from a suggestion list</em> came verbatim out of the index and is unambiguous,
 * so it must match exactly: applying CONTAINS after someone explicitly picked "melanoma" would
 * silently also return "familial melanoma" and "melanoma of skin", values they did not pick.
 *
 * <ul>
 *   <li>{@link #CONTAINS} — the pre-existing behaviour: the value is split on whitespace and each
 *       word is matched as a {@code *word*} wildcard against the field's {@code *_ngram} twin.</li>
 *   <li>{@link #EXACT} — the whole value must equal the field's value, byte-for-byte. Targets the
 *       whole-value {@code _str} twin for {@code text_general} fields and the field itself for
 *       {@code string} fields. Deliberately case-<em>sensitive</em> (not ADR-0026's case-folding
 *       {@code _ci} twin): a picked value is already in the index's own casing, so there is nothing
 *       to be lenient about.</li>
 * </ul>
 */
public enum FilterMatchType {

    /** Substring match on the field's n-gram twin. What a typed fragment means. */
    CONTAINS,

    /** Whole-value match. What a picked suggestion means. */
    EXACT;

    /** The default when a request omits the mode — so existing callers are unaffected. */
    public static final FilterMatchType DEFAULT = CONTAINS;

    @JsonValue
    public String getCode() {
        return name();
    }

    @JsonCreator
    public static FilterMatchType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (FilterMatchType filterMatchType : values()) {
            if (filterMatchType.name().equalsIgnoreCase(code)) {
                return filterMatchType;
            }
        }
        throw new IllegalArgumentException("Unknown FilterMatchType: " + code);
    }
}
