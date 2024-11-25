package uk.ac.ebi.spot.oxo.model.sssom;

import java.util.*;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

/**
 * @see <a href="https://mapping-commons.github.io/sssom/MappingSet/">MappingSet</a>
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
@JsonDeserialize(builder = MappingSet.Builder.class)
public class MappingSet {

    @JsonProperty("curie_map")
    private final SortedMap<String, String> curieMap;

    @JsonProperty("mappings")
    private final SortedSet<Mapping> mappings;

    @JsonProperty("mapping_set_id")
    private final Uri mappingSetId;

    @JsonProperty("mapping_set_version")
    private final Optional<String> mappingSetVersion;

    @JsonProperty("mapping_set_source")
    private final SortedSet<Uri> mappingSetSource;

    @JsonProperty("mapping_set_title")
    private final Optional<String> mappingSetTitle;

    @JsonProperty("mapping_set_description")
    private final Optional<String> mappingSetDescription;

    @JsonProperty("creator_id")
    private final SortedSet<EntityReference> creatorId;

    @JsonProperty("creator_label")
    private final SortedSet<String> creatorLabel;

    @JsonProperty("license")
    private final Uri license;

    @JsonProperty("subject_type")
    private final Optional<EntityTypeEnum> subjectType;

    @JsonProperty("subject_source")
    private final Optional<EntityReference> subjectSource;


    @JsonProperty("subject_source_version")
    private final Optional<String> subjectSourceVersion;

    @JsonProperty("object_type")
    private final Optional<EntityTypeEnum> objectType;

    @JsonProperty("object_source")
    private final Optional<EntityReference> objectSource;


    @JsonProperty("object_source_version")
    private final Optional<String> objectSourceVersion;


    @JsonProperty("mapping_provider")
    private final Optional<Uri> mappingProvider;


    @JsonProperty("mapping_tool")
    private final Optional<String> mappingTool;

    @JsonProperty("mapping_tool_version")
    private final Optional<String> mappingToolVersion;

    @JsonProperty("mapping_date")
    private final Optional<Date> mappingDate;

    @JsonProperty("publication_date")
    private final Optional<Date> publicationDate;


    @JsonProperty("subject_match_field")
    private final SortedSet<EntityReference> subjectMatchField;

    @JsonProperty("object_match_field")
    private final SortedSet<EntityReference> objectMatchField;

    @JsonProperty("subject_preprocessing")
    private final List<EntityReference> subjectPreprocessing;


    @JsonProperty("object_preprocessing")
    private final List<EntityReference> objectPreprocessing;

    @JsonProperty("see_also")
    private final SortedSet<String> seeAlso;

    @JsonProperty("issue_tracker")
    private final Optional<Uri> issueTracker;

    @JsonProperty("other")
    private final Optional<String> other;


    @JsonProperty("comment")
    private final Optional<String> comment;

    @JsonProperty("extension_definitions")
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
        private SortedMap<String, String> curieMap = new TreeMap<>();
        private SortedSet<Mapping> mappings = new TreeSet<>();
        private Uri mappingSetId;
        private Optional<String> mappingSetVersion = Optional.empty();
        private SortedSet<Uri> mappingSetSource = new TreeSet<>();
        private Optional<String> mappingSetTitle = Optional.empty();
        private Optional<String> mappingSetDescription = Optional.empty();
        private SortedSet<EntityReference> creatorId = new TreeSet<>();
        private SortedSet<String> creatorLabel = new TreeSet<>();
        private Uri license;
        private Optional<EntityTypeEnum> subjectType = Optional.empty();
        private Optional<EntityReference> subjectSource = Optional.empty();
        private Optional<String> subjectSourceVersion = Optional.empty();
        private Optional<EntityTypeEnum> objectType = Optional.empty();
        private Optional<EntityReference> objectSource = Optional.empty();
        private Optional<String> objectSourceVersion = Optional.empty();
        private Optional<Uri> mappingProvider = Optional.empty();
        private Optional<String> mappingTool = Optional.empty();
        private Optional<String> mappingToolVersion = Optional.empty();
        private Optional<Date> mappingDate = Optional.empty();
        private Optional<Date> publicationDate = Optional.empty();
        private SortedSet<EntityReference> subjectMatchField = new TreeSet<>();
        private SortedSet<EntityReference> objectMatchField = new TreeSet<>();
        private List<EntityReference> subjectPreprocessing = new ArrayList<>();
        private List<EntityReference> objectPreprocessing = new ArrayList<>();
        private SortedSet<String> seeAlso = new TreeSet<>();
        private Optional<Uri> issueTracker = Optional.empty();
        private Optional<String> other = Optional.empty();
        private Optional<String> comment = Optional.empty();
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
    }
}
