import { useState } from "react";
import Autocomplete from "@mui/material/Autocomplete";
import TextField from "@mui/material/TextField";
import { useQuery } from "@tanstack/react-query";
import { fetchContextualValues, type ValueSuggestion } from "../../pages/results/SuggestSlice";
import { useDebouncedValue } from "../../util/useDebouncedValue";

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
 */
export function ValueSuggest({
    field,
    value,
    search,
    onTyped,
    onPick,
    label,
}: {
    /** The Solr field being filtered, e.g. `object_label`. */
    field: string;
    value: string;
    /** The live search request the suggestions must be scoped to. */
    search: unknown;
    /** The user typed a fragment — a CONTAINS filter. */
    onTyped: (next: string) => void;
    /** The user picked a value out of the index — an EXACT filter. */
    onPick: (picked: string) => void;
    label?: string;
}) {
    const [open, setOpen] = useState(false);
    const debouncedValue = useDebouncedValue(value);

    const enabled = open && debouncedValue.trim().length > 0;
    const { data: suggestions, isFetching } = useQuery({
        queryKey: ["suggestValues", field, debouncedValue.trim(), JSON.stringify(search)],
        queryFn: () => fetchContextualValues(field, debouncedValue.trim(), search),
        enabled,
        // Short, not Infinity: these are scoped to a result set the user is actively changing.
        staleTime: 30_000,
    });

    const options = enabled ? (suggestions ?? []) : [];

    return (
        <Autocomplete<ValueSuggestion, false, false, true>
            freeSolo
            size="small"
            open={open}
            onOpen={() => setOpen(true)}
            onClose={() => setOpen(false)}
            options={options}
            // Server-side faceted and prefix-matched already; MUI's client filter would re-filter and
            // drop values whose casing differs from what was typed.
            filterOptions={(option) => option}
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
