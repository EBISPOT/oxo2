package uk.ac.ebi.spot.oxo.inferences.nemo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.spot.oxo.inferences.nemo.model.NemoInferences;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Append-only, on-disk {@code conclusion -> (ruleName, premises)} store used to explain the
 * cross-set inference closure without holding it in heap (ADR-0018).
 *
 * <p>The cross-set chains file is the union of every set's inference chains (ADR-0016). On a real
 * corpus it reaches tens of GB, and the old path loaded it whole — a {@code NemoInferences} object
 * plus a second full {@code InferredMapping} DAG — so heap scaled with the closure and OOM'd. This
 * store breaks that: the record bytes live on disk; heap holds only an open-addressing
 * {@code hash64 -> file-offset} directory (~16 bytes per distinct conclusion).
 *
 * <p><b>Build:</b> stream every inference into {@link Builder#add}, which appends a length-prefixed
 * record and remembers its byte offset. <b>Read:</b> {@link #find} hashes the conclusion, probes
 * the directory, seeks the records file and <i>verifies the stored conclusion string</i> — so a
 * (vanishingly rare) 64-bit hash collision is resolved correctly, never silently.
 *
 * <p>Precondition: conclusions are unique across the input (the chains file is written
 * de-duplicated by conclusion), so this holds; a duplicate would simply leave two equivalent
 * records, of which {@link #find} returns one.
 *
 * <p>Not thread-safe; the explanation pass is single-threaded.
 */
public final class OnDiskChainStore implements InferenceLookup, Closeable {

    private static final Logger logger = LoggerFactory.getLogger(OnDiskChainStore.class);

    private static final long EMPTY = -1L;
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private static final float LOAD_FACTOR = 0.6f;
    private static final int DEFAULT_INITIAL_CAPACITY = 1 << 20;

    private final File recordsFile;
    private final RandomAccessFile reader;
    private final long[] hashes;
    private final long[] offsets;
    private final int mask;
    private final long size;

    private OnDiskChainStore(File recordsFile, long[] hashes, long[] offsets, int mask, long size)
            throws IOException {
        this.recordsFile = recordsFile;
        this.reader = new RandomAccessFile(recordsFile, "r");
        this.hashes = hashes;
        this.offsets = offsets;
        this.mask = mask;
        this.size = size;
    }

    /** Number of records indexed. */
    public long size() {
        return size;
    }

    @Override
    public Optional<NemoInferences.NemoInference> find(String conclusion) {
        long hash = hash64(conclusion);
        int index = ((int) (hash ^ (hash >>> 32))) & mask;
        while (offsets[index] != EMPTY) {
            if (hashes[index] == hash) {
                NemoInferences.NemoInference inference = readAt(offsets[index]);
                if (inference.getConclusion().equals(conclusion)) {
                    return Optional.of(inference);
                }
            }
            index = (index + 1) & mask;
        }
        return Optional.empty();
    }

    private NemoInferences.NemoInference readAt(long offset) {
        try {
            reader.seek(offset);
            int contentLength = reader.readInt();
            byte[] content = new byte[contentLength];
            reader.readFully(content);
            return decode(content);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed reading chain record at offset " + offset, e);
        }
    }

    private static NemoInferences.NemoInference decode(byte[] content) {
        ByteBuffer buffer = ByteBuffer.wrap(content);
        String conclusion = readString(buffer);
        String ruleName = buffer.get() == 1 ? readString(buffer) : null;
        int premiseCount = buffer.getInt();
        List<String> premises = new ArrayList<>(premiseCount);
        for (int premise = 0; premise < premiseCount; premise++) {
            premises.add(readString(buffer));
        }
        NemoInferences.NemoInference inference = new NemoInferences.NemoInference();
        inference.setConclusion(conclusion);
        inference.setRuleName(ruleName);
        inference.setPremises(premises);
        return inference;
    }

    private static String readString(ByteBuffer buffer) {
        int length = buffer.getInt();
        String value = new String(buffer.array(), buffer.position(), length, StandardCharsets.UTF_8);
        buffer.position(buffer.position() + length);
        return value;
    }

    /** Closes the reader and deletes the backing records file. */
    @Override
    public void close() throws IOException {
        try {
            reader.close();
        } finally {
            Files.deleteIfExists(recordsFile.toPath());
        }
    }

    /**
     * 64-bit FNV-1a over the conclusion's chars. A good 64-bit hash keeps the open-addressing
     * directory collision-free in practice; {@link #find} verifies the actual key on read, so even
     * a true collision is handled correctly.
     */
    static long hash64(String value) {
        long hash = FNV_OFFSET_BASIS;
        for (int i = 0; i < value.length(); i++) {
            hash ^= value.charAt(i);
            hash *= FNV_PRIME;
        }
        return hash;
    }

    /**
     * Builds an {@link OnDiskChainStore}: appends records to a temp file and grows an in-memory
     * {@code hash64 -> offset} directory. Call {@link #build()} exactly once when done; it flushes
     * the records file and hands ownership to the returned store (whose {@link #close()} deletes it).
     */
    public static final class Builder implements Closeable {

        private final File recordsFile;
        private final DataOutputStream out;
        private long bytesWritten;

        private long[] hashes;
        private long[] offsets;
        private int capacity;
        private int mask;
        private int threshold;
        private long size;
        private boolean built;

        public Builder(File recordsFile) throws IOException {
            this(recordsFile, DEFAULT_INITIAL_CAPACITY);
        }

        /**
         * @param expectedEntries a hint used to pre-size the directory and avoid repeated rehashing
         *                        on large inputs; rounded up to the next power of two.
         */
        public Builder(File recordsFile, int expectedEntries) throws IOException {
            this.recordsFile = recordsFile;
            this.out = new DataOutputStream(
                    new BufferedOutputStream(new FileOutputStream(recordsFile), 1 << 20));
            allocate(tableSizeFor(Math.max(expectedEntries, 16)));
        }

        public void add(String conclusion, String ruleName, List<String> premises) {
            if (built) {
                throw new IllegalStateException("Builder already built");
            }
            try {
                byte[] conclusionBytes = conclusion.getBytes(StandardCharsets.UTF_8);
                byte[] ruleNameBytes = ruleName == null ? null : ruleName.getBytes(StandardCharsets.UTF_8);
                List<byte[]> premiseBytes = new ArrayList<>(premises.size());
                int contentLength = 4 + conclusionBytes.length
                        + 1 + (ruleNameBytes == null ? 0 : 4 + ruleNameBytes.length)
                        + 4;
                for (String premise : premises) {
                    byte[] encoded = premise.getBytes(StandardCharsets.UTF_8);
                    premiseBytes.add(encoded);
                    contentLength += 4 + encoded.length;
                }

                long offset = bytesWritten;
                out.writeInt(contentLength);
                out.writeInt(conclusionBytes.length);
                out.write(conclusionBytes);
                if (ruleNameBytes == null) {
                    out.writeByte(0);
                } else {
                    out.writeByte(1);
                    out.writeInt(ruleNameBytes.length);
                    out.write(ruleNameBytes);
                }
                out.writeInt(premiseBytes.size());
                for (byte[] encoded : premiseBytes) {
                    out.writeInt(encoded.length);
                    out.write(encoded);
                }
                bytesWritten += 4L + contentLength;

                insert(hash64(conclusion), offset);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed writing chain record for " + conclusion, e);
            }
        }

        private void insert(long hash, long offset) {
            int index = ((int) (hash ^ (hash >>> 32))) & mask;
            while (offsets[index] != EMPTY) {
                index = (index + 1) & mask;
            }
            hashes[index] = hash;
            offsets[index] = offset;
            size++;
            if (size >= threshold) {
                grow();
            }
        }

        private void grow() {
            long[] oldHashes = hashes;
            long[] oldOffsets = offsets;
            allocate(capacity << 1);
            for (int i = 0; i < oldOffsets.length; i++) {
                if (oldOffsets[i] != EMPTY) {
                    long hash = oldHashes[i];
                    int index = ((int) (hash ^ (hash >>> 32))) & mask;
                    while (offsets[index] != EMPTY) {
                        index = (index + 1) & mask;
                    }
                    hashes[index] = hash;
                    offsets[index] = oldOffsets[i];
                }
            }
        }

        private void allocate(int newCapacity) {
            this.capacity = newCapacity;
            this.mask = newCapacity - 1;
            this.threshold = (int) (newCapacity * LOAD_FACTOR);
            this.hashes = new long[newCapacity];
            this.offsets = new long[newCapacity];
            Arrays.fill(this.offsets, EMPTY);
        }

        public OnDiskChainStore build() throws IOException {
            if (built) {
                throw new IllegalStateException("Builder already built");
            }
            built = true;
            out.flush();
            out.close();
            logger.info("On-disk chain store: {} records, {} bytes, directory capacity {}",
                    size, bytesWritten, capacity);
            return new OnDiskChainStore(recordsFile, hashes, offsets, mask, size);
        }

        @Override
        public void close() throws IOException {
            // Only meaningful if build() was never reached (e.g. an exception mid-stream): release
            // the writer and drop the half-written records file so it doesn't linger.
            if (!built) {
                out.close();
                Files.deleteIfExists(recordsFile.toPath());
            }
        }

        private static int tableSizeFor(int target) {
            int capacity = 1;
            while (capacity < target) {
                capacity <<= 1;
            }
            return capacity;
        }
    }
}
