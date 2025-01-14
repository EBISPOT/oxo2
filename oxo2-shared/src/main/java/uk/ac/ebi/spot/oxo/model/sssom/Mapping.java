package uk.ac.ebi.spot.oxo.model.sssom;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import uk.ac.ebi.spot.oxo.utils.StringUtils;

import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
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
        @JsonProperty(OTHER)
        @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = SSSOMDataType.FilterOut.class)
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
        Optional<EntityTypeEnum> subjectType) implements Comparable<Mapping> {

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public int compareTo(Mapping other) {
        return this.mappingSetId.compareTo(other.mappingSetId);
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

        private static Logger logger = LoggerFactory.getLogger(Builder.class);


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

        public Builder mappingSetSource(String mappingSetSource) {
            this.mappingSetSource = StringUtils.splitStringToSortedSet(mappingSetSource, "\\|", Uri::new);
            return this;
        }

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

        public Builder authorId(String authorId) {
            this.authorId = StringUtils.splitStringToSortedSet(authorId, "\\|", EntityReference::new);
            return this;
        }

        public Builder authorLabel(SortedSet<String> authorLabel) {
            if (authorLabel != null && !authorLabel.isEmpty())
                this.authorLabel = authorLabel;
            return this;
        }

        public Builder authorLabel(String authorLabel) {
            this.authorLabel = StringUtils.splitStringToSortedSet(authorLabel, "\\|", String::new);
            return this;
        }

        public Builder comment(String comment) {
            if (comment != null && !comment.isBlank())
                this.comment = Optional.of(comment);
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

        public Builder creatorLabel(String creatorLabel) {
            this.creatorLabel = StringUtils.splitStringToSortedSet(creatorLabel, "\\|", String::new);
            return this;
        }

//        public Builder curationRule(SortedSet<EntityReference> curationRule) {
//            this.curationRule = curationRule;
//            return this;
//        }

        public Builder curationRule(String curationRule) {
            this.curationRule = StringUtils.splitStringToSortedSet(curationRule, "\\|", EntityReference::new);
            return this;
        }

        public Builder issueTrackerItem(String issueTrackerItem) {
            if (issueTrackerItem != null && !issueTrackerItem.isBlank())
                this.issueTrackerItem = Optional.of(new EntityReference(issueTrackerItem));
            return this;
        }

        public Builder license(String license) {
            this.license = Optional.of(new Uri(license));
            return this;
        }

        public Builder mappingCardinality(MappingCardinalityEnum mappingCardinality) {
            this.mappingCardinality = Optional.of(mappingCardinality);
            return this;
        }

        public Builder mappingCardinality(String mappingCardinality) {
            this.mappingCardinality = MappingCardinalityEnum.fromString(mappingCardinality);
            return this;
        }

        public Builder mappingDate(String mappingDate) {
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

        public Builder mappingProvider(String mappingProvider) {
            this.mappingProvider = Optional.of(new Uri(mappingProvider));
            return this;
        }

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

        public Builder mappingTool(String mappingTool) {
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

        public Builder mappingToolVersion(String mappingToolVersion) {
            if (mappingToolVersion != null && !mappingToolVersion.isBlank())
                this.mappingToolVersion = Optional.of(mappingToolVersion);
            return this;
        }

//        public Builder matchString(SortedSet<String> matchString) {
//            this.matchString = matchString;
//            return this;
//        }

        public Builder matchString(String matchString) {
            this.matchString = StringUtils.splitStringToSortedSet(matchString, "\\|", String::new);
            return this;
        }

        public Builder objectCategory(String objectCategory) {
            if (objectCategory != null && !objectCategory.isBlank())
                this.objectCategory = Optional.of(objectCategory);
            return this;
        }

        public Builder objectId(String objectId) {
            this.objectId = Optional.of(new EntityReference(objectId));
            return this;
        }

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

        public Builder objectSourceVersion(String objectSourceVersion) {
            if (objectSourceVersion != null && !objectSourceVersion.isBlank())
                this.objectSourceVersion = Optional.of(objectSourceVersion);
            return this;
        }

        public Builder objectType(EntityTypeEnum objectType) {
            this.objectType = Optional.of(objectType);
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

        public Builder objectType(String objectType) {
            this.objectType = EntityTypeEnum.fromString(objectType);
            return this;
        }

        public Builder other(String other) {
            if (other != null && !other.isBlank())
                this.other = Optional.of(new KeyValuePairsAsString(other));
            return this;
        }

        public Builder predicateId(String predicateId) {
            this.predicateId = Optional.of(new EntityReference(predicateId));
            return this;
        }

        public Builder predicateLabel(String predicateLabel) {
            if (predicateLabel != null && !predicateLabel.isBlank())
                this.predicateLabel = Optional.of(predicateLabel);
            return this;
        }

        public Builder predicateModifier(PredicateModifierEnum predicateModifier) {
            this.predicateModifier = Optional.of(predicateModifier);
            return this;
        }

        public Builder predicateModifier(String predicateModifier) {
            logger.debug("predicateModifier = {}", predicateModifier);
            this.predicateModifier = PredicateModifierEnum.fromString(predicateModifier);
            logger.debug("this.predicateModifier = {}", this.predicateModifier);
            return this;
        }

        public Builder publicationDate(String publicationDate) {
            this.publicationDate = Optional.of(new Date(publicationDate));
            return this;
        }

        public Builder reviewerId(String reviewerId) {
            this.reviewerId = StringUtils.splitStringToSortedSet(reviewerId, "\\|", EntityReference::new);
            return this;
        }

        public Builder reviewerLabel(String reviewerLabel) {
            this.reviewerLabel = StringUtils.splitStringToSortedSet(reviewerLabel, "\\|", String::new);
            return this;
        }

        public Builder seeAlso(SortedSet<String> seeAlso) {
            this.seeAlso = seeAlso;
            return this;
        }

        public Builder seeAlso(String seeAlso) {
            this.seeAlso = StringUtils.splitStringToSortedSet(seeAlso, "\\|", String::new);
            return this;
        }

        public Builder similarityMeasure(String similarityMeasure) {
            if (similarityMeasure != null && !similarityMeasure.isBlank())
                this.similarityMeasure = Optional.of(similarityMeasure);
            return this;
        }

        public Builder similarityScore(String similarityScore) {
            this.similarityScore = Optional.of(new Double(similarityScore));
            return this;
        }

        public Builder subjectCategory(String subjectCategory) {
            if (subjectCategory != null && !subjectCategory.isBlank())
                this.subjectCategory = Optional.of(subjectCategory);
            return this;
        }

        public Builder subjectId(String subjectId) {
            this.subjectId = Optional.of(new EntityReference(subjectId));
            return this;
        }

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

        public Builder subjectPreprocessing(String subjectPreprocessing,
                                            List<EntityReference> propagateSubjectPreprocessing) {
            if (subjectPreprocessing != null && !subjectPreprocessing.isBlank())
                this.subjectPreprocessing = StringUtils.splitStringToList(
                    subjectPreprocessing, "\\|", EntityReference::new);
            else
                this.subjectPreprocessing = propagateSubjectPreprocessing;
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

        public Builder subjectType(String subjectType) {
            this.subjectType = EntityTypeEnum.fromString(subjectType);
            return this;
        }

        public Mapping build() {
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
                    subjectType
            );
        }
    }
}