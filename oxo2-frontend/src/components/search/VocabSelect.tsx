import Autocomplete from "@mui/material/Autocomplete";
import TextField from "@mui/material/TextField";
import { useQuery } from "@tanstack/react-query";
import { fetchContextualValues, type ValueSuggestion } from "../../pages/results/SuggestSlice";

/**
 * A pick-only combobox over a low-cardinality field's values PRESENT IN THE CURRENT RESULT SET
 * (ADR-0034).
 *
 * Two properties, both deliberate:
 *  - <b>Scoped to the live search, not the whole corpus.</b> The options are faceted over the same
 *    request the result table is showing (`search`), so every value offered is one that actually
 *    occurs in the rows on screen — picking one can never empty the table. Sourcing the whole
 *    vocabulary instead would offer justifications the current search does not contain, which is the
 *    exact "a suggestion must be a promise the search returns rows" trap. The count beside each value
 *    is how many mappings in the result set carry it.
 *  - <b>Pick-only.</b> Unlike VocabSuggest (freeSolo, where a typed fragment is itself a "contains"
 *    filter), typing here only narrows the dropdown; the filter is applied solely by picking. That is
 *    the right model for a controlled vocabulary whose values are stored as opaque CURIEs but shown
 *    by a friendly label (`formatOption`) — a typed fragment of the label could never be matched
 *    against the raw CURIE the backend filters on, so we never send one. Picking emits the exact
 *    underlying value (the CURIE); clearing emits "".
 *
 * The facet is fetched with a blank prefix, which the backend reads as "every value in the result
 * set" (SolrQueryBuilder.addPrefixFacets). It also strips any in-progress filter on this same field
 * server-side, so the list keeps offering the other values even after one has been picked.
 */
export function VocabSelect({
    field,
    value,
    onPick,
    search,
    label,
    placeholder,
    formatOption,
    inputId,
}: {
    /** The Solr field whose values to offer, e.g. `mapping_justification`. */
    field: string;
    /** The currently applied filter value (the raw underlying value, e.g. a CURIE), or "". */
    value: string;
    /** A value was picked (its raw underlying value) or the selection was cleared (""). */
    onPick: (value: string) => void;
    /**
     * The live search request the options must be scoped to — the same body POSTed to
     * /api/v2/mappings/search for the result table. Omitted only degrades to a whole-corpus facet.
     */
    search?: unknown;
    label?: string;
    placeholder?: string;
    /** Map a raw value to its display label; identity when omitted. */
    formatOption?: (value: string) => string;
    inputId?: string;
}) {
    // Blank query → the backend facets the whole result set (every value present, most common first).
    // 25 is the backend's contextual cap and comfortably covers any controlled vocabulary. staleTime
    // is short, not Infinity: these are scoped to a result set the user is actively changing.
    const { data: values } = useQuery({
        queryKey: ["contextualVocab", field, JSON.stringify(search)],
        queryFn: () => fetchContextualValues(field, "", search, 25),
        staleTime: 30_000,
    });

    const format = formatOption ?? ((raw: string) => raw);
    const options = values ?? [];
    // The applied value is stored as a raw string; resolve it back to the option the list holds so
    // the controlled combobox shows it (humanised) after a reload or a URL restore. It is always in
    // the list — the backend strips this field's own filter before faceting, so the rows carrying the
    // picked value are still counted.
    const selected = options.find((option) => option.value === value) ?? null;

    return (
        <Autocomplete<ValueSuggestion, false, false, false>
            size="small"
            options={options}
            value={selected}
            getOptionLabel={(option) => format(option.value)}
            isOptionEqualToValue={(option, chosen) => option.value === chosen.value}
            onChange={(_event, picked) => onPick(picked ? picked.value : "")}
            renderOption={(props, option) => {
                const { key, ...liProps } = props as typeof props & { key?: string };
                return (
                    <li {...liProps} key={key ?? option.value}>
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
