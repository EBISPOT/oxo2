import Autocomplete from "@mui/material/Autocomplete";
import TextField from "@mui/material/TextField";

/**
 * Multi-select for ontology (CURIE) prefixes, driving the cross-ontology mapping from/to fields
 * (ADR-0024). Values remain prefixes; available ontology metadata supplies searchable names and
 * descriptions. An optional count map annotates each option (e.g. mappings as
 * subject, or reachable-target counts from /api/v2/ontologies?forSubject=).
 */
export function OntologySelector({ label, placeholder, options, metadata, counts, value, onChange }: {
    label: string;
    placeholder?: string;
    options: string[];
    metadata: ReadonlyMap<string, { name: string; description?: string }>;
    counts?: Record<string, number>;
    value: string[];
    onChange: (next: string[]) => void;
}) {
    const optionLabel = (prefix: string) => {
        const name = metadata.get(prefix.toUpperCase())?.name;
        if (!name) return prefix;
        return name.toUpperCase().endsWith(`(${prefix.toUpperCase()})`)
            ? name : `${name} (${prefix})`;
    };
    return (
        <Autocomplete
            multiple
            size="small"
            options={options}
            value={value}
            getOptionLabel={optionLabel}
            onChange={(_event, next) => onChange(next as string[])}
            isOptionEqualToValue={(option, selected) => option === selected}
            renderOption={({ key, ...props }, option) => {
                const description = metadata.get(option.toUpperCase())?.description;
                return (
                    <li {...props} key={key}>
                        <div className="min-w-0 py-1">
                            <span>{optionLabel(option)}</span>
                            {counts && counts[option] != null && (
                                <span className="ml-2 text-gray-500 text-sm">· {counts[option].toLocaleString()}</span>
                            )}
                            {description && (
                                <span className="block text-sm text-gray-500 line-clamp-2" title={description}>
                                    {description}
                                </span>
                            )}
                        </div>
                    </li>
                );
            }}
            renderInput={(params) => (
                <TextField {...params} label={label} placeholder={placeholder} variant="outlined" />
            )}
        />
    );
}
