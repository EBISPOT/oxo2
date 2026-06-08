package uk.ac.ebi.spot.oxo.model.sssom;

/**
 * Field-name constants for the {@code oxo2-mappingsets} Solr collection.
 * Mirrors {@link MappingConstants}; references its strings where the same field
 * appears in both schemas so the two cannot drift.
 */
public final class MappingSetConstants {
    public static final String MAPPING_SET_ID          = MappingConstants.MAPPING_SET_ID;
    public static final String MAPPING_SET_TITLE       = MappingConstants.MAPPING_SET_TITLE;
    public static final String MAPPING_SET_DESCRIPTION = MappingConstants.MAPPING_SET_DESCRIPTION;
    public static final String MAPPING_SET_VERSION     = MappingConstants.MAPPING_SET_VERSION;
    public static final String MAPPING_SET_SOURCE      = MappingConstants.MAPPING_SET_SOURCE;
    public static final String CREATOR_LABEL           = MappingConstants.CREATOR_LABEL;
    public static final String CREATOR_ID              = MappingConstants.CREATOR_ID;
    public static final String MAPPING_PROVIDER        = MappingConstants.MAPPING_PROVIDER;
    public static final String INFERENCE_TYPE          = MappingConstants.INFERENCE_TYPE;

    private MappingSetConstants() {}
}
