package uk.ac.ebi.spot.oxo.model.sssom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * In SSSOM Uri refers to instances xsd:anyURI.
 */
public class Uri extends SSSOMDataType<URI> implements Comparable<Uri> {

    private static final Logger logger = LoggerFactory.getLogger(Uri.class);

    private static Map<String, URI> stringToUriMap = new HashMap();

    public Uri(String uri) {
        super(uri);
    }

    @Override
    protected Optional<URI> parseData(String uri) {
        Optional<URI> tempUri = Optional.empty();
        if (uri == null || uri.isBlank()) {
            return tempUri;
        }
        if (!uri.isBlank()) {
            if (stringToUriMap.containsKey(uri)) {
                return Optional.of(stringToUriMap.get(uri));
            }
            if (!uri.contains(":")) {
                logger.warn("URI is null or does not contain a colon, uri: {}", uri);
            }
            try {
                URI uriAsUri = URI.create(uri);
                tempUri = Optional.of(uriAsUri);
                logger.debug("URI created: {}", uriAsUri);
                stringToUriMap.put(uri, uriAsUri);
            } catch (Exception e) {
                tempUri = Optional.empty();
            }
        }

        return tempUri;
    }

    @Override
    protected SSSOMDataTypesEnum getType() {
        return SSSOMDataTypesEnum.URI;
    }


    @Override
    public int compareTo(Uri other) {
        return this.dataAsString.compareTo(other.dataAsString);
    }

    public String extractFragmentOrLastPathSegment() {
        if (dataAsString == null || dataAsString.isEmpty()) {
            return "";
        }

        if (!dataAsString.contains("#") && !dataAsString.contains("/")) {
            return dataAsString;
        }

        int hashIndex = dataAsString.lastIndexOf('#');
        if (hashIndex != -1) {
            return dataAsString.substring(hashIndex + 1);
        }

        int lastSlashIndex = dataAsString.lastIndexOf('/');
        if (lastSlashIndex != -1) {
            if (lastSlashIndex == dataAsString.length() - 1) {
                int secondLastSlashIndex = dataAsString.lastIndexOf('/', lastSlashIndex - 1);
                if (secondLastSlashIndex != -1) {
                    return dataAsString.substring(secondLastSlashIndex + 1, lastSlashIndex);
                }
            } else {
                return dataAsString.substring(lastSlashIndex + 1);
            }
        }

        return dataAsString;
    }

    public String asStringIRI(){
        return dataAsString;
    }

}
