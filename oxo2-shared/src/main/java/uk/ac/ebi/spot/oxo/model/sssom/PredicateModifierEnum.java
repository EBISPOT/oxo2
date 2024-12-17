package uk.ac.ebi.spot.oxo.model.sssom;


import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;

/**
 * @see <a href="https://mapping-commons.github.io/sssom/PredicateModifierEnum/">PredicateModifierEnum</a>.
 */
public enum PredicateModifierEnum {
    NOT ("not");

    private final String value;

    private static final Map<String, PredicateModifierEnum> stringToEnum =
            Stream.of(values()).collect(toMap(PredicateModifierEnum::value, e -> e));

    PredicateModifierEnum(String value) {
        this.value = value;
    }

    public static Optional<PredicateModifierEnum> fromString(String value) {
        return Optional.ofNullable(stringToEnum.get(value.toLowerCase()));
    }

    @JsonValue
    public String value() {
        return value;
    }
}
