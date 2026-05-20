// Keep this regex in sync with SafeFilename.PATTERN in
// oxo2-downloader/src/main/java/uk/ac/ebi/spot/oxo/downloader/util/SafeFilename.java.
class FilenameGuard {
    static String assertSafe(String name) {
        if (!(name ==~ /[A-Za-z0-9._-]+/)
                || name.startsWith('.')
                || name.startsWith('-')
                || name.length() > 255) {
            throw new RuntimeException("Refusing unsafe filename: ${name}")
        }
        return name
    }
}
