package uk.ac.ebi.spot.oxo.model.sssom;

import java.util.List;
import java.util.Optional;

public class Explanation {
    private String conclusion;
    private List<String> premises;
    private Optional<ChainRulesEnum> chainRules;
    private Optional<String> chainRuleAsString;

    public Explanation(String conclusion, List<String> premises, Optional<ChainRulesEnum> chainRules) {
        this.conclusion = conclusion;
        this.premises = premises;
        this.chainRules = chainRules;
        if (chainRules.isPresent()) {
            chainRuleAsString = Optional.of(chainRules.get().getAbbreviatedRule());
        }
    }

    public Optional<ChainRulesEnum> getChainRules() {
        return chainRules;
    }

    public void setChainRules(Optional<ChainRulesEnum> chainRules) {
        this.chainRules = chainRules;
    }

    public Optional<String> getChainRuleAsString() {
        return chainRuleAsString;
    }

    public void setChainRuleAsString(Optional<String> chainRuleAsString) {
        this.chainRuleAsString = chainRuleAsString;
    }

    public String getConclusion() {
        return conclusion;
    }

    public void setConclusion(String conclusion) {
        this.conclusion = conclusion;
    }

    public List<String> getPremises() {
        return premises;
    }

    public void setPremises(List<String> premises) {
        this.premises = premises;
    }


}
