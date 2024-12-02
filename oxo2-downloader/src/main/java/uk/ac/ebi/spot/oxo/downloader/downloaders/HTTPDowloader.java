package uk.ac.ebi.spot.oxo.downloader.downloaders;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Optional;

public class HTTPDowloader {

    private static final Logger logger = LoggerFactory.getLogger(HTTPDowloader.class);

    public static class HTTPDownloadTask implements Runnable {
        private final String url;
        private final String destination;

        private final Optional<String> extension;


        public HTTPDownloadTask(String url, String destination) {
            this.url = url;
            this.destination = destination;
            this.extension = getFileExtension(url);
        }

        @Override
        public void run() {
            String fileWithExtension = null;
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                HttpGet request = new HttpGet(url);


                if (extension.isPresent()) {
                    fileWithExtension = destination + "." + extension.get();
                } else {
                    fileWithExtension = destination;
                }
                downloadFile(httpClient, request, fileWithExtension);

                if (extension.isPresent() && extension.get().equals("tgz") || extension.get().equals("tar.gz")) {
                    extractTgz(fileWithExtension, destination);
                }
            } catch (Exception e) {
                logger.error("Error downloading file from URL = {}, destination = {}, extension = {}, fileWithExtension = {}",
                        url, destination, extension, fileWithExtension, e);
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

        private static void extractTgz(String tgzFilePath, String destDir) throws IOException {
            try (FileInputStream fis = new FileInputStream(tgzFilePath);
                 GzipCompressorInputStream gis = new GzipCompressorInputStream(fis);
                 TarArchiveInputStream tis = new TarArchiveInputStream(gis)) {

                ArchiveEntry entry;
                while ((entry = tis.getNextEntry()) != null) {
                    File outputFile = new File(destDir, entry.getName());
                    if (entry.isDirectory()) {
                        if (!outputFile.exists()) {
                            outputFile.mkdirs();
                        }
                    } else {
                        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                            byte[] buffer = new byte[1024];
                            int len;
                            while ((len = tis.read(buffer)) != -1) {
                                fos.write(buffer, 0, len);
                            }
                        }
                    }
                }
            }
        }

    }
}
