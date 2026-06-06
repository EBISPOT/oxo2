import { INFERENCE_TYPE_BADGE_CLASS, asInferenceType, inferenceTypeLabel } from "../../model/InferenceType";

/**
 * 3-way inference-type badge (ADR-0011): Asserted / SSSOM inference / OWL inference. Replaces the
 * old 2-way asserted/inferred span used across the result tables and the mapping-set selector.
 */
export function InferenceTypeBadge({ value }: { value?: string | null }) {
    const type = asInferenceType(value);
    return (
        <span className={`inline-block rounded px-2 py-0.5 text-xs font-medium ${INFERENCE_TYPE_BADGE_CLASS[type]}`}>
            {inferenceTypeLabel(type)}
        </span>
    );
}
