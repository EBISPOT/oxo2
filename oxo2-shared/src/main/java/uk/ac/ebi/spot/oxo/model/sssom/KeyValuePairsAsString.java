package uk.ac.ebi.spot.oxo.model.sssom;

import java.util.*;

/**
 * Pipe separated list of key value pairs for properties not part of the SSSOM spec.
 * Can be used to encode additional provenance data.
 *
 */
public class KeyValuePairsAsString extends SSSOMDataType<Map<String,String>> {

    public KeyValuePairsAsString(String keyValuePairsAsString) {
        super(keyValuePairsAsString);
    }

    public KeyValuePairsAsString(Map<String, String> keyValuePairs) {
        super(convertMapToString(keyValuePairs));
    }
    @Override
    protected Optional<Map<String, String>> parseData(String pipeDelimitedString) {
        if (pipeDelimitedString != null && !pipeDelimitedString.isBlank()) {
            return Optional.of(extractKeyValuePairs(pipeDelimitedString));
        }
        return Optional.empty();
    }

    @Override
    protected SSSOMDataTypesEnum getType() {
        return SSSOMDataTypesEnum.KEY_VALUE_PAIRS;
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

    private static String convertMapToString(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (sb.length() > 0) {
                sb.append("|");
            }
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return sb.toString();
    }
}