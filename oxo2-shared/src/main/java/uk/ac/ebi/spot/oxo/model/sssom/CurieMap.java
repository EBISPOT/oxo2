package uk.ac.ebi.spot.oxo.model.sssom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class CurieMap extends SSSOMDataType<Map<String, String>> {

    private static final Logger logger = LoggerFactory.getLogger(CurieMap.class);

    public CurieMap(String curieMapAsString) {
        super(curieMapAsString);
        addCurieMapToMap(PrefixMap.getPrefixMap());   
    }

    @Override
    protected Optional<Map<String, String>> parseData(String data) {
        if (data != null && !data.isBlank()) {
            Map<String, String> map = convertStringToMap(data);
            map.putAll(PrefixMap.getPrefixMap());
            return Optional.of(map);
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
                map.put(key.toUpperCase(), value);
            }
        }
        return map;
    }

    public Map<String, String> addCurieMapToMap(Map<String, String> prefixToIRIMap) {
        if (this.dataRepresentation.isEmpty())
            return prefixToIRIMap;
        else {
            this.dataRepresentation.get().forEach((key, value) -> {
                prefixToIRIMap.put(key, value);
            });
        }
        return prefixToIRIMap;
    }

    public static Optional<CurieMap> merge(CurieMap thisCurieMap, CurieMap thatCurieMap) {
        if ((thisCurieMap == null || thisCurieMap.dataRepresentation.isEmpty())) {
            logger.warn("thisCurieMap is null or empty. No merge performed.");
            if (thatCurieMap != null) {
                return Optional.of(thatCurieMap);
            }
            return Optional.empty();
        }
        if ((thatCurieMap == null || thatCurieMap.dataRepresentation.isEmpty())) {
            logger.warn("thatCurieMap is null or empty. No merge performed.");
            if (thisCurieMap != null) {
                return Optional.of(thisCurieMap);
            }
            return Optional.empty();
        }

        Map<String, String> mergedMap = thisCurieMap.dataRepresentation.get();
        mergedMap.putAll(thatCurieMap.dataRepresentation.get());

        return Optional.of(new CurieMap(convertMapToString(mergedMap)));
    }
}
