package uk.ac.ebi.spot.oxo.model.sssom;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Optional;

public record CurationRule (
    @JsonProperty
    EntityReference rule,
    @JsonProperty("rule_text")
    Optional<String> text) implements Comparable<CurationRule> {


    public CurationRule(EntityReference rule, Optional<String> text) {
        this.rule = rule;
        this.text = text;
    }

    public CurationRule(EntityReference rule) {
        this(rule, Optional.empty());
    }

    public CurationRule(String s) {
        this(new EntityReference(s));
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

    @Override
    public String toString() {
        return "CurationRule{" +
                "rule=" + rule +
                ", text=" + text +
                '}';
    }
}