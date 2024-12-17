package uk.ac.ebi.spot.oxo.downloader.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.util.Optional;

import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
public class ConfigurationReader {
    private static final Logger logger = LoggerFactory.getLogger(ConfigurationReader.class);
    public static Optional<OxoConfiguration> read(String configurationFile) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new Jdk8Module());
        try {
            OxoConfiguration configuration = objectMapper.readValue(new File(configurationFile), OxoConfiguration.class);

            if (logger.isDebugEnabled())
                configuration.getMappingRegistries().forEach(registry ->
                    logger.debug("registry id: {}, purl: {}", registry.getId(), registry.getGithubRepository()));
            return Optional.of(configuration);
        } catch (IOException e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }
}