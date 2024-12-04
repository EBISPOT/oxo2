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

        @JsonProperty("ftp_server")
        private final Optional<String> ftpServer;

        @JsonProperty("port")
        private final Optional<Integer> port;

        private final Optional<String> url;

        public MappingRegistry(Builder builder) {
            this.id = builder.id;
            this.githubRepository = Optional.ofNullable(builder.githubRepository);
            this.directory = Optional.ofNullable(builder.directory);
            this.ftpServer = Optional.ofNullable(builder.ftpServer);
            this.port = Optional.ofNullable(builder.port);
            this.url = Optional.ofNullable(builder.url);
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

        public Optional<String> getFtpServer() {
            return ftpServer;
        }

        public Optional<Integer> getPort() {
            return port;
        }

        public Optional<String> getUrl() {
            return url;
        }

        @Override
        public String toString() {
            return "MappingRegistry{" +
                    "id='" + id + '\'' +
                    ", githubRepository=" + githubRepository +
                    ", directory=" + directory +
                    ", ftpServer=" + ftpServer +
                    ", port=" + port +
                    ", url=" + url +
                    '}';
        }

        public static class Builder {
            private String id;
            private String githubRepository;

            private String directory;

            private String ftpServer;

            private Integer port;

            private String url;

            public Builder(@JsonProperty("id") String id) {
                this.id = id;
                this.directory = "mappings";
                this.port = 21;
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

            @JsonProperty("ftp_server")
            public Builder setFtpServer(String ftpServer) {
                this.ftpServer = ftpServer;
                return this;
            }

            @JsonProperty("port")
            public Builder setPort(Integer port) {
                this.port = port;
                return this;
            }

            @JsonProperty("url")
            public Builder setUrl(String url) {
                this.url = url;
                return this;
            }

            public MappingRegistry build() {
                return new MappingRegistry(this);
            }
        }

    }

}
