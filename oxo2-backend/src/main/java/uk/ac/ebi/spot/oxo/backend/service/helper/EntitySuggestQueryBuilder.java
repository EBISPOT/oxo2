package uk.ac.ebi.spot.oxo.backend.service.helper;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.util.ClientUtils;
import uk.ac.ebi.spot.oxo.model.entity.EntityConstants;
import uk.ac.ebi.spot.oxo.model.entity.EntitySide;
import uk.ac.ebi.spot.oxo.model.sssom.WeakPredicate;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds the entity-typeahead query against the {@code oxo2-entities} collection (ADR-0034).
 *
 * <p>A peer of {@link SolrQueryBuilder}, not an extension of it: a different collection, a different
 * schema, and no {@link uk.ac.ebi.spot.oxo.model.sssom.MappingEnum} — an entity document is not a
 * mapping document, and the same-SPO collapse and inference-type filters mean nothing here.
 *
 * <p><b>The one mapping-side filter that does carry over is the weak-predicate exclusion</b>
 * (ADR-0035). It has to. A suggestion is a promise that searching for that entity returns something,
 * and the search hides {@code rdfs:subClassOf} and {@code oboInOwl:hasDbXref} unless the user has
 * ticked them. Ignoring that here is precisely what made the typeahead offer 46,783 entities on a
 * corpus whose default search could reach only 3,714 of them. So the filter and the popularity boost
 * both run over the count buckets the user's checkboxes currently make visible — never over the
 * totals, which count mappings the search will not show.
 *
 * <p><b>The obsolete switch is honoured on the MAPPING, not just the entity</b> (ADR-0045). The
 * {@code obsolete} field says whether a term is itself obsolete; the search hides a row when EITHER of
 * its endpoints is. So each count bucket has a {@code _live} twin counting only mappings with no
 * obsolete endpoint, and a default suggest reads those. Without them a live entity whose every mapping
 * points at an obsolete term is offered and returns nothing — 73% of subject-side suggestions on the
 * worktree corpus.
 *
 * <p><b>A mapping-set restriction is honoured the same way, and for the same reason</b> (ADR-0044).
 * It cannot be a separate {@code mapping_set_id} clause: side and predicate have to hold WITHIN the
 * chosen set, so all three travel together in one {@link EntityConstants#SET_SCOPE} token. The price is
 * that the displayed count is suppressed under a restriction — the counts are corpus-wide, and
 * reporting one for a set the user has narrowed to would overstate what they get.
 *
 * <p>Deliberately a plain edismax query rather than a Solr {@code SuggestComponent}: a suggester
 * takes no filter query, so it could honour neither the subject-side restriction (ADR-0030) nor the
 * ontology-prefix filter nor these predicate buckets — and it would need a dictionary build step in
 * the dataload. See ADR-0034.
 */
public class EntitySuggestQueryBuilder {

    /**
     * The label STARTS with what was typed (KeywordTokenizer edge-n-gram). The strongest signal:
     * typing "mel" should put "melanoma" above "familial atypical melanoma".
     */
    static final int WHOLE_LABEL_PREFIX_BOOST = 8;

    /** The CURIE starts with what was typed — "MONDO:00…". As decisive as a whole-label prefix. */
    static final int CURIE_PREFIX_BOOST = 6;

    /** The IRI matches exactly. Only fires when a full IRI is pasted; see the class note on IRIs. */
    static final int IRI_EXACT_BOOST = 6;

    /**
     * SOME TOKEN of the label starts with what was typed (StandardTokenizer edge-n-gram) — this is
     * what reaches "malignant melanoma" from "mel". Weakest of the four on purpose: it is the widest
     * net, so it must not outrank a genuine leading-edge match.
     */
    static final int TOKEN_PREFIX_BOOST = 2;

    /**
     * Popularity, over the mappings the current checkbox state actually shows (ADR-0035) — the same
     * count the suggestion row displays, not the entity's grand total. Ranking on the total would
     * float an entity to the top of a subject-side typeahead on the strength of thousands of
     * {@code subClassOf} mappings in which it is the OBJECT and which the search hides anyway.
     *
     * <p>Multiplicative (never an additive {@code bq}) for the same reason the provenance ranking is
     * (ADR-0027): an additive boost is scaled against the text score's idf, so a rare term would swamp
     * the popularity signal rather than being tempered by it. log-damped so a 100k-mapping entity
     * beats a 10k-mapping one without burying it, and {@code sum(...,1)} keeps the argument positive
     * for an entity with no visible mappings — which the filter excludes anyway, but a zero here
     * would make the whole product zero rather than merely unboosted.
     */
    static String popularityBoost(List<String> visibleCountFields) {
        return "sum(1,log(sum(" + String.join(",", visibleCountFields) + ",1)))";
    }

    /**
     * Pseudo-field (an {@code fl} alias over a function query), not a schema field: how many mappings
     * this suggestion will actually return under the current checkbox state. The stored
     * {@code mapping_count} is the total over every predicate and every side, so showing it on the row
     * would promise rows the search then hides.
     */
    public static final String VISIBLE_MAPPING_COUNT = "visible_mapping_count";

    /** Hard cap on rows. A typeahead dropdown shows ten-ish; nobody scrolls a suggestion list. */
    public static final int MAX_SUGGEST_ROWS = 25;

    /**
     * Below this, a prefix is not discriminating enough to be worth a query: one character of a
     * label matches a large fraction of the collection, and the user is still typing.
     */
    public static final int MIN_QUERY_LENGTH = 2;

    private EntitySuggestQueryBuilder() {
    }

    /** True when {@code query} is too short (or absent) to suggest on. */
    public static boolean isTooShort(String query) {
        return query == null || query.strip().length() < MIN_QUERY_LENGTH;
    }

    /**
     * @param query                 what the user has typed so far
     * @param side                  which side of a mapping the entity must appear on; the main search
     *                              box passes {@link EntitySide#SUBJECT} (ADR-0030)
     * @param prefixes              restrict to these CURIE prefixes (the from/to ontology selectors);
     *                              may be empty
     * @param includeWeakPredicates the weak predicates the user has ticked; empty — the default —
     *                              means suggest only entities with at least one mapping a default
     *                              search would show (ADR-0035)
     * @param mappingSetIds         restrict to entities findable in these mapping sets, on the
     *                              requested side and under a visible predicate (ADR-0044); empty —
     *                              the default — searches every set
     * @param size                  rows wanted, capped at {@link #MAX_SUGGEST_ROWS}
     */
    public static SolrQuery buildEntitySuggestQuery(String query, EntitySide side,
                                                    List<String> prefixes,
                                                    List<WeakPredicate> includeWeakPredicates,
                                                    boolean includeObsolete,
                                                    List<String> mappingSetIds,
                                                    int size) {
        String escaped = ClientUtils.escapeQueryChars(query.strip());

        // Each clause is QUOTED, so each field's own analyzer sees the WHOLE typed string. That is
        // what makes the two label fields behave differently: on label_prefix_ngram (KeywordTokenizer)
        // "malignant mel" is one term and matches as a whole-string prefix, while on label_ngram
        // (StandardTokenizer) it is a phrase whose last token is a prefix. Letting edismax split on
        // whitespace (its default) would destroy the first of those. Fielded clauses parse through
        // edismax unchanged — the same thing SolrQueryBuilder's classified/advanced paths rely on.
        String disjunction = "(" + EntityConstants.LABEL_PREFIX_NGRAM + ":\"" + escaped + "\"^" + WHOLE_LABEL_PREFIX_BOOST
                + " OR " + EntityConstants.ID_PREFIX_NGRAM + ":\"" + escaped + "\"^" + CURIE_PREFIX_BOOST
                + " OR " + EntityConstants.IRI + ":\"" + escaped + "\"^" + IRI_EXACT_BOOST
                + " OR " + EntityConstants.LABEL_NGRAM + ":\"" + escaped + "\"^" + TOKEN_PREFIX_BOOST
                + ")";

        // The one list that drives the filter, the boost, the displayed count AND the mapping-set
        // filter, so none of them can drift apart: an entity is suggestable exactly when one of these
        // buckets is non-zero (and, under a set restriction, non-zero IN one of the chosen sets), is
        // ranked by their sum, and shows that sum as its mapping count.
        List<VisibleBucket> visibleBuckets = visibleBuckets(side, includeWeakPredicates, includeObsolete);
        List<String> visibleCountFields = visibleBuckets.stream().map(VisibleBucket::countField).toList();

        SolrQuery solrQuery = new SolrQuery();
        solrQuery.setQuery(disjunction);
        solrQuery.set(SolrConstants.DEF_TYPE, SolrConstants.EDISMAX);
        solrQuery.set(SolrConstants.BOOST, popularityBoost(visibleCountFields));

        solrQuery.addFilterQuery(visibleFilter(visibleCountFields));
        String prefixFilter = prefixFilter(prefixes);
        if (prefixFilter != null) {
            solrQuery.addFilterQuery(prefixFilter);
        }

        // ADR-0044: the mapping-set restriction. Kept as its own filter query even though it subsumes
        // the visible-bucket filter above, because the two are cached independently by Solr and the
        // unrestricted case — the overwhelming majority of queries — must keep hitting the cheap one.
        String setScopeFilter = setScopeFilter(visibleBuckets, mappingSetIds);
        if (setScopeFilter != null) {
            solrQuery.addFilterQuery(setScopeFilter);
        }

        // ADR-0041: hide obsolete entities unless the caller opts in, so a suggestion is never a term the
        // default search would then hide. The leading *:* is required because Solr matches nothing for an
        // only-negative filter; an absent flag (a pre-reindex doc) reads not-obsolete.
        //
        // Kept even though ADR-0045's live buckets now subsume it — an obsolete term is an obsolete
        // endpoint of its every mapping, so all its live buckets are zero and the bucket filter already
        // excludes it. It states a different rule ("do not offer obsolete terms") from the buckets ("only
        // count rows the search shows"), and it is the one that still holds if the dataload ever stamps
        // the two inconsistently. One cached filter query is a cheap guard against that.
        if (!includeObsolete) {
            solrQuery.addFilterQuery("*:* -" + EntityConstants.OBSOLETE + ":true");
        }

        // The count is asked for only when it would be true (ADR-0044). The buckets are corpus-wide, so
        // under a mapping-set restriction their sum counts mappings from sets the user has excluded —
        // it would be the same broken promise as ADR-0035's, one level down: the rows exist, but not as
        // many as the number claims. Omitting the pseudo-field leaves mapping_count absent on the
        // response, and the suggestion row then shows no count rather than a wrong one.
        if (setScopeFilter == null) {
            solrQuery.setFields(EntityConstants.ID, EntityConstants.LABEL, EntityConstants.IRI,
                    EntityConstants.PREFIX,
                    VISIBLE_MAPPING_COUNT + ":sum(" + String.join(",", visibleCountFields) + ")");
        } else {
            solrQuery.setFields(EntityConstants.ID, EntityConstants.LABEL, EntityConstants.IRI,
                    EntityConstants.PREFIX);
        }
        solrQuery.setRows(Math.min(Math.max(size, 1), MAX_SUGGEST_ROWS));
        return solrQuery;
    }

    /**
     * One way an entity can currently be found: a side, a predicate bucket, and the count field that
     * counts it. The three are carried together so the count filter and the {@code set_scope} filter
     * are built from the same list and cannot come to disagree about what "visible" means.
     *
     * @param countField the {@code oxo2-entities} count field, e.g. {@code subject_count_hasdbxref}
     * @param asSubject  true for the subject side of a mapping, false for the object side
     * @param bucket     the predicate bucket, e.g. {@code strong} or {@code hasdbxref}
     */
    private record VisibleBucket(String countField, boolean asSubject, String bucket) {

        /** This bucket's {@code set_scope} term for one mapping set, escaped for the query parser. */
        String scopeClause(String mappingSetId) {
            return EntityConstants.SET_SCOPE + ":\"" + ClientUtils.escapeQueryChars(
                    EntityConstants.setScopeToken(mappingSetId, asSubject, bucket)) + "\"";
        }
    }

    /**
     * The ways a search would currently draw rows: the strong bucket on the side being searched, plus a
     * bucket for each weak predicate the user has ticked, each in its live or unrestricted variant
     * depending on whether obsolete rows are being shown.
     *
     * <p>All three halves of this matter. Restricting to the SIDE is what stops the main box offering an
     * entity that only ever appears as an object — under ADR-0030's subject-side search that
     * completes to no rows. Restricting to the visible PREDICATES is what stops it offering an entity
     * whose every mapping is one the search hides (ADR-0035). Reading the LIVE buckets is what stops it
     * offering an entity whose every mapping points at an obsolete term (ADR-0045) — that entity is not
     * obsolete itself, so the {@code obsolete} filter lets it through, and its rows are hidden anyway.
     * Same failure three times, same fix three times.
     */
    private static List<VisibleBucket> visibleBuckets(EntitySide side,
                                                      List<WeakPredicate> includeWeakPredicates,
                                                      boolean includeObsolete) {
        EntitySide effective = side == null ? EntitySide.DEFAULT : side;
        List<WeakPredicate> included =
                includeWeakPredicates == null ? List.of() : includeWeakPredicates;

        List<VisibleBucket> buckets = new ArrayList<>();
        if (effective != EntitySide.OBJECT) {
            addBuckets(buckets, true, included, includeObsolete);
        }
        if (effective != EntitySide.SUBJECT) {
            addBuckets(buckets, false, included, includeObsolete);
        }
        return buckets;
    }

    /** One side's buckets: strong first, then one per ticked weak predicate. */
    private static void addBuckets(List<VisibleBucket> buckets, boolean asSubject,
                                   List<WeakPredicate> included, boolean includeObsolete) {
        List<String> bucketNames = new ArrayList<>();
        bucketNames.add(EntityConstants.STRONG_BUCKET);
        included.forEach(predicate -> bucketNames.add(predicate.bucket()));

        for (String baseBucket : bucketNames) {
            String bucket = EntityConstants.bucketFor(baseBucket, includeObsolete);
            String countField = asSubject
                    ? EntityConstants.subjectCountField(bucket)
                    : EntityConstants.objectCountField(bucket);
            buckets.add(new VisibleBucket(countField, asSubject, bucket));
        }
    }

    /** Suggestable iff at least one visible bucket is non-empty — i.e. iff the search returns a row. */
    private static String visibleFilter(List<String> visibleCountFields) {
        return visibleCountFields.stream()
                .map(field -> field + ":[1 TO *]")
                .collect(Collectors.joining(" OR "));
    }

    /**
     * Restricts the suggest to entities findable in one of {@code mappingSetIds} (ADR-0044), or null
     * when no set was chosen — the usual case, and the one that must add no filter at all.
     *
     * <p>An OR over the cross product of the chosen sets and the currently visible buckets, as exact
     * {@code set_scope} terms. Spelling out the product is what makes side and predicate hold WITHIN a
     * chosen set: {@code mapping_set_id:B AND subject_count_strong:[1 TO *]} is satisfied by an entity
     * that is a subject somewhere else and merely an object in B, and completes to no rows. The product
     * stays small — a handful of buckets times the sets the user actually ticked.
     */
    private static String setScopeFilter(List<VisibleBucket> visibleBuckets,
                                         List<String> mappingSetIds) {
        if (mappingSetIds == null || mappingSetIds.isEmpty()) {
            return null;
        }
        String clause = mappingSetIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .flatMap(id -> visibleBuckets.stream().map(bucket -> bucket.scopeClause(id.strip())))
                .collect(Collectors.joining(" OR "));
        return clause.isBlank() ? null : clause;
    }

    /** Mirrors {@code SolrQueryBuilder.addPrefixFilter}: an OR of escaped prefix terms. */
    private static String prefixFilter(List<String> prefixes) {
        if (prefixes == null || prefixes.isEmpty()) {
            return null;
        }
        String clause = prefixes.stream()
                .filter(prefix -> prefix != null && !prefix.isBlank())
                .map(prefix -> EntityConstants.PREFIX + ":" + ClientUtils.escapeQueryChars(prefix.strip()))
                .collect(Collectors.joining(" OR "));
        return clause.isBlank() ? null : clause;
    }
}
