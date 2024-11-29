package uk.ac.ebi.spot.oxo.downloader.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.List;
import java.util.Optional;

public class OxoConfiguration {
    @JsonProperty("mapping_registries")
    private final List<MappingRegistry> mappingRegistries;

    public List<MappingRegistry> getMappingRegistries() {
        return mappingRegistries;
    }

    @JsonCreator(mode=JsonCreator.Mode.PROPERTIES)
    public OxoConfiguration(@JsonProperty("mapping_registries") List<MappingRegistry> mappingRegistries) {
        this.mappingRegistries = mappingRegistries;
    }

    @JsonDeserialize(builder = MappingRegistry.Builder.class)
    public static class MappingRegistry {
        @JsonProperty("id")
        private final String id;

        /**
         * In the case of a GitHub repository, this is the URL to the repository.
         * In the of an FTP server, this is the URL to the FTP file or directory.
         */
        @JsonProperty("github_repository")
        private final Optional<String> githubRepository;


        /**
         * In the case of a GitHub repository, this is the directory within the repository to download mappings from.
         */
        @JsonProperty("directory")
        private final Optional<String> directory;


        public MappingRegistry(Builder builder) {
            this.id = builder.id;
            this.githubRepository = Optional.ofNullable(builder.githubRepository);
            this.directory = Optional.ofNullable(builder.directory);
        }


        public String getId() {
            return id;
        }

        public Optional<String> getGithubRepository() {
            return githubRepository;
        }


        public Optional<String> getDirectory() {
            return directory;
        }

        @Override
        public String toString() {
            return "MappingRegistry{" +
                    "id='" + id + '\'' +
                    ", githubRepository=" + githubRepository +
                    ", directory=" + directory +
                    '}';
        }

        public static class Builder {
            private String id;
            private String githubRepository;

            private String directory;

            public Builder(@JsonProperty("id") String id) {
                this.id = id;
                this.directory = "mappings";
            }

            @JsonProperty("github_repository")
            public Builder setGithubRepository(String githubRepository) {
                this.githubRepository = githubRepository;
                return this;
            }

            @JsonProperty("directory")
            public Builder setDirectory(String directory) {
                this.directory = directory;
                return this;
            }

            public MappingRegistry build() {
                return new MappingRegistry(this);
            }
        }
    }
}
