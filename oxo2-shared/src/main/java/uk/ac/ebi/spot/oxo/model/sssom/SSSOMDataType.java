package uk.ac.ebi.spot.oxo.model.sssom;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Represents an SSSOM data type such as Date, Double, EntityReference, Uri.
 * It stores both the String and a parsed representation of the data. The parsed representation is stored as an Optional
 * since it may not be possible to parse the data into its proper type because of data quality issues.
 *
 * @param <T>
 */
@JsonSerialize(using = SSSOMDataType.Serializer.class)
abstract public class SSSOMDataType<T> {
    protected final String dataAsString;
    protected final Optional<T> dataRepresentation;

    protected final SSSOMDataTypesEnum type;

    private static final Logger logger = LoggerFactory.getLogger(SSSOMDataType.class);

    public SSSOMDataType(String data) {
        this.dataAsString = data;
        this.dataRepresentation = parseData(data);
        this.type = getType();
    }

    public Optional<T> getDataRepresentation() {
        return dataRepresentation;
    }

    public String getDataAsString() {
        return dataAsString;
    }

    abstract protected Optional<T> parseData(String data);
    abstract protected SSSOMDataTypesEnum getType();

    @Override
    public String toString() {
        return "SSSOMDataType{" +
                "dataAsString='" + dataAsString + '\'' +
                ", dataRepresentation=" + dataRepresentation +
                ", type=" + type +
                '}';
    }

    public enum SSSOMDataTypesEnum {
        CURIE_MAP,
        DATE,
        DOUBLE,
        ENTITY_REFERENCE,
        KEY_VALUE_PAIRS,
        URI;

        SSSOMDataTypesEnum() {
        }

    }

    public static class Serializer<T extends SSSOMDataType> extends JsonSerializer<T> {
        @Override
        public void serialize(T value, JsonGenerator jsonGenerator, SerializerProvider serializers)
                throws IOException {
            logger.debug("Serializing S: {}", value);
            if (value.dataRepresentation.isPresent()) {
                switch(value.type) {
                    case KEY_VALUE_PAIRS -> {
                        Map<String, String> map = (Map<String, String>) value.dataRepresentation.get();
                        String jsonString = new ObjectMapper().writeValueAsString(map);
                        jsonGenerator.writeString(jsonString);
                    }
                    case CURIE_MAP, DATE ->
                        jsonGenerator.writeObject(value.dataAsString);
                    case DOUBLE ->
                        jsonGenerator.writeNumber((java.lang.Double) value.dataRepresentation.get());
                    case ENTITY_REFERENCE, URI ->
                        jsonGenerator.writeString(value.dataRepresentation.get().toString());
                }
            } else if (value.dataAsString != null && !value.dataAsString.isEmpty()) {
                jsonGenerator.writeString(value.dataAsString);
            } else {
                jsonGenerator.writeNull();
            }
        }
    }

    public static class FilterOut<S extends SSSOMDataType> {
        /**
         * This method returns true if the object should be filtered out and false if the object should be included.
         * @see com.fasterxml.jackson.annotation.JsonInclude#valueFilter()
         *
         * @param object
         * @return
         */
        @Override
        public boolean equals(Object object) {
            logger.debug("Checking if S should be filtered out: {}", object);
            boolean filterOut = false;
            logger.trace("Set filterOut to false");
            if (object == null) {
                filterOut = true;
                logger.trace("Object is null, set filterOut to true");
            } else if (object instanceof Optional) {
                Optional optional = (Optional) object;
                logger.trace("Object is Optional");
                if (optional.isPresent()) {
                    logger.trace("Optional.isPresent() is true");
                    SSSOMDataType sssomDataType = (SSSOMDataType)optional.get();
                    filterOut = sssomDataType.dataRepresentation.isEmpty() && sssomDataType.dataAsString.isEmpty();
                    logger.trace("""
                            SSSOMDataType.dataRepresentation.isEmpty()={}, SSSOMDataType.dataAsString.isEmpty()={}
                            and filterOut ={}""",
                            sssomDataType.dataRepresentation.isEmpty(),
                            sssomDataType.dataAsString.isEmpty(), filterOut);
                } else {
                    filterOut = true;
                    logger.trace("Optional.isPresent() is false, set filterOut to true");
                }
            }
            else if (object instanceof SSSOMDataType) {
                logger.trace("object is of type SSSOMDataType");
                SSSOMDataType sssomDataType = (SSSOMDataType) object;
                filterOut = sssomDataType.dataRepresentation.isEmpty() && sssomDataType.dataAsString.isEmpty();
                logger.trace("""
                        SSSOMDataType.dataRepresentation.isEmpty()={}, SSSOMDataType.dataAsString.isEmpty()={}
                        and result ={}""",
                        sssomDataType.dataRepresentation.isEmpty(),
                        sssomDataType.dataAsString.isEmpty(), filterOut);
            } else if (object instanceof Collection<?>){
                Collection collection = (Collection) object;
                logger.trace("SSSOMDataType is part of a collection.size={}", collection.size());
                if (collection.isEmpty()) {
                    filterOut = true;
                    logger.trace("Collection is empty. Setting filterOut = {}", filterOut);
                } else {
                    Optional firstElementOptional = collection.stream().findFirst();
                    if (firstElementOptional.isEmpty()) {
                        filterOut = true;
                        logger.error("First element of collection is empty. Setting filterOut = {}", filterOut);
                    } else {
                        Object firstElement = firstElementOptional.get();
                        if (firstElement instanceof SSSOMDataType<?>) {
                            SSSOMDataType value = (SSSOMDataType) firstElement;
                            filterOut = value.getDataRepresentation().isEmpty() || value.getDataAsString().isBlank();
                            logger.debug("""
                                    value.getDataRepresentation().isEmpty() = {}, value.getDataAsString().isBlank() = {} 
                                    and result={}
                                    """,
                                    value.getDataRepresentation().isEmpty(),
                                    value.getDataAsString().isBlank(),
                                    filterOut);
                        }
                    }
                }
            }
            else
                filterOut = false;
            logger.debug("Checking if S should be filtered out for object {} result = {}", object, filterOut);
            return filterOut;
        }
    }
}
