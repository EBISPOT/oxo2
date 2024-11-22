package uk.ac.ebi.spot.oxo.model.sssom;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

@JsonInclude(JsonInclude.Include.NON_ABSENT)
@JsonDeserialize(builder = PredicateReference.Builder.class)
public class PredicateReference extends LabelledReference {

    @JsonProperty("modifier")
    private final Optional<PredicateModifierEnum> predicateModifier;

    private PredicateReference(Builder builder) {
        super(builder.id, builder.label);
        this.predicateModifier = builder.predicateModifier;
    }

    @JsonPOJOBuilder
    public static class Builder {
        private final Optional<EntityReference> id;
        private final Optional<String> label;
        private Optional<PredicateModifierEnum> predicateModifier = Optional.empty();

        public Builder(String id, String label) {
            this.id = Optional.of(new EntityReference(id));
            this.label = Optional.of(label);
        }

        public Builder(String idOrLabel) {
            if (idOrLabel.contains(":")) {
                this.id = Optional.of(new EntityReference(idOrLabel));
                this.label = Optional.empty();
            } else {
                this.id = Optional.empty();
                this.label = Optional.of(idOrLabel);
            }
        }

        public Builder predicateModifier(PredicateModifierEnum predicateModifier) {
            this.predicateModifier = Optional.of(predicateModifier);
            return this;
        }

        public PredicateReference build() {
            return new PredicateReference(this);
        }
    }
}