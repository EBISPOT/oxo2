package uk.ac.ebi.spot.oxo.backend.service.helper;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.request.FieldQuery;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.request.MappingSearchRequest;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.request.MappingSearchRequest.ColumnFilter;
import uk.ac.ebi.spot.oxo.backend.controller.api.dto.request.SortedField;
import uk.ac.ebi.spot.oxo.model.sssom.FilterMatchType;
import uk.ac.ebi.spot.oxo.model.sssom.InferenceType;
import uk.ac.ebi.spot.oxo.model.sssom.LabelMatchType;
import uk.ac.ebi.spot.oxo.model.sssom.MappingEnum;
import uk.ac.ebi.spot.oxo.model.sssom.WeakPredicate;
import uk.ac.ebi.spot.oxo.model.sssom.MappingSetCategory;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.apache.solr.common.params.FacetParams.FACET_FIELD;
import static org.assertj.core.api.Assertions.assertThat;

class SolrQueryBuilderTest {

    private static final Pageable PAGE_OF_TEN = PageRequest.of(0, 10);
    private static final String INJECTION_PAYLOAD = "a\" OR *:* OR x";

    private static MappingSearchRequest baseRequest() {
        MappingSearchRequest request = new MappingSearchRequest();
        request.setColumnFilters(Collections.emptyList());
        return request;
    }

    private static String labelNgram(MappingEnum labelField) {
        return labelField.getField() + "_ngram";
    }

    /**
     * Mirrors the production split-on-whitespace, AND-of-substring-wildcards clause that
     * label-field column filters now produce, so multi-word values match labels
     * containing all of the words (see {@link SolrQueryBuilder}).
     */
    private static String ngramContainsAll(MappingEnum labelField, String value) {
        String field = labelNgram(labelField);
        return Arrays.stream(value.strip().split("\\s+"))
                .filter(word -> !word.isEmpty())
                .map(word -> field + ":*" + ClientUtils.escapeQueryChars(word) + "*")
                .collect(Collectors.joining(" AND ", "(", ")"));
    }

    private static String labelStr(MappingEnum labelField) {
        return labelField.getField() + "_str";
    }

    /** Case-insensitive exact-match label copy targeted by the EXACT_CASE_INSENSITIVE mode (ADR-0026). */
    private static String labelCi(MappingEnum labelField) {
        return labelField.getField() + "_ci";
    }

    /**
     * Reconstructs the default weak-predicate exclusion filter that every search without an explicit
     * predicate filter now carries (see {@link SolrQueryBuilder}). Independently mirrors the
     * production clause so these tests fail if either the excluded IRIs or the clause shape drift.
     */
    private static String weakPredicateExclusion() {
        return exclusionOf(
                "http://www.w3.org/2000/01/rdf-schema#subClassOf",
                "http://www.geneontology.org/formats/oboInOwl#hasDbXref");
    }

    /** The exclusion clause for exactly these IRIs — one ticked checkbox leaves only the other. */
    private static String exclusionOf(String... iris) {
        String field = MappingEnum.PREDICATE_IRI.getField();
        String excluded = Stream.of(iris)
                .map(iri -> field + ":\"" + ClientUtils.escapeQueryChars(iri) + "\"")
                .collect(Collectors.joining(" OR "));
        return "*:* -(" + excluded + ")";
    }

    // ---------- column filter escaping (security regression) ----------

    @Test
    void labelFieldColumnFilterValueIsEscaped() {
        MappingSearchRequest request = baseRequest();
        request.setColumnFilters(List.of(
                new ColumnFilter(MappingEnum.SUBJECT_LABEL.getField(), INJECTION_PAYLOAD)));

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        // The payload contains whitespace, so the label filter splits it into per-word
        // wildcard clauses — each word still escaped so the " OR *:* injection is inert.
        // A non-predicate filter does not bypass the default weak-predicate exclusion.
        String expected = ngramContainsAll(MappingEnum.SUBJECT_LABEL, INJECTION_PAYLOAD);
        assertThat(solrQuery.getFilterQueries())
                .containsExactlyInAnyOrder(expected, weakPredicateExclusion());
        assertThat(solrQuery.getFilterQueries()[0]).doesNotContain(INJECTION_PAYLOAD);
        assertThat(solrQuery.getFilterQueries()[0]).doesNotContain("\" OR *:*");
    }

    @Test
    void labelFieldColumnFilterSplitsMultiWordValueIntoAndedSubstrings() {
        MappingSearchRequest request = baseRequest();
        request.setColumnFilters(List.of(
                new ColumnFilter(MappingEnum.SUBJECT_LABEL.getField(), "CDISC Questionnaire")));

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        String ngram = labelNgram(MappingEnum.SUBJECT_LABEL);
        String expected = "(" + ngram + ":*CDISC* AND " + ngram + ":*Questionnaire*)";
        assertThat(solrQuery.getFilterQueries())
                .containsExactlyInAnyOrder(expected, weakPredicateExclusion());
    }

    @Test
    void labelFieldColumnFilterCollapsesSurroundingAndRepeatedWhitespace() {
        MappingSearchRequest request = baseRequest();
        request.setColumnFilters(List.of(
                new ColumnFilter(MappingEnum.OBJECT_LABEL.getField(), "  CDISC   Questionnaire  ")));

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        String ngram = labelNgram(MappingEnum.OBJECT_LABEL);
        String expected = "(" + ngram + ":*CDISC* AND " + ngram + ":*Questionnaire*)";
        assertThat(solrQuery.getFilterQueries())
                .containsExactlyInAnyOrder(expected, weakPredicateExclusion());
    }

    @Test
    void labelFieldColumnFilterSingleWordWrapsSingleClause() {
        MappingSearchRequest request = baseRequest();
        request.setColumnFilters(List.of(
                new ColumnFilter(MappingEnum.SUBJECT_LABEL.getField(), "leukemia")));

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        String expected = "(" + labelNgram(MappingEnum.SUBJECT_LABEL) + ":*leukemia*)";
        assertThat(solrQuery.getFilterQueries())
                .containsExactlyInAnyOrder(expected, weakPredicateExclusion());
    }

    @Test
    void nonLabelColumnFilterValueIsEscaped() {
        MappingSearchRequest request = baseRequest();
        request.setColumnFilters(List.of(
                new ColumnFilter(MappingEnum.SUBJECT_ID.getField(), INJECTION_PAYLOAD)));

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        String expected = MappingEnum.SUBJECT_ID.getField() + ":*"
                + ClientUtils.escapeQueryChars(INJECTION_PAYLOAD) + "*";
        assertThat(solrQuery.getFilterQueries())
                .containsExactlyInAnyOrder(expected, weakPredicateExclusion());
    }

    @Test
    void labelFieldColumnFilterPreservesWrappingWildcards() {
        MappingSearchRequest request = baseRequest();
        request.setColumnFilters(List.of(
                new ColumnFilter(MappingEnum.OBJECT_LABEL.getField(), "foo*bar")));

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        String fq = solrQuery.getFilterQueries()[0];
        // Single-word value (no whitespace) → one clause, wrapped in parens.
        assertThat(fq).startsWith("(" + labelNgram(MappingEnum.OBJECT_LABEL) + ":*");
        assertThat(fq).endsWith("*)");
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
        assertThat(solrQuery.getFilterQueries())
                .containsExactlyInAnyOrder(expected, weakPredicateExclusion());
    }

    @Test
    void mappingSetIdFilterSkipsBlankIds() {
        MappingSearchRequest request = baseRequest();
        request.setMappingSetIds(Arrays.asList("kept", "", "  ", null));

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        String field = MappingEnum.MAPPING_SET_ID.getField();
        String expected = "(" + field + ":\"" + ClientUtils.escapeQueryChars("kept") + "\")";
        assertThat(solrQuery.getFilterQueries())
                .containsExactlyInAnyOrder(expected, weakPredicateExclusion());
    }

    @Test
    void mappingSetIdFilterNullProducesNoExtraFq() {
        MappingSearchRequest request = baseRequest();
        request.setMappingSetIds(null);

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        // Only the default weak-predicate exclusion remains (no mapping-set filter).
        assertThat(solrQuery.getFilterQueries()).containsExactly(weakPredicateExclusion());
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

        // All paths now run under edismax so the inference ranking (ADR-0011) applies uniformly.
        assertThat(solrQuery.get(SolrConstants.DEF_TYPE)).isEqualTo(SolrConstants.EDISMAX);
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

        // All paths now run under edismax so the inference ranking (ADR-0011) applies uniformly.
        assertThat(solrQuery.get(SolrConstants.DEF_TYPE)).isEqualTo(SolrConstants.EDISMAX);
        // Default label-match mode is EXACT_CASE_INSENSITIVE (ADR-0026), so free text routes to the
        // subject_label_ci field — subject side only (ADR-0030).
        assertThat(solrQuery.getQuery())
                .contains(labelCi(MappingEnum.SUBJECT_LABEL) + ":\"diabetes\"")
                .doesNotContain(labelCi(MappingEnum.OBJECT_LABEL))
                .doesNotContain(labelCi(MappingEnum.PREDICATE_LABEL));
    }

    // ---------- classified-query (default) path ----------

    @Test
    void classifiedQueryRoutesIriToSubjectIriFieldOnly() {
        MappingSearchRequest request = baseRequest();
        request.setQueries(List.of("http://example.org/Foo"));

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        // Subject side only (ADR-0030) — the query must not fan out to the object/predicate fields.
        // predicate_iri still appears in the weak-predicate exclusion fq, but never in q.
        assertThat(solrQuery.getQuery())
                .contains(MappingEnum.SUBJECT_IRI.getField() + ":\"")
                .doesNotContain(MappingEnum.OBJECT_IRI.getField() + ":")
                .doesNotContain(MappingEnum.PREDICATE_IRI.getField() + ":")
                .doesNotContain(MappingEnum.SUBJECT_LABEL.getField() + ":\"");
    }

    @Test
    void classifiedQueryRoutesCurieToSubjectIdFieldOnly() {
        MappingSearchRequest request = baseRequest();
        request.setQueries(List.of("DOID:0014667"));

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        String escaped = ClientUtils.escapeQueryChars("DOID:0014667");
        assertThat(solrQuery.getQuery())
                .contains(MappingEnum.SUBJECT_ID.getField() + ":\"" + escaped + "\"")
                .doesNotContain(MappingEnum.OBJECT_ID.getField() + ":")
                .doesNotContain(MappingEnum.PREDICATE_ID.getField() + ":");
    }

    @Test
    void classifiedQueryNormalisesCuriePrefixToStoredCase() {
        MappingSearchRequest request = baseRequest();
        request.setQueries(List.of("doid:0014667"));

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        // The classified path shares the batch subject-side classification (ADR-0030), so a
        // lower-cased prefix is normalised to the stored representation before matching subject_id.
        assertThat(solrQuery.getQuery())
                .contains(MappingEnum.SUBJECT_ID.getField() + ":\""
                        + ClientUtils.escapeQueryChars("DOID:0014667") + "\"");
    }

    @Test
    void partialMatchRoutesFreeTextToAnalyzedSubjectLabelField() {
        MappingSearchRequest request = baseRequest();
        request.setQueries(List.of("diabetes mellitus"));
        request.setLabelMatch(LabelMatchType.PARTIAL);

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        String escaped = ClientUtils.escapeQueryChars("diabetes mellitus");
        assertThat(solrQuery.getQuery())
                .contains(MappingEnum.SUBJECT_LABEL.getField() + ":\"" + escaped + "\"")
                .doesNotContain(MappingEnum.OBJECT_LABEL.getField() + ":")
                .doesNotContain(MappingEnum.PREDICATE_LABEL.getField() + ":")
                // Not the exact-match copies.
                .doesNotContain(labelCi(MappingEnum.SUBJECT_LABEL))
                .doesNotContain(labelStr(MappingEnum.SUBJECT_LABEL));
    }

    @Test
    void caseInsensitiveExactIsTheDefaultAndRoutesFreeTextToSubjectCiField() {
        MappingSearchRequest request = baseRequest();
        request.setQueries(List.of("Diabetes Mellitus"));
        // No setLabelMatch(...) — the request default must be EXACT_CASE_INSENSITIVE (ADR-0026).

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        String escaped = ClientUtils.escapeQueryChars("Diabetes Mellitus");
        assertThat(solrQuery.getQuery())
                .contains(labelCi(MappingEnum.SUBJECT_LABEL) + ":\"" + escaped + "\"")
                .doesNotContain(labelCi(MappingEnum.OBJECT_LABEL))
                .doesNotContain(labelCi(MappingEnum.PREDICATE_LABEL))
                // Not the analyzed nor case-sensitive fields.
                .doesNotContain(MappingEnum.SUBJECT_LABEL.getField() + ":\"")
                .doesNotContain(labelStr(MappingEnum.SUBJECT_LABEL));
    }

    @Test
    void caseSensitiveExactRoutesFreeTextToSubjectStrField() {
        MappingSearchRequest request = baseRequest();
        request.setQueries(List.of("Diabetes Mellitus"));
        request.setLabelMatch(LabelMatchType.EXACT_CASE_SENSITIVE);

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        String escaped = ClientUtils.escapeQueryChars("Diabetes Mellitus");
        assertThat(solrQuery.getQuery())
                .contains(labelStr(MappingEnum.SUBJECT_LABEL) + ":\"" + escaped + "\"")
                .doesNotContain(labelStr(MappingEnum.OBJECT_LABEL))
                .doesNotContain(labelStr(MappingEnum.PREDICATE_LABEL))
                // Not the analyzed nor case-insensitive fields.
                .doesNotContain(MappingEnum.SUBJECT_LABEL.getField() + ":\"")
                .doesNotContain(labelCi(MappingEnum.SUBJECT_LABEL));
    }

    @Test
    void labelMatchModeIsIgnoredForCurieTerms() {
        MappingSearchRequest request = baseRequest();
        request.setQueries(List.of("DOID:0014667"));
        request.setLabelMatch(LabelMatchType.EXACT_CASE_SENSITIVE);

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        String escaped = ClientUtils.escapeQueryChars("DOID:0014667");
        // A CURIE is always an exact *_id lookup; the label-match mode does not divert it to a label field.
        assertThat(solrQuery.getQuery())
                .contains(MappingEnum.SUBJECT_ID.getField() + ":\"" + escaped + "\"")
                .doesNotContain("_label");
    }

    @Test
    void labelMatchModeIsIgnoredForIriTerms() {
        MappingSearchRequest request = baseRequest();
        request.setQueries(List.of("http://example.org/Foo"));
        request.setLabelMatch(LabelMatchType.EXACT_CASE_SENSITIVE);

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        // An IRI is always an exact *_iri lookup, regardless of the label-match mode.
        assertThat(solrQuery.getQuery())
                .contains(MappingEnum.SUBJECT_IRI.getField() + ":\"")
                .doesNotContain("_label");
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

        // Free text uses the default EXACT_CASE_INSENSITIVE field (ADR-0026); the term itself is
        // what this test cares about (null/blank skipped, no empty "" clause).
        assertThat(solrQuery.getQuery())
                .contains(labelCi(MappingEnum.SUBJECT_LABEL) + ":\"kept\"");
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

    // ---------- field list ----------

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

    // ---------- inference_type filter ----------

    @Test
    void inferenceTypeSingleAddsOrClause() {
        MappingSearchRequest request = baseRequest();
        request.setInferenceType(List.of(InferenceType.ASSERTED));

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        // Exact term match(es) on the denormalised inference_type string (ADR-0011), OR-joined.
        assertThat(solrQuery.getFilterQueries())
                .containsExactlyInAnyOrder(
                        "(" + MappingEnum.INFERENCE_TYPE.getField() + ":ASSERTED)",
                        weakPredicateExclusion());
    }

    @Test
    void inferenceTypeMultipleOrsCodes() {
        MappingSearchRequest request = baseRequest();
        request.setInferenceType(List.of(InferenceType.ASSERTED, InferenceType.SSSOM_INFERENCE));

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        String field = MappingEnum.INFERENCE_TYPE.getField();
        assertThat(solrQuery.getFilterQueries())
                .containsExactlyInAnyOrder(
                        "(" + field + ":ASSERTED OR " + field + ":SSSOM_INFERENCE)",
                        weakPredicateExclusion());
    }

    @Test
    void inferenceTypeNullProducesNoExtraFq() {
        MappingSearchRequest request = baseRequest();
        request.setInferenceType(null);

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        // Only the default weak-predicate exclusion remains (no inference-type filter).
        assertThat(solrQuery.getFilterQueries()).containsExactly(weakPredicateExclusion());
    }

    @Test
    void inferenceTypeEmptyProducesNoExtraFq() {
        MappingSearchRequest request = baseRequest();
        request.setInferenceType(Collections.emptyList());

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        // Only the default weak-predicate exclusion remains (no inference-type filter).
        assertThat(solrQuery.getFilterQueries()).containsExactly(weakPredicateExclusion());
    }

    @Test
    void inferenceTypeFilterCombinesWithMappingSetIds() {
        MappingSearchRequest request = baseRequest();
        request.setInferenceType(List.of(InferenceType.SSSOM_INFERENCE));
        request.setMappingSetIds(List.of("set-1"));

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        String field = MappingEnum.MAPPING_SET_ID.getField();
        String mappingSetClause = "(" + field + ":\"" + ClientUtils.escapeQueryChars("set-1") + "\")";
        assertThat(solrQuery.getFilterQueries()).containsExactlyInAnyOrder(
                "(" + MappingEnum.INFERENCE_TYPE.getField() + ":SSSOM_INFERENCE)",
                mappingSetClause,
                weakPredicateExclusion());
    }

    // ---------- provenance-led ranking (multiplicative edismax boost, ADR-0027) ----------

    @Test
    void rankingAppliesMultiplicativeEdismaxBoost() {
        MappingSearchRequest request = baseRequest();
        request.setQueries(List.of("diabetes"));

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        assertThat(solrQuery.get(SolrConstants.DEF_TYPE)).isEqualTo(SolrConstants.EDISMAX);
        String boost = solrQuery.get(SolrConstants.BOOST);
        assertThat(boost).startsWith("mul(");

        // Tier 1: keyed on inference_type, so a doc with no category (pre-reindex) reads as an
        // asserted mapping of an unknown corpus and scores CURATED, never as an inference.
        assertThat(boost)
                .contains("if(termfreq(" + MappingEnum.INFERENCE_TYPE.getField() + ",'ASSERTED'),"
                        + "if(termfreq(" + MappingEnum.MAPPING_SET_CATEGORY.getField()
                        + ",'ONTOLOGY'),10000,1000)")
                // Inferred: 100, divided by 5 per extra hop; a missing distance reads as one hop.
                .contains("div(100,pow(5,sub(def(" + MappingEnum.DISTANCE.getField() + ",1),1)))");

        // Tier 2: strict identity (2) > exactMatch (1.7) > closeMatch (1.4) > hierarchy (1.2) > 1.
        String predicate = MappingEnum.PREDICATE_IRI.getField();
        assertThat(boost)
                .contains("mul(2,sum(termfreq(" + predicate + ",'http://www.w3.org/2002/07/owl#equivalentClass')")
                .contains("mul(1.7,termfreq(" + predicate + ",'http://www.w3.org/2004/02/skos/core#exactMatch'))")
                .contains("mul(1.4,termfreq(" + predicate + ",'http://www.w3.org/2004/02/skos/core#closeMatch'))")
                .contains("mul(1.2,sum(termfreq(" + predicate + ",'http://www.w3.org/2004/02/skos/core#broadMatch')");

        // Tiers 3 and 4: manual curation, then the mapping's own confidence.
        assertThat(boost)
                .contains("mul(1.3,sum(termfreq(" + MappingEnum.MAPPING_JUSTIFICATION.getField()
                        + ",'semapv:ManualMappingCuration')")
                .contains("sum(1,mul(0.3,def(" + MappingEnum.CONFIDENCE.getField() + ",0)))");
    }

    /**
     * The property that makes "trust provenance over predicate" true rather than aspirational: the
     * closest two provenance values must differ by more than the widest combined swing of the tiers
     * below, so no predicate/curation/confidence advantage can lift a curated mapping above an
     * ontology one. Verified against a real Solr: an ONTOLOGY doc with a mere closeMatch outranks a
     * CURATED doc with owl:equivalentClass + manual curation + confidence 1.0.
     */
    @Test
    void rankingTiersAreLexicographic() {
        assertThat(SolrQueryBuilder.rankingTiersAreLexicographic())
                .as("provenance must dominate predicate strength, curation and confidence combined")
                .isTrue();
    }

    /** The boost is built by string concatenation over a map; it must not vary between JVM runs. */
    @Test
    void rankingBoostIsDeterministic() {
        MappingSearchRequest request = baseRequest();
        request.setQueries(List.of("diabetes"));
        String first = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN).get(SolrConstants.BOOST);
        String second = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN).get(SolrConstants.BOOST);
        assertThat(first).isEqualTo(second);
    }

    // ---------- mapping-set-category (source) filter, ADR-0027 ----------

    @Test
    void mappingSetCategoryFilterAlwaysKeepsInferences() {
        MappingSearchRequest request = baseRequest();
        request.setQueries(List.of("diabetes"));
        request.setMappingSetCategory(List.of(MappingSetCategory.ONTOLOGY));

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        // An inference chains premises from several sets and carries no category, so it must not be
        // dropped by a corpus choice — that would make this control duplicate the inference filter.
        assertThat(solrQuery.getFilterQueries()).containsExactlyInAnyOrder(
                "(" + MappingEnum.MAPPING_SET_CATEGORY.getField() + ":ONTOLOGY OR "
                        + MappingEnum.INFERENCE_TYPE.getField() + ":SSSOM_INFERENCE)",
                weakPredicateExclusion());
    }

    @Test
    void mappingSetCategoryFilterOrsBothCategories() {
        MappingSearchRequest request = baseRequest();
        request.setQueries(List.of("diabetes"));
        request.setMappingSetCategory(List.of(MappingSetCategory.ONTOLOGY, MappingSetCategory.CURATED));

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        assertThat(solrQuery.getFilterQueries()).contains(
                "(" + MappingEnum.MAPPING_SET_CATEGORY.getField() + ":ONTOLOGY OR "
                        + MappingEnum.MAPPING_SET_CATEGORY.getField() + ":CURATED OR "
                        + MappingEnum.INFERENCE_TYPE.getField() + ":SSSOM_INFERENCE)");
    }

    @Test
    void noMappingSetCategoryAddsNoFilter() {
        MappingSearchRequest request = baseRequest();
        request.setQueries(List.of("diabetes"));

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        // The default searches every corpus — critically, it emits no clause, so it still returns
        // asserted mappings before the reindex that populates mapping_set_category.
        assertThat(solrQuery.getFilterQueries())
                .noneMatch(fq -> fq.contains(MappingEnum.MAPPING_SET_CATEGORY.getField()));
    }

    @Test
    void emptyMappingSetCategoryAddsNoFilter() {
        MappingSearchRequest request = baseRequest();
        request.setQueries(List.of("diabetes"));
        request.setMappingSetCategory(Collections.emptyList());

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        assertThat(solrQuery.getFilterQueries())
                .noneMatch(fq -> fq.contains(MappingEnum.MAPPING_SET_CATEGORY.getField()));
    }

    // ---------- relevance is the default sort, so the ranking is actually visible ----------

    /**
     * An explicit Solr sort replaces {@code score}, so a default sorted field would silently discard
     * the whole provenance ranking. Without a caller sort we must name {@code score desc} ourselves.
     */
    @Test
    void noSortedFieldsSortsByRelevance() {
        MappingSearchRequest request = baseRequest();
        request.setQueries(List.of("diabetes"));

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        assertThat(solrQuery.getSortField()).isEqualTo("score desc");
    }

    /**
     * Collapse appends spo_key as a paging tiebreaker. It must stay a tiebreaker: if relevance were
     * not named first, spo_key would become the primary key and order the page by an opaque hash.
     */
    @Test
    void groupedSearchKeepsRelevancePrimaryAndSpoKeyAsTiebreaker() {
        MappingSearchRequest request = baseRequest();
        request.setQueries(List.of("diabetes"));
        request.setGroupBySpo(true);

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        assertThat(solrQuery.getSortField())
                .isEqualTo("score desc," + MappingEnum.SPO_KEY.getField() + " asc");
    }

    /** An explicit caller sort still wins — the fallback only applies when none was given. */
    @Test
    void explicitSortedFieldsReplaceRelevance() {
        MappingSearchRequest request = baseRequest();
        request.setQueries(List.of("diabetes"));
        SortedField sort = new SortedField();
        sort.setId(MappingEnum.CONFIDENCE);
        sort.setDesc(true);
        request.setSortedFields(List.of(sort));

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        assertThat(solrQuery.getSortField()).isEqualTo(MappingEnum.CONFIDENCE.getField() + " desc");
    }

    /** The export must filter identically to the table, or the download won't match what was shown. */
    @Test
    void exportQueryHonoursMappingSetCategoryFilter() {
        MappingSearchRequest request = baseRequest();
        request.setQueries(List.of("diabetes"));
        request.setMappingSetCategory(List.of(MappingSetCategory.CURATED));

        SolrQuery solrQuery = SolrQueryBuilder.buildExportQuery(request);

        assertThat(solrQuery.getFilterQueries()).contains(
                "(" + MappingEnum.MAPPING_SET_CATEGORY.getField() + ":CURATED OR "
                        + MappingEnum.INFERENCE_TYPE.getField() + ":SSSOM_INFERENCE)");
    }

    // ---------- same-SPO grouping (ADR-0013) ----------

    @Test
    void groupBySpoAddsCollapseAndExpandParams() {
        MappingSearchRequest request = baseRequest();
        request.setGroupBySpo(true);
        SortedField sort = new SortedField();
        sort.setId(MappingEnum.SUBJECT_LABEL);
        sort.setDesc(false);
        request.setSortedFields(List.of(sort));

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        String spoKey = MappingEnum.SPO_KEY.getField();
        String collapseFilter = String.format(
                SolrConstants.COLLAPSE_FQ_TEMPLATE, spoKey, SolrConstants.REPRESENTATIVE_SORT);
        assertThat(solrQuery.getFilterQueries()).contains(collapseFilter);
        assertThat(solrQuery.getBool(SolrConstants.EXPAND, false)).isTrue();
        assertThat(solrQuery.get(SolrConstants.EXPAND_FIELD)).isEqualTo(spoKey);
        assertThat(solrQuery.get(SolrConstants.EXPAND_SORT)).isEqualTo(SolrConstants.REPRESENTATIVE_SORT);
        assertThat(solrQuery.get(SolrConstants.EXPAND_ROWS))
                .isEqualTo(String.valueOf(SolrConstants.GROUP_MEMBER_LIMIT));
        // spo_key is docValues-only, so it is added to fl for the expand join.
        assertThat(solrQuery.getFields()).contains(spoKey);
        // spo_key is appended after the user's sort as a total-order tiebreaker for stable paging.
        SolrQuery.SortClause lastSort = solrQuery.getSorts().get(solrQuery.getSorts().size() - 1);
        assertThat(lastSort.getItem()).isEqualTo(spoKey);
        assertThat(lastSort.getOrder()).isEqualTo(SolrQuery.ORDER.asc);
    }

    @Test
    void groupBySpoFalseAddsNoCollapseOrExpand() {
        MappingSearchRequest request = baseRequest();

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        assertThat(solrQuery.getBool(SolrConstants.EXPAND, false)).isFalse();
        String[] filterQueries = solrQuery.getFilterQueries();
        assertThat(filterQueries == null ? new String[0] : filterQueries)
                .noneMatch(clause -> clause.contains("{!collapse"));
        assertThat(solrQuery.getSorts())
                .noneMatch(clause -> clause.getItem().equals(MappingEnum.SPO_KEY.getField()));
    }

    // ---------- default weak-predicate exclusion (rdfs:subClassOf, oboInOwl:hasDbXref) ----------

    @Test
    void weakPredicatesExcludedByDefaultOnPlainSearch() {
        MappingSearchRequest request = baseRequest();
        request.setQueries(List.of("diabetes"));

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        assertThat(solrQuery.getFilterQueries()).containsExactly(weakPredicateExclusion());
    }

    @Test
    void weakPredicateExclusionIsNegativeOnCanonicalPredicateIri() {
        MappingSearchRequest request = baseRequest();
        request.setQueries(List.of("diabetes"));

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        // Match on the canonical predicate_iri (not the source-dependent predicate_id CURIE), as a
        // *:*-anchored negative so docs with these predicates are subtracted from the full set.
        String exclusion = solrQuery.getFilterQueries()[0];
        assertThat(exclusion)
                .startsWith("*:* -(")
                .contains(MappingEnum.PREDICATE_IRI.getField() + ":")
                .doesNotContain(MappingEnum.PREDICATE_ID.getField() + ":")
                .contains("subClassOf")
                .contains("hasDbXref");
    }

    /**
     * The search-side half of ADR-0035's checkboxes. Ticking one predicate must lift the exclusion for
     * that one ALONE — the two are independently switchable, so asking to see the hierarchy is not a
     * request to also be shown every loose cross-reference.
     */
    @Test
    void tickingOneWeakPredicateShowsItAndKeepsHidingTheOther() {
        MappingSearchRequest request = baseRequest();
        request.setQueries(List.of("diabetes"));
        request.setIncludeWeakPredicates(List.of(WeakPredicate.HAS_DB_XREF));

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        assertThat(solrQuery.getFilterQueries())
                .containsExactly(exclusionOf("http://www.w3.org/2000/01/rdf-schema#subClassOf"));
        assertThat(solrQuery.getFilterQueries()[0]).doesNotContain("hasDbXref");
    }

    @Test
    void tickingBothWeakPredicatesLeavesNoExclusionAtAll() {
        MappingSearchRequest request = baseRequest();
        request.setQueries(List.of("diabetes"));
        request.setIncludeWeakPredicates(
                List.of(WeakPredicate.SUB_CLASS_OF, WeakPredicate.HAS_DB_XREF));

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        // No no-op all-docs clause left behind: with nothing hidden there is nothing to subtract.
        assertThat(solrQuery.getFilterQueries()).isEmpty();
    }

    /** The default is unchanged by the new parameter: an absent list still hides both. */
    @Test
    void anAbsentIncludeListStillHidesBothWeakPredicates() {
        MappingSearchRequest request = baseRequest();
        request.setQueries(List.of("diabetes"));
        request.setIncludeWeakPredicates(null);

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        assertThat(solrQuery.getFilterQueries()).containsExactly(weakPredicateExclusion());
    }

    @Test
    void predicateIdColumnFilterBypassesWeakPredicateExclusion() {
        MappingSearchRequest request = baseRequest();
        request.setColumnFilters(List.of(
                new ColumnFilter(MappingEnum.PREDICATE_ID.getField(), "skos:exactMatch")));

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        // Explicit predicate filter present → only that filter, no default exclusion.
        String expected = MappingEnum.PREDICATE_ID.getField() + ":*"
                + ClientUtils.escapeQueryChars("skos:exactMatch") + "*";
        assertThat(solrQuery.getFilterQueries()).containsExactly(expected);
    }

    @Test
    void predicateLabelColumnFilterBypassesWeakPredicateExclusion() {
        MappingSearchRequest request = baseRequest();
        request.setColumnFilters(List.of(
                new ColumnFilter(MappingEnum.PREDICATE_LABEL.getField(), "exact match")));

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        String expected = ngramContainsAll(MappingEnum.PREDICATE_LABEL, "exact match");
        assertThat(solrQuery.getFilterQueries()).containsExactly(expected);
    }

    @Test
    void predicateIriColumnFilterBypassesWeakPredicateExclusion() {
        MappingSearchRequest request = baseRequest();
        request.setColumnFilters(List.of(new ColumnFilter(
                MappingEnum.PREDICATE_IRI.getField(),
                "http://www.w3.org/2000/01/rdf-schema#subClassOf")));

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        // Filtering directly on a predicate field — even predicate_iri — switches off the default
        // exclusion, so a caller can deliberately pull back the otherwise-hidden subClassOf rows.
        assertThat(solrQuery.getFilterQueries())
                .noneMatch(clause -> clause.equals(weakPredicateExclusion()));
    }

    @Test
    void advancedPredicateFieldQueryBypassesWeakPredicateExclusion() {
        MappingSearchRequest request = baseRequest();
        request.setAdvancedFieldQueries(List.of(
                new FieldQuery(MappingEnum.PREDICATE_ID.getField(), "skos:exactMatch")));

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        // Advanced predicate clause goes in q, not fq; no column filters → no fq at all.
        assertThat(solrQuery.getFilterQueries()).isEmpty();
    }

    @Test
    void blankPredicateFilterDoesNotBypassWeakPredicateExclusion() {
        MappingSearchRequest request = baseRequest();
        request.setColumnFilters(List.of(
                new ColumnFilter(MappingEnum.PREDICATE_ID.getField(), "   ")));

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        // A blank value is not a real predicate constraint, so the default exclusion still applies
        // (and the blank filter itself contributes no clause).
        assertThat(solrQuery.getFilterQueries()).containsExactly(weakPredicateExclusion());
    }

    @Test
    void plainSearchMatchingPredicateDoesNotBypassWeakPredicateExclusion() {
        MappingSearchRequest request = baseRequest();
        // A CURIE typed into the main box routes to subject_id only (ADR-0030) — never a predicate
        // field — so a plain search can never constitute an explicit predicate filter and the
        // exclusion must still apply.
        request.setQueries(List.of("rdfs:subClassOf"));

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        assertThat(solrQuery.getFilterQueries()).containsExactly(weakPredicateExclusion());
    }

    // ---------- cross-ontology prefix filters (ADR-0024) ----------

    @Test
    void subjectPrefixesProduceOrFilterClause() {
        MappingSearchRequest request = baseRequest();
        request.setSubjectPrefixes(List.of("DOID", "HP"));

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        String field = MappingEnum.SUBJECT_PREFIX.getField();
        String expected = "(" + field + ":DOID OR " + field + ":HP)";
        assertThat(solrQuery.getFilterQueries())
                .containsExactlyInAnyOrder(expected, weakPredicateExclusion());
    }

    @Test
    void subjectAndObjectPrefixesProduceTwoDirectionalClauses() {
        MappingSearchRequest request = baseRequest();
        request.setSubjectPrefixes(List.of("DOID"));
        request.setObjectPrefixes(List.of("EFO", "MONDO"));

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        String subjectField = MappingEnum.SUBJECT_PREFIX.getField();
        String objectField = MappingEnum.OBJECT_PREFIX.getField();
        String subjectClause = "(" + subjectField + ":DOID)";
        String objectClause = "(" + objectField + ":EFO OR " + objectField + ":MONDO)";
        assertThat(solrQuery.getFilterQueries())
                .containsExactlyInAnyOrder(subjectClause, objectClause, weakPredicateExclusion());
    }

    @Test
    void prefixFilterValuesAreEscaped() {
        MappingSearchRequest request = baseRequest();
        request.setSubjectPrefixes(List.of(INJECTION_PAYLOAD));

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        String expected = "(" + MappingEnum.SUBJECT_PREFIX.getField() + ":"
                + ClientUtils.escapeQueryChars(INJECTION_PAYLOAD) + ")";
        assertThat(solrQuery.getFilterQueries())
                .containsExactlyInAnyOrder(expected, weakPredicateExclusion());
        assertThat(String.join("", solrQuery.getFilterQueries())).doesNotContain("\" OR *:*");
    }

    @Test
    void blankPrefixesAreIgnored() {
        MappingSearchRequest request = baseRequest();
        request.setSubjectPrefixes(Arrays.asList("  ", null));

        SolrQuery solrQuery = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN);

        // No prefix clause contributed; only the default weak-predicate exclusion remains.
        assertThat(solrQuery.getFilterQueries()).containsExactly(weakPredicateExclusion());
    }

    // ---------- subject-side classification + v1 term query (ADR-0024) ----------

    @Test
    void subjectSideClauseNormalisesCurieCaseToStoredForm() {
        String escaped = ClientUtils.escapeQueryChars("DOID:9352");
        assertThat(SolrQueryBuilder.subjectSideClause("doid:9352"))
                .isEqualTo("(" + MappingEnum.SUBJECT_ID.getField() + ":\"" + escaped + "\")");
    }

    @Test
    void subjectSideClauseRoutesIriAndLabel() {
        assertThat(SolrQueryBuilder.subjectSideClause("http://purl.obolibrary.org/obo/DOID_9352"))
                .startsWith("(" + MappingEnum.SUBJECT_IRI.getField() + ":");
        assertThat(SolrQueryBuilder.subjectSideClause("diabetes"))
                .startsWith("(" + MappingEnum.SUBJECT_LABEL.getField() + ":");
    }

    @Test
    void v1TermQueryDistanceOneFiltersAssertedOnly() {
        SolrQuery solrQuery = SolrQueryBuilder.buildV1TermQuery("DOID:9352", List.of("EFO"), 1, 100);

        assertThat(solrQuery.getFilterQueries())
                .contains("(" + MappingEnum.INFERENCE_TYPE.getField() + ":"
                        + InferenceType.ASSERTED.getCode() + ")");
        assertThat(solrQuery.getFilterQueries())
                .contains("(" + MappingEnum.OBJECT_PREFIX.getField() + ":EFO)");
    }

    @Test
    void v1TermQueryUnlimitedDistanceHasNoInferenceTypeFilter() {
        SolrQuery solrQuery = SolrQueryBuilder.buildV1TermQuery("DOID:9352", List.of("EFO"), -1, 100);

        assertThat(Arrays.stream(solrQuery.getFilterQueries())
                .anyMatch(filterQuery -> filterQuery.contains(MappingEnum.INFERENCE_TYPE.getField())))
                .isFalse();
    }

    // ---------- v1 /api/mappings query (ADR-0025) ----------

    private static final String ASSERTED_FILTER =
            MappingEnum.INFERENCE_TYPE.getField() + ":" + InferenceType.ASSERTED.getCode();

    @Test
    void v1MappingsFromIdOnlyIsUndirectedAndAssertedByDefault() {
        SolrQuery solrQuery =
                SolrQueryBuilder.buildV1MappingsQuery("DOID:0001816", null, false, PAGE_OF_TEN);

        // Undirected: the term matches on either the subject or the object side.
        String term = ClientUtils.escapeQueryChars("DOID:0001816");
        assertThat(solrQuery.getQuery()).isEqualTo(
                "(" + MappingEnum.SUBJECT_ID.getField() + ":\"" + term + "\""
                        + " OR " + MappingEnum.OBJECT_ID.getField() + ":\"" + term + "\")");
        assertThat(solrQuery.getFilterQueries()).containsExactly(ASSERTED_FILTER);
    }

    @Test
    void v1MappingsNormalisesLowercaseCuriePrefix() {
        SolrQuery solrQuery =
                SolrQueryBuilder.buildV1MappingsQuery("doid:0001816", null, false, PAGE_OF_TEN);

        // A lower-cased CURIE prefix is normalised to the stored representation before matching.
        assertThat(solrQuery.getQuery()).contains(
                MappingEnum.SUBJECT_ID.getField() + ":\"" + ClientUtils.escapeQueryChars("DOID:0001816") + "\"");
    }

    @Test
    void v1MappingsFromAndToIsUndirectedPair() {
        SolrQuery solrQuery =
                SolrQueryBuilder.buildV1MappingsQuery("DOID:0001816", "EFO:0000400", false, PAGE_OF_TEN);

        String subjectField = MappingEnum.SUBJECT_ID.getField();
        String objectField = MappingEnum.OBJECT_ID.getField();
        String from = ClientUtils.escapeQueryChars("DOID:0001816");
        String to = ClientUtils.escapeQueryChars("EFO:0000400");
        assertThat(solrQuery.getQuery()).isEqualTo(
                "((" + subjectField + ":\"" + from + "\" AND " + objectField + ":\"" + to + "\")"
                        + " OR (" + subjectField + ":\"" + to + "\" AND "
                        + objectField + ":\"" + from + "\"))");
    }

    @Test
    void v1MappingsToIdOnlyMatchesEverything() {
        SolrQuery solrQuery =
                SolrQueryBuilder.buildV1MappingsQuery(null, "EFO:0000400", false, PAGE_OF_TEN);

        // v1 ignored a lone toId and returned all mappings; reproduced verbatim (ADR-0025).
        assertThat(solrQuery.getQuery()).isEqualTo("*:*");
    }

    @Test
    void v1MappingsHidesWeakPredicatesOnlyWhenRequested() {
        SolrQuery shown = SolrQueryBuilder.buildV1MappingsQuery(null, null, false, PAGE_OF_TEN);
        assertThat(shown.getFilterQueries()).containsExactly(ASSERTED_FILTER);

        SolrQuery hidden = SolrQueryBuilder.buildV1MappingsQuery(null, null, true, PAGE_OF_TEN);
        assertThat(hidden.getFilterQueries()).containsExactly(ASSERTED_FILTER, weakPredicateExclusion());
    }

    @Test
    void v1MappingsMatchesIriOnTheIriField() {
        SolrQuery solrQuery = SolrQueryBuilder.buildV1MappingsQuery(
                "http://purl.obolibrary.org/obo/DOID_0001816", null, false, PAGE_OF_TEN);

        assertThat(solrQuery.getQuery()).contains(MappingEnum.SUBJECT_IRI.getField() + ":\"");
        assertThat(solrQuery.getQuery()).contains(MappingEnum.OBJECT_IRI.getField() + ":\"");
    }

    // ---------------------------------------------------------------------------------------------
    // Value suggestions (ADR-0034)
    // ---------------------------------------------------------------------------------------------

    /** A live search with a bit of everything on it, to prove the suggest inherits all of it. */
    private static MappingSearchRequest liveSearch() {
        MappingSearchRequest request = baseRequest();
        request.setQueries(List.of("cataract"));
        request.setInferenceType(List.of(InferenceType.ASSERTED));
        request.setMappingSetCategory(List.of(MappingSetCategory.ONTOLOGY));
        request.setSubjectPrefixes(List.of("MONDO"));
        request.setObjectPrefixes(List.of("EFO"));
        request.setMappingSetIds(List.of("https://example.org/set/1"));
        request.setColumnFilters(List.of(
                new ColumnFilter(MappingEnum.OBJECT_LABEL.getField(), "eye")));
        return request;
    }

    /**
     * THE regression test for ADR-0034's "reuse, don't reimplement". The contextual suggest must be
     * scoped by exactly the restrictions the search itself applies; if someone ever rebuilds that
     * filter list by hand instead of calling buildSolrQuery, the two will silently drift and the
     * dropdown will start offering values that yield zero rows. Compare them directly.
     */
    @Test
    void valueSuggestInheritsEverySearchFilter() {
        MappingSearchRequest request = liveSearch();

        String[] searchFilters = SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN).getFilterQueries();
        String[] suggestFilters = SolrQueryBuilder
                .buildValueSuggestQuery(request, MappingEnum.PREDICATE_ID, "skos", 10)
                .getFilterQueries();

        // Every filter the search applies is also applied to the facet. The suggest additionally
        // DROPS the filter on the field being suggested, so it is a subset relation, not equality —
        // and here the only column filter is on a different field, so nothing is dropped.
        assertThat(suggestFilters).containsAll(Arrays.asList(searchFilters));
    }

    /** The search's q is inherited too — suggestions come from the rows the query matched. */
    @Test
    void valueSuggestInheritsTheSearchQuery() {
        MappingSearchRequest request = liveSearch();

        assertThat(SolrQueryBuilder.buildValueSuggestQuery(request, MappingEnum.PREDICATE_ID, "skos", 10)
                .getQuery())
                .isEqualTo(SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN).getQuery());
    }

    /**
     * Faceting on the field the user is mid-way through typing, while that half-typed value is still
     * an active filter, scopes the facet by the very thing it is trying to complete — so both the
     * values and the counts come back wrong.
     */
    @Test
    void valueSuggestDropsTheInProgressFilterOnTheSuggestedField() {
        MappingSearchRequest request = baseRequest();
        request.setColumnFilters(List.of(
                new ColumnFilter(MappingEnum.PREDICATE_ID.getField(), "sko"),
                new ColumnFilter(MappingEnum.OBJECT_LABEL.getField(), "eye")));

        String[] filterQueries = SolrQueryBuilder
                .buildValueSuggestQuery(request, MappingEnum.PREDICATE_ID, "sko", 10)
                .getFilterQueries();

        String joined = String.join(" ", filterQueries);
        // The half-typed predicate filter is gone...
        assertThat(joined).doesNotContain(MappingEnum.PREDICATE_ID.getField() + "_ngram");
        // ...but the OTHER column's filter still scopes the suggestions.
        assertThat(joined).contains(labelNgram(MappingEnum.OBJECT_LABEL));
    }

    /** And it must not mutate the caller's request — that request IS the live search. */
    @Test
    void valueSuggestDoesNotMutateTheCallersRequest() {
        MappingSearchRequest request = liveSearch();
        request.setGroupBySpo(true);
        int columnFilterCount = request.getColumnFilters().size();

        SolrQueryBuilder.buildValueSuggestQuery(request, MappingEnum.OBJECT_LABEL, "eye", 10);

        assertThat(request.getColumnFilters()).hasSize(columnFilterCount);
        assertThat(request.isGroupBySpo()).isTrue();
    }

    /**
     * The weak-predicate exclusion is part of what the search means, so the facet inherits it —
     * otherwise the dropdown would offer values that only exist on the rows the search is hiding.
     * The expected clause goes through the escape oracle (escapeQueryChars escapes the '-' in
     * "rdf-schema" too, which is precisely why it is computed and not written out).
     */
    @Test
    void valueSuggestKeepsTheWeakPredicateExclusion() {
        MappingSearchRequest request = baseRequest();
        request.setQueries(List.of("cataract"));
        String subClassOf = ClientUtils.escapeQueryChars(
                "http://www.w3.org/2000/01/rdf-schema#subClassOf");

        assertThat(String.join(" ", SolrQueryBuilder
                .buildValueSuggestQuery(request, MappingEnum.OBJECT_LABEL, "eye", 10)
                .getFilterQueries()))
                .contains(MappingEnum.PREDICATE_IRI.getField() + ":\"" + subClassOf + "\"");
    }

    /** A facet counts documents; the collapse post-filter and its expand pass are pure waste here. */
    @Test
    void valueSuggestDropsTheSameSpoCollapse() {
        MappingSearchRequest request = liveSearch();
        request.setGroupBySpo(true);

        SolrQuery solrQuery = SolrQueryBuilder
                .buildValueSuggestQuery(request, MappingEnum.PREDICATE_ID, "skos", 10);

        assertThat(String.join(" ", solrQuery.getFilterQueries())).doesNotContain("{!collapse");
        assertThat(solrQuery.get(SolrConstants.EXPAND)).isNull();
    }

    @Test
    void valueSuggestAsksForNoDocumentsAndCountedFacets() {
        SolrQuery solrQuery = SolrQueryBuilder
                .buildValueSuggestQuery(baseRequest(), MappingEnum.PREDICATE_ID, "skos", 7);

        assertThat(solrQuery.getRows()).isZero();
        assertThat(solrQuery.getBool("facet")).isTrue();
        assertThat(solrQuery.getInt("facet.mincount")).isEqualTo(1);
        assertThat(solrQuery.getInt("facet.limit")).isEqualTo(7);
        assertThat(solrQuery.get("facet.sort")).isEqualTo("count");
    }

    /**
     * facet.prefix is a RAW TERM PREFIX — not analyzed, not query-parsed. Escaping it would put
     * literal backslashes into the term being matched, so nothing would ever match. This is the
     * opposite of the rule everywhere else in this class, which is exactly why it needs a test.
     */
    @Test
    void facetPrefixIsNotEscaped() {
        SolrQuery solrQuery = SolrQueryBuilder
                .buildValueSuggestQuery(baseRequest(), MappingEnum.PREDICATE_ID, "skos:ex", 10);

        String facetFields = String.join(" ", solrQuery.getParams(FACET_FIELD));
        assertThat(facetFields).contains("facet.prefix=skos:ex");
        assertThat(facetFields).doesNotContain(ClientUtils.escapeQueryChars("skos:ex"));
    }

    /**
     * The facet field preserves the original casing (it must — a case-folded field would hand back
     * "mondo:0005148" as the value to display), so a case-sensitive facet.prefix would miss
     * "Melanoma" for a user typing "mel". Issue the prefix under each realistic casing.
     */
    @Test
    void facetPrefixIsIssuedUnderEachCasing() {
        SolrQuery solrQuery = SolrQueryBuilder
                .buildValueSuggestQuery(baseRequest(), MappingEnum.OBJECT_LABEL, "mel", 10);

        String facetFields = String.join(" ", solrQuery.getParams(FACET_FIELD));
        assertThat(facetFields)
                .contains("facet.prefix=mel")
                .contains("facet.prefix=MEL")
                .contains("facet.prefix=Mel");
    }

    /** Each casing needs its own key, or Solr keeps only the last facet.prefix for the field. */
    @Test
    void eachCasingFacetGetsItsOwnKey() {
        SolrQuery solrQuery = SolrQueryBuilder
                .buildValueSuggestQuery(baseRequest(), MappingEnum.OBJECT_LABEL, "mel", 10);

        String[] facetFields = solrQuery.getParams(FACET_FIELD);
        long distinctKeys = Arrays.stream(facetFields)
                .map(facetField -> facetField.substring(facetField.indexOf("key=")))
                .distinct().count();
        assertThat(distinctKeys).isEqualTo(facetFields.length);
    }

    /**
     * Faceting a text_general field returns its ANALYZED TOKENS, not its values — it would suggest
     * "the" and "disease" as completions of a mapping set title. The _str twin carries the whole
     * value with its original casing. NB deliberately not the _ci twin (ADR-0026): that folds case,
     * so a facet on it hands back a lower-cased value, which is wrong to show or to filter on.
     */
    @Test
    void textGeneralFieldsAreFacetedThroughTheirWholeValueTwin() {
        assertThat(SuggestFields.facetFieldFor(MappingEnum.OBJECT_LABEL))
                .isEqualTo(MappingEnum.OBJECT_LABEL.getField() + "_str");
        assertThat(SuggestFields.facetFieldFor(MappingEnum.MAPPING_SET_TITLE))
                .isEqualTo(MappingEnum.MAPPING_SET_TITLE.getField() + "_str");

        // A string field is already whole-value, so it is faceted directly.
        assertThat(SuggestFields.facetFieldFor(MappingEnum.PREDICATE_ID))
                .isEqualTo(MappingEnum.PREDICATE_ID.getField());
        assertThat(SuggestFields.facetFieldFor(MappingEnum.MAPPING_JUSTIFICATION))
                .isEqualTo(MappingEnum.MAPPING_JUSTIFICATION.getField());
    }

    /**
     * A picked value round-trips: the field an EXACT filter targets is the same whole-value field the
     * suggestion was faceted out of, so applying it returns exactly the rows it was counted from.
     */
    @Test
    void exactFilterTargetsTheFieldTheSuggestionCameFrom() {
        for (MappingEnum field : SuggestFields.CONTEXTUAL_FIELDS) {
            assertThat(SuggestFields.exactMatchFieldFor(field))
                    .isEqualTo(SuggestFields.facetFieldFor(field));
        }
    }

    /** The entity fields are served by the entity typeahead; a GLOBAL facet on them is millions of terms. */
    @Test
    void highCardinalityEntityFieldsAreNotVocabularyFields() {
        assertThat(SuggestFields.VOCAB_FIELDS)
                .doesNotContain(MappingEnum.OBJECT_ID)
                .doesNotContain(MappingEnum.OBJECT_LABEL)
                .doesNotContain(MappingEnum.SUBJECT_ID)
                .doesNotContain(MappingEnum.SUBJECT_LABEL);

        // But a column filter may suggest object values, because that facet is scoped AND prefixed.
        assertThat(SuggestFields.CONTEXTUAL_FIELDS)
                .contains(MappingEnum.OBJECT_ID)
                .contains(MappingEnum.OBJECT_LABEL);
    }

    /** Free prose has no vocabulary, and these two are indexed="false" so they cannot be faceted at all. */
    @Test
    void proseAndUnindexedFieldsAreNotSuggestible() {
        assertThat(SuggestFields.VOCAB_FIELDS)
                .doesNotContain(MappingEnum.COMMENT)
                .doesNotContain(MappingEnum.OTHER)
                .doesNotContain(MappingEnum.SEE_ALSO)
                .doesNotContain(MappingEnum.MAPPING_SET_DESCRIPTION)
                .doesNotContain(MappingEnum.MATCH_STRING)
                .doesNotContain(MappingEnum.ISSUE_TRACKER_ITEM)
                .doesNotContain(MappingEnum.ASSERTED_MAPPINGS);
    }

    @Test
    void distinctValuesQueryIsAMatchAllFacet() {
        SolrQuery solrQuery = SolrQueryBuilder
                .buildDistinctValuesQuery(MappingEnum.MAPPING_JUSTIFICATION, 500);

        assertThat(solrQuery.getQuery()).isEqualTo("*:*");
        assertThat(solrQuery.getRows()).isZero();
        assertThat(solrQuery.getFacetFields())
                .containsExactly(MappingEnum.MAPPING_JUSTIFICATION.getField());
        assertThat(solrQuery.getInt("facet.limit")).isEqualTo(500);
    }

    // ---------- picking is not typing (ADR-0034) ----------

    /**
     * A picked value came verbatim out of the index, so it matches the WHOLE value. Matching it as a
     * substring would be the surprising behaviour: someone who explicitly picked "melanoma" from the
     * dropdown would also get back "familial melanoma" and "melanoma of skin" — values they were
     * shown and did not choose.
     */
    @Test
    void exactColumnFilterMatchesTheWholeValue() {
        MappingSearchRequest request = baseRequest();
        request.setColumnFilters(List.of(new ColumnFilter(
                MappingEnum.OBJECT_LABEL.getField(), "melanoma", FilterMatchType.EXACT)));

        assertThat(String.join(" ", SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN).getFilterQueries()))
                .contains(SuggestFields.exactMatchFieldFor(MappingEnum.OBJECT_LABEL)
                        + ":\"" + ClientUtils.escapeQueryChars("melanoma") + "\"")
                // ...and NOT the substring wildcard the typed path would have produced.
                .doesNotContain(labelNgram(MappingEnum.OBJECT_LABEL) + ":*");
    }

    /** For a string field an exact filter is just the field itself — it is already whole-value. */
    @Test
    void exactColumnFilterOnAStringFieldTargetsTheFieldItself() {
        MappingSearchRequest request = baseRequest();
        request.setColumnFilters(List.of(new ColumnFilter(
                MappingEnum.PREDICATE_ID.getField(), "skos:exactMatch", FilterMatchType.EXACT)));

        assertThat(String.join(" ", SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN).getFilterQueries()))
                .contains(MappingEnum.PREDICATE_ID.getField()
                        + ":\"" + ClientUtils.escapeQueryChars("skos:exactMatch") + "\"");
    }

    /** An exact value is still user input arriving over HTTP; it must not escape its clause. */
    @Test
    void exactColumnFilterIsEscaped() {
        MappingSearchRequest request = baseRequest();
        request.setColumnFilters(List.of(new ColumnFilter(
                MappingEnum.PREDICATE_ID.getField(), INJECTION_PAYLOAD, FilterMatchType.EXACT)));

        String filterQueries = String.join(" ",
                SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN).getFilterQueries());
        String escaped = ClientUtils.escapeQueryChars(INJECTION_PAYLOAD);

        assertThat(filterQueries).contains(MappingEnum.PREDICATE_ID.getField() + ":\"" + escaped + "\"");
        assertThat(filterQueries.replace(escaped, "")).doesNotContain("*:* OR");
    }

    /**
     * The whole point of defaulting to CONTAINS: a caller that has never heard of autocomplete keeps
     * exactly the behaviour it had. This is the regression guard on that.
     */
    @Test
    void aFilterWithNoMatchTypeStillContains() {
        MappingSearchRequest request = baseRequest();
        request.setColumnFilters(List.of(
                new ColumnFilter(MappingEnum.OBJECT_LABEL.getField(), "eye")));

        assertThat(request.getColumnFilters().get(0).getMatch()).isEqualTo(FilterMatchType.CONTAINS);
        assertThat(String.join(" ", SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN).getFilterQueries()))
                .contains(ngramContainsAll(MappingEnum.OBJECT_LABEL, "eye"));
    }

    /** An explicit predicate filter still lifts the weak-predicate hiding when it is EXACT. */
    @Test
    void anExactPredicateFilterStillLiftsTheWeakPredicateHiding() {
        MappingSearchRequest request = baseRequest();
        request.setColumnFilters(List.of(new ColumnFilter(
                MappingEnum.PREDICATE_ID.getField(), "oboInOwl:hasDbXref", FilterMatchType.EXACT)));

        assertThat(String.join(" ", SolrQueryBuilder.buildSolrQuery(request, PAGE_OF_TEN).getFilterQueries()))
                .doesNotContain("-(" + MappingEnum.PREDICATE_IRI.getField());
    }
}
