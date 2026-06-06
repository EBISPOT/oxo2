import { ToggleButton, ToggleButtonGroup } from "@mui/material";
import { INFERENCE_TYPE_LABELS, INFERENCE_TYPE_ORDER, InferenceType } from "../../model/InferenceType";

/**
 * Multi-select inference-type filter (ADR-0011): each of Asserted / SSSOM inference / OWL inference
 * can be toggled independently. Replaces the old exclusive tri-state (all/asserted/inferred). An
 * empty selection means "no restriction" (the backend treats an absent/empty list as all types).
 */
export function InferenceTypeFilter({
    value,
    onChange,
}: {
    value: InferenceType[];
    onChange: (next: InferenceType[]) => void;
}) {
    return (
        <ToggleButtonGroup
            size="small"
            value={value}
            onChange={(_event, next: InferenceType[]) => onChange(next)}
            aria-label="Filter by inference type"
        >
            {INFERENCE_TYPE_ORDER.map((type) => (
                <ToggleButton key={type} value={type}>
                    {INFERENCE_TYPE_LABELS[type]}
                </ToggleButton>
            ))}
        </ToggleButtonGroup>
    );
}
