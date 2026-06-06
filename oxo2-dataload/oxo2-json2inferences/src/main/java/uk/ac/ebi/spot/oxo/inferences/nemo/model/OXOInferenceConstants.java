package uk.ac.ebi.spot.oxo.inferences.nemo.model;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class OXOInferenceConstants {

    public static final String OXO_MAPPING_TOOL = "OxO Inferences";
    public static final String OXO_MAPPING_JUSTIFICATION = "SEMAPV:MappingChaining";

    /**
     * Canonical, EBI-self-hosted base IRI for OxO2 inferred mapping sets (ADR-0012).
     * Mirrors the {@code https://www.ebi.ac.uk/<service>/} convention used by sibling
     * services such as OLS ({@code https://www.ebi.ac.uk/ols4/}).
     */
    public static final String OXO2_INFERENCES_BASE = "https://www.ebi.ac.uk/oxo2/inferences";

    /**
     * Phase 2 (SSSOM, cross-set) inferences all land in a single inferred mapping set
     * resolvable at the base IRI itself (ADR-0009, ADR-0012).
     */
    public static final String PHASE2_INFERENCES_SET_ID = OXO2_INFERENCES_BASE;

    /**
     * The nil UUID carried as the {@code mapping_id} graph term by every inferred
     * conclusion in {@code owl.rls}/{@code sssom.rls} (ADR-0010). A {@code mapping(...)}
     * atom whose id term equals this is an inferred intermediate; any other id is the
     * real {@code mapping_id} of an asserted premise and is the provenance key.
     */
    public static final String NIL_MAPPING_ID = "00000000-0000-0000-0000-000000000000";
    public static final String URN_UUID_PREFIX = "urn:uuid:";
    public static final String NIL_MAPPING_ID_IRI = URN_UUID_PREFIX + NIL_MAPPING_ID;

    /**
     * Phase 1 (OWL, per-set) inferences land in a per-source-set inferred mapping set
     * nested under the base IRI (ADR-0009, ADR-0012), e.g.
     * {@code https://www.ebi.ac.uk/oxo2/inferences/<URL-encoded source set id>}.
     */
    public static String inferredMappingSetIdFor(String sourceMappingSetId) {
        if (sourceMappingSetId == null || sourceMappingSetId.isBlank()) {
            throw new IllegalArgumentException("sourceMappingSetId must be supplied");
        }
        return OXO2_INFERENCES_BASE + "/" + URLEncoder.encode(sourceMappingSetId, StandardCharsets.UTF_8);
    }

    /**
     * True if the given {@code mapping_id} graph term (an {@code urn:uuid:...} IRI, with or
     * without surrounding angle brackets) denotes an inferred conclusion (the nil UUID).
     */
    public static boolean isInferredIdTerm(String mappingIdTerm) {
        if (mappingIdTerm == null) return false;
        String trimmed = mappingIdTerm;
        if (trimmed.startsWith("<") && trimmed.endsWith(">")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        return NIL_MAPPING_ID_IRI.equals(trimmed);
    }

    /**
     * Strip the {@code urn:uuid:} prefix (and any surrounding angle brackets) from a
     * {@code mapping_id} graph term, yielding the bare UUID string as stored in the Solr
     * {@code mapping_id} field.
     */
    public static String toBareMappingId(String mappingIdTerm) {
        if (mappingIdTerm == null) return null;
        String trimmed = mappingIdTerm;
        if (trimmed.startsWith("<") && trimmed.endsWith(">")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        if (trimmed.startsWith(URN_UUID_PREFIX)) {
            trimmed = trimmed.substring(URN_UUID_PREFIX.length());
        }
        return trimmed;
    }
}
