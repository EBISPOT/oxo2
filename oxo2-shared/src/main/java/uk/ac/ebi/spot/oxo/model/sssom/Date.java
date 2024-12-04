package uk.ac.ebi.spot.oxo.model.sssom;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
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
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public class Date {

    @JsonValue
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

    public String getDate() {
        return dateAsString;
    }

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
}