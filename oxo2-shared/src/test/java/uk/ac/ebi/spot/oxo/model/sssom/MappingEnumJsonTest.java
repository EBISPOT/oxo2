package uk.ac.ebi.spot.oxo.model.sssom;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

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
}
