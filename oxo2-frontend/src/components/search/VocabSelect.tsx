import Autocomplete from "@mui/material/Autocomplete";
import TextField from "@mui/material/TextField";
import { useState } from "react";
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
    localOptions,
    label,
    placeholder,
    formatOption,
    formatOptionTitle,
    inputId,
    autoOpen = false,
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
    /**
     * A fixed option list, for callers whose rows are local data rather than a backend search (e.g.
     * the Asserted Mappings table). When given, no facet request is made and `search` is ignored —
     * the caller is expected to have counted the values over the same rows its table is showing, so
     * the scoped-to-what-is-on-screen promise above still holds.
     */
    localOptions?: ValueSuggestion[];
    label?: string;
    placeholder?: string;
    /** Map a raw value to its display label; identity when omitted. */
    formatOption?: (value: string) => string;
    /**
     * Map a raw value to a fuller description shown as the option's hover tooltip. Use it when
     * `formatOption` deliberately shortens the label (e.g. a SEMAPV CURIE to its CamelCase local name)
     * so the full form stays one hover away. Omitted → no tooltip.
     */
    formatOptionTitle?: (value: string) => string;
    inputId?: string;
    /**
     * Open the value list (and focus the field) as soon as the component mounts. Set this when the
     * combobox lives in a popover the user just opened to pick a value — the whole reason the popover
     * is on screen — so the values drop straight down instead of the user having to hunt for the caret.
     * Leave it off where the combobox sits inline among other controls (e.g. an MRT column-filter row),
     * where a list springing open unbidden would be intrusive.
     */
    autoOpen?: boolean;
}) {
    // Blank query → the backend facets the whole result set (every value present, most common first).
    // 25 is the backend's contextual cap and comfortably covers any controlled vocabulary. staleTime
    // is short, not Infinity: these are scoped to a result set the user is actively changing.
    const { data: fetchedValues, isLoading } = useQuery({
        queryKey: ["contextualVocab", field, JSON.stringify(search)],
        queryFn: () => fetchContextualValues(field, "", search, 25),
        staleTime: 30_000,
        enabled: !localOptions,
    });

    const format = formatOption ?? ((raw: string) => raw);
    const options = localOptions ?? fetchedValues ?? [];
    // The applied value is stored as a raw string; resolve it back to the option the list holds so
    // the controlled combobox shows it (humanised) after a reload or a URL restore. It is always in
    // the list — the backend strips this field's own filter before faceting, so the rows carrying the
    // picked value are still counted.
    const selected = options.find((option) => option.value === value) ?? null;

    // Control the popup explicitly instead of leaning on openOnFocus. MUI deliberately suppresses
    // openOnFocus for the initial autoFocus, so a focus-based reveal leaves an autofocused-but-closed
    // box — which looks exactly like the "dropdown with no options" this field was reported to show.
    // Seeding the state from autoOpen opens the list on mount; onOpen/onClose then hand control back to
    // MUI (pick, Escape, click-away, refocus) as normal.
    const [open, setOpen] = useState(autoOpen);

    return (
        <Autocomplete<ValueSuggestion, false, false, false>
            size="small"
            options={options}
            value={selected}
            open={open}
            onOpen={() => setOpen(true)}
            onClose={() => setOpen(false)}
            // Distinguish "still fetching" from "genuinely none". Without this, MUI shows its
            // noOptionsText ("No options") the instant the popover opens, so a slow suggest fetch —
            // e.g. under load, when the backend is contending for CPU — reads as if the field had no
            // values in the current results. `loading` swaps that for loadingText until the query
            // resolves; only a resolved-empty facet then shows noOptionsText.
            loading={isLoading}
            loadingText="Loading…"
            noOptionsText="No values in these results"
            getOptionLabel={(option) => format(option.value)}
            isOptionEqualToValue={(option, chosen) => option.value === chosen.value}
            onChange={(_event, picked) => onPick(picked ? picked.value : "")}
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
                    // Land the cursor in the field when we auto-open, so ↑/↓ and type-to-narrow work
                    // straight away. Off otherwise, to avoid stealing focus from an inline filter row.
                    autoFocus={autoOpen}
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
