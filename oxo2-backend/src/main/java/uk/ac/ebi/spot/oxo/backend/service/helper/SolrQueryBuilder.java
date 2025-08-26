package uk.ac.ebi.spot.oxo.backend.service.helper;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.request.MappingFacetEnum;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.request.MappingSearchRequest;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.request.SortedField;
import uk.ac.ebi.spot.oxo.model.sssom.MappingEnum;

import java.util.*;
import java.util.stream.Collectors;

import static org.apache.solr.common.params.DisMaxParams.QF;
import static uk.ac.ebi.spot.oxo.model.sssom.MappingEnum.MINIMAL_LIST_OF_FIELDS;

public class SolrQueryBuilder {

    private static final Logger logger = LoggerFactory.getLogger(SolrQueryBuilder.class);

    private static final Map<MappingEnum, String> textGeneralToDocValues = Map.of(
        MappingEnum.OBJECT_LABEL, textGeneralFieldAsString(MappingEnum.OBJECT_LABEL),
        MappingEnum.PREDICATE_LABEL, textGeneralFieldAsString(MappingEnum.PREDICATE_LABEL),
        MappingEnum.SUBJECT_LABEL, textGeneralFieldAsString(MappingEnum.SUBJECT_LABEL)
    );

    private static final Map<MappingEnum, String> textGeneralToNGram = Map.of(
        MappingEnum.OBJECT_LABEL, textGeneralFieldAsNGram(MappingEnum.OBJECT_LABEL),
        MappingEnum.PREDICATE_LABEL, textGeneralFieldAsNGram(MappingEnum.PREDICATE_LABEL),
        MappingEnum.SUBJECT_LABEL, textGeneralFieldAsNGram(MappingEnum.SUBJECT_LABEL)
    );

    private static String textGeneralFieldAsString(MappingEnum mappingEnum) {
        return mappingEnum.getField() + "_str";
    }

    private static String textGeneralFieldAsNGram(MappingEnum mappingEnum) {
        return mappingEnum.getField() + "_ngram";
    }

    public static SolrQuery buildSolrQuery(MappingSearchRequest mappingSearchRequest, Pageable pageable) {

        SolrQuery solrQuery = new SolrQuery();

        solrQuery.setStart((int) pageable.getOffset());
        solrQuery.setRows(pageable.getPageSize());

        solrQuery.set(SolrConstants.DEF_TYPE, SolrConstants.EDISMAX);
        solrQuery.setQuery(constructQuery(mappingSearchRequest.getQueries()));
        solrQuery.set(QF, constructQueryFields(mappingSearchRequest.getQueryFields()));
        solrQuery.setFields(constructFieldList(mappingSearchRequest.getFieldList()));
        solrQuery.setFilterQueries(constructFilterQueries(mappingSearchRequest.getColumnFilters()));
        solrQuery = configureFacets(solrQuery, mappingSearchRequest.getFacets());
        solrQuery = constructSortedFields(solrQuery, mappingSearchRequest);

        return solrQuery;
    }


    private static String[] constructFilterQueries(List<MappingSearchRequest.ColumnFilter> queryFilters) {
        List<String> filterQueriesList = queryFilters.stream()
                .map(f ->
                        textGeneralToNGram.containsKey(MappingEnum.fromString(f.getId()))
                                ?
                        textGeneralToNGram.get(MappingEnum.fromString(f.getId())) + ":*" + f.getValue() + "*"
                                :
                        MappingEnum.fromString(f.getId()).getField() + ":*" +
                                ClientUtils.escapeQueryChars(f.getValue()) + "*")
                .collect(Collectors.toList());
        return filterQueriesList.toArray(new String[filterQueriesList.size()]);
    }

    /**
     * Solr can only sort on docValue fields. Text_general fields (used for labels) are not docValue fields.
     * To enable sorting these fields are copied to _str fields that are docValue fields.
     *
     *
     * @param solrQuery
     * @param mappingSearchRequest
     * @return
     */
    private static SolrQuery constructSortedFields(SolrQuery solrQuery, MappingSearchRequest mappingSearchRequest) {
        if (mappingSearchRequest.getSortedFields() != null) {
            ClientUtils.escapeQueryChars(solrQuery.getQuery());
            for (SortedField sortedField : mappingSearchRequest.getSortedFields()) {
                solrQuery.addSort(
                        textGeneralToDocValues.containsKey(sortedField.getId()) ?
                            textGeneralToDocValues.get(sortedField.getId()) :
                            sortedField.getId().getField(),
                        sortedField.isDesc() == true ? SolrQuery.ORDER.desc : SolrQuery.ORDER.asc
                );
            }
        }
        return solrQuery;
    }

    /**
     * @Todo Only do text queries on text fields
     * @param queries
     * @return
     */
    private static String constructQuery(List<String> queries) {
        String query = null;

        for (String q : queries) {
            query =
                    Arrays.stream(MappingEnum.values())
                            .map(f -> "(" + f.getField() + ":\"" + q + "\" OR " + f.getField() + ":\"" +
                                    q.toUpperCase() + "\")")
                            .collect(Collectors.joining(" OR "));

        }
        logger.error("Query string: {}", query);
        return query;
    }

    private static String[] constructQueryFields(List<MappingEnum> queryFields) {
        String[] fields = new String[queryFields.size()];
        queryFields.forEach(f -> fields[queryFields.indexOf(f)] = f.getField());
        return fields;
    }

    private static String[] constructFieldList(List<MappingEnum> fieldList) {
        Set<String> fieldsSet = new HashSet<>();

        if (fieldList != null) {
            fieldList.forEach(f -> {
                if (f != null) {
                    fieldsSet.add(f.getField());
                }
            });
        }

        for (String minimalField : MINIMAL_LIST_OF_FIELDS) {
            fieldsSet.add(minimalField);
        }

        return fieldsSet.toArray(new String[0]);
    }

    private static SolrQuery configureFacets(SolrQuery solrQuery, Set<MappingFacetEnum> facets) {
        facets.forEach(f ->  solrQuery.addFacetField(f.getValue()));
        return solrQuery;
    }
}
