package uk.ac.ebi.spot.oxo.model.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Which side of a mapping an entity suggestion must appear on (ADR-0034).
 *
 * <p>A mapping is a directed statement <em>subject → predicate → object</em>, and an entity may sit
 * on either end. The main search box suggests {@link #SUBJECT} only, because the default search
 * matches the subject side only (ADR-0030) — suggesting an entity that appears solely as an object
 * would offer a completion that returns no rows.
 */
public enum EntitySide {

    /** Entities that appear as the subject of at least one mapping. The main search box. */
    SUBJECT,

    /** Entities that appear as the object of at least one mapping. */
    OBJECT,

    /** Any known entity, whichever side it appears on. */
    ANY;

    /** The default when a request omits the side. */
    public static final EntitySide DEFAULT = SUBJECT;

    @JsonValue
    public String getCode() {
        return name();
    }

    @JsonCreator
    public static EntitySide fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (EntitySide entitySide : values()) {
            if (entitySide.name().equalsIgnoreCase(code)) {
                return entitySide;
            }
        }
        throw new IllegalArgumentException("Unknown EntitySide: " + code);
    }
}
