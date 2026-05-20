package uk.ac.ebi.spot.oxo.downloader.downloaders;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HTTPDowloaderExtractTgzTest {

    @Test
    void extractTgzSkipsEntriesWithShellMetacharactersInName(@TempDir Path tmpDir) throws Exception {
        Path tgz = tmpDir.resolve("archive.tgz");
        Path destDir = tmpDir.resolve("dest");
        Files.createDirectories(destDir);

        String safeName = "good.tsv";
        String evilName = "evil\"; touch " + tmpDir.resolve("pwned").toString() + "; #.tsv";

        try (FileOutputStream fileOut = new FileOutputStream(tgz.toFile());
             GzipCompressorOutputStream gzipOut = new GzipCompressorOutputStream(fileOut);
             TarArchiveOutputStream tarOut = new TarArchiveOutputStream(gzipOut)) {
            tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            writeEntry(tarOut, safeName, "safe-payload");
            writeEntry(tarOut, evilName, "evil-payload");
            tarOut.finish();
        }

        invokeExtractTgz(tgz.toString(), destDir.toString());

        assertTrue(Files.exists(destDir.resolve(safeName)), "safe entry should be extracted");
        assertFalse(Files.exists(destDir.resolve(evilName)), "unsafe entry must not be written");
        assertFalse(Files.exists(tmpDir.resolve("pwned")), "no side-effect commands should run");
    }

    @Test
    void extractTgzSkipsEntriesWithUnsafePathSegments(@TempDir Path tmpDir) throws Exception {
        Path tgz = tmpDir.resolve("nested.tgz");
        Path destDir = tmpDir.resolve("dest");
        Files.createDirectories(destDir);

        try (FileOutputStream fileOut = new FileOutputStream(tgz.toFile());
             GzipCompressorOutputStream gzipOut = new GzipCompressorOutputStream(fileOut);
             TarArchiveOutputStream tarOut = new TarArchiveOutputStream(gzipOut)) {
            tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            writeEntry(tarOut, "sub/inner.tsv", "ok");
            writeEntry(tarOut, "sub/.hidden.tsv", "hidden");
            writeEntry(tarOut, "evil dir/inner.tsv", "bad");
            tarOut.finish();
        }

        invokeExtractTgz(tgz.toString(), destDir.toString());

        assertTrue(Files.exists(destDir.resolve("sub").resolve("inner.tsv")));
        assertFalse(Files.exists(destDir.resolve("sub").resolve(".hidden.tsv")));
        assertFalse(Files.exists(destDir.resolve("evil dir")));
    }

    private static void writeEntry(TarArchiveOutputStream tarOut, String name, String body) throws Exception {
        byte[] bytes = body.getBytes();
        TarArchiveEntry entry = new TarArchiveEntry(name);
        entry.setSize(bytes.length);
        tarOut.putArchiveEntry(entry);
        new ByteArrayInputStream(bytes).transferTo(tarOut);
        tarOut.closeArchiveEntry();
    }

    private static void invokeExtractTgz(String tgzFilePath, String destDir) throws Exception {
        Method method = HTTPDowloader.HTTPDownloadTask.class
                .getDeclaredMethod("extractTgz", String.class, String.class);
        method.setAccessible(true);
        method.invoke(null, tgzFilePath, destDir);
    }
}
