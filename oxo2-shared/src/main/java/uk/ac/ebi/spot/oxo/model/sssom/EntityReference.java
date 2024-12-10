package uk.ac.ebi.spot.oxo.model.sssom;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * EntityReference refers to an rdfs:Resource @see <a href="https://mapping-commons.github.io/sssom/EntityReference/>
 * EntityReference</a>.
 *
 *  According to <a href="https://mapping-commons.github.io/sssom/spec-model/#identifiers"/> can represent a full length
 *  IRI or a curie.
 */
public class EntityReference implements Comparable<EntityReference> {
    @JsonValue
    private  final String uri;

    public EntityReference(String uri) {
        if (uri == null || !uri.contains(":"))
            throw new IllegalArgumentException("The 'uri' cannot be null or blank.");
        this.uri = uri;
    }

    public String getUri() {
        return uri;
    }

    @Override
    public int compareTo(EntityReference o) {
        return this.uri.compareTo(o.uri);
    }

    @Override
    public String toString() {
        return "EntityReference{" +
                "uri='" + uri + '\'' +
                '}';
    }
}
