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

    @Override
    public String toString() {
        StringBuilder mappingsStr = new StringBuilder();
        if (mappings != null && mappings.getContent() != null) {
            mappingsStr.append("[");
            for (Mapping mapping : mappings.getContent()) {
                mappingsStr.append(mapping.toString()).append(", ");
            }
            if (!mappings.getContent().isEmpty()) {
                mappingsStr.setLength(mappingsStr.length() - 2); // remove last comma and space
            }
            mappingsStr.append("]");
        } else {
            mappingsStr.append("null");
        }
        return "FacetedMappingResponse{" +
                "facets=" + facets +
                ", mappings=" + mappingsStr +
                '}';
    }
}