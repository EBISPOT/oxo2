package uk.ac.ebi.spot.oxo.backend.controller.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import uk.ac.ebi.spot.oxo.model.sssom.InferenceType;

import java.util.List;

/**
 * Batch cross-ontology mapping request (ADR-0024): map an explicit list of input terms into target
 * ontologies. Distinct from {@link MappingSearchRequest} because the response also carries the inputs
 * that mapped to nothing — the unmapped report only this mode can give (OxO2 knows only mapped terms).
 */
@Schema(description = "Batch cross-ontology mapping: map a list of input terms to target ontologies.")
public class BatchMapRequest {

    @Schema(description = "Input terms to map (CURIEs, IRIs, or labels). Matched on the subject side.",
            example = "[\"DOID:9352\", \"MONDO:0005148\", \"diabetes\"]")
    private List<String> terms;

    @Schema(description = "Target ontologies (CURIE prefixes) to map into; empty/null = any ontology.",
            example = "[\"EFO\", \"MONDO\"]")
    private List<String> objectPrefixes;

    @Schema(description = "Restrict to these inference types; null/empty returns all types.")
    private List<InferenceType> inferenceType;

    @Schema(description = "Zero-based page index.", defaultValue = "0")
    private int page = 0;

    @Schema(description = "Page size (1–100).", defaultValue = "10")
    private int size = 10;

    @Schema(description = "Collapse same subject/predicate/object rows into one representative.",
            defaultValue = "true")
    private boolean groupBySpo = true;

    public List<String> getTerms() {
        return terms;
    }

    public void setTerms(List<String> terms) {
        this.terms = terms;
    }

    public List<String> getObjectPrefixes() {
        return objectPrefixes;
    }

    public void setObjectPrefixes(List<String> objectPrefixes) {
        this.objectPrefixes = objectPrefixes;
    }

    public List<InferenceType> getInferenceType() {
        return inferenceType;
    }

    public void setInferenceType(List<InferenceType> inferenceType) {
        this.inferenceType = inferenceType;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public boolean isGroupBySpo() {
        return groupBySpo;
    }

    public void setGroupBySpo(boolean groupBySpo) {
        this.groupBySpo = groupBySpo;
    }

    @Override
    public String toString() {
        return "BatchMapRequest{" +
                "terms=" + (terms == null ? 0 : terms.size()) + " terms" +
                ", objectPrefixes=" + objectPrefixes +
                ", inferenceType=" + inferenceType +
                ", page=" + page +
                ", size=" + size +
                ", groupBySpo=" + groupBySpo +
                '}';
    }
}
