package uk.ac.ebi.spot.oxo.downloader.downloaders;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class GitHubFileDownloader {
    private static final Logger logger = LoggerFactory.getLogger(GitHubFileDownloader.class);
    private static final String GITHUB_API_URL = "https://api.github.com/repos/";

    public static class DownloadGithubDirectoryTask implements Callable<Collection<Future>> {

        private final ExecutorService executorService;
        private final String githubRepository;
        private final String directory;
        private final String destination;

        public DownloadGithubDirectoryTask(ExecutorService executorService, String gitbhubRepository,
                                           String directory, String destination) {
            logger.trace("Creating DownloadGithubDirectoryTask for {} to {}", gitbhubRepository, destination);
            this.executorService = executorService;
            this.githubRepository = gitbhubRepository;
            this.directory = directory;
            this.destination = destination;
        }

        @Override
        public Collection<Future> call() {
            logger.trace("DownloadGithubDirectoryTask run() downloading files from {} to {}",
                    githubRepository, destination);
            try {
                List<Map<String, Object>> files = fetchFileListToDownload(githubRepository, directory);
                return downloadFiles(executorService, files, destination);
            } catch (IOException | ParseException e) {
                logger.error("Error downloading files from GitHub repository = {} directory = {}",
                        githubRepository, directory, e);
                return Collections.emptyList();
            }
        }

        private static Collection<Future> downloadFiles(ExecutorService executorService, List<Map<String, Object>> files,
                                          String destination) throws IOException {
            logger.trace("Downloading {} files to {}", files.size(), destination);
            Collection<Future> futures = new LinkedList<>();

            Path dirPath = Paths.get(destination);
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }

            for (Map<String, Object> file : files) {
                String downloadUrl = (String) file.get("download_url");
                String fileName = dirPath.resolve((String) file.get("name")).toString();
                logger.debug("Creating task to download {} ...", fileName);
                Future future = executorService.submit(new DownloadGithubFileTask(downloadUrl, fileName));
                futures.add(future);
            }
            return futures;
        }
    }

    private static class DownloadGithubFileTask implements Runnable {
        private final String fileUrl;
        private final String destination;

        public DownloadGithubFileTask(String fileUrl, String destination) {
            logger.trace("Creating DownloadGithubFileTask for {} to {}", fileUrl, destination);
            this.fileUrl = fileUrl;
            this.destination = destination;
        }

        @Override
        public void run() {
            logger.trace("Downloading file from {} to {}", fileUrl, destination);
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                downloadFile(httpClient, fileUrl, destination);
            } catch (IOException e) {
                logger.error("Error downloading file from GitHub", e);
            }
        }

        private static void downloadFile(CloseableHttpClient httpClient, String fileUrl, String destination) throws IOException {
            HttpGet request = new HttpGet(fileUrl);

            try (CloseableHttpResponse response = httpClient.execute(request);
                 InputStream in = response.getEntity().getContent();
                 FileOutputStream out = new FileOutputStream(destination)) {
                byte[] buffer = new byte[2024];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    out.write(buffer, 0, count);
                }
            }
        }
    }

    private static List<Map<String, Object>> fetchFileListToDownload(String githubRepository,
                                                                     String directory)
            throws IOException, ParseException    {

        logger.trace("Fetching file list from GitHub for {}", githubRepository);

        GitHubUrlComponents components = parseGitHubUrl(githubRepository, directory);
        String apiUrl = GITHUB_API_URL + components.repoOwner + "/" + components.repoName +
                "/contents/" + components.directory;

        logger.debug("apiUrl = {}", apiUrl);


        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(apiUrl);
            request.addHeader("Accept", "application/vnd.github.v3+json");

            try (CloseableHttpResponse response = httpClient.execute(request)) {
                ObjectMapper mapper = new ObjectMapper();
                return mapper.readValue(EntityUtils.toString(response.getEntity()), List.class);
            }
        }
    }

    private static GitHubUrlComponents parseGitHubUrl(String githubReposity, String mappingDirectory)
            throws MalformedURLException {

        URL url = new URL(githubReposity);
        String[] pathSegments = url.getPath().split("/");
        String repoOwner = pathSegments[1];
        String repoName = pathSegments[2];

        String directory = "/" + mappingDirectory;

        return new GitHubUrlComponents(repoOwner, repoName, directory);
    }
    private static class GitHubUrlComponents {
        private final String repoOwner;
        private final String repoName;
        private final String directory;

        public GitHubUrlComponents(String repoOwner, String repoName, String directory) {
            this.repoOwner = repoOwner;
            this.repoName = repoName;
            this.directory = directory;
        }
    }

}