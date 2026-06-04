import { createContext } from "react";
import type { MRT_SortingState } from "material-react-table";

/**
 * The live sort list, shared with every {@link ColumnSortPopover} so each popover can
 * reflect the current sort (active field, direction, multi-column priority) without the
 * columns array having to be re-memoised — recreating columns would remount the header
 * components and snap an open popover shut on each selection. Kept in its own module so
 * the popover file only exports components (react-refresh / fast-refresh friendliness).
 */
export const SortingContext = createContext<MRT_SortingState>([]);
