package uk.ac.ebi.spot.oxo.model.sssom;

import java.util.HashMap;
import java.util.Map;

public class PrefixMap {
    private static Map<String, String> prefixMap = new HashMap<>();

    public void add(Prefix prefix) {
        prefixMap.put(prefix.getName(), prefix.getUrl());
    }
}
