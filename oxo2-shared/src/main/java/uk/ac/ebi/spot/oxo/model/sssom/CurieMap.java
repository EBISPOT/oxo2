package uk.ac.ebi.spot.oxo.model.sssom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class CurieMap extends SSSOMDataType<Map<String, String>> {

    private static final Logger logger = LoggerFactory.getLogger(CurieMap.class);

    public CurieMap(String curieMapAsString) {
        super(curieMapAsString.toLowerCase());
    }

    @Override
    protected Optional<Map<String, String>> parseData(String data) {
        if (data != null && !data.isBlank()) {
            return Optional.of(convertStringToMap(data));
        }
        return Optional.empty();
    }



    @Override
    protected SSSOMDataTypesEnum getType() {
        return SSSOMDataTypesEnum.CURIE_MAP;
    }


    public static String convertMapToString(Map<String, String> map) {
        return map.entrySet()
                .stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .collect(Collectors.joining(", "));
    }

    private static SortedMap<String, String> convertStringToMap(String input) {
        SortedMap<String, String> map = new TreeMap<>();
        String[] pairs = input.split(", ");
        for (String pair : pairs) {
            int idx = pair.indexOf(':');
            if (idx != -1) {
                String key = pair.substring(0, idx);
                String value = pair.substring(idx + 1);
                map.put(key.toLowerCase(), value.toLowerCase());
            }
        }
        return map;
    }
}
