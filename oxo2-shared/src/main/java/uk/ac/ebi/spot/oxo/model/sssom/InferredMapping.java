package uk.ac.ebi.spot.oxo.model.sssom;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * InferredMappings are considered to be unique based on their conclusion.
 *
 *
 */

public class InferredMapping {

    private Optional<uk.ac.ebi.spot.oxo.model.sssom.Double> confidence = Optional.empty();
    private Optional<EntityReference> mappingJustification = Optional.empty();
    private Optional<String> mappingTool = Optional.empty();
    private Optional<EntityReference> objectId = Optional.empty();
    private Optional<String> objectLabel = Optional.empty();
    private Optional<EntityReference> predicateId = Optional.empty();
    private Optional<String> predicateLabel = Optional.empty();
    private Optional<PredicateModifierEnum> predicateModifier = Optional.empty();
    private Optional<EntityReference> subjectId = Optional.empty();
    private Optional<String> subjectLabel = Optional.empty();
    private UUID mappingId;
    private Optional<String> objectIdPrefix = Optional.empty();
    private Optional<Uri> objectIRI = Optional.empty();
    private Optional<String> predicateIdPrefix = Optional.empty();
    private Optional<Uri> predicateIRI = Optional.empty();
    private Optional<String> subjectIdPrefix = Optional.empty();
    private Optional<Uri> subjectIRI = Optional.empty();

    private Optional<ChainRuleApplications> chainRuleApplications = Optional.empty();

    public Optional<Double> getConfidence() {
        return confidence;
    }

    public void setConfidence(Optional<Double> confidence) {
        this.confidence = confidence;
    }

    public UUID getMappingId() {
        return mappingId;
    }

    public void setMappingId(UUID mappingId) {
        this.mappingId = mappingId;
    }

    public Optional<EntityReference> getMappingJustification() {
        return mappingJustification;
    }

    public void setMappingJustification(Optional<EntityReference> mappingJustification) {
        this.mappingJustification = mappingJustification;
    }

    public Optional<String> getMappingTool() {
        return mappingTool;
    }

    public void setMappingTool(Optional<String> mappingTool) {
        this.mappingTool = mappingTool;
    }

    public Optional<EntityReference> getObjectId() {
        return objectId;
    }

    public void setObjectId(Optional<EntityReference> objectId) {
        this.objectId = objectId;
    }

    public Optional<String> getObjectIdPrefix() {
        return objectIdPrefix;
    }

    public void setObjectIdPrefix(Optional<String> objectIdPrefix) {
        this.objectIdPrefix = objectIdPrefix;
    }

    public Optional<Uri> getObjectIRI() {
        return objectIRI;
    }

    public void setObjectIRI(Optional<Uri> objectIRI) {
        this.objectIRI = objectIRI;
    }

    public Optional<String> getObjectLabel() {
        return objectLabel;
    }

    public void setObjectLabel(Optional<String> objectLabel) {
        this.objectLabel = objectLabel;
    }

    public Optional<EntityReference> getPredicateId() {
        return predicateId;
    }

    public void setPredicateId(Optional<EntityReference> predicateId) {
        this.predicateId = predicateId;
    }

    public Optional<String> getPredicateIdPrefix() {
        return predicateIdPrefix;
    }

    public void setPredicateIdPrefix(Optional<String> predicateIdPrefix) {
        this.predicateIdPrefix = predicateIdPrefix;
    }

    public Optional<Uri> getPredicateIRI() {
        return predicateIRI;
    }

    public void setPredicateIRI(Optional<Uri> predicateIRI) {
        this.predicateIRI = predicateIRI;
    }

    public Optional<String> getPredicateLabel() {
        return predicateLabel;
    }

    public void setPredicateLabel(Optional<String> predicateLabel) {
        this.predicateLabel = predicateLabel;
    }

    public Optional<PredicateModifierEnum> getPredicateModifier() {
        return predicateModifier;
    }

    public void setPredicateModifier(Optional<PredicateModifierEnum> predicateModifier) {
        this.predicateModifier = predicateModifier;
    }


    public Optional<EntityReference> getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(Optional<EntityReference> subjectId) {
        this.subjectId = subjectId;
    }

    public Optional<String> getSubjectIdPrefix() {
        return subjectIdPrefix;
    }

    public void setSubjectIdPrefix(Optional<String> subjectIdPrefix) {
        this.subjectIdPrefix = subjectIdPrefix;
    }

    public Optional<Uri> getSubjectIRI() {
        return subjectIRI;
    }

    public void setSubjectIRI(Optional<Uri> subjectIRI) {
        this.subjectIRI = subjectIRI;
    }

    public Optional<String> getSubjectLabel() {
        return subjectLabel;
    }

    public void setSubjectLabel(Optional<String> subjectLabel) {
        this.subjectLabel = subjectLabel;
    }

    public Optional<ChainRuleApplications> getChainRuleApplications() {
        return chainRuleApplications;
    }

    public void setChainRuleApplications(Optional<ChainRuleApplications> chainRuleApplications) {
        this.chainRuleApplications = chainRuleApplications;
    }

    public boolean isMappingToSelf() {
        boolean result = false;
        if (subjectIRI.isPresent() && objectIRI.isPresent() && objectIRI.get().compareTo(subjectIRI.get())==0) {
            result = true;
        }
        return result;
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
                "chainRuleApplications=" + chainRuleApplications +
                '}';
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

        public String getConclusion() {
            return conclusion;
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
