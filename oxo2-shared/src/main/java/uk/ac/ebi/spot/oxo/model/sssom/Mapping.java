package uk.ac.ebi.spot.oxo.model.sssom;

import java.util.*;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

@JsonInclude(JsonInclude.Include.NON_ABSENT)
@JsonDeserialize(builder = Mapping.Builder.class)
public class Mapping {

    @JsonProperty("subject")
    private final NodeReference subject;

    @JsonProperty("predicate")
    private final PredicateReference predicate;

    @JsonProperty("object")
    private final NodeReference object;

    @JsonProperty("mapping_justification")
    private final EntityReference mappingJustification;

    @JsonProperty("author")
    private final SortedSet<LabelledReference> author;

    @JsonProperty("reviewer")
    private final SortedSet<LabelledReference> reviewer;

    @JsonProperty("creator")
    private final SortedSet<LabelledReference> creator;

    @JsonProperty("license")
    private final Optional<Uri> license;

    @JsonProperty("mapping_provider")
    private final Optional<Uri> mappingProvider;

    @JsonProperty("mapping_source")
    private final Optional<EntityReference> mappingSource;

    @JsonProperty("mapping_cardinality")
    private final Optional<MappingCardinalityEnum> mappingCardinality;

    @JsonProperty("mapping_tool")
    private final Optional<String> mappingTool;

    @JsonProperty("mapping_tool_version")
    private final Optional<String> mappingToolVersion;

    @JsonProperty("mapping_date")
    private final Optional<Date> mappingDate;

    @JsonProperty("publication_date")
    private final Optional<Date> publicationDate;

    @JsonProperty("confidence")
    private final Optional<Double> confidence;

    @JsonProperty("curation_rule")
    private final SortedSet<CurationRule> curationRule;

    @JsonProperty("similarity_score")
    private final Optional<Double> similarityScore;

    @JsonProperty("similarity_measure")
    private final Optional<String> similarityMeasure;

    @JsonProperty("see_also")
    private final SortedSet<String> seeAlso;

    @JsonProperty("issue_tracker_item")
    private final Optional<EntityReference> issueTrackerItem;

    @JsonProperty("other")
    private final Optional<String> other;

    @JsonProperty("comment")
    private final Optional<String> comment;

    private Mapping(Builder builder) {
        this.subject = builder.subject;
        this.predicate = builder.predicate;
        this.object = builder.object;
        this.mappingJustification = builder.mappingJustification;
        this.author = builder.author;
        this.reviewer = builder.reviewer;
        this.creator = builder.creator;
        this.license = builder.license;
        this.mappingProvider = builder.mappingProvider;
        this.mappingSource = builder.mappingSource;
        this.mappingCardinality = builder.mappingCardinality;
        this.mappingTool = builder.mappingTool;
        this.mappingToolVersion = builder.mappingToolVersion;
        this.mappingDate = builder.mappingDate;
        this.publicationDate = builder.publicationDate;
        this.confidence = builder.confidence;
        this.curationRule = builder.curationRule;
        this.similarityScore = builder.similarityScore;
        this.similarityMeasure = builder.similarityMeasure;
        this.seeAlso = builder.seeAlso;
        this.issueTrackerItem = builder.issueTrackerItem;
        this.other = builder.other;
        this.comment = builder.comment;
    }

    public NodeReference getSubject() {
        return subject;
    }

    public PredicateReference getPredicate() {
        return predicate;
    }

    public NodeReference getObject() {
        return object;
    }

    public EntityReference getMappingJustification() {
        return mappingJustification;
    }

    public SortedSet<LabelledReference> getAuthor() {
        return author;
    }

    public SortedSet<LabelledReference> getReviewer() {
        return reviewer;
    }

    public SortedSet<LabelledReference> getCreator() {
        return creator;
    }

    public Optional<Uri> getLicense() {
        return license;
    }

    public Optional<Uri> getMappingProvider() {
        return mappingProvider;
    }

    public Optional<EntityReference> getMappingSource() {
        return mappingSource;
    }

    public Optional<MappingCardinalityEnum> getMappingCardinality() {
        return mappingCardinality;
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

    public Optional<Double> getConfidence() {
        return confidence;
    }

    public SortedSet<CurationRule> getCurationRule() {
        return curationRule;
    }

    public Optional<Double> getSimilarityScore() {
        return similarityScore;
    }

    public Optional<String> getSimilarityMeasure() {
        return similarityMeasure;
    }

    public SortedSet<String> getSeeAlso() {
        return seeAlso;
    }

    public Optional<EntityReference> getIssueTrackerItem() {
        return issueTrackerItem;
    }

    public Optional<String> getOther() {
        return other;
    }

    public Optional<String> getComment() {
        return comment;
    }

    @JsonPOJOBuilder
    public static class Builder {
        private NodeReference subject;
        private PredicateReference predicate;
        private NodeReference object;
        private EntityReference mappingJustification;
        private SortedSet<LabelledReference> author = new TreeSet<>();
        private SortedSet<LabelledReference> reviewer = new TreeSet<>();
        private SortedSet<LabelledReference> creator = new TreeSet<>();
        private Optional<Uri> license = Optional.empty();
        private Optional<Uri> mappingProvider = Optional.empty();
        private Optional<EntityReference> mappingSource = Optional.empty();
        private Optional<MappingCardinalityEnum> mappingCardinality = Optional.empty();
        private Optional<String> mappingTool = Optional.empty();
        private Optional<String> mappingToolVersion = Optional.empty();
        private Optional<Date> mappingDate = Optional.empty();
        private Optional<Date> publicationDate = Optional.empty();
        private Optional<Double> confidence = Optional.empty();
        private SortedSet<CurationRule> curationRule = new TreeSet<>();
        private Optional<Double> similarityScore = Optional.empty();
        private Optional<String> similarityMeasure = Optional.empty();
        private SortedSet<String> seeAlso = new TreeSet<>();
        private Optional<EntityReference> issueTrackerItem = Optional.empty();
        private Optional<String> other = Optional.empty();
        private Optional<String> comment = Optional.empty();

        public Builder subject(NodeReference subject) {
            this.subject = subject;
            return this;
        }

        public Builder predicate(PredicateReference predicate) {
            this.predicate = predicate;
            return this;
        }

        public Builder object(NodeReference object) {
            this.object = object;
            return this;
        }

        public Builder mappingJustification(EntityReference mappingJustification) {
            this.mappingJustification = mappingJustification;
            return this;
        }

        public Builder author(SortedSet<LabelledReference> author) {
            this.author = author;
            return this;
        }

        public Builder reviewer(SortedSet<LabelledReference> reviewer) {
            this.reviewer = reviewer;
            return this;
        }

        public Builder creator(SortedSet<LabelledReference> creator) {
            this.creator = creator;
            return this;
        }

        public Builder license(Optional<Uri> license) {
            this.license = license;
            return this;
        }

        public Builder mappingProvider(Optional<Uri> mappingProvider) {
            this.mappingProvider = mappingProvider;
            return this;
        }

        public Builder mappingSource(Optional<EntityReference> mappingSource) {
            this.mappingSource = mappingSource;
            return this;
        }

        public Builder mappingCardinality(Optional<MappingCardinalityEnum> mappingCardinality) {
            this.mappingCardinality = mappingCardinality;
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

        public Builder confidence(Optional<Double> confidence) {
            this.confidence = confidence;
            return this;
        }

        public Builder curationRule(SortedSet<CurationRule> curationRule) {
            this.curationRule = curationRule;
            return this;
        }

        public Builder similarityScore(Optional<Double> similarityScore) {
            this.similarityScore = similarityScore;
            return this;
        }

        public Builder similarityMeasure(Optional<String> similarityMeasure) {
            this.similarityMeasure = similarityMeasure;
            return this;
        }

        public Builder seeAlso(SortedSet<String> seeAlso) {
            this.seeAlso = seeAlso;
            return this;
        }

        public Builder issueTrackerItem(Optional<EntityReference> issueTrackerItem) {
            this.issueTrackerItem = issueTrackerItem;
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

        public Mapping build() {
            return new Mapping(this);
        }
    }
}