import { useState, type JSX, type MouseEvent } from "react";
import {
    Badge,
    Box,
    Checkbox,
    FormControlLabel,
    IconButton,
    Popover,
    Typography,
} from "@mui/material";
import FilterListIcon from "@mui/icons-material/FilterList";
import {
    WEAK_PREDICATE_HINTS,
    WEAK_PREDICATE_LABELS,
    WEAK_PREDICATE_ORDER,
    WeakPredicate,
} from "../../model/WeakPredicate";

// The two "also show" predicate checkboxes (ADR-0035), living in the Predicate column header and
// mirroring InferenceTypeFilterPopover's idiom: a filter icon, dotted when the default has been
// changed, opening a small popover.
//
// Checkboxes rather than a radio group, because the two predicates are independent — ontology
// hierarchy and loose cross-references are different questions, and asking for one is no reason to be
// shown the other. Both start unchecked: on an OLS-derived corpus they are ~98% of all mappings, so
// showing them by default buries the equivalences people are actually looking for.
//
// This control also drives the typeahead, which is filtered by the same selection so that it can only
// ever suggest entities the resulting search can show.

/** Both lists are kept in WEAK_PREDICATE_ORDER, so equality is positional. */
function sameSelection(a: WeakPredicate[], b: WeakPredicate[]): boolean {
    return a.length === b.length && a.every((code, index) => code === b[index]);
}

export function WeakPredicateFilterPopover({
    value,
    onChange,
}: {
    value: WeakPredicate[];
    onChange: (next: WeakPredicate[]) => void;
}): JSX.Element {
    const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);

    // The ticks are held locally while the popover is open and committed when it closes. Committing
    // per tick would refetch the table, whose columns are memoised on this value — rebuilding them
    // remounts the header and so closes this popover, leaving you unable to tick the second box
    // without reopening. It also spares a refetch when both boxes are being changed at once.
    const [draft, setDraft] = useState<WeakPredicate[]>(value);

    // Anything ticked is a departure from the default, so the icon is marked.
    const isNarrowed = value.length > 0;

    const handleOpen = (event: MouseEvent<HTMLElement>) => {
        event.stopPropagation();
        setDraft(value); // seed from the applied state, discarding any abandoned draft
        setAnchorEl(event.currentTarget);
    };

    const handleClose = () => {
        setAnchorEl(null);
        if (!sameSelection(draft, value)) {
            onChange(draft);
        }
    };

    const handleToggle = (predicate: WeakPredicate, checked: boolean) => {
        // Rebuild from the canonical order rather than pushing/splicing, so the resulting URL is the
        // same however the boxes were clicked.
        setDraft(
            WEAK_PREDICATE_ORDER.filter((code) =>
                code === predicate ? checked : draft.includes(code)
            )
        );
    };

    return (
        <>
            <Badge color="primary" variant="dot" overlap="circular" invisible={!isNarrowed}>
                <IconButton
                    size="small"
                    title="Show hidden predicates"
                    onClick={handleOpen}
                    color={isNarrowed ? "primary" : "default"}
                >
                    <FilterListIcon fontSize="small" />
                </IconButton>
            </Badge>
            <Popover
                open={Boolean(anchorEl)}
                anchorEl={anchorEl}
                onClose={handleClose}
                anchorOrigin={{ vertical: "bottom", horizontal: "left" }}
            >
                <Box
                    sx={{ p: 2, display: "flex", flexDirection: "column", gap: 1, width: 320 }}
                    onClick={(event) => event.stopPropagation()}
                >
                    <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
                        Also show
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                        These predicates are hidden by default. They do not assert that two entities are
                        the same, and they outnumber the mappings that do.
                    </Typography>
                    {WEAK_PREDICATE_ORDER.map((predicate) => (
                        <FormControlLabel
                            key={predicate}
                            control={
                                <Checkbox
                                    size="small"
                                    checked={draft.includes(predicate)}
                                    onChange={(event) =>
                                        handleToggle(predicate, event.target.checked)
                                    }
                                />
                            }
                            label={
                                <Box>
                                    <Typography variant="body2">
                                        {WEAK_PREDICATE_LABELS[predicate]}
                                    </Typography>
                                    <Typography variant="caption" color="text.secondary">
                                        {WEAK_PREDICATE_HINTS[predicate]}
                                    </Typography>
                                </Box>
                            }
                            sx={{ alignItems: "flex-start", mr: 0 }}
                        />
                    ))}
                </Box>
            </Popover>
        </>
    );
}
