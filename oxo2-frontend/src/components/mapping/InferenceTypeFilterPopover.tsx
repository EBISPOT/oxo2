import { useState, type JSX, type MouseEvent } from "react";
import {
    Badge,
    Box,
    FormControlLabel,
    IconButton,
    Popover,
    Radio,
    RadioGroup,
    Typography,
} from "@mui/material";
import FilterListIcon from "@mui/icons-material/FilterList";
import { INFERENCE_TYPE_LABELS, INFERENCE_TYPE_ORDER, InferenceType } from "../../model/InferenceType";

// Single-select inference-type filter (ADR-0011) living in the "Type" column header, replacing the
// old toolbar multi-select toggle. With only two inference types the three reachable states are
// All (both), Asserted only, and SSSOM inference only, so a single-select with an explicit "All"
// option is equivalent to the old multi-select. "All" sends both codes to the backend; its soft
// asserted-boost (SolrQueryBuilder RANKING_BOOST: ASSERTED x3 vs SSSOM_INFERENCE x2) then keeps
// asserted mappings ranked above inferred ones. Mirrors the ColumnFilterPopover idiom: a filter
// icon (dotted when narrowed to a single type) opening a small popover.
type SelectionKey = "ALL" | InferenceType;

export function InferenceTypeFilterPopover({
    value,
    onChange,
}: {
    value: InferenceType[];
    onChange: (next: InferenceType[]) => void;
}): JSX.Element {
    const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);

    // A single selected type narrows the filter; anything else (both types, or an empty list) is "All".
    const selected: SelectionKey = value.length === 1 ? value[0] : "ALL";
    const isNarrowed = selected !== "ALL";

    const handleOpen = (event: MouseEvent<HTMLElement>) => {
        event.stopPropagation();
        setAnchorEl(event.currentTarget);
    };

    const handleSelect = (key: SelectionKey) => {
        onChange(key === "ALL" ? [...INFERENCE_TYPE_ORDER] : [key]);
    };

    return (
        <>
            <Badge color="primary" variant="dot" overlap="circular" invisible={!isNarrowed}>
                <IconButton
                    size="small"
                    title="Filter Type"
                    onClick={handleOpen}
                    color={isNarrowed ? "primary" : "default"}
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
                    sx={{ p: 2, display: "flex", flexDirection: "column", gap: 1, width: 220 }}
                    onClick={(event) => event.stopPropagation()}
                >
                    <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
                        Filter by Type
                    </Typography>
                    <RadioGroup
                        value={selected}
                        onChange={(event) => handleSelect(event.target.value as SelectionKey)}
                    >
                        <FormControlLabel value="ALL" control={<Radio size="small" />} label="All" />
                        {INFERENCE_TYPE_ORDER.map((type) => (
                            <FormControlLabel
                                key={type}
                                value={type}
                                control={<Radio size="small" />}
                                label={INFERENCE_TYPE_LABELS[type]}
                            />
                        ))}
                    </RadioGroup>
                </Box>
            </Popover>
        </>
    );
}
