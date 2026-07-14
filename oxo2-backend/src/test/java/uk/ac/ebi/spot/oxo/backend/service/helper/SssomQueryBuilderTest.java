package uk.ac.ebi.spot.oxo.backend.service.helper;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import uk.ac.ebi.spot.oxo.backend.controller.api.sssom.dto.SearchEntityRequest;
import uk.ac.ebi.spot.oxo.backend.service.helper.SssomQueryBuilder.Operator;
import uk.ac.ebi.spot.oxo.backend.service.helper.SssomQueryBuilder.SssomFilter;
import uk.ac.ebi.spot.oxo.model.sssom.EntityReference;
import uk.ac.ebi.spot.oxo.model.sssom.MappingEnum;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Drives the SSSOM-API query builder (ADR-0032) through its public entry points, asserting on the
 * observable {@link SolrQuery} it produces. Expected Solr strings are computed with
 * {@link ClientUtils#escapeQueryChars} (the escape oracle), never hard-coded, matching
 * {@code SolrQueryBuilderTest}.
 */
class SssomQueryBuilderTest {

    private static final Pageable PAGE_OF_TEN = PageRequest.of(0, 10);
    private static final String INJECTION = "a\" OR *:* OR x";

    private static String normalisedCurie(String curie) {
        return new EntityReference(curie).getDataRepresentation().map(Object::toString).orElse(curie);
    }

    // ---------- filter grammar parsing ----------

    @Test
    void parsesFieldOperatorValueTriples() {
        List<SssomFilter> filters = SssomQueryBuilder.parseFilters(
                List.of("confidence|ge|0.8", "predicate_id|eq|skos:exactMatch"),
                SssomQueryBuilder::resolveMappingField);

        assertThat(filters).hasSize(2);
        assertThat(filters.get(0).field()).isEqualTo(MappingEnum.CONFIDENCE);
        assertThat(filters.get(0).operator()).isEqualTo(Operator.GE);
        assertThat(filters.get(1).field()).isEqualTo(MappingEnum.PREDICATE_ID);
        assertThat(filters.get(1).operator()).isEqualTo(Operator.EQ);
    }

    @Test
    void rejectsMalformedFilterArity() {
        assertThatThrownBy(() -> SssomQueryBuilder.parseFilters(
                List.of("confidence|ge"), SssomQueryBuilder::resolveMappingField))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnknownFilterField() {
        assertThatThrownBy(() -> SssomQueryBuilder.parseFilters(
                List.of("not_a_field|eq|x"), SssomQueryBuilder::resolveMappingField))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnknownFilterOperator() {
        assertThatThrownBy(() -> SssomQueryBuilder.parseFilters(
                List.of("confidence|between|0.8"), SssomQueryBuilder::resolveMappingField))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mappingSetFieldResolverRejectsMappingOnlySlots() {
        // subject_id is a mapping slot, absent from the mapping-set collection.
        assertThat(SssomQueryBuilder.resolveMappingSetField("subject_id")).isNull();
        assertThat(SssomQueryBuilder.resolveMappingSetField("mapping_provider"))
                .isEqualTo(MappingEnum.MAPPING_PROVIDER);
    }

    // ---------- clause construction ----------

    @Test
    void equalityOnCurieIdNormalisesPrefixAndEscapes() {
        SolrQuery query = SssomQueryBuilder.buildFieldValueQuery(
                MappingEnum.OBJECT_ID, "mondo:0005148", true, PAGE_OF_TEN);

        String expected = MappingEnum.OBJECT_ID.getField() + ":\""
                + ClientUtils.escapeQueryChars(normalisedCurie("mondo:0005148")) + "\"";
        assertThat(query.getFilterQueries()).contains(expected);
    }

    @Test
    void equalityOnIriValueTargetsIriField() {
        String iri = "http://purl.obolibrary.org/obo/MONDO_0005148";
        SolrQuery query = SssomQueryBuilder.buildFieldValueQuery(
                MappingEnum.OBJECT_ID, iri, true, PAGE_OF_TEN);

        String expected = MappingEnum.OBJECT_IRI.getField() + ":\""
                + ClientUtils.escapeQueryChars(iri) + "\"";
        assertThat(query.getFilterQueries()).contains(expected);
    }

    @Test
    void containsOnLabelUsesNgramWordClauses() {
        SolrQuery query = SssomQueryBuilder.buildMappingsQuery(
                List.of(new SssomFilter(MappingEnum.OBJECT_LABEL, Operator.CONTAINS, "heart disease")),
                true, PAGE_OF_TEN);

        String ngram = MappingEnum.OBJECT_LABEL.getField() + "_ngram";
        String expected = "(" + ngram + ":*" + ClientUtils.escapeQueryChars("heart") + "*"
                + " AND " + ngram + ":*" + ClientUtils.escapeQueryChars("disease") + "*)";
        assertThat(query.getFilterQueries()).contains(expected);
    }

    @Test
    void rangeOperatorsProduceBoundedRanges() {
        SolrQuery ge = SssomQueryBuilder.buildMappingsQuery(
                List.of(new SssomFilter(MappingEnum.CONFIDENCE, Operator.GE, "0.8")), true, PAGE_OF_TEN);
        SolrQuery lt = SssomQueryBuilder.buildMappingsQuery(
                List.of(new SssomFilter(MappingEnum.CONFIDENCE, Operator.LT, "0.5")), true, PAGE_OF_TEN);

        assertThat(ge.getFilterQueries()).contains("confidence:[0.8 TO *]");
        assertThat(lt.getFilterQueries()).contains("confidence:[* TO 0.5}");
    }

    @Test
    void rangeValueWithQuerySyntaxIsRejected() {
        assertThatThrownBy(() -> SssomQueryBuilder.buildMappingsQuery(
                List.of(new SssomFilter(MappingEnum.CONFIDENCE, Operator.GE, INJECTION)),
                true, PAGE_OF_TEN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equalityValueIsEscapedNotInjected() {
        // mapping_set_id is a plain string field (no prefix normalisation), so this isolates escaping.
        SolrQuery query = SssomQueryBuilder.buildFieldValueQuery(
                MappingEnum.MAPPING_SET_ID, INJECTION, true, PAGE_OF_TEN);

        String fq = Arrays.stream(query.getFilterQueries())
                .filter(clause -> clause.startsWith(MappingEnum.MAPPING_SET_ID.getField()))
                .findFirst().orElseThrow();
        assertThat(fq).contains(ClientUtils.escapeQueryChars(INJECTION));
        assertThat(fq).doesNotContain("\" OR *:*");
    }

    @Test
    void justificationEqualityIsPrefixNormalised() {
        // mapping_justification is an entity-reference slot: stored with an upper-cased prefix, so the
        // query value is normalised the same way (semapv: -> SEMAPV:).
        SolrQuery query = SssomQueryBuilder.buildFieldValueQuery(
                MappingEnum.MAPPING_JUSTIFICATION, "semapv:LexicalMatching", true, PAGE_OF_TEN);

        String expected = MappingEnum.MAPPING_JUSTIFICATION.getField() + ":\""
                + ClientUtils.escapeQueryChars(normalisedCurie("semapv:LexicalMatching")) + "\"";
        assertThat(query.getFilterQueries()).contains(expected);
        assertThat(normalisedCurie("semapv:LexicalMatching")).isEqualTo("SEMAPV:LexicalMatching");
    }

    // ---------- entities query ----------

    @Test
    void entitiesQueryMatchesEitherSidePerCurie() {
        SearchEntityRequest request = new SearchEntityRequest();
        request.setCuries(List.of("MONDO:0005148"));

        SolrQuery query = SssomQueryBuilder.buildEntitiesQuery(request, true, PAGE_OF_TEN);

        String normalised = normalisedCurie("MONDO:0005148");
        String subjectClause = MappingEnum.SUBJECT_ID.getField() + ":\""
                + ClientUtils.escapeQueryChars(normalised) + "\"";
        String objectClause = MappingEnum.OBJECT_ID.getField() + ":\""
                + ClientUtils.escapeQueryChars(normalised) + "\"";
        assertThat(query.getQuery()).contains(subjectClause).contains(objectClause).contains(" OR ");
    }

    @Test
    void entitiesJustificationAndPredicateBecomeFilterQueries() {
        SearchEntityRequest request = new SearchEntityRequest();
        request.setCuries(List.of("MONDO:0005148"));
        request.setMappingJustification(List.of("semapv:LexicalMatching"));
        request.setPredicateId(List.of("skos:exactMatch"));

        SolrQuery query = SssomQueryBuilder.buildEntitiesQuery(request, true, PAGE_OF_TEN);

        assertThat(query.getFilterQueries()).anySatisfy(clause ->
                assertThat(clause).contains(MappingEnum.MAPPING_JUSTIFICATION.getField())
                        .contains(ClientUtils.escapeQueryChars(normalisedCurie("semapv:LexicalMatching"))));
        assertThat(query.getFilterQueries()).anySatisfy(clause ->
                assertThat(clause).contains(MappingEnum.PREDICATE_ID.getField()));
    }

    // ---------- ranking, collapse and facets are applied ----------

    @Test
    void mappingsQueryAppliesEdismaxCollapseAndFacets() {
        SolrQuery query = SssomQueryBuilder.buildMappingsQuery(List.of(), true, PAGE_OF_TEN);

        assertThat(query.get(SolrConstants.DEF_TYPE)).isEqualTo(SolrConstants.EDISMAX);
        assertThat(query.get(SolrConstants.BOOST)).isNotBlank();
        assertThat(query.getBool(SolrConstants.EXPAND, false)).isTrue();
        assertThat(query.getFacetFields()).contains(
                MappingEnum.MAPPING_JUSTIFICATION.getField(), MappingEnum.PREDICATE_ID.getField());
        assertThat(query.get("stats.field")).isEqualTo(MappingEnum.CONFIDENCE.getField());
    }

    @Test
    void relevanceIsPrimarySortWithSpoKeyTiebreaker() {
        SolrQuery query = SssomQueryBuilder.buildMappingsQuery(List.of(), true, PAGE_OF_TEN);

        // score desc must precede the collapse's spo_key tiebreaker, or the page would order by the
        // opaque spo_key hash and the provenance boost would be dead weight.
        assertThat(query.getSorts()).isNotEmpty();
        assertThat(query.getSorts().get(0).getItem()).isEqualTo(SolrConstants.SCORE);
        assertThat(query.getSorts().get(0).getOrder()).isEqualTo(SolrQuery.ORDER.desc);
        assertThat(query.getSorts()).anySatisfy(
                sort -> assertThat(sort.getItem()).isEqualTo(MappingEnum.SPO_KEY.getField()));
    }

    @Test
    void noWeakPredicateExclusionOnSssomSurface() {
        SolrQuery query = SssomQueryBuilder.buildMappingsQuery(List.of(), true, PAGE_OF_TEN);

        assertThat(query.getFilterQueries() == null ? new String[0] : query.getFilterQueries())
                .noneSatisfy(clause -> assertThat(clause).contains("hasDbXref"));
    }

    @Test
    void exportQueryIsFlatAndUniqueKeySorted() {
        SolrQuery paged = SssomQueryBuilder.buildMappingsQuery(
                List.of(new SssomFilter(MappingEnum.PREDICATE_ID, Operator.EQ, "skos:exactMatch")),
                true, PAGE_OF_TEN);

        SolrQuery export = SssomQueryBuilder.toExportQuery(paged);

        assertThat(export.getSortField()).isEqualTo("id asc");
        assertThat(export.getFilterQueries()).noneSatisfy(
                clause -> assertThat(clause).startsWith("{!collapse"));
        // the predicate filter survives the flattening
        assertThat(export.getFilterQueries()).anySatisfy(
                clause -> assertThat(clause).contains(MappingEnum.PREDICATE_ID.getField()));
    }

    @Test
    void byMappingIdQueryMatchesMappingIdFieldOnly() {
        SolrQuery query = SssomQueryBuilder.buildByMappingIdQuery("0b3d1f2a-6c4e-3a2b-9f10-2c8e7d6a5b4c");

        assertThat(query.getQuery()).isEqualTo(MappingEnum.MAPPING_ID.getField()
                + ":\"" + ClientUtils.escapeQueryChars("0b3d1f2a-6c4e-3a2b-9f10-2c8e7d6a5b4c") + "\"");
        assertThat(query.getBool(SolrConstants.EXPAND, false)).isFalse();
    }

    @Test
    void byMappingIdQueryReturnsTheFullDocument() {
        // No field list: the mapping-details page renders provenance, mapping-set metadata,
        // explanation and asserted_mappings, none of which are in MINIMAL_LIST_OF_FIELDS.
        SolrQuery query = SssomQueryBuilder.buildByMappingIdQuery("0b3d1f2a-6c4e-3a2b-9f10-2c8e7d6a5b4c");

        assertThat(query.getFields()).isNull();
    }
}
