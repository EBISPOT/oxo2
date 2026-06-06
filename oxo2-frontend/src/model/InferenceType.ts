// 3-valued inference type (ADR-0011), replacing the old is_inferred boolean. The codes match the
// backend InferenceType enum; values arrive from Solr as these exact strings.

export type InferenceType = 'ASSERTED' | 'OWL_INFERENCE' | 'SSSOM_INFERENCE';

export const INFERENCE_TYPE_LABELS: Record<InferenceType, string> = {
    ASSERTED: 'Asserted',
    OWL_INFERENCE: 'OWL inference',
    SSSOM_INFERENCE: 'SSSOM inference',
};

// Display / filter order: asserted first, then the inference tiers users care about most.
export const INFERENCE_TYPE_ORDER: InferenceType[] = ['ASSERTED', 'SSSOM_INFERENCE', 'OWL_INFERENCE'];

// Default filter selection (ADR-0011): users generally want asserted + SSSOM inferences; OWL
// inferences are hidden until explicitly requested.
export const DEFAULT_INFERENCE_TYPES: InferenceType[] = ['ASSERTED', 'SSSOM_INFERENCE'];

// Tailwind badge classes per type.
export const INFERENCE_TYPE_BADGE_CLASS: Record<InferenceType, string> = {
    ASSERTED: 'bg-gray-100 text-gray-700',
    SSSOM_INFERENCE: 'bg-amber-100 text-amber-800',
    OWL_INFERENCE: 'bg-sky-100 text-sky-800',
};

// A missing value defaults to ASSERTED (Solr defaults the field to ASSERTED).
export function asInferenceType(value?: string | null): InferenceType {
    if (value && value in INFERENCE_TYPE_LABELS) {
        return value as InferenceType;
    }
    return 'ASSERTED';
}

export function inferenceTypeLabel(value?: string | null): string {
    return INFERENCE_TYPE_LABELS[asInferenceType(value)];
}
