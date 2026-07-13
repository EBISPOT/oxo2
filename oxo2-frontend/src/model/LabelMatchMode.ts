// Label match mode (ADR-0026): how a free-text (label) query is matched against the subject label
// in a normal search (subject-side matching, ADR-0030). The codes match the backend LabelMatchType
// enum and are sent verbatim as the request `labelMatch` and carried in the URL `match` param. Only
// free-text terms are affected; IRI/CURIE terms are always exact subject_iri / subject_id lookups
// regardless of the mode.

export type LabelMatchMode = 'PARTIAL' | 'EXACT_CASE_INSENSITIVE' | 'EXACT_CASE_SENSITIVE';

export const LABEL_MATCH_LABELS: Record<LabelMatchMode, string> = {
    PARTIAL: 'Partial match',
    EXACT_CASE_INSENSITIVE: 'Exact match (ignore case)',
    EXACT_CASE_SENSITIVE: 'Exact match (case-sensitive)',
};

// Display order in the match-mode control.
export const LABEL_MATCH_ORDER: LabelMatchMode[] = [
    'PARTIAL',
    'EXACT_CASE_INSENSITIVE',
    'EXACT_CASE_SENSITIVE',
];

// Default (ADR-0026): case-insensitive exact match — mirrors the backend default so an omitted
// `match` param and an omitted request field resolve to the same behaviour.
export const DEFAULT_LABEL_MATCH: LabelMatchMode = 'EXACT_CASE_INSENSITIVE';

// Coerce an arbitrary string (e.g. the URL `match` param) to a valid mode, defaulting when absent
// or unrecognised.
export function asLabelMatchMode(value?: string | null): LabelMatchMode {
    if (value && value in LABEL_MATCH_LABELS) {
        return value as LabelMatchMode;
    }
    return DEFAULT_LABEL_MATCH;
}
