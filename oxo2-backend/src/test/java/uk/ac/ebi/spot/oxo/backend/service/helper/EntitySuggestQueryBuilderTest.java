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

    /**
     * Bucket NAMES. The default suggest reads the live twins (ADR-0045) — only mappings with no obsolete
     * endpoint — and the unrestricted buckets only when includeObsolete is set.
     */
    private static final String STRONG = EntityConstants.STRONG_BUCKET;
    private static final String STRONG_LIVE = EntityConstants.bucketFor(STRONG, false);
    private static final String XREF = WeakPredicate.HAS_DB_XREF.bucket();
    private static final String XREF_LIVE = EntityConstants.bucketFor(XREF, false);
    private static final String SUBCLASS_LIVE =
            EntityConstants.bucketFor(WeakPredicate.SUB_CLASS_OF.bucket(), false);

    // Count FIELDS the default (live) path uses.
    private static final String SUBJECT_STRONG_LIVE = EntityConstants.subjectCountField(STRONG_LIVE);
    private static final String OBJECT_STRONG_LIVE = EntityConstants.objectCountField(STRONG_LIVE);
    private static final String SUBJECT_XREF_LIVE = EntityConstants.subjectCountField(XREF_LIVE);
    private static final String SUBJECT_SUBCLASS_LIVE =
            EntityConstants.subjectCountField(SUBCLASS_LIVE);
    private static final String OBJECT_XREF_LIVE = EntityConstants.objectCountField(XREF_LIVE);

    private static SolrQuery suggest(String query) {
        return EntitySuggestQueryBuilder.buildEntitySuggestQuery(
                query, EntitySide.SUBJECT, List.of(), List.of(), false, List.of(), 10);
    }

    private static SolrQuery suggest(EntitySide side, List<WeakPredicate> includeWeakPredicates) {
        return EntitySuggestQueryBuilder.buildEntitySuggestQuery(
                "mel", side, List.of(), includeWeakPredicates, false, List.of(), 10);
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
                .isEqualTo(EntitySuggestQueryBuilder.popularityBoost(List.of(SUBJECT_STRONG_LIVE)));
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

        assertThat(solrQuery.getFilterQueries()).contains(SUBJECT_STRONG_LIVE + ":[1 TO *]");
        assertThat(String.join(" ", solrQuery.getFilterQueries()))
                .doesNotContain(SUBJECT_XREF_LIVE)
                .doesNotContain(SUBJECT_SUBCLASS_LIVE)
                .doesNotContain(EntityConstants.IS_SUBJECT);
    }

    /** Ticking a predicate makes its entities suggestable — and only that one's. */
    @Test
    void tickingHasDbXrefAlsoSuggestsEntitiesWhoseOnlyMappingsAreXrefs() {
        SolrQuery solrQuery = suggest(EntitySide.SUBJECT, List.of(WeakPredicate.HAS_DB_XREF));

        assertThat(solrQuery.getFilterQueries())
                .contains(SUBJECT_STRONG_LIVE + ":[1 TO *] OR " + SUBJECT_XREF_LIVE + ":[1 TO *]");
        assertThat(String.join(" ", solrQuery.getFilterQueries())).doesNotContain(SUBJECT_SUBCLASS_LIVE);
    }

    /** The two checkboxes are independent: subClassOf on, hasDbXref off must not smuggle in xrefs. */
    @Test
    void theTwoPredicatesAreIndependentlySwitchable() {
        SolrQuery solrQuery = suggest(EntitySide.SUBJECT, List.of(WeakPredicate.SUB_CLASS_OF));

        assertThat(solrQuery.getFilterQueries())
                .contains(SUBJECT_STRONG_LIVE + ":[1 TO *] OR " + SUBJECT_SUBCLASS_LIVE + ":[1 TO *]");
        assertThat(String.join(" ", solrQuery.getFilterQueries())).doesNotContain(SUBJECT_XREF_LIVE);
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

        assertThat(boost).contains(SUBJECT_STRONG_LIVE).contains(SUBJECT_XREF_LIVE);
        assertThat(boost)
                .doesNotContain(OBJECT_STRONG_LIVE)
                .doesNotContain(OBJECT_XREF_LIVE)
                .doesNotContain(EntityConstants.MAPPING_COUNT);
    }

    @Test
    void objectSideRestrictsAndRanksOnObjectCounts() {
        SolrQuery solrQuery = suggest(EntitySide.OBJECT, List.of());

        assertThat(solrQuery.getFilterQueries()).contains(OBJECT_STRONG_LIVE + ":[1 TO *]");
        assertThat(solrQuery.get(SolrConstants.BOOST))
                .contains(OBJECT_STRONG_LIVE)
                .doesNotContain(SUBJECT_STRONG_LIVE);
    }

    /** ANY side is still restricted — to entities visible on EITHER side, not to every entity. */
    @Test
    void anySideStillExcludesEntitiesWithNothingVisible() {
        SolrQuery solrQuery = suggest(EntitySide.ANY, List.of());

        assertThat(solrQuery.getFilterQueries())
                .contains(SUBJECT_STRONG_LIVE + ":[1 TO *] OR " + OBJECT_STRONG_LIVE + ":[1 TO *]");
    }

    /** A null side must not mean "no restriction" — the default is the subject side (ADR-0030). */
    @Test
    void nullSideDefaultsToSubject() {
        SolrQuery solrQuery = suggest(null, List.of());

        assertThat(solrQuery.getFilterQueries()).contains(SUBJECT_STRONG_LIVE + ":[1 TO *]");
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
                .contains(SUBJECT_STRONG_LIVE + ":[1 TO *] OR " + SUBJECT_XREF_LIVE + ":[1 TO *]");
        assertThat(solrQuery.get(SolrConstants.BOOST)).isEqualTo(
                EntitySuggestQueryBuilder.popularityBoost(List.of(SUBJECT_STRONG_LIVE, SUBJECT_XREF_LIVE)));
        assertThat(solrQuery.getFields()).contains(
                EntitySuggestQueryBuilder.VISIBLE_MAPPING_COUNT
                        + ":sum(" + SUBJECT_STRONG_LIVE + "," + SUBJECT_XREF_LIVE + ")");
    }

    @Test
    void prefixFilterIsAnOrOfEscapedPrefixes() {
        SolrQuery solrQuery = EntitySuggestQueryBuilder.buildEntitySuggestQuery(
                "mel", EntitySide.SUBJECT, List.of("MONDO", "EFO"), List.of(), false, List.of(), 10);

        assertThat(solrQuery.getFilterQueries())
                .contains(EntityConstants.PREFIX + ":" + ClientUtils.escapeQueryChars("MONDO")
                        + " OR " + EntityConstants.PREFIX + ":" + ClientUtils.escapeQueryChars("EFO"));
    }

    @Test
    void blankPrefixesAddNoPrefixFilter() {
        SolrQuery solrQuery = EntitySuggestQueryBuilder.buildEntitySuggestQuery(
                "mel", EntitySide.ANY, List.of("  ", ""), List.of(), false, List.of(), 10);

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
                "mel", EntitySide.SUBJECT, List.of(), List.of(), false, List.of(), 10_000);

        assertThat(solrQuery.getRows()).isEqualTo(EntitySuggestQueryBuilder.MAX_SUGGEST_ROWS);
    }

    @Test
    void rowsAreAtLeastOne() {
        SolrQuery solrQuery = EntitySuggestQueryBuilder.buildEntitySuggestQuery(
                "mel", EntitySide.SUBJECT, List.of(), List.of(), false, List.of(), 0);

        assertThat(solrQuery.getRows()).isEqualTo(1);
    }

    @Test
    void obsoleteEntitiesAreHiddenByDefault() {
        SolrQuery solrQuery = EntitySuggestQueryBuilder.buildEntitySuggestQuery(
                "mel", EntitySide.SUBJECT, List.of(), List.of(), false, List.of(), 10);

        assertThat(solrQuery.getFilterQueries())
                .contains("*:* -" + EntityConstants.OBSOLETE + ":true");
    }

    @Test
    void includeObsoleteAddsNoObsoleteFilter() {
        SolrQuery solrQuery = EntitySuggestQueryBuilder.buildEntitySuggestQuery(
                "mel", EntitySide.SUBJECT, List.of(), List.of(), true, List.of(), 10);

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

    // ---------------------------------------------------------------------------------------------
    // Mapping-set restriction (ADR-0044)
    // ---------------------------------------------------------------------------------------------

    private static final String INFERENCES = "https://www.ebi.ac.uk/oxo2/inferences";
    private static final String CURATED = "https://w3id.org/oxo2/test/curated";

    private static SolrQuery suggestInSets(EntitySide side, List<WeakPredicate> weakPredicates,
                                           List<String> mappingSetIds) {
        return EntitySuggestQueryBuilder.buildEntitySuggestQuery(
                "mel", side, List.of(), weakPredicates, false, mappingSetIds, 10);
    }

    private static String scope(String mappingSetId, boolean asSubject, String bucket) {
        return EntityConstants.SET_SCOPE + ":\"" + ClientUtils.escapeQueryChars(
                EntityConstants.setScopeToken(mappingSetId, asSubject, bucket)) + "\"";
    }

    private static String setScopeFilterOf(SolrQuery solrQuery) {
        return java.util.Arrays.stream(solrQuery.getFilterQueries())
                .filter(filterQuery -> filterQuery.contains(EntityConstants.SET_SCOPE + ":"))
                .findFirst()
                .orElse(null);
    }

    /** No selection is the common case, and it must leave the query byte-for-byte as it was. */
    @Test
    void noMappingSetSelectionAddsNoSetFilter() {
        assertThat(String.join(" ", suggest("mel").getFilterQueries()))
                .doesNotContain(EntityConstants.SET_SCOPE);
        assertThat(setScopeFilterOf(suggestInSets(EntitySide.SUBJECT, List.of(), List.of()))).isNull();
    }

    @Test
    void oneMappingSetRestrictsToThatSetOnTheSearchedSide() {
        SolrQuery solrQuery = suggestInSets(EntitySide.SUBJECT, List.of(), List.of(INFERENCES));

        assertThat(setScopeFilterOf(solrQuery))
                .isEqualTo(scope(INFERENCES, true, STRONG_LIVE));
    }

    /**
     * The heart of ADR-0044. A bare {@code mapping_set_id} clause would be satisfied by an entity that
     * is merely the OBJECT of a mapping in the chosen set while being a subject somewhere else — and a
     * subject-side search of that set then returns nothing. Because the set, the side and the bucket
     * live in ONE token, the conjunction is structural: no object-side token can appear here.
     */
    @Test
    void setFilterCarriesTheSideSoAnObjectOnlyMemberIsNotSuggested() {
        SolrQuery solrQuery = suggestInSets(EntitySide.SUBJECT, List.of(), List.of(INFERENCES));

        assertThat(setScopeFilterOf(solrQuery))
                .doesNotContain(EntityConstants.SET_SCOPE_DELIMITER + EntityConstants.OBJECT_SIDE
                        + EntityConstants.SET_SCOPE_DELIMITER);
        assertThat(setScopeFilterOf(solrQuery))
                .isNotEqualTo(scope(INFERENCES, false, STRONG_LIVE));
    }

    /**
     * Same argument one dimension over: an entity whose only mappings in the chosen set are hidden
     * ones must not be suggested until that predicate is ticked (ADR-0035 inside ADR-0044's scope).
     */
    @Test
    void setFilterCarriesThePredicateBucket() {
        SolrQuery unticked = suggestInSets(EntitySide.SUBJECT, List.of(), List.of(INFERENCES));
        assertThat(setScopeFilterOf(unticked))
                .doesNotContain(XREF_LIVE);

        SolrQuery ticked = suggestInSets(EntitySide.SUBJECT, List.of(WeakPredicate.HAS_DB_XREF),
                List.of(INFERENCES));
        assertThat(setScopeFilterOf(ticked)).isEqualTo(
                scope(INFERENCES, true, STRONG_LIVE)
                        + " OR " + scope(INFERENCES, true, XREF_LIVE));
    }

    /** Several sets, several buckets: the full cross product, OR-ed, so any one combination suffices. */
    @Test
    void severalSetsAndBucketsExpandToTheCrossProduct() {
        SolrQuery solrQuery = suggestInSets(EntitySide.ANY, List.of(WeakPredicate.SUB_CLASS_OF),
                List.of(INFERENCES, CURATED));

        assertThat(setScopeFilterOf(solrQuery)).isEqualTo(String.join(" OR ",
                scope(INFERENCES, true, STRONG_LIVE),
                scope(INFERENCES, true, SUBCLASS_LIVE),
                scope(INFERENCES, false, STRONG_LIVE),
                scope(INFERENCES, false, SUBCLASS_LIVE),
                scope(CURATED, true, STRONG_LIVE),
                scope(CURATED, true, SUBCLASS_LIVE),
                scope(CURATED, false, STRONG_LIVE),
                scope(CURATED, false, SUBCLASS_LIVE)));
    }

    @Test
    void blankMappingSetIdsAddNoSetFilter() {
        SolrQuery solrQuery = suggestInSets(EntitySide.SUBJECT, List.of(), java.util.Arrays.asList(
                "  ", "", null));

        assertThat(setScopeFilterOf(solrQuery)).isNull();
        assertThat(solrQuery.getFields())
                .contains(EntitySuggestQueryBuilder.VISIBLE_MAPPING_COUNT + ":sum(");
    }

    /** A set id is a full IRI, so the token is escaped whole — its ':' and '/' cannot parse as syntax. */
    @Test
    void mappingSetIdIsEscapedAndTrimmed() {
        SolrQuery solrQuery = suggestInSets(EntitySide.SUBJECT, List.of(),
                List.of("  " + INJECTION_PAYLOAD + "  "));

        String filterQuery = setScopeFilterOf(solrQuery);
        assertThat(filterQuery).isEqualTo(
                scope(INJECTION_PAYLOAD, true, STRONG_LIVE));
        assertThat(filterQuery).doesNotContain(INJECTION_PAYLOAD);
        assertThat(filterQuery.replace(ClientUtils.escapeQueryChars(
                EntityConstants.setScopeToken(INJECTION_PAYLOAD, true, STRONG_LIVE)),
                "")).doesNotContain("*:*");
    }

    /**
     * The counts in the index are corpus-wide, so under a restriction there is no honest number to
     * report — the field is left off entirely rather than filled with one that overstates the rows.
     */
    @Test
    void restrictedSuggestAsksForNoVisibleCount() {
        SolrQuery solrQuery = suggestInSets(EntitySide.SUBJECT, List.of(), List.of(INFERENCES));

        assertThat(solrQuery.getFields())
                .doesNotContain(EntitySuggestQueryBuilder.VISIBLE_MAPPING_COUNT)
                .contains(EntityConstants.ID)
                .contains(EntityConstants.LABEL)
                .contains(EntityConstants.IRI)
                .contains(EntityConstants.PREFIX);
    }

    /** Suppressing the count must not cost the ranking: popularity still orders the restricted list. */
    @Test
    void restrictedSuggestStillRanksByPopularity()  {
        SolrQuery solrQuery = suggestInSets(EntitySide.SUBJECT, List.of(), List.of(INFERENCES));

        assertThat(solrQuery.get(SolrConstants.BOOST))
                .isEqualTo(EntitySuggestQueryBuilder.popularityBoost(List.of(SUBJECT_STRONG_LIVE)));
    }

    /** The cheap bucket filter stays a separate fq so the unrestricted case keeps its own cache entry. */
    @Test
    void setFilterIsAddedAlongsideNotInsteadOfTheVisibleBucketFilter() {
        SolrQuery solrQuery = suggestInSets(EntitySide.SUBJECT, List.of(), List.of(INFERENCES));

        assertThat(solrQuery.getFilterQueries())
                .contains(SUBJECT_STRONG_LIVE + ":[1 TO *]")
                .contains(scope(INFERENCES, true, STRONG_LIVE));
    }

    // ---------------------------------------------------------------------------------------------
    // Obsolete endpoints: the live buckets (ADR-0045)
    // ---------------------------------------------------------------------------------------------

    private static SolrQuery suggestObsolete(EntitySide side, List<WeakPredicate> weakPredicates,
                                             boolean includeObsolete, List<String> mappingSetIds) {
        return EntitySuggestQueryBuilder.buildEntitySuggestQuery(
                "mel", side, List.of(), weakPredicates, includeObsolete, mappingSetIds, 10);
    }

    /**
     * The point of ADR-0045. The default search hides a row when EITHER endpoint is obsolete, so a live
     * entity whose every mapping points AT an obsolete term returns nothing — and the {@code obsolete}
     * field cannot express that, because the entity is not obsolete. So the default suggest must filter
     * on the live twin, never on the unrestricted bucket.
     */
    @Test
    void defaultSuggestFiltersOnTheLiveBucket() {
        SolrQuery solrQuery = suggestObsolete(EntitySide.SUBJECT, List.of(), false, List.of());

        assertThat(solrQuery.getFilterQueries()).contains(SUBJECT_STRONG_LIVE + ":[1 TO *]");
        assertThat(String.join(" ", solrQuery.getFilterQueries()))
                .doesNotContain(EntityConstants.SUBJECT_COUNT_STRONG + ":[1 TO *]");
    }

    /** With obsolete rows shown, the search returns them, so the suggest reads the unrestricted bucket. */
    @Test
    void includeObsoleteSuggestFiltersOnTheUnrestrictedBucket() {
        SolrQuery solrQuery = suggestObsolete(EntitySide.SUBJECT, List.of(), true, List.of());

        assertThat(solrQuery.getFilterQueries())
                .contains(EntityConstants.SUBJECT_COUNT_STRONG + ":[1 TO *]");
        assertThat(String.join(" ", solrQuery.getFilterQueries()))
                .doesNotContain(EntityConstants.LIVE_BUCKET_SUFFIX);
    }

    /**
     * The count and the ranking have to move with the filter, or the row shows a number bigger than the
     * table it opens — the ADR-0035 defect in miniature. This is what was wrong before ADR-0045:
     * EFO:0006471 was offered with mapping_count 1 while its only mapping was hidden.
     */
    @Test
    void liveBucketsDriveTheCountAndTheBoostToo() {
        SolrQuery solrQuery = suggestObsolete(EntitySide.SUBJECT, List.of(WeakPredicate.HAS_DB_XREF),
                false, List.of());

        assertThat(solrQuery.get(SolrConstants.BOOST)).isEqualTo(
                EntitySuggestQueryBuilder.popularityBoost(
                        List.of(SUBJECT_STRONG_LIVE, SUBJECT_XREF_LIVE)));
        assertThat(solrQuery.getFields()).contains(EntitySuggestQueryBuilder.VISIBLE_MAPPING_COUNT
                + ":sum(" + SUBJECT_STRONG_LIVE + "," + SUBJECT_XREF_LIVE + ")");
    }

    /** Weak predicates and obsolescence are independent dimensions: ticking one keeps the other's twin. */
    @Test
    void weakPredicateBucketsAlsoComeInLiveVariants() {
        SolrQuery solrQuery = suggestObsolete(EntitySide.SUBJECT, List.of(WeakPredicate.HAS_DB_XREF),
                false, List.of());

        assertThat(solrQuery.getFilterQueries()).contains(
                SUBJECT_STRONG_LIVE + ":[1 TO *] OR " + SUBJECT_XREF_LIVE + ":[1 TO *]");
    }

    /** All three dimensions compose: the set token carries the live bucket when obsolete rows are hidden. */
    @Test
    void theSetScopeTokenCarriesTheLiveBucket() {
        SolrQuery hidden = suggestObsolete(EntitySide.SUBJECT, List.of(), false, List.of(INFERENCES));
        assertThat(setScopeFilterOf(hidden)).isEqualTo(scope(INFERENCES, true, STRONG_LIVE));

        SolrQuery shown = suggestObsolete(EntitySide.SUBJECT, List.of(), true, List.of(INFERENCES));
        assertThat(setScopeFilterOf(shown)).isEqualTo(scope(INFERENCES, true, STRONG));
    }

    /**
     * The entity-level obsolete filter stays alongside the live buckets. It is now redundant — an
     * obsolete term is an obsolete endpoint of its every mapping, so its live buckets are all zero — but
     * it states a different rule and is the one that survives an inconsistently-stamped reindex.
     */
    @Test
    void theEntityObsoleteFilterIsKeptAlongsideTheLiveBuckets() {
        SolrQuery solrQuery = suggestObsolete(EntitySide.SUBJECT, List.of(), false, List.of());

        assertThat(solrQuery.getFilterQueries())
                .contains("*:* -" + EntityConstants.OBSOLETE + ":true")
                .contains(SUBJECT_STRONG_LIVE + ":[1 TO *]");
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
