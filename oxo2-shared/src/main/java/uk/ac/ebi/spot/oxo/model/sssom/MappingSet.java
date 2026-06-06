package uk.ac.ebi.spot.oxo.model.sssom;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import uk.ac.ebi.spot.oxo.utils.StringUtils;

import java.util.*;

import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.*;

/**
 * @see <a href="https://mapping-commons.github.io/sssom/MappingSet/">MappingSet</a>
 *
 * A SSSOM TSV file contains 1 MappingSet object. See structure of TSV discussed
 * <a href="https://mapping-commons.github.io/sssom/spec-formats-tsv/#structure">here</a>.
 *
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonDeserialize(builder = MappingSet.Builder.class)
public record MappingSet (
        @JsonProperty(COMMENT)
        Optional<String> comment,
        @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = SSSOMDataType.FilterOut.class)
        @JsonProperty(CREATOR_ID)
        SortedSet<EntityReference> creatorId,
        @JsonProperty(CREATOR_LABEL)
        SortedSet<String> creatorLabel,
        @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = SSSOMDataType.FilterOut.class)
        @JsonProperty(CURIE_MAP)
        CurieMap curieMap,
        @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = SSSOMDataType.FilterOut.class)
        @JsonProperty(ISSUE_TRACKER)
        Optional<Uri> issueTracker,
        @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = SSSOMDataType.FilterOut.class)
        @JsonProperty(LICENSE)
        Uri license,
        @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = SSSOMDataType.FilterOut.class)
        @JsonProperty(MAPPING_DATE)
        Optional<Date> mappingDate,
        @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = SSSOMDataType.FilterOut.class)
        @JsonProperty(MAPPING_PROVIDER)
        Optional<Uri> mappingProvider,
        @JsonIgnore
        SortedSet<Mapping> mappings,
        @JsonProperty(MAPPING_SET_DESCRIPTION)
        Optional<String> mappingSetDescription,
        @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = SSSOMDataType.FilterOut.class)
        @JsonProperty(MAPPING_SET_ID)
        Uri mappingSetId,
        @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = SSSOMDataType.FilterOut.class)
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
        @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = SSSOMDataType.FilterOut.class)
        @JsonProperty(OBJECT_MATCH_FIELD)
        SortedSet<EntityReference> objectMatchField,
        @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = SSSOMDataType.FilterOut.class)
        @JsonProperty(OBJECT_PREPROCESSING)
        List<EntityReference> objectPreprocessing,
        @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = SSSOMDataType.FilterOut.class)
        @JsonProperty(OBJECT_SOURCE)
        Optional<EntityReference> objectSource,
        @JsonProperty(OBJECT_SOURCE_VERSION)
        Optional<String> objectSourceVersion,
        @JsonProperty(OBJECT_TYPE)
        Optional<EntityTypeEnum> objectType,
        @JsonProperty(OTHER)
        Optional<KeyValuePairsAsString> other,
        @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = SSSOMDataType.FilterOut.class)
        @JsonProperty(PUBLICATION_DATE)
        Optional<Date> publicationDate,
        @JsonProperty(SEE_ALSO)
        SortedSet<String> seeAlso,
        @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = SSSOMDataType.FilterOut.class)
        @JsonProperty(SUBJECT_MATCH_FIELD)
        SortedSet<EntityReference> subjectMatchField,
        @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = SSSOMDataType.FilterOut.class)
        @JsonProperty(SUBJECT_PREPROCESSING)
        List<EntityReference> subjectPreprocessing,
        @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = SSSOMDataType.FilterOut.class)
        @JsonProperty(SUBJECT_SOURCE)
        Optional<EntityReference> subjectSource,
        @JsonProperty(SUBJECT_SOURCE_VERSION)
        Optional<String> subjectSourceVersion,
        @JsonProperty(SUBJECT_TYPE)
        Optional<EntityTypeEnum> subjectType,
        @JsonProperty(INFERENCE_TYPE)
        InferenceType inferenceType) {

    public static MappingSet.Builder builder() {
        return new MappingSet.Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        private CurieMap curieMap = new CurieMap("");
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
        private InferenceType inferenceType = InferenceType.ASSERTED;
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

        @JsonProperty(CURIE_MAP)
        public Builder curieMap(SortedMap<String, String> curieMap) {
            this.curieMap = new CurieMap(CurieMap.convertMapToString(curieMap));
            return this;
        }

        public Builder curieMap(CurieMap curieMap) {
            this.curieMap = curieMap;
            return this;
        }

        public Builder curieMap(String curieMap){
            this.curieMap = new CurieMap(curieMap);
            return this;
        }

        public Builder mappings(SortedSet<Mapping> mappings) {
            this.mappings = mappings;
            return this;
        }

        @JsonProperty(MAPPING_SET_ID)
        public Builder mappingSetId(String mappingSetId) {
            this.mappingSetId = new Uri(mappingSetId);
            return this;
        }
        public Builder mappingSetId(Uri mappingSetId) {
            this.mappingSetId = mappingSetId;
            return this;
        }


        /**
         * MappingSetId is a required field, but may not be supplied. In these cases the mapping file name will be used as
         * MappingSetId.
         * Allows for mappingSetId to be derived from the name of the mapping file.
         * @param mappingSetFileName
         * @return
         */
        public Builder setMappingSetIdIfNotSetAlready(String mappingSetFileName) {
            if (this.mappingSetId == null) {
                this.mappingSetId = new Uri(mappingSetFileName);
            }
            return this;
        }


        @JsonProperty(MAPPING_SET_VERSION)
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

        @JsonProperty(MAPPING_SET_SOURCE)
        public Builder mappingSetSource(String mappingSetSource) {
            this.mappingSetSource = StringUtils.splitStringToSortedSet(mappingSetSource, "\\|", Uri::new);
            return this;
        }

        @JsonProperty(MAPPING_SET_TITLE)
        public Builder mappingSetTitle(String mappingSetTitle) {
            this.mappingSetTitle = Optional.of(mappingSetTitle);
            return this;
        }
        public Builder mappingSetTitle(Optional<String> mappingSetTitle) {
            this.mappingSetTitle = mappingSetTitle;
            return this;
        }

        @JsonProperty(MAPPING_SET_DESCRIPTION)
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

        @JsonProperty(CREATOR_ID)
        public Builder creatorId(Object creatorId) {
            if (creatorId instanceof String s) {
                this.creatorId = new TreeSet<>();
                this.creatorId.add(new EntityReference(s));
            } else if (creatorId instanceof Collection<?> c) {
                this.creatorId = new TreeSet<>();
                for (Object o : c) {
                    this.creatorId.add(new EntityReference(o.toString()));
                }
            }
            return this;
        }

        public Builder creatorLabel(SortedSet<String> creatorLabel) {
            this.creatorLabel = creatorLabel;
            return this;
        }

        @JsonProperty(CREATOR_LABEL)
        public Builder creatorLabel(String creatorLabel) {
            this.creatorLabel = StringUtils.splitStringToSortedSet(creatorLabel, "\\|", String::new);
            return this;
        }

        @JsonProperty(LICENSE)
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

        @JsonProperty(SUBJECT_TYPE)
        public Builder subjectType(String subjectType) {
            this.subjectType = EntityTypeEnum.fromString(subjectType);
            return this;
        }

        public Builder subjectType(Optional<EntityTypeEnum> subjectType) {
            this.subjectType = subjectType;
            return this;
        }

        @JsonProperty(SUBJECT_SOURCE)
        public Builder subjectSource(String subjectSource) {
            this.subjectSource = Optional.of(new EntityReference(subjectSource));
            return this;
        }

        public Builder subjectSource(Optional<EntityReference> subjectSource) {
            this.subjectSource = subjectSource;
            return this;
        }

        @JsonProperty(SUBJECT_SOURCE_VERSION)
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

        @JsonProperty(OBJECT_TYPE)
        public Builder objectType(String objectType) {
            this.objectType = EntityTypeEnum.fromString(objectType);
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

        @JsonProperty(OBJECT_SOURCE)
        public Builder objectSource(String objectSource) {
            this.objectSource = Optional.of(new EntityReference(objectSource));
            return this;
        }


        @JsonProperty(OBJECT_SOURCE_VERSION)
        public Builder objectSourceVersion(String objectSourceVersion) {
            this.objectSourceVersion = Optional.of(objectSourceVersion);
            return this;
        }

        public Builder objectSourceVersion(Optional<String> objectSourceVersion) {
            this.objectSourceVersion = objectSourceVersion;
            return this;
        }

        @JsonProperty(MAPPING_PROVIDER)
        public Builder mappingProvider(String mappingProvider) {
            this.mappingProvider = Optional.of(new Uri(mappingProvider));
            return this;
        }
        public Builder mappingProvider(Optional<Uri> mappingProvider) {
            this.mappingProvider = mappingProvider;
            return this;
        }

        @JsonProperty(MAPPING_TOOL)
        public Builder mappingTool(String mappingTool) {
            this.mappingTool = Optional.of(mappingTool);
            return this;
        }

        public Builder mappingTool(Optional<String> mappingTool) {
            this.mappingTool = mappingTool;
            return this;
        }

        @JsonProperty(MAPPING_TOOL_VERSION)
        public Builder mappingToolVersion(String mappingToolVersion) {
            this.mappingToolVersion = Optional.of(mappingToolVersion);
            return this;
        }

        public Builder mappingToolVersion(Optional<String> mappingToolVersion) {
            this.mappingToolVersion = mappingToolVersion;
            return this;
        }

        @JsonProperty(INFERENCE_TYPE)
        public Builder inferenceType(InferenceType inferenceType) {
            this.inferenceType = inferenceType;
            return this;
        }

        @JsonProperty(MAPPING_DATE)
        public Builder mappingDate(String mappingDate) {
            this.mappingDate = Optional.of(new Date(mappingDate));
            return this;
        }

        public Builder mappingDate(Optional<Date> mappingDate) {
            this.mappingDate = mappingDate;
            return this;
        }


        @JsonProperty(PUBLICATION_DATE)
        public Builder publicationDate(String publicationDate) {
            this.publicationDate = Optional.of(new Date(publicationDate));
            return this;
        }

        public Builder publicationDate(Optional<Date> publicationDate) {
            if (publicationDate != null && publicationDate.isPresent() && publicationDate.get().getDataRepresentation().isPresent()) {
                this.publicationDate = publicationDate;
            }
            return this;
        }

        public Builder subjectMatchField(SortedSet<EntityReference> subjectMatchField) {
            this.subjectMatchField = subjectMatchField;
            return this;
        }

        @JsonProperty(SUBJECT_MATCH_FIELD)
        public Builder subjectMatchField(String subjectMatchField) {
            this.subjectMatchField = StringUtils.splitStringToSortedSet(subjectMatchField, "\\|", EntityReference::new);
            return this;
        }


        public Builder objectMatchField(SortedSet<EntityReference> objectMatchField) {
            this.objectMatchField = objectMatchField;
            return this;
        }

        @JsonProperty(OBJECT_MATCH_FIELD)
        public Builder objectMatchField(String objectMatchField) {
            this.objectMatchField = StringUtils.splitStringToSortedSet(objectMatchField, "\\|", EntityReference::new);
            return this;
        }

        public Builder subjectPreprocessing(List<EntityReference> subjectPreprocessing) {
            this.subjectPreprocessing = subjectPreprocessing;
            return this;
        }

        @JsonProperty(SUBJECT_PREPROCESSING)
        public Builder subjectPreprocessing(String subjectPreprocessing) {
            this.subjectPreprocessing = StringUtils.splitStringToList(subjectPreprocessing, "\\|", EntityReference::new);
            return this;
        }

        public Builder objectPreprocessing(List<EntityReference> objectPreprocessing) {
            this.objectPreprocessing = objectPreprocessing;
            return this;
        }

        @JsonProperty(OBJECT_PREPROCESSING)
        public Builder objectPreprocessing(String objectPreprocessing) {
            this.objectPreprocessing = StringUtils.splitStringToList(objectPreprocessing, "\\|", EntityReference::new);
            return this;
        }

        public Builder seeAlso(SortedSet<String> seeAlso) {
            this.seeAlso = seeAlso;
            return this;
        }

        @JsonProperty(SEE_ALSO)
        public Builder seeAlso(String seeAlso) {
            this.seeAlso = StringUtils.splitStringToSortedSet(seeAlso, "\\|", String::new);
            return this;
        }

        @JsonProperty(ISSUE_TRACKER)
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

        @JsonProperty(OTHER)
        public Builder other(Map<String, String> other) {
            if (other != null && !other.isEmpty())
                this.other = Optional.of(new KeyValuePairsAsString(other));
            return this;
        }

        public Builder other(String other) {
            if (other != null && !other.isBlank())
                this.other = Optional.of(new KeyValuePairsAsString(other));
            return this;
        }

        @JsonProperty(COMMENT)
        public Builder comment(String comment) {
            if (comment != null && !comment.isBlank())
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
                    subjectType,
                    inferenceType
            );
        }
    }
}
