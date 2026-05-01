package uk.ac.ebi.spot.oxo.inferences.nemo.model;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class OXOInferenceConstants {

    public static final String OXO_MAPPING_TOOL = "OxO Inferences";
    public static final String OXO_MAPPING_JUSTIFICATION = "SEMAPV:MappingChaining";
    public static final String OXO_INFERENCES_BASE = "https://www.ebi.ac.uk/spot/oxo/inferences/";

    public static String inferredMappingSetIdFor(String sourceMappingSetId) {
        if (sourceMappingSetId == null || sourceMappingSetId.isBlank()) {
            throw new IllegalArgumentException("sourceMappingSetId must be supplied");
        }
        return OXO_INFERENCES_BASE + URLEncoder.encode(sourceMappingSetId, StandardCharsets.UTF_8);
    }
}
