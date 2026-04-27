package uk.ac.ebi.spot.oxo.model.sssom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.spot.oxo.utils.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * EntityReference refers to a rdfs:Resource @see <a href="https://mapping-commons.github.io/sssom/EntityReference/>
 * EntityReference</a>.
 *
 *  According to <a href="https://mapping-commons.github.io/sssom/spec-model/#identifiers"/> can represent a full length
 *  IRI or a curie. This is the difference between this class and Uri. The Uri class always aims to represent a URI,
 *  whereas this class could represent a curie.
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

    private static Map<String, String> uriOrCurieToStringMap = new HashMap();

    /**
     * Reset the per-JVM cache. Callers (e.g. SSSOM2JSON's per-file loop) must invoke this
     * between mapping sets, otherwise the map retains every distinct CURIE/URI string from
     * every prior file and OOMs the JVM on large inputs (e.g. NCBI taxon).
     */
    public static void clearCache() {
        uriOrCurieToStringMap.clear();
    }

    @Override
    protected Optional<String> parseData(String uriOrCurie) {
        if (uriOrCurie == null || uriOrCurie.isBlank())
            return Optional.empty();

        if (uriOrCurieToStringMap.containsKey(uriOrCurie))
            return Optional.of(uriOrCurieToStringMap.get(uriOrCurie));
        int index = uriOrCurie.indexOf(':');
        if (index != -1) {
            String suffix = uriOrCurie.substring(index);
            String prefix = StringUtils.isUriNotCurie(suffix) ? uriOrCurie.substring(0, index) :
                    uriOrCurie.substring(0, index).toUpperCase();
            uriOrCurieToStringMap.put(uriOrCurie, prefix + suffix);
            return Optional.of(prefix + suffix);
        } else {
            logger.warn("EntityReference uri is null or does not contain a colon, uri: {}", uriOrCurie);
        }
        uriOrCurieToStringMap.put(uriOrCurie, uriOrCurie.toUpperCase());
        return Optional.of(uriOrCurie.toUpperCase());
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

        if (prefix.toLowerCase().startsWith("http")) {
            return Optional.of(new Uri(dataAsString));
        }

        if ( curieMap.dataRepresentation.isEmpty()) {
            logger.warn("curieMap data representation is empty for: {} ", curieMap.dataAsString );
            return Optional.empty();
        }

        String baseUri = curieMap.dataRepresentation.get().get(prefix);
        String baseUriLower = curieMap.dataRepresentation.get().get(prefix.toLowerCase());
        String baseUriUpper = curieMap.dataRepresentation.get().get(prefix.toUpperCase());

        if (baseUri == null && baseUriLower == null && baseUriUpper == null) {
            logger.warn("Prefix not found in prefixMap: {}", prefix);
            return Optional.empty();
        }

        if (baseUri == null) {
            if (baseUriLower != null) {
                baseUri = baseUriLower;
            } else if (baseUriUpper != null) {
                baseUri = baseUriUpper;
            }
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
