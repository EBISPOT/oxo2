package uk.ac.ebi.spot.oxo.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.spot.oxo.model.sssom.CurieMap;

import java.net.URI;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class StringUtils {
    private static final Logger logger = LoggerFactory.getLogger(StringUtils.class);

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

    public static boolean isURIValid(String uri) {
        if (uri == null || uri.isBlank())
            return false;
        String invalidCharRegex = "[\\[\\]\\{\\}\\(\\)*@]";
        Pattern pattern = Pattern.compile(invalidCharRegex);
        if (pattern.matcher(uri).find())
            return false;
        try {
            URI asURI = new URI(uri);
        } catch (Throwable t) {
            logger.warn("Error while parsing URI: {} ", uri, t);
        }
        return true;
    }
}
