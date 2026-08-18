import {Tooltip} from "@mui/material";
import type {ReactNode} from "react";

/**
 * A word or phrase in the UI that carries its own definition: hovering or keyboard-focusing it reveals
 * a plain-language explanation of the term.
 *
 * The dotted underline is the affordance — it marks the label as a term with a definition behind it,
 * and unlike an info icon beside it, it costs no horizontal space. `tabIndex` makes the tooltip
 * reachable by keyboard, not just by hover.
 *
 * This is only the presentation. The explanations themselves live next to the thing they explain —
 * `ColumnHelp` for mapping-table columns, `DataContent` for the landing page's corpus counts — so a
 * term's wording sits with its call sites rather than in a single map of unrelated topics. What must
 * not drift is how a defined term *looks and behaves*, which is why that part is here and not
 * copy-pasted per feature. Ordinary tooltips that merely name a control (an icon button's "Download
 * all results") are not defined terms and should use MUI's `Tooltip` directly.
 */
export function HelpTerm({help, label}: {help: ReactNode; label: ReactNode}) {
    return (
        <Tooltip
            title={help}
            arrow
            enterDelay={300}
            slotProps={{tooltip: {sx: {fontSize: "0.8125rem", fontWeight: 400, maxWidth: 320}}}}
        >
            <span
                tabIndex={0}
                className="cursor-help underline decoration-dotted decoration-gray-400 underline-offset-4"
            >
                {label}
            </span>
        </Tooltip>
    );
}
