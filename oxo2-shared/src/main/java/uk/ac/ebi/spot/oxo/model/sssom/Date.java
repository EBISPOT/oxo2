package uk.ac.ebi.spot.oxo.model.sssom;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Optional;

/**
 * In SSSOM date represents an xsd:Date @see <a href="https://mapping-commons.github.io/sssom/Date/>Date</a>.
 */

@JsonSerialize(using = Date.Serializer.class)
public class Date {

    private final String dateAsString;

    private final Optional<LocalDate> dateRepresentation;

    private static final Logger logger = LoggerFactory.getLogger(Date.class);
    public Date(String date) {
        this.dateAsString = date;
        Optional<LocalDate> tempDate;
        try {
            tempDate = Optional.of(LocalDate.parse(date));
            logger.debug("Parsed date: {}", dateAsString);
        } catch (Exception e) {
            logger.error("Error parsing date: {}", date, e);
            tempDate = Optional.empty();
        }
        this.dateRepresentation = tempDate;
    }

//    public String getDateAsString() {
//        return dateAsString;
//    }
//
//
    public Optional<LocalDate> getDateRepresentation() {
        return dateRepresentation;
    }


    @Override
    public String toString() {
        return "Date{" +
                "dateAsString='" + dateAsString + '\'' +
                ", dateRepresentation=" + dateRepresentation +
                '}';
    }

    public static class Serializer extends JsonSerializer<Date> {
        public Serializer() {
            super();
        }

        @Override
        public void serialize(Date value, JsonGenerator jsonGenerator, SerializerProvider serializers)
                throws IOException {
            if (value.dateRepresentation.isPresent()) {
                jsonGenerator.writeString(value.dateRepresentation.get().toString());
            } else if (value.dateAsString != null && !value.dateAsString.isEmpty()) {
                jsonGenerator.writeString(value.dateAsString);
            } else {
                jsonGenerator.writeNull();
            }
        }
    }

    public static class ConditionalInclusionFilter {
        @Override
        public boolean equals(Object object) {
            logger.debug("Checking if date should be included: {}", object);
            boolean result = false;
            logger.trace("1. Result set to false");
            if (object == null) {
                result = true; // Ignore field if null
                logger.trace("2. Result set to true");
            } else if (object instanceof Optional) {
                Optional optional = (Optional) object;
                if (optional.isPresent()) {
                    Object value = optional.get();
                    if (value instanceof Date) {
                        Date dateValue = (Date) value;
                        result = dateValue.dateRepresentation.isEmpty() && dateValue.dateAsString.isEmpty();
                        logger.trace("3. Result set to {}", result);
                    }
                }
            } else if (object instanceof Date) {
                Date dateValue = (Date) object;
                result = dateValue.dateRepresentation.isEmpty() && dateValue.dateAsString.isEmpty();
                logger.trace("4. Result set to {}", result);
            } else {
                result = false;
            }
            logger.debug("Checking if date should be included for object {} result = {}", object, result);
            return result;
        }
    }
}