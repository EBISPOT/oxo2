package uk.ac.ebi.spot.oxo.backend.controller.api.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One value offered as a completion of a field filter, with how many mappings carry it (ADR-0034).
 *
 * <p>For a contextual suggestion (a result-table column filter) the count is scoped to the live
 * search, so it is always &gt; 0 — which is what makes it impossible to suggest a value that yields
 * nothing.
 *
 * <p>It counts <b>mappings</b>, not table rows. When the caller collapses same-SPO mappings into one
 * representative row (ADR-0023, which the normal results table does), the row count it then displays
 * can be slightly lower than this — several mappings of the same triple collapse together. The count
 * is deliberately left as the mapping count rather than re-derived per-caller: it is the honest answer
 * to "how much of the corpus carries this value", and the guarantee that matters — that applying the
 * filter leaves something — holds either way.
 */
@Schema(description = "A field value suggested as a filter, with its mapping count.")
public record ValueSuggestion(
        @Schema(description = "The value, in the casing it is stored under.",
                example = "semapv:ManualMappingCuration")
        @JsonProperty("value") String value,

        @Schema(description = "Mappings carrying this value — scoped to the live search for a "
                + "contextual suggestion, corpus-wide for a vocabulary lookup. Always > 0 for a "
                + "contextual suggestion. Counts mappings, not collapsed table rows, so a caller that "
                + "collapses same-SPO mappings may display slightly fewer rows than this.")
        @JsonProperty("count") long count
) {}
