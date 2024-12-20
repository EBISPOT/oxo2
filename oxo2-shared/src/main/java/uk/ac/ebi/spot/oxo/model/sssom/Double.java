package uk.ac.ebi.spot.oxo.model.sssom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Represents an xsd:double.
 */

public class Double extends SSSOMDataType<java.lang.Double> {
    private static final Logger logger = LoggerFactory.getLogger(Double.class);

    public Double(String doubleAsString) {
        super(doubleAsString);
    }

    @Override
    protected Optional<java.lang.Double> parseData(String data) {
        Optional<java.lang.Double> tempDouble;
        if (data == null || data.isBlank()) {
            return Optional.empty();
        }
        try {
            tempDouble = Optional.of(java.lang.Double.parseDouble(data));
        } catch (Exception e) {
            tempDouble = Optional.empty();
        }
        return tempDouble;
    }

    @Override
    protected SSSOMDataTypesEnum getType() {
        return SSSOMDataTypesEnum.DOUBLE;
    }


//    public String getDouble() {
//        return doubleAsString;
//    }
//
//    public Optional<java.lang.Double> getDoubleRepresentation() {
//        return doubleRepresentation;
//    }

}