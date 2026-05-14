package uk.ac.ebi.spot.oxo.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stage-specific canonicalisation routines used by both the comparators and capture mode.
 *
 * For each layer, canonicalise() returns the layer's output as a deterministic byte-stable
 * string so that expected files can be committed in canonical form and diffs are meaningful.
 */
public final class Canonicalisers {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private Canonicalisers() {}

    /* ============================================================================
     * Inferred TTL
     * Input:  turtle file using `,`-shorthand and per-line statements.
     * Output: one N-Triples-style line per (s, p, o), sorted lexically.
     * ============================================================================ */

    private static final Pattern URI_ANGLE = Pattern.compile("<[^>]+>");

    /** Expands a turtle file with comma-shared objects into a sorted list of `<s> <p> <o> .` lines. */
    public static String canonicaliseTtl(String content) {
        List<String> triples = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        for (String rawLine : content.split("\\R")) {
            String line = stripComment(rawLine).trim();
            if (line.isEmpty()) continue;
            buf.append(' ').append(line);
            if (line.endsWith(".")) {
                String statement = buf.toString().trim();
                statement = statement.substring(0, statement.length() - 1).trim(); // drop trailing '.'
                triples.addAll(expandCommaObjects(statement));
                buf.setLength(0);
            }
        }
        Collections.sort(triples);
        StringBuilder out = new StringBuilder();
        for (String t : triples) {
            out.append(t).append('\n');
        }
        return out.toString();
    }

    private static String stripComment(String line) {
        int idx = line.indexOf('#');
        if (idx < 0) return line;
        // Don't strip inside an IRI (between < and >). Our test data uses no IRIs containing '#',
        // wait, it does — # is common in URIs. Solution: only strip '#' that appears outside angle brackets.
        StringBuilder sb = new StringBuilder();
        boolean inIri = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '<') inIri = true;
            else if (c == '>') inIri = false;
            else if (c == '#' && !inIri) {
                return sb.toString();
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static List<String> expandCommaObjects(String statement) {
        // Statement shape: <s> <p> <o1> , <o2> , <o3>
        // Find first two angle-bracket terms (subject, predicate) then split rest on commas.
        Matcher m = URI_ANGLE.matcher(statement);
        if (!m.find()) return Collections.emptyList();
        String subject = m.group();
        if (!m.find()) return Collections.emptyList();
        String predicate = m.group();
        int rest = m.end();
        String objects = statement.substring(rest).trim();
        List<String> triples = new ArrayList<>();
        for (String objRaw : objects.split(",")) {
            String obj = objRaw.trim();
            if (obj.isEmpty()) continue;
            triples.add(subject + " " + predicate + " " + obj + " .");
        }
        return triples;
    }

    public static String readTtl(Path path) throws IOException {
        return canonicaliseTtl(Files.readString(path, StandardCharsets.UTF_8));
    }

    /* ============================================================================
     * Nemo chain JSON
     * Generic tree-canonicalise: sort object keys alphabetically; sort arrays by
     * their serialized text. Then pretty-print.
     * ============================================================================ */

    public static String canonicaliseGenericJson(String content) throws IOException {
        JsonNode tree = MAPPER.readTree(content);
        JsonNode canon = canonNode(tree);
        return MAPPER.writeValueAsString(canon);
    }

    public static String readJson(Path path) throws IOException {
        return canonicaliseGenericJson(Files.readString(path, StandardCharsets.UTF_8));
    }

    private static JsonNode canonNode(JsonNode n) {
        if (n.isObject()) {
            ObjectNode src = (ObjectNode) n;
            ObjectNode dst = MAPPER.createObjectNode();
            // Stable order: sort field names.
            Map<String, JsonNode> sorted = new TreeMap<>();
            Iterator<Map.Entry<String, JsonNode>> it = src.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                sorted.put(e.getKey(), canonNode(e.getValue()));
            }
            for (Map.Entry<String, JsonNode> e : sorted.entrySet()) {
                dst.set(e.getKey(), e.getValue());
            }
            return dst;
        }
        if (n.isArray()) {
            ArrayNode src = (ArrayNode) n;
            List<JsonNode> children = new ArrayList<>(src.size());
            for (JsonNode child : src) children.add(canonNode(child));
            children.sort((a, b) -> {
                try {
                    return MAPPER.writeValueAsString(a).compareTo(MAPPER.writeValueAsString(b));
                } catch (JsonProcessingException ex) {
                    throw new RuntimeException(ex);
                }
            });
            ArrayNode dst = MAPPER.createArrayNode();
            for (JsonNode c : children) dst.add(c);
            return dst;
        }
        return n;
    }

    /* ============================================================================
     * OxO2 explained JSON
     * The explained JSON has fields `asserted_mappings` and `explanation` whose values
     * are JSON-encoded strings (escaped). We deserialise those strings recursively
     * before canonicalising, so the comparison sees one flat tree.
     * ============================================================================ */

    private static final String ASSERTED_MAPPINGS_FIELD = "asserted_mappings";
    private static final String EXPLANATION_FIELD = "explanation";
    private static final String PREMISES_FIELD = "premises";

    public static String canonicaliseExplainedJson(String content) throws IOException {
        JsonNode tree = MAPPER.readTree(content);
        JsonNode unwrapped = unwrapEmbeddedJsonStrings(tree);
        JsonNode canon = canonNode(unwrapped);
        return MAPPER.writeValueAsString(canon);
    }

    public static String readExplainedJson(Path path) throws IOException {
        return canonicaliseExplainedJson(Files.readString(path, StandardCharsets.UTF_8));
    }

    /** Recursively replace any text-valued `asserted_mappings` / `explanation` field with
     *  its parsed JSON tree, so nested premise structure is visible to the comparator. */
    private static JsonNode unwrapEmbeddedJsonStrings(JsonNode n) throws IOException {
        if (n.isObject()) {
            ObjectNode obj = (ObjectNode) n.deepCopy();
            for (String field : new String[]{ASSERTED_MAPPINGS_FIELD, EXPLANATION_FIELD}) {
                JsonNode v = obj.get(field);
                if (v != null && v.isTextual()) {
                    obj.set(field, MAPPER.readTree(v.asText()));
                }
            }
            Iterator<Map.Entry<String, JsonNode>> it = obj.fields();
            ObjectNode dst = MAPPER.createObjectNode();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                dst.set(e.getKey(), unwrapEmbeddedJsonStrings(e.getValue()));
            }
            return dst;
        }
        if (n.isArray()) {
            ArrayNode dst = MAPPER.createArrayNode();
            for (JsonNode child : n) {
                dst.add(unwrapEmbeddedJsonStrings(child));
            }
            return dst;
        }
        return n;
    }
}
