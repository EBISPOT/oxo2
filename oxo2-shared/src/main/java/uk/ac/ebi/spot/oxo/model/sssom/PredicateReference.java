package uk.ac.ebi.spot.oxo.model.sssom;

import java.util.Optional;

public class PredicateReference extends LabelledReference {

    final Optional<PredicateModifierEnum> predicateModifier;


    public PredicateReference(String id, String label, Optional<PredicateModifierEnum> predicateModifier) {
        super(id, label);
        this.predicateModifier = predicateModifier;
    }

    public PredicateReference(String idOrLabel, Optional<PredicateModifierEnum> predicateModifier) {
        super(idOrLabel);
        this.predicateModifier = predicateModifier;
    }

    public PredicateReference(Optional<EntityReference> id, Optional<String> label,
                              Optional<PredicateModifierEnum> predicateModifier) {
        super(id, label);
        this.predicateModifier = predicateModifier;
    }
}
