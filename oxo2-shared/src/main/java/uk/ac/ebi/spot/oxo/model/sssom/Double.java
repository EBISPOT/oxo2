package uk.ac.ebi.spot.oxo.model.sssom;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;

/**
 * Represents an xsd:double.
 */

@JsonSerialize(using = Double.Serializer.class)
public class Double {
    private final String doubleAsString;

    private final Optional<java.lang.Double> doubleRepresentation;

    private static final Logger logger = LoggerFactory.getLogger(Double.class);

    public Double(String doubleAsString) {
        this.doubleAsString = doubleAsString;
        Optional<java.lang.Double> tempDouble;
        try {
            tempDouble = Optional.of(java.lang.Double.parseDouble(doubleAsString));
        } catch (Exception e) {
            tempDouble = Optional.empty();
        }
        this.doubleRepresentation = tempDouble;
    }

//    public String getDouble() {
//        return doubleAsString;
//    }
//
//    public Optional<java.lang.Double> getDoubleRepresentation() {
//        return doubleRepresentation;
//    }

    @Override
    public String toString() {
        return "Double{" +
                "doubleAsString='" + doubleAsString + '\'' +
                ", doubleRepresentation=" + doubleRepresentation +
                '}';
    }

    public static class Serializer extends JsonSerializer<Double> {
        public Serializer() {
            super();
        }

        @Override
        public void serialize(Double value, JsonGenerator jsonGenerator, SerializerProvider serializers)
                throws IOException {
            logger.debug("Serializing double: {}", value);
            if (value.doubleRepresentation.isPresent()) {
                jsonGenerator.writeNumber(value.doubleRepresentation.get());
            } else if (value.doubleAsString != null && !value.doubleAsString.isEmpty()) {
                jsonGenerator.writeString(value.doubleAsString);
            } else {
                jsonGenerator.writeNull();
            }
        }
    }

    public static class ConditionalInclusionFilter {
        @Override
        public boolean equals(Object object) {
            logger.debug("Checking if double should be included: {}", object);
            boolean result = false;
            logger.trace("1. Result set to false");
            if (object == null) {
                result = true; // Ignore field if null
                logger.trace("2. Result set to true");
            } else if (object instanceof Optional) {
                Optional optional = (Optional) object;
                if (optional.isPresent()) {
                    Object value = optional.get();
                    if (value instanceof Double) {
                        Double doubleValue = (Double) value;
                        result = doubleValue.doubleRepresentation.isEmpty() && doubleValue.doubleAsString.isEmpty();
                        logger.trace("3. Result set to {}", result);
                    }
                }
            }
            else if (object instanceof Double) {
                Double doubleValue = (Double) object;
                result = doubleValue.doubleRepresentation.isEmpty() && doubleValue.doubleAsString.isEmpty();
                logger.trace("4. Result set to {}", result);
            } else
                result = false;
            logger.debug("Checking if double should be included for object {} result = {}", object, result);
            return result;
        }
    }
}