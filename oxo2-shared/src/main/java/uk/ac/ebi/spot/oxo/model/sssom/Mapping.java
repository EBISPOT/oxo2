package uk.ac.ebi.spot.oxo.model.sssom;

import java.util.*;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

/**
 * @see <a href="https://mapping-commons.github.io/sssom/Mapping/>Mapping</a>
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
@JsonDeserialize(builder = Mapping.Builder.class)
public class Mapping {

    @JsonProperty("subject")
    private final NodeReference subject;

    @JsonProperty("predicate")
    private final PredicateReference predicate;

    @JsonProperty("object")
    private final NodeReference object;

    /**
     * @see <a href="https://mapping-commons.github.io/sssom/Mapping/mapping_justification">mapping_justification</a>
     */
    @JsonProperty("mapping_justification")
    private final Optional<EntityReference> mappingJustification;

    /**
     * Note that strictly speaking, according to SSSOM, there is no relationship between author_id and author_label.
     * The same holds for reviewers and creators. @see <a href="https://github.com/mapping-commons/sssom/issues/344"/>.
     * Authors, reviewers and creators are represented here a LabelledReference to allow for the possibility of enriching
     * ids with labels.
     */
    @JsonProperty("author")
    private final SortedSet<LabelledReference> author;

    @JsonProperty("reviewer")
    private final SortedSet<LabelledReference> reviewer;

    @JsonProperty("creator")
    private final SortedSet<LabelledReference> creator;

    /**
     * @see <a href="https://mapping-commons.github.io/sssom/Mapping/license">license</a>
     */
    @JsonProperty("license")
    private final Optional<Uri> license;

    /**
     * @see <a href="https://mapping-commons.github.io/sssom/Mapping/mapping_provider">mapping_provider</a>
     */
    @JsonProperty("mapping_provider")
    private final Optional<Uri> mappingProvider;

    /**
     * @see <a href="https://mapping-commons.github.io/sssom/Mapping/mapping_source">mapping_source</a>
     */
    @JsonProperty("mapping_source")
    private final Optional<EntityReference> mappingSource;

    /**
     * @see <a href="https://mapping-commons.github.io/sssom/Mapping/mapping_cardinality">mapping_cardinality</a>
     */
    @JsonProperty("mapping_cardinality")
    private final Optional<MappingCardinalityEnum> mappingCardinality;

    /**
     *  @see <a href="https://mapping-commons.github.io/sssom/Mapping/mapping_tool">mapping_tool</a>
     */
    @JsonProperty("mapping_tool")
    private final Optional<String> mappingTool;

    /**
     *  @see <a href="https://mapping-commons.github.io/sssom/Mapping/mapping_tool_version">mapping_tool_version</a>
     */
    @JsonProperty("mapping_tool_version")
    private final Optional<String> mappingToolVersion;

    /**
     *  @see <a href="https://mapping-commons.github.io/sssom/Mapping/mapping_date">mapping_date</a>
     */
    @JsonProperty("mapping_date")
    private final Optional<Date> mappingDate;

    /**
     *  @see <a href="https://mapping-commons.github.io/sssom/Mapping/publication_date">publication_date</a>
     */
    @JsonProperty("publication_date")
    private final Optional<Date> publicationDate;


    /**
     * @see <a href="https://mapping-commons.github.io/sssom/Mapping/confidence">confidence</a>
     */
    @JsonProperty("confidence")
    private final Optional<Double> confidence;

    /**
     * @see <a href="https://mapping-commons.github.io/sssom/Mapping/curation_rule">curation_rule</a>
     *
     * As with authors, reviewers and creators, curation rules are represented here as a CurationRule to allow for
     * associating a rule with text even though SSSOM does not have a relationship between curation_rule and curation_rule_text.
     */
    @JsonProperty("curation")
    private final SortedSet<CurationRule> curationRule;

    /**
     *  @see <a href="https://mapping-commons.github.io/sssom/Mapping/match_string">match_string</a>
     */
    @JsonProperty("match_string")
    private final SortedSet<String> matchString;

    /**
     *  @see <a href="https://mapping-commons.github.io/sssom/Mapping/similarity_score">similarity_score</a>
     */
    @JsonProperty("similarity_score")
    private final Optional<Double> similarityScore;

    /**
     *  @see <a href="https://mapping-commons.github.io/sssom/Mapping/similarity_measure">similarity_measure</a>
     */
    @JsonProperty("similarity_measure")
    private final Optional<String> similarityMeasure;

    /**
     *  @see <a href="https://mapping-commons.github.io/sssom/Mapping/see_also">see_also</a>
     */
    @JsonProperty("see_also")
    private final SortedSet<String> seeAlso;

    /**
     *  @see <a href="https://mapping-commons.github.io/sssom/Mapping/issue_tracker_item">issue_tracker_item</a>
     */
    @JsonProperty("issue_tracker_item")
    private final Optional<EntityReference> issueTrackerItem;

    /**
     * @see <a href="https://mapping-commons.github.io/sssom/Mapping/other">other</a>
     */
    @JsonProperty("other")
    private final Optional<KeyValuePairsAsString> other;

    /**
     * @see <a href="https://mapping-commons.github.io/sssom/Mapping/comment">comment</a>
     */
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
        this.matchString = builder.matchString;
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

    public Optional<EntityReference> getMappingJustification() {
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

    public SortedSet<String> getMatchString() {
        return matchString;
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

    public SortedSet<String> getOther() {
        return other.isPresent() ? other.get().getKeyValuePairsAsSet() : new TreeSet<>();
    }

    public Optional<String> getComment() {
        return comment;
    }

    @JsonPOJOBuilder
    public static class Builder {
        private NodeReference subject;
        private PredicateReference predicate;
        private NodeReference object;
        private Optional<EntityReference> mappingJustification;
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
        private SortedSet<String> matchString = new TreeSet<>();
        private Optional<Double> similarityScore = Optional.empty();
        private Optional<String> similarityMeasure = Optional.empty();
        private SortedSet<String> seeAlso = new TreeSet<>();
        private Optional<EntityReference> issueTrackerItem = Optional.empty();
        private Optional<KeyValuePairsAsString> other = Optional.empty();
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
            this.mappingJustification = Optional.of(mappingJustification);
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

        public Builder license(String license) {
            this.license = Optional.of(new Uri(license));
            return this;
        }

        public Builder mappingProvider(String mappingProvider) {
            this.mappingProvider = Optional.of(new Uri(mappingProvider));
            return this;
        }

        public Builder mappingSource(EntityReference mappingSource) {
            this.mappingSource = Optional.of(mappingSource);
            return this;
        }

        public Builder mappingCardinality(MappingCardinalityEnum mappingCardinality) {
            this.mappingCardinality = Optional.of(mappingCardinality);
            return this;
        }

        public Builder mappingTool(String mappingTool) {
            this.mappingTool = Optional.of(mappingTool);
            return this;
        }

        public Builder mappingToolVersion(String mappingToolVersion) {
            this.mappingToolVersion = Optional.of(mappingToolVersion);
            return this;
        }

        public Builder mappingDate(String mappingDate) {
            this.mappingDate = Optional.of(new Date(mappingDate));
            return this;
        }

        public Builder publicationDate(String publicationDate) {
            this.publicationDate = Optional.of(new Date(publicationDate));
            return this;
        }

        public Builder confidence(String confidence) {
            this.confidence = Optional.of(new Double(confidence));
            return this;
        }

        public Builder curationRule(SortedSet<CurationRule> curationRule) {
            this.curationRule = curationRule;
            return this;
        }

        public Builder matchString(SortedSet<String> matchString) {
            this.matchString = matchString;
            return this;
        }

        public Builder similarityScore(String similarityScore) {
            this.similarityScore = Optional.of(new Double(similarityScore));
            return this;
        }

        public Builder similarityMeasure(String similarityMeasure) {
            this.similarityMeasure = Optional.of(similarityMeasure);
            return this;
        }

        public Builder seeAlso(SortedSet<String> seeAlso) {
            this.seeAlso = seeAlso;
            return this;
        }

        public Builder issueTrackerItem(EntityReference issueTrackerItem) {
            this.issueTrackerItem = Optional.of(issueTrackerItem);
            return this;
        }

        public Builder other(String other) {
            this.other = Optional.of(new KeyValuePairsAsString(other));
            return this;
        }

        public Builder comment(String comment) {
            this.comment = Optional.of(comment);
            return this;
        }

        public Mapping build() {
            return new Mapping(this);
        }
    }
}