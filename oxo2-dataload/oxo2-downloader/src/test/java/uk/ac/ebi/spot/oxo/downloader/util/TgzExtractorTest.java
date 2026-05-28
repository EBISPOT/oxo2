package uk.ac.ebi.spot.oxo.downloader.util;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TgzExtractorTest {

    // GitHub archives prefix every entry with "{repo}-{ref}/"; the ref here stands in for the sha.
    private static final String WRAPPER = "disease-mappings-main";

    @Test
    void keepsOnlyConfiguredSubdirectoryAndExcludesStrayFiles(@TempDir Path tmpDir) throws Exception {
        Path tgz = tmpDir.resolve("repo.tgz");
        Path destDir = tmpDir.resolve("dest");
        Files.createDirectories(destDir);

        writeTgz(tgz, tarOut -> {
            writeDirEntry(tarOut, WRAPPER + "/");
            writeDirEntry(tarOut, WRAPPER + "/mappings/");
            writeEntry(tarOut, WRAPPER + "/mappings/a.sssom.tsv", "a");
            writeEntry(tarOut, WRAPPER + "/mappings/b.sssom.tsv", "b");
            writeEntry(tarOut, WRAPPER + "/mappings.yml", "root-yaml");
            writeEntry(tarOut, WRAPPER + "/README.md", "readme");
            writeEntry(tarOut, WRAPPER + "/.github/workflows/ci.yml", "ci");
            writeEntry(tarOut, WRAPPER + "/src/test/fixture.tsv", "fixture");
        });

        TgzExtractor.extractSubdirectoryFlattened(tgz.toString(), destDir.toString(), "mappings");

        assertTrue(Files.exists(destDir.resolve("a.sssom.tsv")), "configured-dir file should be extracted");
        assertTrue(Files.exists(destDir.resolve("b.sssom.tsv")), "configured-dir file should be extracted");
        assertFalse(Files.exists(destDir.resolve("mappings.yml")), "root yaml must not leak");
        assertFalse(Files.exists(destDir.resolve("README.md")), "root readme must not leak");
        assertFalse(Files.exists(destDir.resolve("ci.yml")), "workflow yaml must not leak");
        assertFalse(Files.exists(destDir.resolve("fixture.tsv")), "test-dir tsv must not leak");
    }

    @Test
    void flattensNestedFilesUnderConfiguredSubdirectory(@TempDir Path tmpDir) throws Exception {
        Path tgz = tmpDir.resolve("repo.tgz");
        Path destDir = tmpDir.resolve("dest");
        Files.createDirectories(destDir);

        writeTgz(tgz, tarOut -> {
            writeDirEntry(tarOut, WRAPPER + "/mappings/sub/");
            writeEntry(tarOut, WRAPPER + "/mappings/sub/c.sssom.tsv", "c");
        });

        TgzExtractor.extractSubdirectoryFlattened(tgz.toString(), destDir.toString(), "mappings");

        assertTrue(Files.exists(destDir.resolve("c.sssom.tsv")), "nested file should be flattened to basename");
        assertFalse(Files.exists(destDir.resolve("sub")), "nested directories must not be recreated");
    }

    @Test
    void doesNotMatchSubdirectoryNameAsPrefixSubstring(@TempDir Path tmpDir) throws Exception {
        Path tgz = tmpDir.resolve("repo.tgz");
        Path destDir = tmpDir.resolve("dest");
        Files.createDirectories(destDir);

        writeTgz(tgz, tarOut -> {
            writeEntry(tarOut, WRAPPER + "/mappingsX/foo.tsv", "foo");
            writeEntry(tarOut, WRAPPER + "/mappings/keep.tsv", "keep");
        });

        TgzExtractor.extractSubdirectoryFlattened(tgz.toString(), destDir.toString(), "mappings");

        assertTrue(Files.exists(destDir.resolve("keep.tsv")));
        assertFalse(Files.exists(destDir.resolve("foo.tsv")), "a directory that only shares a prefix must not match");
    }

    @Test
    void extractsAlternateConfiguredSubdirectory(@TempDir Path tmpDir) throws Exception {
        // The phenotype registry overrides directory to "curation".
        Path tgz = tmpDir.resolve("repo.tgz");
        Path destDir = tmpDir.resolve("dest");
        Files.createDirectories(destDir);

        writeTgz(tgz, tarOut -> {
            writeEntry(tarOut, "phenotype-main/curation/xpo_hp_xenbase.sssom.tsv", "tsv");
            writeEntry(tarOut, "phenotype-main/mappings/other.tsv", "other");
        });

        TgzExtractor.extractSubdirectoryFlattened(tgz.toString(), destDir.toString(), "curation");

        assertTrue(Files.exists(destDir.resolve("xpo_hp_xenbase.sssom.tsv")));
        assertFalse(Files.exists(destDir.resolve("other.tsv")), "only the configured subdirectory is extracted");
    }

    @Test
    void keepsYamlMetadataInsideConfiguredSubdirectory(@TempDir Path tmpDir) throws Exception {
        // SSSOM external-metadata YAML that ships alongside the TSV inside the configured directory
        // must still be delivered (sssom2json consumes **.yml).
        Path tgz = tmpDir.resolve("repo.tgz");
        Path destDir = tmpDir.resolve("dest");
        Files.createDirectories(destDir);

        writeTgz(tgz, tarOut -> {
            writeEntry(tarOut, WRAPPER + "/mappings/x.sssom.tsv", "tsv");
            writeEntry(tarOut, WRAPPER + "/mappings/x.sssom.yml", "yaml");
        });

        TgzExtractor.extractSubdirectoryFlattened(tgz.toString(), destDir.toString(), "mappings");

        assertTrue(Files.exists(destDir.resolve("x.sssom.tsv")));
        assertTrue(Files.exists(destDir.resolve("x.sssom.yml")), "in-directory yaml metadata should be kept");
    }

    @Test
    void neutralisesTraversalAndSkipsUnsafeNamesWhenFlattening(@TempDir Path tmpDir) throws Exception {
        Path tgz = tmpDir.resolve("repo.tgz");
        Path destDir = tmpDir.resolve("dest");
        Files.createDirectories(destDir);

        String unsafeBase = "evil\"; touch pwned; #.tsv";
        writeTgz(tgz, tarOut -> {
            writeEntry(tarOut, WRAPPER + "/mappings/good.tsv", "good");
            // Flattening reduces this to "escape.tsv" inside destDir — the "../.." never escapes.
            writeEntry(tarOut, WRAPPER + "/mappings/../../escape.tsv", "escape");
            writeEntry(tarOut, WRAPPER + "/mappings/" + unsafeBase, "evil");
        });

        TgzExtractor.extractSubdirectoryFlattened(tgz.toString(), destDir.toString(), "mappings");

        assertTrue(Files.exists(destDir.resolve("good.tsv")));
        assertFalse(Files.exists(destDir.getParent().resolve("escape.tsv")),
                "flattened traversal entry must not escape the destination directory");
        assertFalse(Files.exists(destDir.resolve(unsafeBase)),
                "a basename with shell metacharacters must be skipped");
    }

    @FunctionalInterface
    private interface TarWriter {
        void write(TarArchiveOutputStream tarOut) throws Exception;
    }

    private static void writeTgz(Path tgz, TarWriter writer) throws Exception {
        try (FileOutputStream fileOut = new FileOutputStream(tgz.toFile());
             GzipCompressorOutputStream gzipOut = new GzipCompressorOutputStream(fileOut);
             TarArchiveOutputStream tarOut = new TarArchiveOutputStream(gzipOut)) {
            tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            writer.write(tarOut);
            tarOut.finish();
        }
    }

    private static void writeDirEntry(TarArchiveOutputStream tarOut, String name) throws Exception {
        TarArchiveEntry entry = new TarArchiveEntry(name);
        tarOut.putArchiveEntry(entry);
        tarOut.closeArchiveEntry();
    }

    private static void writeEntry(TarArchiveOutputStream tarOut, String name, String body) throws Exception {
        byte[] bytes = body.getBytes();
        TarArchiveEntry entry = new TarArchiveEntry(name);
        entry.setSize(bytes.length);
        tarOut.putArchiveEntry(entry);
        new ByteArrayInputStream(bytes).transferTo(tarOut);
        tarOut.closeArchiveEntry();
    }
}
