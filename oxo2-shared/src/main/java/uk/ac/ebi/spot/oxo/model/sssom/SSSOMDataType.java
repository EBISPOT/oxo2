package uk.ac.ebi.spot.oxo.model.sssom;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.Optional;

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
                '}';
    }


    public enum SSSOMDataTypesEnum {
        DATE,
        DOUBLE,
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
                    case DATE ->
                            jsonGenerator.writeObject(value.dataRepresentation.get());
                    case DOUBLE ->
                        jsonGenerator.writeNumber((java.lang.Double) value.dataRepresentation.get());
                    case URI ->
                        jsonGenerator.writeString(value.dataRepresentation.get().toString());
                }
            } else if (value.dataAsString != null && !value.dataAsString.isEmpty()) {
                jsonGenerator.writeString(value.dataAsString);
            } else {
                jsonGenerator.writeNull();
            }
        }
    }

    public static class ConditionalInclusionFilter<S extends SSSOMDataType> {
        @Override
        public boolean equals(Object object) {
            logger.debug("Checking if S should be included: {}", object);
            boolean result = false;
            logger.trace("Initialize result to false");
            if (object == null) {
                result = true;
                logger.trace("Object is null, set result to true");
            } else if (object instanceof Optional) {
                Optional optional = (Optional) object;
                logger.trace("Object is Optional");
                if (optional.isPresent()) {
                    logger.trace("Optional.isPresent() is true");
                    SSSOMDataType sssomDataType = (SSSOMDataType)optional.get();
                    result = sssomDataType.dataRepresentation.isEmpty() && sssomDataType.dataAsString.isEmpty();
                    logger.trace("""
                            SSSOMDataType.dataRepresentation.isEmpty()={}, SSSOMDataType.dataAsString.isEmpty()={}
                            and result ={}""",
                            sssomDataType.dataRepresentation.isEmpty(),
                            sssomDataType.dataAsString.isEmpty(), result);
                }
            }
            else if (object instanceof SSSOMDataType) {
                logger.trace("object is of type SSSOMDataType");
                SSSOMDataType sssomDataType = (SSSOMDataType) object;
                result = sssomDataType.dataRepresentation.isEmpty() && sssomDataType.dataAsString.isEmpty();
                logger.trace("""
                        SSSOMDataType.dataRepresentation.isEmpty()={}, SSSOMDataType.dataAsString.isEmpty()={}
                        and result ={}""",
                        sssomDataType.dataRepresentation.isEmpty(),
                        sssomDataType.dataAsString.isEmpty(), result);
            } else if (object instanceof Collection<?>){
                Collection collection = (Collection) object;
                logger.trace("SSSOMDataType is part of a collection.size={}", collection.size());
                if (collection.isEmpty()) {
                    result = true;
                    logger.trace("Collection is empty. Setting result = {}", result);
                } else {
                    Optional firstElementOptional = collection.stream().findFirst();
                    if (firstElementOptional.isEmpty()) {
                        result = true;
                        logger.error("First element of collection is empty. Setting result = {}", result);
                    } else {
                        Object firstElement = firstElementOptional.get();
                        if (firstElement instanceof SSSOMDataType<?>) {
                            SSSOMDataType value = (SSSOMDataType) firstElement;
                            result = value.getDataRepresentation().isPresent() && !value.getDataAsString().isBlank();
                            logger.debug("""
                                    value.getDataRepresentation().isPresent() = {}, value.getDataAsString().isBlank() = {} 
                                    and result={}
                                    """,
                                    value.getDataRepresentation().isPresent(),
                                    value.getDataAsString().isBlank(),
                                    result);
                        }
                    }
                }
            }
            else
                result = false;
            logger.debug("Checking if S should be included for object {} result = {}", object, result);
            return result;
        }
    }
}
