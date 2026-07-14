import { useState, type JSX, type MouseEvent } from "react";
import { Badge, Box, Button, IconButton, Popover, TextField, Typography } from "@mui/material";
import FilterListIcon from "@mui/icons-material/FilterList";
import { ValueSuggest } from "../search/ValueSuggest";

export interface FilterFieldDef {
    /** Canonical Solr field name (snake_case); resolved by the backend MappingEnum. */
    field: string;
    /** User-facing input label. */
    label: string;
    /**
     * `contextual` gives this field a typeahead whose values are faceted over the LIVE search
     * (ADR-0034) — so a suggested value can never yield zero rows, and it arrives with the count of
     * rows it would leave. Fields without it stay plain text boxes. Data, not a switch statement:
     * turning a column's suggest on is a one-flag change.
     */
    suggest?: "contextual" | "none";
}

/**
 * A column-header filter affordance. Renders a filter icon (with a dot when any of
 * its fields is active) that opens a popover holding one text input per field. Each
 * keystroke is reported up via onChange; the parent accumulates these into the
 * backend columnFilters list (AND-combined, "contains" semantics). The popover owns
 * its own input state so the table's column definitions need not re-memoise per
 * keystroke; `initialValues` seeds that state so a filter restored from the URL (e.g.
 * on Back from a detail page) shows in the input. Clicks are kept from propagating so
 * they never toggle column sorting.
 */
export function ColumnFilterPopover({
    title,
    fields,
    onChange,
    onPick,
    initialValues,
    suggestContext,
}: {
    title: string;
    fields: FilterFieldDef[];
    onChange: (field: string, value: string) => void;
    /** A value was PICKED from the suggestions — filter on it exactly, not as a substring. */
    onPick?: (field: string, value: string) => void;
    initialValues?: Record<string, string>;
    /** The live search the contextual suggestions must be scoped to. */
    suggestContext?: unknown;
}): JSX.Element {
    const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
    const [values, setValues] = useState<Record<string, string>>(() => initialValues ?? {});

    const activeCount = fields.filter((fieldDef) => (values[fieldDef.field] ?? "").trim() !== "").length;

    const handleOpen = (event: MouseEvent<HTMLElement>) => {
        event.stopPropagation();
        setAnchorEl(event.currentTarget);
    };

    const handleChange = (field: string, value: string) => {
        setValues((previous) => ({ ...previous, [field]: value }));
        onChange(field, value);
    };

    const handlePick = (field: string, value: string) => {
        setValues((previous) => ({ ...previous, [field]: value }));
        (onPick ?? onChange)(field, value);
    };

    const handleClear = () => {
        fields.forEach((fieldDef) => onChange(fieldDef.field, ""));
        setValues({});
    };

    return (
        <>
            <Badge color="primary" variant="dot" overlap="circular" invisible={activeCount === 0}>
                <IconButton
                    size="small"
                    title={`Filter ${title}`}
                    onClick={handleOpen}
                    color={activeCount > 0 ? "primary" : "default"}
                >
                    <FilterListIcon fontSize="small" />
                </IconButton>
            </Badge>
            <Popover
                open={Boolean(anchorEl)}
                anchorEl={anchorEl}
                onClose={() => setAnchorEl(null)}
                anchorOrigin={{ vertical: "bottom", horizontal: "left" }}
            >
                <Box
                    sx={{ p: 2, display: "flex", flexDirection: "column", gap: 1.5, width: 260 }}
                    onClick={(event) => event.stopPropagation()}
                >
                    <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
                        {`Filter by ${title}`}
                    </Typography>
                    {fields.map((fieldDef) => (
                        fieldDef.suggest === "contextual" ? (
                            <ValueSuggest
                                key={fieldDef.field}
                                field={fieldDef.field}
                                label={fieldDef.label}
                                value={values[fieldDef.field] ?? ""}
                                search={suggestContext}
                                onTyped={(next) => handleChange(fieldDef.field, next)}
                                onPick={(picked) => handlePick(fieldDef.field, picked)}
                            />
                        ) : (
                            <TextField
                                key={fieldDef.field}
                                label={fieldDef.label}
                                size="small"
                                variant="outlined"
                                value={values[fieldDef.field] ?? ""}
                                onChange={(event) => handleChange(fieldDef.field, event.target.value)}
                            />
                        )
                    ))}
                    <Button size="small" onClick={handleClear} disabled={activeCount === 0}>
                        Clear
                    </Button>
                </Box>
            </Popover>
        </>
    );
}
