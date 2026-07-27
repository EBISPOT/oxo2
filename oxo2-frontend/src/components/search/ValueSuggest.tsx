import { useState } from "react";
import Autocomplete, { createFilterOptions } from "@mui/material/Autocomplete";
import TextField from "@mui/material/TextField";
import { useQuery } from "@tanstack/react-query";
import { fetchContextualValues, type ValueSuggestion } from "../../pages/results/SuggestSlice";
import { useDebouncedValue } from "../../util/useDebouncedValue";

// For the local-data path only: narrow the caller's fixed option list by the typed fragment,
// case-insensitively, matching anywhere in the value (the same "contains" the filter itself applies).
// The backend path leaves options untouched — the facet already prefix-matched them server-side.
const filterLocalOptions = createFilterOptions<ValueSuggestion>({
    stringify: (option) => option.value,
    matchFrom: "any",
    trim: true,
});

/**
 * Contextual typeahead for a result-table column filter (ADR-0034).
 *
 * The suggestions are faceted over the LIVE SEARCH, not the whole corpus, so every value offered is
 * one that actually occurs in the current result set. That is the whole promise of this tier: you
 * cannot pick a filter that empties the table.
 *
 * The count beside each value is the number of MAPPINGS carrying it. With same-SPO collapse on
 * (ADR-0023) the table may then show slightly fewer rows, because several mappings of one triple
 * collapse into a single row.
 *
 * The typed value is deliberately NOT sent as part of `search` by the caller for this field — the
 * facet must not be scoped by the half-typed value it is trying to complete. (The backend strips it
 * too; that half has the test.)
 *
 * `localOptions` swaps the backend facet for a fixed list the caller counted itself, for a table whose
 * rows are local data rather than a search (e.g. the Asserted Mappings table). No request is made and
 * `search` is ignored; MUI narrows the list by the typed fragment client-side. The caller counts over
 * the same rows the table is showing, so the "cannot pick a value that empties the table" promise holds.
 */
export function ValueSuggest({
    field,
    value,
    search,
    localOptions,
    onTyped,
    onPick,
    label,
}: {
    /** The Solr field being filtered, e.g. `object_label`. */
    field: string;
    value: string;
    /** The live search request the suggestions must be scoped to. Ignored when `localOptions` is set. */
    search: unknown;
    /**
     * A fixed, caller-counted option list to offer instead of a backend facet, for local-data tables.
     * When present, no suggest request is made and the list is narrowed client-side as the user types.
     */
    localOptions?: ValueSuggestion[];
    /** The user typed a fragment — a CONTAINS filter. */
    onTyped: (next: string) => void;
    /** The user picked a value out of the index — an EXACT filter. */
    onPick: (picked: string) => void;
    label?: string;
}) {
    const [open, setOpen] = useState(false);
    const debouncedValue = useDebouncedValue(value);

    const usingLocalOptions = localOptions !== undefined;
    const enabled = open && debouncedValue.trim().length > 0;
    const { data: suggestions, isFetching } = useQuery({
        queryKey: ["suggestValues", field, debouncedValue.trim(), JSON.stringify(search)],
        queryFn: () => fetchContextualValues(field, debouncedValue.trim(), search),
        enabled: enabled && !usingLocalOptions,
        // Short, not Infinity: these are scoped to a result set the user is actively changing.
        staleTime: 30_000,
    });

    // Local options are always available (no fetch, no typed-length gate) and narrowed by MUI below;
    // the backend list only materialises once a fragment has been typed and fetched.
    const options = usingLocalOptions ? localOptions : (enabled ? (suggestions ?? []) : []);

    return (
        <Autocomplete<ValueSuggestion, false, false, true>
            freeSolo
            size="small"
            open={open}
            onOpen={() => setOpen(true)}
            onClose={() => setOpen(false)}
            options={options}
            // Backend path: server-side faceted and prefix-matched already, so MUI's client filter is
            // bypassed (it would re-filter and drop values whose casing differs from what was typed).
            // Local path: MUI does the narrowing, since the caller hands over the full list unfiltered.
            filterOptions={usingLocalOptions ? filterLocalOptions : (allOptions) => allOptions}
            loading={isFetching}
            getOptionLabel={(option) => (typeof option === "string" ? option : option.value)}
            isOptionEqualToValue={(option, selected) =>
                option.value === (typeof selected === "string" ? selected : selected.value)}
            inputValue={value}
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
                    <li {...liProps} key={key ?? option.value}>
                        <span className="break-all">{option.value}</span>
                        {/* Mappings behind this value — the reason a contextual suggest is worth the
                            extra round trip. */}
                        <span className="ml-2 shrink-0 text-tertiary text-sm">
                            · {option.count.toLocaleString()}
                        </span>
                    </li>
                );
            }}
            renderInput={(params) => (
                <TextField {...params} label={label} size="small" variant="outlined" />
            )}
        />
    );
}
