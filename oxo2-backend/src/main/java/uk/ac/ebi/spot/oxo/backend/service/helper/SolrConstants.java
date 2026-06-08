package uk.ac.ebi.spot.oxo.backend.service.helper;

public class SolrConstants {
    public final static String DEF_TYPE = "defType";
    public final static String EDISMAX = "edismax";
    // edismax multiplicative boost function used for the soft inference-type + distance ranking
    // (ADR-0011). Multiplicative (not additive bq) so the tier boost is independent of term idf:
    // ASSERTED is common (low idf) and SSSOM rare (high idf), so an additive bq would invert the
    // intended order.
    public final static String BOOST = "boost";
}
