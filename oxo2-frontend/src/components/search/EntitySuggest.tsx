import { useState } from "react";
import Autocomplete from "@mui/material/Autocomplete";
import CircularProgress from "@mui/material/CircularProgress";
import TextField from "@mui/material/TextField";
import { useQuery } from "@tanstack/react-query";
import {
    fetchEntitySuggestions,
    MIN_SUGGEST_LENGTH,
    type EntitySide,
    type EntitySuggestion,
} from "../../pages/results/SuggestSlice";
import { useDebouncedValue } from "../../util/useDebouncedValue";

/**
 * Typeahead over entities (ADR-0034): type a label or CURIE prefix, get the entities it could
 * complete to, ranked with a leading-edge match above a mid-label token match and boosted by how
 * many mappings the entity has.
 *
 * `freeSolo` because typing and picking are different acts, and the control has to model both:
 *
 *  - `onInputChange` fires as the user types. That is a SEARCH — they may have typed a fragment, and
 *    submitting it runs the normal term search.
 *  - `onChange` fires when the user picks a row. That is a SELECTION — the value came out of the
 *    index and is unambiguous, so `onPick` gets the whole entity, not just its text.
 *
 * Callers that only care about the text can ignore `onPick`; callers that need the CURIE behind a
 * chosen label (the search box, which navigates to /search/<CURIE>) use it.
 */
export function EntitySuggest({
    value,
    onTyped,
    onPick,
    onSubmit,
    side = 'subject',
    prefixes = [],
    label,
    placeholder,
    inputId,
}: {
    value: string;
    /** The user typed. The value is a fragment; treat it as free text. */
    onTyped: (next: string) => void;
    /** The user picked a suggestion. The entity is exact. */
    onPick: (entity: EntitySuggestion) => void;
    /** Enter pressed without picking a suggestion. */
    onSubmit?: () => void;
    side?: EntitySide;
    prefixes?: string[];
    label?: string;
    placeholder?: string;
    inputId?: string;
}) {
    const [open, setOpen] = useState(false);
    const debouncedValue = useDebouncedValue(value);

    // Query on the DEBOUNCED value but render against the live one, so the box never lags the
    // keyboard even though the network does.
    const enabled = open && debouncedValue.trim().length >= MIN_SUGGEST_LENGTH;
    const { data: suggestions, isFetching } = useQuery({
        queryKey: ["suggestEntities", debouncedValue.trim(), side, prefixes.join(",")],
        queryFn: () => fetchEntitySuggestions(debouncedValue.trim(), side, prefixes),
        enabled,
        staleTime: 5 * 60_000,
    });

    // The server already ranked and filtered these; MUI's default client-side filter would then
    // re-filter them by substring and drop, for instance, the CURIE match for a typed label.
    const options = enabled ? (suggestions ?? []) : [];

    return (
        <Autocomplete<EntitySuggestion, false, false, true>
            freeSolo
            size="small"
            open={open}
            onOpen={() => setOpen(true)}
            onClose={() => setOpen(false)}
            options={options}
            filterOptions={(option) => option}
            loading={isFetching}
            inputValue={value}
            // A picked option is displayed by its CURIE: that is what the search actually runs on,
            // so showing the label would misrepresent what is in the box.
            getOptionLabel={(option) => (typeof option === "string" ? option : option.id)}
            // freeSolo means `selected` can be the raw typed string, not an entity.
            isOptionEqualToValue={(option, selected) =>
                option.id === (typeof selected === "string" ? selected : selected.id)}
            onInputChange={(_event, next, reason) => {
                if (reason === "input" || reason === "clear") {
                    onTyped(next);
                }
            }}
            onChange={(_event, picked) => {
                if (picked && typeof picked !== "string") {
                    onPick(picked);
                }
            }}
            renderOption={(props, option) => {
                const { key, ...liProps } = props as typeof props & { key?: string };
                return (
                    <li {...liProps} key={key ?? option.id}>
                        {/* label > id > IRI, matching EntityRefCell: the label is what a human
                            recognises, so it leads; the id is promoted to primary when there is no
                            label; the IRI is a muted third line. */}
                        <div className="flex flex-col gap-0.5 min-w-0 py-1 w-full">
                            {option.label && (
                                <span className="font-bold break-words">{option.label}</span>
                            )}
                            <span
                                className={option.label
                                    ? "text-sm text-neutral-black break-all"
                                    : "font-bold break-all"}
                            >
                                {option.id}
                            </span>
                            {option.iri && (
                                <span className="text-xs text-gray-500 break-all">{option.iri}</span>
                            )}
                        </div>
                        {option.mappingCount > 0 && (
                            <span className="ml-2 shrink-0 text-tertiary text-sm">
                                · {option.mappingCount.toLocaleString()}
                            </span>
                        )}
                    </li>
                );
            }}
            renderInput={(params) => (
                <TextField
                    {...params}
                    label={label}
                    placeholder={placeholder}
                    variant="outlined"
                    onKeyDown={(event) => {
                        // Only submit when the dropdown is not steering the Enter key: with a
                        // suggestion highlighted, Enter must pick it (Autocomplete's own onChange),
                        // not run a search for the half-typed fragment underneath.
                        if (event.key === "Enter" && !open) {
                            onSubmit?.();
                        }
                    }}
                    slotProps={{
                        ...params.slotProps,
                        // Keep the caller's id on the real <input> (the page targets #home-search).
                        htmlInput: {
                            ...params.slotProps.htmlInput,
                            ...(inputId ? { id: inputId } : {}),
                        },
                        input: {
                            ...params.slotProps.input,
                            endAdornment: (
                                <>
                                    {isFetching ? <CircularProgress color="inherit" size={16} /> : null}
                                    {params.slotProps.input.endAdornment}
                                </>
                            ),
                        },
                    }}
                />
            )}
        />
    );
}
