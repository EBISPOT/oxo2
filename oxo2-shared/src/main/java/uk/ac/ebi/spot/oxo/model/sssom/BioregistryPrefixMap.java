package uk.ac.ebi.spot.oxo.model.sssom;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * The Bioregistry prefix map, bundled as a resource snapshot.
 *
 * <p>SSSOM permits a mapping set to omit its {@code curie_map}; the convention (followed by
 * sssom-py) is then to expand CURIEs against the Bioregistry. Some published sets ship a bare
 * TSV with no metadata header at all — notably the biopragmatics SeMRA landscape
 * {@code priority} views, whose CURIEs are minted entirely from Bioregistry prefixes. For
 * those, {@code TSV2JSON} synthesises the set metadata from the row columns and uses this map
 * as the fallback {@code curie_map} so the CURIEs still expand to IRIs.
 *
 * <p>The snapshot lives at {@code src/main/resources/bioregistry.context.jsonld} (the
 * {@code @context} object of Bioregistry's published JSON-LD context). Refresh it with
 * {@code oxo2-shared/refresh-bioregistry-context.sh}. It is applied ONLY as a fallback for
 * sets that declare no curie_map; sets that ship their own curie_map are unaffected.
 */
public final class BioregistryPrefixMap {

    private static final Logger logger = LoggerFactory.getLogger(BioregistryPrefixMap.class);
    private static final String RESOURCE = "/bioregistry.context.jsonld";

    private static volatile SortedMap<String, String> prefixToIri;

    private BioregistryPrefixMap() {}

    /**
     * @return prefix &rarr; IRI map parsed from the bundled Bioregistry context (lower-case
     *         prefixes as published; {@link EntityReference#toUri} matches case-insensitively).
     *         An empty map if the resource is missing or unreadable — callers degrade to "no
     *         expansion" rather than failing.
     */
    public static SortedMap<String, String> get() {
        SortedMap<String, String> local = prefixToIri;
        if (local == null) {
            synchronized (BioregistryPrefixMap.class) {
                local = prefixToIri;
                if (local == null) {
                    local = load();
                    prefixToIri = local;
                }
            }
        }
        return local;
    }

    private static SortedMap<String, String> load() {
        SortedMap<String, String> map = new TreeMap<>();
        try (InputStream resourceStream = BioregistryPrefixMap.class.getResourceAsStream(RESOURCE)) {
            if (resourceStream == null) {
                logger.error("Bundled Bioregistry context {} not found on classpath; CURIEs in "
                        + "sets without a curie_map will not expand.", RESOURCE);
                return Collections.emptySortedMap();
            }
            JsonNode context = JsonMapper.builder().build().readTree(resourceStream).path("@context");
            for (Map.Entry<String, JsonNode> contextField : context.properties()) {
                String prefix = contextField.getKey();
                JsonNode expansion = contextField.getValue();
                // Skip JSON-LD keywords (@vocab, @base, ...) and any non-string expansion.
                if (prefix.startsWith("@") || !expansion.isString()) {
                    continue;
                }
                map.put(prefix, expansion.asString());
            }
            logger.info("Loaded {} prefixes from the bundled Bioregistry context.", map.size());
        } catch (IOException | JacksonException e) {
            logger.error("Failed to read bundled Bioregistry context {}", RESOURCE, e);
            return Collections.emptySortedMap();
        }
        return map;
    }
}
