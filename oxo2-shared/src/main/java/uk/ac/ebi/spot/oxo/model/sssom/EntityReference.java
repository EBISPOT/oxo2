package uk.ac.ebi.spot.oxo.model.sssom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * EntityReference refers to an rdfs:Resource @see <a href="https://mapping-commons.github.io/sssom/EntityReference/>
 * EntityReference</a>.
 *
 *  According to <a href="https://mapping-commons.github.io/sssom/spec-model/#identifiers"/> can represent a full length
 *  IRI or a curie. This the difference between this class and Uri. The Uri class always aims to represent a URI, where
 *  this class could represent a curie.
 *
 *  This class is implemented as a subclass of SSSOMDataType, which strictly speaking not necessary because there is no
 *  difference between the dataAsString and the dataRepresentation. However, it is implemented this way to avoid
 *  boilerplate code.
 */
public class EntityReference extends SSSOMDataType<String> implements Comparable<EntityReference> {

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

    private static final Logger logger = LoggerFactory.getLogger(EntityReference.class);

    public EntityReference(String uri) {
        super(uri);
    }

    @Override
    public int compareTo(EntityReference o) {
        return this.getDataAsString().compareTo(o.getDataAsString());
    }
}
