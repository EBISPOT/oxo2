package uk.ac.ebi.spot.oxo.model.sssom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

/**
 * EntityReference refers to a rdfs:Resource @see <a href="https://mapping-commons.github.io/sssom/EntityReference/>
 * EntityReference</a>.
 *
 *  According to <a href="https://mapping-commons.github.io/sssom/spec-model/#identifiers"/> can represent a full length
 *  IRI or a curie. This is the difference between this class and Uri. The Uri class always aims to represent a URI, where
 *  as this class could represent a curie.
 *
 *  This class is implemented as a subclass of SSSOMDataType, which is strictly speaking not necessary because there is no
 *  difference between the dataAsString and the dataRepresentation. However, it is implemented this way to avoid
 *  boilerplate code.
 */
public class EntityReference extends SSSOMDataType<String> implements Comparable<EntityReference> {

    private static final Logger logger = LoggerFactory.getLogger(EntityReference.class);

    public EntityReference(String uri) {
        super(uri);
    }

    @Override
    protected Optional<String> parseData(String uri) {
        if (uri == null || uri.isBlank())
            return Optional.empty();
        int index = uri.indexOf(':');
        if (index != -1) {
            String prefix = uri.substring(0, index).toUpperCase();
            String suffix = uri.substring(index);
            return Optional.of(prefix + suffix);
        } else
            logger.warn("EntityReference uri is null or does not contain a colon, uri: {}", uri);
        return Optional.of(uri.toUpperCase());
    }

    @Override
    protected SSSOMDataTypesEnum getType() {
        return SSSOMDataTypesEnum.ENTITY_REFERENCE;
    }


    @Override
    public int compareTo(EntityReference o) {
        return this.getDataAsString().compareTo(o.getDataAsString());
    }

    public Optional<Uri> toUri(CurieMap curieMap) {

        int index = this.dataAsString.indexOf(':');
        if (index == -1) {
            logger.warn("EntityReference does not contain a colon: {}", this.dataAsString);
            return Optional.empty();
        }

        String prefix = this.dataAsString.substring(0, index).toUpperCase();
        String suffix = this.dataAsString.substring(index + 1);

        if ( curieMap.dataRepresentation.isEmpty()) {
            logger.warn("curieMap data representation is empty for: {} ", curieMap.dataAsString );
            return Optional.empty();
        }

        String baseUri = curieMap.dataRepresentation.get().get(prefix);
        if (baseUri == null) {
            logger.warn("Prefix not found in prefixMap: {}", prefix);
            return Optional.empty();
        }

        String fullUri = baseUri + suffix;
        try {
            return Optional.of(new Uri(fullUri));
        } catch (Exception e) {
            logger.error("Failed to create URI from: {}", fullUri, e);
            return Optional.empty();
        }
    }
}
