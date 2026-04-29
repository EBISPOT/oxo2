package uk.ac.ebi.spot.oxo.model.sssom;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PrefixMap {
    private static Map<String, String> prefixMap = new HashMap<>();

    // Reverse index for IRI -> CURIE conversion: entries sorted by IRI length descending
    // so that longer (more specific) prefixes win over shorter overlapping ones.
    private static List<Map.Entry<String, String>> iriToPrefixSorted = Collections.emptyList();

    static {
        setPredefinedPrefixes();
    }

    public void add(Prefix prefix) {
        prefixMap.put(prefix.getName(), prefix.getUrl());
        rebuildReverseIndex();
    }

    /**
     * These are prefixes that are used throughout the SSSOM specification.
     * See <a href="https://mapping-commons.github.io/sssom/spec-intro/#iri-prefixes">IRI Prefixes</a>
     */
    private static void setPredefinedPrefixes() {
        prefixMap.put("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
        prefixMap.put("rdfs", "http://www.w3.org/2000/01/rdf-schema#");
        prefixMap.put("owl", "http://www.w3.org/2002/07/owl#");
        prefixMap.put("xsd", "http://www.w3.org/2001/XMLSchema#");
        prefixMap.put("skos", "http://www.w3.org/2004/02/skos/core#");
        prefixMap.put("sssom", "https://w3id.org/sssom/");
        prefixMap.put("semapv", "https://w3id.org/semapv/vocab/");
        prefixMap.put("linkml", "https://w3id.org/linkml/");
        rebuildReverseIndex();
    }

    private static void rebuildReverseIndex() {
        List<Map.Entry<String, String>> entries = new ArrayList<>(prefixMap.size());
        for (Map.Entry<String, String> e : prefixMap.entrySet()) {
            entries.add(new AbstractMap.SimpleImmutableEntry<>(e.getValue(), e.getKey()));
        }
        entries.sort((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()));
        iriToPrefixSorted = entries;
    }

    public static Map<String, String> getPrefixMap() {
        return prefixMap;
    }

    /**
     * Convert an IRI to a CURIE using the registered prefixes (longest match wins).
     * Returns {@code Optional.empty()} if no registered prefix is a prefix of the given IRI.
     */
    public static Optional<String> toCurie(String iri) {
        if (iri == null || iri.isEmpty()) {
            return Optional.empty();
        }
        for (Map.Entry<String, String> e : iriToPrefixSorted) {
            String iriPrefix = e.getKey();
            if (iri.length() > iriPrefix.length() && iri.startsWith(iriPrefix)) {
                return Optional.of(e.getValue() + ":" + iri.substring(iriPrefix.length()));
            }
        }
        return Optional.empty();
    }

}
