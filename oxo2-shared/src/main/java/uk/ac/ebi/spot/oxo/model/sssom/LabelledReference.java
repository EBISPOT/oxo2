package uk.ac.ebi.spot.oxo.model.sssom;

import java.util.Optional;
import com.fasterxml.jackson.annotation.JsonProperty;

public class LabelledReference {
    @JsonProperty("labelled_reference_id")
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
}
