package uk.ac.ebi.spot.oxo.backend.service.helper;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.spot.oxo.model.entity.EntityConstants;
import uk.ac.ebi.spot.oxo.model.entity.EntitySide;

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

    private static SolrQuery suggest(String query) {
        return EntitySuggestQueryBuilder.buildEntitySuggestQuery(query, EntitySide.SUBJECT, List.of(), 10);
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
                .isEqualTo(EntitySuggestQueryBuilder.POPULARITY_BOOST)
                .contains(EntityConstants.MAPPING_COUNT);
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

    @Test
    void subjectSideRestrictsToEntitiesThatAppearAsASubject() {
        SolrQuery solrQuery = EntitySuggestQueryBuilder.buildEntitySuggestQuery(
                "mel", EntitySide.SUBJECT, List.of(), 10);

        assertThat(solrQuery.getFilterQueries()).contains(EntityConstants.IS_SUBJECT + ":true");
    }

    @Test
    void objectSideRestrictsToEntitiesThatAppearAsAnObject() {
        SolrQuery solrQuery = EntitySuggestQueryBuilder.buildEntitySuggestQuery(
                "mel", EntitySide.OBJECT, List.of(), 10);

        assertThat(solrQuery.getFilterQueries()).contains(EntityConstants.IS_OBJECT + ":true");
    }

    @Test
    void anySideAddsNoSideFilter() {
        SolrQuery solrQuery = EntitySuggestQueryBuilder.buildEntitySuggestQuery(
                "mel", EntitySide.ANY, List.of(), 10);

        String[] filterQueries = solrQuery.getFilterQueries();
        assertThat(filterQueries == null ? new String[0] : filterQueries)
                .noneMatch(fq -> fq.startsWith(EntityConstants.IS_SUBJECT)
                        || fq.startsWith(EntityConstants.IS_OBJECT));
    }

    /** A null side must not mean "no restriction" — the default is the subject side (ADR-0030). */
    @Test
    void nullSideDefaultsToSubject() {
        SolrQuery solrQuery = EntitySuggestQueryBuilder.buildEntitySuggestQuery(
                "mel", null, List.of(), 10);

        assertThat(solrQuery.getFilterQueries()).contains(EntityConstants.IS_SUBJECT + ":true");
    }

    @Test
    void prefixFilterIsAnOrOfEscapedPrefixes() {
        SolrQuery solrQuery = EntitySuggestQueryBuilder.buildEntitySuggestQuery(
                "mel", EntitySide.SUBJECT, List.of("MONDO", "EFO"), 10);

        assertThat(solrQuery.getFilterQueries())
                .contains(EntityConstants.PREFIX + ":" + ClientUtils.escapeQueryChars("MONDO")
                        + " OR " + EntityConstants.PREFIX + ":" + ClientUtils.escapeQueryChars("EFO"));
    }

    @Test
    void blankPrefixesAddNoFilter() {
        SolrQuery solrQuery = EntitySuggestQueryBuilder.buildEntitySuggestQuery(
                "mel", EntitySide.ANY, List.of("  ", ""), 10);

        String[] filterQueries = solrQuery.getFilterQueries();
        assertThat(filterQueries == null ? new String[0] : filterQueries).isEmpty();
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
                "mel", EntitySide.SUBJECT, List.of(), 10_000);

        assertThat(solrQuery.getRows()).isEqualTo(EntitySuggestQueryBuilder.MAX_SUGGEST_ROWS);
    }

    @Test
    void rowsAreAtLeastOne() {
        SolrQuery solrQuery = EntitySuggestQueryBuilder.buildEntitySuggestQuery(
                "mel", EntitySide.SUBJECT, List.of(), 0);

        assertThat(solrQuery.getRows()).isEqualTo(1);
    }

    /** The suggestion row renders label, id and IRI, and shows the count — all four must come back. */
    @Test
    void fieldListCarriesEverythingTheSuggestionRowRenders() {
        assertThat(suggest("mel").getFields())
                .contains(EntityConstants.ID)
                .contains(EntityConstants.LABEL)
                .contains(EntityConstants.IRI)
                .contains(EntityConstants.MAPPING_COUNT);
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
