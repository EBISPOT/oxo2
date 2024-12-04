package uk.ac.ebi.spot.oxo.model.sssom;

import java.util.*;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.*;

/**
 * @see <a href="https://mapping-commons.github.io/sssom/MappingSet/">MappingSet</a>
 *
 * A SSSOM TSV file contains 1 MappingSet object. See structure of TSV discussed
 * <a href="https://mapping-commons.github.io/sssom/spec-formats-tsv/#structure">here</a>.
 *
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
@JsonDeserialize(builder = MappingSet.Builder.class)
public class MappingSet {

    @JsonProperty(CURIE_MAP)
    private final SortedMap<String, String> curieMap;

    private final SortedSet<Mapping> mappings;

    @JsonProperty(MAPPING_SET_ID)
    private final Uri mappingSetId;

    @JsonProperty(MAPPING_SET_VERSION)
    private final Optional<String> mappingSetVersion;

    @JsonProperty(MAPPING_SET_SOURCE)
    private final SortedSet<Uri> mappingSetSource;

    @JsonProperty(MAPPING_SET_TITLE)
    private final Optional<String> mappingSetTitle;

    @JsonProperty(MAPPING_SET_DESCRIPTION)
    private final Optional<String> mappingSetDescription;

    @JsonProperty(CREATOR_ID)
    private final SortedSet<EntityReference> creatorId;

    @JsonProperty(CREATOR_LABEL)
    private final SortedSet<String> creatorLabel;

    @JsonProperty(LICENSE)
    private final Uri license;

    @JsonProperty(SUBJECT_TYPE)
    private final Optional<EntityTypeEnum> subjectType;

    @JsonProperty(SUBJECT_SOURCE)
    private final Optional<EntityReference> subjectSource;


    @JsonProperty(SUBJECT_SOURCE_VERSION)
    private final Optional<String> subjectSourceVersion;

    @JsonProperty(OBJECT_TYPE)
    private final Optional<EntityTypeEnum> objectType;

    @JsonProperty(OBJECT_SOURCE)
    private final Optional<EntityReference> objectSource;


    @JsonProperty(OBJECT_SOURCE_VERSION)
    private final Optional<String> objectSourceVersion;


    @JsonProperty(MAPPING_PROVIDER)
    private final Optional<Uri> mappingProvider;


    @JsonProperty(MAPPING_TOOL)
    private final Optional<String> mappingTool;

    @JsonProperty(MAPPING_TOOL_VERSION)
    private final Optional<String> mappingToolVersion;

    @JsonProperty(MAPPING_DATE)
    private final Optional<Date> mappingDate;

    @JsonProperty(PUBLICATION_DATE)
    private final Optional<Date> publicationDate;


    @JsonProperty(SUBJECT_MATCH_FIELD)
    private final SortedSet<EntityReference> subjectMatchField;

    @JsonProperty(OBJECT_MATCH_FIELD)
    private final SortedSet<EntityReference> objectMatchField;

    @JsonProperty(SUBJECT_PREPROCESSING)
    private final List<EntityReference> subjectPreprocessing;


    @JsonProperty(OBJECT_PREPROCESSING)
    private final List<EntityReference> objectPreprocessing;

    @JsonProperty(SEE_ALSO)
    private final SortedSet<String> seeAlso;

    @JsonProperty(ISSUE_TRACKER)
    private final Optional<Uri> issueTracker;

    @JsonProperty(OTHER)
    private final Optional<String> other;


    @JsonProperty(COMMENT)
    private final Optional<String> comment;

    @JsonProperty(EXTENSION_DEFINITIONS)
    private final SortedSet<ExtensionDefinition> extensionDefinitions;


    public SortedMap<String, String> getCurieMap() {
        return curieMap;
    }

    public SortedSet<Mapping> getMappings() {
        return mappings;
    }

    public Uri getMappingSetId() {
        return mappingSetId;
    }

    public Optional<String> getMappingSetVersion() {
        return mappingSetVersion;
    }

    public SortedSet<Uri> getMappingSetSource() {
        return mappingSetSource;
    }

    public Optional<String> getMappingSetTitle() {
        return mappingSetTitle;
    }

    public Optional<String> getMappingSetDescription() {
        return mappingSetDescription;
    }

    public SortedSet<EntityReference> getCreatorId() {
        return creatorId;
    }

    public SortedSet<String> getCreatorLabel() {
        return creatorLabel;
    }

    public Uri getLicense() {
        return license;
    }

    public Optional<EntityTypeEnum> getSubjectType() {
        return subjectType;
    }

    public Optional<EntityReference> getSubjectSource() {
        return subjectSource;
    }

    public Optional<String> getSubjectSourceVersion() {
        return subjectSourceVersion;
    }

    public Optional<EntityTypeEnum> getObjectType() {
        return objectType;
    }

    public Optional<EntityReference> getObjectSource() {
        return objectSource;
    }

    public Optional<String> getObjectSourceVersion() {
        return objectSourceVersion;
    }

    public Optional<Uri> getMappingProvider() {
        return mappingProvider;
    }

    public Optional<String> getMappingTool() {
        return mappingTool;
    }

    public Optional<String> getMappingToolVersion() {
        return mappingToolVersion;
    }

    public Optional<Date> getMappingDate() {
        return mappingDate;
    }

    public Optional<Date> getPublicationDate() {
        return publicationDate;
    }

    public SortedSet<EntityReference> getSubjectMatchField() {
        return subjectMatchField;
    }

    public SortedSet<EntityReference> getObjectMatchField() {
        return objectMatchField;
    }

    public List<EntityReference> getSubjectPreprocessing() {
        return subjectPreprocessing;
    }

    public List<EntityReference> getObjectPreprocessing() {
        return objectPreprocessing;
    }

    public SortedSet<String> getSeeAlso() {
        return seeAlso;
    }

    public Optional<Uri> getIssueTracker() {
        return issueTracker;
    }

    public Optional<String> getOther() {
        return other;
    }

    public Optional<String> getComment() {
        return comment;
    }

    public SortedSet<ExtensionDefinition> getExtensionDefinitions() {
        return extensionDefinitions;
    }

    public MappingSet(Builder builder) {
        this.curieMap = builder.curieMap;
        this.mappings = builder.mappings;
        this.mappingSetId = builder.mappingSetId;
        this.mappingSetVersion = builder.mappingSetVersion;
        this.mappingSetSource = builder.mappingSetSource;
        this.mappingSetTitle = builder.mappingSetTitle;
        this.mappingSetDescription = builder.mappingSetDescription;
        this.creatorId = builder.creatorId;
        this.creatorLabel = builder.creatorLabel;
        this.license = builder.license;
        this.subjectType = builder.subjectType;
        this.subjectSource = builder.subjectSource;
        this.subjectSourceVersion = builder.subjectSourceVersion;
        this.objectType = builder.objectType;
        this.objectSource = builder.objectSource;
        this.objectSourceVersion = builder.objectSourceVersion;
        this.mappingProvider = builder.mappingProvider;
        this.mappingTool = builder.mappingTool;
        this.mappingToolVersion = builder.mappingToolVersion;
        this.mappingDate = builder.mappingDate;
        this.publicationDate = builder.publicationDate;
        this.subjectMatchField = builder.subjectMatchField;
        this.objectMatchField = builder.objectMatchField;
        this.subjectPreprocessing = builder.subjectPreprocessing;
        this.objectPreprocessing = builder.objectPreprocessing;
        this.seeAlso = builder.seeAlso;
        this.issueTracker = builder.issueTracker;
        this.other = builder.other;
        this.comment = builder.comment;
        this.extensionDefinitions = builder.extensionDefinitions;
    }

    @JsonPOJOBuilder
    public static class Builder {
        @JsonProperty("curie_map")
        private SortedMap<String, String> curieMap = new TreeMap<>();

        @JsonProperty("mappings")
        private SortedSet<Mapping> mappings = new TreeSet<>();

        @JsonProperty("mapping_set_id")
        private Uri mappingSetId;

        @JsonProperty("mapping_set_version")
        private Optional<String> mappingSetVersion = Optional.empty();

        @JsonProperty("mapping_set_source")
        private SortedSet<Uri> mappingSetSource = new TreeSet<>();

        @JsonProperty("mapping_set_title")
        private Optional<String> mappingSetTitle = Optional.empty();

        @JsonProperty("mapping_set_description")
        private Optional<String> mappingSetDescription = Optional.empty();

        @JsonProperty("creator_id")
        private SortedSet<EntityReference> creatorId = new TreeSet<>();

        @JsonProperty("creator_label")
        private SortedSet<String> creatorLabel = new TreeSet<>();

        @JsonProperty("license")
        private Uri license;

        @JsonProperty("subject_type")
        private Optional<EntityTypeEnum> subjectType = Optional.empty();

        @JsonProperty("subject_source")
        private Optional<EntityReference> subjectSource = Optional.empty();

        @JsonProperty("subject_source_version")
        private Optional<String> subjectSourceVersion = Optional.empty();

        @JsonProperty("object_type")
        private Optional<EntityTypeEnum> objectType = Optional.empty();

        @JsonProperty("object_source")
        private Optional<EntityReference> objectSource = Optional.empty();

        @JsonProperty("object_source_version")
        private Optional<String> objectSourceVersion = Optional.empty();

        @JsonProperty("mapping_provider")
        private Optional<Uri> mappingProvider = Optional.empty();

        @JsonProperty("mapping_tool")
        private Optional<String> mappingTool = Optional.empty();

        @JsonProperty("mapping_tool_version")
        private Optional<String> mappingToolVersion = Optional.empty();

        @JsonProperty("mapping_date")
        private Optional<Date> mappingDate = Optional.empty();

        @JsonProperty("publication_date")
        private Optional<Date> publicationDate = Optional.empty();

        @JsonProperty("subject_match_field")
        private SortedSet<EntityReference> subjectMatchField = new TreeSet<>();

        @JsonProperty("object_match_field")
        private SortedSet<EntityReference> objectMatchField = new TreeSet<>();

        @JsonProperty("subject_preprocessing")
        private List<EntityReference> subjectPreprocessing = new ArrayList<>();

        @JsonProperty("object_preprocessing")
        private List<EntityReference> objectPreprocessing = new ArrayList<>();

        @JsonProperty("see_also")
        private SortedSet<String> seeAlso = new TreeSet<>();

        @JsonProperty("issue_tracker")
        private Optional<Uri> issueTracker = Optional.empty();

        @JsonProperty("other")
        private Optional<String> other = Optional.empty();

        @JsonProperty("comment")
        private Optional<String> comment = Optional.empty();

        @JsonProperty("extension_definitions")
        private SortedSet<ExtensionDefinition> extensionDefinitions = new TreeSet<>();

        public Builder curieMap(SortedMap<String, String> curieMap) {
            this.curieMap = curieMap;
            return this;
        }

        public Builder mappings(SortedSet<Mapping> mappings) {
            this.mappings = mappings;
            return this;
        }

        public Builder mappingSetId(Uri mappingSetId) {
            this.mappingSetId = mappingSetId;
            return this;
        }

        public Builder mappingSetVersion(Optional<String> mappingSetVersion) {
            this.mappingSetVersion = mappingSetVersion;
            return this;
        }

        public Builder mappingSetSource(SortedSet<Uri> mappingSetSource) {
            this.mappingSetSource = mappingSetSource;
            return this;
        }

        public Builder mappingSetTitle(Optional<String> mappingSetTitle) {
            this.mappingSetTitle = mappingSetTitle;
            return this;
        }

        public Builder mappingSetDescription(Optional<String> mappingSetDescription) {
            this.mappingSetDescription = mappingSetDescription;
            return this;
        }

        public Builder creatorId(SortedSet<EntityReference> creatorId) {
            this.creatorId = creatorId;
            return this;
        }

        public Builder creatorLabel(SortedSet<String> creatorLabel) {
            this.creatorLabel = creatorLabel;
            return this;
        }

        public Builder license(Uri license) {
            this.license = license;
            return this;
        }

        public Builder subjectType(Optional<EntityTypeEnum> subjectType) {
            this.subjectType = subjectType;
            return this;
        }

        public Builder subjectSource(Optional<EntityReference> subjectSource) {
            this.subjectSource = subjectSource;
            return this;
        }

        public Builder subjectSourceVersion(Optional<String> subjectSourceVersion) {
            this.subjectSourceVersion = subjectSourceVersion;
            return this;
        }

        public Builder objectType(Optional<EntityTypeEnum> objectType) {
            this.objectType = objectType;
            return this;
        }

        public Builder objectSource(Optional<EntityReference> objectSource) {
            this.objectSource = objectSource;
            return this;
        }

        public Builder objectSourceVersion(Optional<String> objectSourceVersion) {
            this.objectSourceVersion = objectSourceVersion;
            return this;
        }

        public Builder mappingProvider(Optional<Uri> mappingProvider) {
            this.mappingProvider = mappingProvider;
            return this;
        }

        public Builder mappingTool(Optional<String> mappingTool) {
            this.mappingTool = mappingTool;
            return this;
        }

        public Builder mappingToolVersion(Optional<String> mappingToolVersion) {
            this.mappingToolVersion = mappingToolVersion;
            return this;
        }

        public Builder mappingDate(Optional<Date> mappingDate) {
            this.mappingDate = mappingDate;
            return this;
        }

        public Builder publicationDate(Optional<Date> publicationDate) {
            this.publicationDate = publicationDate;
            return this;
        }

        public Builder subjectMatchField(SortedSet<EntityReference> subjectMatchField) {
            this.subjectMatchField = subjectMatchField;
            return this;
        }

        public Builder objectMatchField(SortedSet<EntityReference> objectMatchField) {
            this.objectMatchField = objectMatchField;
            return this;
        }

        public Builder subjectPreprocessing(List<EntityReference> subjectPreprocessing) {
            this.subjectPreprocessing = subjectPreprocessing;
            return this;
        }

        public Builder objectPreprocessing(List<EntityReference> objectPreprocessing) {
            this.objectPreprocessing = objectPreprocessing;
            return this;
        }

        public Builder seeAlso(SortedSet<String> seeAlso) {
            this.seeAlso = seeAlso;
            return this;
        }

        public Builder issueTracker(Optional<Uri> issueTracker) {
            this.issueTracker = issueTracker;
            return this;
        }

        public Builder other(Optional<String> other) {
            this.other = other;
            return this;
        }

        public Builder comment(Optional<String> comment) {
            this.comment = comment;
            return this;
        }

        public Builder extensionDefinitions(SortedSet<ExtensionDefinition> extensionDefinitions) {
            this.extensionDefinitions = extensionDefinitions;
            return this;
        }

        public MappingSet build() {
            return new MappingSet(this);
        }

        @Override
        public String toString() {
            return "Builder{" +
                    "curieMap=" + curieMap +
                    ", mappings=" + mappings +
                    ", mappingSetId=" + mappingSetId +
                    ", mappingSetVersion=" + mappingSetVersion +
                    ", mappingSetSource=" + mappingSetSource +
                    ", mappingSetTitle=" + mappingSetTitle +
                    ", mappingSetDescription=" + mappingSetDescription +
                    ", creatorId=" + creatorId +
                    ", creatorLabel=" + creatorLabel +
                    ", license=" + license +
                    ", subjectType=" + subjectType +
                    ", subjectSource=" + subjectSource +
                    ", subjectSourceVersion=" + subjectSourceVersion +
                    ", objectType=" + objectType +
                    ", objectSource=" + objectSource +
                    ", objectSourceVersion=" + objectSourceVersion +
                    ", mappingProvider=" + mappingProvider +
                    ", mappingTool=" + mappingTool +
                    ", mappingToolVersion=" + mappingToolVersion +
                    ", mappingDate=" + mappingDate +
                    ", publicationDate=" + publicationDate +
                    ", subjectMatchField=" + subjectMatchField +
                    ", objectMatchField=" + objectMatchField +
                    ", subjectPreprocessing=" + subjectPreprocessing +
                    ", objectPreprocessing=" + objectPreprocessing +
                    ", seeAlso=" + seeAlso +
                    ", issueTracker=" + issueTracker +
                    ", other=" + other +
                    ", comment=" + comment +
                    ", extensionDefinitions=" + extensionDefinitions +
                    '}';
        }
    }
}
