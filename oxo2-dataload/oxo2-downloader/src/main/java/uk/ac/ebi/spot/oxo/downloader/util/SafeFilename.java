package uk.ac.ebi.spot.oxo.downloader.util;

import java.util.regex.Pattern;

/**
 * Allowlist for filenames sourced from external registries.
 * Remote-supplied names flow into both java.nio.file.Paths and into Bash
 * interpolation in the dataload Nextflow scripts; characters outside this
 * allowlist enable command injection and path traversal.
 */
public final class SafeFilename {

    public static final String PATTERN = "[A-Za-z0-9._-]+";
    private static final Pattern SAFE = Pattern.compile(PATTERN);
    private static final int MAX_LENGTH = 255;

    private SafeFilename() {}

    public static boolean isSafe(String name) {
        if (name == null || name.isEmpty() || name.length() > MAX_LENGTH) {
            return false;
        }
        if (name.equals(".") || name.equals("..")) {
            return false;
        }
        char first = name.charAt(0);
        if (first == '.' || first == '-') {
            return false;
        }
        return SAFE.matcher(name).matches();
    }
}
