package uk.ac.ebi.spot.oxo.model.sssom;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import org.apache.solr.client.solrj.beans.Field;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.spot.oxo.utils.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.*;

/**
 * @see <a href="https://mapping-commons.github.io/sssom/Mapping/>Mapping</a>
 *
 *
 * <a href="https://mapping-commons.github.io/sssom/spec-model/#overview/>Overview</a> states that:
 * Of note, within a set, a mapping may not necessarily be uniquely identified by the combination
 * of its four mandatory slots (subject_id, predicate_id, object_id, and mapping_justification).
 * A set may very well contain several mappings with the same subject, predicate, object, and
 * justification, but that differ on some of the other, complementary slots.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonDeserialize(builder = Mapping.Builder.class)
public record Mapping (
        // SSSOM fields according to spec.
        @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = SSSOMDataType.FilterOut.class)
        @JsonProperty(AUTHOR_ID)
        SortedSet<EntityReference> authorId,
        @JsonProperty(AUTHOR_LABEL)
        SortedSet<String> authorLabel,
        @JsonProperty(COMMENT)
        Optional<String> comment,
        @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = SSSOMDataType.FilterOut.class)
        @JsonProperty(CONFIDENCE)
        Optional<Double> confidence,
        @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = SSSOMDataType.FilterOut.class)
        @JsonProperty(CREATOR_ID)
        SortedSet<EntityReference> creatorId,
        @JsonProperty(CREATOR_LABEL)
        SortedSet<String> creatorLabel,
        /**
         * An example of curation rules can be seen
         * <a href="https://github.com/mapping-commons/sssom/blob/master/examples/schema/curation_rule.sssom.tsv">here</a>.
         */
        @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = SSSOMDataType.FilterOut.class)
        @JsonProperty(CURATION_RULE)
        SortedSet<EntityReference> curationRule,
        @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = SSSOMDataType.FilterOut.class)
        @JsonProperty(ISSUE_TRACKER_ITEM)
        Optional<EntityReference> issueTrackerItem,
        @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = SSSOMDataType.FilterOut.class)
        @JsonProperty(LICENSE)
        Optional<Uri> license,
        @JsonProperty(MAPPING_CARDINALITY)
        Optional<MappingCardinalityEnum> mappingCardinality,
        @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = SSSOMDataType.FilterOut.class)
        @JsonProperty(MAPPING_DATE)
        Optional<Date> mappingDate,
        @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = SSSOMDataType.FilterOut.class)
        @JsonProperty(MAPPING_JUSTIFICATION)
        Optional<EntityReference> mappingJustification,
        @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = SSSOMDataType.FilterOut.class)
        @JsonProperty(MAPPING_PROVIDER)
        Optional<Uri> mappingProvider,
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
        @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = SSSOMDataType.FilterOut.class)
        @JsonProperty(MAPPING_SOURCE)
        Optional<EntityReference> mappingSource,
        @JsonProperty(MAPPING_TOOL)
        Optional<String> mappingTool,
        @JsonProperty(MAPPING_TOOL_VERSION)
        Optional<String> mappingToolVersion,
        @JsonProperty(MATCH_STRING)
        SortedSet<String> matchString,
        @JsonProperty(OBJECT_CATEGORY)
        Optional<String> objectCategory,
        @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = SSSOMDataType.FilterOut.class)
        @JsonProperty(OBJECT_ID)
        Optional<EntityReference> objectId,
        @JsonProperty(OBJECT_LABEL)
        Optional<String> objectLabel,
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
        @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = SSSOMDataType.FilterOut.class)
        @JsonProperty(OTHER)
        Optional<KeyValuePairsAsString> other,
        @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = SSSOMDataType.FilterOut.class)
        @JsonProperty(PREDICATE_ID)
        Optional<EntityReference> predicateId,
        @JsonProperty(PREDICATE_LABEL)
        Optional<String> predicateLabel,
        @JsonProperty(PREDICATE_MODIFIER)
        Optional<PredicateModifierEnum> predicateModifier,
        @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = SSSOMDataType.FilterOut.class)
        @JsonProperty(PUBLICATION_DATE)
        Optional<Date> publicationDate,
        @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = SSSOMDataType.FilterOut.class)
        @JsonProperty(REVIEWER_ID)
        SortedSet<EntityReference> reviewerId,
        @JsonProperty(REVIEWER_LABEL)
        SortedSet<String> reviewerLabel,
        @JsonProperty(SEE_ALSO)
        SortedSet<String> seeAlso,
        @JsonProperty(SIMILARITY_MEASURE)
        Optional<String> similarityMeasure,
        @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = SSSOMDataType.FilterOut.class)
        @JsonProperty(SIMILARITY_SCORE)
        Optional<Double> similarityScore,
        @JsonProperty(SUBJECT_CATEGORY)
        Optional<String> subjectCategory,
        @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = SSSOMDataType.FilterOut.class)
        @JsonProperty(SUBJECT_ID)
        Optional<EntityReference> subjectId,
        @JsonProperty(SUBJECT_LABEL)
        Optional<String> subjectLabel,
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


        // Additional fields for facets
        @JsonProperty(OBJECT_ID_PREFIX)
        Optional<String> objectIdPrefix,
        @JsonProperty(SUBJECT_ID_PREFIX)
        Optional<String> subjectIdPrefix,
        @JsonProperty(MAPPING_ID)
        UUID mappingId) implements Comparable<Mapping> {

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public int compareTo(Mapping o) {
        return this.mappingId.compareTo(o.mappingId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Mapping mapping = (Mapping) o;
        return mappingId == mapping.mappingId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mappingId);
    }


    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        private Uri mappingSetId;
        private Optional<String> mappingSetVersion = Optional.empty();
        private SortedSet<Uri> mappingSetSource = new TreeSet<>();
        private Optional<String> mappingSetTitle = Optional.empty();
        private Optional<String> mappingSetDescription = Optional.empty();
        private SortedSet<EntityReference> authorId = new TreeSet<>();
        private SortedSet<String> authorLabel = new TreeSet<>();
        private Optional<String> comment = Optional.empty();
        private Optional<Double> confidence = Optional.empty();
        private SortedSet<EntityReference> creatorId = new TreeSet<>();
        private SortedSet<String> creatorLabel = new TreeSet<>();
        private SortedSet<EntityReference> curationRule = new TreeSet<>();
        private Optional<EntityReference> issueTrackerItem = Optional.empty();
        private Optional<Uri> license = Optional.empty();
        private Optional<MappingCardinalityEnum> mappingCardinality = Optional.empty();
        private Optional<Date> mappingDate = Optional.empty();
        private Optional<EntityReference> mappingJustification = Optional.empty();
        private Optional<Uri> mappingProvider = Optional.empty();
        private Optional<EntityReference> mappingSource = Optional.empty();
        private Optional<String> mappingTool = Optional.empty();
        private Optional<String> mappingToolVersion = Optional.empty();
        private SortedSet<String> matchString = new TreeSet<>();
        private Optional<String> objectCategory = Optional.empty();
        private Optional<EntityReference> objectId = Optional.empty();
        private Optional<String> objectLabel = Optional.empty();
        private SortedSet<EntityReference> objectMatchField = new TreeSet<>();
        private List<EntityReference> objectPreprocessing = new ArrayList<>();
        private Optional<EntityReference> objectSource = Optional.empty();
        private Optional<String> objectSourceVersion = Optional.empty();
        private Optional<EntityTypeEnum> objectType = Optional.empty();
        private Optional<KeyValuePairsAsString> other = Optional.empty();
        private Optional<EntityReference> predicateId = Optional.empty();
        private Optional<String> predicateLabel = Optional.empty();
        private Optional<PredicateModifierEnum> predicateModifier = Optional.empty();
        private Optional<Date> publicationDate = Optional.empty();
        private SortedSet<EntityReference> reviewerId = new TreeSet<>();
        private SortedSet<String> reviewerLabel = new TreeSet<>();
        private SortedSet<String> seeAlso = new TreeSet<>();
        private Optional<String> similarityMeasure = Optional.empty();
        private Optional<Double> similarityScore = Optional.empty();
        private Optional<String> subjectCategory = Optional.empty();
        private Optional<EntityReference> subjectId = Optional.empty();
        private Optional<String> subjectLabel = Optional.empty();
        private SortedSet<EntityReference> subjectMatchField = new TreeSet<>();
        private List<EntityReference> subjectPreprocessing = new ArrayList<>();
        private Optional<EntityReference> subjectSource = Optional.empty();
        private Optional<String> subjectSourceVersion = Optional.empty();
        private Optional<EntityTypeEnum> subjectType = Optional.empty();


        private Optional<String> objectIdPrefix = Optional.empty();
        private Optional<String> subjectIdPrefix = Optional.empty();

        private UUID mappingId = UUID.randomUUID();

        private static Logger logger = LoggerFactory.getLogger(Builder.class);

        @Field(MAPPING_SET_ID)
        public Builder mappingSetId(String mappingSetId) {
            this.mappingSetId = new Uri(mappingSetId);
            return this;
        }

        public Builder mappingSetId(String mappingSetId, Optional<Uri> propagateMappingSetId) {
            Uri tempMappingSetId = new Uri(mappingSetId);
            if (tempMappingSetId.getDataRepresentation().isPresent())
                this.mappingSetId = tempMappingSetId;
            else if (propagateMappingSetId.isPresent())
                this.mappingSetId = propagateMappingSetId.get();

            return this;
        }

        @Field(MAPPING_SET_VERSION)
        public Builder mappingSetVersion(String mappingSetVersion) {
            this.mappingSetVersion = Optional.of(mappingSetVersion);
            return this;
        }

        public Builder mappingSetVersion(Optional<String> mappingSetVersion) {
            if (mappingSetVersion != null && mappingSetVersion.isPresent() && !mappingSetVersion.get().isBlank())
                this.mappingSetVersion = mappingSetVersion;
            return this;
        }

        public Builder mappingSetSource(SortedSet<Uri> mappingSetSource) {
            if (mappingSetSource != null && !mappingSetSource.isEmpty())
                this.mappingSetSource = mappingSetSource;
            return this;
        }

        @Field(MAPPING_SET_SOURCE)
        public Builder mappingSetSource (List<String> mappingSetSource) {
            if (mappingSetSource != null && !mappingSetSource.isEmpty())
                this.mappingSetSource = mappingSetSource.stream()
                        .map(Uri::new)
                        .collect(Collectors.toCollection(TreeSet::new));

            return this;
        }

        public Builder mappingSetSource(String mappingSetSource) {
            this.mappingSetSource = StringUtils.splitStringToSortedSet(mappingSetSource, "\\|", Uri::new);
            return this;
        }

        @Field(MAPPING_SET_TITLE)
        public Builder mappingSetTitle(String mappingSetTitle) {
            if (mappingSetTitle != null && !mappingSetTitle.isBlank())
                this.mappingSetTitle = Optional.of(mappingSetTitle);
            return this;
        }

        public Builder mappingSetTitle(Optional<String> mappingSetTitle) {
            if (mappingSetTitle != null && mappingSetTitle.isPresent() && !mappingSetTitle.get().isBlank())
                this.mappingSetTitle = mappingSetTitle;
            return this;

        }

        @Field(MAPPING_SET_DESCRIPTION)
        public Builder mappingSetDescription(String mappingSetDescription) {
            if (mappingSetDescription != null && !mappingSetDescription.isBlank())
                this.mappingSetDescription = Optional.of(mappingSetDescription);
            return this;
        }

        public Builder mappingSetDescription(Optional<String> mappingSetDescription) {
            if (mappingSetDescription != null && mappingSetDescription.isPresent() &&
                    !mappingSetDescription.get().isBlank())
                this.mappingSetDescription = mappingSetDescription;
            return this;
        }

        @Field(AUTHOR_ID)
        public Builder authorId(List<String> authorId) {
            if (authorId != null && !authorId.isEmpty())
                this.authorId = new TreeSet<>(authorId.stream()
                        .map(EntityReference::new)
                        .collect(Collectors.toList()));
            return this;
        }

        public Builder authorId(String authorId) {
            this.authorId = StringUtils.splitStringToSortedSet(authorId, "\\|", EntityReference::new);
            return this;
        }

        @Field(AUTHOR_LABEL)
        public Builder authorLabel(List<String> authorLabel) {
            if (authorLabel != null && !authorLabel.isEmpty())
                this.authorLabel = new TreeSet<>(authorLabel);
            return this;
        }

        public Builder authorLabel(String authorLabel) {
            this.authorLabel = StringUtils.splitStringToSortedSet(authorLabel, "\\|", String::new);
            return this;
        }

        @Field(COMMENT)
        public Builder comment(String comment) {
            if (comment != null && !comment.isBlank())
                this.comment = Optional.of(comment);
            return this;
        }

        @Field(CONFIDENCE)
        public Builder confidence(java.lang.Double confidence) {
            this.confidence = Optional.of(Double.of(confidence));
            return this;
        }

        public Builder confidence(String confidence) {
            this.confidence = Optional.of(new Double(confidence));
            return this;
        }

        public Builder creatorId(String creatorId) {
            this.creatorId = StringUtils.splitStringToSortedSet(creatorId, "\\|", EntityReference::new);
            return this;
        }


        @Field(CREATOR_ID)
        public Builder creatorId(List<String> creatorId) {
            if (creatorId != null && !creatorId.isEmpty())
                this.creatorId = new TreeSet<>(creatorId.stream()
                        .map(EntityReference::new)
                        .collect(Collectors.toList()));
            return this;
        }

        @Field(CREATOR_LABEL)
        public Builder creatorLabel(List<String> creatorLabel) {
            if (creatorLabel != null && !creatorLabel.isEmpty())
                this.creatorLabel = new TreeSet<>(creatorLabel);
            return this;
        }

        public Builder creatorLabel(String creatorLabel) {
            this.creatorLabel = StringUtils.splitStringToSortedSet(creatorLabel, "\\|", String::new);
            return this;
        }


        @Field(CURATION_RULE)
        public Builder curationRule(List<String> curationRule) {
            if (curationRule != null && !curationRule.isEmpty())
                this.curationRule = new TreeSet<>(curationRule.stream()
                        .map(EntityReference::new)
                        .collect(Collectors.toList()));
            return this;
        }

        public Builder curationRule(String curationRule) {
            this.curationRule = StringUtils.splitStringToSortedSet(curationRule, "\\|", EntityReference::new);
            return this;
        }

        @Field(ISSUE_TRACKER_ITEM)
        public Builder issueTrackerItem(String issueTrackerItem) {
            if (issueTrackerItem != null && !issueTrackerItem.isBlank())
                this.issueTrackerItem = Optional.of(new EntityReference(issueTrackerItem));
            return this;
        }

        @Field(LICENSE)
        public Builder license(String license) {
            if (license != null && !license.isBlank())
                this.license = Optional.of(new Uri(license));
            return this;
        }

        @Field(MAPPING_CARDINALITY)
        public Builder mappingCardinality(String mappingCardinality) {
            this.mappingCardinality = MappingCardinalityEnum.fromString(mappingCardinality);
            return this;
        }

        @Field(MAPPING_DATE)
        public Builder mappingDate(java.util.Date mappingDate) {
            if (mappingDate != null)
                this.mappingDate = Optional.of(Date.of(mappingDate));
            return this;
        }

        public Builder mappingDate(String mappingDate) {
            if (mappingDate != null && !mappingDate.isBlank())
                this.mappingDate = Optional.of(new Date(mappingDate));
            return this;
        }

        public Builder mappingDate(String mappingDate, Optional<Date>propagateDate) {
            Date tempMappingDate = new Date(mappingDate);
            if (tempMappingDate.dataRepresentation.isPresent())
                this.mappingDate = Optional.of(tempMappingDate);
            else if (propagateDate.isPresent())
                this.mappingDate = propagateDate;

            return this;
        }

        @Field(MAPPING_JUSTIFICATION)
        public Builder mappingJustification(String mappingJustification) {
            if (mappingJustification != null && !mappingJustification.isBlank())
                this.mappingJustification = Optional.of(new EntityReference(mappingJustification));
            return this;
        }

        public Builder mappingProvider(String mappingProvider, Optional<Uri> propagateProvider) {
            Uri tempMappingProvider = new Uri(mappingProvider);
            if (tempMappingProvider.getDataRepresentation().isPresent())
                this.mappingProvider = Optional.of(tempMappingProvider);
            else if (propagateProvider.isPresent())
                this.mappingProvider = propagateProvider;

            return this;
        }

        @Field(MAPPING_PROVIDER)
        public Builder mappingProvider(String mappingProvider) {
            this.mappingProvider = Optional.of(new Uri(mappingProvider));
            return this;
        }

        @Field(MAPPING_SOURCE)
        public Builder mappingSource(String mappingSource) {
            this.mappingSource = Optional.of(new EntityReference(mappingSource));
            return this;
        }

        public Builder mappingTool(String mappingTool, Optional<String> propagateMappingTool) {
            if (mappingTool != null && !mappingTool.isBlank())
                this.mappingTool = Optional.of(mappingTool);
            else if (propagateMappingTool.isPresent())
                this.mappingTool = propagateMappingTool;

            return this;
        }

        @Field(MAPPING_TOOL)
        public Builder mappingTool(String mappingTool) {
            if (mappingTool != null && !mappingTool.isBlank())
                this.mappingTool = Optional.of(mappingTool);
            return this;
        }

        public Builder mappingToolVersion(String mappingToolVersion, Optional<String> propagateMappingToolVersion) {
            if (mappingToolVersion != null && !mappingToolVersion.isBlank())
                this.mappingToolVersion = Optional.of(mappingToolVersion);
            else if (propagateMappingToolVersion.isPresent())
                this.mappingToolVersion = propagateMappingToolVersion;
            return this;
        }

        @Field(MAPPING_TOOL_VERSION)
        public Builder mappingToolVersion(String mappingToolVersion) {
            if (mappingToolVersion != null && !mappingToolVersion.isBlank())
                this.mappingToolVersion = Optional.of(mappingToolVersion);
            return this;
        }

        @Field(MATCH_STRING)
        public Builder matchString(List<String> matchString) {
            if (matchString != null)
                this.matchString = new TreeSet<>(matchString);
            return this;
        }
        public Builder matchString(String matchString) {
            if (matchString != null && !matchString.isBlank())
                this.matchString = StringUtils.splitStringToSortedSet(matchString, "\\|", String::new);
            return this;
        }

        @Field(OBJECT_CATEGORY)
        public Builder objectCategory(String objectCategory) {
            if (objectCategory != null && !objectCategory.isBlank())
                this.objectCategory = Optional.of(objectCategory);
            return this;
        }

        @Field(OBJECT_ID)
        public Builder objectId(String objectId) {
            if (objectId != null && !objectId.isBlank())
                this.objectId = Optional.of(new EntityReference(objectId));
            if (this.objectId.isPresent())
                this.objectIdPrefix = StringUtils.extractPrefix(objectId);
            return this;
        }

        @Field(OBJECT_LABEL)
        public Builder objectLabel(String objectLabel) {
            if (objectLabel != null && !objectLabel.isBlank())
                this.objectLabel = Optional.of(objectLabel);
            return this;
        }

        public Builder objectMatchField(String objectMatchField, SortedSet<EntityReference> propagateObjectMatchField) {
            if (objectMatchField != null && !objectMatchField.isBlank())
                this.objectMatchField = StringUtils.splitStringToSortedSet(
                        objectMatchField, "\\|", EntityReference::new);
            else
                this.objectMatchField = propagateObjectMatchField;
            return this;
        }

        @Field(OBJECT_MATCH_FIELD)
        public Builder objectMatchField(List<String> objectMatchField) {
            if (objectMatchField != null && !objectMatchField.isEmpty())
                this.objectMatchField = new TreeSet<>(objectMatchField.stream()
                        .map(EntityReference::new)
                        .collect(Collectors.toList()));
            return this;
        }

        public Builder objectMatchField(String objectMatchField) {
            if (objectMatchField != null && !objectMatchField.isBlank())
                this.objectMatchField = StringUtils.splitStringToSortedSet(objectMatchField, "\\|", EntityReference::new);
            return this;
        }

        public Builder objectPreprocessing(String objectPreprocessing, List<EntityReference> propagateObjectPreprocessing) {
            if (objectPreprocessing != null && !objectPreprocessing.isBlank())
                this.objectPreprocessing = StringUtils.splitStringToList(
                    objectPreprocessing, "\\|", EntityReference::new);
            else
                this.objectPreprocessing = propagateObjectPreprocessing;
            return this;
        }

        @Field(OBJECT_PREPROCESSING)
        public Builder objectPreprocessing(List<String> objectPreprocessing) {
            if (objectPreprocessing != null && !objectPreprocessing.isEmpty())
                this.objectPreprocessing = objectPreprocessing.stream()
                        .map(EntityReference::new)
                        .collect(Collectors.toList());
            return this;
        }

        public Builder objectPreprocessing(String objectPreprocessing) {
            this.objectPreprocessing = StringUtils.splitStringToList(objectPreprocessing, "\\|", EntityReference::new);
            return this;
        }

        public Builder objectSource(String objectSource, Optional<EntityReference> propagateObjectSource) {
            if (objectSource != null && !objectSource.isBlank())
                this.objectSource = Optional.of(new EntityReference(objectSource));
            else if (propagateObjectSource.isPresent())
                this.objectSource = propagateObjectSource;
            return this;
        }

        @Field(OBJECT_SOURCE)
        public Builder objectSource(String objectSource) {
            this.objectSource = Optional.of(new EntityReference(objectSource));
            return this;
        }

        public Builder objectSourceVersion(String objectSourceVersion, Optional<String> propagateObjectSourceVersion) {
            if (objectSourceVersion != null && !objectSourceVersion.isBlank())
                this.objectSourceVersion = Optional.of(objectSourceVersion);
            else if (propagateObjectSourceVersion.isPresent())
                this.objectSourceVersion = Optional.of(propagateObjectSourceVersion.get());
            return this;
        }

        @Field(OBJECT_SOURCE_VERSION)
        public Builder objectSourceVersion(String objectSourceVersion) {
            if (objectSourceVersion != null && !objectSourceVersion.isBlank())
                this.objectSourceVersion = Optional.of(objectSourceVersion);
            return this;
        }

        public Builder objectType(String objectType, Optional<EntityTypeEnum> propagateObjectType) {
            Optional<EntityTypeEnum> tempObjectType = EntityTypeEnum.fromString(objectType);
            if (tempObjectType.isPresent())
                this.objectType = tempObjectType;
            else if (propagateObjectType.isPresent())
                this.objectType = propagateObjectType;
            return this;
        }

        @Field(OBJECT_TYPE)
        public Builder objectType(String objectType) {
            this.objectType = EntityTypeEnum.fromString(objectType);
            return this;
        }

        @Field(OTHER)
        public Builder other(String other) {
            if (other != null && !other.isBlank()) {
                ObjectMapper objectMapper = new ObjectMapper();
                try {
                    Map<String, String> otherAsMap = objectMapper.readValue(other, Map.class);
                    if (otherAsMap != null && !otherAsMap.isEmpty())
                        this.other = Optional.of(new KeyValuePairsAsString(otherAsMap));
                } catch (Exception e) {
                    logger.error("Error while parsing other field: {}", e.getMessage(), e);
                }
            }
            return this;
        }

        @Field(PREDICATE_ID)
        public Builder predicateId(String predicateId) {
            this.predicateId = Optional.of(new EntityReference(predicateId));
            return this;
        }

        @Field(PREDICATE_LABEL)
        public Builder predicateLabel(String predicateLabel) {
            if (predicateLabel != null && !predicateLabel.isBlank())
                this.predicateLabel = Optional.of(predicateLabel);
            return this;
        }

        @Field(PREDICATE_MODIFIER)
        public Builder predicateModifier(String predicateModifier) {
            logger.debug("predicateModifier = {}", predicateModifier);
            if (predicateModifier != null && !predicateModifier.isBlank())
                this.predicateModifier = PredicateModifierEnum.fromString(predicateModifier);
            logger.debug("this.predicateModifier = {}", this.predicateModifier);
            return this;
        }

        public Builder publicationDate(String publicationDate) {
            if (publicationDate != null && !publicationDate.isBlank())
                this.publicationDate = Optional.of(new Date(publicationDate));
            return this;
        }

        @Field(PUBLICATION_DATE)
        public Builder publicationDate(java.util.Date publicationDate) {
            logger.trace("###### publicationDate = {}", publicationDate);
            if (publicationDate != null)
                this.publicationDate = Optional.of(Date.of(publicationDate));
            return this;
        }

        public Builder reviewerId(String reviewerId) {
            this.reviewerId = StringUtils.splitStringToSortedSet(reviewerId, "\\|", EntityReference::new);
            return this;
        }

        @Field(REVIEWER_ID)
        public Builder reviewerId(List<String> reviewerId) {
            if (reviewerId != null && !reviewerId.isEmpty())
                this.reviewerId = new TreeSet<>(reviewerId.stream()
                        .map(EntityReference::new)
                        .collect(Collectors.toList()));
            return this;
        }

        public Builder reviewerLabel(String reviewerLabel) {
            this.reviewerLabel = StringUtils.splitStringToSortedSet(reviewerLabel, "\\|", String::new);
            return this;
        }

        @Field(REVIEWER_LABEL)
        public Builder reviewLabel(List<String> reviewerLabel) {
            if (reviewerLabel != null && !reviewerLabel.isEmpty())
                this.reviewerLabel = new TreeSet<>(reviewerLabel);
            return this;
        }

        @Field(SEE_ALSO)
        public Builder seeAlso(List<String> seeAlso) {
            if (seeAlso != null && !seeAlso.isEmpty())
                this.seeAlso = new TreeSet<>(seeAlso);
            return this;
        }

        public Builder seeAlso(String seeAlso) {
            this.seeAlso = StringUtils.splitStringToSortedSet(seeAlso, "\\|", String::new);
            return this;
        }

        @Field(SIMILARITY_MEASURE)
        public Builder similarityMeasure(String similarityMeasure) {
            if (similarityMeasure != null && !similarityMeasure.isBlank())
                this.similarityMeasure = Optional.of(similarityMeasure);
            return this;
        }

        public Builder similarityScore(String similarityScore) {
            this.similarityScore = Optional.of(new Double(similarityScore));
            return this;
        }

        @Field(SIMILARITY_SCORE)
        public Builder similarityScore(java.lang.Double similarityScore) {
            this.similarityScore = Optional.of(Double.of(similarityScore));
            return this;
        }

        @Field(SUBJECT_CATEGORY)
        public Builder subjectCategory(String subjectCategory) {
            if (subjectCategory != null && !subjectCategory.isBlank())
                this.subjectCategory = Optional.of(subjectCategory);
            return this;
        }

        @Field(SUBJECT_ID)
        public Builder subjectId(String subjectId) {
            this.subjectId = Optional.of(new EntityReference(subjectId));
            if (this.subjectId.isPresent())
                this.subjectIdPrefix = StringUtils.extractPrefix(subjectId);
            return this;
        }

        @Field(SUBJECT_LABEL)
        public Builder subjectLabel(String subjectLabel) {
            if (subjectLabel != null && !subjectLabel.isBlank())
                this.subjectLabel = Optional.of(subjectLabel);
            return this;
        }

        /**
         * See <a href="https://mapping-commons.github.io/sssom/subject_match_field/">subject_match_field</a>.
         * @param subjectMatchField
         * @return
         */
        public Builder subjectMatchField(String subjectMatchField, SortedSet<EntityReference> propagateSubjectMatchField) {
            if (subjectMatchField != null && !subjectMatchField.isBlank())
                this.subjectMatchField = StringUtils.splitStringToSortedSet(subjectMatchField, "\\|", EntityReference::new);
            else
                this.subjectMatchField = propagateSubjectMatchField;
            return this;
        }

        public Builder subjectMatchField(String subjectMatchField) {
            this.subjectMatchField = StringUtils.splitStringToSortedSet(subjectMatchField, "\\|", EntityReference::new);
            return this;
        }

        @Field(SUBJECT_MATCH_FIELD)
        public Builder subjectMatchField(List<String> subjectMatchField) {
            if (subjectMatchField != null && !subjectMatchField.isEmpty())
                this.subjectMatchField = new TreeSet<>(subjectMatchField.stream()
                        .map(EntityReference::new)
                        .collect(Collectors.toList()));
            return this;
        }

        public Builder subjectPreprocessing(String subjectPreprocessing,
                                            List<EntityReference> propagateSubjectPreprocessing) {
            if (subjectPreprocessing != null && !subjectPreprocessing.isBlank())
                this.subjectPreprocessing = StringUtils.splitStringToList(
                    subjectPreprocessing, "\\|", EntityReference::new);
            else
                this.subjectPreprocessing = propagateSubjectPreprocessing;
            return this;
        }

        @Field(SUBJECT_PREPROCESSING)
        public Builder subjectProcessing(List<String> subjectPreprocessing) {
            if (subjectPreprocessing != null && !subjectPreprocessing.isEmpty())
                this.subjectPreprocessing = subjectPreprocessing.stream()
                        .map(EntityReference::new)
                        .collect(Collectors.toList());
            return this;
        }

        public Builder subjectPreprocessing(String subjectPreprocessing) {
            this.subjectPreprocessing = StringUtils.splitStringToList(subjectPreprocessing, "\\|", EntityReference::new);
            return this;
        }

        public Builder subjectSource(String subjectSource, Optional<EntityReference> propagateSubjectSource) {
            if (subjectSource != null && !subjectSource.isBlank())
                this.subjectSource = Optional.of(new EntityReference(subjectSource));
            else if (propagateSubjectSource.isPresent())
                this.subjectSource = propagateSubjectSource;
            return this;
        }

        @Field(SUBJECT_SOURCE)
        public Builder subjectSource(String subjectSource) {
            this.subjectSource = Optional.of(new EntityReference(subjectSource));
            return this;
        }

        public Builder subjectSourceVersion(String subjectSourceVersion, Optional<String> propagateSubjectSourceVersion) {
            if (subjectSourceVersion != null && !subjectSourceVersion.isBlank())
                this.subjectSourceVersion = Optional.of(subjectSourceVersion);
            else if (propagateSubjectSourceVersion.isPresent())
                this.subjectSourceVersion = Optional.of(propagateSubjectSourceVersion.get());
            return this;
        }

        @Field(SUBJECT_SOURCE_VERSION)
        public Builder subjectSourceVersion(String subjectSourceVersion) {
            if (subjectSourceVersion != null && !subjectSourceVersion.isBlank())
                this.subjectSourceVersion = Optional.of(subjectSourceVersion);
            return this;
        }

        public Builder subjectType(String subjectType, Optional<EntityTypeEnum> propagateSubjectType) {
            if (subjectType != null && !subjectType.isBlank())
                this.subjectType = EntityTypeEnum.fromString(subjectType);
            else if (propagateSubjectType.isPresent())
                this.subjectType = propagateSubjectType;
            return this;
        }

        @Field(SUBJECT_TYPE)
        public Builder subjectType(String subjectType) {
            this.subjectType = EntityTypeEnum.fromString(subjectType);
            return this;
        }

        @Field(MAPPING_ID)
        public Builder mappingId(String mappingId) {
            this.mappingId = UUID.fromString(mappingId);
            return this;
        }

        public Mapping build() {
            if (this.mappingId == null)
                this.mappingId = generateMappingUuid();

            return new Mapping(
                    authorId,
                    authorLabel,
                    comment,
                    confidence,
                    creatorId,
                    creatorLabel,
                    curationRule,
                    issueTrackerItem,
                    license,
                    mappingCardinality,
                    mappingDate,
                    mappingJustification,
                    mappingProvider,
                    mappingSetDescription,
                    mappingSetId,
                    mappingSetSource,
                    mappingSetTitle,
                    mappingSetVersion,
                    mappingSource,
                    mappingTool,
                    mappingToolVersion,
                    matchString,
                    objectCategory,
                    objectId,
                    objectLabel,
                    objectMatchField,
                    objectPreprocessing,
                    objectSource,
                    objectSourceVersion,
                    objectType,
                    other,
                    predicateId,
                    predicateLabel,
                    predicateModifier,
                    publicationDate,
                    reviewerId,
                    reviewerLabel,
                    seeAlso,
                    similarityMeasure,
                    similarityScore,
                    subjectCategory,
                    subjectId,
                    subjectLabel,
                    subjectMatchField,
                    subjectPreprocessing,
                    subjectSource,
                    subjectSourceVersion,
                    subjectType,
                    objectIdPrefix,
                    subjectIdPrefix,
                    mappingId
            );
        }

        private UUID generateMappingUuid() {
            StringBuilder mappingIdAsString = new StringBuilder();

            mappingIdAsString.append(this.mappingSetId.getDataAsString());
            this.subjectId.ifPresent(subjectId -> mappingIdAsString.append(subjectId.getDataAsString()));
            this.subjectLabel.ifPresent(subjectLabel -> mappingIdAsString.append(subjectLabel));
            this.predicateId.ifPresent(predicateId -> mappingIdAsString.append(predicateId.getDataAsString()));
            this.predicateLabel.ifPresent(predicateLabel -> mappingIdAsString.append(predicateLabel));
            this.predicateModifier.ifPresent(predicateModifier -> mappingIdAsString.append(predicateModifier.value()));
            this.objectId.ifPresent(objectId -> mappingIdAsString.append(objectId.getDataAsString()));
            this.objectLabel.ifPresent(objectLabel -> mappingIdAsString.append(objectLabel));
            this.mappingJustification.ifPresent(mappingJustification ->
                    mappingIdAsString.append(mappingJustification.getDataAsString()));

            logger.trace("#### mappingIdAsString = {}", mappingIdAsString);
            byte[] bytes = mappingIdAsString.toString().getBytes(StandardCharsets.UTF_8);
            UUID mappingId = UUID.nameUUIDFromBytes(bytes);
            return mappingId;
        }
    }
}
