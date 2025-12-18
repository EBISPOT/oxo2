package uk.ac.ebi.spot.oxo.downloader;

import org.apache.commons.cli.*;
import uk.ac.ebi.spot.oxo.downloader.config.ConfigurationReader;
import uk.ac.ebi.spot.oxo.downloader.config.OxoConfiguration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.spot.oxo.downloader.downloaders.FTPDownloader;
import uk.ac.ebi.spot.oxo.downloader.downloaders.GitHubDownloader;
import uk.ac.ebi.spot.oxo.downloader.downloaders.HTTPDowloader;

import java.io.File;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Optional;
import java.util.concurrent.*;

public class DownloadMappings {

    private static final Logger logger = LoggerFactory.getLogger(DownloadMappings.class);

    public static void main(String[] args) {
        InputParameters inputParameters = new InputParameters(args);

        OxoConfiguration oxoConfiguration = getOxoConfiguration(inputParameters.configurationFile);
        createDownloadDirectory(inputParameters.downloadDirectory);

        ExecutorService executorService = Executors.newFixedThreadPool(inputParameters.numberOfThreads);
        Collection<Future> futures = new LinkedList<>();

        oxoConfiguration.getMappingRegistries().forEach(mappingRegistry -> {
            logger.debug("Downloading registry {} from {}", mappingRegistry.getId(), mappingRegistry.getPurl());
            try {
                futures.add(downloadMappings(executorService, mappingRegistry,
                        inputParameters.downloadDirectory + File.separator + mappingRegistry.getId()));
            } catch (IllegalArgumentException e) {
                logger.error("Failed to download registry {}: {}", mappingRegistry.getId(), e.getMessage());
            }
        });

        waitForFutures(futures);

        shutdownAndAwaitTermination(executorService);
    }

    private static void waitForFutures(Collection<Future> futures) {
        for (Future future : futures) {
            try {
                Object o = future.get();
                if (o instanceof Collection) {
                    waitForFutures((Collection<Future>) o);
                }
            } catch (InterruptedException | ExecutionException e) {
                logger.error("Error waiting for future to complete", e);
            }
        }
    }

    private static void shutdownAndAwaitTermination(ExecutorService executorService) {
        executorService.shutdown(); // Disable new tasks from being submitted
        try {      // Wait a while for existing tasks to terminate
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS))
            {
                executorService.shutdownNow(); // Cancel currently executing tasks
                // Wait a while for tasks to respond to being cancelled
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS))
                    System.err.println("Pool did not terminate");
            }
        } catch (InterruptedException ex) {
            // (Re-)Cancel if current thread also interrupted
            executorService.shutdownNow();
            // Preserve interrupt status
            Thread.currentThread().interrupt();

        }
    }

    private static Future downloadMappings(ExecutorService executorService,
                                           OxoConfiguration.MappingRegistry mappingRegistry,
                                           String downloadDirectory) {

        Future<?> future = null;


        if (mappingRegistry.getGithubRepository().isPresent()) {
            future = executorService.submit(new GitHubDownloader.DownloadGithubDirectoryTask(
                    executorService,
                    mappingRegistry.getGithubRepository().get(),
                    mappingRegistry.getDirectory().get(),
                    downloadDirectory));

        } else if (mappingRegistry.getUrl().isPresent()) {
            future = executorService.submit(new HTTPDowloader.HTTPDownloadTask(
                    mappingRegistry.getUrl().get(),
                    downloadDirectory));
        } else if (mappingRegistry.getFtpServer().isPresent()) {
            future = executorService.submit(new FTPDownloader.FTPDownloadTask(
                    executorService,
                    mappingRegistry.getFtpServer().get(),
                    mappingRegistry.getPort().get().intValue(),
                    mappingRegistry.getDirectory().get(),
                    downloadDirectory));
        } else
            throw new IllegalArgumentException("Unsupported download option: " + mappingRegistry.getId());

        return future;
    }

    private static void createDownloadDirectory(String downloadDirectory) {
        File file = new File(downloadDirectory);
        if (!file.exists()) {
            file.mkdirs();
        }
    }

    private static OxoConfiguration getOxoConfiguration(String configurationFile) {
        Optional<OxoConfiguration> oxoConfiguration;
        oxoConfiguration = ConfigurationReader.read(configurationFile);
        if (oxoConfiguration.isEmpty()) {
            logger.error("Error reading configuration file.");
            System.exit(1);
        }
        return oxoConfiguration.get();
    }

    private static Options getOptions() {
        Options options = new Options();

        Option configurationFile = new Option(null, Arguments.CONFIGURATION.argument, true,
        "JSON configuration file indicating from where SSSOM registries can be downloaded from.");
        configurationFile.setRequired(true);
        options.addOption(configurationFile);

        Option downloadDirectory = new Option (null, Arguments.DOWNLOAD_DIRECTORY.argument, true,
                "Directory to download the mappings to.");
        downloadDirectory.setRequired(true);
        options.addOption(downloadDirectory);


        Option numberOfThreads = new Option (null, Arguments.NUMBER_OF_THREADS.argument, true,
                "Number of threads to use for downloading the mappings.");
        numberOfThreads.setRequired(false);
        options.addOption(numberOfThreads);

        return options;
    }

    private enum Arguments {
        CONFIGURATION("config"),
        DOWNLOAD_DIRECTORY("download-dir"),
        NUMBER_OF_THREADS("threads");

        private final String argument;

        Arguments(String argument) {
            this.argument = argument;
        }
    }

    private static class InputParameters {
        private final String configurationFile;
        private final  String downloadDirectory;

        private final int numberOfThreads;

        public InputParameters(String [] args) {
            Options options = getOptions();

            CommandLineParser commandLineParser = new DefaultParser();
            HelpFormatter helpFormatter = new HelpFormatter();
            CommandLine commandLine = null;

            try {
                commandLine = commandLineParser.parse(options, args);
            } catch (ParseException e) {
                logger.error(e.getMessage());
                helpFormatter.printHelp("oxo2-downloader", options);

                System.exit(1);
            }
            configurationFile = commandLine.getOptionValue(Arguments.CONFIGURATION.argument);
            logger.debug("Configuration file: {}", configurationFile);
            downloadDirectory = commandLine.getOptionValue(Arguments.DOWNLOAD_DIRECTORY.argument);
            logger.debug("Download directory: {}", downloadDirectory);
            int tmpNumberOfThreads = 0;
            try {
                    tmpNumberOfThreads = Integer.parseInt(commandLine.getOptionValue(Arguments.NUMBER_OF_THREADS.argument));
                } catch (NumberFormatException e) {
                    tmpNumberOfThreads = Runtime.getRuntime().availableProcessors();
            }
            numberOfThreads = tmpNumberOfThreads;
            logger.debug("Number of threads: {}", numberOfThreads);

        }

    }

}
