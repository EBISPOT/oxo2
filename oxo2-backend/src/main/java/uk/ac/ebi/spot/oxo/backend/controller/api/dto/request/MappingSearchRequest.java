package uk.ac.ebi.spot.oxo.backend.controller.api.dto.request;

import uk.ac.ebi.spot.oxo.model.sssom.MappingEnum;

import java.util.List;
import java.util.Set;

public class MappingSearchRequest {

    private List<String> queries;

    private List<MappingEnum> queryFields;
    private List<MappingEnum> fieldList;

    private List<SortedField> sortedFields;

    private int distance;

    private Set<MappingFacetEnum> facets;
    private int page = 1;
    private int size = 10;


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

    public Set<MappingFacetEnum> getFacets() {
        return facets;
    }

    public void setFacets(Set<MappingFacetEnum> facets) {
        this.facets = facets;
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

}
