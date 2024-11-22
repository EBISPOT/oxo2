package uk.ac.ebi.spot.oxo.model.sssom;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

import java.net.URI;
import java.util.Optional;

/**
 * In SSSOM Uri refers to instances xsd:anyURI.
 */
public class Uri {
    @JsonValue
    private final String uriAsString;

    private final Optional<URI> uriRepresentation;


    public Uri(String uri) {
        this.uriAsString = uri;
        Optional<URI> tempUri;
        try {
            tempUri = Optional.of(URI.create(uriAsString));
        } catch (Exception e) {
            tempUri = Optional.empty();
        }
        this.uriRepresentation = tempUri;
    }
}
