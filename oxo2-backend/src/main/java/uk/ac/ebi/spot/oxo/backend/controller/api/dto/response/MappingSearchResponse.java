package uk.ac.ebi.spot.oxo.backend.controller.api.dto.response;

import org.springframework.data.domain.Page;
import uk.ac.ebi.spot.oxo.model.sssom.Mapping;

public class MappingSearchResponse {
    private Page<Mapping> mappings;

    public MappingSearchResponse(Page<Mapping> mappings) {
        this.mappings = mappings;
    }

    public Page<Mapping> getMappings() {
        return mappings;
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
        return "MappingSearchResponse{" +
                "mappings=" + mappingsStr +
                '}';
    }
}
