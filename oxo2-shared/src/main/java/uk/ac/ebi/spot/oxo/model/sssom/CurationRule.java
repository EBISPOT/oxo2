package uk.ac.ebi.spot.oxo.model.sssom;

import java.io.Serializable;
import java.util.Optional;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

public class CurationRule implements Comparable<CurationRule> {

    @JsonProperty
    private final EntityReference rule;

    @JsonProperty("rule_text")
    private final Optional<String> text;

    public CurationRule(EntityReference rule, Optional<String> text) {
        this.rule = rule;
        this.text = text;
    }

    public EntityReference getRule() {
        return rule;
    }

    public Optional<String> getText() {
        return text;
    }

    @Override
    public int compareTo(CurationRule other) {
        int ruleComparison = this.rule.toString().compareTo(other.rule.toString());
        if (ruleComparison != 0) {
            return ruleComparison;
        }
        return this.text.orElse("").compareTo(other.text.orElse(""));
    }
}