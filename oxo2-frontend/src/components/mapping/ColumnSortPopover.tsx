import { useContext, useState, type JSX, type MouseEvent } from "react";
import {
    Box,
    Button,
    FormControlLabel,
    IconButton,
    Popover,
    Radio,
    RadioGroup,
    ToggleButton,
    ToggleButtonGroup,
    Typography,
} from "@mui/material";
import SwapVertIcon from "@mui/icons-material/SwapVert";
import ArrowUpwardIcon from "@mui/icons-material/ArrowUpward";
import ArrowDownwardIcon from "@mui/icons-material/ArrowDownward";
import type { MRT_SortingState } from "material-react-table";
import { SortingContext } from "./sortingContext";

export interface SortFieldDef {
    /** Canonical Solr field name (snake_case); resolved by the backend MappingEnum. */
    field: string;
    /** User-facing radio label. */
    label: string;
}

/**
 * A column-header sort affordance, sibling to {@link ColumnFilterPopover}. Renders a
 * sort icon (an up/down arrow when this column group is the active sort key, otherwise
 * a neutral swap icon) that opens a popover offering one radio per sortable field
 * (id / label / iri) plus an ascending/descending toggle.
 *
 * Sorting is single-key *within* a column group (picking IRI replaces label for that
 * group) but multi-column *across* groups: Subject and Object can both contribute, in
 * which case a badge shows each group's priority in the sort order. Selecting within a
 * group that is already part of the sort replaces its entry in place (preserving
 * precedence); a new group appends to the end. Clearing removes the group's entry.
 * Clicks are kept from propagating so they never reach the (disabled) header sort.
 */
export function ColumnSortPopover({
    title,
    fields,
    onApply,
}: {
    title: string;
    fields: SortFieldDef[];
    onApply: (next: MRT_SortingState) => void;
}): JSX.Element {
    const sorting = useContext(SortingContext);
    const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);

    const fieldNames = fields.map((fieldDef) => fieldDef.field);
    const activeIndex = sorting.findIndex((sort) => fieldNames.includes(sort.id));
    const activeEntry = activeIndex >= 0 ? sorting[activeIndex] : undefined;
    const activeField = activeEntry?.id ?? null;
    const activeDesc = activeEntry?.desc ?? false;
    const priority = activeIndex + 1; // 0 when not part of the sort

    const handleOpen = (event: MouseEvent<HTMLElement>) => {
        event.stopPropagation();
        setAnchorEl(event.currentTarget);
    };

    const applySort = (field: string | null, desc: boolean) => {
        if (field === null) {
            onApply(sorting.filter((sort) => !fieldNames.includes(sort.id)));
        } else if (activeIndex >= 0) {
            // Replace this group's entry in place so its multi-column precedence is kept.
            onApply(sorting.map((sort, index) => (index === activeIndex ? { id: field, desc } : sort)));
        } else {
            onApply([...sorting, { id: field, desc }]);
        }
    };

    const SortIcon = activeEntry ? (activeDesc ? ArrowDownwardIcon : ArrowUpwardIcon) : SwapVertIcon;

    return (
        <>
            <IconButton
                size="small"
                title={`Sort by ${title}`}
                onClick={handleOpen}
                color={activeEntry ? "primary" : "default"}
            >
                <SortIcon fontSize="small" />
                {sorting.length > 1 && priority > 0 && (
                    <Typography
                        component="span"
                        variant="caption"
                        sx={{ ml: 0.25, fontWeight: 700, lineHeight: 1, color: "inherit" }}
                    >
                        {priority}
                    </Typography>
                )}
            </IconButton>
            <Popover
                open={Boolean(anchorEl)}
                anchorEl={anchorEl}
                onClose={() => setAnchorEl(null)}
                anchorOrigin={{ vertical: "bottom", horizontal: "left" }}
            >
                <Box
                    sx={{ p: 2, display: "flex", flexDirection: "column", gap: 1.5, width: 220 }}
                    onClick={(event) => event.stopPropagation()}
                >
                    <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
                        {`Sort by ${title}`}
                    </Typography>
                    <RadioGroup
                        value={activeField ?? ""}
                        onChange={(event) => applySort(event.target.value, activeDesc)}
                    >
                        {fields.map((fieldDef) => (
                            <FormControlLabel
                                key={fieldDef.field}
                                value={fieldDef.field}
                                control={<Radio size="small" />}
                                label={fieldDef.label}
                            />
                        ))}
                    </RadioGroup>
                    <ToggleButtonGroup
                        size="small"
                        exclusive
                        disabled={!activeEntry}
                        value={activeEntry ? (activeDesc ? "desc" : "asc") : null}
                        onChange={(_event, value) => {
                            if (value && activeField) applySort(activeField, value === "desc");
                        }}
                    >
                        <ToggleButton value="asc">
                            <ArrowUpwardIcon fontSize="small" sx={{ mr: 0.5 }} />
                            Asc
                        </ToggleButton>
                        <ToggleButton value="desc">
                            <ArrowDownwardIcon fontSize="small" sx={{ mr: 0.5 }} />
                            Desc
                        </ToggleButton>
                    </ToggleButtonGroup>
                    {sorting.length > 1 && priority > 0 && (
                        <Typography variant="caption" color="text.secondary">
                            {`Sort priority ${priority} of ${sorting.length}`}
                        </Typography>
                    )}
                    <Button size="small" onClick={() => applySort(null, false)} disabled={!activeEntry}>
                        Clear
                    </Button>
                </Box>
            </Popover>
        </>
    );
}
