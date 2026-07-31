package uk.ac.ebi.spot.oxo.sssom2json;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Diagnostic: find CURIE prefixes that different SSSOM sets expand to different IRI stems.
 *
 * <p>When two sets declare, say, {@code MESH:} with different base IRIs, the same entity reaches
 * Nemo as two distinct IRI nodes — the aliasing that produces duplicate cross-set conclusions and
 * self-referential explanations (ADR-0028). This detector surfaces those prefixes so the curated
 * {@code iri-prefix-overrides.json} table (ADR-0029) can be seeded from evidence rather than guessed.
 *
 * <p>It reads the materialised {@code mappingSet/} JSON directory (each set's {@code curie_map}) and
 * prints every prefix with more than one distinct stem, splitting genuine host-aliases from obvious
 * curie_map junk (double-expanded OBO PURLs, {@code unknown_prefix} placeholders). It is a read-only
 * diagnostic — run it on demand, it does not participate in the dataload pipeline.
 *
 * <p>Usage: {@code java ... PrefixDivergenceDetector <mappingSet-dir>}
 */
public final class PrefixDivergenceDetector {

    private static final Logger logger = LoggerFactory.getLogger(PrefixDivergenceDetector.class);
    private static final JsonMapper OBJECT_MAPPER = JsonMapper.builder().build();

    private PrefixDivergenceDetector() {}

    public static void main(String[] arguments) throws IOException {
        if (arguments.length != 1) {
            System.err.println("Usage: PrefixDivergenceDetector <mappingSet-dir>");
            System.exit(2);
        }
        Path mappingSetDir = Path.of(arguments[0]);
        Map<String, Map<String, String>> curieMapsBySet = readCurieMapsBySet(mappingSetDir);
        SortedMap<String, SortedMap<String, List<String>>> divergent =
                findDivergentPrefixes(curieMapsBySet);
        System.out.print(report(divergent, curieMapsBySet.size()));
    }

    /**
     * @return prefix &rarr; (IRI stem &rarr; set names that declared it), for every prefix that more
     *         than one set expands inconsistently. Prefixes all sets agree on are omitted.
     */
    public static SortedMap<String, SortedMap<String, List<String>>> findDivergentPrefixes(
            Map<String, Map<String, String>> curieMapsBySet) {
        SortedMap<String, SortedMap<String, List<String>>> stemsByPrefix = new TreeMap<>();
        curieMapsBySet.forEach((setName, curieMap) ->
                curieMap.forEach((prefix, stem) -> stemsByPrefix
                        .computeIfAbsent(prefix, unused -> new TreeMap<>())
                        .computeIfAbsent(stem, unused -> new ArrayList<>())
                        .add(setName)));
        SortedMap<String, SortedMap<String, List<String>>> divergent = new TreeMap<>();
        stemsByPrefix.forEach((prefix, stems) -> {
            if (stems.size() > 1) {
                divergent.put(prefix, stems);
            }
        });
        return divergent;
    }

    /** A stem that is a curie_map bug rather than a real host-alias (so not an override candidate). */
    static boolean isJunkStem(String stem) {
        return stem.contains("obo/http") || stem.contains("unknown_prefix");
    }

    static String report(SortedMap<String, SortedMap<String, List<String>>> divergent, int setsScanned) {
        long genuine = divergent.values().stream()
                .filter(stems -> stems.keySet().stream().filter(stem -> !isJunkStem(stem)).count() > 1)
                .count();
        StringBuilder report = new StringBuilder();
        report.append("Scanned ").append(setsScanned).append(" sets. Divergent prefixes: ")
                .append(divergent.size()).append(" (genuine host-aliases: ").append(genuine)
                .append("). Override candidates are the genuine ones.\n\n");
        divergent.forEach((prefix, stems) -> {
            boolean genuineHostAlias = stems.keySet().stream().filter(stem -> !isJunkStem(stem)).count() > 1;
            report.append(prefix).append(genuineHostAlias ? "" : "  [curie_map-junk only]").append(":\n");
            stems.entrySet().stream()
                    .sorted((left, right) -> Integer.compare(right.getValue().size(), left.getValue().size()))
                    .forEach(entry -> report.append(String.format("    %3d sets  %s%s%n",
                            entry.getValue().size(), entry.getKey(),
                            isJunkStem(entry.getKey()) ? "  [junk]" : "")));
        });
        return report.toString();
    }

    private static Map<String, Map<String, String>> readCurieMapsBySet(Path mappingSetDir)
            throws IOException {
        Map<String, Map<String, String>> curieMapsBySet = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(mappingSetDir)) {
            for (Path file : (Iterable<Path>) files.filter(path -> path.toString().endsWith(".json"))::iterator) {
                String curieMapString = readCurieMapString(file);
                if (curieMapString != null && !curieMapString.isBlank()) {
                    curieMapsBySet.put(file.getFileName().toString(), parseCurieMap(curieMapString));
                }
            }
        }
        return curieMapsBySet;
    }

    private static String readCurieMapString(Path file) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(file.toFile());
            JsonNode set = root.isArray() && !root.isEmpty() ? root.get(0) : root;
            JsonNode curieMap = set.path("curie_map");
            return curieMap.isString() ? curieMap.asString() : null;
        } catch (JacksonException e) {
            logger.warn("Skipping unreadable set file {}: {}", file, e.getMessage());
            return null;
        }
    }

    /** Parse a {@code "PREFIX:stem, PREFIX2:stem2"} curie_map string; split on the FIRST colon (stems contain colons). */
    static Map<String, String> parseCurieMap(String curieMapString) {
        Map<String, String> curieMap = new LinkedHashMap<>();
        for (String pair : curieMapString.split(", ")) {
            int firstColon = pair.indexOf(':');
            if (firstColon != -1) {
                curieMap.put(pair.substring(0, firstColon).trim(), pair.substring(firstColon + 1).trim());
            }
        }
        return curieMap;
    }
}
