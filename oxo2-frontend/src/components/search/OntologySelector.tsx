import Autocomplete from "@mui/material/Autocomplete";
import TextField from "@mui/material/TextField";

/**
 * Multi-select for ontology (CURIE) prefixes, driving the cross-ontology mapping from/to fields
 * (ADR-0024). Options are prefixes; an optional count map annotates each option (e.g. mappings as
 * subject, or reachable-target counts from /api/v2/ontologies?forSubject=).
 */
export function OntologySelector({ label, placeholder, options, counts, value, onChange }: {
    label: string;
    placeholder?: string;
    options: string[];
    counts?: Record<string, number>;
    value: string[];
    onChange: (next: string[]) => void;
}) {
    return (
        <Autocomplete
            multiple
            size="small"
            options={options}
            value={value}
            onChange={(_event, next) => onChange(next as string[])}
            isOptionEqualToValue={(option, selected) => option === selected}
            renderOption={(props, option) => (
                <li {...props} key={option}>
                    <span>{option}</span>
                    {counts && counts[option] != null && (
                        <span className="ml-2 text-tertiary text-sm">· {counts[option].toLocaleString()}</span>
                    )}
                </li>
            )}
            renderInput={(params) => (
                <TextField {...params} label={label} placeholder={placeholder} variant="outlined" />
            )}
        />
    );
}
