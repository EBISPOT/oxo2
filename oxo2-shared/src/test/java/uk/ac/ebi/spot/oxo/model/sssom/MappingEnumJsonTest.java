package uk.ac.ebi.spot.oxo.model.sssom;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MappingEnumJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

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

    /**
     * Regression: Jackson 2.21 treats any public no-arg String getter on an enum as an
     * "as-value" candidate. Adding a second such getter alongside the @JsonValue-annotated
     * one trips InvalidDefinitionException("Multiple 'as-value' properties defined") the
     * first time Jackson builds a serializer for MappingEnum. Forcing serializer
     * resolution here makes the check deterministic.
     */
    @Test
    void buildingSerializerDoesNotReportMultipleAsValueCandidates() {
        ObjectMapper freshMapper = new ObjectMapper();
        assertDoesNotThrow(() -> freshMapper.getSerializerProviderInstance()
                .findValueSerializer(MappingEnum.class));
    }
}
