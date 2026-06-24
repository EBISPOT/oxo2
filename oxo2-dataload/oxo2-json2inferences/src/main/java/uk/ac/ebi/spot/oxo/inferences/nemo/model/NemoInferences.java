package uk.ac.ebi.spot.oxo.inferences.nemo.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.*;

public class NemoInferences {

    @JsonProperty("finalConclusion")
    private List<String> finalConclusion;

    @JsonProperty("inferences")
    private List<NemoInference> inferences;

    // Getters and setters
    public List<String> getFinalConclusion() {
        return finalConclusion;
    }

    public void setFinalConclusion(List<String> finalConclusion) {
        this.finalConclusion = finalConclusion;
    }

    public List<NemoInference> getInferences() {
        return inferences;
    }

    public void setInferences(List<NemoInference> inferences) {
        this.inferences = inferences;
    }


    /**
     * Nemo conclusions can be considered to be unique even though it is possible that the same conclusion can be derived
     * from multiple premises and rule applications. This is due to an optimization during tracing of inferences.
     * See https://iccl.inf.tu-dresden.de/w/images/f/f6/An_Existential_Rule_Framework_for_Computing_Why_Provenance_On_Demand_for_Datalog_%282%29.pdf
     *
     */
    public static class NemoInference {

        @JsonProperty("rule")
        private String rule;

        /**
         * The {@code #[name("...")]} attribute of the rule that produced this conclusion,
         * emitted by nmo in the {@code --trace-output} JSON (ADR-0009/0010). Matches a
         * {@link uk.ac.ebi.spot.oxo.model.sssom.ChainRulesEnum#getName()} for the named
         * chain rules; absent for the unnamed seed rule and for {@code "Asserted"} EDB facts.
         */
        @JsonProperty("ruleName")
        private String ruleName;

        @JsonProperty("conclusion")
        private String conclusion;

        @JsonProperty("premises")
        private List<String> premises;

        public String getRule() {
            return rule;
        }

        public String getRuleName() {
            return ruleName;
        }

        public void setRuleName(String ruleName) {
            this.ruleName = ruleName;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof NemoInference that)) return false;
            return Objects.equals(getConclusion(), that.getConclusion());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getConclusion());
        }

        public void setRule(String rule) {
            this.rule = rule;
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

}
