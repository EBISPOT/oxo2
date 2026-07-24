package uk.ac.ebi.spot.oxo.downloader.downloaders;

import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the local-{@code url} handling added in ADR-0039: a relative path resolved against a base
 * directory (so a committed test config can reference in-repo fixtures), and a single-member
 * {@code .gz} decompressed to {@code .tsv} so the downstream {@code **.tsv} glob can see it.
 */
class HTTPDowloaderLocalUrlTest {

    @Test
    void relativeUrlResolvesAgainstBaseDirectory(@TempDir Path tmpDir) throws Exception {
        Path checkoutRoot = tmpDir.resolve("checkout");
        Path fixtures = checkoutRoot.resolve("testcases/worktree");
        Files.createDirectories(fixtures);
        String payload = "subject_id\tpredicate_id\tobject_id\nEFO:1\tskos:exactMatch\tMONDO:1\n";
        Files.writeString(fixtures.resolve("efo.sssom.tsv"), payload);

        Path downloadDir = tmpDir.resolve("download");
        Files.createDirectories(downloadDir);
        String destination = downloadDir.resolve("efo").toString();

        new HTTPDowloader.HTTPDownloadTask("testcases/worktree/efo.sssom.tsv", destination,
                checkoutRoot.toString()).run();

        Path copied = Path.of(destination + ".tsv");
        assertTrue(Files.exists(copied), "relative url should be resolved against the base dir and copied");
        assertEquals(payload, Files.readString(copied));
    }

    @Test
    void singleMemberGzipIsDecompressedToTsv(@TempDir Path tmpDir) throws Exception {
        Path checkoutRoot = tmpDir.resolve("checkout");
        Path fixtures = checkoutRoot.resolve("mappings");
        Files.createDirectories(fixtures);
        byte[] payload = "subject_id\tobject_id\nX:1\tY:1\n".getBytes(StandardCharsets.UTF_8);
        Path gzip = fixtures.resolve("priority.sssom.tsv.gz");
        try (GzipCompressorOutputStream out = new GzipCompressorOutputStream(
                new FileOutputStream(gzip.toFile()))) {
            out.write(payload);
        }

        Path downloadDir = tmpDir.resolve("download");
        Files.createDirectories(downloadDir);
        String destination = downloadDir.resolve("priority").toString();

        new HTTPDowloader.HTTPDownloadTask("mappings/priority.sssom.tsv.gz", destination,
                checkoutRoot.toString()).run();

        Path decompressed = Path.of(destination + ".tsv");
        assertTrue(Files.exists(decompressed), "single-member .gz should be decompressed to .tsv");
        assertArrayEquals(payload, Files.readAllBytes(decompressed));
        assertFalse(Files.exists(Path.of(destination + ".gz")), "intermediate .gz should be removed");
    }

    @Test
    void absoluteFileUrlStillCopiedVerbatim(@TempDir Path tmpDir) throws Exception {
        Path source = tmpDir.resolve("mondo.sssom.tsv");
        String payload = "subject_id\tpredicate_id\tobject_id\nMONDO:1\tskos:exactMatch\tEFO:1\n";
        Files.writeString(source, payload);

        Path downloadDir = tmpDir.resolve("download");
        Files.createDirectories(downloadDir);
        String destination = downloadDir.resolve("mondo").toString();

        // No base dir: an absolute file:// url must resolve on its own (pre-ADR-0039 behaviour).
        new HTTPDowloader.HTTPDownloadTask("file://" + source, destination, null).run();

        Path copied = Path.of(destination + ".tsv");
        assertTrue(Files.exists(copied));
        assertEquals(payload, Files.readString(copied));
    }
}
