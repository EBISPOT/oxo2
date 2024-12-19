package uk.ac.ebi.spot.oxo.model.sssom;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.Optional;

/**
 * EntityReference refers to an rdfs:Resource @see <a href="https://mapping-commons.github.io/sssom/EntityReference/>
 * EntityReference</a>.
 *
 *  According to <a href="https://mapping-commons.github.io/sssom/spec-model/#identifiers"/> can represent a full length
 *  IRI or a curie. This the difference between this class and Uri. The Uri class always aims to represent a URI, where
 *  this class could represent a curie.
 */
@JsonSerialize(using = EntityReference.Serializer.class)
public class EntityReference implements Comparable<EntityReference> {
//    @JsonValue
    private  final String uri;

    private static final Logger logger = LoggerFactory.getLogger(EntityReference.class);

    public EntityReference(String uri) {
        if (uri == null)
            throw new IllegalArgumentException("EntityReference uri is null, uri =" + uri);
        if (!uri.contains(":"))
            logger.warn("EntityReference uri is null or does not contain a colon, uri: {}", uri);
        this.uri = uri;
    }

    public String getUri() {
        return uri;
    }

    @Override
    public int compareTo(EntityReference o) {
        return this.uri.compareTo(o.uri);
    }

    @Override
    public String toString() {
        return "EntityReference{" +
                "uri='" + uri + '\'' +
                '}';
    }

    public static class Serializer extends JsonSerializer<EntityReference> {
        public Serializer() {
            super();
        }

        @Override
        public void serialize(EntityReference value, JsonGenerator jsonGenerator, SerializerProvider serializers)
                throws IOException {
            if (value.getUri() != null && !value.getUri().isEmpty()) {
                jsonGenerator.writeString(value.getUri());
            } else {
                jsonGenerator.writeNull();
            }
        }
    }

    public static class ConditionalInclusionFilter {
        @Override
        public boolean equals(Object object) {
            logger.debug("##### Checking if EntityReference should be included: {}", object);
            boolean result = false;
            if (object == null) {
                result = true;
                logger.trace("1. Result = {}", result);
            } else if (object instanceof Optional<?>) {
                logger.trace("2. Object instance of Optional");
                Optional<?> optional = (Optional<?>) object;
                result = optional.isEmpty();
                logger.trace("2.a Object instance of Optional");
                if (optional.isPresent()) {
                    logger.trace("2.b Object instance of optional.isPresent() = {}", optional.isPresent());
                    Object value = optional.get();
                    if (value instanceof EntityReference) {
                        EntityReference entityReference = (EntityReference) value;
                        result = entityReference.getUri() == null || entityReference.getUri().isEmpty();
                        logger.trace("3. Result = {}", result);
                    }
                }
            } else if (object instanceof Collection){
                logger.trace("EntityReference is part of a collection");
                Collection collection = (Collection) object;
                if (collection.isEmpty()) {
                    result = true;
                    logger.trace("4. Result set to {}", result);
                } else {
                    Optional firstElementOptional = collection.stream().findFirst();
                    if (firstElementOptional.isEmpty()) {
                        result = true;
                        logger.trace("5. Result set to {}", result);
                    } else {
                        Object firstElement = firstElementOptional.get();
                        if (firstElement instanceof EntityReference) {
                            EntityReference entityReference = (EntityReference) firstElement;
                            result = entityReference.uri.isBlank();
                            logger.debug("entityReference.uri.isBlank() = {}", entityReference.uri.isBlank());
                            logger.trace("6. Result set to {}", result);
                        }
                    }
                }
            }
            else if (object instanceof EntityReference) {
                EntityReference entityReference = (EntityReference) object;
                result = entityReference.getUri() == null || entityReference.getUri().isEmpty();
                logger.trace("7. Result = {}", result);
            }
            logger.debug("Checking if EntityReference should be included for object {} result = {}", object, result);
            return result;
        }
    }
}
