package uk.ac.ebi.spot.oxo.model.sssom;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Where a mapping set comes from, as an OxO curation category — <em>not</em> an SSSOM concept.
 *
 * <p>The distinction is operator knowledge carried on each OxO config {@code mapping_registries} entry
 * (the {@code category} key): the OLS bulk export is {@link #ONTOLOGY} (an ontology's own asserted
 * cross-references), whereas EVORA, the Mapping Commons registry, and the curated GitHub repositories
 * are {@link #CURATED} standalone SSSOM files. It is stamped onto every mapping of a set during the
 * dataload and stored as the code string in the Solr {@code mapping_set_category} field.
 *
 * <p>Orthogonal to {@link InferenceType}: a mapping is asserted or inferred regardless of whether its
 * set is an ontology or a curated file. The provenance-led ranking (ADR-0027) uses both — ontology
 * before curated before inferred.
 */
public enum MappingSetCategory {

    /** An ontology's own asserted mappings (the OLS bulk export). */
    ONTOLOGY,

    /** A curated standalone SSSOM file (EVORA, Mapping Commons, curated GitHub repositories). */
    CURATED;

    /** Default when a config entry carries no {@code category} key: treat an untagged set as curated. */
    public static final MappingSetCategory DEFAULT = CURATED;

    @JsonValue
    public String getCode() {
        return name();
    }

    @JsonCreator
    public static MappingSetCategory fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (MappingSetCategory category : values()) {
            if (category.name().equalsIgnoreCase(code)) {
                return category;
            }
        }
        throw new IllegalArgumentException("Unknown MappingSetCategory: " + code);
    }
}
