package uk.ac.ebi.spot.oxo.model.sssom;

import com.fasterxml.jackson.annotation.JsonValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class CurieMap {
    private final SortedMap<String, String> curieMap;

    @JsonValue
    private final String curieMapAsString;

    private static final Logger logger = LoggerFactory.getLogger(CurieMap.class);

    public CurieMap(SortedMap<String, String> curieMap) {
        this.curieMap = curieMap;
        this.curieMapAsString = convertMapToString(curieMap);
    }

    public CurieMap(String curieMapAsString) {
        logger.debug("Creating CurieMap from string: {}", curieMapAsString);
        this.curieMap = convertStringToMap(curieMapAsString);
        this.curieMapAsString = curieMapAsString;
    }

    private static String convertMapToString(Map<String, String> map) {
        return map.entrySet()
                .stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .collect(Collectors.joining(","));
    }

    private static SortedMap<String, String> convertStringToMap(String input) {
        SortedMap<String, String> map = new TreeMap<>();
        String[] pairs = input.split(", ");
        for (String pair : pairs) {
            int idx = pair.indexOf(':');
            if (idx != -1) {
                String key = pair.substring(0, idx);
                String value = pair.substring(idx + 1);
                map.put(key, value);
            }
        }
        return map;
    }
}
