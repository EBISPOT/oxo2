package uk.ac.ebi.spot.oxo.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
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

    public static boolean isUriNotCurie(String suffix) {
        if (suffix.contains("//"))
            return true;
        return false;
    }

    public static boolean isIri(String term) {
        if (term == null || term.isBlank()) return false;
        int colon = term.indexOf(':');
        if (colon < 0) return false;
        return isUriNotCurie(term.substring(colon));
    }

    public static boolean isCurie(String term) {
        if (term == null || term.isBlank()) return false;
        int colon = term.indexOf(':');
        if (colon < 0) return false;
        if (term.chars().anyMatch(Character::isWhitespace)) return false;
        return !isUriNotCurie(term.substring(colon));
    }

    public static boolean isURIValid(String uri) {
        if (uri == null || uri.isBlank())
            return false;
        String invalidCharRegex = "[\\[\\]\\{\\}\\(\\)*@]";
        Pattern pattern = Pattern.compile(invalidCharRegex);
        if (pattern.matcher(uri).find())
            return false;
        try {
            // An N-Quads IRI must be absolute (carry a scheme). Nemo's RDF reader rejects a
            // scheme-less IRI ("No scheme found in an absolute IRI") and silently drops the whole
            // triple, so reject it here — the skip is then logged and attributable at conversion
            // time instead of vanishing mid-trace.
            return new URI(uri).isAbsolute();
        } catch (URISyntaxException e) {
            logger.warn("Invalid URI, skipping: {}", uri, e);
            return false;
        }
    }
}
