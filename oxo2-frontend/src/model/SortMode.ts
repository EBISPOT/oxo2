import type { MRT_SortingState } from "material-react-table";

// The result-order choice on the results table's toolbar. It writes the same `?sort` param the
// table's per-column sort popovers write (via useUrlSorting), so the URL stays the single source
// of truth and the two controls can never disagree.
//
// "Best match" sends no sort at all, which is what lets the backend rank by relevance — i.e. by
// the provenance-led boost (ADR-0027). Any explicit Solr sort replaces `score` and would discard
// it. The search form deliberately has no order control (ADR-0036): ordering is a decision made
// while looking at results.

export type SortMode = 'RELEVANCE' | 'CONFIDENCE' | 'NEWEST';

export const SORT_MODE_LABELS: Record<SortMode, string> = {
    RELEVANCE: 'Best match',
    CONFIDENCE: 'Highest confidence',
    NEWEST: 'Most recent',
};

export const SORT_MODE_ORDER: SortMode[] = ['RELEVANCE', 'CONFIDENCE', 'NEWEST'];

/** The sorting state each mode stands for. Best match is the absence of a sort. */
const SORT_MODE_FIELDS: Record<SortMode, MRT_SortingState> = {
    RELEVANCE: [],
    CONFIDENCE: [{ id: 'confidence', desc: true }],
    NEWEST: [{ id: 'mapping_date', desc: true }],
};

export function sortModeToSorting(mode: SortMode): MRT_SortingState {
    return SORT_MODE_FIELDS[mode];
}

/**
 * The mode a sorting state reads back as, or null for a per-column sort the control cannot
 * represent — the select then shows a disabled "Column sort" entry rather than lying about
 * the order.
 */
export function sortModeFromSorting(sorting: MRT_SortingState): SortMode | null {
    const match = SORT_MODE_ORDER.find((mode) => {
        const fields = SORT_MODE_FIELDS[mode];
        return fields.length === sorting.length
            && fields.every((field, index) =>
                sorting[index].id === field.id && sorting[index].desc === field.desc);
    });
    return match ?? null;
}
