import type {ReactNode} from "react";
import {HelpTerm} from "../common/HelpTerm";

// Plain-language explanations of what each mapping column means, shown as a tooltip when the user
// hovers (or keyboard-focuses) the column header. One map serves every table that shows mappings —
// the results table, its same-SPO detail panel, and the asserted-premises table — so headers naming
// the same thing cannot drift apart.
//
// Keyed by what the column *means*, not by any table's column `id`: the same concept is `subject_label`
// in NormalResultsTable and `subject` in InferredMappings. ColumnHelpTopic keeps that honest — a key
// that is not in this map is a compile error, not a header that silently loses its tooltip.
//
// These are for a reader who does not know SSSOM. Keep them to a sentence or two of the domain
// meaning; the mechanics of a column's filter belong on its filter popover, not here.
const COLUMN_HELP = {
    subject_label:
        "The entity being mapped: the subject of the subject–predicate–object triple.",
    predicate_label:
        "The relation the mapping asserts between subject and object, such as skos:exactMatch. " +
        "A “Not” modifier negates it — the relation does not hold.",
    object_label:
        "The entity the subject is mapped to: the object of the subject–predicate–object triple.",
    mapping_justification:
        "Why the mapping is held to be true — curated by a human, derived by matching labels, and " +
        "so on. Hover a value to see its full SEMAPV term.",
    confidence:
        "How confident the mapping's source is in it, from 0 to 1. Shown as — when the source " +
        "supplied no value.",
    distance:
        "How many mappings were chained to reach this one. 1 is asserted straight from a mapping " +
        "set; higher numbers were inferred over that many hops.",
    inference_type:
        "Whether the mapping was asserted — taken directly from an input mapping set — or inferred " +
        "by OxO2's reasoning across mapping sets.",
    mapping_provider:
        "The source that provided the mapping, as recorded by the mapping set.",
    mapping_set:
        "The mapping set the mapping was published in. Follow the link for the set's own details.",
} as const;

export type ColumnHelpTopic = keyof typeof COLUMN_HELP;

// A column header whose text carries its explanation, rendered as the shared defined-term treatment
// (HelpTerm) so it looks and behaves the same as every other explained term in the UI. The dotted
// underline costs no horizontal space, which the 110px Distance and 130px Type columns have none of.
export function ColumnHeaderLabel({topic, label}: {topic: ColumnHelpTopic; label: ReactNode}) {
    return <HelpTerm help={COLUMN_HELP[topic]} label={label} />;
}
