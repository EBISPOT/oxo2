package uk.ac.ebi.spot.oxo.model.sssom;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * EntityReference refers to an rdfs:Resource @see <a href="https://mapping-commons.github.io/sssom/EntityReference/>EntityReference</a>.
 *
 */
public class EntityReference {
    @JsonProperty("uri")
    private  final String uri;

    public EntityReference(String uri) {
        if (uri == null || !uri.contains(":"))
            throw new IllegalArgumentException("The 'uri' cannot be null or blank.");
        this.uri = uri;
    }

    public String getUri() {
        return uri;
    }
}
