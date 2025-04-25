package uk.ac.ebi.spot.oxo.model.sssom;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enum for mapping fields
 * @Todo: Change Mapping class to use this enum
 */
public enum MappingEnum {
    AUTHOR_ID(MappingConstants.AUTHOR_ID),
    AUTHOR_LABEL(MappingConstants.AUTHOR_LABEL),
    COMMENT(MappingConstants.COMMENT),
    CONFIDENCE(MappingConstants.CONFIDENCE),
    CREATOR_ID(MappingConstants.CREATOR_ID),
    CREATOR_LABEL(MappingConstants.CREATOR_LABEL),
    CURATION_RULE(MappingConstants.CURATION_RULE),
    DISTANCE(MappingConstants.DISTANCE),
    ISSUE_TRACKER_ITEM(MappingConstants.ISSUE_TRACKER_ITEM),
    LICENSE(MappingConstants.LICENSE),
    MAPPING_CARDINALITY(MappingConstants.MAPPING_CARDINALITY),
    MAPPING_DATE(MappingConstants.MAPPING_DATE),
    MAPPING_ID(MappingConstants.MAPPING_ID),
    MAPPING_JUSTIFICATION(MappingConstants.MAPPING_JUSTIFICATION),
    MAPPING_PROVIDER(MappingConstants.MAPPING_PROVIDER),
    MAPPING_SET_DESCRIPTION(MappingConstants.MAPPING_SET_DESCRIPTION),
    MAPPING_SET_ID(MappingConstants.MAPPING_SET_ID),
    MAPPING_SET_SOURCE(MappingConstants.MAPPING_SET_SOURCE),
    MAPPING_SET_TITLE(MappingConstants.MAPPING_SET_TITLE),
    MAPPING_SET_VERSION(MappingConstants.MAPPING_SET_VERSION),
    MAPPING_SOURCE(MappingConstants.MAPPING_SOURCE),
    MAPPING_TOOL(MappingConstants.MAPPING_TOOL),
    MAPPING_TOOL_VERSION(MappingConstants.MAPPING_TOOL_VERSION),
    MATCH_STRING(MappingConstants.MATCH_STRING),
    OBJECT_CATEGORY(MappingConstants.OBJECT_CATEGORY),
    OBJECT_ID(MappingConstants.OBJECT_ID),
    OBJECT_ID_PREFIX(MappingConstants.OBJECT_ID_PREFIX),
    OBJECT_LABEL(MappingConstants.OBJECT_LABEL),
    OBJECT_MATCH_FIELD(MappingConstants.OBJECT_MATCH_FIELD),
    OBJECT_PREPROCESSING(MappingConstants.OBJECT_PREPROCESSING),
    OBJECT_SOURCE(MappingConstants.OBJECT_SOURCE),
    OBJECT_SOURCE_VERSION(MappingConstants.OBJECT_SOURCE_VERSION),
    OBJECT_TYPE(MappingConstants.OBJECT_TYPE),
    OTHER(MappingConstants.OTHER),
    PREDICATE_ID(MappingConstants.PREDICATE_ID),
    PREDICATE_LABEL(MappingConstants.PREDICATE_LABEL),
    PREDICATE_MODIFIER(MappingConstants.PREDICATE_MODIFIER),
    PUBLICATION_DATE(MappingConstants.PUBLICATION_DATE),
    REVIEWER_ID(MappingConstants.REVIEWER_ID),
    REVIEWER_LABEL(MappingConstants.REVIEWER_LABEL),
    SEE_ALSO(MappingConstants.SEE_ALSO),
    SIMILARITY_MEASURE(MappingConstants.SIMILARITY_MEASURE),
    SIMILARITY_SCORE(MappingConstants.SIMILARITY_SCORE),
    SUBJECT_CATEGORY(MappingConstants.SUBJECT_CATEGORY),
    SUBJECT_ID(MappingConstants.SUBJECT_ID),
    SUBJECT_ID_PREFIX(MappingConstants.SUBJECT_ID_PREFIX),
    SUBJECT_LABEL(MappingConstants.SUBJECT_LABEL),
    SUBJECT_MATCH_FIELD(MappingConstants.SUBJECT_MATCH_FIELD),
    SUBJECT_PREPROCESSING(MappingConstants.SUBJECT_PREPROCESSING),
    SUBJECT_SOURCE(MappingConstants.SUBJECT_SOURCE),
    SUBJECT_SOURCE_VERSION(MappingConstants.SUBJECT_SOURCE_VERSION),
    SUBJECT_TYPE(MappingConstants.SUBJECT_TYPE);

    private final String field;

    public static final String[] MINIMAL_LIST_OF_FIELDS = new String[]{
            MappingEnum.MAPPING_ID.getField(),
            MappingEnum.MAPPING_SET_ID.getField(),
            MappingEnum.SUBJECT_ID.getField(),
            MappingEnum.SUBJECT_LABEL.getField(),
            MappingEnum.SUBJECT_ID_PREFIX.getField(),
            MappingEnum.PREDICATE_ID.getField(),
            MappingEnum.PREDICATE_LABEL.getField(),
            MappingEnum.PREDICATE_MODIFIER.getField(),
            MappingEnum.OBJECT_ID.getField(),
            MappingEnum.OBJECT_LABEL.getField(),
            MappingEnum.OBJECT_ID_PREFIX.getField(),
            MappingEnum.MAPPING_JUSTIFICATION.getField(),
            MappingEnum.DISTANCE.getField()
    };

    MappingEnum(String field) {
        this.field = field;
    }

    @JsonValue
    public String getField() {
        return field;
    }

    @JsonCreator
    public static MappingEnum fromString(String text) {
        for (MappingEnum mappingEnum : MappingEnum.values()) {
            if (mappingEnum.field.equalsIgnoreCase(text)) {
                return mappingEnum;
            }
        }
        return null;
    }
}
