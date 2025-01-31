package uk.ac.ebi.spot.oxo.backend.controller.api.dto.response;

import org.springframework.data.domain.Page;
import uk.ac.ebi.spot.oxo.model.sssom.Mapping;

import java.util.Map;
public class FacetedMappingResponse {
    private Page<Mapping> mappings;
    private Map<String, Map<String, Long>> facets;

    public FacetedMappingResponse(Page<Mapping> mappings, Map<String, Map<String, Long>> facets) {
        this.mappings = mappings;
        this.facets = facets;
    }

    public Page<Mapping> getMappings() {
        return mappings;
    }

    public Map<String, Map<String, Long>> getFacets() {
        return facets;
    }
}