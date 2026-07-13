package uk.ac.ebi.spot.oxo.backend.controller.api.sssom.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Request body of {@code POST /api/sssom/entities} (ADR-0032), matching the reference API's
 * {@code SearchEntity}: the entities to look up, with optional justification / predicate filters.
 */
public class SearchEntityRequest {

    @Schema(description = "Entities to match, each a CURIE or full IRI. A mapping is returned when any "
            + "of these appears as its subject_id or object_id.", example = "[\"MONDO:0005148\"]")
    @JsonProperty("curies")
    private List<String> curies;

    @Schema(description = "Restrict to mappings with one of these mapping_justification values "
            + "(exact match). Absent/empty applies no justification filter.")
    @JsonProperty("mapping_justification")
    private List<String> mappingJustification;

    @Schema(description = "Restrict to mappings with one of these predicate_id values (CURIE or IRI). "
            + "Absent/empty applies no predicate filter.")
    @JsonProperty("predicate_id")
    private List<String> predicateId;

    public List<String> getCuries() {
        return curies;
    }

    public void setCuries(List<String> curies) {
        this.curies = curies;
    }

    public List<String> getMappingJustification() {
        return mappingJustification;
    }

    public void setMappingJustification(List<String> mappingJustification) {
        this.mappingJustification = mappingJustification;
    }

    public List<String> getPredicateId() {
        return predicateId;
    }

    public void setPredicateId(List<String> predicateId) {
        this.predicateId = predicateId;
    }
}
