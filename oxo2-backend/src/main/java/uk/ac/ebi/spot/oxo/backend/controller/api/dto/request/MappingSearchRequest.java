package uk.ac.ebi.spot.oxo.backend.controller.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import uk.ac.ebi.spot.oxo.model.sssom.InferenceType;
import uk.ac.ebi.spot.oxo.model.sssom.MappingEnum;

import java.util.List;

@Schema(description = "Mapping-search request: free-text queries, filters, sorting and paging.")
public class MappingSearchRequest {

    @Schema(description = "Free-text query terms, OR-ed together and matched against `queryFields`.",
            example = "[\"diabetes\"]")
    private List<String> queries;

    @Schema(description = "Fields the free-text `queries` are matched against. Defaults to the "
            + "text-searchable fields when omitted.")
    private List<MappingEnum> queryFields;
    @Schema(description = "Fields to return for each mapping. Defaults to the minimal result set when omitted.")
    private List<MappingEnum> fieldList;

    @Schema(description = "Sort order, applied in list order (later entries break ties).")
    private List<SortedField> sortedFields;

    @Schema(description = "Reserved: maximum inference chain length to consider.")
    private int distance;

    @Schema(description = "Zero-based page index.", defaultValue = "1")
    private int page = 1;
    @Schema(description = "Page size (1–100).", defaultValue = "10")
    private int size = 10;

    @Schema(description = "Per-column \"contains\" filters. A non-blank filter on any predicate field "
            + "(`predicate_id`, `predicate_label`, `predicate_iri`, `predicate_modifier`) lifts the "
            + "default hiding of `rdfs:subClassOf` and `oboInOwl:hasDbXref`.")
    private List<ColumnFilter> columnFilters;

    @Schema(description = "Restrict results to these mapping-set ids (full IRIs).")
    private List<String> mappingSetIds;

    @Schema(description = "Advanced per-field queries (field id + value). Like column filters, a "
            + "non-blank predicate-field query lifts the default predicate hiding.")
    private List<FieldQuery> advancedFieldQueries;

    // Multi-select inference-type filter (ADR-0011): null/empty = all types; otherwise restrict to
    // the listed types (ASSERTED / SSSOM_INFERENCE). Replaces the old tri-state
    // boolean `inferred`.
    @Schema(description = "Restrict to these inference types; null/empty returns all types.")
    private List<InferenceType> inferenceType;

    // Collapse same-SPO mappings into one row via Solr result grouping (ADR-0013). Default false;
    // the normal/inferences result tables opt in, the Advanced tab stays flat.
    @Schema(description = "Collapse mappings that share the same subject/predicate/object into one "
            + "representative row.", defaultValue = "false")
    private boolean groupBySpo = false;


    public List<String> getQueries() {
        return queries;
    }

    public void setQueries(List<String> queries) {
        this.queries = queries;
    }

    public List<MappingEnum> getQueryFields() {
        return queryFields;
    }

    public void setQueryFields(List<MappingEnum> queryFields) {
        this.queryFields = queryFields;
    }

    public List<MappingEnum> getFieldList() {
        return fieldList;
    }

    public void setFieldList(List<MappingEnum> fieldList) {
        this.fieldList = fieldList;
    }

    public List<SortedField> getSortedFields() {
        return sortedFields;
    }

    public void setSortedFields(List<SortedField> sortedFields) {
        this.sortedFields = sortedFields;
    }

    public int getDistance() {
        return distance;
    }

    public void setDistance(int distance) {
        this.distance = distance;
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

    public List<ColumnFilter> getColumnFilters() {
        return columnFilters;
    }

    public void setColumnFilters(List<ColumnFilter> columnFilters) {
        this.columnFilters = columnFilters;
    }

    public List<String> getMappingSetIds() {
        return mappingSetIds;
    }

    public void setMappingSetIds(List<String> mappingSetIds) {
        this.mappingSetIds = mappingSetIds;
    }

    public List<FieldQuery> getAdvancedFieldQueries() {
        return advancedFieldQueries;
    }

    public void setAdvancedFieldQueries(List<FieldQuery> advancedFieldQueries) {
        this.advancedFieldQueries = advancedFieldQueries;
    }

    public List<InferenceType> getInferenceType() {
        return inferenceType;
    }

    public void setInferenceType(List<InferenceType> inferenceType) {
        this.inferenceType = inferenceType;
    }

    public boolean isGroupBySpo() {
        return groupBySpo;
    }

    public void setGroupBySpo(boolean groupBySpo) {
        this.groupBySpo = groupBySpo;
    }

    @Override
    public String toString() {
        return "MappingSearchRequest{" +
                "columnFilters=" + columnFilters +
                ", queries=" + queries +
                ", queryFields=" + queryFields +
                ", fieldList=" + fieldList +
                ", sortedFields=" + sortedFields +
                ", distance=" + distance +
                ", page=" + page +
                ", size=" + size +
                ", mappingSetIds=" + mappingSetIds +
                ", advancedFieldQueries=" + advancedFieldQueries +
                ", inferenceType=" + inferenceType +
                ", groupBySpo=" + groupBySpo +
                '}';
    }

    @Schema(description = "A \"contains\" filter on a single column.")
    public static class ColumnFilter {
        @Schema(description = "Solr field id to filter on.", example = "predicate_id")
        private String id;
        @Schema(description = "Value the column must contain.", example = "skos:exactMatch")
        private String value;

        public ColumnFilter(String id, String value) {
            this.id = id;
            this.value = value;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return "ColumnFilter{" +
                    "id='" + id + '\'' +
                    ", value='" + value + '\'' +
                    '}';
        }
    }
}
