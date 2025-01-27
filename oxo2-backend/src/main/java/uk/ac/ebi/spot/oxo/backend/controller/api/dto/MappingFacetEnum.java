package uk.ac.ebi.spot.oxo.backend.controller.api.dto;

import java.util.Optional;

public enum MappingFacetEnum {
    MAPPING_SETS("mapping_sets", Optional.of("mapping_set_id"), Optional.empty()),
    ONTOLOGIES("ontologies", Optional.empty(), Optional.of("object_id_prefix,subject_id_prefix"));

    private final String name;
    private final Optional<String> query;
    private final Optional<String> pivot;

    MappingFacetEnum(String name, Optional<String> query, Optional<String> pivot) {
        this.name = name;
        this.query = query;
        this.pivot = pivot;
    }

    public String getName() {
        return name;
    }

    public Optional<String> getQuery() {
        return query;
    }

    public Optional<String> getPivot() {
        return pivot;
    }
}
