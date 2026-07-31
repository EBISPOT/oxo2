package uk.ac.ebi.spot.oxo.downloader.config;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.File;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
public class ConfigurationReader {
    private static final Logger logger = LoggerFactory.getLogger(ConfigurationReader.class);
    public static Optional<OxoConfiguration> read(String configurationFile) {
        // Jackson 3 handles Optional natively, so the Jdk8Module this used to register is gone.
        // FAIL_ON_UNKNOWN_PROPERTIES must now be asked for explicitly: Jackson 2 enabled it by
        // default, Jackson 3 does not. Losing it would let a typo'd key (min_confidence_typo)
        // be silently ignored instead of rejecting the config — see ConfigurationReaderTest.
        JsonMapper objectMapper = JsonMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        try {
            OxoConfiguration configuration = objectMapper.readValue(new File(configurationFile), OxoConfiguration.class);

            if (logger.isDebugEnabled())
                configuration.getMappingRegistries().forEach(registry ->
                    logger.debug("registry id: {}, purl: {}", registry.getId(), registry.getGithubRepository()));
            return Optional.of(configuration);
        } catch (JacksonException e) {
            logger.error("Failed to read configuration file {}", configurationFile, e);
            return Optional.empty();
        }
    }
}