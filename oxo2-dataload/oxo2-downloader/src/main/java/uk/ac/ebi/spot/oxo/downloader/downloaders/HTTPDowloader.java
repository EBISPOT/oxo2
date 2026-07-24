package uk.ac.ebi.spot.oxo.downloader.downloaders;

import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.spot.oxo.downloader.util.TgzExtractor;

import java.io.*;
import java.util.Locale;
import java.util.Optional;

public class HTTPDowloader {

    private static final Logger logger = LoggerFactory.getLogger(HTTPDowloader.class);

    public static class HTTPDownloadTask implements Runnable {
        private final String url;
        private final String destination;

        private final Optional<String> extension;

        /**
         * Directory a relative local {@code url} is resolved against (the config file's directory in the
         * Nextflow flow — see ADR-0039). May be null: then a relative local path resolves against the JVM
         * working directory, the pre-ADR-0039 behaviour. Remote and absolute urls ignore it.
         */
        private final String baseDirectory;

        public HTTPDownloadTask(String url, String destination) {
            this(url, destination, null);
        }

        public HTTPDownloadTask(String url, String destination, String baseDirectory) {
            this.url = url;
            this.destination = destination;
            this.baseDirectory = baseDirectory;
            this.extension = getFileExtension(url);
        }

        @Override
        public void run() {
            String fileWithExtension = null;
            try {
                if (extension.isPresent()) {
                    fileWithExtension = destination + "." + extension.get();
                } else {
                    fileWithExtension = destination;
                }

                if (isRemote(url)) {
                    try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                        HttpGet request = new HttpGet(url);
                        downloadFile(httpClient, request, fileWithExtension);
                    }
                } else {
                    copyLocalFile(resolveLocalPath(url, baseDirectory), fileWithExtension);
                }

                String lowerUrl = url.toLowerCase(Locale.ROOT);
                if (lowerUrl.endsWith(".tgz") || lowerUrl.endsWith(".tar.gz")) {
                    TgzExtractor.extract(fileWithExtension, destination);
                } else if (lowerUrl.endsWith(".gz")) {
                    // Single-member gzip (e.g. a SSSOM `.tsv.gz`): decompress so the `.tsv` lands where the
                    // downstream sssom2json `**.tsv` glob can see it. The Mapping Commons registry downloader
                    // already gunzips its `.gz` entries; a plain-`url` `.gz` had been saved compressed and
                    // silently ignored. See ADR-0039.
                    File gzipFile = new File(fileWithExtension);
                    gunzip(gzipFile, new File(destination + "." + innerExtension(url)));
                    if (!gzipFile.delete()) {
                        logger.warn("Could not delete intermediate gzip file {}", gzipFile);
                    }
                }
            } catch (Exception e) {
                logger.error("Error downloading file from URL = {}, destination = {}, extension = {}, fileWithExtension = {}",
                        url, destination, extension, fileWithExtension, e);
            }
        }

        /** A network location the HTTP client fetches, as opposed to a local filesystem path. */
        private static boolean isRemote(String url) {
            String lower = url.toLowerCase(Locale.ROOT);
            return lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("ftp://");
        }

        /**
         * Filesystem path for a local {@code url}: the {@code file://} body or a bare path, resolved against
         * {@code baseDirectory} when relative (and a base is supplied). Absolute paths are returned verbatim.
         */
        private static String resolveLocalPath(String url, String baseDirectory) {
            String path = url.startsWith("file://") ? url.substring("file://".length()) : url;
            File file = new File(path);
            if (!file.isAbsolute() && baseDirectory != null) {
                file = new File(baseDirectory, path);
            }
            return file.getPath();
        }

        /** Extension of the payload inside a {@code .gz}, e.g. {@code priority.sssom.tsv.gz -> tsv}; defaults to tsv. */
        private static String innerExtension(String url) {
            String withoutGz = url.substring(0, url.length() - ".gz".length());
            int lastDot = withoutGz.lastIndexOf('.');
            int lastSeparator = Math.max(withoutGz.lastIndexOf('/'), withoutGz.lastIndexOf('\\'));
            if (lastDot <= lastSeparator) {
                return "tsv";
            }
            return withoutGz.substring(lastDot + 1);
        }

        private static void gunzip(File source, File target) throws IOException {
            try (InputStream in = new GzipCompressorInputStream(new FileInputStream(source));
                 OutputStream out = new FileOutputStream(target)) {
                byte[] buffer = new byte[2048];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    out.write(buffer, 0, count);
                }
            }
        }

        private static Optional<String> getFileExtension(String urlString) {
            int lastDotIndex = urlString.lastIndexOf('.');
            if (lastDotIndex == -1) {
                return Optional.empty();
            }
            return Optional.of(urlString.substring(lastDotIndex + 1));
        }

        private static void downloadFile(CloseableHttpClient httpClient, HttpGet request, String destination)
                throws IOException {

            try (CloseableHttpResponse response = httpClient.execute(request);
                 InputStream in = response.getEntity().getContent();
                 FileOutputStream out = new FileOutputStream(destination)) {

                byte[] buffer = new byte[2048];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    out.write(buffer, 0, count);
                }
            }
        }

        private static void copyLocalFile(String sourcePath, String destinationPath) throws IOException {
            try (InputStream in = new FileInputStream(sourcePath);
                 OutputStream out = new FileOutputStream(destinationPath)) {
                byte[] buffer = new byte[2048];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    out.write(buffer, 0, count);
                }
            }
        }
    }
}
