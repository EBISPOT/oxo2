import { useState, type JSX, type MouseEvent } from "react";
import { Badge, Box, Button, IconButton, Popover, TextField, Typography } from "@mui/material";
import FilterListIcon from "@mui/icons-material/FilterList";

export interface FilterFieldDef {
    /** Canonical Solr field name (snake_case); resolved by the backend MappingEnum. */
    field: string;
    /** User-facing input label. */
    label: string;
}

/**
 * A column-header filter affordance. Renders a filter icon (with a dot when any of
 * its fields is active) that opens a popover holding one text input per field. Each
 * keystroke is reported up via onChange; the parent accumulates these into the
 * backend columnFilters list (AND-combined, "contains" semantics). The popover owns
 * its own input state so the table's column definitions need not re-memoise per
 * keystroke. Clicks are kept from propagating so they never toggle column sorting.
 */
export function ColumnFilterPopover({
    title,
    fields,
    onChange,
}: {
    title: string;
    fields: FilterFieldDef[];
    onChange: (field: string, value: string) => void;
}): JSX.Element {
    const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
    const [values, setValues] = useState<Record<string, string>>({});

    const activeCount = fields.filter((fieldDef) => (values[fieldDef.field] ?? "").trim() !== "").length;

    const handleOpen = (event: MouseEvent<HTMLElement>) => {
        event.stopPropagation();
        setAnchorEl(event.currentTarget);
    };

    const handleChange = (field: string, value: string) => {
        setValues((previous) => ({ ...previous, [field]: value }));
        onChange(field, value);
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
                        <TextField
                            key={fieldDef.field}
                            label={fieldDef.label}
                            size="small"
                            variant="outlined"
                            value={values[fieldDef.field] ?? ""}
                            onChange={(event) => handleChange(fieldDef.field, event.target.value)}
                        />
                    ))}
                    <Button size="small" onClick={handleClear} disabled={activeCount === 0}>
                        Clear
                    </Button>
                </Box>
            </Popover>
        </>
    );
}
