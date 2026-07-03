package uk.ac.ebi.spot.oxo.backend.controller.api.v1.dto;

import uk.ac.ebi.spot.oxo.model.sssom.EntityReference;
import uk.ac.ebi.spot.oxo.model.sssom.Uri;

import java.util.Optional;

/**
 * OxO v1 mapping {@code Scope}, derived from the SSSOM predicate for the {@code /api/mappings}
 * compatibility endpoint (ADR-0025). v1's enum has no bucket for SSSOM's full predicate range, so the
 * mapping is deliberately lossy:
 * <ul>
 *   <li>{@code EXACT} is reserved for genuine identity ({@code skos:exactMatch},
 *       {@code owl:equivalentClass});</li>
 *   <li>{@code skos:closeMatch} — a weaker, deliberately non-transitive near-equivalence — is folded
 *       into {@code RELATED}, not {@code EXACT}, so a v1 consumer never mistakes it for identity;</li>
 *   <li>unrecognised predicates default to {@code RELATED}.</li>
 * </ul>
 * v1's {@code PREDICTED} (algorithmic mappings) has no OxO2 equivalent and is never emitted. Matching
 * is on the predicate's local name (case-insensitive), so it is independent of the stored CURIE prefix
 * casing.
 */
public enum V1Scope {
    EXACT, NARROWER, BROADER, RELATED;

    public static V1Scope of(Optional<Uri> predicateIri, Optional<EntityReference> predicateId) {
        String localName = predicateIri.map(Uri::getDataAsString).map(V1Scope::localName)
                .or(() -> predicateId.map(EntityReference::getDataAsString).map(V1Scope::localName))
                .orElse("");
        return switch (localName.toLowerCase()) {
            case "exactmatch", "equivalentclass" -> EXACT;
            case "narrowmatch", "subclassof" -> NARROWER;
            case "broadmatch" -> BROADER;
            // relatedMatch, closeMatch, hasDbXref, and anything unrecognised.
            default -> RELATED;
        };
    }

    /** Local name of an IRI or CURIE: the segment after the last {@code #}, {@code /} or {@code :}. */
    private static String localName(String value) {
        if (value == null) {
            return "";
        }
        int cut = Math.max(Math.max(value.lastIndexOf('#'), value.lastIndexOf('/')), value.lastIndexOf(':'));
        return cut >= 0 ? value.substring(cut + 1) : value;
    }
}
