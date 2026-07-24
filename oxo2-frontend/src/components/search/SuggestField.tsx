import type { AdvancedFieldDef } from "../../model/AdvancedFields";
import type { EntitySuggestion } from "../../pages/results/SuggestSlice";
import { formatMappingJustification } from "../../model/MappingJustification";
import { EntitySuggest } from "./EntitySuggest";
import { VocabSuggest } from "./VocabSuggest";

/**
 * One Advanced-tab input, with whatever typeahead its field's cardinality calls for (ADR-0034).
 *
 * This is the ONLY place the suggest tier is branched on. The tier itself lives on the field
 * definition (`AdvancedFieldDef.suggest`), so adding a field is a data change, not a code change,
 * and a new field cannot quietly default into the wrong behaviour — `suggest` is required.
 */
export function SuggestField({
    fieldDef,
    value,
    onChange,
    placeholder,
}: {
    fieldDef: AdvancedFieldDef;
    value: string;
    onChange: (field: string, value: string) => void;
    placeholder?: string;
}) {
    const inputId = `adv-${fieldDef.field}`;

    if (fieldDef.suggest === "entity") {
        // Picking fills the attribute this box is FOR: the "Subject IRI" box gets the entity's IRI,
        // the "Subject label" box its label. Suggesting entities and then pasting the CURIE into a
        // label box would produce a query that matches nothing.
        const attributeOf = (entity: EntitySuggestion): string => {
            switch (fieldDef.entityAttribute) {
                case "iri":
                    return entity.iri ?? entity.id;
                case "label":
                    return entity.label ?? entity.id;
                default:
                    return entity.id;
            }
        };
        return (
            <EntitySuggest
                inputId={inputId}
                value={value}
                onTyped={(next) => onChange(fieldDef.field, next)}
                onPick={(entity) => onChange(fieldDef.field, attributeOf(entity))}
                side={fieldDef.entitySide ?? "subject"}
                placeholder={placeholder}
            />
        );
    }

    if (fieldDef.suggest === "vocab") {
        return (
            <VocabSuggest
                inputId={inputId}
                field={fieldDef.field}
                value={value}
                onTyped={(next) => onChange(fieldDef.field, next)}
                onPick={(picked) => onChange(fieldDef.field, picked)}
                placeholder={placeholder}
                // The justification vocabulary is SEMAPV CURIEs; show their labels (the query still
                // carries the raw CURIE). Other vocab fields have no such mapping and stay verbatim.
                formatOption={fieldDef.field === "mapping_justification"
                    ? formatMappingJustification : undefined}
            />
        );
    }

    // Free prose. A typeahead over a comment field would suggest noise, so it stays a plain box.
    return (
        <input
            id={inputId}
            type="text"
            className="input-default text-sm"
            placeholder={placeholder}
            value={value}
            onChange={(event) => onChange(fieldDef.field, event.target.value)}
        />
    );
}
