package uk.ac.ebi.spot.oxo.backend.service.helper;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.spot.oxo.model.entity.EntityConstants;
import uk.ac.ebi.spot.oxo.model.entity.EntitySide;
import uk.ac.ebi.spot.oxo.model.sssom.WeakPredicate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives {@link EntitySuggestQueryBuilder} through its public entry point and asserts on the
 * observable properties of the {@link SolrQuery} it produces — the same contract
 * {@link SolrQueryBuilderTest} follows. Expected escaped strings are computed with
 * {@link ClientUtils#escapeQueryChars} as the escape oracle, never hard-coded, so the tests cannot
 * drift from Solr's own escaping rules.
 */
class EntitySuggestQueryBuilderTest {

    private static final String INJECTION_PAYLOAD = "a\" OR *:* OR x";

    private static final String SUBJECT_STRONG = EntityConstants.SUBJECT_COUNT_STRONG;
    private static final String OBJECT_STRONG = EntityConstants.OBJECT_COUNT_STRONG;
    private static final String SUBJECT_XREF =
            EntityConstants.subjectCountField(WeakPredicate.HAS_DB_XREF);
    private static final String SUBJECT_SUBCLASS =
            EntityConstants.subjectCountField(WeakPredicate.SUB_CLASS_OF);
    private static final String OBJECT_XREF =
            EntityConstants.objectCountField(WeakPredicate.HAS_DB_XREF);

    private static SolrQuery suggest(String query) {
        return EntitySuggestQueryBuilder.buildEntitySuggestQuery(
                query, EntitySide.SUBJECT, List.of(), List.of(), false, 10);
    }

    private static SolrQuery suggest(EntitySide side, List<WeakPredicate> includeWeakPredicates) {
        return EntitySuggestQueryBuilder.buildEntitySuggestQuery(
                "mel", side, List.of(), includeWeakPredicates, false, 10);
    }

    @Test
    void queryIsABoostedDisjunctionOverTheFourMatchFields() {
        String q = suggest("mel").getQuery();

        assertThat(q)
                .contains(EntityConstants.LABEL_PREFIX_NGRAM + ":\"mel\"^"
                        + EntitySuggestQueryBuilder.WHOLE_LABEL_PREFIX_BOOST)
                .contains(EntityConstants.ID_PREFIX_NGRAM + ":\"mel\"^"
                        + EntitySuggestQueryBuilder.CURIE_PREFIX_BOOST)
                .contains(EntityConstants.IRI + ":\"mel\"^"
                        + EntitySuggestQueryBuilder.IRI_EXACT_BOOST)
                .contains(EntityConstants.LABEL_NGRAM + ":\"mel\"^"
                        + EntitySuggestQueryBuilder.TOKEN_PREFIX_BOOST);
    }

    /**
     * The ranking promise of ADR-0034: typing "mel" must put "melanoma" (whose LABEL starts with the
     * text) above "familial atypical melanoma" (where only a mid-label TOKEN starts with it). That
     * ordering is produced entirely by the relative boosts, so it is the boosts that get pinned.
     */
    @Test
    void wholeLabelPrefixOutranksTokenPrefix() {
        assertThat(EntitySuggestQueryBuilder.WHOLE_LABEL_PREFIX_BOOST)
                .isGreaterThan(EntitySuggestQueryBuilder.TOKEN_PREFIX_BOOST);
        assertThat(EntitySuggestQueryBuilder.CURIE_PREFIX_BOOST)
                .isGreaterThan(EntitySuggestQueryBuilder.TOKEN_PREFIX_BOOST);
    }

    @Test
    void usesEdismaxWithAMultiplicativePopularityBoost() {
        SolrQuery solrQuery = suggest("mel");

        assertThat(solrQuery.get(SolrConstants.DEF_TYPE)).isEqualTo(SolrConstants.EDISMAX);
        assertThat(solrQuery.get(SolrConstants.BOOST))
                .isEqualTo(EntitySuggestQueryBuilder.popularityBoost(List.of(SUBJECT_STRONG)));
    }

    /**
     * Each clause is quoted so the field's own analyzer sees the whole typed string. On
     * label_prefix_ngram (KeywordTokenizer) that is what lets "malignant mel" match as a whole-string
     * prefix; if edismax split it on whitespace, it could not.
     *
     * <p>The whole phrase — space included — goes through the escape oracle, which is why the
     * expectation is computed rather than written out: escapeQueryChars escapes the space too, and
     * Lucene unescapes it back inside the quotes. Same quote-and-escape idiom as
     * {@link SolrQueryBuilder}'s own fielded clauses.
     */
    @Test
    void multiWordInputStaysOnePhrasePerField() {
        String escaped = ClientUtils.escapeQueryChars("malignant mel");

        assertThat(suggest("malignant mel").getQuery())
                .contains(EntityConstants.LABEL_PREFIX_NGRAM + ":\"" + escaped + "\"");
    }

    /** A CURIE must not be split on its colon, or "MONDO:00" could never prefix-match a CURIE. */
    @Test
    void curieInputIsNotSplitOnTheColon() {
        String escaped = ClientUtils.escapeQueryChars("MONDO:00");

        assertThat(suggest("MONDO:00").getQuery())
                .contains(EntityConstants.ID_PREFIX_NGRAM + ":\"" + escaped + "\"");
    }

    // ---------------------------------------------------------------------------------------------
    // Suggest only what the search can show (ADR-0035)
    // ---------------------------------------------------------------------------------------------

    /**
     * The regression test for the bug ADR-0035 fixes. With no weak predicate ticked — the default —
     * an entity is suggestable only if it has a STRONG mapping on the side being searched. Filtering
     * on is_subject instead (i.e. "is the subject of any mapping at all") is what let the typeahead
     * offer 46,783 entities on a corpus whose default search could reach 3,714 of them: pick one of
     * the other 43,069 and the result table came back empty.
     */
    @Test
    void byDefaultSuggestsOnlyEntitiesWithAMappingTheSearchWouldShow() {
        SolrQuery solrQuery = suggest(EntitySide.SUBJECT, List.of());

        assertThat(solrQuery.getFilterQueries()).contains(SUBJECT_STRONG + ":[1 TO *]");
        assertThat(String.join(" ", solrQuery.getFilterQueries()))
                .doesNotContain(SUBJECT_XREF)
                .doesNotContain(SUBJECT_SUBCLASS)
                .doesNotContain(EntityConstants.IS_SUBJECT);
    }

    /** Ticking a predicate makes its entities suggestable — and only that one's. */
    @Test
    void tickingHasDbXrefAlsoSuggestsEntitiesWhoseOnlyMappingsAreXrefs() {
        SolrQuery solrQuery = suggest(EntitySide.SUBJECT, List.of(WeakPredicate.HAS_DB_XREF));

        assertThat(solrQuery.getFilterQueries())
                .contains(SUBJECT_STRONG + ":[1 TO *] OR " + SUBJECT_XREF + ":[1 TO *]");
        assertThat(String.join(" ", solrQuery.getFilterQueries())).doesNotContain(SUBJECT_SUBCLASS);
    }

    /** The two checkboxes are independent: subClassOf on, hasDbXref off must not smuggle in xrefs. */
    @Test
    void theTwoPredicatesAreIndependentlySwitchable() {
        SolrQuery solrQuery = suggest(EntitySide.SUBJECT, List.of(WeakPredicate.SUB_CLASS_OF));

        assertThat(solrQuery.getFilterQueries())
                .contains(SUBJECT_STRONG + ":[1 TO *] OR " + SUBJECT_SUBCLASS + ":[1 TO *]");
        assertThat(String.join(" ", solrQuery.getFilterQueries())).doesNotContain(SUBJECT_XREF);
    }

    /**
     * The other half of the ADR-0035 bug: MONDO:0003847 holds 7 subject-side xrefs and 1,579
     * object-side subClassOf mappings, and the old boost ranked it on all 1,586. A subject-side
     * typeahead must be ranked by subject-side counts alone, or an entity floats to the top of the
     * dropdown on the strength of mappings the user will never be shown.
     */
    @Test
    void subjectSideRankingNeverCountsObjectSideMappings() {
        String boost = suggest(EntitySide.SUBJECT, List.of(WeakPredicate.HAS_DB_XREF))
                .get(SolrConstants.BOOST);

        assertThat(boost).contains(SUBJECT_STRONG).contains(SUBJECT_XREF);
        assertThat(boost)
                .doesNotContain(OBJECT_STRONG)
                .doesNotContain(OBJECT_XREF)
                .doesNotContain(EntityConstants.MAPPING_COUNT);
    }

    @Test
    void objectSideRestrictsAndRanksOnObjectCounts() {
        SolrQuery solrQuery = suggest(EntitySide.OBJECT, List.of());

        assertThat(solrQuery.getFilterQueries()).contains(OBJECT_STRONG + ":[1 TO *]");
        assertThat(solrQuery.get(SolrConstants.BOOST))
                .contains(OBJECT_STRONG)
                .doesNotContain(SUBJECT_STRONG);
    }

    /** ANY side is still restricted — to entities visible on EITHER side, not to every entity. */
    @Test
    void anySideStillExcludesEntitiesWithNothingVisible() {
        SolrQuery solrQuery = suggest(EntitySide.ANY, List.of());

        assertThat(solrQuery.getFilterQueries())
                .contains(SUBJECT_STRONG + ":[1 TO *] OR " + OBJECT_STRONG + ":[1 TO *]");
    }

    /** A null side must not mean "no restriction" — the default is the subject side (ADR-0030). */
    @Test
    void nullSideDefaultsToSubject() {
        SolrQuery solrQuery = suggest(null, List.of());

        assertThat(solrQuery.getFilterQueries()).contains(SUBJECT_STRONG + ":[1 TO *]");
    }

    /**
     * The filter, the boost and the count on the row are three views of one list. If they could
     * disagree, a suggestion could be offered, ranked, or labelled by a different set of mappings from
     * the one the search returns — which is the whole failure mode ADR-0035 closes.
     */
    @Test
    void filterBoostAndDisplayedCountAllUseTheSameBuckets() {
        SolrQuery solrQuery = suggest(EntitySide.SUBJECT, List.of(WeakPredicate.HAS_DB_XREF));

        assertThat(solrQuery.getFilterQueries())
                .contains(SUBJECT_STRONG + ":[1 TO *] OR " + SUBJECT_XREF + ":[1 TO *]");
        assertThat(solrQuery.get(SolrConstants.BOOST)).isEqualTo(
                EntitySuggestQueryBuilder.popularityBoost(List.of(SUBJECT_STRONG, SUBJECT_XREF)));
        assertThat(solrQuery.getFields()).contains(
                EntitySuggestQueryBuilder.VISIBLE_MAPPING_COUNT
                        + ":sum(" + SUBJECT_STRONG + "," + SUBJECT_XREF + ")");
    }

    @Test
    void prefixFilterIsAnOrOfEscapedPrefixes() {
        SolrQuery solrQuery = EntitySuggestQueryBuilder.buildEntitySuggestQuery(
                "mel", EntitySide.SUBJECT, List.of("MONDO", "EFO"), List.of(), false, 10);

        assertThat(solrQuery.getFilterQueries())
                .contains(EntityConstants.PREFIX + ":" + ClientUtils.escapeQueryChars("MONDO")
                        + " OR " + EntityConstants.PREFIX + ":" + ClientUtils.escapeQueryChars("EFO"));
    }

    @Test
    void blankPrefixesAddNoPrefixFilter() {
        SolrQuery solrQuery = EntitySuggestQueryBuilder.buildEntitySuggestQuery(
                "mel", EntitySide.ANY, List.of("  ", ""), List.of(), false, 10);

        assertThat(String.join(" ", solrQuery.getFilterQueries()))
                .doesNotContain(EntityConstants.PREFIX + ":");
    }

    /**
     * The whole typed string is escaped, so a payload carrying a quote and a {@code *:*} cannot break
     * out of its clause and turn the suggest into a match-all.
     */
    @Test
    void injectionPayloadIsFullyEscaped() {
        String q = suggest(INJECTION_PAYLOAD).getQuery();
        String escaped = ClientUtils.escapeQueryChars(INJECTION_PAYLOAD);

        assertThat(q).contains(EntityConstants.LABEL_PREFIX_NGRAM + ":\"" + escaped + "\"");
        // The payload's own *:* survives only in escaped form; no bare match-all reaches the query.
        assertThat(q).doesNotContain("\"" + INJECTION_PAYLOAD + "\"");
        assertThat(q.replace(escaped, "")).doesNotContain("*:*");
    }

    @Test
    void rowsAreCappedAtTheMaximum() {
        SolrQuery solrQuery = EntitySuggestQueryBuilder.buildEntitySuggestQuery(
                "mel", EntitySide.SUBJECT, List.of(), List.of(), false, 10_000);

        assertThat(solrQuery.getRows()).isEqualTo(EntitySuggestQueryBuilder.MAX_SUGGEST_ROWS);
    }

    @Test
    void rowsAreAtLeastOne() {
        SolrQuery solrQuery = EntitySuggestQueryBuilder.buildEntitySuggestQuery(
                "mel", EntitySide.SUBJECT, List.of(), List.of(), false, 0);

        assertThat(solrQuery.getRows()).isEqualTo(1);
    }

    @Test
    void obsoleteEntitiesAreHiddenByDefault() {
        SolrQuery solrQuery = EntitySuggestQueryBuilder.buildEntitySuggestQuery(
                "mel", EntitySide.SUBJECT, List.of(), List.of(), false, 10);

        assertThat(solrQuery.getFilterQueries())
                .contains("*:* -" + EntityConstants.OBSOLETE + ":true");
    }

    @Test
    void includeObsoleteAddsNoObsoleteFilter() {
        SolrQuery solrQuery = EntitySuggestQueryBuilder.buildEntitySuggestQuery(
                "mel", EntitySide.SUBJECT, List.of(), List.of(), true, 10);

        assertThat(String.join(" ", solrQuery.getFilterQueries()))
                .doesNotContain(EntityConstants.OBSOLETE);
    }

    /**
     * The suggestion row renders label, id and IRI, and shows the count. The count must be the
     * computed visible one, never the stored mapping_count — that total counts predicates the search
     * hides, so showing it would promise rows the user will not get.
     */
    @Test
    void fieldListCarriesEverythingTheSuggestionRowRenders() {
        String fieldList = suggest("mel").getFields();

        assertThat(fieldList)
                .contains(EntityConstants.ID)
                .contains(EntityConstants.LABEL)
                .contains(EntityConstants.IRI)
                .contains(EntitySuggestQueryBuilder.VISIBLE_MAPPING_COUNT + ":sum(");
        assertThat(fieldList).doesNotContain("," + EntityConstants.MAPPING_COUNT);
    }

    @Test
    void tooShortRejectsBlankAndSingleCharacterQueries() {
        assertThat(EntitySuggestQueryBuilder.isTooShort(null)).isTrue();
        assertThat(EntitySuggestQueryBuilder.isTooShort("")).isTrue();
        assertThat(EntitySuggestQueryBuilder.isTooShort("  ")).isTrue();
        assertThat(EntitySuggestQueryBuilder.isTooShort("m")).isTrue();
        assertThat(EntitySuggestQueryBuilder.isTooShort(" m ")).isTrue();
        assertThat(EntitySuggestQueryBuilder.isTooShort("me")).isFalse();
    }
}
