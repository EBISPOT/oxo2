package uk.ac.ebi.spot.oxo.model.sssom;

import java.util.*;

/**
 * InferredMappings are considered to be unique based on their conclusion.
 *
 * @Todo: Store asserted mappings in a separate field for easy access. This may be valuable for users as it can help to determine the
 * veracity of inferred mappings.
 * Also include mapping_set_id to allow inclusion/exclusion of inferred mappings the are derived from specific mappings sets.
 */

public class InferredMapping {

    private EntityReference mappingJustification;
    private String mappingTool;
    private Optional<PredicateModifierEnum> predicateModifier = Optional.empty();
    private Uri objectIRI;
    private Uri predicateIRI;
    private Uri subjectIRI;
    private int distance = 1;

    private Optional<ChainRuleApplications> chainRuleApplications = Optional.empty();



    public EntityReference getMappingJustification() {
        return mappingJustification;
    }

    public void setMappingJustification(EntityReference mappingJustification) {
        this.mappingJustification = mappingJustification;
    }

    public String getMappingTool() {
        return mappingTool;
    }

    public void setMappingTool(String mappingTool) {
        this.mappingTool = mappingTool;
    }

    public Uri getObjectIRI() {
        return objectIRI;
    }

    public void setObjectIRI(Uri objectIRI) {
        this.objectIRI = objectIRI;
    }

    public Uri getPredicateIRI() {
        return predicateIRI;
    }

    public void setPredicateIRI(Uri predicateIRI) {
        this.predicateIRI = predicateIRI;
    }

    public Optional<PredicateModifierEnum> getPredicateModifier() {
        return predicateModifier;
    }

    public void setPredicateModifier(Optional<PredicateModifierEnum> predicateModifier) {
        this.predicateModifier = predicateModifier;
    }

    public Uri getSubjectIRI() {
        return subjectIRI;
    }

    public void setSubjectIRI(Uri subjectIRI) {
        this.subjectIRI = subjectIRI;
    }

    public Optional<ChainRuleApplications> getChainRuleApplications() {
        return chainRuleApplications;
    }

    public void setChainRuleApplications(Optional<ChainRuleApplications> chainRuleApplications) {
        this.chainRuleApplications = chainRuleApplications;
    }

    public boolean isMappingToSelf() {
        boolean result = false;
        if (objectIRI.compareTo(subjectIRI) == 0) {
            result = true;
        }
        return result;
    }

    public String getAsConclusion() {
        StringBuilder conclusion = new StringBuilder();
        conclusion.append("(");
        conclusion.append(subjectIRI.asStringIRI()).append(", ");
        conclusion.append(predicateIRI.asStringIRI()).append(", ");
        conclusion.append(objectIRI.asStringIRI());
        conclusion.append(")");
        return conclusion.toString();
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InferredMapping mapping = (InferredMapping) o;
        return Objects.equals(subjectIRI, mapping.subjectIRI) &&
               Objects.equals(predicateIRI, mapping.predicateIRI) &&
               Objects.equals(objectIRI, mapping.objectIRI);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subjectIRI, predicateIRI, objectIRI);
    }

    @Override
    public String toString() {
        return "InferredMapping{" +
                "objectIRI=" + objectIRI +
                ", predicateIRI=" + predicateIRI +
                ", subjectIRI=" + subjectIRI +
                '}';
    }

    public int getDistance() {
        return distance;
    }

    public void setDistance(int distance) {
        this.distance = distance;
    }

    /**
     * Represents a hierarchical list of 1 or more chain rule applications.
     * @Todo: Remove spaces and . from conclusions and premises.
     */
    public static class ChainRuleApplications {
        private String conclusion;
        private Optional<ChainRulesEnum> chainRule;
        private List<InferredMapping> premises;

        public ChainRuleApplications(String conclusion, Optional<ChainRulesEnum> chainRule) {
            this.conclusion = conclusion;
            this.chainRule = chainRule;
        }

        public Optional<ChainRulesEnum> getChainRule() {
            return chainRule;
        }

        public List<InferredMapping> getPremises() {
            return premises;
        }

        public void setPremises(List<InferredMapping> premises) {
            this.premises = premises;
        }

        public List<String> getAsPremises() {
            List<String> asPremises = new ArrayList<>();
            for (InferredMapping premise : premises) {
                asPremises.add(premise.getAsConclusion());
            }
            return asPremises;
        }

        @Override
        public String toString() {
            return "ChainRuleApplications{" +
                    "chainRule=" + chainRule +
                    ", conclusion='" + conclusion + '\'' +
                    ", premises=" + premises +
                    '}';
        }
    }
}
