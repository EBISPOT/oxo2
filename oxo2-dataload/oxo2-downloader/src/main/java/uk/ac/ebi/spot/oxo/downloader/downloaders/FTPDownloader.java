package uk.ac.ebi.spot.oxo.downloader.downloaders;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.spot.oxo.downloader.util.SafeFilename;

public class FTPDownloader {
    private static final Logger logger = LoggerFactory.getLogger(FTPDownloader.class);

    public static class FTPDownloadTask implements Callable<Collection<Future>> {

        private final ExecutorService executorService;
        private final String ftpServer;
        private final int port;
        private final String remoteDirectory;
        private final String destination;

        public FTPDownloadTask(ExecutorService executorService, String ftpServer, int port,
                                        String remoteDirectory, String destination) {
            logger.trace("Creating FTPDownloadTask for {} to {}", ftpServer, destination);
            this.executorService = executorService;
            this.ftpServer = ftpServer;
            this.port = port;
            this.remoteDirectory = remoteDirectory;
            this.destination = destination;
        }

        @Override
        public Collection<Future> call() {
            logger.trace("FTPDownloadTask call() downloading files from {} to {}", ftpServer, destination);

            FTPClient ftpClient = null;
            try {
                ftpClient = connect(ftpServer, port);
                String directory = ftpServer + "/" + remoteDirectory;

                if (isDirectory(ftpClient, directory)) {
                    List<String> files = Arrays.asList(ftpClient.listNames(directory));
                    return downloadFiles(executorService, ftpClient, files, directory, destination);
                } else if (isFile(ftpClient, directory)) {
                    return downloadFile(executorService, ftpClient, directory, destination);
                } else {
                    logger.error("Error downloading files from FTP server = {} directory = {}",
                            ftpServer, remoteDirectory);
                    return Collections.emptyList();
                }
            } catch (IOException e) {
                logger.error("Error downloading files from FTP server = {} directory = {}",
                        ftpServer, remoteDirectory, e);
            } finally {
                try {
                    if (ftpClient != null && ftpClient.isConnected()) {
                        ftpClient.logout();
                        ftpClient.disconnect();
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
            return Collections.emptyList();
        }

        private static Collection<Future> downloadFile(ExecutorService executorService, FTPClient ftpClient,
                                                       String remoteFile, String destination) throws IOException {
            logger.trace("Downloading file {} to {}", remoteFile, destination);
            Collection<Future> futures = new LinkedList<>();

            String remoteBase = Paths.get(remoteFile).getFileName().toString();
            if (!SafeFilename.isSafe(remoteBase)) {
                logger.error("Skipping FTP file with unsafe name from {}: {}", remoteFile, remoteBase);
                return Collections.emptyList();
            }
            String fileName = Paths.get(destination, remoteBase).toString();
            Future future = executorService.submit(new DownloadFTPFileTask(ftpClient, remoteFile, fileName));
            futures.add(future);

            return futures;
        }

        private static Collection<Future> downloadFiles(ExecutorService executorService, FTPClient ftpClient,
                                                        List<String> files, String remoteDirectory, String destination) throws IOException {

            logger.trace("Downloading {} files from {} to {}", files.size(), remoteDirectory, destination);
            Collection<Future> futures = new LinkedList<>();

            for (String file : files) {
                String fileBaseName = Paths.get(file).getFileName().toString();
                if (!SafeFilename.isSafe(fileBaseName)) {
                    logger.error("Skipping FTP file with unsafe name in {}: {}", remoteDirectory, file);
                    continue;
                }
                String remoteFilePath = remoteDirectory + "/" + file;
                String localFilePath = Paths.get(destination, fileBaseName).toString();
                Future future = executorService.submit(new DownloadFTPFileTask(ftpClient, remoteFilePath, localFilePath));
                futures.add(future);
            }

            return futures;
        }

        private static class DownloadFTPFileTask implements Runnable {
            private final FTPClient ftpClient;
            private final String remoteFile;
            private final String destination;

            public DownloadFTPFileTask(FTPClient ftpClient, String remoteFile, String destination) {
                logger.trace("Creating DownloadFTPFile for {} to {}", remoteFile, destination);
                this.ftpClient = ftpClient;
                this.remoteFile = remoteFile;
                this.destination = destination;
            }

            @Override
            public void run() {
                try (OutputStream outputStream = new FileOutputStream(destination)) {
                    boolean success = ftpClient.retrieveFile(remoteFile, outputStream);
                    if (success) {
                        logger.debug("File {} has been downloaded successfully.", destination);
                    } else {
                        logger.error("Failed to download file {}", destination);
                    }
                } catch (IOException e) {
                    logger.error("Error downloading file {}", destination, e);
                }
            }
        }

        public static FTPClient connect(String ftpServer, int port) throws IOException {
            FTPClient ftpClient = new FTPClient();

            ftpClient.connect(ftpServer, port);

            ftpClient.enterLocalPassiveMode();
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

            return ftpClient;
        }


        private static boolean isDirectory(FTPClient ftpClient, String path) throws IOException {
            // Try to change the working directory to the given path
            boolean isDirectory = ftpClient.changeWorkingDirectory(path);
            if (isDirectory) {
                // Change back to the parent directory if it is a directory
                ftpClient.changeToParentDirectory();
            }
            return isDirectory;
        }

        private static boolean isFile(FTPClient ftpClient, String path) throws IOException {
            // Try to retrieve the file stream
            try (InputStream inputStream = ftpClient.retrieveFileStream(path)) {
                if (inputStream != null) {
                    // Complete the pending command
                    ftpClient.completePendingCommand();
                    return true;
                }
            }
            return false;
        }
    }
}
