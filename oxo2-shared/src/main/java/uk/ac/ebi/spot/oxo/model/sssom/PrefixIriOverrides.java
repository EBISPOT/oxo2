package uk.ac.ebi.spot.oxo.model.sssom;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Canonical entity-IRI overrides for prefixes whose SSSOM sets disagree on the IRI stem (ADR-0029).
 *
 * <p>Several published sets declare the same CURIE prefix with different base IRIs — e.g. one set
 * expands {@code MESH:} to {@code http://id.nlm.nih.gov/mesh/} while another uses
 * {@code http://identifiers.org/mesh/}. Nemo reasons over full IRIs, so the two spellings become
 * two distinct nodes for the same entity; cross-set inference then derives duplicate conclusions and
 * the explanation reconstruction, folding both IRIs back to one CURIE, produces a self-referential
 * (circular-looking) proof. See ADR-0028 for the reconstruction and ADR-0029 for this fix.
 *
 * <p>This class pins a single canonical IRI stem per problematic prefix. Unlike the Bioregistry
 * fallback ({@link BioregistryPrefixMap}, ADR-0015) — which applies only to sets that ship no
 * curie_map — an override is unconditional: {@link EntityReference#toUri} applies it ahead of the
 * set's own curie_map so an entity always expands to the one stem, whatever the source declared.
 * The Bioregistry <em>preferred</em> IRI is deliberately NOT reused here: for MeSH it is a third form
 * ({@code https://meshb.nlm.nih.gov/record/ui?ui=}) that we do not want to emit, so the canonical
 * stem is curated instead.
 *
 * <p>The table lives at {@code src/main/resources/iri-prefix-overrides.json}, keyed by UPPERCASE
 * prefix (matching {@link EntityReference}'s normalisation). It is curated, not exhaustive — seed
 * candidates come from {@code PrefixDivergenceDetector}.
 */
public final class PrefixIriOverrides {

    private static final Logger logger = LoggerFactory.getLogger(PrefixIriOverrides.class);
    private static final String RESOURCE = "/iri-prefix-overrides.json";

    // UPPERCASE prefix -> canonical IRI stem.
    private static volatile SortedMap<String, String> canonicalByPrefix;
    // (alias stem -> canonical stem), longest alias first so the most specific stem wins.
    private static volatile List<Map.Entry<String, String>> aliasToCanonicalSorted;

    private PrefixIriOverrides() {}

    /**
     * @return the canonical IRI stem to expand {@code prefixUpper} against, or {@code null} if the
     *         prefix has no override. Callers pass the UPPERCASE prefix.
     */
    public static String canonicalStem(String prefixUpper) {
        ensureLoaded();
        return canonicalByPrefix.get(prefixUpper);
    }

    /**
     * Rewrite a full IRI that uses a known alias stem of an overridden prefix to the canonical stem;
     * return the IRI unchanged if it matches no alias (including when it already uses the canonical
     * stem). For inputs that arrive as CURIEs use {@link #canonicalStem} instead.
     */
    public static String canonicalizeIri(String iri) {
        if (iri == null || iri.isEmpty()) {
            return iri;
        }
        ensureLoaded();
        for (Map.Entry<String, String> entry : aliasToCanonicalSorted) {
            String alias = entry.getKey();
            if (iri.startsWith(alias)) {
                return entry.getValue() + iri.substring(alias.length());
            }
        }
        return iri;
    }

    private static void ensureLoaded() {
        if (canonicalByPrefix == null) {
            synchronized (PrefixIriOverrides.class) {
                if (canonicalByPrefix == null) {
                    load();
                }
            }
        }
    }

    private static void load() {
        SortedMap<String, String> byPrefix = new TreeMap<>();
        List<Map.Entry<String, String>> aliasEntries = new ArrayList<>();
        try (InputStream resourceStream = PrefixIriOverrides.class.getResourceAsStream(RESOURCE)) {
            if (resourceStream == null) {
                logger.error("Bundled IRI override table {} not found on classpath; no prefixes will "
                        + "be canonicalised.", RESOURCE);
                canonicalByPrefix = Collections.emptySortedMap();
                aliasToCanonicalSorted = Collections.emptyList();
                return;
            }
            JsonNode root = JsonMapper.builder().build().readTree(resourceStream);
            for (Map.Entry<String, JsonNode> field : root.properties()) {
                String prefix = field.getKey();
                JsonNode entry = field.getValue();
                // Skip documentation keys (e.g. "_comment") and any malformed entry.
                if (prefix.startsWith("_") || !entry.isObject() || !entry.path("canonical").isString()) {
                    continue;
                }
                String canonical = entry.get("canonical").asString();
                byPrefix.put(prefix.toUpperCase(), canonical);
                for (JsonNode aliasNode : entry.path("aliases")) {
                    String alias = aliasNode.asString();
                    if (!alias.isEmpty() && !alias.equals(canonical)) {
                        aliasEntries.add(new AbstractMap.SimpleImmutableEntry<>(alias, canonical));
                    }
                }
            }
            aliasEntries.sort((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()));
            logger.info("Loaded {} IRI-prefix override(s) with {} alias stem(s).",
                    byPrefix.size(), aliasEntries.size());
        } catch (IOException | JacksonException e) {
            logger.error("Failed to read bundled IRI override table {}", RESOURCE, e);
            canonicalByPrefix = Collections.emptySortedMap();
            aliasToCanonicalSorted = Collections.emptyList();
            return;
        }
        canonicalByPrefix = byPrefix;
        aliasToCanonicalSorted = aliasEntries;
    }
}
