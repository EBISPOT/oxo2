package uk.ac.ebi.spot.oxo.downloader.downloaders;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.spot.oxo.downloader.util.TgzExtractor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * Downloads a GitHub-hosted mapping registry by fetching the repository's default-branch archive
 * tarball over plain HTTP (no {@code api.github.com}, no token) and extracting only the configured
 * subdirectory. Fetching {@code github.com/{owner}/{repo}/archive/HEAD.tar.gz} avoids the
 * unauthenticated Contents API's 60 req/hour-per-IP rate limit, which is exhausted quickly on hosts
 * behind a shared NAT egress (e.g. EBI HPC). See ADR-0007.
 */
public class GitHubDownloader {
    private static final Logger logger = LoggerFactory.getLogger(GitHubDownloader.class);

    public static class DownloadGithubDirectoryTask implements Callable<Collection<Future>> {

        private final String githubRepository;
        private final String directory;
        private final String destination;

        public DownloadGithubDirectoryTask(String githubRepository, String directory, String destination) {
            logger.trace("Creating DownloadGithubDirectoryTask for {} to {}", githubRepository, destination);
            this.githubRepository = githubRepository;
            this.directory = directory;
            this.destination = destination;
        }

        @Override
        public Collection<Future> call() {
            logger.trace("DownloadGithubDirectoryTask downloading {} (directory {}) to {}",
                    githubRepository, directory, destination);
            Path tarball = null;
            try {
                GitHubUrlComponents components = parseGitHubUrl(githubRepository);
                String archiveUrl = "https://github.com/" + components.repoOwner + "/" +
                        components.repoName + "/archive/HEAD.tar.gz";

                Files.createDirectories(Paths.get(destination));
                tarball = Files.createTempFile("oxo-gh-" + components.repoName + "-", ".tar.gz");
                download(archiveUrl, tarball);
                TgzExtractor.extractSubdirectoryFlattened(tarball.toString(), destination, directory);
            } catch (IOException e) {
                logger.error("Error downloading files from GitHub repository = {} directory = {}",
                        githubRepository, directory, e);
            } finally {
                if (tarball != null) {
                    try {
                        Files.deleteIfExists(tarball);
                    } catch (IOException e) {
                        logger.debug("Could not delete temporary tarball {}", tarball, e);
                    }
                }
            }
            return Collections.emptyList();
        }

        private static void download(String archiveUrl, Path tarball) throws IOException {
            logger.debug("Downloading GitHub archive {}", archiveUrl);
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                HttpGet request = new HttpGet(archiveUrl);
                try (CloseableHttpResponse response = httpClient.execute(request)) {
                    int statusCode = response.getCode();
                    if (statusCode != 200) {
                        throw new IOException("GitHub archive download failed for " + archiveUrl +
                                " (status=" + statusCode + ")");
                    }
                    try (InputStream in = response.getEntity().getContent();
                         OutputStream out = Files.newOutputStream(tarball)) {
                        in.transferTo(out);
                    }
                }
            }
        }
    }

    private static GitHubUrlComponents parseGitHubUrl(String githubRepository) throws MalformedURLException {
        URL url = new URL(githubRepository);
        String[] pathSegments = url.getPath().split("/");
        String repoOwner = pathSegments[1];
        String repoName = pathSegments[2];
        return new GitHubUrlComponents(repoOwner, repoName);
    }

    private static class GitHubUrlComponents {
        private final String repoOwner;
        private final String repoName;

        public GitHubUrlComponents(String repoOwner, String repoName) {
            this.repoOwner = repoOwner;
            this.repoName = repoName;
        }
    }
}
