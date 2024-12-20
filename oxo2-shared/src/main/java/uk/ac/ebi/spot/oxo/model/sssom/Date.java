package uk.ac.ebi.spot.oxo.model.sssom;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.Optional;

/**
 * In SSSOM date represents an xsd:Date @see <a href="https://mapping-commons.github.io/sssom/Date/>Date</a>.
 */

@JsonSerialize(using = SSSOMDataType.Serializer.class)
public class Date extends SSSOMDataType<LocalDate> {
    private static final Logger logger = LoggerFactory.getLogger(Date.class);

    public Date(String date) {
        super(date);
    }

    @Override
    protected Optional<LocalDate> parseData(String date) {
        Optional<LocalDate> tempDate;
        try {
            tempDate = Optional.of(LocalDate.parse(date));
        } catch (Exception e) {
            tempDate = Optional.empty();
        }
        return tempDate;
    }

    @Override
    protected SSSOMDataTypesEnum getType() {
        return SSSOMDataTypesEnum.DATE;
    }

}