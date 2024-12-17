package uk.ac.ebi.spot.oxo.model.sssom;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;

/**
 * @see <a href="https://mapping-commons.github.io/sssom/EntityTypeEnum/">EntityTypeEnum</a>
 */
public enum EntityTypeEnum {
    OWL_CLASS("owl class", "owl:Class", ""),
    OWL_OBJECT_PROPERTY("owl object property", "owl:ObjectProperty", ""),
    OWL_DATA_PROPERTY("owl data property", "owl:DataProperty", ""),
    OWL_ANNOTATION_PROPERTY("owl annotation property", "owl:AnnotationProperty", ""),
    OWL_NAMED_INDIVIDUAL("owl named individual", "owl:NamedIndividual", ""),
    SKOS_CONCEPT("skos concept", "skos:Concept", ""),
    RDFS_RESOURCE("rdfs resource", "rdfs:Resource", ""),
    RDFS_CLASS("rdfs class", "rdfs:Class", ""),
    RDFS_LITERAL("rdfs literal", "rdfs:Literal", "This refers to a value and an entity with semantic value.");
    private final String value;
    private final String meaning;
    private final String description;

    private static final Map<String, EntityTypeEnum> stringToEnum =
            Stream.of(values()).collect(toMap(EntityTypeEnum::value, e -> e));

    EntityTypeEnum(String value, String meaning, String description) {
        this.value = value;
        this.meaning = meaning;
        this.description = description;
    }

    @JsonValue
    public String value() {
        return value;
    }

    public String getMeaning() {
        return meaning;
    }

    public String getDescription() {
        return description;
    }


    public static Optional<EntityTypeEnum> fromString(String value) {
        return Optional.ofNullable(stringToEnum.get(value.toLowerCase()));
    }


    @Override
    public String toString() {
        return value;
    }
}
