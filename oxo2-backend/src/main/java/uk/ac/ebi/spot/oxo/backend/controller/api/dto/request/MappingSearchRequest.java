package uk.ac.ebi.spot.oxo.backend.controller.api.dto.request;

import uk.ac.ebi.spot.oxo.model.sssom.InferenceType;
import uk.ac.ebi.spot.oxo.model.sssom.MappingEnum;

import java.util.List;

public class MappingSearchRequest {

    private List<String> queries;

    private List<MappingEnum> queryFields;
    private List<MappingEnum> fieldList;

    private List<SortedField> sortedFields;

    private int distance;

    private int page = 1;
    private int size = 10;

    private List<ColumnFilter> columnFilters;

    private List<String> mappingSetIds;

    private List<FieldQuery> advancedFieldQueries;

    // Multi-select inference-type filter (ADR-0011): null/empty = all types; otherwise restrict to
    // the listed types (ASSERTED / SSSOM_INFERENCE). Replaces the old tri-state
    // boolean `inferred`.
    private List<InferenceType> inferenceType;

    // Collapse same-SPO mappings into one row via Solr result grouping (ADR-0013). Default false;
    // the normal/inferences result tables opt in, the Advanced tab stays flat.
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

    public static class ColumnFilter {
        private String id;
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
