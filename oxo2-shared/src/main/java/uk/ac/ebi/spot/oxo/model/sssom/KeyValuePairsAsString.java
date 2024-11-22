package uk.ac.ebi.spot.oxo.model.sssom;

import java.util.*;

/**
 * Pipe separated list of key value pairs for properties not part of the SSSOM spec.
 * Can be used to encode additional provenance data.
 *
 */
public class KeyValuePairsAsString {

    private final String keyValuePairsAsString;

    private final SortedSet<String> keyValuePairsAsSet;

    private final SortedMap<String, String> keyValuePairsAsMap;


    public KeyValuePairsAsString(String keyValuePairsAsString) {
        this.keyValuePairsAsString = keyValuePairsAsString;
        this.keyValuePairsAsSet = new TreeSet<>(extractKeyValuePairsAsSet(keyValuePairsAsString));
        this.keyValuePairsAsMap = new TreeMap<>(extractKeyValuePairs(keyValuePairsAsString));
    }
    private static Map<String, String> extractKeyValuePairs(String pipeDelimitedString) {
        Map<String, String> keyValuePairs = new HashMap<>();
        if (pipeDelimitedString == null || pipeDelimitedString.isEmpty()) {
            return keyValuePairs;
        }

        String[] pairs = pipeDelimitedString.split("\\|");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=");
            if (keyValue.length == 2) {
                keyValuePairs.put(keyValue[0].trim(), keyValue[1].trim());
            }
        }
        return keyValuePairs;
    }

    private static Set<String> extractKeyValuePairsAsSet(String pipeDelimitedString) {
        Set<String> keyValuePairsSet = new HashSet<>();
        if (pipeDelimitedString == null || pipeDelimitedString.isEmpty()) {
            return keyValuePairsSet;
        }

        String[] pairs = pipeDelimitedString.split("\\|");
        for (String pair : pairs) {
            keyValuePairsSet.add(pair.trim());
        }
        return keyValuePairsSet;
    }

    public SortedSet<String> getKeyValuePairsAsSet() {
        return keyValuePairsAsSet;
    }

    public SortedMap<String, String> getKeyValuePairsAsMap() {
        return keyValuePairsAsMap;
    }
}