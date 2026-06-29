package uk.ac.ebi.spot.oxo.backend.controller.api.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

/**
 * OxO v1 {@code /api/search} request, reproduced for wire compatibility (ADR-0004 / ADR-0024). Field
 * names match v1 exactly so existing v1 clients (form-encoded or JSON) bind unchanged.
 */
@Schema(description = "OxO v1 batch-search request (wire-compatible).")
public class V1MappingSearchRequest {

    @Schema(description = "Input identifiers to map (CURIEs or IRIs), matched on the subject side.")
    private List<String> ids = new ArrayList<>();

    @Schema(description = "Source datasource prefix of the inputs (e.g. DOID).")
    private String inputSource;

    @Schema(description = "Target datasource prefixes to map into (e.g. EFO, MONDO).")
    private List<String> mappingTarget = new ArrayList<>();

    @Schema(description = "Mapping-source prefixes (v1). Accepted for compatibility; not applied as a "
            + "filter in OxO2 (no equivalent queryable provenance prefix).")
    private List<String> mappingSource = new ArrayList<>();

    @Schema(description = "Mapping distance (hops) in v1. OxO2 maps 1 → asserted only; any other value "
            + "(incl. -1) → asserted plus inferred, since the closure is precomputed (ADR-0024).",
            defaultValue = "1")
    private int distance = 1;

    public List<String> getIds() {
        return ids;
    }

    public void setIds(List<String> ids) {
        this.ids = ids;
    }

    public String getInputSource() {
        return inputSource;
    }

    public void setInputSource(String inputSource) {
        this.inputSource = inputSource;
    }

    public List<String> getMappingTarget() {
        return mappingTarget;
    }

    public void setMappingTarget(List<String> mappingTarget) {
        this.mappingTarget = mappingTarget;
    }

    public List<String> getMappingSource() {
        return mappingSource;
    }

    public void setMappingSource(List<String> mappingSource) {
        this.mappingSource = mappingSource;
    }

    public int getDistance() {
        return distance;
    }

    public void setDistance(int distance) {
        this.distance = distance;
    }

    @Override
    public String toString() {
        return "V1MappingSearchRequest{ids=" + (ids == null ? 0 : ids.size()) + " ids"
                + ", inputSource=" + inputSource + ", mappingTarget=" + mappingTarget
                + ", mappingSource=" + mappingSource + ", distance=" + distance + '}';
    }
}
