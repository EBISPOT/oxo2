package uk.ac.ebi.spot.oxo.model.sssom;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

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

    private static final Logger logger = LoggerFactory.getLogger(InferredMapping.class);

    /**
     * The mapper behind the four JSON round-trip helpers below. Jackson 3 mappers are immutable
     * and thread-safe, so one shared instance replaces the four identical per-call mappers this
     * class used to build. Optional support is built into Jackson 3, so there is no Jdk8Module
     * left to register.
     */
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .changeDefaultPropertyInclusion(inclusion ->
                    inclusion.withValueInclusion(JsonInclude.Include.NON_NULL))
            .build();

    @JsonProperty(MAPPING_JUSTIFICATION)
    private Optional<EntityReference> mappingJustification;
    @JsonProperty(MAPPING_TOOL)
    private Optional<String> mappingTool;
    @JsonProperty(MAPPING_SET_ID)
    private String mappingSetId;
    @JsonProperty(MAPPING_ID)
    private String mappingId;
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

    @JsonProperty(CHAIN_RULE_APPLICATIONS)
    private Optional<ChainRuleApplications> chainRuleApplications = Optional.empty();

    /**
     * The full set of asserted mappings whose corpus quads share this leaf's exact
     * subject/predicate/object IRIs — the same triple asserted in more than one set (ADR-0049).
     * Set only on an ASSERTED leaf, only when duplicates exist, and always includes this leaf
     * itself, sorted by mapping_id. Nemo's trace carries whichever duplicate its derivation
     * happened to use; since the duplicates are indistinguishable premises, they are all equally
     * the evidence, and the explanation stage expands the leaf to this list when collecting
     * asserted evidence. Transient — never serialised into the explanation chain, which keeps a
     * single canonical leaf per premise.
     */
    private List<InferredMapping> equivalentAssertedLeaves;

    @JsonIgnore
    public List<InferredMapping> getEquivalentAssertedLeaves() {
        return equivalentAssertedLeaves;
    }

    public void setEquivalentAssertedLeaves(List<InferredMapping> equivalentAssertedLeaves) {
        this.equivalentAssertedLeaves = equivalentAssertedLeaves;
    }

    public Optional<EntityReference> getMappingJustification() {
        return mappingJustification;
    }

    public void setMappingJustification(EntityReference mappingJustification) {
        this.mappingJustification = Optional.of(mappingJustification);
    }

    public Optional<String> getMappingTool() {
        return mappingTool;
    }

    public void setMappingTool(String mappingTool) {
        this.mappingTool = Optional.of(mappingTool);
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

    public String getMappingId() {
        return mappingId;
    }

    public void setMappingId(String mappingId) {
        this.mappingId = mappingId;
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
        this.setMappingId(mapping.mappingId() == null ? null : mapping.mappingId().toString());
        mapping.subjectId().ifPresent(e -> this.setSubjectId(e.getDataAsString()));
        mapping.subjectLabel().ifPresent(this::setSubjectLabel);
        mapping.predicateId().ifPresent(e -> this.setPredicateId(e.getDataAsString()));
        mapping.predicateLabel().ifPresent(this::setPredicateLabel);
        mapping.objectId().ifPresent(e -> this.setObjectId(e.getDataAsString()));
        mapping.objectLabel().ifPresent(this::setObjectLabel);

        return this;
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
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class ChainRuleApplications {
        @JsonProperty(CHAIN_RULE)
        private Optional<ChainRulesEnum> chainRule;
        @JsonProperty(PREMISES)
        private List<InferredMapping> premises;

        public ChainRuleApplications() {
        }

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

    /**
     * Serializes a list of InferredMapping objects to a JSON string.
     */
    public static Optional<String> asJSONString(List<InferredMapping> inferredMappings) {
        try {
            return Optional.of(JSON.writeValueAsString(inferredMappings));
        } catch (JacksonException jpe) {
            logger.error("Error writing to JSON: inferredMappings = {}.", inferredMappings, jpe);
            return Optional.empty();
        }
    }

    public static Optional<String> asJSONString(InferredMapping inferredMapping) {
        try {
            return Optional.of(JSON.writeValueAsString(inferredMapping));
        } catch (JacksonException jpe) {
            logger.error("Error writing to JSON: inferredMappings = {}.", inferredMapping, jpe);
            return Optional.empty();
        }
    }

    /**
     * Deserializes a JSON string to a list of InferredMapping objects.
     */
    public static List<InferredMapping> fromStringAsList(String jsonString) {
        try {
            return JSON.readValue(
                jsonString,
                JSON.getTypeFactory().constructCollectionType(List.class, InferredMapping.class)
            );
        } catch (JacksonException e) {
            logger.error("Error reading from JSON: jsonString = {}.", jsonString, e);
            return new ArrayList<>();
        }
    }

    public static Optional<InferredMapping> fromStringAsObject(String jsonString) {
        try {
            InferredMapping mapping = JSON.readValue(
                    jsonString,
                    JSON.constructType(InferredMapping.class)
            );
            return Optional.of(mapping);
        } catch (JacksonException e) {
            logger.error("Error reading from JSON: jsonString = {}.", jsonString, e);
            return Optional.empty();
        }
    }
}