package uk.ac.ebi.spot.oxo.backend.service.helper;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.request.FieldQuery;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.request.MappingFacetEnum;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.request.MappingSearchRequest;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.request.MappingSearchRequest.ColumnFilter;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.request.SortedField;
import uk.ac.ebi.spot.oxo.model.sssom.MappingEnum;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SolrQueryBuilderTest {

    private static final Pageable PAGE_OF_TEN = PageRequest.of(0, 10);
    private static final String INJECTION_PAYLOAD = "a\" OR *:* OR x";

    private static MappingSearchRequest baseRequest() {
        MappingSearchRequest request = new MappingSearchRequest();
        request.setColumnFilters(Collections.emptyList());
        request.setFacets(EnumSet.noneOf(MappingFacetEnum.class));
        return request;
    }

    private static String labelNgram(MappingEnum labelField) {
        return labelField.getField() + "_ngram";
    }

    private static String labelStr(MappingEnum labelField) {
        return labelField.getField() + "_str";
    }

    // ---------- column filter escaping (security regression) ----------

    @Test
    void labelFieldColumnFilterValueIsEscaped() {
        MappingSearchRequest request = baseRequest();
        request.setColumnFilters(List.of(
                new ColumnFilter(MappingEnum.SUBJECT_LABEL.getField(), INJECTION_PAYLOAD)));

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        String expected = labelNgram(MappingEnum.SUBJECT_LABEL) + ":*"
                + ClientUtils.escapeQueryChars(INJECTION_PAYLOAD) + "*";
        assertThat(solrQuery.getFilterQueries()).containsExactly(expected);
        assertThat(solrQuery.getFilterQueries()[0]).doesNotContain(INJECTION_PAYLOAD);
    }

    @Test
    void nonLabelColumnFilterValueIsEscaped() {
        MappingSearchRequest request = baseRequest();
        request.setColumnFilters(List.of(
                new ColumnFilter(MappingEnum.SUBJECT_ID.getField(), INJECTION_PAYLOAD)));

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        String expected = MappingEnum.SUBJECT_ID.getField() + ":*"
                + ClientUtils.escapeQueryChars(INJECTION_PAYLOAD) + "*";
        assertThat(solrQuery.getFilterQueries()).containsExactly(expected);
    }

    @Test
    void labelFieldColumnFilterPreservesWrappingWildcards() {
        MappingSearchRequest request = baseRequest();
        request.setColumnFilters(List.of(
                new ColumnFilter(MappingEnum.OBJECT_LABEL.getField(), "foo*bar")));

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        String fq = solrQuery.getFilterQueries()[0];
        assertThat(fq).startsWith(labelNgram(MappingEnum.OBJECT_LABEL) + ":*");
        assertThat(fq).endsWith("*");
        // user-supplied * becomes \* — the only un-escaped wildcards are the wrapping ones
        assertThat(fq).contains(ClientUtils.escapeQueryChars("foo*bar"));
        assertThat(fq).doesNotContain("foo*bar");
    }

    // ---------- mapping_set_id filter ----------

    @Test
    void mappingSetIdFilterEscapesEachId() {
        MappingSearchRequest request = baseRequest();
        request.setMappingSetIds(List.of("normal-id", "weird\"id"));

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        String field = MappingEnum.MAPPING_SET_ID.getField();
        String expected = "(" + field + ":\"" + ClientUtils.escapeQueryChars("normal-id") + "\""
                + " OR " + field + ":\"" + ClientUtils.escapeQueryChars("weird\"id") + "\")";
        assertThat(solrQuery.getFilterQueries()).containsExactly(expected);
    }

    @Test
    void mappingSetIdFilterSkipsBlankIds() {
        MappingSearchRequest request = baseRequest();
        request.setMappingSetIds(Arrays.asList("kept", "", "  ", null));

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        String field = MappingEnum.MAPPING_SET_ID.getField();
        String expected = "(" + field + ":\"" + ClientUtils.escapeQueryChars("kept") + "\")";
        assertThat(solrQuery.getFilterQueries()).containsExactly(expected);
    }

    @Test
    void mappingSetIdFilterNullProducesNoExtraFq() {
        MappingSearchRequest request = baseRequest();
        request.setMappingSetIds(null);

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        assertThat(solrQuery.getFilterQueries()).isEmpty();
    }

    // ---------- dispatch precedence ----------

    @Test
    void advancedQueryPathTakesPrecedenceOverLegacy() {
        MappingSearchRequest request = baseRequest();
        request.setAdvancedFieldQueries(List.of(
                new FieldQuery(MappingEnum.SUBJECT_ID.getField(), "DOID:1")));
        request.setQueryFields(List.of(MappingEnum.SUBJECT_LABEL));
        request.setQueries(List.of("ignored"));

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        assertThat(solrQuery.get(SolrConstants.DEF_TYPE)).isNull();
        assertThat(solrQuery.getQuery()).contains(MappingEnum.SUBJECT_ID.getField() + ":\"")
                .doesNotContain("ignored");
    }

    @Test
    void legacyEdismaxPathSetsDefTypeAndQf() {
        MappingSearchRequest request = baseRequest();
        request.setQueryFields(List.of(MappingEnum.SUBJECT_LABEL, MappingEnum.OBJECT_LABEL));
        request.setQueries(List.of("alpha", "beta"));

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        assertThat(solrQuery.get(SolrConstants.DEF_TYPE)).isEqualTo(SolrConstants.EDISMAX);
        assertThat(solrQuery.getParams("qf")).containsExactlyInAnyOrder(
                MappingEnum.SUBJECT_LABEL.getField(),
                MappingEnum.OBJECT_LABEL.getField());
    }

    @Test
    void defaultPathClassifiesWhenNeitherAdvancedNorQueryFieldsSet() {
        MappingSearchRequest request = baseRequest();
        request.setQueries(List.of("diabetes"));

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        assertThat(solrQuery.get(SolrConstants.DEF_TYPE)).isNull();
        assertThat(solrQuery.getQuery())
                .contains(MappingEnum.SUBJECT_LABEL.getField() + ":\"diabetes\"")
                .contains(MappingEnum.OBJECT_LABEL.getField() + ":\"diabetes\"")
                .contains(MappingEnum.PREDICATE_LABEL.getField() + ":\"diabetes\"");
    }

    // ---------- classified-query (default) path ----------

    @Test
    void classifiedQueryRoutesIriToIriFields() {
        MappingSearchRequest request = baseRequest();
        request.setQueries(List.of("http://example.org/Foo"));

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        assertThat(solrQuery.getQuery())
                .contains(MappingEnum.SUBJECT_IRI.getField() + ":\"")
                .contains(MappingEnum.OBJECT_IRI.getField() + ":\"")
                .contains(MappingEnum.PREDICATE_IRI.getField() + ":\"")
                .doesNotContain(MappingEnum.SUBJECT_LABEL.getField() + ":\"");
    }

    @Test
    void classifiedQueryRoutesCurieToIdFields() {
        MappingSearchRequest request = baseRequest();
        request.setQueries(List.of("DOID:0014667"));

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        String escaped = ClientUtils.escapeQueryChars("DOID:0014667");
        assertThat(solrQuery.getQuery())
                .contains(MappingEnum.SUBJECT_ID.getField() + ":\"" + escaped + "\"")
                .contains(MappingEnum.OBJECT_ID.getField() + ":\"" + escaped + "\"")
                .contains(MappingEnum.PREDICATE_ID.getField() + ":\"" + escaped + "\"");
    }

    @Test
    void classifiedQueryRoutesFreeTextToLabelFields() {
        MappingSearchRequest request = baseRequest();
        request.setQueries(List.of("diabetes mellitus"));

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        String escaped = ClientUtils.escapeQueryChars("diabetes mellitus");
        assertThat(solrQuery.getQuery())
                .contains(MappingEnum.SUBJECT_LABEL.getField() + ":\"" + escaped + "\"")
                .contains(MappingEnum.OBJECT_LABEL.getField() + ":\"" + escaped + "\"")
                .contains(MappingEnum.PREDICATE_LABEL.getField() + ":\"" + escaped + "\"");
    }

    @Test
    void classifiedQueryEscapesInjectionPayload() {
        MappingSearchRequest request = baseRequest();
        request.setQueries(List.of(INJECTION_PAYLOAD));

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        String escaped = ClientUtils.escapeQueryChars(INJECTION_PAYLOAD);
        assertThat(solrQuery.getQuery()).contains("\"" + escaped + "\"");
        assertThat(solrQuery.getQuery()).doesNotContain("\"" + INJECTION_PAYLOAD + "\"");
    }

    @Test
    void classifiedQueryEmptyOrNullReturnsMatchAll() {
        MappingSearchRequest request = baseRequest();
        request.setQueries(Collections.emptyList());

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        assertThat(solrQuery.getQuery()).isEqualTo("*:*");
    }

    @Test
    void classifiedQuerySkipsNullAndBlankTerms() {
        MappingSearchRequest request = baseRequest();
        request.setQueries(Arrays.asList("kept", null, "  ", ""));

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        assertThat(solrQuery.getQuery())
                .contains(MappingEnum.SUBJECT_LABEL.getField() + ":\"kept\"");
        assertThat(solrQuery.getQuery()).doesNotContain("\"\"");
    }

    // ---------- advanced path ----------

    @Test
    void advancedQueryAndJoinsEscapedClauses() {
        MappingSearchRequest request = baseRequest();
        request.setAdvancedFieldQueries(List.of(
                new FieldQuery(MappingEnum.SUBJECT_ID.getField(), "DOID:1"),
                new FieldQuery(MappingEnum.OBJECT_LABEL.getField(), "weird\"label")));

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        String clause1 = "(" + MappingEnum.SUBJECT_ID.getField() + ":\""
                + ClientUtils.escapeQueryChars("DOID:1") + "\")";
        String clause2 = "(" + MappingEnum.OBJECT_LABEL.getField() + ":\""
                + ClientUtils.escapeQueryChars("weird\"label") + "\")";
        assertThat(solrQuery.getQuery()).isEqualTo(clause1 + " AND " + clause2);
    }

    @Test
    void advancedQueryWithUnknownFieldSkipsClause() {
        MappingSearchRequest request = baseRequest();
        request.setAdvancedFieldQueries(List.of(
                new FieldQuery(MappingEnum.SUBJECT_ID.getField(), "DOID:1"),
                new FieldQuery("not_a_real_field", "ignored")));

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        assertThat(solrQuery.getQuery())
                .startsWith("(" + MappingEnum.SUBJECT_ID.getField() + ":\"")
                .doesNotContain("not_a_real_field")
                .doesNotContain("ignored")
                .doesNotContain(" AND ");
    }

    @Test
    void advancedQueryEmptyOrAllSkippedReturnsMatchAll() {
        MappingSearchRequest request = baseRequest();
        request.setAdvancedFieldQueries(List.of(
                new FieldQuery(MappingEnum.SUBJECT_ID.getField(), "   "),
                new FieldQuery("not_a_real_field", "ignored")));

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        assertThat(solrQuery.getQuery()).isEqualTo("*:*");
    }

    // ---------- legacy edismax path ----------

    @Test
    void legacyEdismaxQueryEscapesAndOrJoinsTerms() {
        MappingSearchRequest request = baseRequest();
        request.setQueryFields(List.of(MappingEnum.SUBJECT_LABEL));
        request.setQueries(List.of("foo", "ba\"r"));

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        assertThat(solrQuery.getQuery())
                .isEqualTo(ClientUtils.escapeQueryChars("foo")
                        + " OR " + ClientUtils.escapeQueryChars("ba\"r"));
    }

    @Test
    void legacyEdismaxEmptyQueriesReturnMatchAll() {
        MappingSearchRequest request = baseRequest();
        request.setQueryFields(List.of(MappingEnum.SUBJECT_LABEL));
        request.setQueries(Collections.emptyList());

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        assertThat(solrQuery.getQuery()).isEqualTo("*:*");
        assertThat(solrQuery.get(SolrConstants.DEF_TYPE)).isEqualTo(SolrConstants.EDISMAX);
    }

    // ---------- sorting ----------

    @Test
    void sortingOnLabelFieldUsesStrVariant() {
        MappingSearchRequest request = baseRequest();
        SortedField sort = new SortedField();
        sort.setId(MappingEnum.SUBJECT_LABEL);
        sort.setDesc(false);
        request.setSortedFields(List.of(sort));

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        assertThat(solrQuery.getSorts()).hasSize(1);
        SolrQuery.SortClause clause = solrQuery.getSorts().get(0);
        assertThat(clause.getItem()).isEqualTo(labelStr(MappingEnum.SUBJECT_LABEL));
        assertThat(clause.getOrder()).isEqualTo(SolrQuery.ORDER.asc);
    }

    @Test
    void sortingOnNonLabelFieldUsesFieldDirectly() {
        MappingSearchRequest request = baseRequest();
        SortedField sort = new SortedField();
        sort.setId(MappingEnum.MAPPING_SET_ID);
        sort.setDesc(true);
        request.setSortedFields(List.of(sort));

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        assertThat(solrQuery.getSorts()).hasSize(1);
        SolrQuery.SortClause clause = solrQuery.getSorts().get(0);
        assertThat(clause.getItem()).isEqualTo(MappingEnum.MAPPING_SET_ID.getField());
        assertThat(clause.getOrder()).isEqualTo(SolrQuery.ORDER.desc);
    }

    // ---------- field list & facets ----------

    @Test
    void fieldListIncludesMinimalFieldsEvenWhenNull() {
        MappingSearchRequest request = baseRequest();
        request.setFieldList(null);

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        String fields = solrQuery.getFields();
        for (String minimal : MappingEnum.MINIMAL_LIST_OF_FIELDS) {
            assertThat(fields).contains(minimal);
        }
    }

    @Test
    void fieldListAddsUserRequestedFields() {
        MappingSearchRequest request = baseRequest();
        request.setFieldList(List.of(MappingEnum.AUTHOR_ID, MappingEnum.COMMENT));

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        assertThat(solrQuery.getFields())
                .contains(MappingEnum.AUTHOR_ID.getField())
                .contains(MappingEnum.COMMENT.getField());
    }

    @Test
    void facetsAreAddedToSolrQuery() {
        MappingSearchRequest request = baseRequest();
        request.setFacets(EnumSet.of(
                MappingFacetEnum.MAPPING_SET_ID, MappingFacetEnum.PREDICATE_ID));

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        assertThat(solrQuery.getFacetFields()).containsExactlyInAnyOrder(
                MappingFacetEnum.MAPPING_SET_ID.getValue(),
                MappingFacetEnum.PREDICATE_ID.getValue());
    }
}
