package uk.ac.ebi.spot.oxo.model.sssom;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.*;

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
public record MappingSet (
        @JsonProperty(COMMENT)
        Optional<String> comment,
        @JsonProperty(CREATOR_ID)
        SortedSet<EntityReference> creatorId,
        @JsonProperty(CREATOR_LABEL)
        SortedSet<String> creatorLabel,
        @JsonProperty(CURIE_MAP)
        CurieMap curieMap,
        @JsonProperty(ISSUE_TRACKER)
        Optional<Uri> issueTracker,
        @JsonProperty(LICENSE)
        Uri license,
        @JsonProperty(MAPPING_DATE)
        Optional<Date> mappingDate,
        @JsonProperty(MAPPING_PROVIDER)
        Optional<Uri> mappingProvider,
        @JsonIgnore
        SortedSet<Mapping> mappings,
        @JsonProperty(MAPPING_SET_DESCRIPTION)
        Optional<String> mappingSetDescription,
        @JsonProperty(MAPPING_SET_ID)
        Uri mappingSetId,
        @JsonProperty(MAPPING_SET_SOURCE)
        SortedSet<Uri> mappingSetSource,
        @JsonProperty(MAPPING_SET_TITLE)
        Optional<String> mappingSetTitle,
        @JsonProperty(MAPPING_SET_VERSION)
        Optional<String> mappingSetVersion,
        @JsonProperty(MAPPING_TOOL)
        Optional<String> mappingTool,
        @JsonProperty(MAPPING_TOOL_VERSION)
        Optional<String> mappingToolVersion,
        @JsonProperty(OBJECT_MATCH_FIELD)
        SortedSet<EntityReference> objectMatchField,
        @JsonProperty(OBJECT_PREPROCESSING)
        List<EntityReference> objectPreprocessing,
        @JsonProperty(OBJECT_SOURCE)
        Optional<EntityReference> objectSource,
        @JsonProperty(OBJECT_SOURCE_VERSION)
        Optional<String> objectSourceVersion,
        @JsonProperty(OBJECT_TYPE)
        Optional<EntityTypeEnum> objectType,
        @JsonProperty(OTHER)
        Optional<KeyValuePairsAsString> other,
        @JsonProperty(PUBLICATION_DATE)
        Optional<Date> publicationDate,
        @JsonProperty(SEE_ALSO)
        SortedSet<String> seeAlso,
        @JsonProperty(SUBJECT_MATCH_FIELD)
        SortedSet<EntityReference> subjectMatchField,
        @JsonProperty(SUBJECT_PREPROCESSING)
        List<EntityReference> subjectPreprocessing,
        @JsonProperty(SUBJECT_SOURCE)
        Optional<EntityReference> subjectSource,
        @JsonProperty(SUBJECT_SOURCE_VERSION)
        Optional<String> subjectSourceVersion,
        @JsonProperty(SUBJECT_TYPE)
        Optional<EntityTypeEnum> subjectType) {

    public static MappingSet.Builder builder() {
        return new MappingSet.Builder();
    }

//    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        private CurieMap curieMap = new CurieMap(new TreeMap<>());
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
        private Optional<KeyValuePairsAsString> other = Optional.empty();
        private Optional<String> comment = Optional.empty();

        public Builder curieMap(SortedMap<String, String> curieMap) {
            this.curieMap = new CurieMap(curieMap);
            return this;
        }

        public Builder curieMap(CurieMap curieMap) {
            this.curieMap = curieMap;
            return this;
        }

        public Builder mappings(SortedSet<Mapping> mappings) {
            this.mappings = mappings;
            return this;
        }

        public Builder mappingSetId(String mappingSetId) {
            this.mappingSetId = new Uri(mappingSetId);
            return this;
        }
        public Builder mappingSetId(Uri mappingSetId) {
            this.mappingSetId = mappingSetId;
            return this;
        }
        public Builder mappingSetVersion(String mappingSetVersion) {
            this.mappingSetVersion = Optional.of(mappingSetVersion);
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

        public Builder mappingSetTitle(String mappingSetTitle) {
            this.mappingSetTitle = Optional.of(mappingSetTitle);
            return this;
        }
        public Builder mappingSetTitle(Optional<String> mappingSetTitle) {
            this.mappingSetTitle = mappingSetTitle;
            return this;
        }

        public Builder mappingSetDescription(String mappingSetDescription) {
            this.mappingSetDescription = Optional.of(mappingSetDescription);
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

        public Builder license(String license) {
            this.license = new Uri(license);
            return this;
        }
        public Builder license(Uri license) {
            this.license = license;
            return this;
        }

        public Builder subjectType(EntityTypeEnum subjectType) {
            this.subjectType = Optional.of(subjectType);
            return this;
        }

        public Builder subjectType(Optional<EntityTypeEnum> subjectType) {
            this.subjectType = subjectType;
            return this;
        }

        public Builder subjectSource(String subjectSource) {
            this.subjectSource = Optional.of(new EntityReference(subjectSource));
            return this;
        }

        public Builder subjectSource(Optional<EntityReference> subjectSource) {
            this.subjectSource = subjectSource;
            return this;
        }

        public Builder subjectSourceVersion(String subjectSourceVersion) {
            this.subjectSourceVersion = Optional.of(subjectSourceVersion);
            return this;
        }

        public Builder subjectSourceVersion(Optional<String> subjectSourceVersion) {
            this.subjectSourceVersion = subjectSourceVersion;
            return this;
        }

        public Builder objectType(EntityTypeEnum objectType) {
            this.objectType = Optional.of(objectType);
            return this;
        }

        public Builder objectType(Optional<EntityTypeEnum> objectType) {
            this.objectType = objectType;
            return this;
        }

        public Builder objectSource(EntityReference objectSource) {
            this.objectSource = Optional.of(objectSource);
            return this;
        }
        public Builder objectSource(Optional<EntityReference> objectSource) {
            this.objectSource = objectSource;
            return this;
        }

        public Builder objectSourceVersion(String objectSourceVersion) {
            this.objectSourceVersion = Optional.of(objectSourceVersion);
            return this;
        }

        public Builder objectSourceVersion(Optional<String> objectSourceVersion) {
            this.objectSourceVersion = objectSourceVersion;
            return this;
        }

        public Builder mappingProvider(String mappingProvider) {
            this.mappingProvider = Optional.of(new Uri(mappingProvider));
            return this;
        }
        public Builder mappingProvider(Optional<Uri> mappingProvider) {
            this.mappingProvider = mappingProvider;
            return this;
        }

        public Builder mappingTool(String mappingTool) {
            this.mappingTool = Optional.of(mappingTool);
            return this;
        }

        public Builder mappingTool(Optional<String> mappingTool) {
            this.mappingTool = mappingTool;
            return this;
        }
        public Builder mappingToolVersion(String mappingToolVersion) {
            this.mappingToolVersion = Optional.of(mappingToolVersion);
            return this;
        }

        public Builder mappingToolVersion(Optional<String> mappingToolVersion) {
            this.mappingToolVersion = mappingToolVersion;
            return this;
        }

        public Builder mappingDate(String mappingDate) {
            this.mappingDate = Optional.of(new Date(mappingDate));
            return this;
        }

        public Builder mappingDate(Optional<Date> mappingDate) {
            this.mappingDate = mappingDate;
            return this;
        }

        public Builder publicationDate(String publicationDate) {
            this.publicationDate = Optional.of(new Date(publicationDate));
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

        public Builder issueTracker(String issueTracker) {
            this.issueTracker = Optional.of(new Uri(issueTracker));
            return this;
        }

        public Builder issueTracker(Optional<Uri> issueTracker) {
            this.issueTracker = issueTracker;
            return this;
        }


        public Builder other(Optional<KeyValuePairsAsString> other) {
            this.other = other;
            return this;
        }

        public Builder comment(String comment) {
            this.comment = Optional.of(comment);
            return this;
        }

        public Builder comment(Optional<String> comment) {
            this.comment = comment;
            return this;
        }


        public MappingSet build() {
            return new MappingSet(
                    comment,
                    creatorId,
                    creatorLabel,
                    curieMap,
                    issueTracker,
                    license,
                    mappingDate,
                    mappingProvider,
                    mappings,
                    mappingSetDescription,
                    mappingSetId,
                    mappingSetSource,
                    mappingSetTitle,
                    mappingSetVersion,
                    mappingTool,
                    mappingToolVersion,
                    objectMatchField,
                    objectPreprocessing,
                    objectSource,
                    objectSourceVersion,
                    objectType,
                    other,
                    publicationDate,
                    seeAlso,
                    subjectMatchField,
                    subjectPreprocessing,
                    subjectSource,
                    subjectSourceVersion,
                    subjectType
            );
        }
    }
}
