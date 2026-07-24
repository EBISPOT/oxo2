import Autocomplete from "@mui/material/Autocomplete";
import TextField from "@mui/material/TextField";
import { useQuery } from "@tanstack/react-query";
import { fetchDistinctValues, type ValueSuggestion } from "../../pages/results/SuggestSlice";

/**
 * Typeahead over a controlled-vocabulary field (ADR-0034): predicates, justifications, licenses,
 * mapping sets — anything with tens to a few hundred distinct values.
 *
 * Unlike the entity typeahead, this fetches the WHOLE value list once and filters it client-side.
 * That is the right trade at this cardinality: the corpus is static between dataloads, so the list
 * can be cached forever, and filtering locally means the dropdown responds with no network at all.
 * It is the same pattern OntologySelector already uses for ontology prefixes.
 *
 * `freeSolo` because typing and picking mean different things (ADR-0034): a typed fragment is a
 * substring search, a picked value is exact.
 */
export function VocabSuggest({
    field,
    value,
    onTyped,
    onPick,
    label,
    placeholder,
    inputId,
    formatOption,
    formatOptionTitle,
}: {
    /** The Solr field whose vocabulary to offer, e.g. `mapping_justification`. */
    field: string;
    value: string;
    onTyped: (next: string) => void;
    onPick: (value: string) => void;
    label?: string;
    placeholder?: string;
    inputId?: string;
    /**
     * Map a raw value to a friendly display label; identity when omitted. Applied to both what the
     * dropdown shows and what MUI's client filter matches typed text against, so a user can type the
     * label ("man…") even though the underlying value is an opaque CURIE. A picked value still emits
     * the raw CURIE; only the display is translated.
     */
    formatOption?: (value: string) => string;
    /**
     * Map a raw value to a fuller description shown as the option's hover tooltip, for when
     * `formatOption` deliberately shortens the label. Omitted → no tooltip.
     */
    formatOptionTitle?: (value: string) => string;
}) {
    // Static between dataloads, so it never needs refetching within a session.
    const { data: values } = useQuery({
        queryKey: ["distinctValues", field],
        queryFn: () => fetchDistinctValues(field),
        staleTime: Infinity,
    });

    const format = formatOption ?? ((raw: string) => raw);

    return (
        <Autocomplete<ValueSuggestion, false, false, true>
            freeSolo
            size="small"
            options={values ?? []}
            // MUI's default client-side filter is what we want here — the whole list is local. It
            // matches typed text against getOptionLabel, so formatting the label lets users type it.
            getOptionLabel={(option) => (typeof option === "string" ? option : format(option.value))}
            isOptionEqualToValue={(option, selected) =>
                option.value === (typeof selected === "string" ? selected : selected.value)}
            // A picked value is stored raw (a CURIE); show its label. A half-typed fragment is not a
            // known value, so format() returns it unchanged and the user sees exactly what they type.
            inputValue={format(value)}
            onInputChange={(_event, next, reason) => {
                if (reason === "input" || reason === "clear") {
                    onTyped(next);
                }
            }}
            onChange={(_event, picked) => {
                if (picked && typeof picked !== "string") {
                    onPick(picked.value);
                }
            }}
            renderOption={(props, option) => {
                const { key, ...liProps } = props as typeof props & { key?: string };
                return (
                    <li {...liProps} key={key ?? option.value} title={formatOptionTitle?.(option.value)}>
                        <span className="break-all">{format(option.value)}</span>
                        <span className="ml-2 shrink-0 text-tertiary text-sm">
                            · {option.count.toLocaleString()}
                        </span>
                    </li>
                );
            }}
            renderInput={(params) => (
                <TextField
                    {...params}
                    label={label}
                    placeholder={placeholder}
                    variant="outlined"
                    slotProps={{
                        ...params.slotProps,
                        htmlInput: {
                            ...params.slotProps.htmlInput,
                            ...(inputId ? { id: inputId } : {}),
                        },
                    }}
                />
            )}
        />
    );
}
