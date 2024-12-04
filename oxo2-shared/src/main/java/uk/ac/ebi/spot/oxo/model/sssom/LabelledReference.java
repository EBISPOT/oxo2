package uk.ac.ebi.spot.oxo.model.sssom;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_ABSENT)
public class LabelledReference implements Comparable<LabelledReference>{
    @JsonProperty("id")
    protected final Optional<EntityReference> id;
    @JsonProperty("label")
    protected final Optional<String> label;

    public LabelledReference(String id, String label) {
        this(Optional.of(new EntityReference(id)),
                Optional.of(label));
    }

    public LabelledReference(String idOrLabel) {
        this((idOrLabel.contains(":") ? Optional.of(new EntityReference(idOrLabel)) : Optional.empty()),
                idOrLabel.contains(":") ? Optional.empty() : Optional.of(idOrLabel));
    }

    protected LabelledReference(Optional<EntityReference> id, Optional<String> label) {
        if (id.isEmpty() && label.isEmpty())
            throw new IllegalArgumentException("As a minimum either an 'id' or a 'label' needs to be supplied.");
        this.id = id;
        this.label = label;
    }

    @Override
    public int compareTo(LabelledReference other) {
        int labelComparison = this.label.orElse("").compareTo(other.label.orElse(""));
        if (labelComparison != 0) {
            return labelComparison;
        }
        return this.id.map(EntityReference::toString).orElse("")
                .compareTo(other.id.map(EntityReference::toString).orElse(""));
    }

    @Override
    public String toString() {
        return "LabelledReference{" +
                "id=" + id +
                ", label=" + label +
                '}';
    }
}
