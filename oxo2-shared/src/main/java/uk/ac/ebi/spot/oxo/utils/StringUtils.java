package uk.ac.ebi.spot.oxo.utils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class StringUtils {

    public static <T> SortedSet<T> splitStringToSortedSet(String input, String delimiter, Function<String, T> mapper) {
        if (input==null || input.isBlank()) {
            return new TreeSet<>();
        }
        return Arrays.stream(input.split(delimiter))
                .map(mapper)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    public static <T> List<T> splitStringToList(String input, String delimiter, Function<String, T> mapper) {
        if (input == null || input.isBlank()) {
            return new ArrayList<>();
        }
        return Arrays.stream(input.split(delimiter))
                .map(mapper)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public static Optional<String> extractPrefix(String uriOrCurie) {
        int index = uriOrCurie.indexOf(':');
        if (index != -1) {
            String suffix = uriOrCurie.substring(index);
            String prefix = isUriNotCurie(suffix) ? uriOrCurie.substring(0, index) :
                    uriOrCurie.substring(0, index).toUpperCase();
            return isUriNotCurie(suffix) ? Optional.empty() : Optional.of(prefix);
        }
        return Optional.empty();
    }

    public static boolean isUriNotCurie(String suffix) {
        if (suffix.contains("//"))
            return true;
        return false;
    }
}
