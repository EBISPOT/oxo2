package uk.ac.ebi.spot.oxo.backend.service.helper;

public class SolrConstants {
    public final static String DEF_TYPE = "defType";
    public final static String EDISMAX = "edismax";
    // edismax multiplicative boost function used for the soft inference-type + distance ranking
    // (ADR-0011). Multiplicative (not additive bq) so the tier boost is independent of term idf:
    // ASSERTED is common (low idf) and SSSOM rare (high idf), so an additive bq would invert the
    // intended order.
    public final static String BOOST = "boost";

    // Result grouping for same-SPO collapse (ADR-0013). group=true collapses documents sharing
    // spo_key; group.ngroups makes the total a group count (a page is N groups); group.sort picks
    // the representative (highest inference tier, via the score boost); group.limit caps the number
    // of member documents returned per group.
    public final static String GROUP = "group";
    public final static String GROUP_FIELD = "group.field";
    public final static String GROUP_NGROUPS = "group.ngroups";
    public final static String GROUP_LIMIT = "group.limit";
    public final static String GROUP_SORT = "group.sort";
    public final static int GROUP_MEMBER_LIMIT = 20;
}
