package uk.ac.ebi.spot.oxo.support;

import tools.jackson.core.JsonToken;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Initial asserted load: count sources across all input files, then index complete enriched docs.
 * No Solr reads or partial updates. Input JSON is preserved. Heap use is bounded by a document;
 * GNU sort spills the compact (group, source) pairs to disk, and counts use a mapped disk index.
 */
public class SupportedMappingsLoader {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    static final String COUNT = "asserted_source_count";

    @FunctionalInterface
    interface FileConsumer { void accept(Path file) throws Exception; }
    @FunctionalInterface
    private interface DocumentConsumer { void accept(ObjectNode document) throws Exception; }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: SupportedMappingsLoader INPUT_DIR SOLR_COLLECTION_URL");
        URI update = URI.create(args[1].replaceAll("/+$", "") + "/update?commit=true");
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build()) {
            load(Path.of(args[0]), file -> {
                HttpRequest request = HttpRequest.newBuilder(update).header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofFile(file)).build();
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IOException("Initial mapping load failed: HTTP " + response.statusCode() + " " + response.body());
                }
            });
        }
    }

    /** The consumer receives one complete JSON update array at a time, valid only during the call. */
    static void load(Path input, FileConsumer index) throws Exception {
        List<Path> inputs;
        try (var files = Files.walk(input)) {
            inputs = files.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
        // A sibling rather than a child: scratch files cannot become input on retries or recursive scans.
        Path scratch = Files.createTempDirectory(input.toAbsolutePath().getParent(), ".mapping-support-");
        try {
            Path pairs = scratch.resolve("pairs.tsv");
            try (var writer = Files.newBufferedWriter(pairs)) {
                for (Path file : inputs) readDocuments(file, doc -> {
                    UUID key = groupKey(doc);
                    if (!"ASSERTED".equals(doc.path("inference_type").asText())) {
                        throw new IOException("Expected asserted input in " + file);
                    }
                    String source = doc.path("mapping_set_id").asText("");
                    if (!source.isBlank()) {
                        // Base64 preserves exact source identity while escaping tabs/newlines.
                        writer.write(key + "\t" + Base64.getEncoder().encodeToString(source.getBytes(StandardCharsets.UTF_8)));
                        writer.newLine();
                    }
                });
            }
            Path sorted = scratch.resolve("unique-pairs.tsv");
            ProcessBuilder sorter = new ProcessBuilder("sort", "-u", "-S", "128M", "-T", scratch.toString(),
                    "-o", sorted.toString(), pairs.toString()).inheritIO();
            sorter.environment().put("LC_ALL", "C");
            Process process = sorter.start();
            try {
                if (process.waitFor() != 0) throw new IOException("Sorting asserted source pairs failed");
            } finally {
                if (process.isAlive()) process.destroyForcibly().waitFor();
            }
            Path counts = scratch.resolve("counts.bin");
            writeCounts(sorted, counts);
            Files.delete(pairs);
            Files.delete(sorted);
            try (DiskCounts lookup = new DiskCounts(counts)) {
                Path enriched = scratch.resolve("mappings.json");
                for (Path file : inputs) {
                    try (var output = Files.newOutputStream(enriched);
                         var generator = MAPPER.createGenerator(output)) {
                        generator.writeStartArray();
                        readDocuments(file, doc -> {
                            doc.put(COUNT, lookup.get(groupKey(doc)));
                            generator.writeTree(doc);
                        });
                        generator.writeEndArray();
                    }
                    index.accept(enriched);
                    Files.delete(enriched);
                    System.out.println("Indexed asserted mappings with support counts: " + file);
                }
            }
        } finally {
            try (var files = Files.walk(scratch)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
            }
        }
    }

    private static void readDocuments(Path file, DocumentConsumer consume) throws Exception {
        try (var parser = MAPPER.createParser(file.toFile())) {
            if (parser.nextToken() != JsonToken.START_ARRAY) throw new IOException("Expected JSON array in " + file);
            JsonToken token;
            while ((token = parser.nextToken()) != JsonToken.END_ARRAY) {
                if (token != JsonToken.START_OBJECT) throw new IOException("Expected mapping object in " + file);
                consume.accept((ObjectNode) parser.readValueAsTree());
            }
            if (parser.nextToken() != null) throw new IOException("Trailing JSON in " + file);
        }
    }

    private static UUID groupKey(ObjectNode doc) throws IOException {
        String value = doc.path("spo_key").asText("");
        try {
            UUID key = UUID.fromString(value);
            if (key.toString().equals(value)) return key;
        } catch (IllegalArgumentException ignored) { }
        throw new IOException("Missing or noncanonical spo_key on mapping " + doc.path("mapping_id").asText());
    }

    private static void writeCounts(Path sorted, Path output) throws IOException {
        try (var reader = Files.newBufferedReader(sorted);
             var writer = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(output)))) {
            String key = null;
            long count = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                String next = line.substring(0, line.indexOf('\t'));
                if (!next.equals(key)) {
                    if (key != null) writeCount(writer, key, count);
                    key = next;
                    count = 0;
                }
                count++;
            }
            if (key != null) writeCount(writer, key, count);
        }
    }

    private static void writeCount(DataOutputStream writer, String key, long count) throws IOException {
        UUID uuid = UUID.fromString(key);
        writer.writeLong(uuid.getMostSignificantBits());
        writer.writeLong(uuid.getLeastSignificantBits());
        writer.writeLong(count);
    }

    /** Fixed 24-byte records, sorted by unsigned UUID (the same order as canonical UUID text). */
    static final class DiskCounts implements AutoCloseable {
        private static final int RECORD_BYTES = 24;
        private static final int RECORDS_PER_SEGMENT = 1_000_000;
        private final FileChannel channel;
        private final List<MappedByteBuffer> segments = new ArrayList<>();
        private final long records;

        DiskCounts(Path path) throws IOException {
            channel = FileChannel.open(path, StandardOpenOption.READ);
            records = channel.size() / RECORD_BYTES;
            long segmentBytes = (long) RECORD_BYTES * RECORDS_PER_SEGMENT;
            for (long offset = 0; offset < channel.size(); offset += segmentBytes) {
                segments.add(channel.map(FileChannel.MapMode.READ_ONLY, offset, Math.min(segmentBytes, channel.size() - offset)));
            }
        }

        long get(UUID key) {
            long low = 0, high = records - 1;
            while (low <= high) {
                long middle = low + (high - low) / 2;
                var segment = segments.get((int) (middle / RECORDS_PER_SEGMENT));
                int offset = (int) (middle % RECORDS_PER_SEGMENT) * RECORD_BYTES;
                int order = Long.compareUnsigned(segment.getLong(offset), key.getMostSignificantBits());
                if (order == 0) order = Long.compareUnsigned(segment.getLong(offset + 8), key.getLeastSignificantBits());
                if (order == 0) return segment.getLong(offset + 16);
                if (order < 0) low = middle + 1; else high = middle - 1;
            }
            return 0;
        }

        @Override public void close() throws IOException { channel.close(); }
    }
}
