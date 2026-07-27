import { useMemo } from "react";
import { MenuItem, TextField } from "@mui/material";
import { useQuery } from "@tanstack/react-query";
import { fetchContextualValues } from "../../pages/results/SuggestSlice";

/**
 * A dropdown for a small-integer column filter whose entries track the values present in the current
 * search (ADR-0034). It lists 1..max, where max is the largest value in the live result set. An "All"
 * entry (which clears the filter) is prepended ONLY when the cap holds the list short of that maximum —
 * i.e. when results reach past the top number, so "at most <top>" would hide the longer rows that only
 * "All" keeps. When the top number already reaches the maximum, picking it is identical to All, so All
 * is dropped and the top number stands in as the show-everything default.
 *
 * `max` is faceted over the live search, exactly like the contextual value suggests: scoped by every
 * restriction the result table applies, with this field's own in-progress filter stripped server-side
 * (SolrQueryBuilder.buildValueSuggestQuery). Stripping it is what lets the range grow back — faceting
 * with the current pick still applied would only ever report values ≤ it, so once you chose 2 the top
 * entry could never climb past 2 again.
 *
 * The facet runs on mount, and this component only mounts while the column-filter popover is open, so
 * the extra round trip is paid only when the user actually reaches for the filter — not on every
 * search.
 *
 * `max` caps the list: when the result set reaches further than `max`, the extra values are reached
 * through "All" rather than offered as their own entries — so a long inference tail cannot stretch the
 * dropdown to a dozen barely-used hop counts. This is also the only case that keeps "All": below the
 * cap the list is complete, so its top entry is already "everything" and "All" would be a duplicate.
 *
 * Distance is the only user today: the backend reads the picked value as an inclusive maximum ("at
 * most N hops", distance:[* TO N]), so the entries are maxima, not equalities.
 */
export function RangeSelect({
    field,
    value,
    search,
    onChange,
    label,
    max,
}: {
    /** The Solr field whose maximum sizes the list, e.g. `distance`. */
    field: string;
    /** The currently applied value, or "" for All. */
    value: string;
    /** The live search request the maximum must be scoped to — the body POSTed to the search. */
    search?: unknown;
    /** A value was chosen (its numeric string) or the selection was cleared (""). */
    onChange: (value: string) => void;
    label?: string;
    /** Largest value ever offered; values above it fall under "All". Uncapped when omitted. */
    max?: number;
}) {
    // Blank query → the backend facets every distinct value of the field in the result set; 25 buckets
    // comfortably covers any real hop range. staleTime is short, not Infinity: it is scoped to a result
    // set the user is actively changing.
    const { data } = useQuery({
        queryKey: ["rangeMax", field, JSON.stringify(search)],
        queryFn: () => fetchContextualValues(field, "", search, 25),
        staleTime: 30_000,
    });

    // The largest value actually present in the (distance-filter-stripped) result set. A real hop range
    // fits well inside 25 buckets, so this is the true maximum, not a truncated sample.
    const fromResults = useMemo(
        () => (data ?? []).reduce(
            (largest, suggestion) => Math.max(largest, Number(suggestion.value) || 0), 0),
        [data]);

    const top = useMemo(() => {
        // Union the applied value so a value restored from the URL stays selectable before the facet
        // resolves (or if the data has since shifted below it), and floor at 1 so the range is never
        // empty even when the search returned nothing.
        const adaptive = Math.max(fromResults, Number(value) || 0, 1);
        return max ? Math.min(adaptive, max) : adaptive;
    }, [fromResults, value, max]);

    const options = useMemo(
        () => Array.from({ length: top }, (_, index) => String(index + 1)), [top]);

    // "All" earns a slot only when the cap holds the list short of the real maximum — then "at most
    // <top>" would hide the longer-distance rows that only "All" (no filter) keeps. Otherwise the top
    // number already selects every row, so it stands in as the show-everything default and "All" is
    // dropped as a duplicate. When "All" is absent, an unset filter ("") displays as that top number.
    const showAll = fromResults > top;
    const emptyDisplay = showAll ? "" : String(top);
    const displayValue = value !== "" ? value : emptyDisplay;

    return (
        <TextField
            select
            label={label}
            size="small"
            variant="outlined"
            // displayEmpty + shrink so an unset filter renders as its stand-in ("All" when shown, else
            // the top number) rather than an apparently blank input.
            slotProps={{ select: { displayEmpty: true }, inputLabel: { shrink: true } }}
            value={displayValue}
            onChange={(event) => onChange(event.target.value)}
        >
            {showAll && <MenuItem value="">All</MenuItem>}
            {options.map((option) => (
                <MenuItem key={option} value={option}>{option}</MenuItem>
            ))}
        </TextField>
    );
}
