package uk.ac.ebi.spot.oxo.model.sssom;

import java.util.HashMap;
import java.util.Map;

public class PrefixMap {
    private static Map<String, String> prefixMap = new HashMap<>();
    
    static {
        setPredefinedPrefixes();
    }

    public void add(Prefix prefix) {   
        prefixMap.put(prefix.getName(), prefix.getUrl());
    }

    /**
     * These are prefixes that are used throughout the SSSOM specification.
     * See <a href="https://mapping-commons.github.io/sssom/spec-intro/#iri-prefixes">IRI Prefixes</a>
     * @return
     */
    private static Map<String, String> setPredefinedPrefixes() {
        prefixMap.put("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
        prefixMap.put("rdfs", "http://www.w3.org/2000/01/rdf-schema#");
        prefixMap.put("owl", "http://www.w3.org/2002/07/owl#");
        prefixMap.put("xsd", "http://www.w3.org/2001/XMLSchema#");
        prefixMap.put("skos", "http://www.w3.org/2004/02/skos/core#");
        prefixMap.put("sssom", "https://w3id.org/sssom/");
        prefixMap.put("semapv", "https://w3id.org/semapv/vocab/");
        prefixMap.put("linkml", "https://w3id.org/linkml/");
        return prefixMap;
    }

    public static Map<String, String> getPrefixMap() {
        return prefixMap;
    }

}
