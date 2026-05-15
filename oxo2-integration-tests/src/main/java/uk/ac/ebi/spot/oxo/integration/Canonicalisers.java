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
        StringBuilder statementBuffer = new StringBuilder();
        for (String rawLine : content.split("\\R")) {
            String line = stripComment(rawLine).trim();
            if (line.isEmpty()) continue;
            statementBuffer.append(' ').append(line);
            if (line.endsWith(".")) {
                String statement = statementBuffer.toString().trim();
                statement = statement.substring(0, statement.length() - 1).trim(); // drop trailing '.'
                triples.addAll(expandCommaObjects(statement));
                statementBuffer.setLength(0);
            }
        }
        Collections.sort(triples);
        StringBuilder output = new StringBuilder();
        for (String triple : triples) {
            output.append(triple).append('\n');
        }
        return output.toString();
    }

    private static String stripComment(String line) {
        int commentIndex = line.indexOf('#');
        if (commentIndex < 0) return line;
        // Don't strip inside an IRI (between < and >). Our test data uses no IRIs containing '#',
        // wait, it does — # is common in URIs. Solution: only strip '#' that appears outside angle brackets.
        StringBuilder stripped = new StringBuilder();
        boolean inIri = false;
        for (int i = 0; i < line.length(); i++) {
            char character = line.charAt(i);
            if (character == '<') inIri = true;
            else if (character == '>') inIri = false;
            else if (character == '#' && !inIri) {
                return stripped.toString();
            }
            stripped.append(character);
        }
        return stripped.toString();
    }

    private static List<String> expandCommaObjects(String statement) {
        // Statement shape: <s> <p> <o1> , <o2> , <o3>
        // Find first two angle-bracket terms (subject, predicate) then split rest on commas.
        Matcher matcher = URI_ANGLE.matcher(statement);
        if (!matcher.find()) return Collections.emptyList();
        String subject = matcher.group();
        if (!matcher.find()) return Collections.emptyList();
        String predicate = matcher.group();
        int objectsStart = matcher.end();
        String objects = statement.substring(objectsStart).trim();
        List<String> triples = new ArrayList<>();
        for (String rawObject : objects.split(",")) {
            String object = rawObject.trim();
            if (object.isEmpty()) continue;
            triples.add(subject + " " + predicate + " " + object + " .");
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
        JsonNode canonical = canonicaliseNode(tree);
        return MAPPER.writeValueAsString(canonical);
    }

    public static String readJson(Path path) throws IOException {
        return canonicaliseGenericJson(Files.readString(path, StandardCharsets.UTF_8));
    }

    private static JsonNode canonicaliseNode(JsonNode node) {
        if (node.isObject()) {
            ObjectNode source = (ObjectNode) node;
            ObjectNode destination = MAPPER.createObjectNode();
            // Stable order: sort field names.
            Map<String, JsonNode> sorted = new TreeMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = source.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                sorted.put(entry.getKey(), canonicaliseNode(entry.getValue()));
            }
            for (Map.Entry<String, JsonNode> entry : sorted.entrySet()) {
                destination.set(entry.getKey(), entry.getValue());
            }
            return destination;
        }
        if (node.isArray()) {
            ArrayNode source = (ArrayNode) node;
            List<JsonNode> children = new ArrayList<>(source.size());
            for (JsonNode child : source) children.add(canonicaliseNode(child));
            children.sort((left, right) -> {
                try {
                    return MAPPER.writeValueAsString(left).compareTo(MAPPER.writeValueAsString(right));
                } catch (JsonProcessingException ex) {
                    throw new RuntimeException(ex);
                }
            });
            ArrayNode destination = MAPPER.createArrayNode();
            for (JsonNode child : children) destination.add(child);
            return destination;
        }
        return node;
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
        JsonNode canonical = canonicaliseNode(unwrapped);
        return MAPPER.writeValueAsString(canonical);
    }

    public static String readExplainedJson(Path path) throws IOException {
        return canonicaliseExplainedJson(Files.readString(path, StandardCharsets.UTF_8));
    }

    /** Recursively replace any text-valued `asserted_mappings` / `explanation` field with
     *  its parsed JSON tree, so nested premise structure is visible to the comparator. */
    private static JsonNode unwrapEmbeddedJsonStrings(JsonNode node) throws IOException {
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node.deepCopy();
            for (String field : new String[]{ASSERTED_MAPPINGS_FIELD, EXPLANATION_FIELD}) {
                JsonNode fieldValue = objectNode.get(field);
                if (fieldValue != null && fieldValue.isTextual()) {
                    objectNode.set(field, MAPPER.readTree(fieldValue.asText()));
                }
            }
            Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
            ObjectNode destination = MAPPER.createObjectNode();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                destination.set(entry.getKey(), unwrapEmbeddedJsonStrings(entry.getValue()));
            }
            return destination;
        }
        if (node.isArray()) {
            ArrayNode destination = MAPPER.createArrayNode();
            for (JsonNode child : node) {
                destination.add(unwrapEmbeddedJsonStrings(child));
            }
            return destination;
        }
        return node;
    }
}
