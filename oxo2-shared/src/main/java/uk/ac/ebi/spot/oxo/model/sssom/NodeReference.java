package uk.ac.ebi.spot.oxo.model.sssom;

import java.util.*;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

@JsonInclude(JsonInclude.Include.NON_ABSENT)
@JsonDeserialize(builder = NodeReference.Builder.class)
public class NodeReference extends LabelledReference {
    @JsonProperty("category")
    private final Optional<String> category;

    @JsonProperty("role")
    private final NodeReferenceEnum role;

    @JsonProperty("type")
    private final Optional<EntityTypeEnum> type;

    @JsonProperty("source")
    private final Optional<EntityReference> source;

    @JsonProperty("source_version")
    private final Optional<String> sourceVersion;

    @JsonProperty("match_field")
    private final SortedSet<String> matchField;

    @JsonProperty("match_string")
    private final SortedSet<String> matchString;

    @JsonProperty("preprocessing")
    private final List<EntityReference> preprocessing;

    private NodeReference(Builder builder) {
        super(builder.id, builder.label);
        this.role = builder.role;
        this.category = builder.category;
        this.type = builder.type;
        this.source = builder.source;
        this.sourceVersion = builder.sourceVersion;
        this.matchField = builder.matchField;
        this.matchString = builder.matchString;
        this.preprocessing = builder.preprocessing;
    }

    public Optional<String> getCategory() {
        return category;
    }

    public NodeReferenceEnum getRole() {
        return role;
    }

    public Optional<EntityTypeEnum> getType() {
        return type;
    }

    public Optional<EntityReference> getSource() {
        return source;
    }

    public Optional<String> getSourceVersion() {
        return sourceVersion;
    }

    public SortedSet<String> getMatchField() {
        return matchField;
    }

    public SortedSet<String> getMatchString() {
        return matchString;
    }

    public List<EntityReference> getPreprocessing() {
        return preprocessing;
    }

    @JsonPOJOBuilder
    public static class Builder {
        private final Optional<EntityReference> id;

        private final Optional<String> label;

        private final NodeReferenceEnum role;

        private Optional<String> category = Optional.empty();

        private Optional<EntityTypeEnum> type = Optional.empty();

        private Optional<EntityReference> source = Optional.empty();

        private Optional<String> sourceVersion = Optional.empty();

        private SortedSet<String> matchField = new TreeSet<>();

        private SortedSet<String> matchString = new TreeSet<>();

        private List<EntityReference> preprocessing = new ArrayList<>();

        public Builder(String id, String label, NodeReferenceEnum role) {
            if (id == null && label == null)
                throw new IllegalArgumentException("Both 'id' and 'label' are null. At least 1 of them must not be null" +
                        "and must not be empty or consisting of whitespaces only.");
            else if (id.isBlank() && label.isBlank())
                throw new IllegalArgumentException("Both 'id' and 'label' cannot be empty or consisting of whitespaces only.");

            this.id = !id.isBlank() ? Optional.of(new EntityReference(id)) : Optional.empty();
            this.label = !label.isBlank() ? Optional.of(label) : Optional.empty();
            this.role = role;
        }

        public Builder catergory(String category) {
            this.category = Optional.of(category);
            return this;
        }

        public Builder type(EntityTypeEnum entityTypeEnum) {
            this.type = Optional.of(entityTypeEnum);
            return this;
        }

        public Builder source(String source) {
            this.source = Optional.of(new EntityReference(source));
            return this;
        }

        public Builder sourceVersion(String sourceVersion) {
            this.sourceVersion = Optional.of(sourceVersion);
            return this;
        }

        public Builder matchFields(Set<String> matchFields) {
            this.matchField = new TreeSet<>(matchFields);
            return this;
        }

        public Builder matchString(Set<String> matchString) {
            this.matchString = new TreeSet<>(matchString);
            return this;
        }

        public Builder preprocessing(List<EntityReference> preprocessing) {
            this.preprocessing = preprocessing;
            return this;
        }

        public NodeReference build() {
            return new NodeReference(this);
        }
    }

    public enum NodeReferenceEnum {
        OBJECT,
        SUBJECT;
    }

}
