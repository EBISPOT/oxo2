package uk.ac.ebi.spot.oxo.downloader.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class OxoConfiguration {
    @JsonProperty("mapping_registries")
    private final List<MappingRegistry> mappingRegistries;

    /**
     * Confidence-gate threshold (ADR-0037): a mapping whose SSSOM {@code confidence} is present and
     * strictly below this value is kept out of the cross-set inference corpus (it is still indexed and
     * served as an asserted mapping). A <em>global</em> knob — one value for the whole load, not a
     * per-registry field — read by the {@code nquads} dataload stage. Absent → the gate is disabled.
     * Registered here so the strict config deserializer accepts the key; the dataload reads it from the
     * config JSON directly ({@code lib/InferenceConfidenceThreshold.groovy}).
     */
    @JsonProperty("min_inference_confidence")
    private final Optional<Double> minInferenceConfidence;

    public List<MappingRegistry> getMappingRegistries() {
        return mappingRegistries;
    }

    public Optional<Double> getMinInferenceConfidence() {
        return minInferenceConfidence;
    }

    @JsonCreator(mode=JsonCreator.Mode.PROPERTIES)
    public OxoConfiguration(
            @JsonProperty("mapping_registries") List<MappingRegistry> mappingRegistries,
            @JsonProperty("min_inference_confidence") Double minInferenceConfidence) {
        this.mappingRegistries = mappingRegistries;
        this.minInferenceConfidence = Optional.ofNullable(minInferenceConfidence);
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

        /**
         * URL of a Mapping Commons aggregated registry catalogue (the {@code mapping-specifications.json}
         * published by {@code mapping-commons.github.io}): a flat JSON array of mapping-set entries, each
         * carrying a {@code content_url} pointing at a SSSOM TSV. See ADR-0014.
         */
        @JsonProperty("mapping_commons_registry")
        private final Optional<String> mappingCommonsRegistry;

        /**
         * Filenames (the basename of each entry's {@code content_url}, e.g. {@code processed.sssom.tsv.gz})
         * to skip when consuming a {@code mapping_commons_registry}. Used to keep known closure-exploding
         * SeMRA assemblies out of the corpus while still ingesting the curated views (see ADR-0014).
         */
        @JsonProperty("exclude")
        private final List<String> exclude;

        /**
         * OxO curation category for this registry: {@code "ontology"} (an ontology's own asserted
         * cross-references — the OLS bulk export) or {@code "curated"} (EVORA, Mapping Commons, curated
         * GitHub repositories). This is <em>not</em> an SSSOM property — it is operator knowledge that
         * cannot be derived from the entry type ({@code url} is used by both the OLS bulk and EVORA).
         * It is stamped onto every mapping of the set during the dataload as {@code mapping_set_category}
         * (the codes match {@code MappingSetCategory}). Absent → the set is treated as curated.
         */
        @JsonProperty("category")
        private final Optional<String> category;

        /**
         * Obsolete-terms support (ADR-0041): when {@code true}, every subject of this registry is an
         * obsolete term. Like {@code category} this is operator knowledge, not an SSSOM property, and is
         * registered here only so the strict config deserializer accepts the key — the dataload reads it
         * in Groovy ({@code lib/ObsoleteRegistries.groovy}) and threads it into the SSSOM-to-JSON stage.
         * Absent → the registry's subjects are live.
         */
        @JsonProperty("obsolete")
        private final Optional<Boolean> obsolete;

        public MappingRegistry(Builder builder) {
            this.id = builder.id;
            this.githubRepository = Optional.ofNullable(builder.githubRepository);
            this.directory = Optional.ofNullable(builder.directory);
            this.ftpServer = Optional.ofNullable(builder.ftpServer);
            this.port = Optional.ofNullable(builder.port);
            this.url = Optional.ofNullable(builder.url);
            this.mappingCommonsRegistry = Optional.ofNullable(builder.mappingCommonsRegistry);
            this.exclude = builder.exclude == null
                    ? Collections.emptyList()
                    : List.copyOf(builder.exclude);
            this.category = Optional.ofNullable(builder.category);
            this.obsolete = Optional.ofNullable(builder.obsolete);
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

        public Optional<String> getMappingCommonsRegistry() {
            return mappingCommonsRegistry;
        }

        public List<String> getExclude() {
            return exclude;
        }

        public Optional<String> getCategory() {
            return category;
        }

        public Optional<Boolean> getObsolete() {
            return obsolete;
        }

        public String getPurl() {
            if (getUrl().isPresent()) {
                return getUrl().get();
            } else if (getFtpServer().isPresent()) {
                return "ftp://" + getFtpServer().get() + ":" + getPort().get() + "/" + getDirectory().get();
            } else if (getGithubRepository().isPresent()) {
                return "https://github.com/" + getGithubRepository().get();
            } else if (getMappingCommonsRegistry().isPresent()) {
                return getMappingCommonsRegistry().get();
            } else {
                throw new IllegalArgumentException("No URL or FTP server or GitHub repository or Mapping Commons " +
                        "registry found for registry " + id);
            }
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
                    ", mappingCommonsRegistry=" + mappingCommonsRegistry +
                    ", exclude=" + exclude +
                    ", category=" + category +
                    ", obsolete=" + obsolete +
                    '}';
        }

        public static class Builder {
            private String id;
            private String githubRepository;

            private String directory;

            private String ftpServer;

            private Integer port;

            private String url;

            private String mappingCommonsRegistry;

            private List<String> exclude;

            private String category;

            private Boolean obsolete;

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

            @JsonProperty("mapping_commons_registry")
            public Builder setMappingCommonsRegistry(String mappingCommonsRegistry) {
                this.mappingCommonsRegistry = mappingCommonsRegistry;
                return this;
            }

            @JsonProperty("exclude")
            public Builder setExclude(List<String> exclude) {
                this.exclude = exclude;
                return this;
            }

            @JsonProperty("category")
            public Builder setCategory(String category) {
                this.category = category;
                return this;
            }

            @JsonProperty("obsolete")
            public Builder setObsolete(Boolean obsolete) {
                this.obsolete = obsolete;
                return this;
            }

            public MappingRegistry build() {
                return new MappingRegistry(this);
            }
        }

    }

}
