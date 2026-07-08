package uk.ac.ebi.spot.oxo.backend.service.helper;

public class SolrConstants {
    public final static String DEF_TYPE = "defType";
    public final static String EDISMAX = "edismax";
    // edismax multiplicative boost function used for the soft provenance-led ranking (ADR-0027,
    // superseding ADR-0011's inference-tier boost). Multiplicative (not additive bq) so the tier
    // boost is independent of term idf: ASSERTED is common (low idf) and SSSOM rare (high idf), so
    // an additive bq would invert the intended order.
    public final static String BOOST = "boost";
    // Relevance, i.e. the RANKING_BOOST applied to the text score. Named explicitly as the fallback
    // sort: an explicit Solr sort replaces score, so without this the collapse tiebreaker (spo_key)
    // would become the primary sort key. See SolrQueryBuilder#constructSortedFields.
    public final static String SCORE = "score";

    // Same-SPO collapse (ADR-0023, superseding the result-grouping mechanism of ADR-0013).
    // CollapsingQParserPlugin keeps one representative per spo_key as a post-filter, so numFound on
    // the collapsed set IS the exact group count — what group.ngroups previously computed by
    // enumerating every group, which is prohibitively slow on high-frequency terms (~19s for
    // "disease": 180k matches / 160k groups). The %s placeholders are the collapse field and the
    // representative sort.
    public final static String COLLAPSE_FQ_TEMPLATE = "{!collapse field=%s sort='%s'}";
    // Representative = highest inference tier (the ADR-0011 score boost); reused for expand ordering.
    public final static String REPRESENTATIVE_SORT = "score desc";
    // ExpandComponent returns each page representative's other members (the rows the detail view
    // inlines, previously group.limit).
    public final static String EXPAND = "expand";
    public final static String EXPAND_FIELD = "expand.field";
    public final static String EXPAND_SORT = "expand.sort";
    public final static String EXPAND_ROWS = "expand.rows";
    public final static int GROUP_MEMBER_LIMIT = 20;
}
