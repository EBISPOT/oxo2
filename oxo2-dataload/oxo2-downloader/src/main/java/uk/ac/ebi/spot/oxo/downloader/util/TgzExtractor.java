package uk.ac.ebi.spot.oxo.downloader.util;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Extracts gzipped-tar (.tgz / .tar.gz) archives. The traversal and command-injection guards
 * (per-segment {@link SafeFilename} validation plus a canonical-path containment check) live in the
 * single private core, so every extraction mode inherits them.
 */
public final class TgzExtractor {

    private static final Logger logger = LoggerFactory.getLogger(TgzExtractor.class);

    private TgzExtractor() {}

    /**
     * Extract every entry, preserving the archive's directory structure under {@code destDir}.
     */
    public static void extract(String tgzFilePath, String destDir) throws IOException {
        extractEntries(tgzFilePath, new File(destDir).getCanonicalFile(), TgzExtractor::preserveStructure);
    }

    /**
     * Extract only the files that lie under {@code subdirectory} once the archive's single
     * top-level wrapper segment (GitHub archives prefix every entry with {@code "{repo}-{ref}/"})
     * has been stripped, writing each kept file flat — by basename — into {@code destDir}.
     * Directory entries are skipped, so no nested directories are created.
     */
    public static void extractSubdirectoryFlattened(String tgzFilePath, String destDir,
                                                     String subdirectory) throws IOException {
        extractEntries(tgzFilePath, new File(destDir).getCanonicalFile(),
                entryName -> subdirectoryBasename(entryName, subdirectory));
    }

    /**
     * Shared core. {@code entryMapper} maps a raw tar entry name to the relative output path under
     * {@code destinationDir}, or {@link Optional#empty()} to skip the entry. The mapped name is
     * still validated with {@link #hasOnlySafeSegments} and confined by a canonical-path check, so
     * no mapper can write outside {@code destinationDir}.
     */
    private static void extractEntries(String tgzFilePath, File destinationDir,
                                       Function<String, Optional<String>> entryMapper) throws IOException {
        try (FileInputStream fileInput = new FileInputStream(tgzFilePath);
             GzipCompressorInputStream gzipInput = new GzipCompressorInputStream(fileInput);
             TarArchiveInputStream tarInput = new TarArchiveInputStream(gzipInput)) {

            ArchiveEntry entry;
            while ((entry = tarInput.getNextEntry()) != null) {
                Optional<String> mappedName = entryMapper.apply(entry.getName());
                if (mappedName.isEmpty()) {
                    continue;
                }
                String relativePath = mappedName.get();
                if (!hasOnlySafeSegments(relativePath)) {
                    logger.error("Skipping tar entry with unsafe name in {}: {}", tgzFilePath, entry.getName());
                    continue;
                }
                File outputFile = new File(destinationDir, relativePath).getCanonicalFile();
                if (!outputFile.toPath().startsWith(destinationDir.toPath())) {
                    throw new IOException("Archive entry outside destination directory: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    if (!outputFile.exists()) {
                        outputFile.mkdirs();
                    }
                } else {
                    File parent = outputFile.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    try (FileOutputStream fileOutput = new FileOutputStream(outputFile)) {
                        byte[] buffer = new byte[1024];
                        int count;
                        while ((count = tarInput.read(buffer)) != -1) {
                            fileOutput.write(buffer, 0, count);
                        }
                    }
                }
            }
        }
    }

    private static Optional<String> preserveStructure(String entryName) {
        if (entryName == null || entryName.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(entryName);
    }

    private static Optional<String> subdirectoryBasename(String entryName, String subdirectory) {
        if (entryName == null || entryName.endsWith("/")) {
            // Directory entries (and the wrapper itself) carry a trailing slash; we flatten to files.
            return Optional.empty();
        }
        List<String> segments = new ArrayList<>();
        for (String segment : entryName.split("/")) {
            if (!segment.isEmpty()) {
                segments.add(segment);
            }
        }
        // Need at least: wrapper segment + subdirectory + filename.
        if (segments.size() < 3) {
            return Optional.empty();
        }
        if (!segments.get(1).equals(subdirectory)) {
            return Optional.empty();
        }
        return Optional.of(segments.get(segments.size() - 1));
    }

    private static boolean hasOnlySafeSegments(String entryName) {
        if (entryName == null || entryName.isEmpty()) {
            return false;
        }
        String normalised = entryName.endsWith("/")
                ? entryName.substring(0, entryName.length() - 1)
                : entryName;
        if (normalised.isEmpty()) {
            return false;
        }
        for (String segment : normalised.split("/")) {
            if (segment.equals(".")) {
                // current-directory marker (e.g. GNU-tar's "./" prefix); no-op, not a traversal
                continue;
            }
            if (!SafeFilename.isSafe(segment)) {
                return false;
            }
        }
        return true;
    }
}
