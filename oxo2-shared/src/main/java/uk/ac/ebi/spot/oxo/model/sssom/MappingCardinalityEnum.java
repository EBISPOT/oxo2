package uk.ac.ebi.spot.oxo.model.sssom;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;

/**
 * @see <a haref="https://mapping-commons.github.io/sssom/MappingCardinalityEnum/">MappingCardinalityEnum</a>
 */

public enum MappingCardinalityEnum {
    ONE_TO_ONE("1:1", "One-to-one mapping"),
    ONE_TO_MANY("1:n", "One-to-many mapping"),
    MANY_TO_ONE("n:1", "Many-to-one mapping"),
    ONE_TO_NONE("1:0", "One-to-none mapping"),
    NONE_TO_ONE("0:1", "None-to-one mapping"),
    MANY_TO_MANY("n:n", "Many-to-many mapping");

    private final String value;
    private final String description;


    private static final Map<String, MappingCardinalityEnum> stringToEnum =
            Stream.of(values()).collect(toMap(MappingCardinalityEnum::value, e -> e));

    MappingCardinalityEnum(String value, String description) {
        this.value = value;
        this.description = description;
    }

    @JsonValue
    public String value() {
        return value;
    }

    public static Optional<MappingCardinalityEnum> fromString(String value) {
        return Optional.ofNullable(stringToEnum.get(value.toLowerCase()));
    }
}
