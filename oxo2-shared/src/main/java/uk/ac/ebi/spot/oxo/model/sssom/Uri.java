package uk.ac.ebi.spot.oxo.model.sssom;

import com.fasterxml.jackson.annotation.JsonValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Collection;
import java.util.Optional;

/**
 * In SSSOM Uri refers to instances xsd:anyURI.
 */
public class Uri implements Comparable<Uri> {
    @JsonValue
    private final String uriAsString;

    private final Optional<URI> uriRepresentation;

    private static final Logger logger = LoggerFactory.getLogger(Uri.class);


    public Uri(String uri) {
        this.uriAsString = uri;
        Optional<URI> tempUri = Optional.empty();
        if (!uri.isBlank()) {
            if (!uri.contains(":")) {
                logger.warn("URI is null or does not contain a colon, uri: {}", uri);
            }
            try {
                URI uriAsUri = URI.create(uri);
                tempUri = Optional.of(uriAsUri);
                logger.debug("URI created: {}", uriAsUri);
            } catch (Exception e) {
                tempUri = Optional.empty();
            }
        }
        this.uriRepresentation = tempUri;
    }

    public String getUriAsString() {
        return uriAsString;
    }

    public Optional<URI> getUriRepresentation() {
        return uriRepresentation;
    }

    @Override
    public int compareTo(Uri other) {
        return this.uriAsString.compareTo(other.uriAsString);
    }

    public String extractFragmentOrLastPathSegment() {
        if (uriAsString == null || uriAsString.isEmpty()) {
            return "";
        }

        if (!uriAsString.contains("#") && !uriAsString.contains("/")) {
            return uriAsString;
        }

        int hashIndex = uriAsString.lastIndexOf('#');
        if (hashIndex != -1) {
            return uriAsString.substring(hashIndex + 1);
        }

        int lastSlashIndex = uriAsString.lastIndexOf('/');
        if (lastSlashIndex != -1) {
            if (lastSlashIndex == uriAsString.length() - 1) {
                int secondLastSlashIndex = uriAsString.lastIndexOf('/', lastSlashIndex - 1);
                if (secondLastSlashIndex != -1) {
                    return uriAsString.substring(secondLastSlashIndex + 1, lastSlashIndex);
                }
            } else {
                return uriAsString.substring(lastSlashIndex + 1);
            }
        }

        return uriAsString;
    }

    public static class ConditionalInclusionFilter {
        @Override
        public boolean equals(Object object) {
            logger.debug("Checking if URI should be included: {}", object);
            boolean result = false;
            logger.trace("Initial result to false.");
            if (object == null) {
                result = true; // Ignore field if null
                logger.trace("Object is null. Result set to {}", result);
            } else if (object instanceof Optional) {
                logger.trace("Uri is an optional");
                Optional optional = (Optional) object;
                if (optional.isPresent()) {
                    Object value = optional.get();
                    if (value instanceof Uri) {
                        Uri uriValue = (Uri) value;
                        result = uriValue.uriRepresentation.isEmpty() && uriValue.uriAsString.isBlank();
                        logger.trace("uriValue.uriRepresentation.isEmpty()={} uriValue.uriAsString.isBlank()={}. result={}",
                                uriValue.uriRepresentation.isEmpty(), uriValue.uriAsString.isBlank(), result);
                    }
                }
            } else if (object instanceof Collection){
                Collection collection = (Collection) object;
                logger.trace("Uri is part of a collection.size={}", collection.size());
                if (collection.isEmpty()) {
                    result = true;
                    logger.trace("4. Result set to {}", result);
                } else {
                    Optional firstElementOptional = collection.stream().findFirst();
                    if (firstElementOptional.isEmpty()) {
                        result = true;
                        logger.trace(". Result set to {}", result);
                    } else {
                        Object firstElement = firstElementOptional.get();
                        if (firstElement instanceof Uri) {
                            Uri uriValue = (Uri) firstElement;
                            result = uriValue.uriRepresentation.isPresent() && !uriValue.uriAsString.isBlank();
                            logger.debug("uriValue.uriRepresentation.isPresent() = {}, uriValue.uriAsString.isBlank() = {}",
                                    uriValue.uriRepresentation.isPresent(), uriValue.uriAsString.isBlank());
                            logger.trace("5. Result set to {}", result);
                        }
                    }
                }
            }
            else if (object instanceof Uri) {
                Uri uriValue = (Uri) object;
                result = uriValue.uriRepresentation.isEmpty() && uriValue.uriAsString.isEmpty();
                logger.trace("6. Result set to {}", result);
            } else {
                result = false;
            }
            logger.debug("Checking if URI should be included for object {} result = {}", object, result);
            return result;
        }
    }


    @Override
    public String toString() {
        return "Uri{" +
                "uriAsString='" + uriAsString + '\'' +
                ", uriRepresentation=" + uriRepresentation +
                '}';
    }
}
