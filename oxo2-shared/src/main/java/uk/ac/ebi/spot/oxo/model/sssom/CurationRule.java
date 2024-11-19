package uk.ac.ebi.spot.oxo.model.sssom;

import java.util.Optional;

public class CurationRule {
    private final EntityReference rule;
    private final Optional<String> text;

    public CurationRule(EntityReference rule, Optional<String> text) {
        this.rule = rule;
        this.text = text;
    }
}
