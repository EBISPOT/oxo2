package uk.ac.ebi.spot.oxo.model.sssom;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public class Prefix {
    @JsonProperty("name")
    private final String name;

    @JsonProperty("url")
    private final String url;

    private static SortedMap<String, String> prefixMap = new TreeMap<>();

    public Prefix(String name, String url) {
        this.name = name;
        this.url = url;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    public static void addPrefixes(Map<String, String> prefixes) {
        prefixes.forEach((k,v)-> prefixMap.put(k, v));
    }

}
