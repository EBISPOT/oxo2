package uk.ac.ebi.spot.oxo.model.sssom;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static uk.ac.ebi.spot.oxo.model.sssom.MappingConstants.*;

/**
 * InferredMappings are considered to be unique based on their conclusion.
 *
 * @Todo: Store asserted mappings in a separate field for easy access. This may be valuable for users as it can help to determine the
 * veracity of inferred mappings.
 * Also include mapping_set_id to allow inclusion/exclusion of inferred mappings the are derived from specific mappings sets.
 */

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class InferredMapping {

    @JsonProperty(MAPPING_JUSTIFICATION)
    private EntityReference mappingJustification;
    @JsonProperty(MAPPING_TOOL)
    private String mappingTool;
    @JsonProperty(MAPPING_SET_ID)
    private String mappingSetId;
    @JsonProperty(OBJECT_IRI)
    private Uri objectIRI;
    @JsonProperty(OBJECT_ID)
    private Optional<EntityReference> objectId;
    @JsonProperty(OBJECT_LABEL)
    private Optional<String> objectLabel;
    @JsonProperty(PREDICATE_IRI)
    private Uri predicateIRI;
    @JsonProperty(PREDICATE_ID)
    private Optional<EntityReference> predicateId;
    @JsonProperty(PREDICATE_LABEL)
    private Optional<String> predicateLabel;
    @JsonProperty(SUBJECT_IRI)
    private Uri subjectIRI;
    @JsonProperty(SUBJECT_ID)
    private Optional<EntityReference> subjectId;
    @JsonProperty(SUBJECT_LABEL)
    private Optional<String> subjectLabel;
    @JsonProperty(DISTANCE)
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

    public String getMappingSetId() {
        return mappingSetId;
    }

    public void setMappingSetId(String mappingSetId) {
        this.mappingSetId = mappingSetId;
    }

    public Optional<EntityReference> getObjectId() {
        return objectId;
    }

    public void setObjectId(String strObjectId) {
        if (strObjectId != null && !strObjectId.isBlank()) {
            EntityReference objectId = new EntityReference(strObjectId);
            this.objectId = Optional.of(objectId);
        }
    }

    public Optional<String> getObjectLabel() {
        return objectLabel;
    }

    public void setObjectLabel(String objectLabel) {
        if (objectLabel != null && !objectLabel.isBlank()) {
            this.objectLabel = Optional.of(objectLabel);
        }
    }

    public Optional<EntityReference> getPredicateId() {
        return predicateId;
    }

    public void setPredicateId(String strPredicateId) {
        if (strPredicateId != null && !strPredicateId.isBlank()) {
            EntityReference predicateId = new EntityReference(strPredicateId);
            this.predicateId = Optional.of(predicateId);
        }
    }

    public Optional<String> getPredicateLabel() {
        return predicateLabel;
    }

    public void setPredicateLabel(String predicateLabel) {
        if (predicateLabel != null && !predicateLabel.isBlank())
            this.predicateLabel = Optional.of(predicateLabel);
    }

    public Optional<EntityReference> getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(String strSubjectId) {
        if (strSubjectId != null && !strSubjectId.isBlank()) {
            EntityReference subjectId = new EntityReference(strSubjectId);
            this.subjectId = Optional.of(subjectId);
        }
    }

    public Optional<String> getSubjectLabel() {
        return subjectLabel;
    }

    public void setSubjectLabel(String subjectLabel) {
        if (subjectLabel != null && !subjectLabel.isBlank())
            this.subjectLabel = Optional.of(subjectLabel);
    }

    @JsonIgnore
    public boolean isMappingToSelf() {
        boolean result = false;
        if (objectIRI.compareTo(subjectIRI) == 0) {
            result = true;
        }
        return result;
    }

    public InferredMapping populateFromMapping(Mapping mapping) {
        mapping.mappingTool().ifPresent(this::setMappingTool);
        mapping.mappingJustification().ifPresent(this::setMappingJustification);
        this.setMappingSetId(mapping.mappingSetId().asStringIRI());
        mapping.subjectId().ifPresent(e -> this.setSubjectId(e.getDataAsString()));
        mapping.subjectLabel().ifPresent(this::setSubjectLabel);
        mapping.predicateId().ifPresent(e -> this.setPredicateId(e.getDataAsString()));
        mapping.predicateLabel().ifPresent(this::setPredicateLabel);
        mapping.objectId().ifPresent(e -> this.setObjectId(e.getDataAsString()));
        mapping.objectLabel().ifPresent(this::setObjectLabel);

        return this;
    }

    public static InferredMapping createFromMapping(Mapping mapping) {
        InferredMapping inferredMapping = new InferredMapping();
        mapping.subjectIRI().ifPresent(inferredMapping::setSubjectIRI);
        mapping.predicateIRI().ifPresent(inferredMapping::setPredicateIRI);
        mapping.objectIRI().ifPresent(inferredMapping::setObjectIRI);
        return inferredMapping.populateFromMapping(mapping);
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

    private static int compareChainRulesEnum(Optional<ChainRulesEnum> optionalChainRulesEnum1,
                                             Optional<ChainRulesEnum> optionalChainRulesEnum2) {

        if (optionalChainRulesEnum1.isEmpty() && optionalChainRulesEnum2.isPresent()) return -1;
        if (optionalChainRulesEnum1.isPresent() && optionalChainRulesEnum2.isEmpty()) return 1;
        if (optionalChainRulesEnum1.isEmpty() && optionalChainRulesEnum2.isEmpty()) return 0;
        return optionalChainRulesEnum1.get().compareTo(optionalChainRulesEnum2.get());
    }

    /**
     * Represents a hierarchical list of 1 or more chain rule applications.
     * @Todo: Remove spaces and . from conclusions and premises.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class ChainRuleApplications {
        @JsonProperty(CHAIN_RULE)
        private Optional<ChainRulesEnum> chainRule;
        @JsonProperty(PREMISES)
        private List<InferredMapping> premises;

        public ChainRuleApplications(Optional<ChainRulesEnum> chainRule) {
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

        @Override
        public String toString() {
            return "ChainRuleApplications{" +
                    "chainRule=" + chainRule +
                    ", premises=" + premises +
                    '}';
        }
    }
}