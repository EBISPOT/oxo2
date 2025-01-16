package uk.ac.ebi.spot.oxo.model.sssom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

/**
 * In SSSOM date represents an xsd:Date @see <a href="https://mapping-commons.github.io/sssom/Date/>Date</a>.
 */

public class Date extends SSSOMDataType<LocalDate> {
    private static final Logger logger = LoggerFactory.getLogger(Date.class);

    public Date(String date) {
        super(date);
    }

    static public Date of(java.util.Date date) {
        return new Date(date.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .toString());
    }

    @Override
    protected Optional<LocalDate> parseData(String date) {
        Optional<LocalDate> tempDate;
        if (date == null || date.isBlank()) {
            return Optional.empty();
        }
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