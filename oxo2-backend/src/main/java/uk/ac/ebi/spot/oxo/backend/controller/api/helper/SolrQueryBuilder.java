package uk.ac.ebi.spot.oxo.backend.controller.api.helper;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.util.ClientUtils;
import static org.apache.solr.common.params.CommonParams.*;

import org.apache.solr.common.params.CommonParams;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.MappingFacetEnum;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.MappingSearchRequest;
import uk.ac.ebi.spot.oxo.model.sssom.MappingEnum;

import java.util.List;
import java.util.Set;

import static org.apache.solr.common.params.DisMaxParams.QF;
import static uk.ac.ebi.spot.oxo.backend.controller.api.helper.SolrConstants.DEF_TYPE;
import static uk.ac.ebi.spot.oxo.backend.controller.api.helper.SolrConstants.EDISMAX;

public class SolrQueryBuilder {

    public static SolrQuery buildSolrQuery(MappingSearchRequest mappingSearchRequest, Pageable pageable) {



        SolrQuery solrQuery = new SolrQuery();

        solrQuery.setStart((int) pageable.getOffset());
        solrQuery.setRows(pageable.getPageSize());

        solrQuery.set(DEF_TYPE, EDISMAX);
        solrQuery.setQuery(constructQuery(mappingSearchRequest.getQueries()));
        solrQuery.set(QF, constructQueryFields(mappingSearchRequest.getQueryFields()));
        solrQuery.setFields(constructFieldList(mappingSearchRequest.getFieldList()));
        solrQuery = configureFacets(solrQuery, mappingSearchRequest.getFacets());

        return solrQuery;
    }

    private static String constructQuery(List<String> queries) {
        StringBuilder query = new StringBuilder();

        for (String q : queries) {
            if (query.length() > 0) {
                query.append(" OR ");
            }
            query.append(ClientUtils.escapeQueryChars(q));
        }

        return query.toString();
    }

    private static String[] constructQueryFields(List<MappingEnum> queryFields) {
        String[] fields = new String[queryFields.size()];
        queryFields.forEach(f -> fields[queryFields.indexOf(f)] = f.getField());
        return fields;
    }

    private static String[] constructFieldList(List<MappingEnum> fieldList) {
        String[] fields = new String[fieldList.size()];
        fieldList.forEach(f -> fields[fieldList.indexOf(f)] = f.getField());
        return fields;
    }

    private static SolrQuery configureFacets(SolrQuery solrQuery, Set<MappingFacetEnum> facets) {
        facets.forEach(f -> {
            if (f.getQuery().isPresent())
                solrQuery.addFacetField(f.getQuery().get());
            if (f.getPivot().isPresent())
                solrQuery.addFacetPivotField(f.getPivot().get());
        });
        return solrQuery;
    }

}
