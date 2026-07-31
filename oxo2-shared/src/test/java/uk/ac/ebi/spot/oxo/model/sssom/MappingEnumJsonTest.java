package uk.ac.ebi.spot.oxo.model.sssom;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MappingEnumJsonTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    void serializesMappingEnumAsSssomFieldName() throws Exception {
        assertEquals("\"subject_id\"", objectMapper.writeValueAsString(MappingEnum.SUBJECT_ID));
    }

    @Test
    void deserializesMappingEnumFromSssomFieldName() throws Exception {
        MappingEnum mappingEnum = objectMapper.readValue("\"subject_id\"", MappingEnum.class);

        assertEquals(MappingEnum.SUBJECT_ID, mappingEnum);
    }

    @Test
    void deserializesMappingEnumFromCamelCasePropertyName() throws Exception {
        MappingEnum mappingEnum = objectMapper.readValue("\"subjectId\"", MappingEnum.class);

        assertEquals(MappingEnum.SUBJECT_ID, mappingEnum);
    }

    @Test
    void deserializesTheNewMappingSetCategoryField() throws Exception {
        assertEquals(MappingEnum.MAPPING_SET_CATEGORY,
                objectMapper.readValue("\"mapping_set_category\"", MappingEnum.class));
    }

    /**
     * The frontend "Sort by" control posts these two field names as {@code sortedFields[].id}, which
     * binds to a MappingEnum. If either stopped resolving, picking that option would fail the search.
     */
    @Test
    void deserializesTheFieldsTheSortByControlSends() throws Exception {
        assertEquals(MappingEnum.CONFIDENCE, objectMapper.readValue("\"confidence\"", MappingEnum.class));
        assertEquals(MappingEnum.MAPPING_DATE, objectMapper.readValue("\"mapping_date\"", MappingEnum.class));
    }

    /**
     * Regression: Jackson treats any public no-arg String getter on an enum as an "as-value"
     * candidate. Adding a second such getter alongside the @JsonValue-annotated one trips
     * "Multiple 'as-value' properties defined" the first time Jackson builds a serializer for
     * MappingEnum. The mapper is deliberately fresh so no serializer is cached and this call
     * is what forces the serializer to be constructed.
     */
    @Test
    void buildingSerializerDoesNotReportMultipleAsValueCandidates() {
        ObjectMapper freshMapper = JsonMapper.builder().build();
        assertDoesNotThrow(() -> freshMapper.writeValueAsString(MappingEnum.SUBJECT_ID));
    }
}
