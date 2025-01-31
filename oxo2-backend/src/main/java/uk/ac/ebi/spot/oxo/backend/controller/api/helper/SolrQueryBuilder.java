package uk.ac.ebi.spot.oxo.backend.controller.api.helper;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.util.ClientUtils;

import org.springframework.data.domain.Pageable;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.request.MappingFacetEnum;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.request.MappingSearchRequest;
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
        if (fieldList == null || fieldList.isEmpty()) {
            return new String[]{
                    MappingEnum.SUBJECT_ID.getField(),
                    MappingEnum.SUBJECT_ID_PREFIX.getField(),
                    MappingEnum.PREDICATE_ID.getField(),
                    MappingEnum.OBJECT_ID.getField(),
                    MappingEnum.OBJECT_ID_PREFIX.getField(),
                    MappingEnum.MAPPING_JUSTIFICATION.getField()};
        }
        String[] fields = new String[fieldList.size()];
        fieldList.forEach(f -> {
            if (f != null)
                fields[fieldList.indexOf(f)] = f.getField();
        });
        return fields;
    }

    private static SolrQuery configureFacets(SolrQuery solrQuery, Set<MappingFacetEnum> facets) {
        facets.forEach(f ->  solrQuery.addFacetField(f.getValue()));
        return solrQuery;
    }

}
